package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.util.*;

/**
 * Repository for Contact Members
 */
public class ContactMemberRepository {
    private static final Logger logger = Logger.getLogger(ContactMemberRepository.class);
    private final DatabaseEngine dbEngine;
    
    public ContactMemberRepository(DatabaseEngine dbEngine) {
        this.dbEngine = dbEngine;
    }
    
    /**
     * Find all members in a group
     */
    public List<Map<String, Object>> findByGroupId(int groupId) {
        List<Map<String, Object>> members = new ArrayList<>();
        String sql = "SELECT * FROM contact_members WHERE group_id = ? AND is_active = TRUE ORDER BY name";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(mapResultSet(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find members for group: " + groupId, e);
        }
        
        return members;
    }
    
    /**
     * Create new member
     */
    public int create(Map<String, Object> data) {
        String sql = "INSERT INTO contact_members (group_id, name, email, telegram_handle, phone_number, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?) RETURNING member_id";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, (Integer) data.get("groupId"));
            stmt.setString(2, (String) data.get("name"));
            stmt.setString(3, (String) data.get("email"));
            stmt.setString(4, (String) data.get("telegramHandle"));
            stmt.setString(5, (String) data.get("phoneNumber"));
            stmt.setBoolean(6, (Boolean) data.getOrDefault("isActive", true));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to create contact member", e);
        }
        
        return -1;
    }
    
    /**
     * Update member
     */
    public boolean update(int memberId, Map<String, Object> data) {
        StringBuilder sql = new StringBuilder("UPDATE contact_members SET ");
        List<Object> params = new ArrayList<>();
        
        if (data.containsKey("name")) {
            sql.append("name = ?, ");
            params.add(data.get("name"));
        }
        if (data.containsKey("email")) {
            sql.append("email = ?, ");
            params.add(data.get("email"));
        }
        if (data.containsKey("telegramHandle")) {
            sql.append("telegram_handle = ?, ");
            params.add(data.get("telegramHandle"));
        }
        if (data.containsKey("phoneNumber")) {
            sql.append("phone_number = ?, ");
            params.add(data.get("phoneNumber"));
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
        sql.append(" WHERE member_id = ?");
        params.add(memberId);
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            return stmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update contact member: " + memberId, e);
        }
        
        return false;
    }
    
    /**
     * Delete member
     */
    public boolean delete(int memberId) {
        String sql = "UPDATE contact_members SET is_active = FALSE WHERE member_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, memberId);
            return stmt.executeUpdate() > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to delete contact member: " + memberId, e);
        }
        
        return false;
    }
    
    /**
     * Map ResultSet to Map
     */
    private Map<String, Object> mapResultSet(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("memberId", rs.getInt("member_id"));
        map.put("groupId", rs.getInt("group_id"));
        map.put("name", rs.getString("name"));
        map.put("email", rs.getString("email"));
        map.put("telegramHandle", rs.getString("telegram_handle"));
        map.put("phoneNumber", rs.getString("phone_number"));
        map.put("isActive", rs.getBoolean("is_active"));
        map.put("createdAt", rs.getTimestamp("created_at"));
        map.put("updatedAt", rs.getTimestamp("updated_at"));
        return map;
    }
}
