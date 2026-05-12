package com.pki.ra.common.config.dto;

/**
 * Sealed marker interface for all database-backed configuration DTOs.
 *
 * <p>Every DTO that can be stored in the {@code app_config} table must implement
 * this interface and declare its unique {@code config_type} discriminator via
 * {@link #configType()}.  The discriminator is stored in the {@code config_type}
 * column and used by {@code ConfigDtoRegistry} to select the correct deserialisation
 * target class at startup.
 *
 * <p>Adding a new configuration type requires:
 * <ol>
 *   <li>Creating a {@code record} that {@code implements ConfigDto}.</li>
 *   <li>Adding the record to the {@code permits} clause below.</li>
 *   <li>Registering the type string in {@code ConfigDtoRegistry}.</li>
 * </ol>
 */
public sealed interface ConfigDto permits LdapConfigDto {

    /** Returns the {@code config_type} discriminator stored in the database. */
    String configType();
}
