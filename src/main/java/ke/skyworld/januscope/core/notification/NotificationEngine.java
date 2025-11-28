package ke.skyworld.januscope.core.notification;

import ke.skyworld.januscope.config.ConfigNode;
import ke.skyworld.januscope.core.engine.BaseEngine;
import ke.skyworld.januscope.core.engine.EngineException;
import ke.skyworld.januscope.core.notification.channels.*;
import ke.skyworld.januscope.domain.models.NotificationRequest;
import ke.skyworld.januscope.domain.models.NotificationResult;

import java.util.*;
import java.util.concurrent.*;

/**
 * Notification Engine - Manages multi-channel notifications
 */
public class NotificationEngine extends BaseEngine {
    private final Map<String, NotificationChannel> channels = new ConcurrentHashMap<>();
    private ExecutorService executorService;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private int defaultCooldownSeconds;
    
    @Override
    protected void doInitialize() throws Exception {
        // Get notification config from context
        ConfigNode notificationConfig = (ConfigNode) context.getAttribute("notification-config");
        
        if (notificationConfig == null) {
            throw new EngineException("Notification configuration not found in context");
        }
        
        // Load cooldown configuration
        ConfigNode cooldownConfig = notificationConfig.getChild("cooldown");
        if (cooldownConfig != null) {
            defaultCooldownSeconds = cooldownConfig.getChildValueAsInt("defaultPeriod", 300);
            logger.info("Notification cooldown: {} seconds", defaultCooldownSeconds);
        } else {
            defaultCooldownSeconds = 300;
        }
        
        // Initialize channels
        ConfigNode channelsConfig = notificationConfig.getChild("channels");
        if (channelsConfig != null) {
            initializeChannels(channelsConfig);
        }
        
        // Always add console channel for demo
        channels.put("console", new ConsoleChannel());
        logger.info("Console channel added for demo purposes");
        
        logger.info("Notification engine initialized with {} channels", channels.size());
        logger.info("Registered channels: {}", String.join(", ", channels.keySet()));
    }
    
    private void initializeChannels(ConfigNode channelsConfig) {
        // Email channel
        ConfigNode emailConfig = channelsConfig.getChild("email");
        logger.debug("Email config node: {}", emailConfig != null ? "found" : "NOT FOUND");
        
        if (emailConfig != null) {
            // Read 'enabled' as an attribute, not a child value
            String enabledStr = emailConfig.getAttribute("enabled", "false");
            boolean enabled = "true".equalsIgnoreCase(enabledStr);
            logger.debug("Email enabled (from attribute '{}'): {}", enabledStr, enabled);
            
            if (enabled) {
                ConfigNode smtp = emailConfig.getChild("smtp");
                logger.debug("SMTP config node: {}", smtp != null ? "found" : "NOT FOUND");
                
                if (smtp != null) {
                    String host = smtp.getChildValue("host");
                    int port = smtp.getChildValueAsInt("port", 587);
                    String username = smtp.getChildValue("username");
                    String password = smtp.getChildValue("password");
                    boolean tls = smtp.getChildValueAsBoolean("tls", true);
                    String from = smtp.getChildValue("from");
                    
                    logger.debug("Email config - Host: {}, Port: {}, Username: {}, From: {}", 
                                host, port, username != null ? "SET" : "NULL", from);
                    
                    EmailChannel emailChannel = new EmailChannel(
                        enabled, host, port, username, password, tls, from
                    );
                    channels.put("email", emailChannel);
                    logger.info("Email channel configured and registered");
                } else {
                    logger.warn("Email enabled but SMTP config not found!");
                }
            } else {
                logger.info("Email channel is disabled in config");
            }
        } else {
            logger.warn("Email config node not found in notification config!");
        }
        
        // Telegram channel
        ConfigNode telegramConfig = channelsConfig.getChild("telegram");
        if (telegramConfig != null) {
            String enabledStr = telegramConfig.getAttribute("enabled", "false");
            boolean enabled = "true".equalsIgnoreCase(enabledStr);
            
            if (enabled) {
                String botToken = telegramConfig.getChildValue("botToken");
                String apiUrl = telegramConfig.getChildValue("apiUrl", "https://api.telegram.org");
                
                TelegramChannel telegramChannel = new TelegramChannel(enabled, botToken, apiUrl);
                channels.put("telegram", telegramChannel);
                logger.info("Telegram channel configured");
            } else {
                logger.info("Telegram channel is disabled");
            }
        }
    }
    
