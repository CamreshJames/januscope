package ke.skyworld.januscope.config;

import java.util.*;

/**
 * Represents a node in the XML configuration tree.
 * Each node can have attributes, a value, and child nodes.
 * Supports automatic decryption of values marked with status="encrypted"
 */
public class ConfigNode {
    private final String name;
    private String value;
    private final Map<String, String> attributes = new HashMap<>();
    private final List<ConfigNode> children = new ArrayList<>();
    private ConfigEncryption encryption;
    
    public ConfigNode(String name) {
        this.name = name;
    }
    
    /**
     * Set encryption utility for automatic decryption
     */
    public void setEncryption(ConfigEncryption encryption) {
        this.encryption = encryption;
        // Propagate to all children
        for (ConfigNode child : children) {
            child.setEncryption(encryption);
        }
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Get value with automatic decryption if status="encrypted"
     */
    public String getValue() {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // Check if value is marked as encrypted
        String status = getAttribute("status");
        if ("encrypted".equalsIgnoreCase(status)) {
            if (encryption == null) {
                throw new RuntimeException("Cannot decrypt value - encryption not initialized for node: " + name);
            }
            
            try {
                return encryption.decrypt(value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decrypt value for node: " + name, e);
            }
        }
        
        return value;
    }
    
    /**
     * Get raw value without decryption
     */
    public String getRawValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }
    
    public String getAttribute(String key) {
        return attributes.get(key);
    }
    
    public String getAttribute(String key, String defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }
    
    public void addChild(ConfigNode child) {
        children.add(child);
    }
    
    public List<ConfigNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
    
    public ConfigNode getChild(String name) {
        return children.stream()
            .filter(c -> c.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    public List<ConfigNode> getChildren(String name) {
        return children.stream()
            .filter(c -> c.getName().equals(name))
            .toList();
    }
    
    public String getChildValue(String name) {
        ConfigNode child = getChild(name);
        return child != null ? child.getValue() : null;
    }
    
    public String getChildValue(String name, String defaultValue) {
        String value = getChildValue(name);
        return value != null ? value : defaultValue;
    }
    
    public int getChildValueAsInt(String name, int defaultValue) {
        String value = getChildValue(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public boolean getChildValueAsBoolean(String name, boolean defaultValue) {
        String value = getChildValue(name);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }
    
    @Override
    public String toString() {
        return "ConfigNode{name='" + name + "', value='" + value + "', children=" + children.size() + "}";
    }
}
