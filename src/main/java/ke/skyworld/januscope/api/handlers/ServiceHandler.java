package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.domain.models.Service;
import ke.skyworld.januscope.domain.models.UptimeCheckResult;
import ke.skyworld.januscope.domain.models.SSLCheckResult;
import ke.skyworld.januscope.domain.repositories.ServiceRepository;
import ke.skyworld.januscope.domain.repositories.UptimeCheckRepository;
import ke.skyworld.januscope.domain.repositories.SSLCheckRepository;
import ke.skyworld.januscope.domain.repositories.IncidentRepository;

import java.util.List;
import java.util.Map;

/**
 * Service management API handler
 * Endpoints: /api/services/*
 */
public class ServiceHandler extends BaseHandler {
    private final ServiceRepository serviceRepository;
    private final UptimeCheckRepository uptimeCheckRepository;
    private final SSLCheckRepository sslCheckRepository;
    private final IncidentRepository incidentRepository;
    private final ke.skyworld.januscope.core.monitoring.MonitoringEngine monitoringEngine;
    
    public ServiceHandler(ServiceRepository serviceRepository,
                         UptimeCheckRepository uptimeCheckRepository,
                         SSLCheckRepository sslCheckRepository,
                         IncidentRepository incidentRepository,
                         ke.skyworld.januscope.core.monitoring.MonitoringEngine monitoringEngine) {
        this.serviceRepository = serviceRepository;
        this.uptimeCheckRepository = uptimeCheckRepository;
        this.sslCheckRepository = sslCheckRepository;
        this.incidentRepository = incidentRepository;
        this.monitoringEngine = monitoringEngine;
    }
    
