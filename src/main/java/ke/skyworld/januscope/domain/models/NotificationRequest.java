package ke.skyworld.januscope.domain.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Notification request with recipient and message details
 */
public class NotificationRequest {
    private String recipient;
    private String subject;
    private String message;
    private String eventType;
    private Integer serviceId;
    private Map<String, String> variables;
    
    public NotificationRequest() {
        this.variables = new HashMap<>();
    }
    
    public NotificationRequest(String recipient, String subject, String message) {
        this();
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
    }
    
    // Getters and Setters
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    
    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }
    
    public void addVariable(String key, String value) {
        this.variables.put(key, value);
    }
    
    @Override
    public String toString() {
        return "NotificationRequest{" +
                "recipient='" + recipient + '\'' +
                ", subject='" + subject + '\'' +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
