package com.pki.ra.uiservice.mapper;

import com.pki.ra.uiservice.dto.EmployeeRequest;
import com.pki.ra.uiservice.dto.EmployeeResponse;
import com.pki.ra.uiservice.model.Employee;
import org.mapstruct.*;

/**
 * MapStruct mapper — DTO ↔ Entity conversion.
 *
 * <p><b>MapStruct kyun?</b>
 * <ul>
 *   <li>Compile-time code generation — runtime reflection nahi</li>
 *   <li>Type-safe — compile error agar field match na ho</li>
 *   <li>Manual mapping se fast — zero boilerplate</li>
 *   <li>Lombok ke saath well-integrated (lombok-mapstruct-binding)</li>
 * </ul>
 *
 * <p><b>componentModel = "spring"</b> — MapStruct generated class ko
 * {@code @Component} se annotate karta hai — Spring DI se inject ho sakti hai.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface EmployeeMapper {

    /**
     * EmployeeRequest (DTO) → Employee (Entity).
     *
     * <p>{@code disableBuilder = true} — Lombok @Builder sirf Employee ke declared fields
     * ka builder banata hai; inherited BaseAuditEntity fields (createdAt, updatedAt, etc.)
     * EmployeeBuilder mein exist nahi karte. Builder disable kar ke MapStruct
     * no-args constructor + setters use karta hai, jo inherited fields ko bhi cover karta hai.
     */
    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Employee toEntity(EmployeeRequest request);

    /**
     * Employee (Entity) → EmployeeResponse (DTO).
     *
     * <p>Sabhi fields automatically map honge same-name basis pe.
     */
    EmployeeResponse toResponse(Employee employee);

    /**
     * Existing entity ko request se update karo (partial update).
     *
     * <p>{@code @MappingTarget} — existing object update hoga,
     * naya object nahi banega.
     * <br>{@code NullValuePropertyMappingStrategy.IGNORE} — null fields skip honge
     * (partial update support).
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(EmployeeRequest request, @MappingTarget Employee employee);
}
