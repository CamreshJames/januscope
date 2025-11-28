package ke.skyworld.januscope.core.notification.channels;

import ke.skyworld.januscope.core.notification.NotificationChannel;
import ke.skyworld.januscope.domain.models.NotificationRequest;
import ke.skyworld.januscope.domain.models.NotificationResult;
import ke.skyworld.januscope.utils.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Telegram notification channel using Bot API
 */
public class TelegramChannel implements NotificationChannel {
    private static final Logger logger = Logger.getLogger(TelegramChannel.class);
    
    private final boolean enabled;
    private final String botToken;
    private final String apiUrl;
    
    public TelegramChannel(boolean enabled, String botToken, String apiUrl) {
        this.enabled = enabled;
        this.botToken = botToken;
        this.apiUrl = apiUrl;
        
        if (enabled) {
            logger.info("Telegram channel initialized - API: {}", apiUrl);
        }
    }
    
    @Override
    public String getChannelName() {
        return "Telegram";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public NotificationResult send(NotificationRequest request) {
        if (!enabled) {
            logger.warn("Telegram channel is disabled");
            return NotificationResult.failure("Telegram", "Channel is disabled");
        }
        
        try {
            logger.debug("Sending Telegram message to: {}", request.getRecipient());
            
            // Format message with subject
            String fullMessage = request.getSubject() != null 
                ? "*" + request.getSubject() + "*\n\n" + request.getMessage()
                : request.getMessage();
            
            // Build API URL
            String urlString = String.format("%s/bot%s/sendMessage", apiUrl, botToken);
            URL url = new URL(urlString);
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            
            // Build request body
            String chatId = request.getRecipient();
            String text = URLEncoder.encode(fullMessage, StandardCharsets.UTF_8);
            String body = "chat_id=" + chatId + "&text=" + text + "&parse_mode=Markdown";
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                logger.info("✓ Telegram message sent successfully to: {}", request.getRecipient());
                return NotificationResult.success("Telegram");
            } else {
                String error = "HTTP " + responseCode;
                logger.error("✗ Failed to send Telegram message to: {} - {}", 
                           request.getRecipient(), error);
                return NotificationResult.failure("Telegram", error);
            }
            
        } catch (Exception e) {
            logger.error("✗ Failed to send Telegram message to: {} - {}", 
                        request.getRecipient(), e.getMessage());
            return NotificationResult.failure("Telegram", e.getMessage());
        }
    }
    
    @Override
    public boolean testConnection() {
        if (!enabled) {
            return false;
        }
        
        try {
            // Test by calling getMe API
            String urlString = String.format("%s/bot%s/getMe", apiUrl, botToken);
            URL url = new URL(urlString);
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            boolean success = responseCode == 200;
            
            if (success) {
                logger.info("Telegram channel connection test: SUCCESS");
            } else {
                logger.error("Telegram channel connection test: FAILED - HTTP {}", responseCode);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("Telegram channel connection test: FAILED - {}", e.getMessage());
            return false;
        }
    }
}
