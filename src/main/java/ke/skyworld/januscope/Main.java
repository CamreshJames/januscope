package ke.skyworld.januscope;

import ke.skyworld.januscope.core.engine.EngineContext;
import ke.skyworld.januscope.utils.Logger;

/**
 * Januscope - Uptime & SSL Monitoring System
 * Main application entry point
 */
public class Main {
    private static final Logger logger = Logger.getLogger(Main.class);
    private static final String VERSION = "1.0.0";
    private static final int PORT = 9876;
    
    public static void main(String[] args) {
        printBanner();
        
        logger.info("Starting Januscope v{}", VERSION);
        logger.info("Port: {}", PORT);
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        
        try {
            // Create engine context
            EngineContext context = new EngineContext();
            context.setAttribute("version", VERSION);
            context.setAttribute("port", PORT);
            
            logger.info("Engine context created");
            logger.info("========================================");
            
            // 1. Configuration Engine
            logger.info("Initializing Configuration Engine...");
            ke.skyworld.januscope.config.ConfigurationEngine configEngine = 
                new ke.skyworld.januscope.config.ConfigurationEngine("config");
            configEngine.initialize(context);
            configEngine.start();
            context.registerEngine("configuration", configEngine);
            
            // Load configurations into context
            ke.skyworld.januscope.config.ConfigNode appConfig = configEngine.getApplicationConfig();
            if (appConfig == null) {
                throw new Exception("application.xml not found");
            }
            
            context.setAttribute("database-config", appConfig.getChild("database"));
            context.setAttribute("security-config", appConfig.getChild("security"));
            context.setAttribute("monitoring-config", appConfig.getChild("monitoring"));
            context.setAttribute("notification-config", appConfig.getChild("notification"));
            context.setAttribute("server-config", appConfig.getChild("server"));
            
            logger.info("========================================");
            
            // 2. Security Engine
            logger.info("Initializing Security Engine...");
            ke.skyworld.januscope.core.security.SecurityEngine securityEngine = 
                new ke.skyworld.januscope.core.security.SecurityEngine();
            securityEngine.initialize(context);
            securityEngine.start();
            context.registerEngine("security", securityEngine);
            
            logger.info("========================================");
            
            // 3. Database Engine
            logger.info("Initializing Database Engine...");
            ke.skyworld.januscope.core.database.DatabaseEngine databaseEngine = null;
            try {
                databaseEngine = new ke.skyworld.januscope.core.database.DatabaseEngine();
                databaseEngine.initialize(context);
                databaseEngine.start();
                context.registerEngine("database", databaseEngine);
                logger.info("Database engine initialized successfully");
            } catch (Exception e) {
                logger.warn("Database engine initialization failed: {}", e.getMessage());
                logger.warn("Continuing without database (configure database in config/application.xml)");
            }
            
            // 4. Monitoring Engine
            logger.info("Initializing Monitoring Engine...");
            ke.skyworld.januscope.core.monitoring.MonitoringEngine monitoringEngine = 
                new ke.skyworld.januscope.core.monitoring.MonitoringEngine();
            monitoringEngine.initialize(context);
            monitoringEngine.start();
            context.registerEngine("monitoring", monitoringEngine);
            
            logger.info("========================================");
            
            // 5. Notification Engine
            logger.info("Initializing Notification Engine...");
            ke.skyworld.januscope.core.notification.NotificationEngine notificationEngine = 
                new ke.skyworld.januscope.core.notification.NotificationEngine();
            notificationEngine.initialize(context);
            notificationEngine.start();
            context.registerEngine("notification", notificationEngine);
            
            logger.info("========================================");
            
            // 6. Job Engine
            logger.info("Initializing Job Engine...");
            ke.skyworld.januscope.core.jobs.JobEngine jobEngine = 
                new ke.skyworld.januscope.core.jobs.JobEngine();
            jobEngine.initialize(context);
            jobEngine.start();
            context.registerEngine("job", jobEngine);
            
            logger.info("========================================");
            
            // 7. Undertow REST API Server
            logger.info("Initializing Undertow Server...");
            ke.skyworld.januscope.api.server.UndertowServer undertowServer = 
                new ke.skyworld.januscope.api.server.UndertowServer();
            undertowServer.initialize(context);
            undertowServer.start();
            context.registerEngine("undertow", undertowServer);
            
            logger.info("========================================");
            logger.info("All engines initialized successfully!");
            logger.info("========================================");
            logger.info("Januscope is ready!");
            logger.info("REST API: http://localhost:{}", PORT);
            logger.info("Health Check: http://localhost:{}/api/health", PORT);
            logger.info("========================================");
            
            // Setup jobs if database is available
            if (databaseEngine != null) {
                setupJobs(jobEngine, monitoringEngine, notificationEngine, databaseEngine);
            } else {
                logger.warn("Database not configured - monitoring jobs not scheduled");
                logger.warn("Configure database and add services to enable monitoring");
            }
            
            // Keep application running
            addShutdownHook(context);
            
            logger.info("========================================");
            logger.info("Januscope is running. Press Ctrl+C to stop.");
            logger.info("========================================");
            
            Thread.currentThread().join();
            
        } catch (Exception e) {
            logger.error("Fatal error during startup", e);
            System.exit(1);
        }
    }
    
