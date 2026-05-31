package com.pki.ra.common.config;

import com.pki.ra.common.config.dto.AppConfigDto;
import com.pki.ra.common.model.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigBean")
class ConfigBeanTest {

    @Mock AppConfigRepository repository;

    ConfigBean configBean;

    @BeforeEach
    void setUp() {
        configBean = new ConfigBean(repository);
    }

    private AppConfig row(String key, String type, String value) {
        return AppConfig.builder()
                .configKey(key)
                .configType(type)
                .configValue(value)
                .isActive(true)
                .build();
    }

    private List<AppConfig> ldapRows() {
        return List.of(
            row("host",                "LDAP", "ldap.pki.internal"),
            row("port",                "LDAP", "636"),
            row("baseDn",              "LDAP", "DC=pki,DC=internal"),
            row("bindDn",              "LDAP", "CN=svc-pki-bind"),
            row("bindPassword",        "LDAP", "secret"),
            row("useSsl",              "LDAP", "true"),
            row("connectionTimeoutMs", "LDAP", "5000"),
            row("readTimeoutMs",       "LDAP", "10000")
        );
    }

    @Test
    @DisplayName("loadOnReady: caches all active rows keyed by config_key")
    void loadOnReady_cachesAllRows() {
        when(repository.findAllActive()).thenReturn(ldapRows());

        configBean.loadOnReady();

        assertThat(configBean.size()).isEqualTo(8);
        assertThat(configBean.containsKey("host")).isTrue();
        assertThat(configBean.containsKey("port")).isTrue();
    }

    @Test
    @DisplayName("get: returns AppConfigDto with correct fields")
    void get_returnsCorrectDto() {
        when(repository.findAllActive()).thenReturn(ldapRows());
        configBean.loadOnReady();

        var dto = configBean.get("host");

        assertThat(dto).isPresent();
        assertThat(dto.get().configKey()).isEqualTo("host");
        assertThat(dto.get().configType()).isEqualTo("LDAP");
        assertThat(dto.get().configValue()).isEqualTo("ldap.pki.internal");
        assertThat(dto.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("getValue: returns just the value string")
    void getValue_returnsValueString() {
        when(repository.findAllActive()).thenReturn(ldapRows());
        configBean.loadOnReady();

        assertThat(configBean.getValue("port")).hasValue("636");
    }

    @Test
    @DisplayName("get: returns empty for unknown key")
    void get_unknownKey_returnsEmpty() {
        when(repository.findAllActive()).thenReturn(ldapRows());
        configBean.loadOnReady();

        assertThat(configBean.get("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("loadOnReady: clears old cache before reloading")
    void loadOnReady_clearsCacheBeforeReload() {
        when(repository.findAllActive()).thenReturn(ldapRows());
        configBean.loadOnReady();
        assertThat(configBean.size()).isEqualTo(8);

        when(repository.findAllActive()).thenReturn(List.of());
        configBean.loadOnReady();

        assertThat(configBean.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("loadOnReady: empty table leaves cache empty")
    void loadOnReady_emptyTable_cachEmpty() {
        when(repository.findAllActive()).thenReturn(List.of());

        configBean.loadOnReady();

        assertThat(configBean.size()).isEqualTo(0);
        assertThat(configBean.getAll()).isEmpty();
    }

    @Test
    @DisplayName("refresh: reloads cache and returns new entry count")
    void refresh_reloadsCacheAndReturnsCount() {
        when(repository.findAllActive()).thenReturn(ldapRows());

        var count = configBean.refresh();

        assertThat(count).isEqualTo(8);
        verify(repository, times(1)).findAllActive();
    }
}
