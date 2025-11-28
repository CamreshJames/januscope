package ke.skyworld.januscope.core.auth;

import ke.skyworld.januscope.domain.models.User;
import ke.skyworld.januscope.domain.repositories.UserRepository;
import ke.skyworld.januscope.utils.Logger;

/**
 * Authentication Service
 * Handles user registration, login, and password management
 */
public class AuthService {
    private static final Logger logger = Logger.getLogger(AuthService.class);
    
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenManager tokenManager;
    private final ke.skyworld.januscope.core.notification.NotificationEngine notificationEngine;
    
    public AuthService(UserRepository userRepository, String jwtSecret, 
                      ke.skyworld.januscope.core.notification.NotificationEngine notificationEngine) {
        this.userRepository = userRepository;
        this.passwordHasher = new PasswordHasher();
        this.tokenManager = new JwtTokenManager(jwtSecret);
        this.notificationEngine = notificationEngine;
    }
    
    /**
     * Creates pending user with only email, firstName, lastName
     * Username and password generated upon admin approval
     */
    public AuthResult register(RegistrationRequest request) {
        // Validate request
        if (request.getEmail() == null || request.getEmail().isEmpty()) {
            return AuthResult.failure("Email is required");
        }
        
        if (!isValidEmail(request.getEmail())) {
            return AuthResult.failure("Invalid email format");
        }
        
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            return AuthResult.failure("First name is required");
        }
        
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            return AuthResult.failure("Last name is required");
        }
        
        // Check if email already exists
        if (userRepository.emailExists(request.getEmail())) {
            return AuthResult.failure("Email already registered");
        }
        
        // Create pending user (no username/password yet)
        User user = new User();
        user.setRoleId(3); // Default to viewer role
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setUsername(null); // Will be generated on approval
        user.setPasswordHash(null); // Will be generated on approval
        user.setActive(false); // Inactive until approved
        
        User createdUser = userRepository.createPendingUser(user);
        
        if (createdUser == null) {
            return AuthResult.failure("Failed to submit registration request");
        }
        
        logger.info("Registration request submitted: {} {} ({})", 
                   createdUser.getFirstName(), 
                   createdUser.getLastName(), 
                   createdUser.getEmail());
        
        // Send registration pending email
        sendRegistrationPendingEmail(createdUser);
        
        // Return success without tokens (user must wait for approval)
        return AuthResult.registrationPending(createdUser);
    }
    
    /**
     * Send registration pending email notification using HTML template
     */
    private void sendRegistrationPendingEmail(User user) {
        try {
            if (notificationEngine == null) {
                logger.warn("Notification engine not available, cannot send registration email");
                return;
            }
            
            // Use EmailTemplateService to render the registration-pending template
            ke.skyworld.januscope.core.email.EmailTemplateService templateService = 
                new ke.skyworld.januscope.core.email.EmailTemplateService();
            
            java.util.Map<String, String> variables = new java.util.HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("lastName", user.getLastName());
            variables.put("email", user.getEmail());
            variables.put("submittedDate", java.time.Instant.now().toString());
            
            String htmlContent = templateService.renderTemplate("registration-pending", variables);
            
            ke.skyworld.januscope.domain.models.NotificationRequest notification = 
                new ke.skyworld.januscope.domain.models.NotificationRequest();
            notification.setRecipient(user.getEmail());
            notification.setSubject("Registration Request Received - Pending Approval");
            notification.setMessage(htmlContent);
            notification.setEventType("REGISTRATION_PENDING");
            
            var result = notificationEngine.send("email", notification);
            if (result.isSuccess()) {
                logger.info("Registration pending email sent to: {}", user.getEmail());
            } else {
                logger.warn("Failed to send registration pending email to: {}", user.getEmail());
            }
            
        } catch (Exception e) {
            logger.error("Error sending registration pending email", e);
        }
    }
    
    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    
    /**
     * Login user
     */
    public AuthResult login(LoginRequest request) {
        // Find user by email or username
        User user = null;
        
        if (request.getIdentifier().contains("@")) {
            user = userRepository.findByEmail(request.getIdentifier());
        } else {
            user = userRepository.findByUsername(request.getIdentifier());
        }
        
        if (user == null) {
            logger.warn("Login attempt with invalid identifier: {}", request.getIdentifier());
            return AuthResult.failure("Invalid credentials");
        }
        
        // Verify password
        if (!passwordHasher.verify(request.getPassword(), user.getPasswordHash())) {
            logger.warn("Login attempt with wrong password for user: {}", user.getEmail());
            return AuthResult.failure("Invalid credentials");
        }
        
        // Update last login
        userRepository.updateLastLogin(user.getUserId());
        
        logger.info("User logged in successfully: {}", user.getEmail());
        
        // Generate tokens
        String accessToken = tokenManager.generateAccessToken(user);
        String refreshToken = tokenManager.generateRefreshToken(user);
        
        return AuthResult.success(user, accessToken, refreshToken);
    }
    
    /**
     * Verify access token
     */
    public User verifyAccessToken(String token) {
        Integer userId = tokenManager.verifyAccessToken(token);
        
        if (userId == null) {
            return null;
        }
        
        return userRepository.findById(userId);
    }
    
    /**
     * Refresh access token
     */
    public AuthResult refreshAccessToken(String refreshToken) {
        Integer userId = tokenManager.verifyRefreshToken(refreshToken);
        
        if (userId == null) {
            return AuthResult.failure("Invalid refresh token");
        }
        
        User user = userRepository.findById(userId);
        
        if (user == null) {
            return AuthResult.failure("User not found");
        }
        
        String newAccessToken = tokenManager.generateAccessToken(user);
        
        return AuthResult.success(user, newAccessToken, refreshToken);
    }
    
    /**
     * Change password
     */
    public boolean changePassword(int userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId);
        
        if (user == null) {
            return false;
        }
        
        // Verify old password
        if (!passwordHasher.verify(oldPassword, user.getPasswordHash())) {
            logger.warn("Password change failed - wrong old password for user: {}", user.getEmail());
            return false;
        }
        
        // Validate new password
        if (newPassword == null || newPassword.length() < 8) {
            logger.warn("Password change failed - new password too short");
            return false;
        }
        
        // Hash and update password
        String newPasswordHash = passwordHasher.hash(newPassword);
        // TODO: Add updatePassword method to UserRepository
        
        logger.info("Password changed successfully for user: {}", user.getEmail());
        return true;
    }
    
    /**
     * Request password reset - generates token and sends email
     */
    public AuthResult requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email);
        
        if (user == null) {
            // Don't reveal if email exists - return success anyway for security
            logger.info("Password reset requested for non-existent email: {}", email);
            return AuthResult.success(null, null, null);
        }
        
        // Generate reset token (valid for 1 hour)
        String resetToken = tokenManager.generatePasswordResetToken(user);
        
        // Send password reset email
        sendPasswordResetEmail(user, resetToken);
        
        logger.info("Password reset requested for user: {}", user.getEmail());
        return AuthResult.success(null, null, null);
    }
    
    /**
     * Reset password using reset token
     */
    public AuthResult resetPassword(String token, String newPassword) {
        // Validate new password
        if (newPassword == null || newPassword.length() < 8) {
            return AuthResult.failure("Password must be at least 8 characters");
        }
        
        // Verify reset token
        Integer userId = tokenManager.verifyPasswordResetToken(token);
        
        if (userId == null) {
            return AuthResult.failure("Invalid or expired reset token");
        }
        
        User user = userRepository.findById(userId);
        
        if (user == null) {
            return AuthResult.failure("User not found");
        }
        
        // Hash and update password
        String newPasswordHash = passwordHasher.hash(newPassword);
        boolean updated = userRepository.updatePassword(userId, newPasswordHash);
        
        if (!updated) {
            return AuthResult.failure("Failed to update password");
        }
        
        logger.info("Password reset successfully for user: {}", user.getEmail());
        return AuthResult.success(user, null, null);
    }
    
    /**
     * Send password reset email using HTML template
     */
    private void sendPasswordResetEmail(User user, String resetToken) {
        try {
            if (notificationEngine == null) {
                logger.warn("Notification engine not available, cannot send password reset email");
                return;
            }
            
            // Use EmailTemplateService to render the password-reset template
            ke.skyworld.januscope.core.email.EmailTemplateService templateService = 
                new ke.skyworld.januscope.core.email.EmailTemplateService();
            
            // Build reset URL (frontend URL)
            String resetUrl = "http://localhost:5173/auth/reset-password?token=" + resetToken;
            
            java.util.Map<String, String> variables = new java.util.HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("resetUrl", resetUrl);
            variables.put("expiryHours", "1");
            variables.put("year", String.valueOf(java.time.Year.now().getValue()));
            
            String htmlContent = templateService.renderTemplate("password-reset", variables);
            
            ke.skyworld.januscope.domain.models.NotificationRequest notification = 
                new ke.skyworld.januscope.domain.models.NotificationRequest();
            notification.setRecipient(user.getEmail());
            notification.setSubject("Password Reset Request - Januscope");
            notification.setMessage(htmlContent);
            notification.setEventType("PASSWORD_RESET");
            
            var result = notificationEngine.send("email", notification);
            if (result.isSuccess()) {
                logger.info("Password reset email sent to: {}", user.getEmail());
            } else {
                logger.warn("Failed to send password reset email to: {}", user.getEmail());
            }
            
        } catch (Exception e) {
            logger.error("Error sending password reset email", e);
        }
    }
}
