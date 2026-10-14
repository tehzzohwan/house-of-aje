package com.houseofaje.customer;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Customers are identified by phone number: an existing customer is reused so
 * repeat bookings attribute to one customer (relevant to loyalty reporting).
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public Customer create(CreateCustomerRequest request) {
        if (isBlank(request.firstName()) || isBlank(request.lastName()) || isBlank(request.phone())) {
            throw new IllegalArgumentException("firstName, lastName and phone are required");
        }
        return customers.findByPhone(request.phone())
                .orElseGet(() -> {
                    Customer customer = new Customer(UUID.randomUUID(), request.firstName(), request.lastName(),
                            request.email(), request.phone(), null, Instant.now());
                    customers.insert(customer.id(), customer.firstName(), customer.lastName(),
                            customer.email(), customer.phone(), null, null);
                    return customer;
                });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
