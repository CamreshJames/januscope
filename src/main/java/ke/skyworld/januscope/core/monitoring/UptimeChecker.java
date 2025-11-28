package ke.skyworld.januscope.core.monitoring;

import ke.skyworld.januscope.domain.models.Service;
import ke.skyworld.januscope.domain.models.UptimeCheckResult;
import ke.skyworld.januscope.utils.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.Map;

/**
 * Performs HTTP/HTTPS uptime checks with retry logic
 */
public class UptimeChecker {
    private static final Logger logger = Logger.getLogger(UptimeChecker.class);
    
    private final int timeout;
    private final int maxRetries;
    private final int retryDelay;
    
    public UptimeChecker(int timeout, int maxRetries, int retryDelay) {
        this.timeout = timeout;
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }
    
    /**
     * Check service uptime with retry logic
     */
    public UptimeCheckResult check(Service service) {
        logger.debug("Checking uptime for service: {} ({})", service.getName(), service.getUrl());
        
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetries) {
            try {
                UptimeCheckResult result = performCheck(service);
                
                if (result.isUp()) {
                    logger.info("✓ Service UP: {} - {}ms (HTTP {})", 
                               service.getName(), 
                               result.getResponseTimeMs(), 
                               result.getHttpCode());
                    return result;
                } else if (attempt < maxRetries - 1) {
                    logger.warn("✗ Service DOWN: {} - HTTP {} (attempt {}/{})", 
                               service.getName(), 
                               result.getHttpCode(), 
                               attempt + 1, 
                               maxRetries);
                    attempt++;
                    sleep(retryDelay);
                } else {
                    logger.error("✗ Service DOWN: {} - HTTP {} (all retries exhausted)", 
                                service.getName(), 
                                result.getHttpCode());
                    return result;
                }
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                if (attempt < maxRetries) {
                    logger.warn("✗ Service check failed: {} - {} (attempt {}/{})", 
                               service.getName(), 
                               e.getMessage(), 
                               attempt, 
                               maxRetries);
                    sleep(retryDelay);
                } else {
                    logger.error("✗ Service check failed: {} - {} (all retries exhausted)", 
                                service.getName(), 
                                e.getMessage());
                }
            }
        }
        
        // All retries failed
        String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
        return UptimeCheckResult.failure(service.getServiceId(), errorMsg);
    }
    
    /**
     * Perform a single uptime check
     */
    private UptimeCheckResult performCheck(Service service) throws IOException {
        long startTime = System.currentTimeMillis();
        
        URL url = new URL(service.getUrl());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setInstanceFollowRedirects(true);
            
            // Add custom headers
            if (service.getCustomHeaders() != null) {
                for (Map.Entry<String, String> header : service.getCustomHeaders().entrySet()) {
                    conn.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            
            // Add user agent
            conn.setRequestProperty("User-Agent", "Januscope/1.0.0");
            
            // Connect and get response
            int responseCode = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Consider 2xx and 3xx as success
            boolean isUp = responseCode >= 200 && responseCode < 400;
            
            if (isUp) {
                return UptimeCheckResult.success(
                    service.getServiceId(), 
                    (int) responseTime, 
                    responseCode
                );
            } else {
                return UptimeCheckResult.failure(
                    service.getServiceId(), 
                    responseCode, 
                    "HTTP " + responseCode
                );
            }
        } finally {
            conn.disconnect();
        }
    }
    
    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Sleep interrupted", e);
        }
    }
}
