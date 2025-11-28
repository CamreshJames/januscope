package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import ke.skyworld.januscope.core.engine.EngineContext;

import java.util.*;

/**
 * API Documentation Handler
 * Auto-generates API documentation at root endpoint
 */
public class ApiDocumentationHandler extends BaseHandler {
    
    @Override
    protected void handleGet(HttpServerExchange exchange) throws Exception {
        
        // Get server info
        String host = exchange.getHostName();
        int port = exchange.getHostPort();
        String scheme = exchange.getRequestScheme();
        String baseUrl = scheme + "://" + host + ":" + port;
        
        // Build API documentation
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", "Januscope - Uptime & SSL Monitoring System");
        response.put("version", "1.0.0");
        response.put("company", "Sky World Limited");
        response.put("baseUrl", baseUrl);
        response.put("documentation", baseUrl + "/");
        
        // API endpoints organized by category
        Map<String, List<Map<String, Object>>> endpoints = new LinkedHashMap<>();
        
        // System endpoints
        endpoints.put("System", Arrays.asList(
            createEndpoint("GET", "/", "API Documentation (this page)", false),
            createEndpoint("GET", "/api/health", "Health check endpoint", false),
            createEndpoint("GET", "/api/system/status", "System status and engine information", true)
        ));
        
        // Authentication endpoints
        endpoints.put("Authentication", Arrays.asList(
            createEndpoint("POST", "/api/auth/register", "Register new user", false,
                "Body: {firstName, lastName, email, password}"),
            createEndpoint("POST", "/api/auth/login", "User login", false,
                "Body: {email, password}"),
            createEndpoint("POST", "/api/auth/refresh", "Refresh access token", false,
                "Body: {refreshToken}"),
            createEndpoint("POST", "/api/auth/logout", "User logout", true,
                "Body: {refreshToken}")
        ));
        
        // Service Management endpoints
        endpoints.put("Services", Arrays.asList(
            createEndpoint("GET", "/api/services", "List all services", true),
            createEndpoint("GET", "/api/services/{id}", "Get service details", true),
            createEndpoint("POST", "/api/services", "Create new service", true,
                "Body: {name, url, checkInterval, timeout, maxRetries, enabled}"),
            createEndpoint("PUT", "/api/services/{id}", "Update service", true,
                "Body: {name, url, checkInterval, timeout, maxRetries, enabled}"),
            createEndpoint("DELETE", "/api/services/{id}", "Delete service", true)
        ));
        
        // Dashboard endpoints
        endpoints.put("Dashboard", Arrays.asList(
            createEndpoint("GET", "/api/dashboard/summary", "Dashboard summary statistics", true),
            createEndpoint("GET", "/api/dashboard/incidents", "Recent incidents", true),
            createEndpoint("GET", "/api/dashboard/ssl-warnings", "SSL certificates expiring soon", true)
        ));
        
        // Monitoring endpoints
        endpoints.put("Monitoring", Arrays.asList(
            createEndpoint("GET", "/api/services/{id}/uptime", "Service uptime history", true),
            createEndpoint("GET", "/api/services/{id}/incidents", "Service incident history", true),
            createEndpoint("GET", "/api/services/{id}/ssl", "Service SSL certificate info", true)
        ));
        
        // Contact Groups endpoints
        endpoints.put("Contact Groups", Arrays.asList(
            createEndpoint("GET", "/api/contact-groups", "List all contact groups", true),
            createEndpoint("GET", "/api/contact-groups/{id}", "Get contact group details", true),
            createEndpoint("POST", "/api/contact-groups", "Create new contact group", true,
                "Body: {name, description, isActive}"),
            createEndpoint("PUT", "/api/contact-groups/{id}", "Update contact group", true,
                "Body: {name, description, isActive}"),
            createEndpoint("DELETE", "/api/contact-groups/{id}", "Delete contact group", true),
            createEndpoint("GET", "/api/contact-groups/{id}/members", "List group members", true),
            createEndpoint("POST", "/api/contact-groups/{id}/members", "Add member to group", true,
                "Body: {name, email, telegramHandle, phoneNumber}"),
            createEndpoint("PUT", "/api/contact-groups/{groupId}/members/{memberId}", "Update member", true,
                "Body: {name, email, telegramHandle, phoneNumber}"),
            createEndpoint("DELETE", "/api/contact-groups/{groupId}/members/{memberId}", "Remove member", true)
        ));
        
        // Settings endpoints
        endpoints.put("Settings", Arrays.asList(
            createEndpoint("GET", "/api/settings", "List all system settings", true),
            createEndpoint("GET", "/api/settings/{key}", "Get specific setting", true),
            createEndpoint("PUT", "/api/settings/{key}", "Update setting value", true,
                "Body: {value}")
        ));
        
        // Add full URLs to each endpoint
        Map<String, List<Map<String, Object>>> endpointsWithUrls = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> category : endpoints.entrySet()) {
            List<Map<String, Object>> categoryEndpoints = new ArrayList<>();
            for (Map<String, Object> endpoint : category.getValue()) {
                Map<String, Object> endpointWithUrl = new LinkedHashMap<>(endpoint);
                endpointWithUrl.put("fullUrl", baseUrl + endpoint.get("path"));
                categoryEndpoints.add(endpointWithUrl);
            }
            endpointsWithUrls.put(category.getKey(), categoryEndpoints);
        }
        
