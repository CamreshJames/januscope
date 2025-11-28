package ke.skyworld.januscope.config;

import ke.skyworld.januscope.core.engine.BaseEngine;
import ke.skyworld.januscope.core.engine.EngineException;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration Engine - Loads and manages XML configurations.
 * Supports hot-reload and automatic decryption of sensitive values.
 */
public class ConfigurationEngine extends BaseEngine {
    private final String configDir;
    private final Map<String, ConfigNode> configurations = new ConcurrentHashMap<>();
    private ConfigEncryption encryption;
    
    public ConfigurationEngine(String configDir) {
        this.configDir = configDir;
    }
    
    @Override
    protected void doInitialize() throws Exception {
        logger.info("Loading configurations from: {}", configDir);
        
        // Initialize encryption support
        try {
            String masterPassword = ConfigEncryption.getMasterPassword();
            this.encryption = new ConfigEncryption(masterPassword);
            logger.info("Configuration encryption initialized");
        } catch (Exception e) {
            logger.warn("Failed to initialize encryption: {}", e.getMessage());
            logger.warn("Encrypted values will not be decrypted");
        }
        
        loadAllConfigurations();
        logger.info("Loaded {} configuration files", configurations.size());
        
        // Auto-encrypt plaintext sensitive values
        if (encryption != null) {
            autoEncryptPlaintextValues();
        }
    }
    
    @Override
    protected void doStart() throws Exception {
        // Configuration engine is passive, no active start needed
        logger.debug("Configuration engine ready");
    }
    
    @Override
    protected void doStop() throws Exception {
        configurations.clear();
        logger.debug("Configuration engine stopped");
    }
    
    @Override
    public String getName() {
        return "Configuration";
    }
    
    @Override
    public boolean isHealthy() {
        return !configurations.isEmpty();
    }
    
    private void loadAllConfigurations() throws Exception {
        File configDirectory = new File(configDir);
        
        if (!configDirectory.exists()) {
            throw new EngineException("Configuration directory does not exist: " + configDir);
        }
        
        if (!configDirectory.isDirectory()) {
            throw new EngineException("Configuration path is not a directory: " + configDir);
        }
        
        File[] xmlFiles = configDirectory.listFiles((dir, name) -> name.endsWith(".xml"));
        
        if (xmlFiles == null || xmlFiles.length == 0) {
            throw new EngineException("No XML configuration files found in: " + configDir);
        }
        
        for (File xmlFile : xmlFiles) {
            logger.debug("Loading configuration file: {}", xmlFile.getName());
            loadConfiguration(xmlFile);
        }
    }
    
    private void loadConfiguration(File xmlFile) throws Exception {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            // Set error handler to capture detailed XML errors
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException e) {
                    logger.warn("XML Warning at line {}, column {}: {}", 
                        e.getLineNumber(), e.getColumnNumber(), e.getMessage());
                }
                
