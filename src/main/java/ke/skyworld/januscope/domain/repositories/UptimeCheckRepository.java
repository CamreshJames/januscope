package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.domain.models.UptimeCheckResult;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for UptimeCheck entity
 */
public class UptimeCheckRepository {
    private static final Logger logger = Logger.getLogger(UptimeCheckRepository.class);
    
    private final DatabaseEngine databaseEngine;
    
    public UptimeCheckRepository(DatabaseEngine databaseEngine) {
        this.databaseEngine = databaseEngine;
    }
    
    /**
     * Save uptime check result
     */
    public void save(UptimeCheckResult result) {
        String sql = "INSERT INTO uptime_checks (service_id, status, response_time_ms, " +
                    "http_code, error_message, checked_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, result.getServiceId());
            stmt.setString(2, result.getStatus());
            
            if (result.getResponseTimeMs() != null) {
                stmt.setInt(3, result.getResponseTimeMs());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            
            if (result.getHttpCode() != null) {
                stmt.setInt(4, result.getHttpCode());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            
            stmt.setString(5, result.getErrorMessage());
            stmt.setTimestamp(6, Timestamp.from(result.getCheckedAt()));
            
            stmt.executeUpdate();
            
            logger.debug("Saved uptime check for service {}: {}", result.getServiceId(), result.getStatus());
            
        } catch (SQLException e) {
            logger.error("Failed to save uptime check", e);
        }
    }
    
    /**
     * Get uptime percentage for last 24 hours
     */
    public double getUptimePercentage24h(int serviceId) {
        String sql = "SELECT " +
                    "COUNT(*) as total, " +
                    "SUM(CASE WHEN status = 'UP' THEN 1 ELSE 0 END) as up_count " +
                    "FROM uptime_checks " +
                    "WHERE service_id = ? AND checked_at >= NOW() - INTERVAL '24 hours'";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int upCount = rs.getInt("up_count");
                    
                    if (total > 0) {
                        return (upCount * 100.0) / total;
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to calculate uptime percentage", e);
        }
        
        return 0.0;
    }
    
    /**
     * Count checks in last 24 hours
     */
    public int countLast24h(int serviceId) {
        String sql = "SELECT COUNT(*) FROM uptime_checks " +
                    "WHERE service_id = ? AND checked_at >= NOW() - INTERVAL '24 hours'";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to count checks", e);
        }
        
        return 0;
    }
    
    /**
     * Find uptime checks by service ID with limit
     */
    public List<UptimeCheckResult> findByServiceId(int serviceId, int limit) {
        List<UptimeCheckResult> results = new ArrayList<>();
        String sql = "SELECT * FROM uptime_checks " +
                    "WHERE service_id = ? " +
                    "ORDER BY checked_at DESC LIMIT ?";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, serviceId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToUptimeCheck(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find uptime checks by service ID", e);
        }
        
        return results;
    }
    
    /**
     * Calculate uptime percentage for last N hours
     */
    public double calculateUptimePercentage(int hours) {
        String sql = "SELECT " +
                    "COUNT(*) as total, " +
                    "SUM(CASE WHEN status = 'UP' THEN 1 ELSE 0 END) as up_count " +
                    "FROM uptime_checks " +
                    "WHERE checked_at >= NOW() - INTERVAL '" + hours + " hours'";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int total = rs.getInt("total");
                int upCount = rs.getInt("up_count");
                
                if (total > 0) {
                    return (upCount * 100.0) / total;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to calculate uptime percentage", e);
        }
        
        return 0.0;
    }
    
    /**
     * Calculate average response time for last N hours
     */
    public double calculateAverageResponseTime(int hours) {
        String sql = "SELECT AVG(response_time_ms) as avg_response " +
                    "FROM uptime_checks " +
                    "WHERE checked_at >= NOW() - INTERVAL '" + hours + " hours' " +
                    "AND status = 'UP' AND response_time_ms IS NOT NULL";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getDouble("avg_response");
            }
            
        } catch (SQLException e) {
            logger.error("Failed to calculate average response time", e);
        }
        
        return 0.0;
    }
    
    private UptimeCheckResult mapResultSetToUptimeCheck(ResultSet rs) throws SQLException {
        UptimeCheckResult result = new UptimeCheckResult();
        result.setServiceId(rs.getInt("service_id"));
        result.setStatus(rs.getString("status"));
        result.setResponseTimeMs(rs.getInt("response_time_ms"));
        result.setHttpCode(rs.getInt("http_code"));
        result.setErrorMessage(rs.getString("error_message"));
        result.setCheckedAt(rs.getTimestamp("checked_at").toInstant());
        return result;
    }
}
