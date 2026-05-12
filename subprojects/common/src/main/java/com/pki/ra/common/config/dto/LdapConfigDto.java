package com.pki.ra.common.config.dto;

/**
 * DTO for LDAP / AD connectivity configuration.
 *
 * <p>Stored in {@code app_config} with {@code config_type = "LDAP"}.
 * The {@code config_value} column holds the JSON serialisation of this record,
 * which is deserialised by {@code ConfigBean} on {@code ApplicationReadyEvent}.
 *
 * <p>Example JSON payload stored in the database:
 * <pre>{@code
 * {
 *   "host":               "ldap.pki.internal",
 *   "port":               636,
 *   "baseDn":             "DC=pki,DC=internal",
 *   "bindDn":             "CN=svc-pki-bind,OU=ServiceAccounts,DC=pki,DC=internal",
 *   "bindPassword":       "change-me",
 *   "useSsl":             true,
 *   "connectionTimeoutMs": 5000,
 *   "readTimeoutMs":      10000
 * }
 * }</pre>
 *
 * @param host               LDAP server hostname or IP
 * @param port               LDAP port (typically 389 plain, 636 SSL)
 * @param baseDn             Base distinguished name for user searches
 * @param bindDn             Service account DN used to bind to LDAP
 * @param bindPassword       Service account password (should be vault-injected in prod)
 * @param useSsl             {@code true} for LDAPS (port 636); {@code false} for plain LDAP
 * @param connectionTimeoutMs Milliseconds before a connection attempt times out
 * @param readTimeoutMs      Milliseconds before a read operation times out
 */
public record LdapConfigDto(
        String host,
        int    port,
        String baseDn,
        String bindDn,
        String bindPassword,
        boolean useSsl,
        int    connectionTimeoutMs,
        int    readTimeoutMs
) implements ConfigDto {

    public static final String TYPE = "LDAP";

    @Override
    public String configType() {
        return TYPE;
    }
}