                @Override
                public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    String errorMsg = String.format(
                        "XML Error at line %d, column %d: %s\nNear: %s",
                        e.getLineNumber(), e.getColumnNumber(), e.getMessage(),
                        getContextAroundError(xmlFile, e.getLineNumber())
                    );
                    logger.error(errorMsg);
                    throw e;
                }
                
                @Override
                public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    String errorMsg = String.format(
                        "XML Fatal Error at line %d, column %d: %s\nNear: %s",
                        e.getLineNumber(), e.getColumnNumber(), e.getMessage(),
                        getContextAroundError(xmlFile, e.getLineNumber())
                    );
                    logger.error(errorMsg);
                    throw e;
                }
            });
            
            Document doc = builder.parse(xmlFile);
            doc.getDocumentElement().normalize();
            
            ConfigNode rootNode = parseNode(doc.getDocumentElement());
            configurations.put(xmlFile.getName(), rootNode);
            
            logger.info("Loaded configuration: {}", xmlFile.getName());
        } catch (org.xml.sax.SAXParseException e) {
            String errorMsg = String.format(
                "XML Parse Error in %s at line %d, column %d:\n%s\n\nContext:\n%s",
                xmlFile.getName(), e.getLineNumber(), e.getColumnNumber(), 
                e.getMessage(), getContextAroundError(xmlFile, e.getLineNumber())
            );
            throw new EngineException(errorMsg, e);
        } catch (Exception e) {
            throw new EngineException("Failed to load config: " + xmlFile.getName(), e);
        }
    }
    
    /**
     * Get context around error line for better debugging
     */
    private String getContextAroundError(File xmlFile, int errorLine) {
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(xmlFile.toPath());
            StringBuilder context = new StringBuilder();
            
            int start = Math.max(0, errorLine - 3);
            int end = Math.min(lines.size(), errorLine + 2);
            
            for (int i = start; i < end; i++) {
                String prefix = (i + 1 == errorLine) ? ">>> " : "    ";
                context.append(String.format("%s%4d: %s\n", prefix, i + 1, lines.get(i)));
            }
            
            return context.toString();
        } catch (Exception e) {
            return "(Unable to read context)";
        }
    }
    
    private ConfigNode parseNode(Element element) {
        ConfigNode node = new ConfigNode(element.getNodeName());
        
        // Set encryption if available
        if (encryption != null) {
            node.setEncryption(encryption);
        }
        
        // Parse attributes
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            node.setAttribute(attr.getNodeName(), attr.getNodeValue());
        }
        
        // Parse child nodes
        NodeList children = element.getChildNodes();
        boolean hasElementChildren = false;
        StringBuilder textContent = new StringBuilder();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                node.addChild(parseNode((Element) child));
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }
        
        // Only set value if there are no element children
        if (!hasElementChildren && textContent.length() > 0) {
            node.setValue(textContent.toString());
        }
        
        return node;
    }
    
    /**
     * Get configuration by filename
     */
    public ConfigNode getConfiguration(String fileName) {
        return configurations.get(fileName);
    }
    
    /**
     * Get the main application configuration
     */
    public ConfigNode getApplicationConfig() {
        return getConfiguration("application.xml");
    }
    
    /**
     * Get a specific section from application config
     */
    public ConfigNode getConfigSection(String sectionName) {
        ConfigNode appConfig = getApplicationConfig();
        return appConfig != null ? appConfig.getChild(sectionName) : null;
    }
    
    /**
     * Check if a value is encrypted
     */
    public boolean isEncrypted(ConfigNode node) {
        String status = node.getAttribute("status");
        return "encrypted".equalsIgnoreCase(status);
    }
    
    /**
     * Get value with automatic decryption if needed
     */
    public String getValue(ConfigNode node) {
        if (node == null) {
            return null;
        }
        
        String value = node.getValue();
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // Check if value is marked as encrypted
        if (isEncrypted(node)) {
            if (encryption == null) {
                logger.error("Cannot decrypt value - encryption not initialized");
                return null;
            }
            
            try {
                return encryption.decrypt(value);
            } catch (Exception e) {
                logger.error("Failed to decrypt value: {}", e.getMessage());
                return null;
            }
        }
        
        return value;
    }
    
    /**
     * Get encryption utility (for encrypting new values)
     */
    public ConfigEncryption getEncryption() {
        return encryption;
    }
    
    /**
     * Reload all configurations
     */
    public void reload() throws Exception {
        logger.info("Reloading configurations...");
        configurations.clear();
        loadAllConfigurations();
        logger.info("Configurations reloaded successfully");
    }
    
    /**
     * Auto-encrypt plaintext sensitive values and update config files
     */
    private void autoEncryptPlaintextValues() {
        logger.info("Checking for plaintext sensitive values...");
        
        File configDirectory = new File(configDir);
        File[] xmlFiles = configDirectory.listFiles((dir, name) -> name.endsWith(".xml"));
        
        if (xmlFiles == null) {
            return;
        }
        
        int totalEncrypted = 0;
        
        for (File xmlFile : xmlFiles) {
            try {
                int encrypted = autoEncryptFile(xmlFile);
                if (encrypted > 0) {
                    totalEncrypted += encrypted;
                    logger.info("Auto-encrypted {} values in {}", encrypted, xmlFile.getName());
                }
            } catch (Exception e) {
                logger.error("Failed to auto-encrypt {}: {}", xmlFile.getName(), e.getMessage());
            }
        }
        
        if (totalEncrypted > 0) {
            logger.warn("⚠️  AUTO-ENCRYPTED {} PLAINTEXT SENSITIVE VALUES", totalEncrypted);
            logger.warn("⚠️  Configuration files have been updated with encrypted values");
            logger.warn("⚠️  Please review the changes and commit to version control");
        } else {
            logger.info("✅ No plaintext sensitive values found - all secure!");
        }
    }
    
    /**
     * Auto-encrypt plaintext values in a single file
     */
    private int autoEncryptFile(File xmlFile) throws Exception {
        // Parse XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setIgnoringElementContentWhitespace(false);
        factory.setIgnoringComments(false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();
        
        // Track if any changes were made
        int encryptedCount = 0;
        
        // Find and encrypt plaintext sensitive values
        encryptedCount += processNodeForEncryption(doc.getDocumentElement());
        
        // Save if changes were made
        if (encryptedCount > 0) {
            saveXmlDocument(doc, xmlFile);
        }
        
        return encryptedCount;
    }
    
    /**
     * Process a node and its children for encryption
     */
    private int processNodeForEncryption(Element element) throws Exception {
        int count = 0;
        
        // Check if this node should be encrypted
        if (shouldEncryptNode(element)) {
            String status = element.getAttribute("status");
            
            // Only encrypt if status is "plaintext" or missing
            if (status == null || status.isEmpty() || "plaintext".equalsIgnoreCase(status)) {
                String plaintext = element.getTextContent().trim();
                
                if (!plaintext.isEmpty()) {
                    // Encrypt the value
                    String encrypted = encryption.encrypt(plaintext);
                    
                    // Update the element
                    element.setTextContent(encrypted);
                    element.setAttribute("status", "encrypted");
                    
                    logger.debug("Encrypted <{}> value", element.getNodeName());
                    count++;
                }
            }
        }
        
        // Process child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count += processNodeForEncryption((Element) child);
            }
        }
        
        return count;
    }
    
    /**
     * Determine if a node contains sensitive data that should be encrypted
     */
    private boolean shouldEncryptNode(Element element) {
        String nodeName = element.getNodeName().toLowerCase();
        
        // List of sensitive field names
        String[] sensitiveFields = {
            "password",
            "secret",
            "token",
            "bottoken",
            "apikey",
            "api_key",
            "privatekey",
            "private_key",
            "accesskey",
            "access_key",
            "secretkey",
            "secret_key",
            "username",  // Database username
            "url"        // Database URL (may contain credentials)
        };
        
        for (String field : sensitiveFields) {
            if (nodeName.equals(field) || nodeName.endsWith(field)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Save XML document to file with proper formatting
     */
    private void saveXmlDocument(Document doc, File file) throws Exception {
        javax.xml.transform.TransformerFactory transformerFactory = 
            javax.xml.transform.TransformerFactory.newInstance();
        javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
        
        // Set output properties for nice formatting
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        
        // Write to file
        javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(doc);
        javax.xml.transform.stream.StreamResult result = 
            new javax.xml.transform.stream.StreamResult(file);
        
        transformer.transform(source, result);
        
        logger.debug("Saved updated configuration to: {}", file.getName());
    }
}
