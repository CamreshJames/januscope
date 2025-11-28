package ke.skyworld.januscope.core.jobs.jobs;

import ke.skyworld.januscope.core.jobs.Job;
import ke.skyworld.januscope.core.monitoring.MonitoringEngine;
import ke.skyworld.januscope.core.notification.NotificationEngine;
import ke.skyworld.januscope.domain.models.Service;
import ke.skyworld.januscope.domain.models.SSLCheckResult;
import ke.skyworld.januscope.domain.repositories.SSLCheckRepository;
import ke.skyworld.januscope.utils.Logger;

import java.util.List;

/**
 * Job that performs SSL certificate checks on all HTTPS services
 */
public class SSLCheckJob implements Job {
    private static final Logger logger = Logger.getLogger(SSLCheckJob.class);
    
    private final MonitoringEngine monitoringEngine;
    private final NotificationEngine notificationEngine;
    private final List<Service> services;
    private final boolean enabled;
    private final int expiryThresholdDays;
    private final SSLCheckRepository sslCheckRepo;
    
    public SSLCheckJob(MonitoringEngine monitoringEngine,
                      NotificationEngine notificationEngine,
                      List<Service> services,
                      boolean enabled,
                      int expiryThresholdDays,
                      SSLCheckRepository sslCheckRepo) {
        this.monitoringEngine = monitoringEngine;
        this.notificationEngine = notificationEngine;
        this.services = services;
        this.enabled = enabled;
        this.expiryThresholdDays = expiryThresholdDays;
        this.sslCheckRepo = sslCheckRepo;
    }
    
    @Override
    public String getName() {
        return "SSLCheckJob";
    }
    
    @Override
    public void execute() throws Exception {
        logger.info("Starting SSL check job for {} services", services.size());
        
        int httpsCount = 0;
        int checkedCount = 0;
        
        for (Service service : services) {
            // Only check HTTPS services
            if (!service.getUrl().toLowerCase().startsWith("https://")) {
                continue;
            }
            
            httpsCount++;
            
            try {
                // Perform SSL check
                SSLCheckResult result = monitoringEngine.checkSSL(service);
                
                // Save to database
                if (sslCheckRepo != null && result != null) {
                    sslCheckRepo.save(result);
                    checkedCount++;
                    
                    // Check if certificate is expiring soon
                    if (result.getDaysRemaining() != null && 
                        result.getDaysRemaining() <= expiryThresholdDays &&
                        result.getDaysRemaining() > 0) {
                        logger.warn("SSL certificate for {} expires in {} days", 
                                   service.getName(), result.getDaysRemaining());
                        // TODO: Send expiry notification
                    }
                }
                
            } catch (Exception e) {
                logger.error("Failed to check SSL for service: " + service.getName(), e);
            }
        }
        
        logger.info("SSL check job completed - checked {}/{} HTTPS services", 
                   checkedCount, httpsCount);
    }
    
    @Override
    public String getDescription() {
        return "Checks SSL certificates for HTTPS services";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
