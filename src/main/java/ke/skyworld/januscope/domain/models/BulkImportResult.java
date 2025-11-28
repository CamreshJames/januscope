package ke.skyworld.januscope.domain.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of bulk import operation
 */
public class BulkImportResult {
    private int totalRecords;
    private int successCount;
    private int failureCount;
    private List<String> errors;
    private List<Integer> createdIds;
    private long processingTimeMs;
    
    public BulkImportResult() {
        this.errors = new ArrayList<>();
        this.createdIds = new ArrayList<>();
    }
    
    public void addError(int row, String message) {
        errors.add("Row " + row + ": " + message);
        failureCount++;
    }
    
    public void addSuccess(int id) {
        createdIds.add(id);
        successCount++;
    }
    
    // Getters and setters
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    
    public List<Integer> getCreatedIds() { return createdIds; }
    public void setCreatedIds(List<Integer> createdIds) { this.createdIds = createdIds; }
    
    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
}
