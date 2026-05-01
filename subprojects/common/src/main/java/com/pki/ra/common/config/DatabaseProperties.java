package com.pki.ra.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding for all properties declared in {@code db.properties}.
 *
 * <p>Properties are loaded via {@code @PropertySource("classpath:db.properties")}
 * on {@link DataSourceConfig} and bound here under the {@code db} prefix.
 *
 * <p>Validation annotations ensure the application fails fast at startup
 * if required DB properties are missing or out of range.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "db")
public class DatabaseProperties {

    @Valid
    @NotNull
    private final Datasource datasource = new Datasource();

    @Valid
    @NotNull
    private final H2 h2 = new H2();

    // ── DataSource ────────────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Datasource {

        @NotBlank
        private String url;

        @NotBlank
        private String username;

        /** Nullable — H2 default user has no password. */
        private String password = "";

        @NotBlank
        private String driverClassName;

        @Valid
        @NotNull
        private final Hikari hikari = new Hikari();
    }

    // ── Hikari pool ───────────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class Hikari {

        private String poolName = "PKI-RA-Pool";

        @Min(1)
        private int maximumPoolSize = 5;

        @Min(0)
        private int minimumIdle = 1;

        @Min(1000)
        private long connectionTimeout = 20_000;

        @Min(10_000)
        private long idleTimeout = 300_000;

        @Min(30_000)
        private long maxLifetime = 900_000;

        private boolean autoCommit = true;
    }

    // ── H2 Console ────────────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class H2 {

        @Valid
        @NotNull
        private final Console console = new Console();

        @Getter
        @Setter
        public static class Console {

            private boolean enabled = false;
            private String path    = "/h2-console";

            @Valid
            @NotNull
            private final Settings settings = new Settings();

            @Getter
            @Setter
            public static class Settings {
                private boolean webAllowOthers = false;
                private boolean trace          = false;
            }
        }
    }
}
