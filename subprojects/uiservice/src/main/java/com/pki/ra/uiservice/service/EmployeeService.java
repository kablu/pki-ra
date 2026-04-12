package com.pki.ra.uiservice.service;

import com.pki.ra.uiservice.dto.EmployeeRequest;
import com.pki.ra.uiservice.dto.EmployeeResponse;
import com.pki.ra.uiservice.exception.EmployeeNotFoundException;
import com.pki.ra.uiservice.mapper.EmployeeMapper;
import com.pki.ra.uiservice.model.Employee;
import com.pki.ra.uiservice.model.Employee.EmployeeStatus;
import com.pki.ra.uiservice.repository.EmployeeRepository;
import com.pki.ra.common.exception.PkiRaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Employee service — business logic layer.
 *
 * <p><b>@Transactional strategy:</b>
 * <ul>
 *   <li>Class level: {@code readOnly = true} — sabhi methods read-only by default</li>
 *   <li>Write methods: {@code @Transactional} override — read-only hata deta hai</li>
 *   <li>Read-only optimization: Hibernate dirty checking band ho jati hai → faster</li>
 * </ul>
 *
 * <p><b>Why service layer?</b>
 * <ul>
 *   <li>Business logic controller se alag</li>
 *   <li>Transaction boundary yahan define hoti hai</li>
 *   <li>Multiple repositories ek transaction mein use kar sakte hain</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // default — sabhi methods read-only
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper     employeeMapper;

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Naya employee create karo.
     *
     * <p>{@code @Transactional} — readOnly override, write transaction.
     *
     * @param request employee details
     * @return saved employee response
     * @throws PkiRaException agar email already exist karta hai
     */
    @Transactional   // readOnly = false (default)
    public EmployeeResponse create(EmployeeRequest request) {
        log.info("Creating employee: email={}, department={}",
                  request.getEmail(), request.getDepartment());

        // Business rule: email unique hona chahiye
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new PkiRaException(
                "Employee already exists with email: " + request.getEmail());
        }

        // DTO → Entity
        Employee employee = employeeMapper.toEntity(request);

        // Save → DB INSERT
        Employee saved = employeeRepository.save(employee);

        log.info("Employee created: id={}, email={}", saved.getId(), saved.getEmail());

        // Entity → Response DTO
        return employeeMapper.toResponse(saved);
    }

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * ID se employee fetch karo.
     *
     * <p>readOnly = true — inherited from class level.
     * Hibernate dirty checking disabled → faster query.
     *
     * @throws EmployeeNotFoundException agar employee nahi mila
     */
    public EmployeeResponse findById(Long id) {
        log.debug("Finding employee by id={}", id);

        return employeeRepository.findById(id)
                .map(employeeMapper::toResponse)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    /**
     * Sabhi employees — paginated.
     *
     * <p>Pageable se control: page number, size, sort.
     * Example: {@code Pageable.of(0, 10, Sort.by("lastName"))}
     */
    public Page<EmployeeResponse> findAll(Pageable pageable) {
        log.debug("Finding all employees, page={}, size={}",
                   pageable.getPageNumber(), pageable.getPageSize());

        return employeeRepository.findAll(pageable)
                .map(employeeMapper::toResponse);
    }

    /**
     * Department ke employees — paginated.
     */
    public Page<EmployeeResponse> findByDepartment(String department, Pageable pageable) {
        log.debug("Finding employees by department={}", department);

        return employeeRepository.findByDepartment(department, pageable)
                .map(employeeMapper::toResponse);
    }

    /**
     * Name se search — partial, case-insensitive.
     */
    public List<EmployeeResponse> search(String name) {
        log.debug("Searching employees by name={}", name);

        return employeeRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(name, name)
                .stream()
                .map(employeeMapper::toResponse)
                .toList();
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Employee update karo — full update.
     *
     * <p><b>JPA update flow:</b>
     * <ol>
     *   <li>findById → entity load (managed state)</li>
     *   <li>mapper.updateEntity → fields update</li>
     *   <li>Transaction commit → Hibernate dirty check → UPDATE SQL</li>
     *   <li>Explicit save() bhi kar sakte ho — optional when managed</li>
     * </ol>
     *
     * @throws EmployeeNotFoundException agar employee nahi mila
     */
    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        log.info("Updating employee: id={}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        // Email change hoi to uniqueness check
        if (!employee.getEmail().equalsIgnoreCase(request.getEmail())
                && employeeRepository.existsByEmail(request.getEmail())) {
            throw new PkiRaException(
                "Email already in use: " + request.getEmail());
        }

        // Existing entity update karo (partial update — null fields skip)
        employeeMapper.updateEntity(request, employee);

        // Transaction commit pe Hibernate automatically UPDATE chalayega
        Employee updated = employeeRepository.save(employee);

        log.info("Employee updated: id={}", updated.getId());
        return employeeMapper.toResponse(updated);
    }

    /**
     * Sirf status update karo — @Modifying query use karta hai.
     *
     * <p>Puri entity load karne ki zaroorat nahi — direct UPDATE SQL.
     */
    @Transactional
    public void updateStatus(Long id, EmployeeStatus status) {
        log.info("Updating employee status: id={}, status={}", id, status);

        if (!employeeRepository.existsById(id)) {
            throw new EmployeeNotFoundException(id);
        }

        int updated = employeeRepository.updateStatus(id, status);
        log.info("Status updated for {} record(s)", updated);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * Employee delete karo — hard delete.
     *
     * @throws EmployeeNotFoundException agar employee nahi mila
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting employee: id={}", id);

        if (!employeeRepository.existsById(id)) {
            throw new EmployeeNotFoundException(id);
        }

        employeeRepository.deleteById(id);
        log.info("Employee deleted: id={}", id);
    }
}
