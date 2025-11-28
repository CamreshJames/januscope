package ke.skyworld.januscope.core.auth;

import ke.skyworld.januscope.core.notification.NotificationEngine;
import ke.skyworld.januscope.domain.models.NotificationRequest;
import ke.skyworld.januscope.domain.models.User;
import ke.skyworld.januscope.domain.repositories.UserRepository;
import ke.skyworld.januscope.utils.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * User Approval Service
 * Handles admin approval workflow:
 * 1. Generate unique username
 * 2. Generate secure password
 * 3. Update user record
 * 4. Send email with credentials
 */
public class UserApprovalService {
    private static final Logger logger = Logger.getLogger(UserApprovalService.class);
    
    private final UserRepository userRepository;
    private final UsernameGenerator usernameGenerator;
    private final PasswordHasher passwordHasher;
    private final NotificationEngine notificationEngine;
    
    public UserApprovalService(UserRepository userRepository, 
                              NotificationEngine notificationEngine) {
        this.userRepository = userRepository;
        this.usernameGenerator = new UsernameGenerator(userRepository);
        this.passwordHasher = new PasswordHasher();
        this.notificationEngine = notificationEngine;
    }
    
    /**
     * Approve user registration
     * Generates username and password, sends email
     */
    public ApprovalResult approveUser(int userId, int approvedByUserId, String notes) {
        logger.info("Processing approval for user ID: {}", userId);
        
        // Get pending user
        User user = userRepository.findById(userId);
        if (user == null) {
            return ApprovalResult.failure("User not found");
        }
        
        if (user.isActive()) {
            return ApprovalResult.failure("User is already approved and active");
        }
        
        // Generate unique username
        String username = usernameGenerator.generateUsername(
            user.getFirstName(), 
            user.getLastName()
        );
        
        // Generate secure password
        String plainPassword = PasswordGenerator.generate(12);
        String passwordHash = passwordHasher.hash(plainPassword);
        
        // Update user record
        Map<String, Object> updates = new HashMap<>();
        updates.put("username", username);
        updates.put("passwordHash", passwordHash);
        updates.put("isApproved", true);
        updates.put("isActive", true);
        updates.put("approvedBy", approvedByUserId);
        updates.put("approvedAt", Instant.now());
        if (notes != null && !notes.isEmpty()) {
            updates.put("approvalNotes", notes);
        }
        
        boolean updated;
        try {
            updated = userRepository.update(userId, updates);
        } catch (java.sql.SQLException e) {
            logger.error("SQL error updating user during approval", e);
            return ApprovalResult.failure("Database error: " + e.getMessage());
        }
        
        if (!updated) {
            return ApprovalResult.failure("Failed to update user record");
        }
        
        logger.info("User approved: {} (username: {})", user.getEmail(), username);
        
        // Send email with credentials
        boolean emailSent = sendWelcomeEmail(user, username, plainPassword);
        
        if (!emailSent) {
            logger.warn("Failed to send welcome email to: {}", user.getEmail());
            return ApprovalResult.partialSuccess(username, plainPassword, 
                "User approved but email notification failed. Please provide credentials manually.");
        }
        
        return ApprovalResult.success(username, plainPassword);
    }
    
    /**
     * Send welcome email with credentials using HTML template
     */
    private boolean sendWelcomeEmail(User user, String username, String password) {
        try {
            // Use EmailTemplateService to render the welcome template
            ke.skyworld.januscope.core.email.EmailTemplateService templateService = 
                new ke.skyworld.januscope.core.email.EmailTemplateService();
            
            java.util.Map<String, String> variables = new java.util.HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("lastName", user.getLastName());
            variables.put("username", username);
            variables.put("password", password);
            variables.put("loginUrl", "http://localhost:9876/");
            
            String htmlContent = templateService.renderTemplate("welcome", variables);
            
            NotificationRequest notification = new NotificationRequest();
            notification.setRecipient(user.getEmail());
            notification.setSubject("Welcome to Januscope - Your Account is Approved");
            notification.setMessage(htmlContent);
            notification.setEventType("USER_APPROVED");
            
            // Send via email channel
            if (notificationEngine != null) {
                var result = notificationEngine.send("email", notification);
                return result.isSuccess();
            }
            
            logger.warn("Notification engine not available, cannot send email");
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to send welcome email", e);
            return false;
        }
    }
    
    /**
     * Approval result
     */
    public static class ApprovalResult {
        private final boolean success;
        private final String message;
        private final String username;
        private final String password;
        
        private ApprovalResult(boolean success, String message, String username, String password) {
            this.success = success;
            this.message = message;
            this.username = username;
            this.password = password;
        }
        
        public static ApprovalResult success(String username, String password) {
            return new ApprovalResult(true, 
                "User approved successfully. Credentials sent via email.", 
                username, password);
        }
        
        public static ApprovalResult partialSuccess(String username, String password, String message) {
            return new ApprovalResult(true, message, username, password);
        }
        
        public static ApprovalResult failure(String message) {
            return new ApprovalResult(false, message, null, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
}
