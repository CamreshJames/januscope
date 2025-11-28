package ke.skyworld.januscope.api.server;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import ke.skyworld.januscope.api.dto.ApiResponse;
import ke.skyworld.januscope.core.engine.BaseEngine;
import ke.skyworld.januscope.core.engine.EngineException;
import ke.skyworld.januscope.core.jobs.JobEngine;
import ke.skyworld.januscope.core.jobs.ScheduledJob;
import ke.skyworld.januscope.core.monitoring.MonitoringEngine;
import ke.skyworld.januscope.core.notification.NotificationEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Undertow REST API Server
 */
public class UndertowServer extends BaseEngine {
    private Undertow server;
    private String host;
    private int port;
    private MonitoringEngine monitoringEngine;
    private NotificationEngine notificationEngine;
    private JobEngine jobEngine;
    
    @Override
    protected void doInitialize() throws Exception {
        // Get server config
        host = (String) context.getAttribute("host");
        if (host == null) host = "0.0.0.0";
        
        Integer portObj = (Integer) context.getAttribute("port");
        port = portObj != null ? portObj : 9876;
        
        // Get engines from context
        monitoringEngine = context.getEngine("monitoring", MonitoringEngine.class);
        notificationEngine = context.getEngine("notification", NotificationEngine.class);
        jobEngine = context.getEngine("job", JobEngine.class);
        
        logger.info("Undertow server configured - {}:{}", host, port);
    }
    
