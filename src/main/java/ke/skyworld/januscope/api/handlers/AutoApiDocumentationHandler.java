package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-Discovery API Documentation Handler
 * Automatically discovers and documents all registered routes
 */
public class AutoApiDocumentationHandler extends BaseHandler {
    private final List<RouteInfo> routes;
    private final String apiVersion;
    
    public AutoApiDocumentationHandler(List<RouteInfo> routes, String apiVersion) {
        this.routes = routes;
        this.apiVersion = apiVersion != null ? apiVersion : "v1";
    }
    
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
        response.put("apiVersion", apiVersion);
        response.put("company", "Sky World Limited");
        response.put("baseUrl", baseUrl);
        response.put("documentation", baseUrl + "/");
        
        // Auto-discovered endpoints organized by category
        Map<String, List<Map<String, Object>>> endpoints = organizeEndpoints(baseUrl);
        response.put("endpoints", endpoints);
        
        // Quick start guide
        Map<String, String> quickStart = new LinkedHashMap<>();
        quickStart.put("1. Health Check", baseUrl + "/api/health");
        quickStart.put("2. API Documentation", baseUrl + "/");
        quickStart.put("3. Register User", "POST " + baseUrl + "/api/" + apiVersion + "/auth/register");
        quickStart.put("4. Login", "POST " + baseUrl + "/api/" + apiVersion + "/auth/login");
        quickStart.put("5. List Services", "GET " + baseUrl + "/api/" + apiVersion + "/services (with Bearer token)");
        
        response.put("quickStart", quickStart);
        
        // Example requests
        Map<String, Object> examples = new LinkedHashMap<>();
        
        examples.put("Register User", Map.of(
            "method", "POST",
            "url", baseUrl + "/api/" + apiVersion + "/auth/register",
            "headers", Map.of("Content-Type", "application/json"),
            "body", Map.of(
                "firstName", "James",
                "lastName", "Njenga",
                "email", "jamesnjenga@skyworld.ac.ke",
                "password", "BlahBlah@123!"
            )
        ));
        
        examples.put("Login", Map.of(
            "method", "POST",
            "url", baseUrl + "/api/" + apiVersion + "/auth/login",
            "headers", Map.of("Content-Type", "application/json"),
            "body", Map.of(
                "identifier", "jamesnjenga@skyworld.ac.ke",
                "password", "BlahBlah@123!"
            )
        ));
        
        response.put("examples", examples);
        
        // Statistics
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEndpoints", routes.size());
        stats.put("publicEndpoints", routes.stream().filter(r -> !r.requiresAuth).count());
        stats.put("protectedEndpoints", routes.stream().filter(r -> r.requiresAuth).count());
        stats.put("methods", routes.stream().map(r -> r.method).distinct().collect(Collectors.toList()));
        
        response.put("statistics", stats);
        
        // Additional info
        response.put("authentication", "Most endpoints require Bearer token authentication");
        response.put("contentType", "application/json");
        response.put("support", "camreshjames@gmail.com");
        
        // Send response
        sendSuccess(exchange, response);
    }
    
    /**
     * Organize endpoints by category
     */
    private Map<String, List<Map<String, Object>>> organizeEndpoints(String baseUrl) {
        Map<String, List<Map<String, Object>>> organized = new LinkedHashMap<>();
        
        // Categorize routes
        for (RouteInfo route : routes) {
            String category = categorizeRoute(route.path);
            
            Map<String, Object> endpoint = new LinkedHashMap<>();
            endpoint.put("method", route.method);
            endpoint.put("path", route.path);
            endpoint.put("fullUrl", baseUrl + route.path);
            endpoint.put("description", route.description);
            endpoint.put("requiresAuth", route.requiresAuth);
            
            if (route.requestBody != null) {
                endpoint.put("requestBody", route.requestBody);
            }
            
            organized.computeIfAbsent(category, k -> new ArrayList<>()).add(endpoint);
        }
        
        return organized;
    }
    
    /**
     * Categorize route based on path
     */
    private String categorizeRoute(String path) {
        if (path.equals("/") || path.contains("/health")) {
            return "System";
        } else if (path.contains("/auth")) {
            return "Authentication";
        } else if (path.contains("/services")) {
            return "Services";
        } else if (path.contains("/dashboard")) {
            return "Dashboard";
        } else if (path.contains("/contact-groups")) {
            return "Contact Groups";
        } else if (path.contains("/settings")) {
            return "Settings";
        } else if (path.contains("/monitoring")) {
            return "Monitoring";
        } else if (path.contains("/notifications")) {
            return "Notifications";
        } else if (path.contains("/jobs")) {
            return "Jobs";
        } else if (path.contains("/system")) {
            return "System";
        } else {
            return "Other";
        }
    }
    
    /**
     * Route information holder
     */
    public static class RouteInfo {
        public final String method;
        public final String path;
        public final String description;
        public final boolean requiresAuth;
        public final String requestBody;
        
        public RouteInfo(String method, String path, String description, boolean requiresAuth) {
            this(method, path, description, requiresAuth, null);
        }
        
        public RouteInfo(String method, String path, String description, boolean requiresAuth, String requestBody) {
            this.method = method;
            this.path = path;
            this.description = description;
            this.requiresAuth = requiresAuth;
            this.requestBody = requestBody;
        }
    }
}
