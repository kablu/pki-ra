package com.pki.ra.raservice.config;

import com.pki.ra.common.config.ConfigBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the full config startup sequence.
 *
 * Spring context with {@code h2} profile:
 * 1. H2 DataSource created, Hibernate creates app_config table
 * 2. AppConfigSeeder inserts 8 LDAP rows (ApplicationRunner)
 * 3. ConfigBean.loadOnReady() caches rows keyed by config_key
 */
@SpringBootTest
@ActiveProfiles("h2")
@DisplayName("ConfigBean — integration")
class ConfigBeanIntegrationTest {

    @Autowired ConfigBean configBean;

    @Test
    @DisplayName("cache contains 8 entries after startup (seeded by AppConfigSeeder)")
    void cacheHasEightEntriesAfterStartup() {
        assertThat(configBean.size()).isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("get('host') returns LDAP host row")
    void get_host_returnsLdapRow() {
        var dto = configBean.get("host");

        assertThat(dto).isPresent();
        assertThat(dto.get().configType()).isEqualTo("LDAP");
        assertThat(dto.get().configValue()).isEqualTo("ldap.pki.internal");
    }

    @Test
    @DisplayName("getValue('port') returns 636")
    void getValue_port_returns636() {
        assertThat(configBean.getValue("port")).hasValue("636");
    }

    @Test
    @DisplayName("getValue('useSsl') returns true")
    void getValue_useSsl_returnsTrue() {
        assertThat(configBean.getValue("useSsl")).hasValue("true");
    }

    @Test
    @DisplayName("get for unknown key returns empty")
    void get_unknownKey_returnsEmpty() {
        assertThat(configBean.get("nonexistent-key")).isEmpty();
    }

    @Test
    @DisplayName("all seeded keys are present in cache")
    void allSeedKeysPresent() {
        assertThat(configBean.containsKey("host")).isTrue();
        assertThat(configBean.containsKey("port")).isTrue();
        assertThat(configBean.containsKey("baseDn")).isTrue();
        assertThat(configBean.containsKey("bindDn")).isTrue();
        assertThat(configBean.containsKey("bindPassword")).isTrue();
        assertThat(configBean.containsKey("useSsl")).isTrue();
        assertThat(configBean.containsKey("connectionTimeoutMs")).isTrue();
        assertThat(configBean.containsKey("readTimeoutMs")).isTrue();
    }
}
