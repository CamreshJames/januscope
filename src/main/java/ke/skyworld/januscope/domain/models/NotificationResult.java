package ke.skyworld.januscope.domain.models;

import java.time.Instant;

/**
 * Result of a notification delivery attempt
 */
public class NotificationResult {
    private boolean success;
    private String status; // SENT, FAILED, PENDING
    private String errorMessage;
    private Instant sentAt;
    private String channel;
    
    public NotificationResult() {
        this.sentAt = Instant.now();
    }
    
    public static NotificationResult success(String channel) {
        NotificationResult result = new NotificationResult();
        result.setSuccess(true);
        result.setStatus("SENT");
        result.setChannel(channel);
        return result;
    }
    
    public static NotificationResult failure(String channel, String errorMessage) {
        NotificationResult result = new NotificationResult();
        result.setSuccess(false);
        result.setStatus("FAILED");
        result.setChannel(channel);
        result.setErrorMessage(errorMessage);
        return result;
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    
    @Override
    public String toString() {
        return "NotificationResult{" +
                "success=" + success +
                ", status='" + status + '\'' +
                ", channel='" + channel + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
