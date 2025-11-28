package ke.skyworld.januscope.core.security;

import ke.skyworld.januscope.config.ConfigNode;
import ke.skyworld.januscope.core.auth.AuthService;
import ke.skyworld.januscope.core.engine.BaseEngine;
import ke.skyworld.januscope.core.engine.EngineException;

/**
 * Security Engine - Handles encryption, authentication, and authorization.
 * Provides JWT token management and password hashing.
 */
public class SecurityEngine extends BaseEngine {
    private AuthService authService;
    private String jwtSecret;
    
    @Override
    protected void doInitialize() throws Exception {
        // Get security config from context
        ConfigNode securityConfig = (ConfigNode) context.getAttribute("security-config");
        
        if (securityConfig == null) {
            logger.warn("Security configuration not found - using default JWT secret");
            jwtSecret = "januscope-default-secret-change-in-production-12345678";
        } else {
            // Load JWT configuration
            ConfigNode jwtConfig = securityConfig.getChild("jwt");
            if (jwtConfig != null) {
                jwtSecret = jwtConfig.getChildValue("secret");
            } else {
                logger.warn("JWT configuration not found - using default secret");
                jwtSecret = "januscope-default-secret-change-in-production-12345678";
            }
        }
        
        logger.info("Security engine initialized with JWT support");
    }
    
    @Override
    protected void doStart() throws Exception {
        logger.debug("Security engine started");
    }
    
    @Override
    protected void doStop() throws Exception {
        logger.debug("Security engine stopped");
    }
    
    @Override
    public String getName() {
        return "Security";
    }
    
    @Override
    public boolean isHealthy() {
        return jwtSecret != null;
    }
    
    /**
     * Get JWT secret for creating AuthService
     */
    public String getJwtSecret() {
        return jwtSecret;
    }
    
    /**
     * Hash password using Argon2
     */
    public String hashPassword(String password) {
        de.mkammerer.argon2.Argon2 argon2 = de.mkammerer.argon2.Argon2Factory.create();
        try {
            return argon2.hash(10, 65536, 1, password.toCharArray());
        } finally {
            argon2.wipeArray(password.toCharArray());
        }
    }
    
    /**
     * Verify password against hash
     */
    public boolean verifyPassword(String hash, String password) {
        de.mkammerer.argon2.Argon2 argon2 = de.mkammerer.argon2.Argon2Factory.create();
        try {
            return argon2.verify(hash, password.toCharArray());
        } finally {
            argon2.wipeArray(password.toCharArray());
        }
    }
}
