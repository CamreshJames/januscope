package ke.skyworld.januscope.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ke.skyworld.januscope.config.ConfigNode;
import ke.skyworld.januscope.core.engine.BaseEngine;
import ke.skyworld.januscope.core.engine.EngineException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database Engine - Manages database connections using HikariCP.
 */
public class DatabaseEngine extends BaseEngine {
    private HikariDataSource dataSource;
    private String jdbcUrl;
    private String username;
    
    @Override
    protected void doInitialize() throws Exception {
        // Get database config from context
        ConfigNode dbConfig = (ConfigNode) context.getAttribute("database-config");
        
        if (dbConfig == null) {
            throw new EngineException("Database configuration not found in context");
        }
        
        // Load database configuration
        jdbcUrl = dbConfig.getChildValue("url");
        username = dbConfig.getChildValue("username");
        String password = dbConfig.getChildValue("password");
        
        if (jdbcUrl == null || username == null || password == null) {
            throw new EngineException("Database configuration incomplete (url, username, or password missing)");
        }
        
        logger.info("Configuring database connection to: {}", jdbcUrl);
        
        // Configure HikariCP
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        
        // Load pool configuration
        ConfigNode poolConfig = dbConfig.getChild("pool");
        if (poolConfig != null) {
            hikariConfig.setMaximumPoolSize(poolConfig.getChildValueAsInt("maxPoolSize", 20));
            hikariConfig.setMinimumIdle(poolConfig.getChildValueAsInt("minIdle", 5));
            hikariConfig.setConnectionTimeout(poolConfig.getChildValueAsInt("connectionTimeout", 30000));
            hikariConfig.setIdleTimeout(poolConfig.getChildValueAsInt("idleTimeout", 600000));
            hikariConfig.setMaxLifetime(poolConfig.getChildValueAsInt("maxLifetime", 1800000));
            
            logger.info("Pool configuration - Max: {}, Min: {}", 
                       hikariConfig.getMaximumPoolSize(), 
                       hikariConfig.getMinimumIdle());
        }
        
        // Additional HikariCP settings
        hikariConfig.setPoolName("JanuscopePool");
        hikariConfig.setAutoCommit(true);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        
        // Initialize connection pool
        dataSource = new HikariDataSource(hikariConfig);
        
        logger.info("Database connection pool initialized successfully");
    }
    
    @Override
    protected void doStart() throws Exception {
        // Test connection
        try (Connection conn = getConnection()) {
            if (conn.isValid(5)) {
                logger.info("Database connection test successful");
            } else {
                throw new EngineException("Database connection test failed");
            }
        }
    }
    
    @Override
    protected void doStop() throws Exception {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing database connection pool...");
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
    
    @Override
    public String getName() {
        return "Database";
    }
    
    @Override
    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            logger.error("Health check failed", e);
            return false;
        }
    }
    
    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database engine not initialized");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Get pool statistics
     */
    public String getPoolStats() {
        if (dataSource == null) {
            return "Pool not initialized";
        }
        
        return String.format("Pool Stats - Active: %d, Idle: %d, Total: %d, Waiting: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
}
