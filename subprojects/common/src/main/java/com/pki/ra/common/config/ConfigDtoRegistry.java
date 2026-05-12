package com.pki.ra.common.config;

import com.pki.ra.common.config.dto.ConfigDto;
import com.pki.ra.common.config.dto.LdapConfigDto;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Registry that maps a {@code config_type} discriminator string to its
 * corresponding {@link ConfigDto} implementation class.
 *
 * <p>{@link ConfigBean} consults this registry for every row it reads from the
 * {@code app_config} table, so it knows which Jackson target class to use when
 * deserialising the {@code config_value} JSON column.
 *
 * <p>To add a new configuration type:
 * <ol>
 *   <li>Create a {@code record} implementing {@link ConfigDto}.</li>
 *   <li>Add its {@code TYPE} constant to {@link #REGISTRY} below.</li>
 *   <li>Add the record to the {@code permits} clause of {@link ConfigDto}.</li>
 * </ol>
 */
@Component
public class ConfigDtoRegistry {

    private static final Map<String, Class<? extends ConfigDto>> REGISTRY = Map.of(
            LdapConfigDto.TYPE, LdapConfigDto.class
    );

    /**
     * Resolves the DTO class for the given {@code configType} discriminator.
     *
     * @param configType value of the {@code config_type} column, e.g. {@code "LDAP"}
     * @return the DTO {@code Class} to use for Jackson deserialisation
     * @throws IllegalArgumentException if no DTO is registered for the given type
     */
    public Class<? extends ConfigDto> resolve(String configType) {
        return Optional.ofNullable(REGISTRY.get(configType))
                .orElseThrow(() -> new IllegalArgumentException(
                        "ConfigDtoRegistry: no DTO registered for config_type='" + configType + "'"));
    }

    /** Returns {@code true} if the given {@code configType} has a registered DTO class. */
    public boolean isKnown(String configType) {
        return REGISTRY.containsKey(configType);
    }
}
