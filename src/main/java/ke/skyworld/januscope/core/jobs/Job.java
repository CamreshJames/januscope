package ke.skyworld.januscope.core.jobs;

import java.time.Instant;

/**
 * Represents a scheduled job
 */
public interface Job {
    /**
     * Get job name
     */
    String getName();
    
    /**
     * Execute the job
     */
    void execute() throws Exception;
    
    /**
     * Get job description
     */
    String getDescription();
    
    /**
     * Check if job is enabled
     */
    boolean isEnabled();
}
