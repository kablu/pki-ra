package com.pki.ra.uiservice.repository;

import com.pki.ra.uiservice.model.Employee;
import com.pki.ra.uiservice.model.Employee.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Employee JPA Repository — Spring Data JPA ka power.
 *
 * <p><b>JpaRepository kya deta hai automatically?</b>
 * <ul>
 *   <li>{@code save()}         — INSERT ya UPDATE</li>
 *   <li>{@code findById()}     — SELECT by PK</li>
 *   <li>{@code findAll()}      — SELECT all with pagination</li>
 *   <li>{@code deleteById()}   — DELETE by PK</li>
 *   <li>{@code count()}        — SELECT COUNT(*)</li>
 *   <li>{@code existsById()}   — SELECT EXISTS</li>
 * </ul>
 *
 * <p><b>Yahan koi SQL likhne ki zaroorat nahi</b> — method name se query banti hai.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // -------------------------------------------------------------------------
    // Method Name Queries — Spring automatically SQL generate karta hai
    // -------------------------------------------------------------------------

    /**
     * Email se employee dhundho.
     * SQL: SELECT * FROM employee WHERE email = ?
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Email exist karta hai ya nahi.
     * SQL: SELECT COUNT(*) > 0 FROM employee WHERE email = ?
     */
    boolean existsByEmail(String email);

    /**
     * Department ke sabhi employees — paginated.
     * SQL: SELECT * FROM employee WHERE department = ? LIMIT ? OFFSET ?
     */
    Page<Employee> findByDepartment(String department, Pageable pageable);

    /**
     * Status ke sabhi employees.
     * SQL: SELECT * FROM employee WHERE status = ?
     */
    List<Employee> findByStatus(EmployeeStatus status);

    /**
     * Name se search — case-insensitive partial match.
     * SQL: SELECT * FROM employee
     *      WHERE LOWER(first_name) LIKE LOWER(?)
     *         OR LOWER(last_name) LIKE LOWER(?)
     */
    List<Employee> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName);

    // -------------------------------------------------------------------------
    // @Query — Custom JPQL Queries
    // -------------------------------------------------------------------------

    /**
     * Department wise employee count.
     *
     * <p>JPQL use kiya — table name nahi, <b>Entity class name</b> use hota hai.
     */
    @Query("SELECT e.department, COUNT(e) FROM Employee e GROUP BY e.department")
    List<Object[]> countByDepartment();

    /**
     * Department ke active employees with salary filter.
     *
     * <p>{@code :department}, {@code :minSalary} — named parameters (@Param se bind)
     */
    @Query("""
            SELECT e FROM Employee e
             WHERE e.department = :department
               AND e.salary >= :minSalary
               AND e.status = 'ACTIVE'
             ORDER BY e.salary DESC
            """)
    List<Employee> findActiveByDepartmentAndMinSalary(
            @Param("department") String department,
            @Param("minSalary")  java.math.BigDecimal minSalary);

    // -------------------------------------------------------------------------
    // @Modifying — UPDATE / DELETE queries
    // -------------------------------------------------------------------------

    /**
     * Employee ka status update karo.
     *
     * <p>{@code @Modifying} — DML query (UPDATE/DELETE) ke liye zaroori.
     * <br>{@code @Transactional} — Modifying query transaction mein honi chahiye.
     */
    @Modifying
    @Query("UPDATE Employee e SET e.status = :status WHERE e.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") EmployeeStatus status);

    /**
     * Department ke sabhi employees ko inactive karo (bulk update).
     */
    @Modifying
    @Query("UPDATE Employee e SET e.status = 'INACTIVE' WHERE e.department = :department")
    int deactivateDepartment(@Param("department") String department);
}
