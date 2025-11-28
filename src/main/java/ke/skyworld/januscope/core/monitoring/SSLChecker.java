package ke.skyworld.januscope.core.monitoring;

import ke.skyworld.januscope.domain.models.Service;
import ke.skyworld.januscope.domain.models.SSLCheckResult;
import ke.skyworld.januscope.utils.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * SSL Certificate Checker
 */
public class SSLChecker {
    private static final Logger logger = Logger.getLogger(SSLChecker.class);
    private final int timeout;
    
    public SSLChecker(int timeout) {
        this.timeout = timeout;
    }
    
    /**
     * Check SSL certificate for a service
     */
    public SSLCheckResult check(Service service) {
        SSLCheckResult result = new SSLCheckResult();
        result.setServiceId(service.getServiceId());
        result.setLastCheckedAt(Instant.now());
        
        try {
            URL url = new URL(service.getUrl());
            String domain = url.getHost();
            result.setDomain(domain);
            
            // Only check HTTPS URLs
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                result.setValid(false);
                result.setErrorMessage("Not an HTTPS URL");
                return result;
            }
            
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.connect();
            
            Certificate[] certs = conn.getServerCertificates();
            conn.disconnect();
            
            if (certs.length > 0 && certs[0] instanceof X509Certificate) {
                X509Certificate cert = (X509Certificate) certs[0];
                extractCertificateInfo(result, cert);
            } else {
                result.setValid(false);
                result.setErrorMessage("No valid X509 certificate found");
            }
            
        } catch (Exception e) {
            logger.error("Error checking SSL for service " + service.getServiceId(), e);
            result.setValid(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    private void extractCertificateInfo(SSLCheckResult result, X509Certificate cert) {
        try {
            // Basic certificate info
            result.setIssuer(cert.getIssuerDN().getName());
            result.setSubject(cert.getSubjectDN().getName());
            result.setSerialNumber(cert.getSerialNumber().toString(16).toUpperCase());
            result.setAlgorithm(cert.getSigAlgName());
            
            // Validity dates
            Date notBefore = cert.getNotBefore();
            Date notAfter = cert.getNotAfter();
            result.setValidFrom(notBefore.toInstant());
            result.setValidTo(notAfter.toInstant());
            
            // Calculate days remaining
            Instant now = Instant.now();
            long daysRemaining = ChronoUnit.DAYS.between(now, notAfter.toInstant());
            result.setDaysRemaining((int) daysRemaining);
            
            // Check if certificate is currently valid
            try {
                cert.checkValidity();
                result.setValid(true);
            } catch (Exception e) {
                result.setValid(false);
                result.setErrorMessage("Certificate not valid: " + e.getMessage());
            }
            
            // Check if self-signed
            result.setSelfSigned(cert.getIssuerDN().equals(cert.getSubjectDN()));
            
            // Get key size
            try {
                String keyAlgorithm = cert.getPublicKey().getAlgorithm();
                if ("RSA".equals(keyAlgorithm)) {
                    java.security.interfaces.RSAPublicKey rsaKey = 
                        (java.security.interfaces.RSAPublicKey) cert.getPublicKey();
                    result.setKeySize(rsaKey.getModulus().bitLength());
                } else if ("EC".equals(keyAlgorithm)) {
                    java.security.interfaces.ECPublicKey ecKey = 
                        (java.security.interfaces.ECPublicKey) cert.getPublicKey();
                    result.setKeySize(ecKey.getParams().getOrder().bitLength());
                }
            } catch (Exception e) {
                logger.warn("Could not determine key size: " + e.getMessage());
            }
            
            // Calculate fingerprint (SHA-256)
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] der = cert.getEncoded();
                md.update(der);
                byte[] digest = md.digest();
                result.setFingerprint(bytesToHex(digest));
            } catch (Exception e) {
                logger.warn("Could not calculate fingerprint: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error extracting certificate info: " + e.getMessage());
            result.setValid(false);
            result.setErrorMessage(e.getMessage());
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }
}
