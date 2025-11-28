package ke.skyworld.januscope.core.monitoring;

import ke.skyworld.januscope.config.ConfigNode;
import ke.skyworld.januscope.core.engine.BaseEngine;
import ke.skyworld.januscope.core.engine.EngineException;
import ke.skyworld.januscope.domain.models.Service;
import ke.skyworld.januscope.domain.models.SSLCheckResult;
import ke.skyworld.januscope.domain.models.UptimeCheckResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Monitoring Engine - Orchestrates uptime and SSL checks
 */
public class MonitoringEngine extends BaseEngine {
    private UptimeChecker uptimeChecker;
    private SSLChecker sslChecker;
    private ExecutorService executorService;
    
    private int defaultTimeout;
    private int defaultMaxRetries;
    private int defaultRetryDelay;
    private int threadPoolSize;
    
    @Override
    protected void doInitialize() throws Exception {
        // Get monitoring config from context
        ConfigNode monitoringConfig = (ConfigNode) context.getAttribute("monitoring-config");
        
        if (monitoringConfig == null) {
            throw new EngineException("Monitoring configuration not found in context");
        }
        
        // Load defaults
        ConfigNode defaults = monitoringConfig.getChild("defaults");
        if (defaults != null) {
            defaultTimeout = defaults.getChildValueAsInt("timeout", 10000);
            defaultMaxRetries = defaults.getChildValueAsInt("maxRetries", 3);
            defaultRetryDelay = defaults.getChildValueAsInt("retryDelay", 5000);
            
            logger.info("Monitoring defaults - Timeout: {}ms, Max Retries: {}, Retry Delay: {}ms",
                       defaultTimeout, defaultMaxRetries, defaultRetryDelay);
        }
        
        // Load thread pool config
        ConfigNode threadPool = monitoringConfig.getChild("threadPool");
        if (threadPool != null) {
            threadPoolSize = threadPool.getChildValueAsInt("maxSize", 50);
            logger.info("Thread pool size: {}", threadPoolSize);
        } else {
            threadPoolSize = 50;
        }
        
        // Initialize checkers
        uptimeChecker = new UptimeChecker(defaultTimeout, defaultMaxRetries, defaultRetryDelay);
        sslChecker = new SSLChecker(defaultTimeout);
        
        logger.info("Monitoring engine initialized successfully");
    }
    
    @Override
    protected void doStart() throws Exception {
        // Create thread pool for concurrent checks
        executorService = Executors.newFixedThreadPool(
            threadPoolSize,
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("MonitoringWorker-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            }
        );
        
        logger.info("Monitoring engine started with {} worker threads", threadPoolSize);
    }
    
    @Override
    protected void doStop() throws Exception {
        if (executorService != null && !executorService.isShutdown()) {
            logger.info("Shutting down monitoring thread pool...");
            executorService.shutdown();
            
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Monitoring thread pool shut down");
        }
    }
    
    @Override
    public String getName() {
        return "Monitoring";
    }
    
    @Override
    public boolean isHealthy() {
        return uptimeChecker != null && sslChecker != null && 
               executorService != null && !executorService.isShutdown();
    }
    
    /**
     * Check uptime for a single service
     */
    public UptimeCheckResult checkUptime(Service service) {
        return uptimeChecker.check(service);
    }
    
    /**
     * Check SSL for a single service
     */
    public SSLCheckResult checkSSL(Service service) {
        return sslChecker.check(service);
    }
    
    /**
     * Check uptime for multiple services concurrently
     */
    public List<UptimeCheckResult> checkUptimeBatch(List<Service> services) {
        logger.info("Starting batch uptime check for {} services", services.size());
        
        List<Future<UptimeCheckResult>> futures = new ArrayList<>();
        
        for (Service service : services) {
            Future<UptimeCheckResult> future = executorService.submit(() -> checkUptime(service));
            futures.add(future);
        }
        
        List<UptimeCheckResult> results = new ArrayList<>();
        for (Future<UptimeCheckResult> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.error("Failed to get uptime check result", e);
            }
        }
        
        logger.info("Batch uptime check completed - {}/{} successful", 
                   results.stream().filter(UptimeCheckResult::isUp).count(), 
                   results.size());
        
        return results;
    }
    
    /**
     * Check SSL for multiple services concurrently
     */
    public List<SSLCheckResult> checkSSLBatch(List<Service> services) {
        logger.info("Starting batch SSL check for {} services", services.size());
        
        List<Future<SSLCheckResult>> futures = new ArrayList<>();
        
        for (Service service : services) {
            Future<SSLCheckResult> future = executorService.submit(() -> checkSSL(service));
            futures.add(future);
        }
        
        List<SSLCheckResult> results = new ArrayList<>();
        for (Future<SSLCheckResult> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.error("Failed to get SSL check result", e);
            }
        }
        
        logger.info("Batch SSL check completed - {}/{} valid", 
                   results.stream().filter(SSLCheckResult::isValid).count(), 
                   results.size());
        
        return results;
    }
    
    /**
     * Get monitoring statistics
     */
    public String getStats() {
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
            return String.format("Monitoring Stats - Active: %d, Completed: %d, Queue: %d",
                tpe.getActiveCount(),
                tpe.getCompletedTaskCount(),
                tpe.getQueue().size()
            );
        }
        return "Monitoring engine running";
    }
}
