package ke.skyworld.januscope.core.notification.channels;

import ke.skyworld.januscope.core.notification.NotificationChannel;
import ke.skyworld.januscope.domain.models.NotificationRequest;
import ke.skyworld.januscope.domain.models.NotificationResult;
import ke.skyworld.januscope.utils.Logger;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

/**
 * Email notification channel using SMTP
 */
public class EmailChannel implements NotificationChannel {
    private static final Logger logger = Logger.getLogger(EmailChannel.class);
    
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean useTls;
    private final String fromAddress;
    private Session session;
    
    public EmailChannel(boolean enabled, String host, int port, String username, 
                       String password, boolean useTls, String fromAddress) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useTls = useTls;
        this.fromAddress = fromAddress;
        
        if (enabled) {
            initializeSession();
        }
    }
    
    private void initializeSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        
        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        
        session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        
        logger.info("Email channel initialized - Host: {}, Port: {}, TLS: {}", host, port, useTls);
    }
    
    @Override
    public String getChannelName() {
        return "Email";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public NotificationResult send(NotificationRequest request) {
        if (!enabled) {
            logger.warn("Email channel is disabled");
            return NotificationResult.failure("Email", "Channel is disabled");
        }
        
        try {
            logger.debug("Sending email to: {}", request.getRecipient());
            
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO, 
                                 InternetAddress.parse(request.getRecipient()));
            message.setSubject(request.getSubject());
            
            // Send as HTML if message contains HTML tags, otherwise plain text
            if (request.getMessage().trim().startsWith("<") || request.getMessage().contains("<html")) {
                message.setContent(request.getMessage(), "text/html; charset=utf-8");
            } else {
                message.setText(request.getMessage());
            }
            
            Transport.send(message);
            
            logger.info("✓ Email sent successfully to: {}", request.getRecipient());
            return NotificationResult.success("Email");
            
        } catch (MessagingException e) {
            logger.error("✗ Failed to send email to: {} - {}", 
                        request.getRecipient(), e.getMessage());
            return NotificationResult.failure("Email", e.getMessage());
        }
    }
    
    @Override
    public boolean testConnection() {
        if (!enabled) {
            return false;
        }
        
        try {
            // Try to connect to SMTP server
            Transport transport = session.getTransport("smtp");
            transport.connect(host, port, username, password);
            transport.close();
            logger.info("Email channel connection test: SUCCESS");
            return true;
        } catch (Exception e) {
            logger.error("Email channel connection test: FAILED - {}", e.getMessage());
            return false;
        }
    }
}
