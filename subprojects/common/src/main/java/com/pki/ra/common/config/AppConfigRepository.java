package com.pki.ra.common.config;

import com.pki.ra.common.model.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for the {@code app_config} table.
 *
 * <p>This repository is discovered by {@code @EnableJpaRepositories(basePackages = "com.pki.ra")}
 * declared in {@code DatabaseConfig} — no additional wiring is required.
 *
 * <p>At request time this repository is <em>never</em> called directly.
 * All reads go through the {@code ConfigBean} in-memory cache.
 * The repository is only used during cache load (startup) and manual refresh.
 */
public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    /** Returns all active config rows — called once on {@code ApplicationReadyEvent}. */
    List<AppConfig> findAllByIsActiveTrue();

    /** Direct DB lookup by key — used as a fallback if a key is absent from cache. */
    Optional<AppConfig> findByConfigKeyAndIsActiveTrue(String configKey);
}
