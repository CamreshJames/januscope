package ke.skyworld.januscope.utils;

import ke.skyworld.januscope.config.ConfigEncryption;

import java.util.Scanner;

/**
 * Command-line tool for encrypting configuration values
 * 
 * Usage:
 *   java -cp januscope.jar ke.skyworld.januscope.utils.ConfigEncryptionTool
 * 
 * Or with custom master password:
 *   JANUSCOPE_MASTER_PASSWORD=your-password java -cp januscope.jar ke.skyworld.januscope.utils.ConfigEncryptionTool
 */
public class ConfigEncryptionTool {
    
    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║         JANUSCOPE CONFIGURATION ENCRYPTION TOOL                   ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        try {
            // Get master password
            String masterPassword = ConfigEncryption.getMasterPassword();
            System.out.println("Master Password Source: " + getMasterPasswordSource());
            System.out.println();
            
            // Initialize encryption
            ConfigEncryption encryption = new ConfigEncryption(masterPassword);
            
            Scanner scanner = new Scanner(System.in);
            
            while (true) {
                System.out.println("Choose an option:");
                System.out.println("  1. Encrypt a value");
                System.out.println("  2. Decrypt a value");
                System.out.println("  3. Exit");
                System.out.print("\nOption: ");
                
                String option = scanner.nextLine().trim();
                System.out.println();
                
                switch (option) {
                    case "1":
                        encryptValue(scanner, encryption);
                        break;
                    case "2":
                        decryptValue(scanner, encryption);
                        break;
                    case "3":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid option. Please choose 1, 2, or 3.");
                }
                
                System.out.println();
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void encryptValue(Scanner scanner, ConfigEncryption encryption) {
        System.out.print("Enter plaintext value to encrypt: ");
        String plaintext = scanner.nextLine();
        
        if (plaintext.isEmpty()) {
            System.out.println("ERROR: Value cannot be empty");
            return;
        }
        
        try {
            String encrypted = encryption.encrypt(plaintext);
            
            System.out.println("\n✅ Encryption successful!");
            System.out.println("\nEncrypted value:");
            System.out.println("─────────────────────────────────────────────────────────────────");
            System.out.println(encrypted);
            System.out.println("─────────────────────────────────────────────────────────────────");
            System.out.println("\nAdd to your application.xml:");
            System.out.println("<password status=\"encrypted\">" + encrypted + "</password>");
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to encrypt value: " + e.getMessage());
        }
    }
    
    private static void decryptValue(Scanner scanner, ConfigEncryption encryption) {
        System.out.print("Enter encrypted value to decrypt: ");
        String encrypted = scanner.nextLine().trim();
        
        if (encrypted.isEmpty()) {
            System.out.println("ERROR: Value cannot be empty");
            return;
        }
        
        try {
            String plaintext = encryption.decrypt(encrypted);
            
            System.out.println("\n✅ Decryption successful!");
            System.out.println("\nDecrypted value:");
            System.out.println("─────────────────────────────────────────────────────────────────");
            System.out.println(plaintext);
            System.out.println("─────────────────────────────────────────────────────────────────");
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("ERROR: Failed to decrypt value: " + e.getMessage());
            System.err.println("Make sure you're using the correct master password.");
        }
    }
    
    private static String getMasterPasswordSource() {
        if (System.getenv("JANUSCOPE_MASTER_PASSWORD") != null) {
            return "Environment Variable (JANUSCOPE_MASTER_PASSWORD)";
        } else if (System.getProperty("januscope.master.password") != null) {
            return "System Property (januscope.master.password)";
        } else {
            return "Default (⚠️ NOT SECURE - Change in production!)";
        }
    }
}
