package ke.skyworld.januscope.core.email;

import ke.skyworld.januscope.utils.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Universal Email Template Service
 * Loads and processes HTML email templates with variable substitution
 * 
 * Features:
 * - Template caching for performance
 * - Variable substitution with {{variable}} syntax
 * - Conditional blocks with {{#if variable}}...{{/if}}
 * - Reusable across projects
 * 
 * Usage:
 * EmailTemplateService service = new EmailTemplateService();
 * Map<String, String> vars = new HashMap<>();
 * vars.put("firstName", "John");
 * String html = service.renderTemplate("welcome", vars);
 */
public class EmailTemplateService {
    private static final Logger logger = Logger.getLogger(EmailTemplateService.class);
    private static final String TEMPLATE_PATH = "/email-templates/";
    
    private final Map<String, String> templateCache;
    private final boolean cacheEnabled;
    
    public EmailTemplateService() {
        this(true);
    }
    
    public EmailTemplateService(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        this.templateCache = cacheEnabled ? new HashMap<>() : null;
    }
    
    /**
     * Render a template with variables
     * 
     * @param templateName Name of template file (without .html extension)
     * @param variables Map of variable names to values
     * @return Rendered HTML string
     */
    public String renderTemplate(String templateName, Map<String, String> variables) {
        try {
            String template = loadTemplate(templateName);
            return processTemplate(template, variables);
        } catch (Exception e) {
            logger.error("Failed to render template: " + templateName, e);
            return getFallbackTemplate(templateName, variables);
        }
    }
    
    /**
     * Load template from resources
     */
    private String loadTemplate(String templateName) throws Exception {
        // Check cache first
        if (cacheEnabled && templateCache.containsKey(templateName)) {
            return templateCache.get(templateName);
        }
        
        String fileName = templateName.endsWith(".html") ? templateName : templateName + ".html";
        String resourcePath = TEMPLATE_PATH + fileName;
        
        InputStream inputStream = getClass().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalArgumentException("Template not found: " + resourcePath);
        }
        
        String template = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"));
        
        // Cache if enabled
        if (cacheEnabled) {
            templateCache.put(templateName, template);
        }
        
        return template;
    }
    
    /**
     * Process template with variable substitution
     */
    private String processTemplate(String template, Map<String, String> variables) {
        String result = template;
        
        // Add default variables
        Map<String, String> allVars = new HashMap<>(variables);
        allVars.putIfAbsent("year", String.valueOf(Year.now().getValue()));
        
        // Replace simple variables {{variable}}
        // Using String.replace() is safe - it doesn't use regex
        for (Map.Entry<String, String> entry : allVars.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        
        // Process conditional blocks {{#if variable}}...{{/if}}
        result = processConditionals(result, allVars);
        
        return result;
    }
    
    /**
     * Process conditional blocks
     */
    private String processConditionals(String template, Map<String, String> variables) {
        String result = template;
        
        // Simple regex for {{#if variable}}...{{/if}}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\{\\{#if\\s+(\\w+)\\}\\}(.*?)\\{\\{/if\\}\\}",
            java.util.regex.Pattern.DOTALL
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            String content = matcher.group(2);
            
            // Check if variable exists and is not empty
            String varValue = variables.get(varName);
            boolean shouldInclude = varValue != null && !varValue.isEmpty() && !varValue.equals("false");
            
            // Use quoteReplacement to avoid issues with special characters like $ in content
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(shouldInclude ? content : ""));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * Get fallback template if main template fails
     */
    private String getFallbackTemplate(String templateName, Map<String, String> variables) {
        return "<html><body><h1>Email Notification</h1><p>Template: " + templateName + "</p></body></html>";
    }
    
    /**
     * Clear template cache
     */
    public void clearCache() {
        if (templateCache != null) {
            templateCache.clear();
        }
    }
    
    /**
     * Preload commonly used templates
     */
    public void preloadTemplates(String... templateNames) {
        for (String name : templateNames) {
            try {
                loadTemplate(name);
            } catch (Exception e) {
                logger.warn("Failed to preload template: " + name);
            }
        }
    }
}
