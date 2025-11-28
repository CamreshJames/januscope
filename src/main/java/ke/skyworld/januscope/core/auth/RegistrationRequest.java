package ke.skyworld.januscope.core.auth;

/**
 * User registration request
 * Simplified: Only email, firstName, and lastName required
 * Username and password generated upon admin approval
 */
public class RegistrationRequest {
    private String email;
    private String firstName;
    private String lastName;
    
    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
