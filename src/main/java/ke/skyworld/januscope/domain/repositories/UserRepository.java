package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.domain.models.User;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for User entity
 */
public class UserRepository {
    private static final Logger logger = Logger.getLogger(UserRepository.class);
    
    private final DatabaseEngine databaseEngine;
    
    public UserRepository(DatabaseEngine databaseEngine) {
        this.databaseEngine = databaseEngine;
    }
    
    /**
     * Create new user (registration)
     */
    public User create(User user) {
        var sql = """
            INSERT INTO users (role_id, gender, branch_id, first_name, middle_name,
                              last_name, username, email, phone_number, national_id, 
                              date_of_birth, password_hash, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING user_id, created_at
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, user.getRoleId());
            
            if (user.getGender() != null) {
                stmt.setString(2, user.getGender());
            } else {
                stmt.setNull(2, Types.VARCHAR);
            }
            
            if (user.getBranchId() != null) {
                stmt.setInt(3, user.getBranchId());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            
            stmt.setString(4, user.getFirstName());
            
            if (user.getMiddleName() != null) {
                stmt.setString(5, user.getMiddleName());
            } else {
                stmt.setNull(5, Types.VARCHAR);
            }
            
            stmt.setString(6, user.getLastName());
            stmt.setString(7, user.getUsername());
            stmt.setString(8, user.getEmail());
            
            if (user.getPhoneNumber() != null) {
                stmt.setString(9, user.getPhoneNumber());
            } else {
                stmt.setNull(9, Types.VARCHAR);
            }
            
            if (user.getNationalId() != null) {
                stmt.setString(10, user.getNationalId());
            } else {
                stmt.setNull(10, Types.VARCHAR);
            }
            
            if (user.getDateOfBirth() != null && !user.getDateOfBirth().isEmpty()) {
                try {
                    stmt.setDate(11, Date.valueOf(user.getDateOfBirth()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid date format for dateOfBirth: {}", user.getDateOfBirth());
                    stmt.setNull(11, Types.DATE);
                }
            } else {
                stmt.setNull(11, Types.DATE);
            }
            
            stmt.setString(12, user.getPasswordHash());
            stmt.setBoolean(13, user.isActive());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    user.setUserId(rs.getInt("user_id"));
                    user.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                    logger.info("Created user: {} (ID: {})", user.getEmail(), user.getUserId());
                    return user;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to create user: {} - SQL Error: {}", user.getEmail(), e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Create pending user (simplified registration)
     * Only email, firstName, lastName required
     * Username and password will be generated upon approval
     */
    public User createPendingUser(User user) {
        var sql = """
            INSERT INTO users (role_id, first_name, last_name, email, 
                              is_active, is_approved)
            VALUES (?, ?, ?, ?, FALSE, FALSE)
            RETURNING user_id, created_at
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, user.getRoleId());
            stmt.setString(2, user.getFirstName());
            stmt.setString(3, user.getLastName());
            stmt.setString(4, user.getEmail());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    user.setUserId(rs.getInt("user_id"));
                    user.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                    user.setActive(false);
                    logger.info("Created pending user: {} {} ({})", 
                               user.getFirstName(), user.getLastName(), user.getEmail());
                    return user;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to create pending user: {} - SQL Error: {}", 
                        user.getEmail(), e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Find user by email (for login)
     */
    public User findByEmail(String email) {
        var sql = """
            SELECT u.*, r.role_name
            FROM users u
            JOIN roles r ON u.role_id = r.role_id
            WHERE u.email = ? AND u.is_active = TRUE AND u.is_deleted = FALSE
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find user by email: {}", email, e);
        }
        
        return null;
    }
    
    /**
     * Find user by username (for login)
     */
    public User findByUsername(String username) {
        var sql = """
            SELECT u.*, r.role_name
            FROM users u
            JOIN roles r ON u.role_id = r.role_id
            WHERE u.username = ? AND u.is_active = TRUE AND u.is_deleted = FALSE
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find user by username: {}", username, e);
        }
        
