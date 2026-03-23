package com.pki.ra.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Quartz Scheduler configuration backed by PostgreSQL job store.
 *
 * <p>Uses {@code JDBCJobStore} so scheduled jobs survive application restarts.
 * All Quartz tables ({@code QRTZ_*}) are created by Flyway migration.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Configuration
public class QuartzConfig {

    /**
     * Configures Quartz SchedulerFactory with persistent JDBC job store.
     *
     * @param dataSource the application DataSource (PostgreSQL)
     * @return configured {@link SchedulerFactoryBean}
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setOverwriteExistingJobs(true);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setQuartzProperties(quartzProperties());
        return factory;
    }

    private Properties quartzProperties() {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName",             "PkiRaScheduler");
        props.setProperty("org.quartz.scheduler.instanceId",               "AUTO");
        props.setProperty("org.quartz.jobStore.class",                     "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.setProperty("org.quartz.jobStore.driverDelegateClass",       "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        props.setProperty("org.quartz.jobStore.tablePrefix",               "QRTZ_");
        props.setProperty("org.quartz.jobStore.isClustered",               "false");
        props.setProperty("org.quartz.threadPool.class",                   "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount",             "5");
        props.setProperty("org.quartz.threadPool.threadPriority",          "5");
        return props;
    }
}
