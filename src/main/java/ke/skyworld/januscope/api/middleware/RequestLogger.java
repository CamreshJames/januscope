package ke.skyworld.januscope.api.middleware;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import ke.skyworld.januscope.utils.Logger;

import java.time.Instant;
import java.time.Duration;
import java.util.Deque;

/**
 * Request Logger Middleware
 * Logs all HTTP requests with comprehensive details for auditing
 */
public class RequestLogger implements HttpHandler {
    private static final Logger logger = Logger.getLogger(RequestLogger.class);
    private final HttpHandler next;
    
    public RequestLogger(HttpHandler next) {
        this.next = next;
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // Capture request start time
        Instant startTime = Instant.now();
        
        // Extract request details
        String method = exchange.getRequestMethod().toString();
        String path = exchange.getRequestPath();
        String queryString = exchange.getQueryString();
        String fullUrl = queryString.isEmpty() ? path : path + "?" + queryString;
        
        // Client information
        String clientIp = getClientIp(exchange);
        String userAgent = exchange.getRequestHeaders().getFirst(Headers.USER_AGENT);
        
        // Authentication info (if present)
        String authHeader = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        String userId = extractUserId(authHeader);
        
        // Log request start
        logger.info("→ {} {} from {} | User: {} | UA: {}", 
            method, fullUrl, clientIp, 
            userId != null ? userId : "anonymous",
            userAgent != null ? truncate(userAgent, 50) : "unknown");
        
        // Add completion handler to log response
        exchange.addExchangeCompleteListener((ex, nextListener) -> {
            try {
                Duration duration = Duration.between(startTime, Instant.now());
                int statusCode = ex.getStatusCode();
                long responseSize = ex.getResponseBytesSent();
                
                // Determine log level based on status code
                if (statusCode >= 500) {
                    logger.error("← {} {} | Status: {} | Duration: {}ms | Size: {} bytes | User: {}", 
                        method, path, statusCode, duration.toMillis(), responseSize,
                        userId != null ? userId : "anonymous");
                } else if (statusCode >= 400) {
                    logger.warn("← {} {} | Status: {} | Duration: {}ms | Size: {} bytes | User: {}", 
                        method, path, statusCode, duration.toMillis(), responseSize,
                        userId != null ? userId : "anonymous");
                } else {
                    logger.info("← {} {} | Status: {} | Duration: {}ms | Size: {} bytes | User: {}", 
                        method, path, statusCode, duration.toMillis(), responseSize,
                        userId != null ? userId : "anonymous");
                }
                
                // Log to audit trail if authenticated
                if (userId != null) {
                    logAuditTrail(method, path, statusCode, userId, clientIp, duration);
                }
                
            } finally {
                nextListener.proceed();
            }
        });
        
        // Continue to next handler
        next.handleRequest(exchange);
    }
    
    /**
     * Get client IP address (handles proxies)
     */
    private String getClientIp(HttpServerExchange exchange) {
        // Check X-Forwarded-For header (for proxies)
        String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String realIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        
        // Fall back to source address
        return exchange.getSourceAddress().getAddress().getHostAddress();
    }
    
    /**
     * Extract user ID from JWT token (simplified)
     */
    private String extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        
        try {
            // Extract token
            String token = authHeader.substring(7);
            
            // Parse JWT (simplified - just extract user info from payload)
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                
                // Extract userId or email from payload
                if (payload.contains("\"userId\"")) {
                    int start = payload.indexOf("\"userId\":") + 10;
                    int end = payload.indexOf(",", start);
                    if (end == -1) end = payload.indexOf("}", start);
                    return payload.substring(start, end).trim().replace("\"", "");
                } else if (payload.contains("\"email\"")) {
                    int start = payload.indexOf("\"email\":\"") + 9;
                    int end = payload.indexOf("\"", start);
                    return payload.substring(start, end);
                }
            }
        } catch (Exception e) {
            // Invalid token, ignore
        }
        
        return null;
    }
    
    /**
     * Log to audit trail
     */
    private void logAuditTrail(String method, String path, int statusCode, 
                               String userId, String clientIp, Duration duration) {
        // Determine action type
        String action = determineAction(method, path);
        
        logger.info("AUDIT | User: {} | Action: {} | Path: {} | Status: {} | IP: {} | Duration: {}ms",
            userId, action, path, statusCode, clientIp, duration.toMillis());
    }
    
    /**
     * Determine action type from method and path
     */
    private String determineAction(String method, String path) {
        if (path.contains("/login")) return "LOGIN";
        if (path.contains("/logout")) return "LOGOUT";
        if (path.contains("/register")) return "REGISTER";
        
        switch (method) {
            case "POST": return "CREATE";
            case "PUT": return "UPDATE";
            case "DELETE": return "DELETE";
            case "GET": return "READ";
            default: return method;
        }
    }
    
    /**
     * Truncate string to max length
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
