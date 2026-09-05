package com.spark.falcon.analytics.service;

import com.spark.falcon.analytics.dto.*;
import com.spark.falcon.analytics.exception.AnalyticsValidationException;
import com.spark.falcon.analytics.repository.AnalyticsReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int LEADER_LIMIT = 5;
    private static final int BIRTHDAY_WINDOW_DAYS = 30;
    private static final int LOGIN_LOG_LIMIT = 40;

    private final AnalyticsReadRepository repository;

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(AnalyticsContextResponse context,
                                              LocalDate requestedFrom,
                                              LocalDate requestedTo,
                                              YearMonth requestedMonth) {
        ZoneId zone = zone(context.reportBranch().timeZone());
        LocalDate today = LocalDate.now(zone);
        LocalDate to = requestedTo == null ? today : requestedTo;
        LocalDate from = requestedFrom == null ? to.withDayOfMonth(1) : requestedFrom;
        validateRange(from, to, 366);

        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(zone).toInstant();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Long businessId = context.setup().businessId();
        Long branchId = context.reportBranch().branchId();

        FinancialSummaryResponse financial = repository.financialSummary(
                businessId, branchId, fromInstant, toExclusive, todayStart, todayEnd, from, to, today);
        YearMonth currentMonth = YearMonth.from(today);
        ExpirySummaryResponse expiry = repository.expirySummary(businessId, branchId, today,
                currentMonth.atDay(1), currentMonth.plusMonths(1).atDay(1));

        YearMonth chartMonth = requestedMonth == null ? YearMonth.from(to) : requestedMonth;
        LocalDate chartFrom = chartMonth.atDay(1);
        LocalDate chartTo = chartMonth.atEndOfMonth();
        Instant chartFromInstant = chartFrom.atStartOfDay(zone).toInstant();
        Instant chartToInstant = chartTo.plusDays(1).atStartOfDay(zone).toInstant();

        return new AnalyticsOverviewResponse(from, to, financial, expiry,
                repository.topProducts(businessId, branchId, fromInstant, toExclusive, LEADER_LIMIT),
                repository.topCustomers(businessId, branchId, fromInstant, toExclusive, LEADER_LIMIT),
                repository.leadingSuppliers(businessId, branchId, from, to, LEADER_LIMIT),
                repository.topBrands(businessId, branchId, fromInstant, toExclusive, LEADER_LIMIT),
                birthdays(repository.customerBirthdays(businessId), today, zone),
                repository.dailyPerformance(businessId, branchId, chartFromInstant, chartToInstant, chartFrom, chartTo, zone),
                repository.paymentMix(businessId, branchId, fromInstant, toExclusive),
                loginLogs(repository.loginAuditRows(businessId, LOGIN_LOG_LIMIT)));
    }

    public ZoneId zone(AnalyticsContextResponse context) {
        return zone(context.reportBranch().timeZone());
    }

    public void validateRange(LocalDate from, LocalDate to, int maximumDays) {
        if (from == null || to == null) throw new AnalyticsValidationException("From and To dates are required.");
        if (from.isAfter(to)) throw new AnalyticsValidationException("From date must not be after To date.");
        if (ChronoUnit.DAYS.between(from, to) > maximumDays) {
            throw new AnalyticsValidationException("The selected date range is too large. Choose " + maximumDays + " days or fewer.");
        }
    }

    private List<CustomerBirthdayResponse> birthdays(List<Map<String, Object>> rows, LocalDate today, ZoneId zone) {
        return rows.stream().map(row -> {
                    Long id = number(row.get("id"));
                    String name = Objects.toString(row.get("customer_name"), "Customer");
                    LocalDate birth = localDate(row.get("date_of_birth"));
                    Instant created = instant(row.get("created_at"));
                    LocalDate next = nextBirthday(birth, today);
                    return new CustomerBirthdayResponse(id, name, birth, next,
                            ChronoUnit.DAYS.between(today, next),
                            created == null ? 0 : ChronoUnit.DAYS.between(created.atZone(zone).toLocalDate(), today));
                })
                .filter(item -> item.daysUntilBirthday() >= 0 && item.daysUntilBirthday() <= BIRTHDAY_WINDOW_DAYS)
                .sorted(Comparator.comparingLong(CustomerBirthdayResponse::daysUntilBirthday)
                        .thenComparing(CustomerBirthdayResponse::customerName))
                .limit(8)
                .toList();
    }

    private List<LoginLogResponse> loginLogs(List<Map<String, Object>> rows) {
        Map<String, String> currentStatus = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String actor = Objects.toString(row.get("actor_identifier"), "");
            String type = Objects.toString(row.get("event_type"), "");
            if (!currentStatus.containsKey(actor)) {
                if ("LOGIN_SUCCESS".equals(type)) currentStatus.put(actor, "Logged in");
                else if ("LOGOUT".equals(type)) currentStatus.put(actor, "Signed out");
            }
        }
        return rows.stream().map(row -> {
            String actor = Objects.toString(row.get("actor_identifier"), "Unknown");
            String type = Objects.toString(row.get("event_type"), "UNKNOWN");
            String status = currentStatus.getOrDefault(actor, "Not logged in");
            return new LoginLogResponse(number(row.get("id")), actor,
                    Objects.toString(row.get("ip_address"), "—"), instant(row.get("created_at")), type, status);
        }).toList();
    }

    private LocalDate nextBirthday(LocalDate birth, LocalDate today) {
        LocalDate next = birthdayInYear(birth, today.getYear());
        return next.isBefore(today) ? birthdayInYear(birth, today.getYear() + 1) : next;
    }

    private LocalDate birthdayInYear(LocalDate birth, int year) {
        if (birth.getMonthValue() == 2 && birth.getDayOfMonth() == 29 && !Year.isLeap(year)) {
            return LocalDate.of(year, 2, 28);
        }
        return LocalDate.of(year, birth.getMonthValue(), birth.getDayOfMonth());
    }

    private ZoneId zone(String value) {
        try { return value == null || value.isBlank() ? ZoneId.systemDefault() : ZoneId.of(value); }
        catch (DateTimeException ignored) { return ZoneId.systemDefault(); }
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(value.toString());
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return value == null ? null : LocalDate.parse(value.toString());
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return value == null ? null : Instant.parse(value.toString());
    }
}
