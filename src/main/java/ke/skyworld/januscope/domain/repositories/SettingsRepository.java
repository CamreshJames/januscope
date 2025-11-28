package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.util.*;

/**
 * Repository for System Settings
 */
public class SettingsRepository {
    private static final Logger logger = Logger.getLogger(SettingsRepository.class);
    private final DatabaseEngine dbEngine;
    
    public SettingsRepository(DatabaseEngine dbEngine) {
        this.dbEngine = dbEngine;
    }
    
    /**
     * Find all settings
     */
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> settings = new ArrayList<>();
        String sql = "SELECT * FROM settings ORDER BY key";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                settings.add(mapResultSet(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find all settings", e);
        }
        
        return settings;
    }
    
    /**
     * Find setting by key
     */
    public Map<String, Object> findByKey(String key) {
        String sql = "SELECT * FROM settings WHERE key = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, key);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find setting by key: " + key, e);
        }
        
        return null;
    }
    
    /**
     * Update setting value
     */
    public boolean update(String key, String value) {
        String sql = "UPDATE settings SET value = ?, updated_at = CURRENT_TIMESTAMP WHERE key = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, value);
            stmt.setString(2, key);
            
            return stmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update setting: " + key, e);
        }
        
        return false;
    }
    
    /**
     * Map ResultSet to Map
     */
    private Map<String, Object> mapResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("key", rs.getString("key"));
        map.put("value", rs.getString("value"));
        map.put("description", rs.getString("description"));
        map.put("dataType", rs.getString("data_type"));
        map.put("isSensitive", rs.getBoolean("is_sensitive"));
        map.put("createdAt", rs.getTimestamp("created_at"));
        map.put("updatedAt", rs.getTimestamp("updated_at"));
        return map;
    }
}
