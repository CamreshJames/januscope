package ke.skyworld.januscope.domain.models;

import java.time.Instant;

/**
 * Result of an uptime check
 */
public class UptimeCheckResult {
    private Integer serviceId;
    private String status; // UP or DOWN
    private Integer responseTimeMs;
    private Integer httpCode;
    private String errorMessage;
    private Instant checkedAt;
    
    public UptimeCheckResult() {
        this.checkedAt = Instant.now();
    }
    
    public UptimeCheckResult(Integer serviceId, String status, Integer responseTimeMs, 
                            Integer httpCode, String errorMessage, Instant checkedAt) {
        this.serviceId = serviceId;
        this.status = status;
        this.responseTimeMs = responseTimeMs;
        this.httpCode = httpCode;
        this.errorMessage = errorMessage;
        this.checkedAt = checkedAt;
    }
    
    public static UptimeCheckResult success(Integer serviceId, int responseTimeMs, int httpCode) {
        return new UptimeCheckResult(serviceId, "UP", responseTimeMs, httpCode, null, Instant.now());
    }
    
    public static UptimeCheckResult failure(Integer serviceId, String errorMessage) {
        return new UptimeCheckResult(serviceId, "DOWN", null, null, errorMessage, Instant.now());
    }
    
    public static UptimeCheckResult failure(Integer serviceId, int httpCode, String errorMessage) {
        return new UptimeCheckResult(serviceId, "DOWN", null, httpCode, errorMessage, Instant.now());
    }
    
    // Getters and Setters
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    
    public Integer getHttpCode() { return httpCode; }
    public void setHttpCode(Integer httpCode) { this.httpCode = httpCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
    
    public boolean isUp() {
        return "UP".equals(status);
    }
    
    @Override
    public String toString() {
        return "UptimeCheckResult{" +
                "serviceId=" + serviceId +
                ", status='" + status + '\'' +
                ", responseTimeMs=" + responseTimeMs +
                ", httpCode=" + httpCode +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
