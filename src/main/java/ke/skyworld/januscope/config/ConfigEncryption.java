package ke.skyworld.januscope.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Configuration Encryption Utility
 * Uses AES-256-GCM for encrypting sensitive configuration values
 */
public class ConfigEncryption {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    
    private final SecretKey secretKey;
    
    /**
     * Initialize with a master password
     * In production, this should come from environment variable or secure vault
     */
    public ConfigEncryption(String masterPassword) throws Exception {
        if (masterPassword == null || masterPassword.length() < 16) {
            throw new IllegalArgumentException("Master password must be at least 16 characters");
        }
        
        // Derive key from password using PBKDF2
        // In production, use a fixed salt stored securely
        byte[] salt = "januscope-config".getBytes(StandardCharsets.UTF_8);
        this.secretKey = deriveKey(masterPassword, salt);
    }
    
    /**
     * Encrypt a plaintext value
     * Returns: base64(salt + iv + ciphertext + tag)
     */
    public String encrypt(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        // Encrypt
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // Combine IV + ciphertext
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        byteBuffer.put(iv);
        byteBuffer.put(ciphertext);
        
        // Return base64 encoded
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }
    
    /**
     * Decrypt an encrypted value
     * Input: base64(iv + ciphertext + tag)
     */
    public String decrypt(String encrypted) throws Exception {
        if (encrypted == null || encrypted.isEmpty()) {
            return encrypted;
        }
        
        try {
            // Decode base64
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            
            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);
            
            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new Exception("Failed to decrypt value: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if a value appears to be encrypted (base64 format)
     */
    public static boolean looksEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        // Check if it's valid base64 and has minimum length
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length > GCM_IV_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Derive encryption key from password using PBKDF2
     */
    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATION_COUNT,
            KEY_LENGTH
        );
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    /**
     * Get master password from environment, system property, or config
     * Priority: ENV > System Property > Config > Default (NOT SECURE)
     */
    public static String getMasterPassword() {
        return getMasterPassword(null);
    }
    
    /**
     * Get master password with optional config value
     * Priority: ENV > System Property > Config > Default (NOT SECURE)
     */
    public static String getMasterPassword(String configPassword) {
        // Try environment variable first (MOST SECURE)
        String password = System.getenv("JANUSCOPE_MASTER_PASSWORD");
        
        // Try system property
        if (password == null || password.isEmpty()) {
            password = System.getProperty("januscope.master.password");
        }
        
        // Try config value (if provided and not empty)
        if ((password == null || password.isEmpty()) && configPassword != null && !configPassword.isEmpty()) {
            password = configPassword;
        }
        
        // Default (NOT SECURE - for development only)
        if (password == null || password.isEmpty()) {
            password = "januscope-default-master-password-change-in-production";
        }
        
        return password;
    }
}
