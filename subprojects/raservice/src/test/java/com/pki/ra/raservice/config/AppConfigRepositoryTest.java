package com.pki.ra.raservice.config;

import com.pki.ra.common.config.AppConfigRepository;
import com.pki.ra.common.model.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for {@link AppConfigRepository}.
 * Uses full Spring context with H2 in-memory database.
 * Each test rolls back via {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
@DisplayName("AppConfigRepository — integration")
class AppConfigRepositoryTest {

    @Autowired AppConfigRepository repository;

    private AppConfig persist(String key, String type, String value, boolean active) {
        var entity = AppConfig.builder()
                .configKey(key)
                .configType(type)
                .configValue(value)
                .isActive(active)
                .build();
        return repository.saveAndFlush(entity);
    }

    @Test
    @DisplayName("findAllActive: returns only rows where is_active = true")
    void findAllActive_returnsOnlyActiveRows() {
        persist("smtp-host", "MAIL", "smtp.pki.internal", true);
        persist("smtp-port", "MAIL", "587",               true);
        persist("smtp-from", "MAIL", "noreply@pki",       false); // inactive

        var result = repository.findAllActive();

        assertThat(result).extracting(AppConfig::getConfigKey)
                .contains("smtp-host", "smtp-port")
                .doesNotContain("smtp-from");
        assertThat(result).allMatch(AppConfig::isActive);
    }

    @Test
    @DisplayName("findAllActive: inactive row is excluded from results")
    void findAllActive_inactiveRow_isExcluded() {
        persist("smtp-host", "MAIL", "smtp.pki.internal", false);

        assertThat(repository.findAllActive())
                .extracting(AppConfig::getConfigKey)
                .doesNotContain("smtp-host");
    }

    @Test
    @DisplayName("findActiveByKey: returns active row for matching key")
    void findActiveByKey_matchingKey_returnsRow() {
        persist("smtp-host", "MAIL", "smtp.pki.internal", true);

        var result = repository.findActiveByKey("smtp-host");

        assertThat(result).isPresent();
        assertThat(result.get().getConfigValue()).isEqualTo("smtp.pki.internal");
    }

    @Test
    @DisplayName("findActiveByKey: returns empty for inactive row")
    void findActiveByKey_inactiveRow_returnsEmpty() {
        persist("smtp-host", "MAIL", "smtp.pki.internal", false);

        assertThat(repository.findActiveByKey("smtp-host")).isEmpty();
    }

    @Test
    @DisplayName("findActiveByKey: returns empty when key does not exist")
    void findActiveByKey_missingKey_returnsEmpty() {
        assertThat(repository.findActiveByKey("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("unique constraint (config_key, config_type) is enforced")
    void save_duplicateKeyAndType_throwsException() {
        persist("smtp-host", "MAIL", "smtp.pki.internal", true);

        var duplicate = AppConfig.builder()
                .configKey("smtp-host")
                .configType("MAIL")
                .configValue("another-host")
                .isActive(true)
                .build();

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("same config_key with different config_type is allowed")
    void save_sameKeyDifferentType_succeeds() {
        persist("ca-host", "CA",   "ca.pki.internal",   true);
        persist("ca-host", "MAIL", "smtp.pki.internal", true);

        assertThat(repository.findAllActive())
                .extracting(AppConfig::getConfigKey)
                .contains("ca-host");
    }
}
