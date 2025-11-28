package ke.skyworld.januscope.core.jobs;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * cron expression parser
 * Supports: star-slash-N (every N units), N (specific value), star (every)
 * Format: minute hour day month dayOfWeek
 * Example: "star-slash-5 star star star star" = every 5 minutes
 */
public class CronExpression {
    private final String expression;
    private final String[] parts;
    
    public CronExpression(String expression) {
        this.expression = expression;
        this.parts = expression.split("\\s+");
        
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid cron expression. Expected 5 parts: " + expression);
        }
    }
    
    /**
     * Calculate next execution time from now
     */
    public LocalDateTime getNextExecutionTime() {
        return getNextExecutionTime(LocalDateTime.now());
    }
    
    /**
     * Calculate next execution time from given time
     */
    public LocalDateTime getNextExecutionTime(LocalDateTime from) {
        LocalDateTime next = from.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
        
        // Simple implementation for common patterns
        String minutePart = parts[0];
        
        // Handle */N pattern (every N minutes)
        if (minutePart.startsWith("*/")) {
            int interval = Integer.parseInt(minutePart.substring(2));
            int currentMinute = next.getMinute();
            int nextMinute = ((currentMinute / interval) + 1) * interval;
            
            if (nextMinute >= 60) {
                next = next.plusHours(1).withMinute(0);
            } else {
                next = next.withMinute(nextMinute);
            }
        }
        // Handle * pattern (every minute)
        else if (minutePart.equals("*")) {
            // Already set to next minute
        }
        // Handle specific minute
        else {
            int targetMinute = Integer.parseInt(minutePart);
            if (next.getMinute() >= targetMinute) {
                next = next.plusHours(1);
            }
            next = next.withMinute(targetMinute);
        }
        
        return next;
    }
    
    /**
     * Check if should execute at given time
     */
    public boolean shouldExecute(LocalDateTime time) {
        String minutePart = parts[0];
        int minute = time.getMinute();
        
        // Handle */N pattern
        if (minutePart.startsWith("*/")) {
            int interval = Integer.parseInt(minutePart.substring(2));
            return minute % interval == 0;
        }
        // Handle * pattern
        else if (minutePart.equals("*")) {
            return true;
        }
        // Handle specific minute
        else {
            int targetMinute = Integer.parseInt(minutePart);
            return minute == targetMinute;
        }
    }
    
    public String getExpression() {
        return expression;
    }
    
    @Override
    public String toString() {
        return expression;
    }
}
