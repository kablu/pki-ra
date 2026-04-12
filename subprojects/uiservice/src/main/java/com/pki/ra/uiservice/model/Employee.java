package com.pki.ra.uiservice.model;

import com.pki.ra.common.model.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Employee JPA entity — {@code employee} table ka Java representation.
 *
 * <p><b>Inheritance:</b> {@link BaseAuditEntity} se extend kiya — isse
 * {@code created_at}, {@code updated_at}, {@code created_by}, {@code updated_by}
 * automatically manage hote hain via {@code @EnableJpaAuditing}.
 *
 * <p><b>Annotations explained:</b>
 * <ul>
 *   <li>{@code @Entity}   — Spring ko batata hai yeh ek DB entity hai</li>
 *   <li>{@code @Table}    — DB table name explicitly map karta hai</li>
 *   <li>{@code @Id}       — Primary key field</li>
 *   <li>{@code @GeneratedValue} — DB se auto-increment ID lena</li>
 *   <li>{@code @Column}   — Column name, nullable, length constraints</li>
 *   <li>{@code @Enumerated} — Enum ko DB mein String store karna</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Entity
@Table(
    name = "employee",
    indexes = {
        @Index(name = "idx_employee_email",      columnList = "email",       unique = true),
        @Index(name = "idx_employee_department", columnList = "department"),
        @Index(name = "idx_employee_status",     columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "salary")   // salary sensitive — toString mein mat aaye
public class Employee extends BaseAuditEntity {

    // -------------------------------------------------------------------------
    // Primary Key
    // -------------------------------------------------------------------------

    /**
     * DB auto-generated primary key.
     *
     * <p>{@code IDENTITY} strategy — MariaDB {@code BIGINT AUTO_INCREMENT} column use karta hai.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    // -------------------------------------------------------------------------
    // Personal Information
    // -------------------------------------------------------------------------

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Email — unique constraint DB level pe bhi hai (see @Index above).
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    // -------------------------------------------------------------------------
    // Employment Information
    // -------------------------------------------------------------------------

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "designation", nullable = false, length = 100)
    private String designation;

    /**
     * Salary — DECIMAL(12,2) in MariaDB (e.g. 999999999999.99).
     */
    @Column(name = "salary", nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal salary;

    /**
     * Employee status — {@code ACTIVE}, {@code INACTIVE}, {@code ON_LEAVE}.
     *
     * <p>{@code EnumType.STRING} — DB mein "ACTIVE" store hoga, na ki 0/1.
     * String type prefer karo — DB directly readable hoti hai.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    // -------------------------------------------------------------------------
    // Enum
    // -------------------------------------------------------------------------

    public enum EmployeeStatus {
        ACTIVE,
        INACTIVE,
        ON_LEAVE
    }
}
