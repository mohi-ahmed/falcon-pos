package com.spark.falcon.dashboard.repository;

import com.spark.falcon.dashboard.dto.DashboardChartPointResponse;
import com.spark.falcon.dashboard.dto.DashboardCustomerSummaryResponse;
import com.spark.falcon.dashboard.dto.DashboardInventorySummaryResponse;
import com.spark.falcon.dashboard.dto.DashboardPurchaseSummaryResponse;
import com.spark.falcon.dashboard.dto.DashboardSalesSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DashboardReadRepository {

    private static final int MONEY_SCALE = 4;
    private static final int QUANTITY_SCALE = 8;
    private static final String VALID_SALE = "('CONFIRMED','PARTIALLY_RETURNED','RETURNED')";
    private static final String VALID_PURCHASE = "('CONFIRMED','PARTIALLY_RETURNED','RETURNED')";

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardSalesSummaryResponse salesSummary(Long businessId,
                                                       Long branchId,
                                                       Instant from,
                                                       Instant toExclusive,
                                                       Instant todayStart,
                                                       Instant todayEnd,
                                                       Instant yesterdayStart,
                                                       Instant yesterdayEnd,
                                                       BigDecimal periodNetSales) {
        MapSqlParameterSource period = period(businessId, branchId, from, toExclusive);
        long confirmedInvoices = count("""
                select count(*) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), period);
        BigDecimal totalItems = quantity("""
                select coalesce(sum(si.base_quantity),0)
                from sale_items si join sales s on s.id=si.sale_id
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), period);
        long heldSales = count("""
                select count(*) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status='HELD' and s.created_at>=:from and s.created_at<:to
                """, period);
        long confirmedReturns = count("""
                select count(*) from sale_returns sr
                where sr.business_id=:businessId and sr.branch_id=:branchId
                  and sr.status='CONFIRMED' and sr.confirmed_at>=:from and sr.confirmed_at<:to
                """, period);
        long corrected = count("""
                select count(*) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in ('VOIDED','REVERSED')
                  and coalesce(s.corrected_at,s.updated_at)>=:from and coalesce(s.corrected_at,s.updated_at)<:to
                """, period);
        BigDecimal effectiveNetSales = periodNetSales == null ? netSales(businessId, branchId, from, toExclusive)
                : safeMoney(periodNetSales);
        BigDecimal averageInvoice = confirmedInvoices == 0 ? moneyZero()
                : effectiveNetSales.divide(BigDecimal.valueOf(confirmedInvoices), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal today = netSales(businessId, branchId, todayStart, todayEnd);
        BigDecimal yesterday = netSales(businessId, branchId, yesterdayStart, yesterdayEnd);
        BigDecimal comparison = percentChange(today, yesterday);
        return new DashboardSalesSummaryResponse(confirmedInvoices, totalItems, averageInvoice, heldSales,
                confirmedReturns, corrected, today, yesterday, comparison);
    }

    public DashboardInventorySummaryResponse inventorySummary(Long businessId,
                                                               Long branchId,
                                                               LocalDate today,
                                                               YearMonth currentMonth) {
        MapSqlParameterSource params = businessBranch(businessId, branchId)
                .addValue("today", today, Types.DATE)
                .addValue("d7", today.plusDays(7), Types.DATE)
                .addValue("d30", today.plusDays(30), Types.DATE)
                .addValue("monthStart", currentMonth.atDay(1), Types.DATE)
                .addValue("monthEnd", currentMonth.plusMonths(1).atDay(1), Types.DATE);

        Map<String, Object> stock = jdbc.queryForMap("""
                with variant_stock as (
                    select pv.id,
                           pv.reorder_level,
                           p.batch_tracking_required,
                           p.track_expiry,
                           coalesce(bps.base_quantity-bps.reserved_base_quantity,0) as branch_available,
                           coalesce((
                               select sum(greatest(pb.available_base_quantity-pb.reserved_base_quantity,0))
                               from product_batches pb
                               where pb.business_id=:businessId and pb.branch_id=:branchId
                                 and pb.product_variant_id=pv.id and pb.status='ACTIVE'
                                 and (not p.track_expiry or pb.expiry_date is null or pb.expiry_date>=:today)
                           ),0) as batch_available
                    from product_variants pv
                    join products p on p.id=pv.product_id
                    join branch_products bp on bp.product_id=p.id and bp.branch_id=:branchId and bp.active=true
                    left join branch_product_stocks bps on bps.business_id=:businessId and bps.branch_id=:branchId
                                                       and bps.product_variant_id=pv.id
                    where p.business_id=:businessId and p.status='ACTIVE' and p.archived_at is null
                      and pv.status='ACTIVE'
                ), resolved as (
                    select *, case when batch_tracking_required then batch_available else greatest(branch_available,0) end as sellable
                    from variant_stock
                )
                select coalesce(sum(sellable),0) as sellable_quantity,
                       count(*) filter (where sellable>0 and sellable<=reorder_level) as low_stock,
                       count(*) filter (where sellable<=0) as out_of_stock
                from resolved
                """, params);

        BigDecimal currentStockValue = money("""
                select coalesce(sum(bps.inventory_value),0) from branch_product_stocks bps
                where bps.business_id=:businessId and bps.branch_id=:branchId
                """, params);

        long within7 = count("""
                select count(distinct pb.product_variant_id) from product_batches pb
                where pb.business_id=:businessId and pb.branch_id=:branchId
                  and pb.available_base_quantity>0 and pb.status='ACTIVE'
                  and pb.expiry_date>=:today and pb.expiry_date<=:d7
                """, params);
        long within30 = count("""
                select count(distinct pb.product_variant_id) from product_batches pb
                where pb.business_id=:businessId and pb.branch_id=:branchId
                  and pb.available_base_quantity>0 and pb.status='ACTIVE'
                  and pb.expiry_date>=:today and pb.expiry_date<=:d30
                """, params);
        BigDecimal expiredQuantity = quantity("""
                select coalesce(sum(pb.available_base_quantity),0) from product_batches pb
                where pb.business_id=:businessId and pb.branch_id=:branchId
                  and pb.available_base_quantity>0 and (pb.expiry_date<:today or pb.status='EXPIRED')
                """, params);
        BigDecimal expiredValue = money("""
                select coalesce(sum(pb.available_base_quantity*coalesce(bps.weighted_average_cost,0)),0)
                from product_batches pb
                left join branch_product_stocks bps on bps.business_id=pb.business_id and bps.branch_id=pb.branch_id
                                                   and bps.product_variant_id=pb.product_variant_id
                where pb.business_id=:businessId and pb.branch_id=:branchId
                  and pb.available_base_quantity>0 and (pb.expiry_date<:today or pb.status='EXPIRED')
                """, params);
        BigDecimal monthLoss = money("""
                select coalesce(sum(il.financial_loss),0) from inventory_losses il
                where il.business_id=:businessId and il.branch_id=:branchId and il.status='POSTED'
                  and il.disposal_date>=:monthStart and il.disposal_date<:monthEnd
                """, params);
        long pendingTransfers = count("""
                select count(*) from branch_transfers bt
                where bt.business_id=:businessId
                  and (bt.source_branch_id=:branchId or bt.destination_branch_id=:branchId)
                  and bt.status in ('SUBMITTED','APPROVED','DISPATCHED','PARTIALLY_RECEIVED')
                """, params);
        long transferDiscrepancies = count("""
                select count(distinct bt.id) from branch_transfers bt
                join branch_transfer_items bti on bti.transfer_id=bt.id
                where bt.business_id=:businessId
                  and (bt.source_branch_id=:branchId or bt.destination_branch_id=:branchId)
                  and bti.discrepancy_resolved=false
                  and (bti.damaged_quantity>0 or bti.missing_quantity>0 or bti.rejected_quantity>0)
                """, params);
        long countVariances = count("""
                select count(distinct pc.id) from physical_stock_counts pc
                join physical_stock_count_rows pr on pr.count_id=pc.id
                where pc.business_id=:businessId and pc.branch_id=:branchId
                  and pc.status in ('SUBMITTED','UNDER_REVIEW','APPROVED')
                  and coalesce(pr.variance,0)<>0
                """, params);
        long failedImports = count("""
                select count(*) from stock_import_batches sib
                where sib.business_id=:businessId and sib.branch_id=:branchId
                  and sib.status in ('VALIDATION_FAILED','FAILED')
                """, params);
        long waitingImports = count("""
                select count(*) from stock_import_batches sib
                where sib.business_id=:businessId and sib.branch_id=:branchId
                  and sib.status in ('UPLOADED','VALIDATING','READY_FOR_CONFIRMATION')
                """, params);

        return new DashboardInventorySummaryResponse(
                decimal(stock.get("sellable_quantity"), QUANTITY_SCALE),
                currentStockValue,
                number(stock.get("low_stock")), number(stock.get("out_of_stock")),
                within7, within30, expiredQuantity, expiredValue, monthLoss,
                pendingTransfers, transferDiscrepancies, countVariances, failedImports, waitingImports);
    }

    public DashboardCustomerSummaryResponse customerSummary(Long businessId,
                                                              Long branchId,
                                                              LocalDate today,
                                                              ZoneId zone,
                                                              BigDecimal receivedDuePayments) {
        MapSqlParameterSource params = businessBranch(businessId, branchId)
                .addValue("today", today, Types.DATE)
                .addValue("zoneName", zone.getId());
        Map<String, Object> row = jdbc.queryForMap("""
                select coalesce(sum(s.due_amount),0) as outstanding_due,
                       coalesce(sum(case when s.due_amount>0
                                           and coalesce(s.due_date, cast(s.confirmed_at at time zone :zoneName as date))<:today
                                         then s.due_amount else 0 end),0) as overdue_amount,
                       count(*) filter (where s.due_amount>0
                                           and coalesce(s.due_date, cast(s.confirmed_at at time zone :zoneName as date))<:today)
                           as overdue_count
                from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s
                """.formatted(VALID_SALE), params);
        return new DashboardCustomerSummaryResponse(
                decimal(row.get("outstanding_due"), MONEY_SCALE),
                receivedDuePayments == null ? null : safeMoney(receivedDuePayments),
                decimal(row.get("overdue_amount"), MONEY_SCALE),
                number(row.get("overdue_count")));
    }

    public DashboardPurchaseSummaryResponse purchaseSummary(Long businessId,
                                                             Long branchId,
                                                             Instant from,
                                                             Instant toExclusive,
                                                             Instant todayStart,
                                                             Instant todayEnd) {
        MapSqlParameterSource period = period(businessId, branchId, from, toExclusive);
        MapSqlParameterSource today = period(businessId, branchId, todayStart, todayEnd);
        BigDecimal todayPurchases = money("""
                select coalesce(sum(p.total_payable),0) from purchases p
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status in %s and p.confirmed_at>=:from and p.confirmed_at<:to
                """.formatted(VALID_PURCHASE), today);
        BigDecimal periodPurchases = money("""
                select coalesce(sum(p.total_payable),0) from purchases p
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status in %s and p.confirmed_at>=:from and p.confirmed_at<:to
                """.formatted(VALID_PURCHASE), period);
        BigDecimal supplierDue = money("""
                select coalesce(sum(p.due_amount),0) from purchases p
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status in %s and p.due_amount>0
                """.formatted(VALID_PURCHASE), businessBranch(businessId, branchId));
        long dueInvoices = count("""
                select count(*) from purchases p
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status in %s and p.due_amount>0
                """.formatted(VALID_PURCHASE), businessBranch(businessId, branchId));
        long pendingReturns = count("""
                select count(*) from purchase_returns pr
                where pr.business_id=:businessId and pr.branch_id=:branchId and pr.status='DRAFT'
                """, businessBranch(businessId, branchId));
        long pendingRefundCredits = count("""
                select count(*) from purchase_returns pr
                where pr.business_id=:businessId and pr.branch_id=:branchId and pr.status='DRAFT'
                  and pr.settlement_type in ('SUPPLIER_CREDIT','REFUND')
                """, businessBranch(businessId, branchId));
        return new DashboardPurchaseSummaryResponse(todayPurchases, periodPurchases, supplierDue,
                dueInvoices, pendingReturns, pendingRefundCredits);
    }

    public List<DashboardChartPointResponse> chart(Long businessId,
                                                   Long branchId,
                                                   LocalDate fromDate,
                                                   LocalDate toDate,
                                                   ZoneId zone) {
        LinkedHashMap<LocalDate, ChartAccumulator> values = new LinkedHashMap<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            values.put(date, new ChartAccumulator());
        }
        Instant from = fromDate.atStartOfDay(zone).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(zone).toInstant();
        MapSqlParameterSource instants = period(businessId, branchId, from, to).addValue("zoneName", zone.getId());
        MapSqlParameterSource dates = businessBranch(businessId, branchId)
                .addValue("fromDate", fromDate, Types.DATE).addValue("toDate", toDate, Types.DATE);

        jdbc.query("""
                select daily.day,
                       coalesce(sum(daily.sales),0) as sales
                from (
                    select cast(s.confirmed_at at time zone :zoneName as date) as day,
                           (s.gross_item_total-s.item_discount_total-s.order_discount) as sales
                    from sales s
                    where s.business_id=:businessId and s.branch_id=:branchId and s.status in %s
                      and s.confirmed_at>=:from and s.confirmed_at<:to
                ) daily
                group by daily.day
                """.formatted(VALID_SALE), instants, rs -> {
            ChartAccumulator value = values.get(rs.getDate("day").toLocalDate());
            if (value != null) value.sales = safeMoney(rs.getBigDecimal("sales"));
        });
        jdbc.query("""
                select daily.day,
                       coalesce(sum(daily.return_amount),0) as returns
                from (
                    select cast(sr.confirmed_at at time zone :zoneName as date) as day,
                           sr.total_return_amount as return_amount
                    from sale_returns sr
                    where sr.business_id=:businessId and sr.branch_id=:branchId and sr.status='CONFIRMED'
                      and sr.confirmed_at>=:from and sr.confirmed_at<:to
                ) daily
                group by daily.day
                """, instants, rs -> {
            ChartAccumulator value = values.get(rs.getDate("day").toLocalDate());
            if (value != null) value.returns = safeMoney(rs.getBigDecimal("returns"));
        });
        jdbc.query("""
                select e.expense_date as day, coalesce(sum(e.amount),0) as expense
                from expenses e
                where e.business_id=:businessId and e.branch_id=:branchId
                  and e.status='POSTED' and e.classification='OPERATING_EXPENSE'
                  and e.expense_date>=:fromDate and e.expense_date<=:toDate
                group by e.expense_date
                """, dates, rs -> {
            ChartAccumulator value = values.get(rs.getDate("day").toLocalDate());
            if (value != null) value.expense = safeMoney(rs.getBigDecimal("expense"));
        });

        return values.entrySet().stream()
                .map(entry -> new DashboardChartPointResponse(entry.getKey(),
                        entry.getValue().sales.subtract(entry.getValue().returns).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                        entry.getValue().expense))
                .toList();
    }

    private BigDecimal netSales(Long businessId, Long branchId, Instant from, Instant toExclusive) {
        MapSqlParameterSource params = period(businessId, branchId, from, toExclusive);
        BigDecimal sales = money("""
                select coalesce(sum(s.gross_item_total-s.item_discount_total-s.order_discount),0)
                from sales s where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), params);
        BigDecimal returns = money("""
                select coalesce(sum(sr.total_return_amount),0)
                from sale_returns sr where sr.business_id=:businessId and sr.branch_id=:branchId
                  and sr.status='CONFIRMED' and sr.confirmed_at>=:from and sr.confirmed_at<:to
                """, params);
        return sales.subtract(returns).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return current != null && current.signum() != 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private MapSqlParameterSource period(Long businessId, Long branchId, Instant from, Instant toExclusive) {
        return businessBranch(businessId, branchId)
                .addValue("from", utcOffsetDateTime(from), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", utcOffsetDateTime(toExclusive), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private OffsetDateTime utcOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private MapSqlParameterSource businessBranch(Long businessId, Long branchId) {
        return new MapSqlParameterSource().addValue("businessId", businessId, Types.BIGINT)
                .addValue("branchId", branchId, Types.BIGINT);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal money(String sql, MapSqlParameterSource params) {
        return decimal(jdbc.queryForObject(sql, params, BigDecimal.class), MONEY_SCALE);
    }

    private BigDecimal quantity(String sql, MapSqlParameterSource params) {
        return decimal(jdbc.queryForObject(sql, params, BigDecimal.class), QUANTITY_SCALE);
    }

    private BigDecimal decimal(Object value, int scale) {
        if (value == null) return BigDecimal.ZERO.setScale(scale, RoundingMode.UNNECESSARY);
        BigDecimal decimal = value instanceof BigDecimal big ? big : new BigDecimal(value.toString());
        return decimal.setScale(scale, RoundingMode.HALF_UP);
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? 0L : Long.parseLong(value.toString());
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? moneyZero() : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static final class ChartAccumulator {
        private BigDecimal sales = BigDecimal.ZERO.setScale(MONEY_SCALE);
        private BigDecimal returns = BigDecimal.ZERO.setScale(MONEY_SCALE);
        private BigDecimal expense = BigDecimal.ZERO.setScale(MONEY_SCALE);
    }
}
