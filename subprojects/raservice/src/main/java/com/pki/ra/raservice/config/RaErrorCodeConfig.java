package com.pki.ra.raservice.config;

import com.pki.ra.common.error.ErrorCodeKeySource;
import com.pki.ra.raservice.error.RaErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers raservice's error code enum with the
 * {@link com.pki.ra.common.error.ErrorCatalogStartupValidator}.
 *
 * <p>The validator collects all {@link ErrorCodeKeySource} beans across every
 * module at startup and verifies each code has a matching row in the
 * {@code error_catalog} DB table.
 *
 * <p>One-liner registration — method-reference to {@link RaErrorCode#values()}.
 *
 * @see com.pki.ra.common.error.ErrorCatalogStartupValidator
 * @see RaErrorCode
 * @author pki-ra
 * @since  1.0.0
 */
@Configuration
public class RaErrorCodeConfig {

    @Bean
    public ErrorCodeKeySource raErrorCodeKeySource() {
        return RaErrorCode::values;
    }
}
