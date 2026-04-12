package com.pki.ra.uiservice.controller;

import com.pki.ra.uiservice.dto.EmployeeRequest;
import com.pki.ra.uiservice.dto.EmployeeResponse;
import com.pki.ra.uiservice.model.Employee.EmployeeStatus;
import com.pki.ra.uiservice.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Employee REST Controller — CRUD endpoints.
 *
 * <p><b>Base URL:</b> {@code /api/employees}
 * (context-path: /api from application.yml)
 *
 * <p><b>Design principles:</b>
 * <ul>
 *   <li>Controller mein zero business logic — sirf HTTP ↔ Service bridge</li>
 *   <li>@Valid — DTO validation trigger karta hai</li>
 *   <li>ResponseEntity — HTTP status code explicit control</li>
 *   <li>Pageable — pagination/sorting request parameters automatically parse</li>
 * </ul>
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
@Tag(name = "Employee", description = "Employee CRUD operations")
public class EmployeeController {

    private final EmployeeService employeeService;

    // =========================================================================
    // CREATE — POST /employees
    // =========================================================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create new employee",
        responses = {
            @ApiResponse(responseCode = "201", description = "Employee created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
        }
    )
    public ResponseEntity<EmployeeResponse> create(
            @Valid @RequestBody EmployeeRequest request) {

        log.info("POST /employees — email={}", request.getEmail());
        EmployeeResponse response = employeeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // READ — GET /employees/{id}
    // =========================================================================

    @GetMapping("/{id}")
    @Operation(
        summary = "Get employee by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Employee found"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
        }
    )
    public ResponseEntity<EmployeeResponse> findById(
            @Parameter(description = "Employee ID")
            @PathVariable Long id) {

        log.debug("GET /employees/{}", id);
        return ResponseEntity.ok(employeeService.findById(id));
    }

    // =========================================================================
    // READ ALL — GET /employees?page=0&size=10&sort=lastName,asc
    // =========================================================================

    @GetMapping
    @Operation(summary = "Get all employees (paginated)")
    public ResponseEntity<Page<EmployeeResponse>> findAll(
            @PageableDefault(size = 20, sort = "lastName",
                             direction = Sort.Direction.ASC)
            Pageable pageable) {

        log.debug("GET /employees — page={}, size={}",
                   pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(employeeService.findAll(pageable));
    }

    // =========================================================================
    // SEARCH — GET /employees/search?name=John
    // =========================================================================

    @GetMapping("/search")
    @Operation(summary = "Search employees by name")
    public ResponseEntity<List<EmployeeResponse>> search(
            @Parameter(description = "Name to search (partial, case-insensitive)")
            @RequestParam String name) {

        log.debug("GET /employees/search — name={}", name);
        return ResponseEntity.ok(employeeService.search(name));
    }

    // =========================================================================
    // READ BY DEPARTMENT — GET /employees/department/{dept}
    // =========================================================================

    @GetMapping("/department/{department}")
    @Operation(summary = "Get employees by department (paginated)")
    public ResponseEntity<Page<EmployeeResponse>> findByDepartment(
            @PathVariable String department,
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("GET /employees/department/{}", department);
        return ResponseEntity.ok(
            employeeService.findByDepartment(department, pageable));
    }

    // =========================================================================
    // UPDATE — PUT /employees/{id}
    // =========================================================================

    @PutMapping("/{id}")
    @Operation(
        summary = "Update employee",
        responses = {
            @ApiResponse(responseCode = "200", description = "Employee updated"),
            @ApiResponse(responseCode = "404", description = "Employee not found"),
            @ApiResponse(responseCode = "400", description = "Validation failed")
        }
    )
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request) {

        log.info("PUT /employees/{}", id);
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    // =========================================================================
    // UPDATE STATUS — PATCH /employees/{id}/status
    // =========================================================================

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update employee status only")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam EmployeeStatus status) {

        log.info("PATCH /employees/{}/status — status={}", id, status);
        employeeService.updateStatus(id, status);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // DELETE — DELETE /employees/{id}
    // =========================================================================

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete employee",
        responses = {
            @ApiResponse(responseCode = "204", description = "Employee deleted"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
        }
    )
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /employees/{}", id);
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