        return null;
    }
    
    /**
     * Find user by ID
     */
    public User findById(int userId) {
        var sql = """
            SELECT u.*, r.role_name
            FROM users u
            JOIN roles r ON u.role_id = r.role_id
            WHERE u.user_id = ?
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find user by ID: {}", userId, e);
        }
        
        return null;
    }
    
    /**
     * Update last login timestamp
     */
    public void updateLastLogin(int userId) {
        var sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE user_id = ?";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.executeUpdate();
            
            logger.debug("Updated last login for user ID: {}", userId);
            
        } catch (SQLException e) {
            logger.error("Failed to update last login", e);
        }
    }
    
    /**
     * Check if email exists
     */
    public boolean emailExists(String email) {
        var sql = "SELECT 1 FROM users WHERE email = ? AND is_deleted = FALSE";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
            
        } catch (SQLException e) {
            logger.error("Failed to check email existence", e);
        }
        
        return false;
    }
    
    /**
     * Check if username exists
     */
    public boolean usernameExists(String username) {
        var sql = "SELECT 1 FROM users WHERE username = ? AND is_deleted = FALSE";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
            
        } catch (SQLException e) {
            logger.error("Failed to check username existence", e);
        }
        
        return false;
    }
    
    /**
     * Get all active users
     */
    public List<User> findAll() {
        var users = new ArrayList<User>();
        var sql = """
            SELECT u.*, r.role_name
            FROM users u
            JOIN roles r ON u.role_id = r.role_id
            WHERE u.is_deleted = FALSE
            ORDER BY u.created_at DESC
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to fetch all users", e);
        }
        
        return users;
    }
    
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setRoleId(rs.getInt("role_id"));
        user.setRoleName(rs.getString("role_name"));
        user.setGender(rs.getString("gender"));
        
        int branchId = rs.getInt("branch_id");
        if (!rs.wasNull()) {
            user.setBranchId(branchId);
        }
        
        user.setFirstName(rs.getString("first_name"));
        user.setMiddleName(rs.getString("middle_name"));
        user.setLastName(rs.getString("last_name"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPhoneNumber(rs.getString("phone_number"));
        user.setNationalId(rs.getString("national_id"));
        
        Date dob = rs.getDate("date_of_birth");
        if (dob != null) {
            user.setDateOfBirth(dob.toString());
        }
        
        user.setProfileImageUrl(rs.getString("profile_image_url"));
        user.setPasswordHash(rs.getString("password_hash"));
        
        Timestamp lastLogin = rs.getTimestamp("last_login");
        if (lastLogin != null) {
            user.setLastLogin(lastLogin.toInstant());
        }
        
        user.setActive(rs.getBoolean("is_active"));
        user.setDeleted(rs.getBoolean("is_deleted"));
        
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) {
            user.setDeletedAt(deletedAt.toInstant());
        }
        
        user.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        user.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        
        return user;
    }
    
    /**
     * Find all users with filters
     */
    public List<java.util.Map<String, Object>> findAllWithFilters(java.util.Map<String, String> filters) {
        var users = new ArrayList<java.util.Map<String, Object>>();
        var sql = new StringBuilder("""
            SELECT u.user_id, u.role_id, r.role_name, u.first_name, u.middle_name, u.last_name,
                   u.username, u.email, u.phone_number, u.is_active, u.is_approved, 
                   u.created_at, u.last_login, b.name as branch_name
            FROM users u
            JOIN roles r ON u.role_id = r.role_id
            LEFT JOIN branches b ON u.branch_id = b.branch_id
            WHERE u.is_deleted = FALSE
            """);
        
        var params = new ArrayList<>();
        
        if (filters.containsKey("role")) {
            sql.append(" AND r.role_name = ?");
            params.add(filters.get("role"));
        }
        
        if (filters.containsKey("status")) {
            boolean isActive = "active".equalsIgnoreCase(filters.get("status"));
            sql.append(" AND u.is_active = ?");
            params.add(isActive);
        }
        
        if (filters.containsKey("approved")) {
            boolean isApproved = "true".equalsIgnoreCase(filters.get("approved"));
            sql.append(" AND u.is_approved = ?");
            params.add(isApproved);
        }
        
        sql.append(" ORDER BY u.created_at DESC");
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> user = new java.util.HashMap<>();
                    user.put("userId", rs.getInt("user_id"));
                    user.put("roleId", rs.getInt("role_id"));
                    user.put("roleName", rs.getString("role_name"));
                    user.put("firstName", rs.getString("first_name"));
                    user.put("middleName", rs.getString("middle_name"));
                    user.put("lastName", rs.getString("last_name"));
                    user.put("username", rs.getString("username"));
                    user.put("email", rs.getString("email"));
                    user.put("phoneNumber", rs.getString("phone_number"));
                    user.put("isActive", rs.getBoolean("is_active"));
                    user.put("isApproved", rs.getBoolean("is_approved"));
                    user.put("branchName", rs.getString("branch_name"));
                    user.put("createdAt", rs.getTimestamp("created_at").toInstant());
                    
                    Timestamp lastLogin = rs.getTimestamp("last_login");
                    if (lastLogin != null) {
                        user.put("lastLogin", lastLogin.toInstant());
                    }
                    
                    users.add(user);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to fetch users with filters", e);
        }
        
        return users;
    }
    
    /**
     * Find user by ID (returns Map for API)
     */
    public java.util.Map<String, Object> findByIdAsMap(int userId) {
        var sql = """
            SELECT u.user_id, u.role_id, r.role_name, u.gender, u.branch_id, b.name as branch_name,
                   u.first_name, u.middle_name, u.last_name, u.username, u.email, u.phone_number,
                   u.national_id, u.date_of_birth, u.profile_image_url, u.is_active, u.is_approved,
                   u.approved_by, u.approved_at, u.approval_notes, u.last_login, u.created_at, u.updated_at
            FROM users u
            JOIN roles r ON u.role_id = r.role_id
            LEFT JOIN branches b ON u.branch_id = b.branch_id
            WHERE u.user_id = ? AND u.is_deleted = FALSE
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.util.Map<String, Object> user = new java.util.HashMap<>();
                    user.put("userId", rs.getInt("user_id"));
                    user.put("roleId", rs.getInt("role_id"));
                    user.put("roleName", rs.getString("role_name"));
                    user.put("gender", rs.getString("gender"));
                    user.put("branchId", rs.getObject("branch_id"));
                    user.put("branchName", rs.getString("branch_name"));
                    user.put("firstName", rs.getString("first_name"));
                    user.put("middleName", rs.getString("middle_name"));
                    user.put("lastName", rs.getString("last_name"));
                    user.put("username", rs.getString("username"));
                    user.put("email", rs.getString("email"));
                    user.put("phoneNumber", rs.getString("phone_number"));
                    user.put("nationalId", rs.getString("national_id"));
                    user.put("dateOfBirth", rs.getDate("date_of_birth"));
                    user.put("profileImageUrl", rs.getString("profile_image_url"));
                    user.put("isActive", rs.getBoolean("is_active"));
                    user.put("isApproved", rs.getBoolean("is_approved"));
                    user.put("approvedBy", rs.getObject("approved_by"));
                    user.put("approvedAt", rs.getTimestamp("approved_at"));
                    user.put("approvalNotes", rs.getString("approval_notes"));
                    user.put("lastLogin", rs.getTimestamp("last_login"));
                    user.put("createdAt", rs.getTimestamp("created_at").toInstant());
                    user.put("updatedAt", rs.getTimestamp("updated_at").toInstant());
                    return user;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find user by ID: {}", userId, e);
        }
        
        return null;
    }
    
    /**
     * Find user by ID (returns full User object with password)
     */
    public User findByIdFull(int userId) {
        return findById(userId);
    }
    
    /**
     * Find users pending approval
     */
    public List<java.util.Map<String, Object>> findPendingApproval() {
        var users = new ArrayList<java.util.Map<String, Object>>();
        var sql = "SELECT * FROM pending_user_approvals";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                java.util.Map<String, Object> user = new java.util.HashMap<>();
                user.put("userId", rs.getInt("user_id"));
                user.put("firstName", rs.getString("first_name"));
                user.put("middleName", rs.getString("middle_name"));
                user.put("lastName", rs.getString("last_name"));
                user.put("email", rs.getString("email"));
                user.put("username", rs.getString("username"));
                user.put("phoneNumber", rs.getString("phone_number"));
                user.put("roleName", rs.getString("role_name"));
                user.put("branchName", rs.getString("branch_name"));
                user.put("createdAt", rs.getTimestamp("created_at").toInstant());
                user.put("hoursPending", rs.getDouble("hours_pending"));
                users.add(user);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to fetch pending users", e);
        }
        
        return users;
    }
    
    /**
     * Create user from Map (for admin creation)
     * @throws SQLException with detailed error message for constraint violations
     */
    public int create(java.util.Map<String, Object> data) throws SQLException {
        var sql = new StringBuilder("INSERT INTO users (");
        var values = new StringBuilder("VALUES (");
        var params = new ArrayList<>();
        
        // Build dynamic SQL based on provided fields
        boolean first = true;
        for (String key : data.keySet()) {
            String column = camelToSnake(key);
            if (!first) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(column);
            values.append("?");
            params.add(data.get(key));
            first = false;
        }
        
        sql.append(") ").append(values).append(") RETURNING user_id");
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int userId = rs.getInt("user_id");
                    logger.info("Created user with ID: {}", userId);
                    return userId;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to create user - SQL: {} - Params: {} - Error: {}", 
                        sql.toString(), params, e.getMessage(), e);
            // Re-throw with detailed message
            throw e;
        }
        
        return -1;
    }
    
    /**
     * Update user
     * @throws SQLException with detailed error message for constraint violations
     */
    public boolean update(int userId, java.util.Map<String, Object> data) throws SQLException {
        if (data.isEmpty()) return false;
        
        var sql = new StringBuilder("UPDATE users SET ");
        var params = new ArrayList<>();
        
        boolean first = true;
        for (String key : data.keySet()) {
            if (!first) sql.append(", ");
            sql.append(camelToSnake(key)).append(" = ?");
            params.add(data.get(key));
            first = false;
        }
        
        sql.append(", updated_at = CURRENT_TIMESTAMP WHERE user_id = ? AND is_deleted = FALSE");
        params.add(userId);
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                // Convert Instant to Timestamp for PostgreSQL
                if (param instanceof java.time.Instant) {
                    stmt.setTimestamp(i + 1, java.sql.Timestamp.from((java.time.Instant) param));
                } else {
                    stmt.setObject(i + 1, param);
                }
            }
            
            logger.debug("Executing update SQL: {} with params: {}", sql.toString(), params);
            int affected = stmt.executeUpdate();
            logger.debug("Updated user {}: {} rows affected", userId, affected);
            return affected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update user {} - SQL: {} - Params: {} - Error: {}", 
                        userId, sql.toString(), params, e.getMessage(), e);
            // Re-throw with detailed message
            throw e;
        }
    }
    
    /**
     * Soft delete user
     */
    public boolean softDelete(int userId) {
        var sql = """
            UPDATE users 
            SET is_deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, is_active = FALSE 
            WHERE user_id = ? AND is_deleted = FALSE
            """;
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                logger.info("Soft deleted user: {}", userId);
                return true;
            }
            
        } catch (SQLException e) {
            logger.error("Failed to soft delete user {}", userId, e);
        }
        
        return false;
    }
    
    /**
     * Convert camelCase to snake_case
     */
    private String camelToSnake(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * Update user password
     */
    public boolean updatePassword(int userId, String passwordHash) {
        String sql = "UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        
        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, passwordHash);
            stmt.setInt(2, userId);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("Updated password for user ID: {}", userId);
                return true;
            }
            
            return false;
            
        } catch (SQLException e) {
            logger.error("Failed to update password for user ID: {}", userId, e);
            return false;
        }
    }
}
