package ke.skyworld.januscope.core.auth;

import ke.skyworld.januscope.domain.repositories.UserRepository;
import ke.skyworld.januscope.utils.Logger;

import java.security.SecureRandom;

/**
 * Username Generator
 * Generates unique usernames based on first and last name
 * Format: firstname.lastname or firstname.lastname2, firstname.lastname3, etc.
 */
public class UsernameGenerator {
    private static final Logger logger = Logger.getLogger(UsernameGenerator.class);
    private static final SecureRandom random = new SecureRandom();
    
    private final UserRepository userRepository;
    
    public UsernameGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * Generate unique username from first and last name
     * Examples:
     *   John Doe -> john.doe
     *   John Doe (if exists) -> john.doe2
     *   John Doe (if exists) -> john.doe3
     */
    public String generateUsername(String firstName, String lastName) {
        // Normalize names
        String first = normalize(firstName);
        String last = normalize(lastName);
        
        // Base username
        String baseUsername = first + "." + last;
        
        // Check if base username is available
        if (!userRepository.usernameExists(baseUsername)) {
            logger.info("Generated username: {}", baseUsername);
            return baseUsername;
        }
        
        // Try with numeric suffix (2, 3, 4, ...)
        for (int i = 2; i <= 999; i++) {
            String username = baseUsername + i;
            if (!userRepository.usernameExists(username)) {
                logger.info("Generated username: {} (attempt {})", username, i);
                return username;
            }
        }
        
        // Fallback: add random number
        String fallbackUsername = baseUsername + random.nextInt(10000);
        logger.warn("Using fallback username: {}", fallbackUsername);
        return fallbackUsername;
    }
    
    /**
     * Normalize name for username
     * - Convert to lowercase
     * - Remove special characters
     * - Replace spaces with nothing
     * - Keep only alphanumeric
     */
    private String normalize(String name) {
        if (name == null || name.isEmpty()) {
            return "user";
        }
        
        return name.toLowerCase()
                   .trim()
                   .replaceAll("[^a-z0-9]", "")
                   .replaceAll("\\s+", "");
    }
}
