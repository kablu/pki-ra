package com.pki.ra.uiservice.service;

import com.pki.ra.common.exception.PkiRaException;
import com.pki.ra.uiservice.dto.EmployeeRequest;
import com.pki.ra.uiservice.dto.EmployeeResponse;
import com.pki.ra.uiservice.exception.EmployeeNotFoundException;
import com.pki.ra.uiservice.mapper.EmployeeMapper;
import com.pki.ra.uiservice.model.Employee;
import com.pki.ra.uiservice.model.Employee.EmployeeStatus;
import com.pki.ra.uiservice.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmployeeService}.
 *
 * <p>No Spring context — pure unit test with Mockito.
 * Repository aur Mapper mock hai — sirf service logic test hoti hai.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeService Unit Tests")
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeService employeeService;

    private EmployeeRequest  validRequest;
    private Employee         savedEmployee;
    private EmployeeResponse expectedResponse;

    @BeforeEach
    void setUp() {
        validRequest = EmployeeRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@pki-ra.com")
                .department("IT Security")
                .designation("PKI Engineer")
                .salary(new BigDecimal("85000.00"))
                .build();

        savedEmployee = Employee.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@pki-ra.com")
                .department("IT Security")
                .designation("PKI Engineer")
                .salary(new BigDecimal("85000.00"))
                .status(EmployeeStatus.ACTIVE)
                .build();

        expectedResponse = EmployeeResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@pki-ra.com")
                .department("IT Security")
                .status(EmployeeStatus.ACTIVE)
                .build();
    }

    // =========================================================================
    // CREATE Tests
    // =========================================================================

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("valid request → employee created successfully")
        void create_validRequest_returnsResponse() {
            when(employeeRepository.existsByEmail(validRequest.getEmail()))
                    .thenReturn(false);
            when(employeeMapper.toEntity(validRequest))
                    .thenReturn(savedEmployee);
            when(employeeRepository.save(savedEmployee))
                    .thenReturn(savedEmployee);
            when(employeeMapper.toResponse(savedEmployee))
                    .thenReturn(expectedResponse);

            EmployeeResponse result = employeeService.create(validRequest);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo("john.doe@pki-ra.com");
            verify(employeeRepository).save(any(Employee.class));
        }

        @Test
        @DisplayName("duplicate email → PkiRaException thrown")
        void create_duplicateEmail_throwsException() {
            when(employeeRepository.existsByEmail(validRequest.getEmail()))
                    .thenReturn(true);

            assertThatThrownBy(() -> employeeService.create(validRequest))
                    .isInstanceOf(PkiRaException.class)
                    .hasMessageContaining("already exists");

            verify(employeeRepository, never()).save(any());
        }
    }

    // =========================================================================
    // READ Tests
    // =========================================================================

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("existing id → employee returned")
        void findById_existingId_returnsResponse() {
            when(employeeRepository.findById(1L))
                    .thenReturn(Optional.of(savedEmployee));
            when(employeeMapper.toResponse(savedEmployee))
                    .thenReturn(expectedResponse);

            EmployeeResponse result = employeeService.findById(1L);

            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("non-existing id → EmployeeNotFoundException")
        void findById_nonExistingId_throwsNotFoundException() {
            when(employeeRepository.findById(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.findById(99L))
                    .isInstanceOf(EmployeeNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // =========================================================================
    // DELETE Tests
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("existing id → deleted successfully")
        void delete_existingId_deletesEmployee() {
            when(employeeRepository.existsById(1L)).thenReturn(true);

            assertThatNoException()
                    .isThrownBy(() -> employeeService.delete(1L));

            verify(employeeRepository).deleteById(1L);
        }

        @Test
        @DisplayName("non-existing id → EmployeeNotFoundException")
        void delete_nonExistingId_throwsNotFoundException() {
            when(employeeRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> employeeService.delete(99L))
                    .isInstanceOf(EmployeeNotFoundException.class);

            verify(employeeRepository, never()).deleteById(any());
        }
    }
}
