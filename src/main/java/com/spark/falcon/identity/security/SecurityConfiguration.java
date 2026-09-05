package com.spark.falcon.identity.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@RequiredArgsConstructor
@Configuration
public class SecurityConfiguration {

    private final RecoveryCsrfAccessDeniedHandler recoveryCsrfAccessDeniedHandler;
    private final FalconAuthenticationProvider authenticationProvider;
    private final FalconAuthenticationDetailsSource authenticationDetailsSource;
    private final FalconAuthenticationSuccessHandler authenticationSuccessHandler;
    private final StaffAuthenticationRefreshFilter staffAuthenticationRefreshFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        return http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/", "/signup", "/owner/signup", "/owner/email-verification", "/owner/email-verification/resend",
                                "/login", "/sign-in", "/forgot-password", "/password-reset-verification", "/password-reset-verification/resend", "/reset-password", "/password-reset-complete", "/terms", "/privacy",
                                "/css/**", "/js/**", "/images/**", "/uploads/users/**", "/favicon.ico", "/error"
                        ).permitAll()
                        .requestMatchers("/owner/setup/**").hasRole("OWNER")
                        .requestMatchers("/owner/users/**", "/owner/user-groups/**")
                            .hasAnyAuthority("ROLE_OWNER", "USER_MANAGEMENT")
                        .requestMatchers("/owner/branches/change")
                            .hasAnyAuthority("ROLE_OWNER", "BRANCH_CHANGE")
                        .requestMatchers(HttpMethod.GET, "/owner/branches/new")
                            .hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/owner/branches")
                            .hasRole("OWNER")
                        .requestMatchers("/owner/branches/**")
                            .hasAnyAuthority("ROLE_OWNER", "BRANCH_MANAGEMENT")
                        .requestMatchers(HttpMethod.POST, "/owner/pos/hold", "/owner/pos/checkout")
                            .hasAnyAuthority("ROLE_OWNER", "SALE_CREATE")
                        .requestMatchers("/owner/pos/**")
                            .hasAnyAuthority("ROLE_OWNER", "POS_OPEN")
                        .requestMatchers(HttpMethod.GET, "/owner/sales/*/invoice")
                            .hasAnyAuthority("ROLE_OWNER", "SELL_ACCESS", "SALE_CREATE")
                        .requestMatchers(HttpMethod.POST, "/owner/sales/*/email-receipt")
                            .hasAnyAuthority("ROLE_OWNER", "SELL_ACCESS", "SALE_CREATE")
                        .requestMatchers("/owner/sales/**")
                            .hasAnyAuthority("ROLE_OWNER", "SELL_ACCESS")
                        .requestMatchers("/owner/products/**")
                            .hasAnyAuthority("ROLE_OWNER", "PRODUCT_ACCESS")
                        .requestMatchers("/owner/purchases/**")
                            .hasAnyAuthority("ROLE_OWNER", "PURCHASE_ACCESS")
                        .requestMatchers("/owner/suppliers/**")
                            .hasAnyAuthority("ROLE_OWNER", "SUPPLIER_ACCESS")
                        .requestMatchers("/owner/customers/**")
                            .hasAnyAuthority("ROLE_OWNER", "CUSTOMER_ACCESS")
                        .requestMatchers(HttpMethod.GET, "/owner/payments/history/export")
                            .hasAnyAuthority("ROLE_OWNER", "PAYMENT_EXPORT")
                        .requestMatchers(HttpMethod.POST, "/owner/payments/*/reverse")
                            .hasAnyAuthority("ROLE_OWNER", "PAYMENT_REVERSE")
                        .requestMatchers(HttpMethod.POST, "/owner/payments/customer")
                            .hasAnyAuthority("ROLE_OWNER", "PAYMENT_RECEIVE_CUSTOMER_DUE")
                        .requestMatchers(HttpMethod.POST, "/owner/payments/supplier")
                            .hasAnyAuthority("ROLE_OWNER", "PAYMENT_PAY_SUPPLIER_DUE")
                        .requestMatchers(HttpMethod.GET, "/owner/payments/customer")
                            .hasAnyAuthority("ROLE_OWNER", "PAYMENT_RECEIVE_CUSTOMER_DUE")
                        .requestMatchers(HttpMethod.GET, "/owner/payments/supplier")
                            .hasAnyAuthority("ROLE_OWNER", "PAYMENT_PAY_SUPPLIER_DUE")
                        .requestMatchers("/owner/payments/**")
                            .hasAnyAuthority("ROLE_OWNER", "PAYMENT_VIEW", "PAYMENT_VIEW_CONSOLIDATED")
                        .requestMatchers(HttpMethod.GET, "/owner/expenses/export")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENSE_EXPORT")
                        .requestMatchers(HttpMethod.GET, "/owner/expenses/add", "/owner/expense-categories/add", "/owner/expense-categories/*/edit")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENSE_CREATE")
                        .requestMatchers(HttpMethod.POST, "/owner/expenses/*/reverse")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENSE_REVERSE")
                        .requestMatchers(HttpMethod.POST, "/owner/expenses/*/post")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENSE_POST")
                        .requestMatchers(HttpMethod.POST, "/owner/expenses/*/approve", "/owner/expenses/*/reject")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENSE_APPROVE")
                        .requestMatchers(HttpMethod.POST, "/owner/expenses/*/submit")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENSE_SUBMIT")
                        .requestMatchers(HttpMethod.POST, "/owner/expenses/*/recover")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENDITURE_ACCESS")
                        .requestMatchers(HttpMethod.POST, "/owner/expenses", "/owner/expenses/*", "/owner/expenses/*/delete",
                                "/owner/expenses/*/cancel", "/owner/expense-categories/**")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENSE_CREATE")
                        .requestMatchers("/owner/expenses/**", "/owner/expense-categories/**")
                            .hasAnyAuthority("ROLE_OWNER", "EXPENDITURE_ACCESS", "EXPENSE_VIEW")
                        .requestMatchers(HttpMethod.GET,
                                "/owner/inventory/movements/export",
                                "/owner/inventory/counts/export",
                                "/owner/inventory/adjustments/export",
                                "/owner/inventory/transfers/export",
                                "/owner/inventory/wastage/export")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_EXPORT")
                        .requestMatchers(HttpMethod.GET, "/owner/inventory/counts/new")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_COUNT_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/counts",
                                "/owner/inventory/counts/*/draft/update",
                                "/owner/inventory/counts/*/rows",
                                "/owner/inventory/counts/*/start",
                                "/owner/inventory/counts/*/submit",
                                "/owner/inventory/counts/*/cancel",
                                "/owner/inventory/counts/*/delete")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_COUNT_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/counts/*/review",
                                "/owner/inventory/counts/*/recount",
                                "/owner/inventory/counts/*/approve",
                                "/owner/inventory/counts/*/reject")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_COUNT_REVIEW_APPROVE")
                        .requestMatchers(HttpMethod.POST, "/owner/inventory/counts/*/post")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_COUNT_POST_VARIANCE")
                        .requestMatchers(HttpMethod.GET, "/owner/inventory/adjustments/new")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_ADJUSTMENT_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/adjustments",
                                "/owner/inventory/adjustments/*/draft/update",
                                "/owner/inventory/adjustments/*/submit",
                                "/owner/inventory/adjustments/*/cancel",
                                "/owner/inventory/adjustments/*/delete")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_ADJUSTMENT_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/adjustments/*/approve",
                                "/owner/inventory/adjustments/*/reject",
                                "/owner/inventory/adjustments/*/post")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_ADJUSTMENT_APPROVE_POST")
                        .requestMatchers(HttpMethod.POST, "/owner/inventory/adjustments/*/reverse")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_ADJUSTMENT_REVERSE")
                        .requestMatchers(HttpMethod.GET, "/owner/inventory/transfers/new")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_TRANSFER_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/transfers",
                                "/owner/inventory/transfers/*/draft/update",
                                "/owner/inventory/transfers/*/submit",
                                "/owner/inventory/transfers/*/cancel",
                                "/owner/inventory/transfers/*/delete")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_TRANSFER_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/transfers/*/approve",
                                "/owner/inventory/transfers/*/reject")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_TRANSFER_APPROVE")
                        .requestMatchers(HttpMethod.POST, "/owner/inventory/transfers/*/dispatch")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_TRANSFER_DISPATCH")
                        .requestMatchers(HttpMethod.POST, "/owner/inventory/transfers/*/receive")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_TRANSFER_RECEIVE")
                        .requestMatchers(HttpMethod.POST, "/owner/inventory/transfers/*/resolve")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_TRANSFER_RESOLVE_DISCREPANCY")
                        .requestMatchers(HttpMethod.GET, "/owner/inventory/wastage/new")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_WASTAGE_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/wastage",
                                "/owner/inventory/wastage/*/draft/update",
                                "/owner/inventory/wastage/*/submit",
                                "/owner/inventory/wastage/*/cancel",
                                "/owner/inventory/wastage/*/delete")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_WASTAGE_CREATE_SUBMIT")
                        .requestMatchers(HttpMethod.POST,
                                "/owner/inventory/wastage/*/approve",
                                "/owner/inventory/wastage/*/reject",
                                "/owner/inventory/wastage/*/post")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_WASTAGE_APPROVE_POST")
                        .requestMatchers(HttpMethod.POST, "/owner/inventory/wastage/*/reverse")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_WASTAGE_REVERSE")
                        .requestMatchers("/owner/inventory/**")
                            .hasAnyAuthority("ROLE_OWNER", "INVENTORY_VIEW")
                        .requestMatchers("/owner/cash-management/**")
                            .hasAnyAuthority("ROLE_OWNER", "CASH_MANAGEMENT_ACCESS")
                        .requestMatchers("/owner/settings/**")
                            .hasAnyAuthority("ROLE_OWNER", "SETTINGS_ACCESS")
                        .requestMatchers("/owner/analytics/**")
                            .hasAnyAuthority("ROLE_OWNER", "ANALYTICS_REPORTS_ACCESS")
                        .requestMatchers("/owner/dashboard", "/owner/dashboard/**")
                            .authenticated()
                        .requestMatchers("/owner/**").hasRole("OWNER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception.accessDeniedHandler(recoveryCsrfAccessDeniedHandler))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .invalidSessionUrl("/login?expired")
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .authenticationDetailsSource(authenticationDetailsSource)
                        .successHandler(authenticationSuccessHandler)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .addFilterAfter(staffAuthenticationRefreshFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/sign-out")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .build();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
