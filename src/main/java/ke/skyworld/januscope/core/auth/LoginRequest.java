package ke.skyworld.januscope.core.auth;

/**
 * User login request
 */
public class LoginRequest {
    private String identifier; // email or username
    private String password;
    
    // Getters and Setters
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