    @Override
    protected void doStart() throws Exception {
        // Create thread pool for async notifications
        executorService = Executors.newFixedThreadPool(
            10,
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("NotificationWorker-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            }
        );
        
        // Test channel connections
        for (NotificationChannel channel : channels.values()) {
            if (channel.isEnabled()) {
                logger.info("Testing {} channel...", channel.getChannelName());
                boolean connected = channel.testConnection();
                logger.info("{} channel test: {}", 
                           channel.getChannelName(), 
                           connected ? "SUCCESS" : "FAILED");
            }
        }
        
        logger.info("Notification engine started");
    }
    
    @Override
    protected void doStop() throws Exception {
        if (executorService != null && !executorService.isShutdown()) {
            logger.info("Shutting down notification thread pool...");
            executorService.shutdown();
            
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Notification thread pool shut down");
        }
    }
    
    @Override
    public String getName() {
        return "Notification";
    }
    
    @Override
    public boolean isHealthy() {
        return !channels.isEmpty() && executorService != null && !executorService.isShutdown();
    }
    
    /**
     * Send notification through specified channel
     */
    public NotificationResult send(String channelName, NotificationRequest request) {
        NotificationChannel channel = channels.get(channelName.toLowerCase());
        
        if (channel == null) {
            logger.error("Channel not found: {}", channelName);
            return NotificationResult.failure(channelName, "Channel not found");
        }
        
        if (!channel.isEnabled()) {
            logger.warn("Channel is disabled: {}", channelName);
            return NotificationResult.failure(channelName, "Channel is disabled");
        }
        
        // Check cooldown
        if (isInCooldown(request)) {
            logger.debug("Notification in cooldown period, skipping: {}", request);
            return NotificationResult.failure(channelName, "In cooldown period");
        }
        
        // Send notification
        NotificationResult result = channel.send(request);
        
        // Update cooldown if successful
        if (result.isSuccess()) {
            updateCooldown(request);
        }
        
        return result;
    }
    
    /**
     * Send notification through all enabled channels
     */
    public List<NotificationResult> sendToAll(NotificationRequest request) {
        List<NotificationResult> results = new ArrayList<>();
        
        for (NotificationChannel channel : channels.values()) {
            if (channel.isEnabled()) {
                NotificationResult result = send(channel.getChannelName(), request);
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Send notification asynchronously
     */
    public Future<NotificationResult> sendAsync(String channelName, NotificationRequest request) {
        return executorService.submit(() -> send(channelName, request));
    }
    
    /**
     * Check if notification is in cooldown period
     */
    private boolean isInCooldown(NotificationRequest request) {
        if (request.getServiceId() == null || request.getEventType() == null) {
            return false;
        }
        
        String key = request.getServiceId() + ":" + request.getEventType();
        Long lastSent = cooldowns.get(key);
        
        if (lastSent == null) {
            return false;
        }
        
        long elapsed = (System.currentTimeMillis() - lastSent) / 1000;
        return elapsed < defaultCooldownSeconds;
    }
    
    /**
     * Update cooldown timestamp
     */
    private void updateCooldown(NotificationRequest request) {
        if (request.getServiceId() != null && request.getEventType() != null) {
            String key = request.getServiceId() + ":" + request.getEventType();
            cooldowns.put(key, System.currentTimeMillis());
        }
    }
    
    /**
     * Get available channels
     */
    public List<String> getAvailableChannels() {
        return new ArrayList<>(channels.keySet());
    }
    
    /**
     * Get enabled channels
     */
    public List<String> getEnabledChannels() {
        return channels.values().stream()
            .filter(NotificationChannel::isEnabled)
            .map(NotificationChannel::getChannelName)
            .toList();
    }
    
    /**
     * Get notification statistics
     */
    public String getStats() {
        return String.format("Notification Stats - Channels: %d, Enabled: %d, Cooldowns: %d",
            channels.size(),
            getEnabledChannels().size(),
            cooldowns.size()
        );
    }
}
