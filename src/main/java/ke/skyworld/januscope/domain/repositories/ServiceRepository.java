package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.domain.models.Service;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for Service entity
 */
public class ServiceRepository {
    private static final Logger logger = Logger.getLogger(ServiceRepository.class);
    
    private final DatabaseEngine databaseEngine;
    
    public ServiceRepository(DatabaseEngine databaseEngine) {
        this.databaseEngine = databaseEngine;
    }
    
    /**
     * Find all active services
     */
    public List<Service> findAllActive() {
        var services = new ArrayList<Service>();
        
        var sql = """
            SELECT service_id, name, url, check_interval_seconds, timeout_ms,
                   max_retries, retry_delay_ms, current_status, last_checked_at,
                   is_active, created_at, updated_at
            FROM services 
            WHERE is_active = true AND is_deleted = false
            ORDER BY name
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Service service = mapResultSetToService(rs);
                services.add(service);
            }
            
            logger.debug("Found {} active services", services.size());
            
        } catch (SQLException e) {
            logger.error("Failed to find active services", e);
        }
        
        return services;
    }
    
    /**
     * Find all services (including inactive)
     */
    public List<Service> findAll() {
        var services = new ArrayList<Service>();
        var sql = "SELECT * FROM services WHERE is_deleted = FALSE ORDER BY name";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                services.add(mapResultSetToService(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to fetch all services", e);
        }
        
        return services;
    }
    
    /**
     * Find service by ID
     */
    public Service findById(int serviceId) {
        var sql = """
            SELECT service_id, name, url, check_interval_seconds, timeout_ms,
                   max_retries, retry_delay_ms, current_status, last_checked_at,
                   is_active, created_at, updated_at
            FROM services 
            WHERE service_id = ? AND is_deleted = false
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToService(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find service by ID: {}", serviceId, e);
        }
        
        return null;
    }
    
    /**
     * Create new service
     */
    public Service create(Service service) {
        var sql = """
            INSERT INTO services (name, url, check_interval_seconds, timeout_ms,
                                 max_retries, retry_delay_ms, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING service_id, created_at
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, service.getName());
            stmt.setString(2, service.getUrl());
            stmt.setInt(3, service.getCheckIntervalSeconds());
            stmt.setInt(4, service.getTimeoutMs());
            stmt.setInt(5, service.getMaxRetries());
            stmt.setInt(6, service.getRetryDelayMs());
            stmt.setBoolean(7, service.isActive());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    service.setServiceId(rs.getInt("service_id"));
                    service.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                    logger.info("Created service: {} (ID: {})", service.getName(), service.getServiceId());
                    return service;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to create service", e);
        }
        
        return null;
    }
    
    /**
     * Update service
     */
    public boolean update(Service service) {
        var sql = """
            UPDATE services 
            SET name = ?, url = ?, check_interval_seconds = ?, timeout_ms = ?,
                max_retries = ?, retry_delay_ms = ?, is_active = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE service_id = ?
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, service.getName());
            stmt.setString(2, service.getUrl());
            stmt.setInt(3, service.getCheckIntervalSeconds());
            stmt.setInt(4, service.getTimeoutMs());
            stmt.setInt(5, service.getMaxRetries());
            stmt.setInt(6, service.getRetryDelayMs());
            stmt.setBoolean(7, service.isActive());
            stmt.setInt(8, service.getServiceId());
            
            int updated = stmt.executeUpdate();
            return updated > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update service", e);
        }
        
        return false;
    }
    
    /**
     * Soft delete service
     */
    public boolean delete(int serviceId) {
        var sql = """
            UPDATE services 
            SET is_deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, is_active = FALSE
            WHERE service_id = ?
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            int updated = stmt.executeUpdate();
            return updated > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to delete service", e);
        }
        
        return false;
    }
    
    /**
     * Update service status
     */
    public void updateStatus(int serviceId, String status) {
        var sql = """
            UPDATE services 
            SET current_status = ?, last_checked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE service_id = ?
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setInt(2, serviceId);
            
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                logger.debug("Updated service {} status to {}", serviceId, status);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to update service status", e);
        }
    }
    
    /**
     * Count all active services
     */
    public int countAll() {
        var sql = "SELECT COUNT(*) FROM services WHERE is_active = TRUE AND is_deleted = FALSE";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count services", e);
        }
        
        return 0;
    }
    
    /**
     * Count services by status
     */
    public int countByStatus(String status) {
        var sql = """
            SELECT COUNT(*) FROM services
            WHERE current_status = ? AND is_active = TRUE AND is_deleted = FALSE
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count services by status", e);
        }
        
        return 0;
    }
    
    /**
     * Count total services
     */
    public int countActive() {
        var sql = "SELECT COUNT(*) FROM services WHERE is_active = true AND is_deleted = false";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count services", e);
        }
        
        return 0;
    }
    
    private Service mapResultSetToService(ResultSet rs) throws SQLException {
        Service service = new Service();
        service.setServiceId(rs.getInt("service_id"));
        service.setName(rs.getString("name"));
        service.setUrl(rs.getString("url"));
        service.setCheckIntervalSeconds(rs.getInt("check_interval_seconds"));
        service.setTimeoutMs(rs.getInt("timeout_ms"));
        service.setMaxRetries(rs.getInt("max_retries"));
        service.setRetryDelayMs(rs.getInt("retry_delay_ms"));
        service.setCurrentStatus(rs.getString("current_status"));
        
        Timestamp lastChecked = rs.getTimestamp("last_checked_at");
        if (lastChecked != null) {
            service.setLastCheckedAt(lastChecked.toInstant());
        }
        
        service.setActive(rs.getBoolean("is_active"));
        
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            service.setCreatedAt(created.toInstant());
        }
        
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            service.setUpdatedAt(updated.toInstant());
        }
        
        return service;
    }
}
