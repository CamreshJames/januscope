package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.core.auth.AuthResult;
import ke.skyworld.januscope.core.auth.AuthService;
import ke.skyworld.januscope.core.auth.LoginRequest;
import ke.skyworld.januscope.core.auth.RegistrationRequest;
import ke.skyworld.januscope.domain.models.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication API handler
 * Endpoints: /api/auth/*
 */
public class AuthHandler extends BaseHandler {
    private final AuthService authService;
    
    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // Dispatch to worker thread if on IO thread
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        
        String path = exchange.getRequestPath();
        
        if (path.endsWith("/login")) {
            handleLogin(exchange);
        } else if (path.endsWith("/register")) {
            handleRegister(exchange);
        } else if (path.endsWith("/refresh")) {
            handleRefresh(exchange);
        } else if (path.endsWith("/me")) {
            handleMe(exchange);
        } else if (path.endsWith("/forgot-password")) {
            handleForgotPassword(exchange);
        } else if (path.endsWith("/reset-password")) {
            handleResetPassword(exchange);
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }
    
    /**
     * POST /api/auth/login
     */
    private void handleLogin(HttpServerExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Only POST allowed");
            return;
        }
        
        try {
            LoginRequest request = parseRequestBody(exchange, LoginRequest.class);
            
            if (request.getIdentifier() == null || request.getIdentifier().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Identifier is required");
                return;
            }
            
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Password is required");
                return;
            }
            
            AuthResult result = authService.login(request);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("user", sanitizeUser(result.getUser()));
                response.put("accessToken", result.getAccessToken());
                response.put("refreshToken", result.getRefreshToken());
                
                sendSuccess(exchange, response, "Login successful");
            } else {
                sendError(exchange, StatusCodes.UNAUTHORIZED, result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Login failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Login failed: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/auth/register
     * Simplified registration: Only email, firstName, lastName required
     * Username and password generated upon admin approval
     */
    private void handleRegister(HttpServerExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Only POST allowed");
            return;
        }
        
        try {
            RegistrationRequest request = parseRequestBody(exchange, RegistrationRequest.class);
            
            // Validation - only 3 fields required
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Email is required");
                return;
            }
            
            if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "First name is required");
                return;
            }
            
            if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Last name is required");
                return;
            }
            
            AuthResult result = authService.register(request);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", result.getMessage());
                response.put("email", result.getUser().getEmail());
                response.put("firstName", result.getUser().getFirstName());
                response.put("lastName", result.getUser().getLastName());
                response.put("status", "pending_approval");
                
                // No tokens returned - user must wait for approval
                sendSuccess(exchange, response, result.getMessage());
            } else {
                sendError(exchange, StatusCodes.BAD_REQUEST, result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Registration failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Registration failed: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/auth/refresh
     */
    private void handleRefresh(HttpServerExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Only POST allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            @SuppressWarnings("unchecked")
            Map<String, String> request = (Map<String, String>) ke.skyworld.januscope.api.server.JsonUtil.fromJson(body, Map.class);
            String refreshToken = request.get("refreshToken");
            
            if (refreshToken == null || refreshToken.isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Refresh token is required");
                return;
            }
            
            AuthResult result = authService.refreshAccessToken(refreshToken);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("accessToken", result.getAccessToken());
                response.put("refreshToken", result.getRefreshToken());
                
                sendSuccess(exchange, response, "Token refreshed");
            } else {
                sendError(exchange, StatusCodes.UNAUTHORIZED, result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Token refresh failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Token refresh failed: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/auth/me
     */
    private void handleMe(HttpServerExchange exchange) throws Exception {
        if (!"GET".equals(exchange.getRequestMethod().toString())) {
            sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Only GET allowed");
            return;
        }
        
        try {
            // Extract token from Authorization header
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendError(exchange, StatusCodes.UNAUTHORIZED, "Missing or invalid Authorization header");
                return;
            }
            
            String token = authHeader.substring(7);
            User user = authService.verifyAccessToken(token);
            
            if (user != null) {
                sendSuccess(exchange, sanitizeUser(user), "User retrieved");
            } else {
                sendError(exchange, StatusCodes.UNAUTHORIZED, "Invalid or expired token");
            }
            
        } catch (Exception e) {
            logger.error("Failed to get user info", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get user info: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/auth/forgot-password
     */
    private void handleForgotPassword(HttpServerExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Only POST allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            @SuppressWarnings("unchecked")
            Map<String, String> request = (Map<String, String>) ke.skyworld.januscope.api.server.JsonUtil.fromJson(body, Map.class);
            String email = request.get("email");
            
            if (email == null || email.trim().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Email is required");
                return;
            }
            
            AuthResult result = authService.requestPasswordReset(email);
            
            // Always return success for security (don't reveal if email exists)
            sendSuccess(exchange, null, "If the email exists, reset instructions have been sent");
            
        } catch (Exception e) {
            logger.error("Forgot password failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to process request");
        }
    }
    
    /**
     * POST /api/auth/reset-password
     */
    private void handleResetPassword(HttpServerExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Only POST allowed");
            return;
        }
        
        try {
            String body = readRequestBody(exchange);
            @SuppressWarnings("unchecked")
            Map<String, String> request = (Map<String, String>) ke.skyworld.januscope.api.server.JsonUtil.fromJson(body, Map.class);
            String token = request.get("token");
            String newPassword = request.get("newPassword");
            
            if (token == null || token.trim().isEmpty()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Reset token is required");
                return;
            }
            
            if (newPassword == null || newPassword.length() < 8) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Password must be at least 8 characters");
                return;
            }
            
            AuthResult result = authService.resetPassword(token, newPassword);
            
            if (result.isSuccess()) {
                sendSuccess(exchange, null, "Password reset successfully");
            } else {
                sendError(exchange, StatusCodes.BAD_REQUEST, result.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Reset password failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to reset password");
        }
    }
    
    /**
     * Remove sensitive data from user object
     */
    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> sanitized = new HashMap<>();
        sanitized.put("userId", user.getUserId());
        sanitized.put("email", user.getEmail());
        sanitized.put("username", user.getUsername());
        sanitized.put("firstName", user.getFirstName());
        sanitized.put("middleName", user.getMiddleName());
        sanitized.put("lastName", user.getLastName());
        sanitized.put("fullName", user.getFullName());
        sanitized.put("role", user.getRoleName());
        sanitized.put("isActive", user.isActive());
        sanitized.put("lastLogin", user.getLastLogin());
        sanitized.put("createdAt", user.getCreatedAt());
        // Never send password_hash
        return sanitized;
    }
}
