package ke.skyworld.januscope.core.notification.channels;

import ke.skyworld.januscope.core.notification.NotificationChannel;
import ke.skyworld.januscope.domain.models.NotificationRequest;
import ke.skyworld.januscope.domain.models.NotificationResult;
import ke.skyworld.januscope.utils.Logger;

/**
 * Console notification channel for testing/demo purposes
 */
public class ConsoleChannel implements NotificationChannel {
    private static final Logger logger = Logger.getLogger(ConsoleChannel.class);
    
    @Override
    public String getChannelName() {
        return "Console";
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public NotificationResult send(NotificationRequest request) {
        logger.info("========================================");
        logger.info("📧 NOTIFICATION (Console)");
        logger.info("To: {}", request.getRecipient());
        if (request.getSubject() != null) {
            logger.info("Subject: {}", request.getSubject());
        }
        logger.info("Message:");
        logger.info("{}", request.getMessage());
        logger.info("========================================");
        
        return NotificationResult.success("Console");
    }
    
    @Override
    public boolean testConnection() {
        return true;
    }
}
