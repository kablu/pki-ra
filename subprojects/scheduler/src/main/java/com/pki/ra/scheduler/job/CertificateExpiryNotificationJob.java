package com.pki.ra.scheduler.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Quartz job that scans for certificates expiring within the next 30 days
 * and sends notification emails to certificate owners and RA operators.
 *
 * <p>Schedule: daily at 08:00 UTC (configured via Quartz trigger)
 *
 * <p>{@link DisallowConcurrentExecution} prevents overlapping runs
 * if the previous execution is still in progress.
 *
 * @author pki-ra
 * @since  1.0.0
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class CertificateExpiryNotificationJob implements Job {

    /**
     * Main job execution — queries expiring certificates and dispatches notifications.
     *
     * @param context Quartz job execution context (non-null)
     * @throws JobExecutionException if a fatal error occurs during execution
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("CertificateExpiryNotificationJob started — fireTime={}",
                 context.getFireTime());
        try {
            // TODO: inject CertificateRepository and NotificationService
            //       query certificates where expiry < NOW() + 30 days
            //       send email notifications per owner
            log.info("CertificateExpiryNotificationJob completed successfully");
        } catch (Exception ex) {
            log.error("CertificateExpiryNotificationJob failed", ex);
            throw new JobExecutionException(ex, false);
        }
    }
}
