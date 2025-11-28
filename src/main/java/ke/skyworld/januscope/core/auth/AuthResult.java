package ke.skyworld.januscope.core.auth;

import ke.skyworld.januscope.domain.models.User;

/**
 * Authentication result
 */
public class AuthResult {
    private boolean success;
    private String message;
    private User user;
    private String accessToken;
    private String refreshToken;
    private boolean pending; // For registration pending approval
    
    private AuthResult() {}
    
    public static AuthResult success(User user, String accessToken, String refreshToken) {
        AuthResult result = new AuthResult();
        result.success = true;
        result.message = "Authentication successful";
        result.user = user;
        result.accessToken = accessToken;
        result.refreshToken = refreshToken;
        result.pending = false;
        return result;
    }
    
    public static AuthResult failure(String message) {
        AuthResult result = new AuthResult();
        result.success = false;
        result.message = message;
        result.pending = false;
        return result;
    }
    
    public static AuthResult registrationPending(User user) {
        AuthResult result = new AuthResult();
        result.success = true;
        result.message = "Registration request submitted successfully. Please wait for admin approval.";
        result.user = user;
        result.accessToken = null;
        result.refreshToken = null;
        result.pending = true;
        return result;
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public User getUser() { return user; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public boolean isPending() { return pending; }
}