    private static void printBanner() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                                           ║");
        System.out.println("║     ██╗ █████╗ ███╗   ██╗██╗   ██╗███████╗ ██████╗ ██████╗ ██████╗ ███████╗");
        System.out.println("║     ██║██╔══██╗████╗  ██║██║   ██║██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝");
        System.out.println("║     ██║███████║██╔██╗ ██║██║   ██║███████╗██║     ██║   ██║██████╔╝█████╗  ");
        System.out.println("║██   ██║██╔══██║██║╚██╗██║██║   ██║╚════██║██║     ██║   ██║██╔═══╝ ██╔══╝  ");
        System.out.println("║╚█████╔╝██║  ██║██║ ╚████║╚██████╔╝███████║╚██████╗╚██████╔╝██║     ███████╗");
        System.out.println("║ ╚════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝ ╚═════╝ ╚═════╝ ╚═╝     ╚══════╝");
        System.out.println("║                                                                           ║");
        System.out.println("║              Uptime & SSL Monitoring System v" + VERSION + "                        ║");
        System.out.println("║                    Sky World Limited                                      ║");
        System.out.println("║                                                                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
    
    /**
     * Setup monitoring and SSL check jobs
     */
    private static void setupJobs(ke.skyworld.januscope.core.jobs.JobEngine jobEngine,
                                  ke.skyworld.januscope.core.monitoring.MonitoringEngine monitoringEngine,
                                  ke.skyworld.januscope.core.notification.NotificationEngine notificationEngine,
                                  ke.skyworld.januscope.core.database.DatabaseEngine databaseEngine) {
        
        logger.info("Setting up scheduled jobs...");
        
        // Create repositories
        ke.skyworld.januscope.domain.repositories.ServiceRepository serviceRepo = 
            new ke.skyworld.januscope.domain.repositories.ServiceRepository(databaseEngine);
        ke.skyworld.januscope.domain.repositories.UptimeCheckRepository uptimeCheckRepo = 
            new ke.skyworld.januscope.domain.repositories.UptimeCheckRepository(databaseEngine);
        ke.skyworld.januscope.domain.repositories.IncidentRepository incidentRepo = 
            new ke.skyworld.januscope.domain.repositories.IncidentRepository(databaseEngine);
        ke.skyworld.januscope.domain.repositories.SSLCheckRepository sslCheckRepo = 
            new ke.skyworld.januscope.domain.repositories.SSLCheckRepository(databaseEngine);
        
        // Load services from database
        java.util.List<ke.skyworld.januscope.domain.models.Service> services = serviceRepo.findAllActive();
        
        if (services.isEmpty()) {
            logger.warn("No services configured in database");
            logger.warn("Add services via REST API: POST /api/services");
            return;
        }
        
        logger.info("Loaded {} active services from database", services.size());
        
        // Schedule monitoring job - every 5 minutes
        ke.skyworld.januscope.core.jobs.jobs.MonitoringJob monitoringJob = 
            new ke.skyworld.januscope.core.jobs.jobs.MonitoringJob(
                monitoringEngine, notificationEngine, services, true,
                uptimeCheckRepo, serviceRepo, incidentRepo);
        jobEngine.scheduleJob(monitoringJob, "*/5 * * * *");
        logger.info("Scheduled MonitoringJob (every 5 minutes)");
        
        // Schedule SSL check job - every 6 hours
        ke.skyworld.januscope.core.jobs.jobs.SSLCheckJob sslCheckJob = 
            new ke.skyworld.januscope.core.jobs.jobs.SSLCheckJob(
                monitoringEngine, notificationEngine, services, true, 30,
                sslCheckRepo);
        jobEngine.scheduleJob(sslCheckJob, "0 */6 * * *");
        logger.info("Scheduled SSLCheckJob (every 6 hours)");
        
        logger.info("All jobs scheduled successfully");
    }
    
    private static void addShutdownHook(EngineContext context) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("========================================");
            logger.info("Shutdown signal received, stopping engines...");
            
            try {
                // Stop engines in reverse order
                ke.skyworld.januscope.api.server.UndertowServer undertowServer = 
                    context.getEngine("undertow", ke.skyworld.januscope.api.server.UndertowServer.class);
                if (undertowServer != null) {
                    undertowServer.stop();
                }
                
                ke.skyworld.januscope.core.jobs.JobEngine jobEngine = 
                    context.getEngine("job", ke.skyworld.januscope.core.jobs.JobEngine.class);
                if (jobEngine != null) {
                    jobEngine.stop();
                }
                
                ke.skyworld.januscope.core.notification.NotificationEngine notifEngine = 
                    context.getEngine("notification", ke.skyworld.januscope.core.notification.NotificationEngine.class);
                if (notifEngine != null) {
                    notifEngine.stop();
                }
                
                ke.skyworld.januscope.core.monitoring.MonitoringEngine monEngine = 
                    context.getEngine("monitoring", ke.skyworld.januscope.core.monitoring.MonitoringEngine.class);
                if (monEngine != null) {
                    monEngine.stop();
                }
                
                ke.skyworld.januscope.core.database.DatabaseEngine dbEngine = 
                    context.getEngine("database", ke.skyworld.januscope.core.database.DatabaseEngine.class);
                if (dbEngine != null) {
                    dbEngine.stop();
                }
                
                ke.skyworld.januscope.core.security.SecurityEngine secEngine = 
                    context.getEngine("security", ke.skyworld.januscope.core.security.SecurityEngine.class);
                if (secEngine != null) {
                    secEngine.stop();
                }
                
                ke.skyworld.januscope.config.ConfigurationEngine configEngine = 
                    context.getEngine("configuration", ke.skyworld.januscope.config.ConfigurationEngine.class);
                if (configEngine != null) {
                    configEngine.stop();
                }
                
                logger.info("All engines stopped successfully");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
            
            logger.info("Januscope stopped gracefully");
            logger.info("========================================");
        }));
    }
}
