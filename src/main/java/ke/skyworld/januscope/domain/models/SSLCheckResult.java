package ke.skyworld.januscope.domain.models;

import java.time.Instant;

/**
 * Result of an SSL certificate check
 */
public class SSLCheckResult {
    private Integer serviceId;
    private String domain;
    private String issuer;
    private String subject;
    private Instant validFrom;
    private Instant validTo;
    private Integer daysRemaining;
    private String serialNumber;
    private String fingerprint;
    private String algorithm;
    private Integer keySize;
    private boolean isSelfSigned;
    private boolean isValid;
    private Instant lastCheckedAt;
    private String errorMessage;
    
    public SSLCheckResult() {
        this.lastCheckedAt = Instant.now();
        this.isValid = true;
    }
    
    // Getters and Setters
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    
    public Instant getValidTo() { return validTo; }
    public void setValidTo(Instant validTo) { this.validTo = validTo; }
    
    public Integer getDaysRemaining() { return daysRemaining; }
    public void setDaysRemaining(Integer daysRemaining) { this.daysRemaining = daysRemaining; }
    
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    
    public Integer getKeySize() { return keySize; }
    public void setKeySize(Integer keySize) { this.keySize = keySize; }
    
    public boolean isSelfSigned() { return isSelfSigned; }
    public void setSelfSigned(boolean selfSigned) { isSelfSigned = selfSigned; }
    
    public boolean isValid() { return isValid; }
    public void setValid(boolean valid) { isValid = valid; }
    
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    @Override
    public String toString() {
        return "SSLCheckResult{" +
                "serviceId=" + serviceId +
                ", domain='" + domain + '\'' +
                ", daysRemaining=" + daysRemaining +
                ", isValid=" + isValid +
                ", issuer='" + issuer + '\'' +
                '}';
    }
}
