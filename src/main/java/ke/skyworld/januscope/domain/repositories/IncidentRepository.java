package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.time.Instant;

/**
 * Repository for Incident entity
 */
public class IncidentRepository {
    private static final Logger logger = Logger.getLogger(IncidentRepository.class);
    
    private final DatabaseEngine databaseEngine;
    
    public IncidentRepository(DatabaseEngine databaseEngine) {
        this.databaseEngine = databaseEngine;
    }
    
    /**
     * Create new incident when service goes down
     */
    public int createIncident(int serviceId, String errorMessage) {
        String sql = "INSERT INTO incidents (service_id, started_at, error_message, is_resolved) " +
                    "VALUES (?, CURRENT_TIMESTAMP, ?, false) RETURNING incident_id";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            stmt.setString(2, errorMessage);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int incidentId = rs.getInt("incident_id");
                    logger.info("Created incident {} for service {}", incidentId, serviceId);
                    return incidentId;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to create incident", e);
        }
        
        return -1;
    }
    
    /**
     * Resolve incident when service recovers
     */
    public void resolveIncident(int incidentId) {
        String sql = "UPDATE incidents SET " +
                    "recovered_at = CURRENT_TIMESTAMP, " +
                    "duration_seconds = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - started_at))::INTEGER, " +
                    "is_resolved = true, " +
                    "updated_at = CURRENT_TIMESTAMP " +
                    "WHERE incident_id = ?";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, incidentId);
            
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                logger.info("Resolved incident {}", incidentId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to resolve incident", e);
        }
    }
    
    /**
     * Find active (unresolved) incident for service
     */
    public Integer findActiveIncident(int serviceId) {
        String sql = "SELECT incident_id FROM incidents " +
                    "WHERE service_id = ? AND is_resolved = false " +
                    "ORDER BY started_at DESC LIMIT 1";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("incident_id");
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find active incident", e);
        }
        
        return null;
    }
    
    /**
     * Count incidents in last 24 hours
     */
    public int countLast24h(int serviceId) {
        String sql = "SELECT COUNT(*) FROM incidents " +
                    "WHERE service_id = ? AND started_at >= NOW() - INTERVAL '24 hours'";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count incidents", e);
        }
        
        return 0;
    }
    
    /**
     * Count active (unresolved) incidents
     */
    public int countActive() {
        String sql = "SELECT COUNT(*) FROM incidents WHERE is_resolved = FALSE";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count active incidents", e);
        }
        
        return 0;
    }
}
