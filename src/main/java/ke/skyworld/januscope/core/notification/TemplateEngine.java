package ke.skyworld.januscope.core.notification;

import ke.skyworld.januscope.utils.Logger;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * template engine for variable substitution
 * Supports {{variable}} syntax
 */
public class TemplateEngine {
    private static final Logger logger = Logger.getLogger(TemplateEngine.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    /**
     * Process template with variables
     * Example: "Service {{service_name}} is {{status}}" with {service_name: "API", status: "DOWN"}
     * Result: "Service API is DOWN"
     */
    public static String process(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            String value = variables.getOrDefault(variableName, "{{" + variableName + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        
        matcher.appendTail(result);
        
        logger.debug("Template processed: {} variables replaced", variables.size());
        return result.toString();
    }
    
    /**
     * Create default service down template
     */
    public static String getServiceDownTemplate() {
        return "⚠️ ALERT: Service Down\n\n" +
               "Service: {{service_name}}\n" +
               "URL: {{service_url}}\n" +
               "Status: DOWN\n" +
               "Time: {{down_time}}\n" +
               "Error: {{error_message}}\n" +
               "HTTP Code: {{http_code}}\n\n" +
               "Please investigate immediately.";
    }
    
    /**
     * Create default service recovered template
     */
    public static String getServiceRecoveredTemplate() {
        return "✅ RESOLVED: Service Recovered\n\n" +
               "Service: {{service_name}}\n" +
               "URL: {{service_url}}\n" +
               "Status: UP\n" +
               "Downtime: {{downtime_duration}}\n" +
               "Recovered: {{recovered_time}}\n\n" +
               "Service is back online.";
    }
    
    /**
     * Create default SSL expiry template
     */
    public static String getSSLExpiryTemplate() {
        return "⚠️ WARNING: SSL Certificate Expiring Soon\n\n" +
               "Service: {{service_name}}\n" +
               "Domain: {{domain}}\n" +
               "Days Remaining: {{days_remaining}}\n" +
               "Expires: {{expiry_date}}\n" +
               "Issuer: {{issuer}}\n\n" +
               "Please renew the certificate.";
    }
}
