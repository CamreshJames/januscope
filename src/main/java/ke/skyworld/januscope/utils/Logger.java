package ke.skyworld.januscope.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple custom logger with full control
 * Logs to both console and file
 */
public class Logger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LOG_FILE = "logs/januscope.log";
    private static boolean fileLoggingEnabled = true;
    
    private final String className;
    
    private Logger(String className) {
        this.className = className;
    }
    
    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz.getSimpleName());
    }
    
    public static Logger getLogger(String name) {
        return new Logger(name);
    }
    
    public void info(String message, Object... args) {
        log("INFO", message, args);
    }
    
    public void debug(String message, Object... args) {
        log("DEBUG", message, args);
    }
    
    public void warn(String message, Object... args) {
        log("WARN", message, args);
    }
    
    public void error(String message, Object... args) {
        log("ERROR", message, args);
    }
    
    public void error(String message, Throwable throwable) {
        log("ERROR", message + " - " + throwable.getMessage());
        if (throwable.getCause() != null) {
            log("ERROR", "Caused by: " + throwable.getCause().getMessage());
        }
    }
    
    private void log(String level, String message, Object... args) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String formattedMessage = formatMessage(message, args);
        String logLine = String.format("%s [%s] %-5s %s - %s", 
                                      timestamp, 
                                      Thread.currentThread().getName(), 
                                      level, 
                                      className, 
                                      formattedMessage);
        
        // Console output
        System.out.println(logLine);
        
        // File output
        if (fileLoggingEnabled) {
            writeToFile(logLine);
        }
    }
    
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        
        // Replace {} placeholders with arguments
        // Use Matcher.quoteReplacement to avoid issues with special characters like $ in args
        String result = message;
        for (Object arg : args) {
            result = result.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        }
        return result;
    }
    
    private void writeToFile(String logLine) {
        try {
            // Create logs directory if it doesn't exist
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // Append to log file
            try (FileWriter fw = new FileWriter(LOG_FILE, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(logLine);
            }
        } catch (IOException e) {
            // If file logging fails, just continue with console logging
            // Don't throw exception to avoid breaking the application
        }
    }
    
    public static void setFileLoggingEnabled(boolean enabled) {
        fileLoggingEnabled = enabled;
    }
}
