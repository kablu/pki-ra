package com.pki.ra.uiservice.dto;

import com.pki.ra.uiservice.model.Employee.EmployeeStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Employee response DTO — API response payload.
 *
 * <p>Entity se alag — sirf woh fields jo client ko dikhane chahiye.
 * Salary jaise sensitive fields ko control kiya ja sakta hai yahan se.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Data
@Builder
public class EmployeeResponse {

    private Long            id;
    private String          firstName;
    private String          lastName;
    private String          email;
    private String          phone;
    private String          department;
    private String          designation;
    private BigDecimal      salary;
    private EmployeeStatus  status;
    private Instant         createdAt;
    private Instant         updatedAt;
}
