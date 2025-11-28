package ke.skyworld.januscope.core.jobs.jobs;

import ke.skyworld.januscope.core.jobs.Job;
import ke.skyworld.januscope.core.monitoring.MonitoringEngine;
import ke.skyworld.januscope.core.notification.NotificationEngine;
import ke.skyworld.januscope.core.notification.TemplateEngine;
import ke.skyworld.januscope.domain.models.*;
import ke.skyworld.januscope.domain.repositories.UptimeCheckRepository;
import ke.skyworld.januscope.domain.repositories.ServiceRepository;
import ke.skyworld.januscope.domain.repositories.IncidentRepository;
import ke.skyworld.januscope.utils.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Job that performs uptime checks on all services
 */
public class MonitoringJob implements Job {
    private static final Logger logger = Logger.getLogger(MonitoringJob.class);
    
    private final MonitoringEngine monitoringEngine;
    private final NotificationEngine notificationEngine;
    private final List<Service> services;
    private final boolean enabled;
    private final UptimeCheckRepository uptimeCheckRepo;
    private final ServiceRepository serviceRepo;
    private final IncidentRepository incidentRepo;
    
    public MonitoringJob(MonitoringEngine monitoringEngine, 
                        NotificationEngine notificationEngine,
                        List<Service> services,
                        boolean enabled,
                        UptimeCheckRepository uptimeCheckRepo,
                        ServiceRepository serviceRepo,
                        IncidentRepository incidentRepo) {
        this.monitoringEngine = monitoringEngine;
        this.notificationEngine = notificationEngine;
        this.services = services;
        this.enabled = enabled;
        this.uptimeCheckRepo = uptimeCheckRepo;
        this.serviceRepo = serviceRepo;
        this.incidentRepo = incidentRepo;
    }
    
    @Override
    public String getName() {
        return "MonitoringJob";
    }
    
    @Override
    public void execute() throws Exception {
        logger.info("Starting monitoring job for {} services", services.size());
        
        // Perform uptime checks
        List<UptimeCheckResult> results = monitoringEngine.checkUptimeBatch(services);
        
        // Process results
        for (UptimeCheckResult result : results) {
            // Save to database if available
            if (uptimeCheckRepo != null) {
                uptimeCheckRepo.save(result);
            }
            
            // Update service status if available
            if (serviceRepo != null) {
                serviceRepo.updateStatus(result.getServiceId(), result.getStatus());
            }
            
            // Handle incidents
            if (!result.isUp()) {
                handleServiceDown(result);
            } else {
                handleServiceUp(result);
            }
        }
        
        logger.info("Monitoring job completed - {}/{} services UP", 
                   results.stream().filter(UptimeCheckResult::isUp).count(),
                   results.size());
    }
    
    private void handleServiceDown(UptimeCheckResult result) {
        // Check if there's already an active incident
        if (incidentRepo != null) {
            Integer activeIncident = incidentRepo.findActiveIncident(result.getServiceId());
            
            if (activeIncident == null) {
                // Create new incident
                incidentRepo.createIncident(result.getServiceId(), result.getErrorMessage());
                
                // Send alert
                sendDownAlert(result);
            }
        } else {
            // No database, just send alert
            sendDownAlert(result);
        }
    }
    
    private void handleServiceUp(UptimeCheckResult result) {
        // Check if there's an active incident to resolve
        if (incidentRepo != null) {
            Integer activeIncident = incidentRepo.findActiveIncident(result.getServiceId());
            
            if (activeIncident != null) {
                // Resolve incident
                incidentRepo.resolveIncident(activeIncident);
                logger.info("Service {} recovered, resolved incident {}", 
                           result.getServiceId(), activeIncident);
            }
        }
    }
    
    private void sendDownAlert(UptimeCheckResult result) {
        try {
            // Find service
            Service service = services.stream()
                .filter(s -> s.getServiceId().equals(result.getServiceId()))
                .findFirst()
                .orElse(null);
            
            if (service == null) {
                return;
            }
            
            // Create notification request
            NotificationRequest request = new NotificationRequest();
            request.setRecipient("admin@example.com");
            request.setSubject("ALERT: Service Down - " + service.getName());
            request.setEventType("SERVICE_DOWN");
            request.setServiceId(service.getServiceId());
            
            // Add variables
            request.addVariable("service_name", service.getName());
            request.addVariable("service_url", service.getUrl());
            request.addVariable("down_time", Instant.now().toString());
            request.addVariable("error_message", result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown");
            request.addVariable("http_code", result.getHttpCode() != null ? result.getHttpCode().toString() : "N/A");
            
            // Process template
            String template = TemplateEngine.getServiceDownTemplate();
            String message = TemplateEngine.process(template, request.getVariables());
            request.setMessage(message);
            
            // Send notification
            notificationEngine.sendToAll(request);
            
            logger.info("Sent down alert for service: {}", service.getName());
            
        } catch (Exception e) {
            logger.error("Failed to send down alert", e);
        }
    }
    
    @Override
    public String getDescription() {
        return "Monitors service uptime and sends alerts";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
