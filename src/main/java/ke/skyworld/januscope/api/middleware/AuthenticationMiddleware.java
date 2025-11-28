package ke.skyworld.januscope.api.middleware;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.api.dto.ApiResponse;
import ke.skyworld.januscope.api.server.JsonUtil;
import ke.skyworld.januscope.core.auth.JwtTokenManager;
import ke.skyworld.januscope.domain.models.User;
import ke.skyworld.januscope.domain.repositories.UserRepository;
import ke.skyworld.januscope.utils.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Authentication Middleware
 * Validates JWT tokens and protects endpoints
 */
public class AuthenticationMiddleware implements HttpHandler {
    private static final Logger logger = Logger.getLogger(AuthenticationMiddleware.class);
    // Use a static shared key so handlers can access it
    public static final AttachmentKey<User> USER_KEY = AttachmentKey.create(User.class);
    
    private final HttpHandler next;
    private final JwtTokenManager tokenManager;
    private final UserRepository userRepository;
    private final Set<String> publicEndpoints;
    private final boolean authEnabled;
    
    public AuthenticationMiddleware(HttpHandler next, JwtTokenManager tokenManager,
                                    UserRepository userRepository,
                                    Set<String> publicEndpoints, boolean authEnabled) {
        this.next = next;
        this.tokenManager = tokenManager;
        this.userRepository = userRepository;
        this.publicEndpoints = publicEndpoints != null ? publicEndpoints : new HashSet<>();
        this.authEnabled = authEnabled;
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        // If authentication is disabled, allow all requests
        if (!authEnabled) {
            next.handleRequest(exchange);
            return;
        }
        
        // Check if endpoint is public
        if (isPublicEndpoint(path)) {
            next.handleRequest(exchange);
            return;
        }
        
        // Extract and validate token
        String authHeader = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(exchange, "Missing or invalid authorization header");
            return;
        }
        
        String token = authHeader.substring(7);
        
        try {
            // Validate token and get user ID
            Integer userId = tokenManager.verifyAccessToken(token);
            
            if (userId != null) {
                // Load full user from database (includes roleName)
                User user = userRepository.findById(userId);
                
                if (user != null && user.isActive()) {
                    // Attach user object to exchange for handlers to use
                    exchange.putAttachment(USER_KEY, user);
                    
                    logger.debug("Authenticated user: {} (role: {})", user.getEmail(), user.getRoleName());
                    
                    // Continue to next handler
                    next.handleRequest(exchange);
                } else {
                    sendUnauthorized(exchange, "User not found or inactive");
                }
            } else {
                sendUnauthorized(exchange, "Invalid or expired token");
            }
        } catch (Exception e) {
            logger.error("Token validation failed", e);
            sendUnauthorized(exchange, "Token validation failed");
        }
    }
    
    /**
     * Check if endpoint is public (doesn't require authentication)
     */
    private boolean isPublicEndpoint(String path) {
        // Exact match
        if (publicEndpoints.contains(path)) {
            return true;
        }
        
        // Pattern match (e.g., /api/v1/auth/*)
        for (String publicPath : publicEndpoints) {
            if (publicPath.endsWith("*")) {
                String prefix = publicPath.substring(0, publicPath.length() - 1);
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Extract user ID from JWT token
     */
    private String extractUserId(String token) {
        try {
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
            logger.debug("Failed to extract user ID from token", e);
        }
        
        return null;
    }
    
    /**
     * Send unauthorized response
     */
    private void sendUnauthorized(HttpServerExchange exchange, String message) {
        exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        
        ApiResponse response = ApiResponse.error(message);
        String json = JsonUtil.toJson(response);
        
        exchange.getResponseSender().send(json);
    }
}
