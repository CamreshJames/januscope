package ke.skyworld.januscope.api.server;

import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import ke.skyworld.januscope.api.handlers.AutoApiDocumentationHandler.RouteInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Elegant route registration with automatic documentation
 * Reduces verbosity and keeps code DRY
 */
public class RouteRegistry {
    private final RoutingHandler router;
    private final List<RouteInfo> routes;
    private final String apiPrefix;
    
    public RouteRegistry(RoutingHandler router, String apiPrefix) {
        this.router = router;
        this.apiPrefix = apiPrefix;
        this.routes = new ArrayList<>();
    }
    
    /**
     * Register GET endpoint with documentation
     */
    public RouteRegistry get(String path, HttpHandler handler, String description, boolean requiresAuth) {
        router.get(path, handler);
        routes.add(new RouteInfo("GET", path, description, requiresAuth));
        return this;
    }
    
    /**
     * Register POST endpoint with documentation
     */
    public RouteRegistry post(String path, HttpHandler handler, String description, boolean requiresAuth) {
        router.post(path, handler);
        routes.add(new RouteInfo("POST", path, description, requiresAuth));
        return this;
    }
    
    /**
     * Register PUT endpoint with documentation
     */
    public RouteRegistry put(String path, HttpHandler handler, String description, boolean requiresAuth) {
        router.put(path, handler);
        routes.add(new RouteInfo("PUT", path, description, requiresAuth));
        return this;
    }
    
    /**
     * Register DELETE endpoint with documentation
     */
    public RouteRegistry delete(String path, HttpHandler handler, String description, boolean requiresAuth) {
        router.delete(path, handler);
        routes.add(new RouteInfo("DELETE", path, description, requiresAuth));
        return this;
    }
    
    /**
     * Register multiple CRUD endpoints for a resource
     * Automatically creates: GET, POST, GET/{id}, PUT/{id}, DELETE/{id}
     */
    public RouteRegistry crud(String resource, HttpHandler handler, String resourceName) {
        String path = apiPrefix + "/" + resource;
        
        get(path, handler, "List all " + resourceName, true);
        post(path, handler, "Create " + resourceName, true);
        get(path + "/{id}", handler, "Get " + resourceName + " details", true);
        put(path + "/{id}", handler, "Update " + resourceName, true);
        delete(path + "/{id}", handler, "Delete " + resourceName, true);
        
        return this;
    }
    
    /**
     * Register a group of related endpoints with a common handler
     * Example: auth endpoints (login, register, refresh, me)
     */
    public RouteRegistry group(String basePath, HttpHandler handler, EndpointConfig... configs) {
        for (EndpointConfig config : configs) {
            String fullPath = apiPrefix + "/" + basePath + config.path;
            
            switch (config.method.toUpperCase()) {
                case "GET":
                    get(fullPath, handler, config.description, config.requiresAuth);
                    break;
                case "POST":
                    post(fullPath, handler, config.description, config.requiresAuth);
                    break;
                case "PUT":
                    put(fullPath, handler, config.description, config.requiresAuth);
                    break;
                case "DELETE":
                    delete(fullPath, handler, config.description, config.requiresAuth);
                    break;
            }
        }
        return this;
    }
    
    /**
     * Get all registered routes for documentation
     */
    public List<RouteInfo> getRoutes() {
        return routes;
    }
    
    /**
     * Endpoint configuration for group registration
     */
    public static class EndpointConfig {
        final String method;
        final String path;
        final String description;
        final boolean requiresAuth;
        
        public EndpointConfig(String method, String path, String description, boolean requiresAuth) {
            this.method = method;
            this.path = path;
            this.description = description;
            this.requiresAuth = requiresAuth;
        }
        
        // Convenience factory methods
        public static EndpointConfig get(String path, String description, boolean requiresAuth) {
            return new EndpointConfig("GET", path, description, requiresAuth);
        }
        
        public static EndpointConfig post(String path, String description, boolean requiresAuth) {
            return new EndpointConfig("POST", path, description, requiresAuth);
        }
        
        public static EndpointConfig put(String path, String description, boolean requiresAuth) {
            return new EndpointConfig("PUT", path, description, requiresAuth);
        }
        
        public static EndpointConfig delete(String path, String description, boolean requiresAuth) {
            return new EndpointConfig("DELETE", path, description, requiresAuth);
        }
    }
}
