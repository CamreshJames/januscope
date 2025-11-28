package ke.skyworld.januscope.core.jobs;

import ke.skyworld.januscope.core.engine.BaseEngine;
import ke.skyworld.januscope.core.engine.EngineException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Job Engine - Manages scheduled background jobs
 */
public class JobEngine extends BaseEngine {
    private final List<ScheduledJob> scheduledJobs = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;
    private ExecutorService jobExecutor;
    private volatile boolean running = false;
    
    @Override
    protected void doInitialize() throws Exception {
        logger.info("Job engine initialized");
    }
    
    @Override
    protected void doStart() throws Exception {
        // Create scheduler for checking jobs
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("JobScheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Create executor for running jobs
        jobExecutor = Executors.newFixedThreadPool(5, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("JobWorker-" + (++counter));
                t.setDaemon(true);
                return t;
            }
        });
        
        running = true;
        
        // Start scheduler - check every minute
        scheduler.scheduleAtFixedRate(this::checkAndExecuteJobs, 0, 1, TimeUnit.MINUTES);
        
        logger.info("Job engine started - Checking jobs every minute");
        logger.info("Scheduled jobs: {}", scheduledJobs.size());
    }
    
    @Override
    protected void doStop() throws Exception {
        running = false;
        
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("Shutting down job scheduler...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (jobExecutor != null && !jobExecutor.isShutdown()) {
            logger.info("Shutting down job executor...");
            jobExecutor.shutdown();
            try {
                if (!jobExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    jobExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                jobExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Job engine stopped");
    }
    
    @Override
    public String getName() {
        return "Job";
    }
    
    @Override
    public boolean isHealthy() {
        return running && scheduler != null && !scheduler.isShutdown() &&
               jobExecutor != null && !jobExecutor.isShutdown();
    }
    
    /**
     * Schedule a job with cron expression
     */
    public void scheduleJob(Job job, String cronExpression) {
        if (!job.isEnabled()) {
            logger.info("Job {} is disabled, not scheduling", job.getName());
            return;
        }
        
        ScheduledJob scheduledJob = new ScheduledJob(job, cronExpression);
        scheduledJobs.add(scheduledJob);
        
        logger.info("Scheduled job: {} with cron: {} (next: {})", 
                   job.getName(), 
                   cronExpression, 
                   scheduledJob.getNextExecutionTime());
    }
    
    /**
     * Check and execute jobs that are due
     */
    private void checkAndExecuteJobs() {
        if (!running) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        logger.debug("Checking scheduled jobs at {}", now);
        
        for (ScheduledJob scheduledJob : scheduledJobs) {
            if (scheduledJob.shouldExecuteNow()) {
                executeJob(scheduledJob);
            }
        }
    }
    
    /**
     * Execute a job asynchronously
     */
    private void executeJob(ScheduledJob scheduledJob) {
        Job job = scheduledJob.getJob();
        
        logger.info("Executing job: {}", job.getName());
        
        jobExecutor.submit(() -> {
            Instant startTime = Instant.now();
            
            try {
                job.execute();
                
                Instant endTime = Instant.now();
                long duration = endTime.toEpochMilli() - startTime.toEpochMilli();
                
                scheduledJob.setLastExecutionTime(LocalDateTime.now());
                scheduledJob.setLastSuccessTime(endTime);
                scheduledJob.updateNextExecutionTime();
                
                logger.info("Job {} completed successfully in {}ms (next: {})", 
                           job.getName(), 
                           duration, 
                           scheduledJob.getNextExecutionTime());
                
            } catch (Exception e) {
                Instant endTime = Instant.now();
                long duration = endTime.toEpochMilli() - startTime.toEpochMilli();
                
                scheduledJob.setLastExecutionTime(LocalDateTime.now());
                scheduledJob.setLastFailureTime(endTime);
                scheduledJob.setLastError(e.getMessage());
                scheduledJob.updateNextExecutionTime();
                
                logger.error("Job {} failed after {}ms: {}", 
                            job.getName(), 
                            duration, 
                            e.getMessage());
            }
        });
    }
    
    /**
     * Get all scheduled jobs
     */
    public List<ScheduledJob> getScheduledJobs() {
        return new ArrayList<>(scheduledJobs);
    }
    
    /**
     * Get job statistics
     */
    public String getStats() {
        int totalJobs = scheduledJobs.size();
        int enabledJobs = (int) scheduledJobs.stream()
            .filter(sj -> sj.getJob().isEnabled())
            .count();
        int totalExecutions = scheduledJobs.stream()
            .mapToInt(ScheduledJob::getExecutionCount)
            .sum();
        
        return String.format("Job Stats - Total: %d, Enabled: %d, Executions: %d",
            totalJobs, enabledJobs, totalExecutions);
    }
}