        response.put("endpoints", endpointsWithUrls);
        
        // Quick start guide
        Map<String, Object> quickStart = new LinkedHashMap<>();
        quickStart.put("1. Health Check", baseUrl + "/api/health");
        quickStart.put("2. Register User", "POST " + baseUrl + "/api/auth/register");
        quickStart.put("3. Login", "POST " + baseUrl + "/api/auth/login");
        quickStart.put("4. Add Service", "POST " + baseUrl + "/api/services (with Bearer token)");
        quickStart.put("5. View Dashboard", "GET " + baseUrl + "/api/dashboard/summary (with Bearer token)");
        
        response.put("quickStart", quickStart);
        
        // Example requests
        Map<String, Object> examples = new LinkedHashMap<>();
        
        examples.put("Register User", Map.of(
            "method", "POST",
            "url", baseUrl + "/api/auth/register",
            "headers", Map.of("Content-Type", "application/json"),
            "body", Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "email", "john@example.com",
                "password", "SecurePass123!"
            )
        ));
        
        examples.put("Login", Map.of(
            "method", "POST",
            "url", baseUrl + "/api/auth/login",
            "headers", Map.of("Content-Type", "application/json"),
            "body", Map.of(
                "email", "john@example.com",
                "password", "SecurePass123!"
            )
        ));
        
        examples.put("Add Service", Map.of(
            "method", "POST",
            "url", baseUrl + "/api/services",
            "headers", Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer YOUR_ACCESS_TOKEN"
            ),
            "body", Map.of(
                "name", "My Website",
                "url", "https://example.com",
                "checkInterval", 300,
                "timeout", 10000,
                "maxRetries", 3,
                "enabled", true
            )
        ));
        
        response.put("examples", examples);
        
        // Additional info
        response.put("authentication", "Most endpoints require Bearer token authentication");
        response.put("contentType", "application/json");
        response.put("support", "cnjmtechnologiesinc@gmail.com");
        
        // Send response
        sendSuccess(exchange, response);
    }
    
    /**
     * Create endpoint definition
     */
    private Map<String, Object> createEndpoint(String method, String path, 
                                               String description, boolean requiresAuth) {
        return createEndpoint(method, path, description, requiresAuth, null);
    }
    
    /**
     * Create endpoint definition with body info
     */
    private Map<String, Object> createEndpoint(String method, String path, 
                                               String description, boolean requiresAuth,
                                               String body) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("method", method);
        endpoint.put("path", path);
        endpoint.put("description", description);
        endpoint.put("requiresAuth", requiresAuth);
        if (body != null) {
            endpoint.put("body", body);
        }
        return endpoint;
    }
}
