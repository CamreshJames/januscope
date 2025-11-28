package ke.skyworld.januscope.core.jobs;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Wrapper for a job with scheduling information
 */
public class ScheduledJob {
    private final Job job;
    private final CronExpression cronExpression;
    private LocalDateTime nextExecutionTime;
    private LocalDateTime lastExecutionTime;
    private int executionCount;
    private Instant lastSuccessTime;
    private Instant lastFailureTime;
    private String lastError;
    
    public ScheduledJob(Job job, String cronExpression) {
        this.job = job;
        this.cronExpression = new CronExpression(cronExpression);
        this.nextExecutionTime = this.cronExpression.getNextExecutionTime();
        this.executionCount = 0;
    }
    
    public Job getJob() {
        return job;
    }
    
    public CronExpression getCronExpression() {
        return cronExpression;
    }
    
    public LocalDateTime getNextExecutionTime() {
        return nextExecutionTime;
    }
    
    public void updateNextExecutionTime() {
        this.nextExecutionTime = cronExpression.getNextExecutionTime();
    }
    
    public LocalDateTime getLastExecutionTime() {
        return lastExecutionTime;
    }
    
    public void setLastExecutionTime(LocalDateTime lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
        this.executionCount++;
    }
    
    public int getExecutionCount() {
        return executionCount;
    }
    
    public Instant getLastSuccessTime() {
        return lastSuccessTime;
    }
    
    public void setLastSuccessTime(Instant lastSuccessTime) {
        this.lastSuccessTime = lastSuccessTime;
        this.lastError = null;
    }
    
    public Instant getLastFailureTime() {
        return lastFailureTime;
    }
    
    public void setLastFailureTime(Instant lastFailureTime) {
        this.lastFailureTime = lastFailureTime;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
    
    public boolean shouldExecuteNow() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(nextExecutionTime) || now.isEqual(nextExecutionTime);
    }
    
    @Override
    public String toString() {
        return "ScheduledJob{" +
                "job=" + job.getName() +
                ", cron=" + cronExpression +
                ", next=" + nextExecutionTime +
                ", executions=" + executionCount +
                '}';
    }
}
