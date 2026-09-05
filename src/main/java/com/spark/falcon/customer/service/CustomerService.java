package com.spark.falcon.customer.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.customer.dto.CustomerAccessResponse;
import com.spark.falcon.customer.dto.CustomerDuplicateCandidateResponse;
import com.spark.falcon.customer.dto.CustomerRequest;
import com.spark.falcon.customer.dto.CustomerResponse;
import com.spark.falcon.customer.entity.Customer;
import com.spark.falcon.customer.exception.CustomerAccessDeniedException;
import com.spark.falcon.customer.exception.CustomerNotFoundException;
import com.spark.falcon.customer.exception.CustomerOperationNotAllowedException;
import com.spark.falcon.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService implements CustomerAccessService {

    private static final String WALK_IN_CUSTOMER_NAME = "Walk-in Customer";

    private final CustomerRepository customerRepository;
    private final BusinessAccessService businessAccessService;
    private final Clock clock;

    @Transactional
    public CustomerResponse create(Long ownerId, CustomerRequest request) {
        BusinessAccessResponse business = business(ownerId);
        return createForBusiness(business.businessId(), request);
    }

    @Override
    @Transactional
    public CustomerResponse createForBusiness(Long businessId, CustomerRequest request) {
        Instant now = Instant.now(clock);
        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setSystemControlled(false);
        copyRequest(request, customer, now);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return toResponse(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public CustomerResponse update(Long ownerId, Long customerId, CustomerRequest request) {
        BusinessAccessResponse business = business(ownerId);
        Customer customer = customer(business.businessId(), customerId);
        rejectSystemControlledChange(customer);

        Instant now = Instant.now(clock);
        copyRequest(request, customer, now);
        customer.setUpdatedAt(now);
        return toResponse(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public CustomerResponse archive(Long ownerId, Long customerId) {
        BusinessAccessResponse business = business(ownerId);
        Customer customer = customer(business.businessId(), customerId);
        rejectSystemControlledChange(customer);

        if (!customer.isArchived()) {
            Instant now = Instant.now(clock);
            customer.setActive(false);
            customer.setArchivedAt(now);
            customer.setUpdatedAt(now);
        }
        return toResponse(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public CustomerResponse restore(Long ownerId, Long customerId) {
        BusinessAccessResponse business = business(ownerId);
        Customer customer = customer(business.businessId(), customerId);
        rejectSystemControlledChange(customer);

        if (customer.isArchived() || !customer.isActive()) {
            customer.setActive(true);
            customer.setArchivedAt(null);
            customer.setUpdatedAt(Instant.now(clock));
        }
        return toResponse(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public void permanentlyDeleteUnused(Long ownerId, Long customerId) {
        BusinessAccessResponse business = business(ownerId);
        Customer customer = customer(business.businessId(), customerId);
        rejectSystemControlledChange(customer);

        try {
            customerRepository.delete(customer);
            customerRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new CustomerOperationNotAllowedException(
                    "A customer with transaction, payment, return, credit, statement or audit dependencies cannot be permanently deleted."
            );
        }
    }

    public Optional<CustomerResponse> findByOwnerAndId(Long ownerId, Long customerId) {
        BusinessAccessResponse business = business(ownerId);
        return customerRepository.findByIdAndBusinessId(customerId, business.businessId())
                .map(this::toResponse);
    }

    public Page<CustomerResponse> findAll(Long ownerId,
                                          String keyword,
                                          Boolean archived,
                                          Pageable pageable) {
        BusinessAccessResponse business = business(ownerId);
        String safeKeyword = safeKeyword(keyword);
        if (safeKeyword.isBlank()) {
            return customerRepository.findAllForBusiness(business.businessId(), archived, pageable)
                    .map(this::toResponse);
        }
        return customerRepository.search(
                        business.businessId(), archived, safeKeyword, normalizePhone(safeKeyword),
                        numericCustomerId(safeKeyword), pageable)
                .map(this::toResponse);
    }

    public List<CustomerDuplicateCandidateResponse> findProbableDuplicates(Long ownerId,
                                                                            String phone,
                                                                            String email,
                                                                            Long excludeCustomerId) {
        BusinessAccessResponse business = business(ownerId);
        return findProbableDuplicatesByBusiness(
                business.businessId(), phone, email, excludeCustomerId);
    }

    @Override
    public List<CustomerDuplicateCandidateResponse> findProbableDuplicatesByBusiness(Long businessId,
                                                                                      String phone,
                                                                                      String email,
                                                                                      Long excludeCustomerId) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedPhone == null && normalizedEmail == null) {
            return List.of();
        }

        return customerRepository.findProbableDuplicates(
                        businessId,
                        normalizedPhone,
                        normalizedEmail,
                        excludeCustomerId,
                        PageRequest.of(0, 10))
                .stream()
                .map(this::toDuplicateCandidate)
                .toList();
    }

    @Override
    public Optional<CustomerAccessResponse> findActiveByBusinessIdAndCustomerId(Long businessId, Long customerId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .filter(Customer::isActive)
                .filter(customer -> !customer.isArchived())
                .map(this::toAccessResponse);
    }

    @Override
    public Optional<CustomerAccessResponse> findByBusinessIdAndCustomerId(Long businessId, Long customerId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .map(this::toAccessResponse);
    }

    @Override
    public List<CustomerAccessResponse> searchActive(Long businessId, String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String safeKeyword = safeKeyword(keyword);
        if (safeKeyword.isBlank()) {
            return customerRepository.findByBusinessIdAndActiveTrueAndArchivedAtIsNull(
                            businessId,
                            PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.ASC, "name")))
                    .stream()
                    .map(this::toAccessResponse)
                    .toList();
        }
        return customerRepository.searchActive(
                        businessId,
                        safeKeyword,
                        normalizePhone(safeKeyword),
                        numericCustomerId(safeKeyword),
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.ASC, "name")))
                .stream()
                .map(this::toAccessResponse)
                .toList();
    }

    @Override
    public Map<Long, CustomerAccessResponse> findByBusinessIdAndCustomerIds(
            Long businessId, Collection<Long> customerIds) {
        if (businessId == null || customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = customerIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findByBusinessIdAndIdIn(businessId, ids).stream()
                .map(this::toAccessResponse)
                .collect(Collectors.toUnmodifiableMap(
                        CustomerAccessResponse::id, Function.identity(), (left, right) -> left));
    }

    @Override
    @Transactional
    public CustomerAccessResponse getOrCreateWalkInCustomer(Long businessId) {
        Optional<Customer> existing = customerRepository.findFirstByBusinessIdAndSystemControlledTrue(businessId);
        if (existing.isPresent()) {
            Customer customer = existing.get();
            if (!customer.isActive() || customer.isArchived()) {
                customer.setActive(true);
                customer.setArchivedAt(null);
                customer.setUpdatedAt(Instant.now(clock));
                customer = customerRepository.saveAndFlush(customer);
            }
            return toAccessResponse(customer);
        }

        Instant now = Instant.now(clock);
        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setName(WALK_IN_CUSTOMER_NAME);
        customer.setActive(true);
        customer.setSystemControlled(true);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return toAccessResponse(customerRepository.saveAndFlush(customer));
    }

    @Override
    public boolean isEligibleForDueSale(Long businessId, Long customerId) {
        return findActiveByBusinessIdAndCustomerId(businessId, customerId)
                .map(CustomerAccessResponse::dueEligible)
                .orElse(false);
    }

    @Override
    public CustomerAccessResponse requireDueEligibleCustomer(Long businessId, Long customerId) {
        Customer customer = customer(businessId, customerId);
        if (!customer.isEligibleForDueSale()) {
            throw new CustomerOperationNotAllowedException(
                    "A due sale requires an active identifiable customer. Walk-in, inactive or archived customers are not eligible."
            );
        }
        return toAccessResponse(customer);
    }

    private void copyRequest(CustomerRequest request, Customer customer, Instant now) {
        customer.setName(clean(request.getName()));
        customer.setPhone(clean(request.getPhone()));
        customer.setNormalizedPhone(normalizePhone(request.getPhone()));
        customer.setEmail(clean(request.getEmail()));
        customer.setNormalizedEmail(normalizeEmail(request.getEmail()));
        customer.setGender(clean(request.getGender()));
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setAge(request.getAge());
        customer.setAddress(clean(request.getAddress()));
        customer.setCity(clean(request.getCity()));
        customer.setStateDivision(clean(request.getStateDivision()));
        customer.setCountry(clean(request.getCountry()));
        customer.setNotes(clean(request.getNotes()));
        customer.setActive(request.isActive());

        if (request.isActive()) {
            customer.setArchivedAt(null);
        } else if (customer.getArchivedAt() == null) {
            customer.setArchivedAt(now);
        }
    }

    private Customer customer(Long businessId, Long customerId) {
        return customerRepository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(CustomerNotFoundException::new);
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(CustomerAccessDeniedException::new);
    }

    private void rejectSystemControlledChange(Customer customer) {
        if (customer.isSystemControlled()) {
            throw new CustomerOperationNotAllowedException(
                    "The system-controlled Walk-in Customer cannot be edited, archived, restored or permanently deleted."
            );
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getBusinessId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getGender(),
                customer.getDateOfBirth(),
                customer.getAge(),
                customer.getAddress(),
                customer.getCity(),
                customer.getStateDivision(),
                customer.getCountry(),
                customer.getNotes(),
                customer.isActive(),
                customer.isArchived(),
                customer.isSystemControlled(),
                customer.isEligibleForDueSale(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getArchivedAt()
        );
    }

    private CustomerAccessResponse toAccessResponse(Customer customer) {
        return new CustomerAccessResponse(
                customer.getId(),
                customer.getBusinessId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.isActive(),
                customer.isArchived(),
                customer.isSystemControlled(),
                customer.isEligibleForDueSale()
        );
    }

    private CustomerDuplicateCandidateResponse toDuplicateCandidate(Customer customer) {
        return new CustomerDuplicateCandidateResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.isArchived()
        );
    }

    private String safeKeyword(String value) {
        return value == null ? "" : value.trim();
    }

    private Long numericCustomerId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizeEmail(String value) {
        String email = clean(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        String phone = clean(value);
        if (phone == null) {
            return null;
        }

        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < phone.length(); index++) {
            char character = phone.charAt(index);
            if (Character.isDigit(character)) {
                normalized.append(character);
            } else if (character == '+' && normalized.isEmpty()) {
                normalized.append(character);
            }
        }
        return normalized.isEmpty() || "+".contentEquals(normalized) ? null : normalized.toString();
    }
}
