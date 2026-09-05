package com.spark.falcon.analytics.repository;

import com.spark.falcon.analytics.dto.*;
import com.spark.falcon.analytics.exception.AnalyticsValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class AnalyticsReadRepository {

    private static final int MONEY_SCALE = 4;
    private static final String VALID_SALE = "('CONFIRMED','PARTIALLY_RETURNED','RETURNED')";
    private static final String VALID_PURCHASE = "('CONFIRMED','PARTIALLY_RETURNED','RETURNED')";

    private final NamedParameterJdbcTemplate jdbc;

    public FinancialSummaryResponse financialSummary(Long businessId,
                                                      Long branchId,
                                                      Instant from,
                                                      Instant toExclusive,
                                                      Instant todayStart,
                                                      Instant todayEnd,
                                                      LocalDate fromDate,
                                                      LocalDate toDate,
                                                      LocalDate todayDate) {
        MapSqlParameterSource period = periodParams(businessId, branchId, from, toExclusive);
        MapSqlParameterSource today = periodParams(businessId, branchId, todayStart, todayEnd);

        BigDecimal grossSales = money("""
                select coalesce(sum(s.gross_item_total),0) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), period);
        BigDecimal salesReturns = saleReturns(period);
        BigDecimal discounts = money("""
                select coalesce(sum(s.item_discount_total + s.order_discount),0) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), period);
        BigDecimal cogs = money("""
                select coalesce(sum(s.cogs_total),0) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), period).subtract(returnedCogs(period));
        BigDecimal operatingExpense = operatingExpense(businessId, branchId, fromDate, toDate);
        BigDecimal inventoryLoss = inventoryLoss(businessId, branchId, fromDate, toDate);
        BigDecimal netSales = grossSales.subtract(salesReturns).subtract(discounts);
        BigDecimal grossProfit = netSales.subtract(cogs);
        BigDecimal netProfit = grossProfit.subtract(operatingExpense).subtract(inventoryLoss);

        BigDecimal todayGross = money("""
                select coalesce(sum(s.gross_item_total),0) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), today);
        BigDecimal todayDiscount = money("""
                select coalesce(sum(s.item_discount_total + s.order_discount),0) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.confirmed_at>=:from and s.confirmed_at<:to
                """.formatted(VALID_SALE), today);
        BigDecimal todayIncome = todayGross.subtract(saleReturns(today)).subtract(todayDiscount);

        BigDecimal customerDue = money("""
                select coalesce(sum(s.due_amount),0) from sales s
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in %s and s.due_amount>0
                """.formatted(VALID_SALE), businessBranchParams(businessId, branchId));
        BigDecimal supplierDue = money("""
                select coalesce(sum(p.due_amount),0) from purchases p
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status in %s and p.due_amount>0
                """.formatted(VALID_PURCHASE), businessBranchParams(businessId, branchId));
        BigDecimal receivedPayments = money("""
                select coalesce(sum(p.amount),0) from payments p
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status='CONFIRMED' and p.direction='INFLOW'
                  and p.financial_purpose='CUSTOMER_DUE_COLLECTION'
                  and p.confirmed_at>=:from and p.confirmed_at<:to
                """, period);
        BigDecimal opening = cashBalanceAt(businessId, branchId, from);
        BigDecimal closing = cashBalanceAt(businessId, branchId, toExclusive);
        BigDecimal current = money("""
                select coalesce(max(c.current_balance),0) from cashbooks c
                where c.business_id=:businessId and c.branch_id=:branchId
                """, businessBranchParams(businessId, branchId));

        return new FinancialSummaryResponse(opening, todayIncome, netSales, operatingExpense(businessId, branchId, todayDate, todayDate),
                current, closing, grossSales, salesReturns, discounts, netSales, cogs.max(zero()),
                grossProfit, operatingExpense, inventoryLoss, netProfit, customerDue, supplierDue,
                receivedPayments);
    }

    public ExpirySummaryResponse expirySummary(Long businessId, Long branchId, LocalDate today,
                                               LocalDate monthStart, LocalDate monthEndExclusive) {
        MapSqlParameterSource params = businessBranchParams(businessId, branchId)
                .addValue("today", today, Types.DATE).addValue("d7", today.plusDays(7), Types.DATE).addValue("d30", today.plusDays(30), Types.DATE)
                .addValue("monthStart", monthStart, Types.DATE).addValue("monthEnd", monthEndExclusive, Types.DATE);
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
        BigDecimal expiredQty = money("""
                select coalesce(sum(pb.available_base_quantity),0) from product_batches pb
                where pb.business_id=:businessId and pb.branch_id=:branchId
                  and pb.available_base_quantity>0 and (pb.expiry_date<:today or pb.status='EXPIRED')
                """, params);
        BigDecimal expiredValue = money("""
                select coalesce(sum(pb.available_base_quantity * coalesce(bps.weighted_average_cost,0)),0)
                from product_batches pb
                left join branch_product_stocks bps on bps.business_id=pb.business_id
                  and bps.branch_id=pb.branch_id and bps.product_variant_id=pb.product_variant_id
                where pb.business_id=:businessId and pb.branch_id=:branchId
                  and pb.available_base_quantity>0 and (pb.expiry_date<:today or pb.status='EXPIRED')
                """, params);
        BigDecimal expiryLoss = money("""
                select coalesce(sum(il.financial_loss),0)
                from inventory_losses il
                left join product_batches pb on pb.id=il.product_batch_id
                where il.business_id=:businessId and il.branch_id=:branchId and il.status='POSTED'
                  and il.disposal_date>=:monthStart and il.disposal_date<:monthEnd
                  and (lower(il.reason) like '%expir%' or (pb.expiry_date is not null and pb.expiry_date<=il.disposal_date))
                """, params);
        return new ExpirySummaryResponse(within7, within30, expiredQty, expiredValue, expiryLoss);
    }

    public List<RankedMetricResponse> topProducts(Long businessId, Long branchId, Instant from, Instant toExclusive, int limit) {
        MapSqlParameterSource params = periodParams(businessId, branchId, from, toExclusive).addValue("limit", limit, Types.INTEGER);
        return jdbc.query("""
                select si.product_variant_id as id,
                       max(si.product_name_snapshot) as label,
                       max(si.variant_name_snapshot) as secondary_label,
                       coalesce(sum(si.line_payable-coalesce(r.return_amount,0)),0) as value,
                       coalesce(sum(si.base_quantity-coalesce(r.return_quantity,0)),0) as quantity
                from sale_items si join sales s on s.id=si.sale_id
                left join (
                    select sri.sale_item_id,coalesce(sum(sri.return_amount),0) as return_amount,
                           coalesce(sum(sri.base_quantity),0) as return_quantity
                    from sale_return_items sri join sale_returns sr on sr.id=sri.sale_return_id
                    where sr.status='CONFIRMED' and sr.confirmed_at>=:from and sr.confirmed_at<:to
                    group by sri.sale_item_id
                ) r on r.sale_item_id=si.id
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED')
                  and s.confirmed_at>=:from and s.confirmed_at<:to
                group by si.product_variant_id
                having coalesce(sum(si.base_quantity-coalesce(r.return_quantity,0)),0)>0
                order by quantity desc, value desc
                limit :limit
                """, params, (rs, row) -> ranked(rs.getLong("id"), rs.getString("label"),
                rs.getString("secondary_label"), rs.getBigDecimal("value"), rs.getBigDecimal("quantity")));
    }

    public List<RankedMetricResponse> topCustomers(Long businessId, Long branchId, Instant from, Instant toExclusive, int limit) {
        MapSqlParameterSource params = periodParams(businessId, branchId, from, toExclusive).addValue("limit", limit, Types.INTEGER);
        return jdbc.query("""
                select c.id as id, coalesce(c.name,'Walk-in Customer') as label,
                       coalesce(c.phone,c.email,'') as secondary_label,
                       coalesce(sum(s.total_payable-coalesce(r.return_amount,0)),0) as value,
                       count(s.id) as quantity
                from sales s join customers c on c.id=s.customer_id
                left join (
                    select sr.sale_id,coalesce(sum(sr.total_return_amount),0) as return_amount
                    from sale_returns sr where sr.status='CONFIRMED' and sr.confirmed_at>=:from and sr.confirmed_at<:to
                    group by sr.sale_id
                ) r on r.sale_id=s.id
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED')
                  and s.confirmed_at>=:from and s.confirmed_at<:to
                group by c.id,c.name,c.phone,c.email
                order by value desc, quantity desc
                limit :limit
                """, params, (rs, row) -> ranked(rs.getLong("id"), rs.getString("label"),
                rs.getString("secondary_label"), rs.getBigDecimal("value"), rs.getBigDecimal("quantity")));
    }

    public List<RankedMetricResponse> leadingSuppliers(Long businessId, Long branchId, LocalDate from, LocalDate to, int limit) {
        MapSqlParameterSource params = businessBranchParams(businessId, branchId)
                .addValue("fromDate", from, Types.DATE).addValue("toDate", to, Types.DATE).addValue("limit", limit, Types.INTEGER);
        return jdbc.query("""
                select s.id as id, s.name as label, coalesce(s.mobile_number,s.email,'') as secondary_label,
                       coalesce(sum(p.total_payable-p.returned_amount),0) as value,
                       count(p.id) as quantity
                from purchases p join suppliers s on s.id=p.supplier_id
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED')
                  and p.purchase_date>=:fromDate and p.purchase_date<=:toDate
                group by s.id,s.name,s.mobile_number,s.email
                order by value desc, quantity desc
                limit :limit
                """, params, (rs, row) -> ranked(rs.getLong("id"), rs.getString("label"),
                rs.getString("secondary_label"), rs.getBigDecimal("value"), rs.getBigDecimal("quantity")));
    }

    public List<RankedMetricResponse> topBrands(Long businessId, Long branchId, Instant from, Instant toExclusive, int limit) {
        MapSqlParameterSource params = periodParams(businessId, branchId, from, toExclusive).addValue("limit", limit, Types.INTEGER);
        return jdbc.query("""
                select min(p.id) as id, coalesce(nullif(p.brand,''),'Unbranded') as label,
                       '' as secondary_label,
                       coalesce(sum(si.line_payable-coalesce(r.return_amount,0)),0) as value,
                       coalesce(sum(si.base_quantity-coalesce(r.return_quantity,0)),0) as quantity
                from sale_items si
                join sales s on s.id=si.sale_id
                join product_variants pv on pv.id=si.product_variant_id
                join products p on p.id=pv.product_id
                left join (
                    select sri.sale_item_id,coalesce(sum(sri.return_amount),0) as return_amount,
                           coalesce(sum(sri.base_quantity),0) as return_quantity
                    from sale_return_items sri join sale_returns sr on sr.id=sri.sale_return_id
                    where sr.status='CONFIRMED' and sr.confirmed_at>=:from and sr.confirmed_at<:to
                    group by sri.sale_item_id
                ) r on r.sale_item_id=si.id
                where s.business_id=:businessId and s.branch_id=:branchId
                  and s.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED')
                  and s.confirmed_at>=:from and s.confirmed_at<:to
                group by coalesce(nullif(p.brand,''),'Unbranded')
                having coalesce(sum(si.base_quantity-coalesce(r.return_quantity,0)),0)>0
                order by value desc, quantity desc
                limit :limit
                """, params, (rs, row) -> ranked(rs.getLong("id"), rs.getString("label"),
                rs.getString("secondary_label"), rs.getBigDecimal("value"), rs.getBigDecimal("quantity")));
    }

    public List<Map<String, Object>> customerBirthdays(Long businessId) {
        return jdbc.queryForList("""
                select id, coalesce(name,'Customer') as customer_name, date_of_birth, created_at
                from customers
                where business_id=:businessId and active=true and archived_at is null and date_of_birth is not null
                """, new MapSqlParameterSource().addValue("businessId", businessId, Types.BIGINT));
    }

    public List<DailyPerformanceResponse> dailyPerformance(Long businessId, Long branchId,
                                                            Instant monthStart, Instant monthEnd,
                                                            LocalDate firstDay, LocalDate lastDay,
                                                            ZoneId branchZone) {
        MapSqlParameterSource instantParams = periodParams(businessId, branchId, monthStart, monthEnd)
                .addValue("zoneName", branchZone.getId(), Types.VARCHAR);
        MapSqlParameterSource dateParams = businessBranchParams(businessId, branchId)
                .addValue("fromDate", firstDay, Types.DATE).addValue("toDate", lastDay, Types.DATE);
        Map<LocalDate, DayAccumulator> days = new TreeMap<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) days.put(day, new DayAccumulator());

        jdbc.query("""
                select daily.sale_day as day,
                       coalesce(sum(daily.net_income),0) as income,
                       coalesce(sum(daily.cogs),0) as cogs
                from (
                    select cast(s.confirmed_at at time zone :zoneName as date) as sale_day,
                           (s.gross_item_total-s.item_discount_total-s.order_discount) as net_income,
                           s.cogs_total as cogs
                    from sales s
                    where s.business_id=:businessId and s.branch_id=:branchId
                      and s.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED')
                      and s.confirmed_at>=:from and s.confirmed_at<:to
                ) daily
                group by daily.sale_day
                """, instantParams, rs -> {
            DayAccumulator a = days.computeIfAbsent(rs.getDate("day").toLocalDate(), key -> new DayAccumulator());
            a.income = decimal(rs.getBigDecimal("income"));
            a.cogs = decimal(rs.getBigDecimal("cogs"));
        });
        jdbc.query("""
                select daily.return_day as day,
                       coalesce(sum(daily.return_amount),0) as returns,
                       coalesce(sum(daily.returned_cogs),0) as returned_cogs
                from (
                    select cast(sr.confirmed_at at time zone :zoneName as date) as return_day,
                           sr.total_return_amount as return_amount,
                           coalesce(rc.returned_cogs,0) as returned_cogs
                    from sale_returns sr
                    left join (
                        select sri.sale_return_id,
                               coalesce(sum(sri.preserved_financial_cost_snapshot*sri.base_quantity),0) as returned_cogs
                        from sale_return_items sri
                        group by sri.sale_return_id
                    ) rc on rc.sale_return_id=sr.id
                    where sr.business_id=:businessId and sr.branch_id=:branchId and sr.status='CONFIRMED'
                      and sr.confirmed_at>=:from and sr.confirmed_at<:to
                ) daily
                group by daily.return_day
                """, instantParams, rs -> {
            DayAccumulator a = days.computeIfAbsent(rs.getDate("day").toLocalDate(), key -> new DayAccumulator());
            a.returns = decimal(rs.getBigDecimal("returns"));
            a.returnedCogs = decimal(rs.getBigDecimal("returned_cogs"));
        });
        jdbc.query("""
                select e.expense_date as day, coalesce(sum(e.amount),0) as expense
                from expenses e where e.business_id=:businessId and e.branch_id=:branchId
                  and e.status='POSTED' and e.classification='OPERATING_EXPENSE'
                  and e.expense_date>=:fromDate and e.expense_date<=:toDate
                group by e.expense_date
                """, dateParams, rs -> {
            DayAccumulator a = days.computeIfAbsent(rs.getDate("day").toLocalDate(), key -> new DayAccumulator());
            a.expense = decimal(rs.getBigDecimal("expense"));
        });
        jdbc.query("""
                select il.disposal_date as day, coalesce(sum(il.financial_loss),0) as loss
                from inventory_losses il where il.business_id=:businessId and il.branch_id=:branchId
                  and il.status='POSTED' and il.disposal_date>=:fromDate and il.disposal_date<=:toDate
                group by il.disposal_date
                """, dateParams, rs -> {
            DayAccumulator a = days.computeIfAbsent(rs.getDate("day").toLocalDate(), key -> new DayAccumulator());
            a.loss = decimal(rs.getBigDecimal("loss"));
        });

        return days.entrySet().stream().map(entry -> {
            DayAccumulator a = entry.getValue();
            BigDecimal income = a.income.subtract(a.returns);
            BigDecimal netProfit = income.subtract(a.cogs.subtract(a.returnedCogs)).subtract(a.expense).subtract(a.loss);
            return new DailyPerformanceResponse(entry.getKey(), income, a.expense, netProfit);
        }).toList();
    }

    public List<PaymentMixResponse> paymentMix(Long businessId, Long branchId, Instant from, Instant toExclusive) {
        return jdbc.query("""
                select p.payment_method_name_snapshot as label, p.cash_payment as cash,
                       coalesce(sum(p.amount),0) as amount
                from payments p
                where p.business_id=:businessId and p.branch_id=:branchId
                  and p.status='CONFIRMED' and p.direction='INFLOW'
                  and p.financial_purpose='CUSTOMER_DUE_COLLECTION'
                  and p.confirmed_at>=:from and p.confirmed_at<:to
                group by p.payment_method_name_snapshot,p.cash_payment
                order by amount desc
                """, periodParams(businessId, branchId, from, toExclusive),
                (rs, row) -> new PaymentMixResponse(rs.getString("label"), decimal(rs.getBigDecimal("amount")), rs.getBoolean("cash")));
    }

    public List<Map<String, Object>> loginAuditRows(Long businessId, int limit) {
        List<String> actors = jdbc.queryForList("""
                select o.email from owners o join businesses b on b.owner_id=o.id where b.id=:businessId
                union select u.email from users u where u.business_id=:businessId
                """, new MapSqlParameterSource().addValue("businessId", businessId, Types.BIGINT), String.class);
        if (actors.isEmpty()) return List.of();
        return jdbc.queryForList("""
                select id, actor_identifier, ip_address, event_type, created_at
                from security_audit_events
                where actor_identifier in (:actors) and event_type in ('LOGIN_SUCCESS','LOGIN_FAILURE','LOGOUT')
                order by created_at desc, id desc
                limit :limit
                """, new MapSqlParameterSource().addValue("actors", actors).addValue("limit", limit, Types.INTEGER));
    }


    public ReportFilterOptionsResponse filterOptions(Long businessId, List<Long> branchIds) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("businessId", businessId, Types.BIGINT).addValue("branchIds", branchIds);
        List<FilterOptionResponse> products = jdbc.query("""
                select distinct p.id,p.name from products p join branch_products bp on bp.product_id=p.id
                where p.business_id=:businessId and bp.branch_id in (:branchIds) and bp.active=true and p.archived_at is null
                order by p.name
                """, params, (rs,row) -> new FilterOptionResponse(rs.getLong("id"), rs.getString("name")));
        List<FilterOptionResponse> categories = jdbc.query("""
                select distinct c.id,c.name from product_categories c join branch_product_categories bc on bc.category_id=c.id
                where c.business_id=:businessId and bc.branch_id in (:branchIds) and bc.active=true and c.archived_at is null
                order by c.name
                """, params, (rs,row) -> new FilterOptionResponse(rs.getLong("id"), rs.getString("name")));
        List<FilterOptionResponse> suppliers = jdbc.query("""
                select distinct s.id,s.name from suppliers s join supplier_branches sb on sb.supplier_id=s.id
                where s.business_id=:businessId and sb.branch_id in (:branchIds) and s.active=true and s.archived_at is null
                order by s.name
                """, params, (rs,row) -> new FilterOptionResponse(rs.getLong("id"), rs.getString("name")));
        List<FilterOptionResponse> customers = jdbc.query("""
                select c.id,coalesce(c.name,'Customer') as name from customers c
                where c.business_id=:businessId and c.active=true and c.archived_at is null order by c.name
                """, params, (rs,row) -> new FilterOptionResponse(rs.getLong("id"), rs.getString("name")));
        List<FilterOptionResponse> methods = jdbc.query("""
                select pm.id,pm.name from payment_methods pm where pm.business_id=:businessId and pm.archived_at is null
                order by pm.display_order,pm.name
                """, params, (rs,row) -> new FilterOptionResponse(rs.getLong("id"), rs.getString("name")));
        return new ReportFilterOptionsResponse(products,categories,suppliers,customers,methods);
    }
    public ReportPageResponse reportPage(AnalyticsReportType type,
                                         Long businessId,
                                         List<Long> branchIds,
                                         ReportFilter filter,
                                         LocalDate today,
                                         Instant fromInstant,
                                         Instant toExclusiveInstant,
                                         ZoneId branchZone) {
        ReportQuery report = reportQuery(type, businessId, branchIds, filter, today, fromInstant, toExclusiveInstant, branchZone);
        long total = count("select count(*) from (" + report.sql() + ") analytics_rows", report.params());
        MapSqlParameterSource pageParams = copy(report.params()).addValue("limit", filter.size(), Types.INTEGER).addValue("offset", filter.offset(), Types.INTEGER);
        List<Map<String, Object>> rows = normalizeRows(jdbc.queryForList(
                report.sql() + " " + orderClause(type, filter.sort()) + " limit :limit offset :offset", pageParams));
        return new ReportPageResponse(type, columns(type), rows, total, filter.page(), filter.size());
    }

    public List<Map<String, Object>> reportChunk(AnalyticsReportType type,
                                                 Long businessId,
                                                 List<Long> branchIds,
                                                 ReportFilter filter,
                                                 LocalDate today,
                                                 Instant fromInstant,
                                                 Instant toExclusiveInstant,
                                                 ZoneId branchZone,
                                                 int offset,
                                                 int limit) {
        ReportQuery report = reportQuery(type, businessId, branchIds, filter, today, fromInstant, toExclusiveInstant, branchZone);
        MapSqlParameterSource params = copy(report.params()).addValue("limit", limit, Types.INTEGER).addValue("offset", offset, Types.INTEGER);
        return normalizeRows(jdbc.queryForList(report.sql() + " " + orderClause(type, filter.sort())
                + " limit :limit offset :offset", params));
    }

    public List<ReportColumnResponse> columns(AnalyticsReportType type) {
        return switch (type) {
            case CUSTOMER_OUTSTANDING_DUE -> cols(
                    c("invoice_id","Invoice ID","NUMBER"), c("customer","Customer","TEXT"), c("invoice_date","Invoice Date","DATE_TIME"),
                    c("due_date","Due Date","DATE"), c("original_payable","Original Payable","MONEY"), c("outstanding_due","Outstanding Due","MONEY"),
                    c("days_overdue","Days Overdue","NUMBER"), c("payment_status","Payment Status","TEXT"));
            case CUSTOMER_AGING -> cols(c("customer","Customer","TEXT"), c("current_due","Current","MONEY"), c("days_1_30","1-30 Days","MONEY"),
                    c("days_31_60","31-60 Days","MONEY"), c("days_61_90","61-90 Days","MONEY"), c("over_90","Over 90 Days","MONEY"), c("total_due","Total Due","MONEY"));
            case CUSTOMER_PAYMENT_COLLECTION -> cols(c("payment_id","Payment ID","NUMBER"), c("date_time","Date & Time","DATE_TIME"), c("customer","Customer","TEXT"),
                    c("method","Payment Method","TEXT"), c("channel","Channel","TEXT"), c("amount","Amount","MONEY"), c("reference","Reference","TEXT"));
            case CUSTOMER_STATEMENT -> cols(c("date_time","Date & Time","DATE_TIME"), c("branch","Branch","TEXT"), c("currency","Currency","TEXT"),
                    c("customer","Customer","TEXT"), c("reference","Reference","TEXT"), c("type","Type","TEXT"), c("debit","Debit","MONEY"),
                    c("credit","Credit","MONEY"), c("balance","Running Balance","MONEY"), c("source","Source","TEXT"));
            case CUSTOMER_CREDIT -> cols(c("customer","Customer","TEXT"), c("credit_balance","Available Credit","MONEY"), c("last_credit_at","Last Credit","DATE_TIME"));
            case SUPPLIER_DUE_PAYMENT, SUPPLIER_PAYMENT -> cols(c("payment_id","Payment ID","NUMBER"), c("date_time","Date & Time","DATE_TIME"), c("supplier","Supplier","TEXT"),
                    c("method","Payment Method","TEXT"), c("amount","Amount","MONEY"), c("reference","Reference","TEXT"));
            case PAYMENT_METHOD_SUMMARY -> cols(c("method","Payment Method","TEXT"), c("direction","Direction","TEXT"), c("transaction_count","Transactions","NUMBER"), c("amount","Amount","MONEY"));
            case FAILED_PAYMENT -> cols(c("payment_id","Payment ID","NUMBER"), c("date_time","Date & Time","DATE_TIME"), c("party","Party","TEXT"), c("party_name","Party Name","TEXT"),
                    c("method","Payment Method","TEXT"), c("amount","Amount","MONEY"), c("purpose","Purpose","TEXT"));
            case REVERSED_PAYMENT -> cols(c("payment_id","Payment ID","NUMBER"), c("date_time","Date & Time","DATE_TIME"), c("party_name","Party Name","TEXT"), c("method","Payment Method","TEXT"),
                    c("amount","Amount","MONEY"), c("reversal_of","Reversal Of","NUMBER"), c("reason","Reason","TEXT"));
            case CASH_VS_NON_CASH_COLLECTION -> cols(c("channel","Collection Channel","TEXT"), c("transaction_count","Transactions","NUMBER"), c("amount","Amount","MONEY"));
            case OPERATING_EXPENSE -> cols(c("expense_id","Expense ID","NUMBER"), c("date","Date","DATE"), c("category","Category","TEXT"), c("title","Title","TEXT"),
                    c("method","Payment Method","TEXT"), c("amount","Amount","MONEY"), c("payee","Payee","TEXT"));
            case EXPENSE_CATEGORY -> cols(c("category","Category","TEXT"), c("transaction_count","Transactions","NUMBER"), c("amount","Amount","MONEY"));
            case RECOVERABLE_DEPOSIT_ADVANCE -> cols(c("expense_id","Expense ID","NUMBER"), c("date","Date","DATE"), c("title","Title","TEXT"), c("payee","Payee","TEXT"), c("amount","Amount","MONEY"));
            case CASH_OUTFLOW -> cols(c("movement_id","Movement ID","NUMBER"), c("date_time","Date & Time","DATE_TIME"), c("movement_type","Movement Type","TEXT"),
                    c("source_module","Source Module","TEXT"), c("source_reference","Source Reference","TEXT"), c("amount","Amount","MONEY"));
            case CUSTOMER_REFUND -> cols(c("payment_id","Payment ID","NUMBER"), c("date_time","Date & Time","DATE_TIME"), c("customer","Customer","TEXT"),
                    c("method","Payment Method","TEXT"), c("amount","Amount","MONEY"), c("reference","Reference","TEXT"));
            case INVENTORY_LOSS -> cols(c("loss_id","Loss ID","NUMBER"), c("date","Date","DATE"), c("product","Product / Variant","TEXT"), c("batch","Batch","TEXT"),
                    c("reason","Reason","TEXT"), c("base_quantity","Base Quantity","NUMBER"), c("wac","WAC Snapshot","MONEY"), c("financial_loss","Financial Loss","MONEY"));
            case EXPIRY -> cols(c("product","Product","TEXT"), c("variant","Variant","TEXT"), c("batch","Batch","TEXT"), c("supplier","Supplier","TEXT"),
                    c("expiry_date","Expiry Date","DATE"), c("days_remaining","Days Remaining","NUMBER"), c("available_quantity","Available Quantity","NUMBER"), c("stock_value","Stock Value","MONEY"), c("status","Status","TEXT"));
            case NEAR_EXPIRY_STOCK -> cols(c("product","Product","TEXT"), c("variant","Variant","TEXT"), c("batch","Batch","TEXT"), c("supplier","Supplier","TEXT"),
                    c("expiry_date","Expiry Date","DATE"), c("days_remaining","Days Remaining","NUMBER"), c("available_quantity","Available Quantity","NUMBER"), c("stock_value","Stock Value","MONEY"));
            case EXPIRED_STOCK_VALUATION -> cols(c("product","Product","TEXT"), c("variant","Variant","TEXT"), c("batch","Batch","TEXT"), c("expiry_date","Expiry Date","DATE"),
                    c("expired_quantity","Expired Quantity","NUMBER"), c("wac","WAC","MONEY"), c("stock_value","Expired Stock Value","MONEY"));
            case EXPIRY_WASTAGE -> cols(c("loss_id","Loss ID","NUMBER"), c("date","Disposal Date","DATE"), c("product","Product / Variant","TEXT"), c("batch","Batch","TEXT"),
                    c("expiry_date","Expiry Date","DATE"), c("quantity","Quantity","NUMBER"), c("financial_loss","Expiry Loss","MONEY"), c("reason","Reason","TEXT"));
            case SUPPLIER_WISE_EXPIRY_LOSS -> cols(c("supplier","Supplier","TEXT"), c("loss_events","Loss Events","NUMBER"), c("quantity","Quantity","NUMBER"), c("financial_loss","Expiry Loss","MONEY"));
            case BRANCH_WISE_EXPIRY -> cols(c("branch","Branch","TEXT"), c("expiring_30_batches","Expiring ≤30d Batches","NUMBER"), c("expired_quantity","Expired Quantity","NUMBER"), c("expired_value","Expired Value","MONEY"));
            case EXPIRY_PURCHASE_RETURN -> cols(c("return_id","Return ID","NUMBER"), c("return_date","Return Date","DATE"), c("supplier","Supplier","TEXT"), c("product","Product / Variant","TEXT"),
                    c("batch","Batch","TEXT"), c("expiry_date","Expiry Date","DATE"), c("quantity","Return Quantity","NUMBER"), c("return_amount","Return Amount","MONEY"));
        };
    }

    private ReportQuery reportQuery(AnalyticsReportType type, Long businessId, List<Long> branchIds,
                                    ReportFilter filter, LocalDate today, Instant fromInstant, Instant toExclusive,
                                    ZoneId branchZone) {
        if (branchIds == null || branchIds.isEmpty()) throw new AnalyticsValidationException("No accessible branch is available for this report.");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("businessId", businessId, Types.BIGINT)
                .addValue("branchIds", branchIds)
                .addValue("from", jdbcInstant(fromInstant), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", jdbcInstant(toExclusive), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("fromDate", filter.from(), Types.DATE)
                .addValue("toDate", filter.to(), Types.DATE)
                .addValue("today", today, Types.DATE)
                .addValue("zoneName", branchZone.getId(), Types.VARCHAR)
                .addValue("d1", today.minusDays(1), Types.DATE)
                .addValue("d30", today.minusDays(30), Types.DATE)
                .addValue("d60", today.minusDays(60), Types.DATE)
                .addValue("d90", today.minusDays(90), Types.DATE)
                .addValue("future30", today.plusDays(30), Types.DATE)
                .addValue("productId", filter.productId(), Types.BIGINT)
                .addValue("categoryId", filter.categoryId(), Types.BIGINT)
                .addValue("supplierId", filter.supplierId(), Types.BIGINT)
                .addValue("customerId", filter.customerId(), Types.BIGINT)
                .addValue("paymentMethodId", filter.paymentMethodId(), Types.BIGINT)
                .addValue("batchNumber", filter.batchNumber(), Types.VARCHAR)
                .addValue("search", filter.query() == null ? null : "%" + filter.query().toLowerCase(Locale.ROOT) + "%", Types.VARCHAR);
        String expiryFilter = expiryFilter(filter.expiryStatus(), p, today);
        String search = filter.query() == null ? "" : " and ";

        String sql = switch (type) {
            case CUSTOMER_OUTSTANDING_DUE -> """
                    select s.id as invoice_id, coalesce(c.name,'Walk-in Customer') as customer,
                           s.confirmed_at as invoice_date, s.due_date as due_date, s.total_payable as original_payable,
                           s.due_amount as outstanding_due,
                           case when coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))>=:today then 0 else (:today-coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))) end as days_overdue,
                           s.payment_status as payment_status
                    from sales s join customers c on c.id=s.customer_id
                    where s.business_id=:businessId and s.branch_id in (:branchIds)
                      and s.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED') and s.due_amount>0
                      and s.confirmed_at>=:from and s.confirmed_at<:to
                      and (cast(:customerId as bigint) is null or s.customer_id=:customerId)
                    """ + (filter.query()==null ? "" : " and (lower(coalesce(c.name,'')) like :search or cast(s.id as varchar) like :search)");
            case CUSTOMER_AGING -> """
                    select coalesce(c.name,'Walk-in Customer') as customer,
                           coalesce(sum(case when coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))>=:today then s.due_amount else 0 end),0) as current_due,
                           coalesce(sum(case when coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date)) between :d30 and :d1 then s.due_amount else 0 end),0) as days_1_30,
                           coalesce(sum(case when coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))>=:d60 and coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))<:d30 then s.due_amount else 0 end),0) as days_31_60,
                           coalesce(sum(case when coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))>=:d90 and coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))<:d60 then s.due_amount else 0 end),0) as days_61_90,
                           coalesce(sum(case when coalesce(s.due_date,cast(s.confirmed_at at time zone :zoneName as date))<:d90 then s.due_amount else 0 end),0) as over_90,
                           coalesce(sum(s.due_amount),0) as total_due
                    from sales s join customers c on c.id=s.customer_id
                    where s.business_id=:businessId and s.branch_id in (:branchIds)
                      and s.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED') and s.due_amount>0
                      and (cast(:customerId as bigint) is null or s.customer_id=:customerId)
                    """ + (filter.query()==null ? "" : " and lower(coalesce(c.name,'')) like :search") + " group by c.id,c.name";
            case CUSTOMER_PAYMENT_COLLECTION -> """
                    select p.id as payment_id,p.confirmed_at as date_time,coalesce(c.name,'Customer') as customer,
                           p.payment_method_name_snapshot as method,case when p.cash_payment then 'Cash' else 'Non-Cash' end as channel,
                           p.amount as amount,p.transaction_reference as reference
                    from payments p left join customers c on c.id=p.customer_id
                    where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                      and p.financial_purpose='CUSTOMER_DUE_COLLECTION' and p.confirmed_at>=:from and p.confirmed_at<:to
                      and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                      and (cast(:paymentMethodId as bigint) is null or p.payment_method_id=:paymentMethodId)
                    """ + (filter.query()==null ? "" : " and (lower(coalesce(c.name,'')) like :search or lower(coalesce(p.transaction_reference,'')) like :search)");
            case CUSTOMER_STATEMENT -> customerStatementSql(filter);
            case CUSTOMER_CREDIT -> """
                    select customer,coalesce(sum(credit_amount),0) as credit_balance,max(credit_at) as last_credit_at
                    from (
                        select c.id as customer_id,coalesce(c.name,'Customer') as customer,
                               p.customer_credit_amount as credit_amount,p.confirmed_at as credit_at
                        from payments p join customers c on c.id=p.customer_id
                        where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                          and p.customer_settlement_type='CUSTOMER_CREDIT' and p.customer_credit_amount>0
                          and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                        union all
                        select c.id,coalesce(c.name,'Customer'),sr.customer_credit_amount,sr.confirmed_at
                        from sale_returns sr join customers c on c.id=sr.customer_id
                        where sr.business_id=:businessId and sr.branch_id in (:branchIds) and sr.status='CONFIRMED'
                          and sr.customer_credit_amount>0 and (cast(:customerId as bigint) is null or sr.customer_id=:customerId)
                    ) customer_credits
                    where 1=1
                    """ + (filter.query()==null ? "" : " and lower(customer) like :search")
                    + " group by customer_id,customer having coalesce(sum(credit_amount),0)>0";
            case SUPPLIER_DUE_PAYMENT, SUPPLIER_PAYMENT -> """
                    select p.id as payment_id,p.confirmed_at as date_time,coalesce(s.name,'Supplier') as supplier,
                           p.payment_method_name_snapshot as method,p.amount as amount,p.transaction_reference as reference
                    from payments p left join suppliers s on s.id=p.supplier_id
                    where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                      and p.financial_purpose='SUPPLIER_DUE_SETTLEMENT' and p.confirmed_at>=:from and p.confirmed_at<:to
                      and (cast(:supplierId as bigint) is null or p.supplier_id=:supplierId)
                      and (cast(:paymentMethodId as bigint) is null or p.payment_method_id=:paymentMethodId)
                    """ + (filter.query()==null ? "" : " and (lower(coalesce(s.name,'')) like :search or lower(coalesce(p.transaction_reference,'')) like :search)");
            case PAYMENT_METHOD_SUMMARY -> """
                    select p.payment_method_name_snapshot as method,p.direction as direction,count(*) as transaction_count,
                           coalesce(sum(p.amount),0) as amount
                    from payments p where p.business_id=:businessId and p.branch_id in (:branchIds)
                      and p.status='CONFIRMED' and p.confirmed_at>=:from and p.confirmed_at<:to
                      and (cast(:paymentMethodId as bigint) is null or p.payment_method_id=:paymentMethodId)
                    group by p.payment_method_name_snapshot,p.direction
                    """;
            case FAILED_PAYMENT -> """
                    select p.id as payment_id,p.created_at as date_time,p.party_type as party,
                           coalesce(c.name,s.name,'Unknown') as party_name,p.payment_method_name_snapshot as method,
                           p.amount as amount,p.financial_purpose as purpose
                    from payments p left join customers c on c.id=p.customer_id left join suppliers s on s.id=p.supplier_id
                    where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='FAILED'
                      and p.created_at>=:from and p.created_at<:to
                    """ + (filter.query()==null ? "" : " and lower(coalesce(c.name,s.name,'')) like :search");
            case REVERSED_PAYMENT -> """
                    select p.id as payment_id,p.confirmed_at as date_time,
                           coalesce(c.name,s.name,'Unknown') as party_name,p.payment_method_name_snapshot as method,
                           p.amount as amount,p.reversal_of_payment_id as reversal_of,p.reversal_reason as reason
                    from payments p left join customers c on c.id=p.customer_id left join suppliers s on s.id=p.supplier_id
                    where p.business_id=:businessId and p.branch_id in (:branchIds)
                      and p.status='CONFIRMED' and p.reversal_of_payment_id is not null
                      and p.confirmed_at>=:from and p.confirmed_at<:to
                    """ + (filter.query()==null ? "" : " and lower(coalesce(c.name,s.name,'')) like :search");
            case CASH_VS_NON_CASH_COLLECTION -> """
                    select case when p.cash_payment then 'Cash' else 'Non-Cash' end as channel,
                           count(*) as transaction_count,coalesce(sum(p.amount),0) as amount
                    from payments p where p.business_id=:businessId and p.branch_id in (:branchIds)
                      and p.status='CONFIRMED' and p.direction='INFLOW' and p.financial_purpose='CUSTOMER_DUE_COLLECTION'
                      and p.confirmed_at>=:from and p.confirmed_at<:to
                    group by p.cash_payment
                    """;
            case OPERATING_EXPENSE -> """
                    select e.id as expense_id,e.expense_date as date,coalesce(e.category_name_snapshot,'Uncategorized') as category,
                           e.title as title,e.payment_method_snapshot as method,e.amount as amount,e.payee as payee
                    from expenses e where e.business_id=:businessId and e.branch_id in (:branchIds)
                      and e.status='POSTED' and e.classification='OPERATING_EXPENSE'
                      and e.expense_date>=:fromDate and e.expense_date<=:toDate
                      and (cast(:categoryId as bigint) is null or e.category_id=:categoryId)
                      and (cast(:paymentMethodId as bigint) is null or e.payment_method_id=:paymentMethodId)
                    """ + (filter.query()==null ? "" : " and (lower(e.title) like :search or lower(coalesce(e.category_name_snapshot,'')) like :search or lower(coalesce(e.payee,'')) like :search)");
            case EXPENSE_CATEGORY -> """
                    select coalesce(e.category_name_snapshot,'Uncategorized') as category,count(*) as transaction_count,
                           coalesce(sum(e.amount),0) as amount
                    from expenses e where e.business_id=:businessId and e.branch_id in (:branchIds)
                      and e.status='POSTED' and e.classification='OPERATING_EXPENSE'
                      and e.expense_date>=:fromDate and e.expense_date<=:toDate
                      and (cast(:categoryId as bigint) is null or e.category_id=:categoryId)
                    group by coalesce(e.category_name_snapshot,'Uncategorized')
                    """;
            case RECOVERABLE_DEPOSIT_ADVANCE -> """
                    select e.id as expense_id,e.expense_date as date,e.title as title,e.payee as payee,e.amount as amount
                    from expenses e where e.business_id=:businessId and e.branch_id in (:branchIds)
                      and e.status='POSTED' and e.classification='RECOVERABLE_DEPOSIT_ADVANCE'
                      and e.expense_date>=:fromDate and e.expense_date<=:toDate
                    """ + (filter.query()==null ? "" : " and (lower(e.title) like :search or lower(coalesce(e.payee,'')) like :search)");
            case CASH_OUTFLOW -> """
                    select cm.id as movement_id,cm.posted_at as date_time,cm.movement_type as movement_type,
                           cm.source_module as source_module,cm.source_reference as source_reference,cm.amount as amount
                    from cash_movements cm where cm.business_id=:businessId and cm.branch_id in (:branchIds)
                      and cm.status='POSTED' and cm.direction='OUTFLOW' and cm.posted_at>=:from and cm.posted_at<:to
                    """ + (filter.query()==null ? "" : " and (lower(coalesce(cm.source_reference,'')) like :search or lower(coalesce(cm.note,'')) like :search)");
            case CUSTOMER_REFUND -> """
                    select p.id as payment_id,p.confirmed_at as date_time,coalesce(c.name,'Customer') as customer,
                           p.payment_method_name_snapshot as method,p.amount as amount,p.transaction_reference as reference
                    from payments p left join customers c on c.id=p.customer_id
                    where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                      and p.financial_purpose='CUSTOMER_REFUND' and p.confirmed_at>=:from and p.confirmed_at<:to
                      and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                    """ + (filter.query()==null ? "" : " and lower(coalesce(c.name,'')) like :search");
            case INVENTORY_LOSS -> """
                    select il.id as loss_id,il.disposal_date as date,
                           (p.name || ' · ' || pv.variant_name) as product,coalesce(pb.batch_number,'—') as batch,
                           il.reason as reason,il.base_quantity as base_quantity,il.weighted_average_cost_snapshot as wac,
                           il.financial_loss as financial_loss
                    from inventory_losses il join product_variants pv on pv.id=il.product_variant_id
                    join products p on p.id=pv.product_id left join product_batches pb on pb.id=il.product_batch_id
                    where il.business_id=:businessId and il.branch_id in (:branchIds) and il.status='POSTED'
                      and il.disposal_date>=:fromDate and il.disposal_date<=:toDate
                      and (cast(:productId as bigint) is null or p.id=:productId) and (cast(:categoryId as bigint) is null or p.category_id=:categoryId)
                    """ + (filter.query()==null ? "" : " and (lower(p.name) like :search or lower(pv.variant_name) like :search or lower(il.reason) like :search)");
            case EXPIRY -> expiryBaseSql(filter, expiryReportFilter(filter, expiryFilter), false);
            case NEAR_EXPIRY_STOCK -> expiryBaseSql(filter,
                    (filter.expiryStatus() == null
                            ? " and pb.expiry_date>=:today and pb.expiry_date<=:future30 "
                            : expiryFilter) + " and pb.status='ACTIVE' ", true);
            case EXPIRED_STOCK_VALUATION -> """
                    select p.name as product,pv.variant_name as variant,pb.batch_number as batch,pb.expiry_date as expiry_date,
                           pb.available_base_quantity as expired_quantity,coalesce(bps.weighted_average_cost,0) as wac,
                           pb.available_base_quantity*coalesce(bps.weighted_average_cost,0) as stock_value
                    from product_batches pb join product_variants pv on pv.id=pb.product_variant_id join products p on p.id=pv.product_id
                    left join branch_product_stocks bps on bps.business_id=pb.business_id and bps.branch_id=pb.branch_id and bps.product_variant_id=pb.product_variant_id
                    where pb.business_id=:businessId and pb.branch_id in (:branchIds) and pb.available_base_quantity>0
                      and (pb.expiry_date<:today or pb.status='EXPIRED')
                      and (cast(:productId as bigint) is null or p.id=:productId) and (cast(:categoryId as bigint) is null or p.category_id=:categoryId)
                      and (cast(:supplierId as bigint) is null or pb.supplier_id=:supplierId)
                      and (cast(:batchNumber as varchar) is null or lower(pb.batch_number) like lower('%' || :batchNumber || '%'))
                    """ + (filter.query()==null ? "" : " and (lower(p.name) like :search or lower(pv.variant_name) like :search or lower(pb.batch_number) like :search)");
            case EXPIRY_WASTAGE -> """
                    select il.id as loss_id,il.disposal_date as date,(p.name || ' · ' || pv.variant_name) as product,
                           coalesce(pb.batch_number,'—') as batch,pb.expiry_date as expiry_date,il.base_quantity as quantity,
                           il.financial_loss as financial_loss,il.reason as reason
                    from inventory_losses il join product_variants pv on pv.id=il.product_variant_id join products p on p.id=pv.product_id
                    left join product_batches pb on pb.id=il.product_batch_id
                    where il.business_id=:businessId and il.branch_id in (:branchIds) and il.status='POSTED'
                      and il.disposal_date>=:fromDate and il.disposal_date<=:toDate
                      and (lower(il.reason) like '%expir%' or (pb.expiry_date is not null and pb.expiry_date<=il.disposal_date))
                      and (cast(:productId as bigint) is null or p.id=:productId) and (cast(:categoryId as bigint) is null or p.category_id=:categoryId)
                      and (cast(:supplierId as bigint) is null or pb.supplier_id=:supplierId)
                    """ + (filter.query()==null ? "" : " and (lower(p.name) like :search or lower(coalesce(pb.batch_number,'')) like :search or lower(il.reason) like :search)");
            case SUPPLIER_WISE_EXPIRY_LOSS -> """
                    select coalesce(s.name,'Unknown Supplier') as supplier,count(il.id) as loss_events,
                           coalesce(sum(il.base_quantity),0) as quantity,coalesce(sum(il.financial_loss),0) as financial_loss
                    from inventory_losses il
                    left join product_batches pb on pb.id=il.product_batch_id
                    join product_variants pv on pv.id=il.product_variant_id
                    join products p on p.id=pv.product_id
                    left join suppliers s on s.id=pb.supplier_id
                    where il.business_id=:businessId and il.branch_id in (:branchIds) and il.status='POSTED'
                      and il.disposal_date>=:fromDate and il.disposal_date<=:toDate
                      and (lower(il.reason) like '%expir%' or (pb.expiry_date is not null and pb.expiry_date<=il.disposal_date))
                      and (cast(:productId as bigint) is null or p.id=:productId) and (cast(:categoryId as bigint) is null or p.category_id=:categoryId)
                      and (cast(:supplierId as bigint) is null or pb.supplier_id=:supplierId)
                      and (cast(:batchNumber as varchar) is null or lower(coalesce(pb.batch_number,'')) like lower('%' || :batchNumber || '%'))
                    group by s.id,s.name
                    """;
            case BRANCH_WISE_EXPIRY -> """
                    select b.name as branch,
                           count(case when pb.available_base_quantity>0 and pb.status='ACTIVE' and pb.expiry_date>=:today and pb.expiry_date<=:future30 then 1 end) as expiring_30_batches,
                           coalesce(sum(case when pb.available_base_quantity>0 and (pb.expiry_date<:today or pb.status='EXPIRED') then pb.available_base_quantity else 0 end),0) as expired_quantity,
                           coalesce(sum(case when pb.available_base_quantity>0 and (pb.expiry_date<:today or pb.status='EXPIRED') then pb.available_base_quantity*coalesce(bps.weighted_average_cost,0) else 0 end),0) as expired_value
                    from branches b
                    left join product_batches pb on pb.business_id=b.business_id and pb.branch_id=b.id
                    left join product_variants pv on pv.id=pb.product_variant_id
                    left join products p on p.id=pv.product_id
                    left join branch_product_stocks bps on bps.business_id=pb.business_id and bps.branch_id=pb.branch_id and bps.product_variant_id=pb.product_variant_id
                    where b.business_id=:businessId and b.id in (:branchIds)
                      and (cast(:productId as bigint) is null or p.id=:productId) and (cast(:categoryId as bigint) is null or p.category_id=:categoryId)
                      and (cast(:supplierId as bigint) is null or pb.supplier_id=:supplierId)
                      and (cast(:batchNumber as varchar) is null or lower(coalesce(pb.batch_number,'')) like lower('%' || :batchNumber || '%'))
                    group by b.id,b.name
                    """;
            case EXPIRY_PURCHASE_RETURN -> """
                    select pr.id as return_id,pr.return_date as return_date,coalesce(s.name,'Supplier') as supplier,
                           (p.name || ' · ' || pv.variant_name) as product,coalesce(pb.batch_number,'—') as batch,pb.expiry_date as expiry_date,
                           pri.base_quantity as quantity,pri.return_amount as return_amount
                    from purchase_returns pr join purchase_return_items pri on pri.purchase_return_id=pr.id
                    join product_variants pv on pv.id=pri.product_variant_id join products p on p.id=pv.product_id
                    left join product_batches pb on pb.id=pri.product_batch_id left join suppliers s on s.id=pr.supplier_id
                    where pr.business_id=:businessId and pr.branch_id in (:branchIds) and pr.status='CONFIRMED'
                      and pr.return_date>=:fromDate and pr.return_date<=:toDate and pb.expiry_date is not null
                      and (cast(:productId as bigint) is null or p.id=:productId) and (cast(:categoryId as bigint) is null or p.category_id=:categoryId)
                      and (cast(:supplierId as bigint) is null or pr.supplier_id=:supplierId)
                      and (cast(:batchNumber as varchar) is null or lower(coalesce(pb.batch_number,'')) like lower('%' || :batchNumber || '%'))
                    """ + (filter.query()==null ? "" : " and (lower(p.name) like :search or lower(coalesce(pb.batch_number,'')) like :search or lower(coalesce(s.name,'')) like :search)");
        };
        return new ReportQuery(sql, p);
    }

    private String customerStatementSql(ReportFilter filter) {
        String union = """
                select s.confirmed_at as date_time,c.name as customer,('SALE-' || cast(s.id as varchar)) as reference,
                       'CONFIRMED_INVOICE' as type,s.total_payable as debit,cast(0 as numeric) as credit,'SELL' as source,c.id as customer_id,s.branch_id as branch_id
                from sales s join customers c on c.id=s.customer_id
                where s.business_id=:businessId and s.branch_id in (:branchIds) and s.status in ('CONFIRMED','PARTIALLY_RETURNED','RETURNED')
                  and s.confirmed_at>=:from and s.confirmed_at<:to and (cast(:customerId as bigint) is null or s.customer_id=:customerId)
                union all
                select sr.confirmed_at,c.name,sr.reference_number,'SALES_RETURN_OR_CREDIT',cast(0 as numeric),sr.total_return_amount,'SELL',c.id,sr.branch_id
                from sale_returns sr join customers c on c.id=sr.customer_id
                where sr.business_id=:businessId and sr.branch_id in (:branchIds) and sr.status in ('CONFIRMED','REVERSED')
                  and sr.confirmed_at>=:from and sr.confirmed_at<:to and (cast(:customerId as bigint) is null or sr.customer_id=:customerId)
                union all
                select sr.reversed_at,c.name,sr.reference_number,'SALES_RETURN_REVERSAL',sr.total_return_amount,cast(0 as numeric),'SELL',c.id,sr.branch_id
                from sale_returns sr join customers c on c.id=sr.customer_id
                where sr.business_id=:businessId and sr.branch_id in (:branchIds) and sr.status='REVERSED' and sr.reversed_at is not null
                  and sr.reversed_at>=:from and sr.reversed_at<:to and (cast(:customerId as bigint) is null or sr.customer_id=:customerId)
                union all
                select p.confirmed_at,c.name,('PAY-' || cast(p.id as varchar)),
                       case when pa.effect='REVERSE' then 'PAYMENT_REVERSAL' else 'PAYMENT_ALLOCATION' end,
                       case when pa.effect='REVERSE' then pa.amount else cast(0 as numeric) end,
                       case when pa.effect='APPLY' then pa.amount else cast(0 as numeric) end,'PAYMENT',c.id,p.branch_id
                from payments p join payment_allocations pa on pa.payment_id=p.id join customers c on c.id=p.customer_id
                where p.business_id=:businessId and p.branch_id in (:branchIds) and pa.invoice_type='SALE'
                  and p.confirmed_at>=:from and p.confirmed_at<:to and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                union all
                select p.confirmed_at,c.name,('PAY-' || cast(p.id as varchar)),'CUSTOMER_REFUND',p.amount,cast(0 as numeric),'PAYMENT',c.id,p.branch_id
                from payments p join customers c on c.id=p.customer_id
                where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                  and p.financial_purpose='CUSTOMER_REFUND' and p.confirmed_at>=:from and p.confirmed_at<:to
                  and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                union all
                select p.confirmed_at,c.name,('PAY-' || cast(p.id as varchar)),'CUSTOMER_REFUND_REVERSAL',cast(0 as numeric),p.amount,'PAYMENT',c.id,p.branch_id
                from payments p join customers c on c.id=p.customer_id
                where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                  and p.financial_purpose='CUSTOMER_REFUND_REVERSAL' and p.confirmed_at>=:from and p.confirmed_at<:to
                  and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                union all
                select p.confirmed_at,c.name,('PAY-' || cast(p.id as varchar)),'CUSTOMER_CREDIT',cast(0 as numeric),p.customer_credit_amount,'PAYMENT',c.id,p.branch_id
                from payments p join customers c on c.id=p.customer_id
                where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                  and p.customer_credit_amount>0 and p.confirmed_at>=:from and p.confirmed_at<:to
                  and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                union all
                select p.confirmed_at,c.name,('PAY-' || cast(p.id as varchar)),'CUSTOMER_CREDIT_REVERSAL',op.customer_credit_amount,cast(0 as numeric),'PAYMENT',c.id,p.branch_id
                from payments p join payments op on op.id=p.reversal_of_payment_id join customers c on c.id=p.customer_id
                where p.business_id=:businessId and p.branch_id in (:branchIds) and p.status='CONFIRMED'
                  and p.reversal_of_payment_id is not null and op.customer_credit_amount>0
                  and p.confirmed_at>=:from and p.confirmed_at<:to and (cast(:customerId as bigint) is null or p.customer_id=:customerId)
                """;
        String sql = "select cs.date_time,b.name as branch,b.currency as currency,cs.customer,cs.reference,cs.type,cs.debit,cs.credit,cs.balance,cs.source from ("
                + "select date_time,customer,reference,type,debit,credit,source,customer_id,branch_id,"
                + "sum(debit-credit) over (partition by customer_id,branch_id order by date_time,reference,type rows between unbounded preceding and current row) as balance "
                + "from (" + union + ") raw_statement) cs join branches b on b.id=cs.branch_id where 1=1";
        if (filter.query()!=null) sql += " and (lower(customer) like :search or lower(reference) like :search or lower(type) like :search)";
        return sql;
    }

    private String expiryBaseSql(ReportFilter filter, String expiryFilter, boolean forceAvailable) {
        String sql = """
                select p.name as product,pv.variant_name as variant,pb.batch_number as batch,coalesce(s.name,'—') as supplier,
                       pb.expiry_date as expiry_date,
                       case when pb.expiry_date is null then null else (pb.expiry_date-:today) end as days_remaining,
                       pb.available_base_quantity as available_quantity,
                       pb.available_base_quantity*coalesce(bps.weighted_average_cost,0) as stock_value,
                       case when pb.expiry_date is null then 'MISSING EXPIRY'
                            when pb.expiry_date<:today or pb.status='EXPIRED' then 'EXPIRED'
                            when pb.expiry_date=:today then 'EXPIRING TODAY'
                            when pb.expiry_date<=:future30 then 'NEAR EXPIRY' else pb.status end as status
                from product_batches pb join product_variants pv on pv.id=pb.product_variant_id join products p on p.id=pv.product_id
                left join suppliers s on s.id=pb.supplier_id
                left join branch_product_stocks bps on bps.business_id=pb.business_id and bps.branch_id=pb.branch_id and bps.product_variant_id=pb.product_variant_id
                where pb.business_id=:businessId and pb.branch_id in (:branchIds) and p.track_expiry=true
                  and (cast(:productId as bigint) is null or p.id=:productId) and (cast(:categoryId as bigint) is null or p.category_id=:categoryId)
                  and (cast(:supplierId as bigint) is null or pb.supplier_id=:supplierId)
                  and (cast(:batchNumber as varchar) is null or lower(pb.batch_number) like lower('%' || :batchNumber || '%'))
                """ + expiryFilter;
        if (forceAvailable) sql += " and pb.available_base_quantity>0";
        if (filter.query()!=null) sql += " and (lower(p.name) like :search or lower(pv.variant_name) like :search or lower(pb.batch_number) like :search or lower(coalesce(s.name,'')) like :search)";
        return sql;
    }

    private String expiryReportFilter(ReportFilter filter, String statusFilter) {
        if (filter.expiryStatus() != null) {
            String normalized = filter.expiryStatus().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (normalized.equals("MISSING") || normalized.equals("MISSING_EXPIRY_INFORMATION")) return statusFilter;
        }
        return statusFilter + " and pb.expiry_date>=:fromDate and pb.expiry_date<=:toDate ";
    }

    private String expiryFilter(String value, MapSqlParameterSource params, LocalDate today) {
        if (value == null) return "";
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "EXPIRED" -> " and (pb.expiry_date<:today or pb.status='EXPIRED') ";
            case "TODAY", "EXPIRING_TODAY" -> " and pb.expiry_date=:today ";
            case "7", "WITHIN_7_DAYS" -> addFuture(params, today, 7);
            case "15", "WITHIN_15_DAYS" -> addFuture(params, today, 15);
            case "30", "WITHIN_30_DAYS" -> addFuture(params, today, 30);
            case "60", "WITHIN_60_DAYS" -> addFuture(params, today, 60);
            case "90", "WITHIN_90_DAYS" -> addFuture(params, today, 90);
            case "MISSING", "MISSING_EXPIRY_INFORMATION" -> " and pb.expiry_date is null ";
            default -> throw new AnalyticsValidationException("Unsupported expiry status filter: " + value);
        };
    }

    private String addFuture(MapSqlParameterSource params, LocalDate today, int days) {
        params.addValue("expiryFuture", today.plusDays(days), Types.DATE);
        return " and pb.expiry_date>=:today and pb.expiry_date<=:expiryFuture ";
    }

    private String orderClause(AnalyticsReportType type, String requested) {
        String sort = requested == null ? "date_desc" : requested.toLowerCase(Locale.ROOT);
        return switch (sort) {
            case "date_asc" -> " order by " + dateColumn(type) + " asc";
            case "date_desc" -> " order by " + dateColumn(type) + " desc";
            case "amount_desc" -> " order by " + amountColumn(type) + " desc";
            case "amount_asc" -> " order by " + amountColumn(type) + " asc";
            case "name_asc" -> " order by " + nameColumn(type) + " asc";
            case "name_desc" -> " order by " + nameColumn(type) + " desc";
            default -> throw new AnalyticsValidationException("Unsupported report sort: " + requested);
        };
    }

    private String dateColumn(AnalyticsReportType type) {
        return switch (type) {
            case CUSTOMER_OUTSTANDING_DUE -> "invoice_date";
            case CUSTOMER_PAYMENT_COLLECTION, SUPPLIER_DUE_PAYMENT, SUPPLIER_PAYMENT, FAILED_PAYMENT,
                 REVERSED_PAYMENT, CUSTOMER_REFUND -> "date_time";
            case CUSTOMER_STATEMENT -> "date_time";
            case OPERATING_EXPENSE, RECOVERABLE_DEPOSIT_ADVANCE, INVENTORY_LOSS, EXPIRY_WASTAGE -> "date";
            case CASH_OUTFLOW -> "date_time";
            case EXPIRY, NEAR_EXPIRY_STOCK, EXPIRED_STOCK_VALUATION -> "expiry_date";
            case EXPIRY_PURCHASE_RETURN -> "return_date";
            default -> nameColumn(type);
        };
    }

    private String nameColumn(AnalyticsReportType type) {
        return switch (type) {
            case CUSTOMER_OUTSTANDING_DUE, CUSTOMER_AGING, CUSTOMER_PAYMENT_COLLECTION, CUSTOMER_STATEMENT,
                 CUSTOMER_CREDIT, CUSTOMER_REFUND -> "customer";
            case SUPPLIER_DUE_PAYMENT, SUPPLIER_PAYMENT, EXPIRY_PURCHASE_RETURN, SUPPLIER_WISE_EXPIRY_LOSS -> "supplier";
            case PAYMENT_METHOD_SUMMARY -> "method";
            case FAILED_PAYMENT, REVERSED_PAYMENT -> "party_name";
            case CASH_VS_NON_CASH_COLLECTION -> "channel";
            case OPERATING_EXPENSE, RECOVERABLE_DEPOSIT_ADVANCE -> "title";
            case EXPENSE_CATEGORY -> "category";
            case CASH_OUTFLOW -> "source_reference";
            case INVENTORY_LOSS, EXPIRY, NEAR_EXPIRY_STOCK, EXPIRED_STOCK_VALUATION, EXPIRY_WASTAGE -> "product";
            case BRANCH_WISE_EXPIRY -> "branch";
        };
    }

    private String amountColumn(AnalyticsReportType type) {
        return switch (type) {
            case CUSTOMER_OUTSTANDING_DUE -> "outstanding_due";
            case CUSTOMER_AGING -> "total_due";
            case CUSTOMER_PAYMENT_COLLECTION, SUPPLIER_DUE_PAYMENT, SUPPLIER_PAYMENT, PAYMENT_METHOD_SUMMARY,
                 FAILED_PAYMENT, REVERSED_PAYMENT, CASH_VS_NON_CASH_COLLECTION, OPERATING_EXPENSE,
                 EXPENSE_CATEGORY, RECOVERABLE_DEPOSIT_ADVANCE, CASH_OUTFLOW, CUSTOMER_REFUND -> "amount";
            case CUSTOMER_CREDIT -> "credit_balance";
            case CUSTOMER_STATEMENT -> "greatest(debit,credit)";
            case INVENTORY_LOSS, EXPIRY_WASTAGE, SUPPLIER_WISE_EXPIRY_LOSS -> "financial_loss";
            case EXPIRY, NEAR_EXPIRY_STOCK, EXPIRED_STOCK_VALUATION -> "stock_value";
            case BRANCH_WISE_EXPIRY -> "expired_value";
            case EXPIRY_PURCHASE_RETURN -> "return_amount";
        };
    }

    private BigDecimal cashBalanceAt(Long businessId, Long branchId, Instant asOfExclusive) {
        MapSqlParameterSource params = businessBranchParams(businessId, branchId).addValue("asOf", jdbcInstant(asOfExclusive), Types.TIMESTAMP_WITH_TIMEZONE);
        return money("""
                select coalesce(
                    (select cm.balance_after from cash_movements cm
                     where cm.business_id=:businessId and cm.branch_id=:branchId and cm.status='POSTED'
                       and cm.posted_at<:asOf
                     order by cm.posted_at desc,cm.id desc limit 1),
                    (select max(c.opening_balance) from cashbooks c
                     where c.business_id=:businessId and c.branch_id=:branchId),
                    0)
                """, params);
    }

    private BigDecimal saleReturns(MapSqlParameterSource params) {
        return money("""
                select coalesce(sum(sr.total_return_amount),0) from sale_returns sr
                where sr.business_id=:businessId and sr.branch_id=:branchId and sr.status='CONFIRMED'
                  and sr.confirmed_at>=:from and sr.confirmed_at<:to
                """, params);
    }

    private BigDecimal returnedCogs(MapSqlParameterSource params) {
        return money("""
                select coalesce(sum(sri.preserved_financial_cost_snapshot*sri.base_quantity),0)
                from sale_returns sr join sale_return_items sri on sri.sale_return_id=sr.id
                where sr.business_id=:businessId and sr.branch_id=:branchId and sr.status='CONFIRMED'
                  and sr.confirmed_at>=:from and sr.confirmed_at<:to
                """, params);
    }

    private BigDecimal operatingExpense(Long businessId, Long branchId, LocalDate fromDate, LocalDate toDate) {
        MapSqlParameterSource p = businessBranchParams(businessId, branchId)
                .addValue("fromDate", fromDate, Types.DATE).addValue("toDate", toDate, Types.DATE);
        return money("""
                select coalesce(sum(e.amount),0) from expenses e
                where e.business_id=:businessId and e.branch_id=:branchId and e.status='POSTED'
                  and e.classification='OPERATING_EXPENSE' and e.expense_date>=:fromDate and e.expense_date<=:toDate
                """, p);
    }

    private BigDecimal inventoryLoss(Long businessId, Long branchId, LocalDate fromDate, LocalDate toDate) {
        MapSqlParameterSource p = businessBranchParams(businessId, branchId)
                .addValue("fromDate", fromDate, Types.DATE).addValue("toDate", toDate, Types.DATE);
        return money("""
                select coalesce(sum(il.financial_loss),0) from inventory_losses il
                where il.business_id=:businessId and il.branch_id=:branchId and il.status='POSTED'
                  and il.disposal_date>=:fromDate and il.disposal_date<=:toDate
                """, p);
    }

    private MapSqlParameterSource periodParams(Long businessId, Long branchId, Instant from, Instant to) {
        return businessBranchParams(businessId, branchId)
                .addValue("from", jdbcInstant(from), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", jdbcInstant(to), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private MapSqlParameterSource businessBranchParams(Long businessId, Long branchId) {
        return new MapSqlParameterSource()
                .addValue("businessId", businessId, Types.BIGINT)
                .addValue("branchId", branchId, Types.BIGINT);
    }

    private MapSqlParameterSource copy(MapSqlParameterSource source) {
        MapSqlParameterSource target = new MapSqlParameterSource();
        for (String parameterName : source.getParameterNames()) {
            Object value = source.getValue(parameterName);
            int sqlType = source.getSqlType(parameterName);
            String typeName = source.getTypeName(parameterName);
            if (sqlType == SqlParameterSource.TYPE_UNKNOWN) {
                target.addValue(parameterName, value);
            } else if (typeName == null) {
                target.addValue(parameterName, value, sqlType);
            } else {
                target.addValue(parameterName, value, sqlType, typeName);
            }
        }
        return target;
    }

    private OffsetDateTime jdbcInstant(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private BigDecimal money(String sql, MapSqlParameterSource params) {
        BigDecimal value = jdbc.queryForObject(sql, params, BigDecimal.class);
        return decimal(value);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Number value = jdbc.queryForObject(sql, params, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private RankedMetricResponse ranked(Long id, String label, String secondary, BigDecimal value, BigDecimal quantity) {
        return new RankedMetricResponse(id, label, secondary, decimal(value), quantity == null ? BigDecimal.ZERO : quantity);
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? zero() : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() { return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP); }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), normalizeValue(value)));
            return Collections.unmodifiableMap(normalized);
        }).toList();
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Date date) return date.toLocalDate();
        return value;
    }

    private ReportColumnResponse c(String key, String heading, String type) {
        return new ReportColumnResponse(key, heading, com.spark.falcon.shared.export.ExportColumn.ValueType.valueOf(type));
    }

    private List<ReportColumnResponse> cols(ReportColumnResponse... values) { return List.of(values); }

    private record ReportQuery(String sql, MapSqlParameterSource params) { }

    private static final class DayAccumulator {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal returns = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        BigDecimal returnedCogs = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
    }
}
