package com.spark.falcon.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class CustomerRequest {

    @Size(max = 120)
    private String name;

    @Size(max = 32)
    private String phone;

    @Email(message = "Enter a valid customer email")
    @Size(max = 160)
    private String email;

    @Size(max = 32)
    private String gender;

    private LocalDate dateOfBirth;

    @PositiveOrZero(message = "Age cannot be negative")
    private Integer age;

    @Size(max = 500)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String stateDivision;

    @Size(max = 100)
    private String country;

    @Size(max = 1000)
    private String notes;

    private boolean active = true;
}
