package com.pki.ra.uiservice.dto;

import com.pki.ra.uiservice.model.Employee.EmployeeStatus;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Employee create/update request DTO.
 *
 * <p><b>DTO kyun? Entity directly kyun nahi?</b>
 * <ul>
 *   <li>API contract aur DB schema alag rakhte hain</li>
 *   <li>Sensitive fields (id, createdAt) expose nahi hote</li>
 *   <li>Validation sirf DTO pe hoti hai — Entity clean rehta hai</li>
 *   <li>API versioning asaan hoti hai</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Data
@Builder
public class EmployeeRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be 2-100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be 2-100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Designation is required")
    private String designation;

    @NotNull(message = "Salary is required")
    @DecimalMin(value = "0.01", message = "Salary must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Invalid salary format")
    private BigDecimal salary;

    private EmployeeStatus status;
}
