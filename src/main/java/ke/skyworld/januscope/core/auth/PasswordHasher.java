package ke.skyworld.januscope.core.auth;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import ke.skyworld.januscope.utils.Logger;

/**
 * Password hasher using Argon2id
 */
public class PasswordHasher {
    private static final Logger logger = Logger.getLogger(PasswordHasher.class);
    
    private final Argon2 argon2;
    
    // Argon2 parameters (adjust based on your security requirements)
    private static final int ITERATIONS = 3;
    private static final int MEMORY = 65536; // 64 MB
    private static final int PARALLELISM = 1;
    
    public PasswordHasher() {
        this.argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    }
    
    /**
     * Hash password using Argon2id
     */
    public String hash(String password) {
        try {
            return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, password.toCharArray());
        } catch (Exception e) {
            logger.error("Failed to hash password", e);
            throw new RuntimeException("Password hashing failed", e);
        }
    }
    
    /**
     * Verify password against hash
     */
    public boolean verify(String password, String hash) {
        try {
            return argon2.verify(hash, password.toCharArray());
        } catch (Exception e) {
            logger.error("Failed to verify password", e);
            return false;
        }
    }
}
