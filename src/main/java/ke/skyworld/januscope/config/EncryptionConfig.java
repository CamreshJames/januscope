package ke.skyworld.januscope.config;

import ke.skyworld.januscope.core.security.EncryptionUtil;
import ke.skyworld.januscope.utils.Logger;

/**
 * Universal Encryption Configuration
 * Manages encryption settings and key generation
 * 
 * Features:
 * - Load encryption key from environment or XML config
 * - Generate new keys for setup
 * - Validate encryption configuration
 * - Thread-safe singleton pattern
 * 
 * Configuration Priority:
 * 1. Environment Variable (JANUSCOPE_ENCRYPTION_KEY)
 * 2. XML Config (config/application.xml)
 * 3. Auto-generate (with warning)
 * 
 * Usage:
 * EncryptionConfig config = EncryptionConfig.getInstance();
 * String key = config.getEncryptionKey();
 * boolean enabled = config.isEncryptionEnabled();
 */
public class EncryptionConfig {
    private static final Logger logger = Logger.getLogger(EncryptionConfig.class);
    private static EncryptionConfig instance;
    
    // Configuration keys
    private static final String ENV_ENCRYPTION_KEY = "JANUSCOPE_ENCRYPTION_KEY";
    private static final String ENV_ENCRYPTION_ENABLED = "JANUSCOPE_ENCRYPTION_ENABLED";
    
    private String encryptionKey;
    private boolean encryptionEnabled;
    private boolean initialized;
    private String[] whitelistPaths;
    private String[] blacklistPaths;
    
    private EncryptionConfig() {
        loadConfiguration();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized EncryptionConfig getInstance() {
        if (instance == null) {
            instance = new EncryptionConfig();
        }
        return instance;
    }
    
    /**
     * Load configuration from environment or XML config
     */
    private void loadConfiguration() {
        try {
            // Try environment variables first (highest priority)
            encryptionKey = System.getenv(ENV_ENCRYPTION_KEY);
            String enabledStr = System.getenv(ENV_ENCRYPTION_ENABLED);
            
            // Fall back to XML config
            if (encryptionKey == null || enabledStr == null) {
                loadFromXmlConfig();
            } else {
                encryptionEnabled = Boolean.parseBoolean(enabledStr);
            }
            
            // Validate key if encryption is enabled
            if (encryptionEnabled) {
                if (encryptionKey == null || encryptionKey.isEmpty()) {
                    logger.warn("Encryption enabled but no key found. Disabling encryption.");
                    logger.warn("Set JANUSCOPE_ENCRYPTION_KEY environment variable or configure in application.xml");
                    encryptionEnabled = false;
                } else {
                    // Validate key format
                    validateKey(encryptionKey);
                }
            }
            
            initialized = true;
            logger.info("Encryption configuration loaded. Enabled: " + encryptionEnabled);
            
        } catch (Exception e) {
            logger.error("Failed to load encryption configuration", e);
            encryptionEnabled = false;
            initialized = false;
        }
    }
    
    /**
     * Load configuration from XML config file
     * This method should be implemented to read from application.xml
     * For now, it uses default values
     */
    private void loadFromXmlConfig() {
        // TODO: Integrate with existing XML config parser
        // For now, use defaults
        logger.debug("XML config loading not yet implemented, using environment variables only");
        encryptionEnabled = false;
        whitelistPaths = new String[]{
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/change-password"
        };
        blacklistPaths = new String[]{
            "/api/health",
            "/api/v1/health",
            "/api/v1/metrics"
        };
    }
    
    /**
     * Validate encryption key format
     */
    private void validateKey(String key) throws IllegalArgumentException {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Encryption key cannot be empty");
        }
        
        try {
            java.util.Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Encryption key must be valid base64", e);
        }
    }
    
    /**
     * Get encryption key
     */
    public String getEncryptionKey() {
        return encryptionKey;
    }
    
    /**
     * Check if encryption is enabled
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled && initialized;
    }
    
    /**
     * Set encryption enabled (runtime configuration)
     */
    public void setEncryptionEnabled(boolean enabled) {
        this.encryptionEnabled = enabled;
    }
    
    /**
     * Generate and set new encryption key
     */
    public String generateNewKey() throws Exception {
        String newKey = EncryptionUtil.generateKey();
        this.encryptionKey = newKey;
        logger.info("New encryption key generated. Set JANUSCOPE_ENCRYPTION_KEY environment variable.");
        return newKey;
    }
    
    /**
     * Get whitelist paths
     */
    public String[] getWhitelistPaths() {
        return whitelistPaths;
    }
    
    /**
     * Get blacklist paths
     */
    public String[] getBlacklistPaths() {
        return blacklistPaths;
    }
    
    /**
     * Get encryption utility instance
     */
    public EncryptionUtil getEncryptionUtil() {
        if (!isEncryptionEnabled()) {
            throw new IllegalStateException("Encryption is not enabled");
        }
        return new EncryptionUtil(encryptionKey);
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        loadConfiguration();
    }
    
    /**
     * Get configuration summary
     */
    public String getConfigSummary() {
        return String.format(
            "Encryption: %s, Key: %s, Initialized: %s",
            encryptionEnabled ? "ENABLED" : "DISABLED",
            encryptionKey != null ? "SET" : "NOT SET",
            initialized ? "YES" : "NO"
        );
    }
}
