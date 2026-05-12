package com.pki.ra.common.config;

import com.pki.ra.common.model.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for the {@code app_config} table.
 *
 * <p>Discovered automatically by {@code @EnableJpaRepositories(basePackages = "com.pki.ra")}
 * declared in {@code DatabaseConfig} — no additional wiring required.
 *
 * <p>At request time this repository is <em>never</em> called directly.
 * All reads go through the {@code ConfigBean} in-memory cache.
 * The repository is only used during cache load (startup) and manual refresh.
 */
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    /** JPQL — returns all active config entries; called once on {@code ApplicationReadyEvent}. */
    @Query("SELECT c FROM AppConfig c WHERE c.isActive = true")
    List<AppConfig> findAllActive();

    /** JPQL — direct lookup by key; used as fallback if a key is absent from cache. */
    @Query("SELECT c FROM AppConfig c WHERE c.configKey = :configKey AND c.isActive = true")
    Optional<AppConfig> findActiveByKey(String configKey);
}
