package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.util.*;

/**
 * Repository for Contact Groups
 */
public class ContactGroupRepository {
    private static final Logger logger = Logger.getLogger(ContactGroupRepository.class);
    private final DatabaseEngine dbEngine;
    
    public ContactGroupRepository(DatabaseEngine dbEngine) {
        this.dbEngine = dbEngine;
    }
    
    /**
     * Find all contact groups
     */
    public List<Map<String, Object>> findAll() {
        var groups = new ArrayList<Map<String, Object>>();
        var sql = "SELECT * FROM contact_groups WHERE is_active = TRUE ORDER BY name";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                groups.add(mapResultSet(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find all contact groups", e);
        }
        
        return groups;
    }
    
    /**
     * Find contact group by ID
     */
    public Map<String, Object> findById(int groupId) {
        var sql = "SELECT * FROM contact_groups WHERE group_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find contact group by ID: " + groupId, e);
        }
        
        return null;
    }
    
    /**
     * Create new contact group
     */
    public int create(Map<String, Object> data) {
        var sql = """
            INSERT INTO contact_groups (name, description, is_active)
            VALUES (?, ?, ?)
            RETURNING group_id
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, (String) data.get("name"));
            stmt.setString(2, (String) data.getOrDefault("description", ""));
            stmt.setBoolean(3, (Boolean) data.getOrDefault("isActive", true));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to create contact group", e);
        }
        
        return -1;
    }
    
    /**
     * Update contact group
     */
    public boolean update(int groupId, Map<String, Object> data) {
        var sql = new StringBuilder("UPDATE contact_groups SET ");
        var params = new ArrayList<>();
        
        if (data.containsKey("name")) {
            sql.append("name = ?, ");
            params.add(data.get("name"));
        }
        if (data.containsKey("description")) {
            sql.append("description = ?, ");
            params.add(data.get("description"));
        }
        if (data.containsKey("isActive")) {
            sql.append("is_active = ?, ");
            params.add(data.get("isActive"));
        }
        
        if (params.isEmpty()) {
            return false;
        }
        
        // Remove trailing comma and space
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE group_id = ?");
        params.add(groupId);
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            return stmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update contact group: " + groupId, e);
        }
        
        return false;
    }
    
    /**
     * Delete contact group
     */
    public boolean delete(int groupId) {
        var sql = "UPDATE contact_groups SET is_active = FALSE WHERE group_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, groupId);
            return stmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to delete contact group: " + groupId, e);
        }
        
        return false;
    }
    
    /**
     * Map ResultSet to Map
     */
    private Map<String, Object> mapResultSet(ResultSet rs) throws SQLException {
        var map = new HashMap<String, Object>();
        map.put("groupId", rs.getInt("group_id"));
        map.put("name", rs.getString("name"));
        map.put("description", rs.getString("description"));
        map.put("isActive", rs.getBoolean("is_active"));
        map.put("createdAt", rs.getTimestamp("created_at"));
        map.put("updatedAt", rs.getTimestamp("updated_at"));
        return map;
    }
}