    @Override
    protected void handleGet(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.matches(".*/services/\\d+/uptime$")) {
            handleGetUptimeHistory(exchange);
        } else if (path.matches(".*/services/\\d+/ssl$")) {
            handleGetSSLHistory(exchange);
        } else if (path.matches(".*/services/\\d+/incidents$")) {
            handleGetIncidents(exchange);
        } else if (path.matches(".*/services/\\d+$")) {
            handleGetService(exchange);
        } else {
            handleGetAllServices(exchange);
        }
    }
    
    @Override
    protected void handlePost(HttpServerExchange exchange) throws Exception {
        handleCreateService(exchange);
    }
    
    @Override
    protected void handlePut(HttpServerExchange exchange) throws Exception {
        handleUpdateService(exchange);
    }
    
    @Override
    protected void handleDelete(HttpServerExchange exchange) throws Exception {
        handleDeleteService(exchange);
    }
    
    /**
     * GET /api/services
     */
    private void handleGetAllServices(HttpServerExchange exchange) {
        try {
            List<Service> services = serviceRepository.findAll();
            sendSuccess(exchange, services, "Services retrieved");
        } catch (Exception e) {
            logger.error("Failed to get services", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get services");
        }
    }
    
    /**
     * GET /api/services/:id
     */
    private void handleGetService(HttpServerExchange exchange) {
        try {
            String idStr = getPathParam(exchange, "id");
            int serviceId = Integer.parseInt(idStr);
            
            Service service = serviceRepository.findById(serviceId);
            
            if (service != null) {
                sendSuccess(exchange, service, "Service retrieved");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Service not found");
            }
        } catch (NumberFormatException e) {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid service ID");
        } catch (Exception e) {
            logger.error("Failed to get service", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get service");
        }
    }
    
    /**
     * POST /api/services
     */
    private void handleCreateService(HttpServerExchange exchange) throws Exception {
        try {
            Service service = parseRequestBody(exchange, Service.class);
            
            // Validation
            if (service.getName() == null || service.getName().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Service name is required");
                return;
            }
            
            if (service.getUrl() == null || service.getUrl().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Service URL is required");
                return;
            }
            
            // Set defaults (these are primitives, so check for 0 instead of null)
            if (service.getCheckIntervalSeconds() == 0) {
                service.setCheckIntervalSeconds(300); // 5 minutes
            }
            
            if (service.getTimeoutMs() == 0) {
                service.setTimeoutMs(10000); // 10 seconds
            }
            
            if (service.getMaxRetries() == 0) {
                service.setMaxRetries(3);
            }
            
            Service created = serviceRepository.create(service);
            
            if (created != null) {
                // Trigger immediate check for newly created service
                if (monitoringEngine != null) {
                    final Service serviceToCheck = created;
                    final boolean isHttps = serviceToCheck.getUrl().toLowerCase().startsWith("https://");
                    
                    new Thread(() -> {
                        try {
                            // Check uptime immediately
                            ke.skyworld.januscope.domain.models.UptimeCheckResult uptimeResult = monitoringEngine.checkUptime(serviceToCheck);
                            
                            // Save uptime check result to database
                            if (uptimeResult != null && uptimeCheckRepository != null) {
                                uptimeCheckRepository.save(uptimeResult);
                                logger.debug("Saved initial uptime check for service: {}", serviceToCheck.getName());
                            }
                            
                            // Update service status
                            if (uptimeResult != null && serviceRepository != null) {
                                serviceRepository.updateStatus(serviceToCheck.getServiceId(), uptimeResult.getStatus());
                                logger.debug("Updated service status to: {}", uptimeResult.getStatus());
                            }
                            
                            logger.info("✓ Completed initial uptime check for new service: {} - Status: {}", 
                                       serviceToCheck.getName(), 
                                       uptimeResult != null ? uptimeResult.getStatus() : "UNKNOWN");
                            
                            // Also check SSL for HTTPS services
                            if (isHttps) {
                                ke.skyworld.januscope.domain.models.SSLCheckResult sslResult = monitoringEngine.checkSSL(serviceToCheck);
                                
                                // Save SSL check result to database
                                if (sslResult != null && sslCheckRepository != null) {
                                    sslCheckRepository.save(sslResult);
                                    logger.debug("Saved initial SSL check for service: {}", serviceToCheck.getName());
                                }
                                
                                logger.info("✓ Completed initial SSL check for new service: {} - Valid: {}, Days remaining: {}", 
                                           serviceToCheck.getName(), 
                                           sslResult != null ? sslResult.isValid() : false,
                                           sslResult != null ? sslResult.getDaysRemaining() : 0);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to trigger initial check for service {}: {}", serviceToCheck.getServiceId(), e.getMessage());
                        }
                    }).start();
                }
                
                sendSuccess(exchange, created, "Service created");
            } else {
                sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create service");
            }
            
        } catch (Exception e) {
            logger.error("Failed to create service", e);
            // Check if it's a SQL exception
            if (e.getCause() instanceof java.sql.SQLException) {
                String errorMessage = extractUserFriendlyError((java.sql.SQLException) e.getCause());
                sendError(exchange, StatusCodes.BAD_REQUEST, errorMessage);
            } else {
                sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create service: " + e.getMessage());
            }
        }
    }
    
    /**
     * PUT /api/services/:id
     */
    private void handleUpdateService(HttpServerExchange exchange) throws Exception {
        try {
            String idStr = getPathParam(exchange, "id");
            int serviceId = Integer.parseInt(idStr);
            
            Service service = parseRequestBody(exchange, Service.class);
            service.setServiceId(serviceId);
            
            boolean updated = serviceRepository.update(service);
            
            if (updated) {
                Service updatedService = serviceRepository.findById(serviceId);
                sendSuccess(exchange, updatedService, "Service updated");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Service not found");
            }
            
        } catch (NumberFormatException e) {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid service ID");
        } catch (Exception e) {
            logger.error("Failed to update service", e);
            // Check if it's a SQL exception
            if (e.getCause() instanceof java.sql.SQLException) {
                String errorMessage = extractUserFriendlyError((java.sql.SQLException) e.getCause());
                sendError(exchange, StatusCodes.BAD_REQUEST, errorMessage);
            } else {
                sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update service: " + e.getMessage());
            }
        }
    }
    
    /**
     * DELETE /api/services/:id
     */
    private void handleDeleteService(HttpServerExchange exchange) {
        try {
            String idStr = getPathParam(exchange, "id");
            int serviceId = Integer.parseInt(idStr);
            
            boolean deleted = serviceRepository.delete(serviceId);
            
            if (deleted) {
                sendSuccess(exchange, null, "Service deleted");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Service not found");
            }
            
        } catch (NumberFormatException e) {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid service ID");
        } catch (Exception e) {
            logger.error("Failed to delete service", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete service");
        }
    }
    
    /**
     * GET /api/services/:id/uptime
     */
    private void handleGetUptimeHistory(HttpServerExchange exchange) {
        try {
            String idStr = getPathParam(exchange, "id");
            int serviceId = Integer.parseInt(idStr);
            
            Integer limit = getQueryParamAsInt(exchange, "limit", 100);
            
            List<UptimeCheckResult> history = uptimeCheckRepository.findByServiceId(serviceId, limit);
            sendSuccess(exchange, history, "Uptime history retrieved");
            
        } catch (NumberFormatException e) {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid service ID");
        } catch (Exception e) {
            logger.error("Failed to get uptime history", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get uptime history");
        }
    }
    
    /**
     * GET /api/services/:id/ssl
     */
    private void handleGetSSLHistory(HttpServerExchange exchange) {
        try {
            // Extract service ID from path like /api/v1/services/123/ssl
            String path = exchange.getRequestPath();
            String[] parts = path.split("/");
            String idStr = null;
            
            // Find the part after "services"
            for (int i = 0; i < parts.length - 1; i++) {
                if ("services".equals(parts[i]) && i + 1 < parts.length) {
                    idStr = parts[i + 1];
                    break;
                }
            }
            
            if (idStr == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid service ID");
                return;
            }
            
            int serviceId = Integer.parseInt(idStr);
            
            SSLCheckResult latest = sslCheckRepository.findLatestByServiceId(serviceId);
            
            if (latest != null) {
                sendSuccess(exchange, latest, "SSL check retrieved");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "No SSL check found");
            }
            
        } catch (NumberFormatException e) {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid service ID");
        } catch (Exception e) {
            logger.error("Failed to get SSL history", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get SSL history");
        }
    }
    
    /**
     * GET /api/services/:id/incidents
     */
    private void handleGetIncidents(HttpServerExchange exchange) {
        try {
            String idStr = getPathParam(exchange, "id");
            int serviceId = Integer.parseInt(idStr);
            
            Integer limit = getQueryParamAsInt(exchange, "limit", 50);
            
            // TODO: Implement findByServiceId in IncidentRepository
            sendSuccess(exchange, List.of(), "Incidents retrieved");
            
        } catch (NumberFormatException e) {
            sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid service ID");
        } catch (Exception e) {
            logger.error("Failed to get incidents", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get incidents");
        }
    }
}
