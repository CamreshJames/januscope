package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.domain.models.User;
import ke.skyworld.januscope.domain.repositories.UserRepository;
import ke.skyworld.januscope.core.security.SecurityEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive User Management API Handler
 * Supports 3 user types: admin, operator, viewer
 * Implements admin approval workflow with auto-generated credentials
 * 
 * Endpoints: /api/v1/users/*
 */
public class UserManagementHandler extends BaseHandler {
    private final UserRepository userRepository;
    private final SecurityEngine securityEngine;
    private final ke.skyworld.januscope.core.auth.UserApprovalService approvalService;
    
    public UserManagementHandler(UserRepository userRepository, 
                                SecurityEngine securityEngine,
                                ke.skyworld.januscope.core.notification.NotificationEngine notificationEngine) {
        this.userRepository = userRepository;
        this.securityEngine = securityEngine;
        this.approvalService = new ke.skyworld.januscope.core.auth.UserApprovalService(
            userRepository, notificationEngine);
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // Dispatch to worker thread if on IO thread (prevents blocking IO errors)
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        
        String path = exchange.getRequestPath();
        String method = exchange.getRequestMethod().toString();
        
        // Extract authenticated user from exchange attribute (set by auth middleware)
        User currentUser = exchange.getAttachment(ke.skyworld.januscope.api.middleware.AuthenticationMiddleware.USER_KEY);
        
        if (path.matches(".*/users/pending$") && "GET".equals(method)) {
            handleGetPendingUsers(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+/approve$") && "POST".equals(method)) {
            handleApproveUser(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+/reject$") && "POST".equals(method)) {
            handleRejectUser(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+/activate$") && "POST".equals(method)) {
            handleActivateUser(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+/deactivate$") && "POST".equals(method)) {
            handleDeactivateUser(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+/password$") && "PUT".equals(method)) {
            handleChangePassword(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+/role$") && "PUT".equals(method)) {
            handleChangeRole(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+$") && "GET".equals(method)) {
            handleGetUser(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+$") && "PUT".equals(method)) {
            handleUpdateUser(exchange, currentUser);
        } else if (path.matches(".*/users/\\d+$") && "DELETE".equals(method)) {
            handleDeleteUser(exchange, currentUser);
        } else if (path.matches(".*/users$") && "GET".equals(method)) {
            handleGetAllUsers(exchange, currentUser);
        } else if (path.matches(".*/users$") && "POST".equals(method)) {
            handleCreateUser(exchange, currentUser);
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }
    
    /**
     * GET /api/v1/users
     * Get all users (with filtering)
     * Query params: role, status, approved
     * ADMIN ONLY
     */
    private void handleGetAllUsers(HttpServerExchange exchange, User currentUser) {
        try {
            // Only admins can list all users
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can view all users");
                return;
            }
            
            Map<String, String> filters = new HashMap<>();
            
            // Parse query parameters
            if (exchange.getQueryParameters().containsKey("role")) {
                filters.put("role", exchange.getQueryParameters().get("role").getFirst());
            }
            if (exchange.getQueryParameters().containsKey("status")) {
                filters.put("status", exchange.getQueryParameters().get("status").getFirst());
            }
            if (exchange.getQueryParameters().containsKey("approved")) {
                filters.put("approved", exchange.getQueryParameters().get("approved").getFirst());
            }
            
            List<Map<String, Object>> users = userRepository.findAllWithFilters(filters);
            
            // Remove sensitive data
            users.forEach(this::sanitizeUserData);
            
            sendSuccess(exchange, users);
            logger.info("User {} retrieved {} users", currentUser.getEmail(), users.size());
            
        } catch (Exception e) {
            logger.error("Failed to get users", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve users");
        }
    }
    
    /**
     * GET /api/v1/users/{id}
     * Get specific user details
     * Users can view own profile, admins can view any
     */
    private void handleGetUser(HttpServerExchange exchange, User currentUser) {
        try {
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            // Users can view their own profile, only admins can view others
            if (currentUser.getUserId() != userId && !hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can view other users");
                return;
            }
            
            Map<String, Object> user = userRepository.findByIdAsMap(userId);
            
            if (user == null) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sanitizeUserData(user);
            sendSuccess(exchange, user);
            
        } catch (Exception e) {
            logger.error("Failed to get user", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve user");
        }
    }
    
    /**
     * POST /api/v1/users
     * Create new user (admin only)
     */
    private void handleCreateUser(HttpServerExchange exchange, User currentUser) {
        try {
            // Only admins can create users directly
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can create users");
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            
            // Validate required fields
            if (!data.containsKey("email") || !data.containsKey("firstName") || 
                !data.containsKey("lastName") || !data.containsKey("password")) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Required fields: email, firstName, lastName, password");
                return;
            }
            
            // Hash password
            String password = (String) data.get("password");
            String passwordHash = securityEngine.hashPassword(password);
            data.put("passwordHash", passwordHash);
            data.remove("password");
            
            // Admin-created users are auto-approved
            data.put("isApproved", true);
            data.put("approvedBy", currentUser.getUserId());
            data.put("approvedAt", java.time.Instant.now());
            
            int userId = userRepository.create(data);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("message", "User created successfully");
            
            sendSuccess(exchange, response);
            logger.info("Admin {} created user {}", currentUser.getEmail(), data.get("email"));
            
        } catch (java.sql.SQLException e) {
            logger.error("SQL error creating user", e);
            String errorMessage = extractUserFriendlyError(e);
            sendError(exchange, StatusCodes.BAD_REQUEST, errorMessage);
        } catch (Exception e) {
            logger.error("Failed to create user", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create user: " + e.getMessage());
        }
    }
    
    /**
     * PUT /api/v1/users/{id}
     * Update user details
     */
    private void handleUpdateUser(HttpServerExchange exchange, User currentUser) {
        try {
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            // Users can update their own profile, admins can update any
            if (currentUser.getUserId() != userId && !hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Insufficient permissions");
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            
            // Prevent non-admins from changing sensitive fields
            if (!hasPermission(currentUser, "admin")) {
                data.remove("roleId");
                data.remove("isActive");
                data.remove("isApproved");
                data.remove("approvedBy");
            }
            
            // Never allow password change through this endpoint
            data.remove("password");
            data.remove("passwordHash");
            
            boolean updated = userRepository.update(userId, data);
            
            if (!updated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "User updated successfully"));
            logger.info("User {} updated user {}", currentUser.getEmail(), userId);
            
        } catch (java.sql.SQLException e) {
            logger.error("SQL error updating user", e);
            String errorMessage = extractUserFriendlyError(e);
            sendError(exchange, StatusCodes.BAD_REQUEST, errorMessage);
        } catch (Exception e) {
            logger.error("Failed to update user", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update user");
        }
    }
    
    /**
     * DELETE /api/v1/users/{id}
     * Soft delete user (admin only)
     */
    private void handleDeleteUser(HttpServerExchange exchange, User currentUser) {
        try {
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can delete users");
                return;
            }
            
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            // Prevent self-deletion
            if (currentUser.getUserId() == userId) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Cannot delete your own account");
                return;
            }
            
            boolean deleted = userRepository.softDelete(userId);
            
            if (!deleted) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "User deleted successfully"));
            logger.warn("Admin {} deleted user {}", currentUser.getEmail(), userId);
            
        } catch (Exception e) {
            logger.error("Failed to delete user", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete user");
        }
    }
    
    /**
     * GET /api/v1/users/pending
     * Get users pending approval (admin only)
     */
    private void handleGetPendingUsers(HttpServerExchange exchange, User currentUser) {
        try {
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can view pending users");
                return;
            }
            
            List<Map<String, Object>> pendingUsers = userRepository.findPendingApproval();
            pendingUsers.forEach(this::sanitizeUserData);
            
            sendSuccess(exchange, pendingUsers);
            logger.info("Admin {} retrieved {} pending users", currentUser.getEmail(), pendingUsers.size());
            
        } catch (Exception e) {
            logger.error("Failed to get pending users", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve pending users");
        }
    }
    
    /**
     * POST /api/v1/users/{id}/approve
     * Approve pending user (admin only)
     * Generates username and password, sends email with credentials
     */
    private void handleApproveUser(HttpServerExchange exchange, User currentUser) {
        try {
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can approve users");
                return;
            }
            
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            String notes = data != null ? (String) data.get("notes") : null;
            
            // Use approval service to handle username/password generation and email
            ke.skyworld.januscope.core.auth.UserApprovalService.ApprovalResult result = 
                approvalService.approveUser(userId, currentUser.getUserId(), notes);
            
            if (!result.isSuccess()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, result.getMessage());
                return;
            }
            
            // Build response with generated credentials
            Map<String, Object> response = new HashMap<>();
            response.put("message", result.getMessage());
            response.put("username", result.getUsername());
            response.put("password", result.getPassword());
            response.put("note", "Credentials have been sent to user's email. " +
                                "Please save these credentials as backup.");
            
            sendSuccess(exchange, response);
            logger.info("Admin {} approved user {} (username: {})", 
                       currentUser.getEmail(), userId, result.getUsername());
            
        } catch (Exception e) {
            logger.error("Failed to approve user - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            e.printStackTrace(); // Temporary for debugging
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to approve user: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/v1/users/{id}/reject
     * Reject pending user (admin only)
     */
    private void handleRejectUser(HttpServerExchange exchange, User currentUser) {
        try {
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can reject users");
                return;
            }
            
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            String reason = data != null ? (String) data.get("reason") : "Rejected by admin";
            
            Map<String, Object> rejectionData = new HashMap<>();
            rejectionData.put("isApproved", false);
            rejectionData.put("isActive", false);
            rejectionData.put("approvalNotes", reason);
            
            boolean rejected = userRepository.update(userId, rejectionData);
            
            if (!rejected) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "User rejected successfully"));
            logger.info("Admin {} rejected user {}", currentUser.getEmail(), userId);
            
        } catch (Exception e) {
            logger.error("Failed to reject user", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to reject user");
        }
    }
    
    /**
     * POST /api/v1/users/{id}/activate
     * Activate user (admin only)
     */
    private void handleActivateUser(HttpServerExchange exchange, User currentUser) {
        try {
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can activate users");
                return;
            }
            
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            boolean activated = userRepository.update(userId, Map.of("isActive", true));
            
            if (!activated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "User activated successfully"));
            logger.info("Admin {} activated user {}", currentUser.getEmail(), userId);
            
        } catch (Exception e) {
            logger.error("Failed to activate user", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to activate user");
        }
    }
    
    /**
     * POST /api/v1/users/{id}/deactivate
     * Deactivate user (admin only)
     */
    private void handleDeactivateUser(HttpServerExchange exchange, User currentUser) {
        try {
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can deactivate users");
                return;
            }
            
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            // Prevent self-deactivation
            if (currentUser.getUserId() == userId) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Cannot deactivate your own account");
                return;
            }
            
            boolean deactivated = userRepository.update(userId, Map.of("isActive", false));
            
            if (!deactivated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "User deactivated successfully"));
            logger.info("Admin {} deactivated user {}", currentUser.getEmail(), userId);
            
        } catch (Exception e) {
            logger.error("Failed to deactivate user", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to deactivate user");
        }
    }
    
    /**
     * PUT /api/v1/users/{id}/password
     * Change user password
     */
    private void handleChangePassword(HttpServerExchange exchange, User currentUser) {
        try {
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            // Users can change their own password, admins can change any
            if (currentUser.getUserId() != userId && !hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Insufficient permissions");
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            
            String currentPassword = (String) data.get("currentPassword");
            String newPassword = (String) data.get("newPassword");
            
            if (newPassword == null || newPassword.length() < 8) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "New password must be at least 8 characters");
                return;
            }
            
            // Non-admins must provide current password
            if (currentUser.getUserId() == userId && !hasPermission(currentUser, "admin")) {
                if (currentPassword == null) {
                    sendError(exchange, StatusCodes.BAD_REQUEST, "Current password is required");
                    return;
                }
                
                // Verify current password
                User user = userRepository.findByIdFull(userId);
                if (user == null || !securityEngine.verifyPassword(currentPassword, user.getPasswordHash())) {
                    sendError(exchange, StatusCodes.UNAUTHORIZED, "Current password is incorrect");
                    return;
                }
            }
            
            // Hash new password
            String newPasswordHash = securityEngine.hashPassword(newPassword);
            
            boolean updated = userRepository.update(userId, Map.of("passwordHash", newPasswordHash));
            
            if (!updated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "Password changed successfully"));
            logger.info("User {} changed password for user {}", currentUser.getEmail(), userId);
            
        } catch (Exception e) {
            logger.error("Failed to change password", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to change password");
        }
    }
    
    /**
     * PUT /api/v1/users/{id}/role
     * Change user role (admin only)
     */
    private void handleChangeRole(HttpServerExchange exchange, User currentUser) {
        try {
            if (!hasPermission(currentUser, "admin")) {
                sendError(exchange, StatusCodes.FORBIDDEN, "Only admins can change user roles");
                return;
            }
            
            int userId = extractIdFromPath(exchange.getRequestPath());
            
            // Prevent changing own role
            if (currentUser.getUserId() == userId) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Cannot change your own role");
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) parseRequestBody(exchange, Map.class);
            
            Integer roleId = (Integer) data.get("roleId");
            if (roleId == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "roleId is required");
                return;
            }
            
            // Validate role exists (1=admin, 2=operator, 3=viewer)
            if (roleId < 1 || roleId > 3) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid roleId. Must be 1 (admin), 2 (operator), or 3 (viewer)");
                return;
            }
            
            boolean updated = userRepository.update(userId, Map.of("roleId", roleId));
            
            if (!updated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "User not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "User role changed successfully"));
            logger.info("Admin {} changed role for user {} to roleId {}", currentUser.getEmail(), userId, roleId);
            
        } catch (Exception e) {
            logger.error("Failed to change user role", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to change user role");
        }
    }
    
    // Helper methods
    
    private boolean hasPermission(User user, String... allowedRoles) {
        if (user == null) return false;
        
        // Get role name from user object
        String userRole = user.getRoleName();
        
        // If roleName is null, this might be an issue with JWT parsing
        // Log a warning to help debug
        if (userRole == null) {
            logger.warn("User {} has null roleName. User object: {}", 
                       user.getEmail(), user.toString());
            return false;
        }
        
        for (String role : allowedRoles) {
            if (role.equalsIgnoreCase(userRole)) {
                return true;
            }
        }
        
        logger.debug("User {} with role '{}' does not have permission. Required: {}", 
                    user.getEmail(), userRole, String.join(", ", allowedRoles));
        return false;
    }
    
    private void sanitizeUserData(Map<String, Object> user) {
        user.remove("passwordHash");
        user.remove("password_hash");
    }
    
    private int extractIdFromPath(String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) {
                return Integer.parseInt(parts[i]);
            }
        }
        throw new IllegalArgumentException("No ID found in path: " + path);
    }
}
