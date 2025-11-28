package ke.skyworld.januscope.core.notification;

import ke.skyworld.januscope.domain.models.NotificationRequest;
import ke.skyworld.januscope.domain.models.NotificationResult;

/**
 * Interface for notification channels (Email, Telegram, SMS)
 */
public interface NotificationChannel {
    /**
     * Get channel name
     */
    String getChannelName();
    
    /**
     * Check if channel is enabled
     */
    boolean isEnabled();
    
    /**
     * Send notification
     */
    NotificationResult send(NotificationRequest request);
    
    /**
     * Test channel connectivity
     */
    boolean testConnection();
}
