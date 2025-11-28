package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.domain.repositories.ServiceRepository;
import ke.skyworld.januscope.domain.repositories.UptimeCheckRepository;
import ke.skyworld.januscope.domain.repositories.SSLCheckRepository;
import ke.skyworld.januscope.domain.repositories.IncidentRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Dashboard API handler
 * Endpoints: /api/dashboard/*
 */
public class DashboardHandler extends BaseHandler {
    private final ServiceRepository serviceRepository;
    private final UptimeCheckRepository uptimeCheckRepository;
    private final SSLCheckRepository sslCheckRepository;
    private final IncidentRepository incidentRepository;
    
    public DashboardHandler(ServiceRepository serviceRepository,
                           UptimeCheckRepository uptimeCheckRepository,
                           SSLCheckRepository sslCheckRepository,
                           IncidentRepository incidentRepository) {
        this.serviceRepository = serviceRepository;
        this.uptimeCheckRepository = uptimeCheckRepository;
        this.sslCheckRepository = sslCheckRepository;
        this.incidentRepository = incidentRepository;
    }
    
    @Override
    protected void handleGet(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.endsWith("/summary")) {
            handleGetSummary(exchange);
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }
    
    /**
     * GET /api/dashboard/summary
     */
    private void handleGetSummary(HttpServerExchange exchange) {
        try {
            Map<String, Object> summary = new HashMap<>();
            
            // Total services
            int totalServices = serviceRepository.countAll();
            summary.put("totalServices", totalServices);
            
            // Services by status
            int servicesUp = serviceRepository.countByStatus("UP");
            int servicesDown = serviceRepository.countByStatus("DOWN");
            int servicesUnknown = serviceRepository.countByStatus("UNKNOWN");
            
            summary.put("servicesUp", servicesUp);
            summary.put("servicesDown", servicesDown);
            summary.put("servicesUnknown", servicesUnknown);
            
            // Uptime percentage (last 24h)
            double uptimePercentage = uptimeCheckRepository.calculateUptimePercentage(24);
            summary.put("uptimePercentage24h", Math.round(uptimePercentage * 100.0) / 100.0);
            
            // Average response time (last 24h)
            double avgResponseTime = uptimeCheckRepository.calculateAverageResponseTime(24);
            summary.put("avgResponseTime24h", Math.round(avgResponseTime));
            
            // Active incidents
            int activeIncidents = incidentRepository.countActive();
            summary.put("activeIncidents", activeIncidents);
            
            // SSL warnings (expiring within 30 days)
            int sslWarnings = sslCheckRepository.countExpiringWithin(30);
            summary.put("sslWarnings", sslWarnings);
            
            sendSuccess(exchange, summary, "Dashboard summary retrieved");
            
        } catch (Exception e) {
            logger.error("Failed to get dashboard summary", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get dashboard summary");
        }
    }
}