    @Override
    protected void doStart() throws Exception {
        // Get API version from config
        ke.skyworld.januscope.config.ConfigNode serverConfig = 
            (ke.skyworld.januscope.config.ConfigNode) context.getAttribute("server-config");
        String apiVersion = "v1"; // default
        boolean authEnabled = true; // default
        java.util.Set<String> publicEndpoints = new java.util.HashSet<>();
        
        if (serverConfig != null) {
            ke.skyworld.januscope.config.ConfigNode apiConfig = serverConfig.getChild("api");
            if (apiConfig != null) {
                String version = apiConfig.getChildValue("version");
                if (version != null) {
                    apiVersion = version;
                }
            }
            
            // Get auth config
            ke.skyworld.januscope.config.ConfigNode authConfig = serverConfig.getChild("auth");
            if (authConfig != null) {
                authEnabled = authConfig.getChildValueAsBoolean("enabled", true);
                
                // Get public endpoints
                ke.skyworld.januscope.config.ConfigNode publicEndpointsNode = authConfig.getChild("publicEndpoints");
                if (publicEndpointsNode != null) {
                    for (ke.skyworld.januscope.config.ConfigNode endpoint : publicEndpointsNode.getChildren("endpoint")) {
                        String path = endpoint.getValue();
                        if (path != null) {
                            publicEndpoints.add(path);
                            // Also add versioned path
                            if (!path.contains("/api/")) {
                                publicEndpoints.add("/api/" + apiVersion + path.replace("/api", ""));
                            }
                        }
                    }
                }
            }
        }
        
        final String API_PREFIX = "/api/" + apiVersion;
        logger.info("API Version: {}, Base Path: {}", apiVersion, API_PREFIX);
        logger.info("Authentication: {}", authEnabled ? "ENABLED" : "DISABLED");
        logger.info("Public Endpoints: {}", publicEndpoints);
        
        // Get repositories from context
        ke.skyworld.januscope.core.database.DatabaseEngine dbEngine = 
            context.getEngine("database", ke.skyworld.januscope.core.database.DatabaseEngine.class);
        ke.skyworld.januscope.core.security.SecurityEngine securityEngine =
            context.getEngine("security", ke.skyworld.januscope.core.security.SecurityEngine.class);
        
        // Initialize repositories
        ke.skyworld.januscope.domain.repositories.ServiceRepository serviceRepo = null;
        ke.skyworld.januscope.domain.repositories.UptimeCheckRepository uptimeRepo = null;
        ke.skyworld.januscope.domain.repositories.SSLCheckRepository sslRepo = null;
        ke.skyworld.januscope.domain.repositories.IncidentRepository incidentRepo = null;
        ke.skyworld.januscope.domain.repositories.UserRepository userRepo = null;
        ke.skyworld.januscope.domain.repositories.ContactGroupRepository contactGroupRepo = null;
        ke.skyworld.januscope.domain.repositories.ContactMemberRepository contactMemberRepo = null;
        ke.skyworld.januscope.domain.repositories.SettingsRepository settingsRepo = null;
        ke.skyworld.januscope.domain.repositories.SystemRepository systemRepo = null;
        ke.skyworld.januscope.core.auth.AuthService authService = null;
        
        ke.skyworld.januscope.core.auth.JwtTokenManager jwtTokenManager = null;
        
        if (dbEngine != null) {
            serviceRepo = new ke.skyworld.januscope.domain.repositories.ServiceRepository(dbEngine);
            uptimeRepo = new ke.skyworld.januscope.domain.repositories.UptimeCheckRepository(dbEngine);
            sslRepo = new ke.skyworld.januscope.domain.repositories.SSLCheckRepository(dbEngine);
            incidentRepo = new ke.skyworld.januscope.domain.repositories.IncidentRepository(dbEngine);
            userRepo = new ke.skyworld.januscope.domain.repositories.UserRepository(dbEngine);
            contactGroupRepo = new ke.skyworld.januscope.domain.repositories.ContactGroupRepository(dbEngine);
            contactMemberRepo = new ke.skyworld.januscope.domain.repositories.ContactMemberRepository(dbEngine);
            settingsRepo = new ke.skyworld.januscope.domain.repositories.SettingsRepository(dbEngine);
            systemRepo = new ke.skyworld.januscope.domain.repositories.SystemRepository(dbEngine);
            
            if (securityEngine != null) {
                // Create JWT token manager (shared between AuthService and AuthenticationMiddleware)
                jwtTokenManager = new ke.skyworld.januscope.core.auth.JwtTokenManager(securityEngine.getJwtSecret());
                authService = new ke.skyworld.januscope.core.auth.AuthService(userRepo, securityEngine.getJwtSecret(), notificationEngine);
            }
        }
        
        // Build routing handler with elegant route registry
        RoutingHandler router = new RoutingHandler();
        RouteRegistry registry = new RouteRegistry(router, API_PREFIX);
        
        // ═══════════════════════════════════════════════════════════════════════
        // HEALTH & SYSTEM ENDPOINTS
        // ═══════════════════════════════════════════════════════════════════════
        registry
            .get("/api/health", this::handleHealth, "Health check endpoint", false)
            .get(API_PREFIX + "/health", this::handleHealth, "Health check (versioned)", false)
            .get(API_PREFIX + "/system/status", this::handleSystemStatus, "System status and uptime", true)
            .get(API_PREFIX + "/system/engines", this::handleEngines, "Engine status and health", true)
            .get(API_PREFIX + "/monitoring/stats", this::handleMonitoringStats, "Real-time monitoring statistics", true)
            .get(API_PREFIX + "/notifications/channels", this::handleNotificationChannels, "Active notification channels", true)
            .get(API_PREFIX + "/jobs", this::handleJobs, "Scheduled background jobs", true);
        
        // ═══════════════════════════════════════════════════════════════════════
        // AUTHENTICATION - JWT-based security
        // ═══════════════════════════════════════════════════════════════════════
        if (authService != null) {
            ke.skyworld.januscope.api.handlers.AuthHandler authHandler = 
                new ke.skyworld.januscope.api.handlers.AuthHandler(authService);
            
            registry.group("auth", authHandler,
                RouteRegistry.EndpointConfig.post("/login", "Authenticate user and get JWT tokens", false),
                RouteRegistry.EndpointConfig.post("/register", "Register new user account", false),
                RouteRegistry.EndpointConfig.post("/refresh", "Refresh expired access token", false),
                RouteRegistry.EndpointConfig.post("/forgot-password", "Request password reset email", false),
                RouteRegistry.EndpointConfig.post("/reset-password", "Reset password with token", false),
                RouteRegistry.EndpointConfig.get("/me", "Get authenticated user profile", true)
            );
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // USER MANAGEMENT - Admin approval workflow with auto-generated credentials
        // ═══════════════════════════════════════════════════════════════════════
        if (userRepo != null && securityEngine != null) {
            ke.skyworld.januscope.api.handlers.UserManagementHandler userMgmtHandler = 
                new ke.skyworld.januscope.api.handlers.UserManagementHandler(
                    userRepo, securityEngine, notificationEngine);
            
            registry.group("users", userMgmtHandler,
                // Core CRUD
                RouteRegistry.EndpointConfig.get("", "List users with filters (role, status, branch)", true),
                RouteRegistry.EndpointConfig.post("", "Create user (admin only, auto-approved)", true),
                RouteRegistry.EndpointConfig.get("/{id}", "Get user profile and details", true),
                RouteRegistry.EndpointConfig.put("/{id}", "Update user profile information", true),
                RouteRegistry.EndpointConfig.delete("/{id}", "Soft delete user (admin only)", true),
                // Approval workflow
                RouteRegistry.EndpointConfig.get("/pending", "List users awaiting admin approval", true),
                RouteRegistry.EndpointConfig.post("/{id}/approve", "Approve pending user registration", true),
                RouteRegistry.EndpointConfig.post("/{id}/reject", "Reject user registration with reason", true),
                // Account management
                RouteRegistry.EndpointConfig.post("/{id}/activate", "Activate deactivated user account", true),
                RouteRegistry.EndpointConfig.post("/{id}/deactivate", "Temporarily disable user account", true),
                RouteRegistry.EndpointConfig.put("/{id}/password", "Change user password (self or admin)", true),
                RouteRegistry.EndpointConfig.put("/{id}/role", "Change user role (admin only)", true)
            );
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // MONITORING SERVICES - Uptime, SSL, incidents tracking
        // ═══════════════════════════════════════════════════════════════════════
        if (serviceRepo != null) {
            ke.skyworld.januscope.api.handlers.ServiceHandler serviceHandler = 
                new ke.skyworld.januscope.api.handlers.ServiceHandler(
                    serviceRepo, uptimeRepo, sslRepo, incidentRepo, monitoringEngine);
            
            registry.group("services", serviceHandler,
                RouteRegistry.EndpointConfig.get("", "List all monitored services with status", true),
                RouteRegistry.EndpointConfig.post("", "Add new service to monitor", true),
                RouteRegistry.EndpointConfig.get("/{id}", "Get service configuration and current status", true),
                RouteRegistry.EndpointConfig.put("/{id}", "Update service configuration", true),
                RouteRegistry.EndpointConfig.delete("/{id}", "Remove service from monitoring", true),
                RouteRegistry.EndpointConfig.get("/{id}/uptime", "Get uptime history and statistics", true),
                RouteRegistry.EndpointConfig.get("/{id}/ssl", "Get SSL certificate details and expiry", true),
                RouteRegistry.EndpointConfig.get("/{id}/incidents", "Get downtime incidents and alerts", true)
            );
            
            // Dashboard - aggregated statistics
            ke.skyworld.januscope.api.handlers.DashboardHandler dashboardHandler = 
                new ke.skyworld.januscope.api.handlers.DashboardHandler(
                    serviceRepo, uptimeRepo, sslRepo, incidentRepo);
            registry.get(API_PREFIX + "/dashboard/summary", dashboardHandler, 
                "Real-time dashboard: uptime %, SSL status, active incidents", true);
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // CONTACT GROUPS - Notification recipients management
        // ═══════════════════════════════════════════════════════════════════════
        if (contactGroupRepo != null) {
            ke.skyworld.januscope.api.handlers.ContactGroupHandler contactGroupHandler = 
                new ke.skyworld.januscope.api.handlers.ContactGroupHandler(contactGroupRepo, contactMemberRepo);
            
            registry.group("contact-groups", contactGroupHandler,
                // Groups
                RouteRegistry.EndpointConfig.get("", "List all notification contact groups", true),
                RouteRegistry.EndpointConfig.post("", "Create new contact group", true),
                RouteRegistry.EndpointConfig.get("/{id}", "Get group details and members", true),
                RouteRegistry.EndpointConfig.put("/{id}", "Update group name/description", true),
                RouteRegistry.EndpointConfig.delete("/{id}", "Delete contact group", true),
                // Members
                RouteRegistry.EndpointConfig.get("/{id}/members", "List all members in group", true),
                RouteRegistry.EndpointConfig.post("/{id}/members", "Add member to group (email/phone/telegram)", true),
                RouteRegistry.EndpointConfig.put("/{groupId}/members/{memberId}", "Update member contact info", true),
                RouteRegistry.EndpointConfig.delete("/{groupId}/members/{memberId}", "Remove member from group", true)
            );
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // BULK OPERATIONS - Enterprise-grade import/export
        // ═══════════════════════════════════════════════════════════════════════
        if (contactGroupRepo != null && contactMemberRepo != null) {
            ke.skyworld.januscope.core.bulk.BulkImportService bulkImportService = 
                new ke.skyworld.januscope.core.bulk.BulkImportService(contactGroupRepo, contactMemberRepo);
            ke.skyworld.januscope.core.bulk.BulkExportService bulkExportService = 
                new ke.skyworld.januscope.core.bulk.BulkExportService(contactGroupRepo, contactMemberRepo);
            ke.skyworld.januscope.core.bulk.ServiceBulkImportService serviceBulkImportService = 
                new ke.skyworld.januscope.core.bulk.ServiceBulkImportService(serviceRepo, monitoringEngine, uptimeRepo, sslRepo);
            ke.skyworld.januscope.api.handlers.BulkOperationsHandler bulkHandler = 
                new ke.skyworld.januscope.api.handlers.BulkOperationsHandler(bulkImportService, bulkExportService, serviceBulkImportService);
            
            registry.group("bulk", bulkHandler,
                RouteRegistry.EndpointConfig.post("/import", "Bulk import groups/members (JSON/XML/CSV/Excel)", true),
                RouteRegistry.EndpointConfig.post("/services/import", "Bulk import services (JSON/XML/CSV/Excel)", true),
                RouteRegistry.EndpointConfig.get("/export", "Bulk export all data (format=json|xml|csv|excel)", true),
                RouteRegistry.EndpointConfig.get("/template", "Download import template (format=csv|excel)", true)
            );
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // SETTINGS - System configuration
        // ═══════════════════════════════════════════════════════════════════════
        if (settingsRepo != null) {
            ke.skyworld.januscope.api.handlers.SettingsHandler settingsHandler = 
                new ke.skyworld.januscope.api.handlers.SettingsHandler(settingsRepo);
            
            registry.group("settings", settingsHandler,
                RouteRegistry.EndpointConfig.get("", "List all system settings", true),
                RouteRegistry.EndpointConfig.get("/{key}", "Get specific setting value", true),
                RouteRegistry.EndpointConfig.put("/{key}", "Update setting value", true)
            );
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // SYSTEM ADMIN - Reference data management (roles, countries, branches, etc.)
        // ═══════════════════════════════════════════════════════════════════════
        if (systemRepo != null) {
            ke.skyworld.januscope.api.handlers.SystemHandler systemHandler = 
                new ke.skyworld.januscope.api.handlers.SystemHandler(systemRepo);
            
            // Roles
            registry.group("system/roles", systemHandler,
                RouteRegistry.EndpointConfig.get("", "List all roles", true),
                RouteRegistry.EndpointConfig.post("", "Create new role", true),
                RouteRegistry.EndpointConfig.get("/{roleId}", "Get role by ID", true),
                RouteRegistry.EndpointConfig.put("", "Update role", true),
                RouteRegistry.EndpointConfig.delete("/{roleId}", "Delete role", true)
            );
            
            // Countries
            registry.group("system/countries", systemHandler,
                RouteRegistry.EndpointConfig.get("", "List all countries", true),
                RouteRegistry.EndpointConfig.post("", "Create new country", true),
                RouteRegistry.EndpointConfig.get("/{countryCode}", "Get country by code", true),
                RouteRegistry.EndpointConfig.put("", "Update country", true),
                RouteRegistry.EndpointConfig.delete("/{countryCode}", "Delete country", true),
                RouteRegistry.EndpointConfig.post("/bulk-import", "Bulk import countries", true)
            );
            
            // Branches
            registry.group("system/branches", systemHandler,
                RouteRegistry.EndpointConfig.get("", "List all branches", true),
                RouteRegistry.EndpointConfig.post("", "Create new branch", true),
                RouteRegistry.EndpointConfig.get("/{branchId}", "Get branch by ID", true),
                RouteRegistry.EndpointConfig.put("", "Update branch", true),
                RouteRegistry.EndpointConfig.delete("/{branchId}", "Delete branch", true),
                RouteRegistry.EndpointConfig.post("/bulk-import", "Bulk import branches", true)
            );
            
            // Locations
            registry.group("system/locations", systemHandler,
                RouteRegistry.EndpointConfig.get("", "List all locations", true),
                RouteRegistry.EndpointConfig.post("", "Create new location", true),
                RouteRegistry.EndpointConfig.get("/{locationId}", "Get location by ID", true),
                RouteRegistry.EndpointConfig.put("", "Update location", true),
                RouteRegistry.EndpointConfig.delete("/{locationId}", "Delete location", true),
                RouteRegistry.EndpointConfig.post("/bulk-import", "Bulk import locations", true)
            );
            
            // Notification Templates
            registry.group("system/templates", systemHandler,
                RouteRegistry.EndpointConfig.get("", "List all notification templates", true),
                RouteRegistry.EndpointConfig.post("", "Create new template", true),
                RouteRegistry.EndpointConfig.get("/{templateId}", "Get template by ID", true),
                RouteRegistry.EndpointConfig.put("", "Update template", true),
                RouteRegistry.EndpointConfig.delete("/{templateId}", "Delete template", true)
            );
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // API DOCUMENTATION - Auto-generated from registered routes
        // ═══════════════════════════════════════════════════════════════════════
        ke.skyworld.januscope.api.handlers.AutoApiDocumentationHandler apiDocHandler = 
            new ke.skyworld.januscope.api.handlers.AutoApiDocumentationHandler(registry.getRoutes(), apiVersion);
        registry.get("/", apiDocHandler, "Interactive API documentation with examples", false);
        
        // Fallback for 404
        router.setFallbackHandler(this::handle404);
        
        // Build middleware chain: Request Logger -> Authentication -> Router
        HttpHandler handler = router;
        
        // Add authentication middleware
        if (authEnabled && jwtTokenManager != null && userRepo != null) {
            handler = new ke.skyworld.januscope.api.middleware.AuthenticationMiddleware(
                handler, jwtTokenManager, userRepo, publicEndpoints, authEnabled);
            logger.info("Authentication middleware enabled");
        }
        
        // Add request logger (outermost middleware)
        handler = new ke.skyworld.januscope.api.middleware.RequestLogger(handler);
        
        // Build server with middleware chain
        final HttpHandler finalHandler = handler;
        server = Undertow.builder()
            .addHttpListener(port, host)
            .setHandler(exchange -> {
                // Add CORS headers
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange.getResponseHeaders().put(io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
                exchange.getResponseHeaders().put(io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().put(io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Headers"), "Content-Type, Authorization");
                
                // Handle OPTIONS preflight
                if (exchange.getRequestMethod().equals(Methods.OPTIONS)) {
                    exchange.setStatusCode(200);
                    exchange.endExchange();
                    return;
                }
                
                // Pass through middleware chain
                finalHandler.handleRequest(exchange);
            })
            .build();
        
        server.start();
        
        logger.info("Undertow server started on http://{}:{}", host, port);
    }
    
    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            logger.info("Stopping Undertow server...");
            server.stop();
            logger.info("Undertow server stopped");
        }
    }
    
    @Override
    public String getName() {
        return "UndertowServer";
    }
    
    @Override
    public boolean isHealthy() {
        return server != null;
    }
    
    /**
     * Helper to register route and track it for documentation
     */
    private void registerRoute(RoutingHandler router, 
                              java.util.List<ke.skyworld.januscope.api.handlers.AutoApiDocumentationHandler.RouteInfo> routes,
                              String method, String path, HttpHandler handler, 
                              String description, boolean requiresAuth) {
        // Register route
        switch (method.toUpperCase()) {
            case "GET":
                router.get(path, handler);
                break;
            case "POST":
                router.post(path, handler);
                break;
            case "PUT":
                router.put(path, handler);
                break;
            case "DELETE":
                router.delete(path, handler);
                break;
        }
        
        // Track for documentation
        routes.add(new ke.skyworld.januscope.api.handlers.AutoApiDocumentationHandler.RouteInfo(
            method, path, description, requiresAuth));
    }
    
    // Handler methods
    
    private void handleHealth(HttpServerExchange exchange) {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", java.time.Instant.now());
        
        sendJson(exchange, ApiResponse.success(health));
    }
    
    private void handleSystemStatus(HttpServerExchange exchange) {
        Map<String, Object> status = new HashMap<>();
        status.put("uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        status.put("memory", Map.of(
            "total", Runtime.getRuntime().totalMemory(),
            "free", Runtime.getRuntime().freeMemory(),
            "max", Runtime.getRuntime().maxMemory()
        ));
        status.put("threads", Thread.activeCount());
        
        sendJson(exchange, ApiResponse.success(status));
    }
    
    private void handleEngines(HttpServerExchange exchange) {
        Map<String, String> engines = new HashMap<>();
        engines.put("configuration", "RUNNING");
        engines.put("security", "RUNNING");
        engines.put("monitoring", monitoringEngine != null ? monitoringEngine.getStatus().toString() : "NOT_LOADED");
        engines.put("notification", notificationEngine != null ? notificationEngine.getStatus().toString() : "NOT_LOADED");
        engines.put("job", jobEngine != null ? jobEngine.getStatus().toString() : "NOT_LOADED");
        
        sendJson(exchange, ApiResponse.success(engines));
    }
    
    private void handleMonitoringStats(HttpServerExchange exchange) {
        if (monitoringEngine == null) {
            sendJson(exchange, ApiResponse.error("Monitoring engine not available"));
            return;
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("status", monitoringEngine.getStatus().toString());
        stats.put("stats", monitoringEngine.getStats());
        stats.put("healthy", monitoringEngine.isHealthy());
        
        sendJson(exchange, ApiResponse.success(stats));
    }
    
    private void handleNotificationChannels(HttpServerExchange exchange) {
        if (notificationEngine == null) {
            sendJson(exchange, ApiResponse.error("Notification engine not available"));
            return;
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("available", notificationEngine.getAvailableChannels());
        data.put("enabled", notificationEngine.getEnabledChannels());
        data.put("stats", notificationEngine.getStats());
        
        sendJson(exchange, ApiResponse.success(data));
    }
    
    private void handleJobs(HttpServerExchange exchange) {
        if (jobEngine == null) {
            sendJson(exchange, ApiResponse.error("Job engine not available"));
            return;
        }
        
        List<Map<String, Object>> jobs = jobEngine.getScheduledJobs().stream()
            .map(sj -> {
                Map<String, Object> jobInfo = new HashMap<>();
                jobInfo.put("name", sj.getJob().getName());
                jobInfo.put("description", sj.getJob().getDescription());
                jobInfo.put("enabled", sj.getJob().isEnabled());
                jobInfo.put("cron", sj.getCronExpression().toString());
                jobInfo.put("nextExecution", sj.getNextExecutionTime());
                jobInfo.put("lastExecution", sj.getLastExecutionTime());
                jobInfo.put("executionCount", sj.getExecutionCount());
                jobInfo.put("lastSuccess", sj.getLastSuccessTime());
                jobInfo.put("lastFailure", sj.getLastFailureTime());
                jobInfo.put("lastError", sj.getLastError());
                return jobInfo;
            })
            .collect(Collectors.toList());
        
        sendJson(exchange, ApiResponse.success(jobs));
    }
    
    private void handle404(HttpServerExchange exchange) {
        exchange.setStatusCode(404);
        sendJson(exchange, ApiResponse.error("Endpoint not found: " + exchange.getRequestPath()));
    }
    
    private void sendJson(HttpServerExchange exchange, Object data) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(JsonUtil.toJson(data));
    }
}
