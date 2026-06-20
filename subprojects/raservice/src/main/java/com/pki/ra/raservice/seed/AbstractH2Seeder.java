package com.pki.ra.raservice.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Template base class for all H2 development data seeders.
 *
 * <h3>What this eliminates</h3>
 * Every seeder used to duplicate:
 * <ul>
 *   <li>{@link ApplicationRunner#run(ApplicationArguments)} boilerplate</li>
 *   <li>The {@code if (count() > 0) skip} guard</li>
 *   <li>"already has data — skipping" / "N rows inserted" log lines</li>
 * </ul>
 *
 * <h3>Subclass contract — 2 methods only</h3>
 * <ol>
 *   <li>{@link #count()} — return the current row count from the repository;
 *       used to decide whether seeding is needed.</li>
 *   <li>{@link #seed()} — insert the development data rows into the repository.</li>
 * </ol>
 *
 * <h3>Annotations on concrete classes</h3>
 * Each concrete seeder carries its own {@code @Component}, {@code @Profile("h2")},
 * and (where ordering matters) {@code @Order}. Those annotations are not on
 * this abstract class so the base class itself is never registered as a bean.
 *
 * <h3>Minimum viable seeder</h3>
 * <pre>{@code
 * @Component
 * @Profile("h2")
 * @Order(1)
 * public class WidgetSeeder extends AbstractH2Seeder {
 *
 *     private final WidgetRepository repository;
 *
 *     public WidgetSeeder(WidgetRepository repository) {
 *         this.repository = repository;
 *     }
 *
 *     @Override protected long count() { return repository.count(); }
 *
 *     @Override
 *     protected void seed() {
 *         repository.saveAll(List.of(
 *             new Widget("widget-1"),
 *             new Widget("widget-2")
 *         ));
 *     }
 * }
 * }</pre>
 *
 * @author pki-ra
 * @since  1.0.0
 */
public abstract class AbstractH2Seeder implements ApplicationRunner {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Template method — runs the idempotent seed flow.
     *
     * <p>Marked {@code final} — the skip guard and log messages must
     * not be altered or bypassed by subclasses.
     */
    @Override
    public final void run(ApplicationArguments args) {
        if (count() > 0) {
            log.info("{}: already has data — skipping seed.", getClass().getSimpleName());
            return;
        }
        log.info("{}: seeding development data...", getClass().getSimpleName());
        seed();
        log.info("{}: {} row(s) inserted.", getClass().getSimpleName(), count());
    }

    // =========================================================================
    // Subclass contract
    // =========================================================================

    /**
     * Returns the current number of rows in the target table.
     * Called twice: once to check whether seeding is needed, once to report
     * the final row count.
     *
     * @return current row count — must be {@code >= 0}
     */
    protected abstract long count();

    /**
     * Inserts the development data rows into the repository.
     * Called only when {@link #count()} returns {@code 0}.
     */
    protected abstract void seed();
}
