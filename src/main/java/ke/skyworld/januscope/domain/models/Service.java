package ke.skyworld.januscope.domain.models;

import java.time.Instant;
import java.util.Map;

/**
 * Service entity - Represents a monitored HTTP/HTTPS endpoint
 */
public class Service {
    private Integer serviceId;
    private String name;
    private String url;
    private int checkIntervalSeconds;
    private int timeoutMs;
    private int maxRetries;
    private int retryDelayMs;
    private String currentStatus; // UP, DOWN, UNKNOWN
    private Instant lastCheckedAt;
    private Map<String, String> customHeaders;
    private boolean isActive;
    private boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;
    
    public Service() {
        this.checkIntervalSeconds = 300;
        this.timeoutMs = 10000;
        this.maxRetries = 3;
        this.retryDelayMs = 5000;
        this.currentStatus = "UNKNOWN";
        this.isActive = true;
        this.isDeleted = false;
    }
    
    // Getters and Setters
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public int getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public void setCheckIntervalSeconds(int checkIntervalSeconds) { 
        this.checkIntervalSeconds = checkIntervalSeconds; 
    }
    
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public int getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(int retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    
    public Map<String, String> getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(Map<String, String> customHeaders) { 
        this.customHeaders = customHeaders; 
    }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    @Override
    public String toString() {
        return "Service{" +
                "serviceId=" + serviceId +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", currentStatus='" + currentStatus + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
