package ke.skyworld.januscope.core.email;

import ke.skyworld.januscope.utils.Logger;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Universal Email Sender Service
 * Sends HTML emails with template support
 * 
 * Features:
 * - HTML email support
 * - Async sending with CompletableFuture
 * - Template integration
 * - Configurable SMTP settings
 * - Thread-safe
 * 
 * Usage:
 * EmailSender sender = EmailSender.builder()
 *     .host("smtp.gmail.com")
 *     .port(587)
 *     .username("your@email.com")
 *     .password("password")
 *     .build();
 * 
 * sender.sendHtml("to@email.com", "Subject", htmlContent);
 */
public class EmailSender {
    private static final Logger logger = Logger.getLogger(EmailSender.class);
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromEmail;
    private final String fromName;
    private final boolean useTLS;
    private final boolean useSSL;
    private final ExecutorService executorService;
    
    private EmailSender(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
        this.fromEmail = builder.fromEmail != null ? builder.fromEmail : builder.username;
        this.fromName = builder.fromName != null ? builder.fromName : "Januscope";
        this.useTLS = builder.useTLS;
        this.useSSL = builder.useSSL;
        this.executorService = Executors.newFixedThreadPool(builder.threadPoolSize);
    }
    
    /**
     * Send HTML email synchronously
     */
    public boolean sendHtml(String to, String subject, String htmlContent) {
        return sendHtml(to, subject, htmlContent, null);
    }
    
    /**
     * Send HTML email with optional plain text fallback
     */
    public boolean sendHtml(String to, String subject, String htmlContent, String plainText) {
        try {
            Session session = createSession();
            Message message = createMessage(session, to, subject, htmlContent, plainText);
            Transport.send(message);
            
            logger.info("Email sent successfully to: " + to);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to send email to: " + to, e);
            return false;
        }
    }
    
    /**
     * Send HTML email asynchronously
     */
    public CompletableFuture<Boolean> sendHtmlAsync(String to, String subject, String htmlContent) {
        return CompletableFuture.supplyAsync(() -> sendHtml(to, subject, htmlContent), executorService);
    }
    
    /**
     * Send email using template
     */
    public boolean sendTemplate(String to, String subject, String templateName, 
                                java.util.Map<String, String> variables) {
        try {
            EmailTemplateService templateService = new EmailTemplateService();
            String htmlContent = templateService.renderTemplate(templateName, variables);
            return sendHtml(to, subject, htmlContent);
        } catch (Exception e) {
            logger.error("Failed to send template email: " + templateName, e);
            return false;
        }
    }
    
    /**
     * Send template email asynchronously
     */
    public CompletableFuture<Boolean> sendTemplateAsync(String to, String subject, 
                                                        String templateName, 
                                                        java.util.Map<String, String> variables) {
        return CompletableFuture.supplyAsync(
            () -> sendTemplate(to, subject, templateName, variables), 
            executorService
        );
    }
    
    /**
     * Send to multiple recipients
     */
    public boolean sendToMultiple(String[] recipients, String subject, String htmlContent) {
        boolean allSuccess = true;
        for (String recipient : recipients) {
            if (!sendHtml(recipient, subject, htmlContent)) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }
    
    /**
     * Create email session
     */
    private Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        
        if (useTLS) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        if (useSSL) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
    
    /**
     * Create email message
     */
    private Message createMessage(Session session, String to, String subject, 
                                  String htmlContent, String plainText) throws Exception {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail, fromName));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        
        if (plainText != null) {
            // Multipart message with HTML and plain text
            Multipart multipart = new MimeMultipart("alternative");
            
            // Plain text part
            BodyPart textPart = new MimeBodyPart();
            textPart.setText(plainText);
            multipart.addBodyPart(textPart);
            
            // HTML part
            BodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlContent, "text/html; charset=utf-8");
            multipart.addBodyPart(htmlPart);
            
            message.setContent(multipart);
        } else {
            // HTML only
            message.setContent(htmlContent, "text/html; charset=utf-8");
        }
        
        return message;
    }
    
    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
    }
    
    /**
     * Builder for EmailSender
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private String fromEmail;
        private String fromName;
        private boolean useTLS = true;
        private boolean useSSL = false;
        private int threadPoolSize = 5;
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder fromEmail(String fromEmail) {
            this.fromEmail = fromEmail;
            return this;
        }
        
        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }
        
        public Builder useTLS(boolean useTLS) {
            this.useTLS = useTLS;
            return this;
        }
        
        public Builder useSSL(boolean useSSL) {
            this.useSSL = useSSL;
            return this;
        }
        
        public Builder threadPoolSize(int size) {
            this.threadPoolSize = size;
            return this;
        }
        
        /**
         * Load configuration from environment variables
         */
        public Builder fromEnvironment() {
            this.host = System.getenv("SMTP_HOST");
            String portStr = System.getenv("SMTP_PORT");
            if (portStr != null) {
                this.port = Integer.parseInt(portStr);
            }
            this.username = System.getenv("SMTP_USERNAME");
            this.password = System.getenv("SMTP_PASSWORD");
            this.fromEmail = System.getenv("SMTP_FROM_EMAIL");
            this.fromName = System.getenv("SMTP_FROM_NAME");
            
            String tlsStr = System.getenv("SMTP_USE_TLS");
            if (tlsStr != null) {
                this.useTLS = Boolean.parseBoolean(tlsStr);
            }
            
            return this;
        }
        
        /**
         * Load configuration from XML config
         * This method should be implemented to read from application.xml
         * For now, it tries environment variables
         */
        public Builder fromXmlConfig() {
            // TODO: Integrate with existing XML config parser
            // For now, fall back to environment variables
            return fromEnvironment();
        }
        
        public EmailSender build() {
            if (host == null || username == null || password == null) {
                throw new IllegalStateException("Host, username, and password are required");
            }
            return new EmailSender(this);
        }
    }
}
