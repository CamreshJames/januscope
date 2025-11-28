package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.domain.models.*;
import ke.skyworld.januscope.core.database.DatabaseEngine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for system/admin reference tables
 */
public class SystemRepository {
    private final DatabaseEngine dbEngine;

    public SystemRepository(DatabaseEngine dbEngine) {
        this.dbEngine = dbEngine;
    }

    // ==================== ROLES ====================
    public List<Role> getAllRoles() throws SQLException {
        List<Role> roles = new ArrayList<>();
        String sql = "SELECT role_id, role_name, description, created_at, updated_at FROM roles ORDER BY role_name";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                roles.add(mapRole(rs));
            }
        }
        return roles;
    }

    public Optional<Role> getRoleById(Integer roleId) throws SQLException {
        String sql = "SELECT role_id, role_name, description, created_at, updated_at FROM roles WHERE role_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, roleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRole(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Role createRole(Role role) throws SQLException {
        String sql = "INSERT INTO roles (role_name, description) VALUES (?, ?) RETURNING role_id, created_at, updated_at";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, role.getRoleName());
            stmt.setString(2, role.getDescription());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    role.setRoleId(rs.getInt("role_id"));
                    role.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    role.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return role;
    }

    public Role updateRole(Role role) throws SQLException {
        String sql = "UPDATE roles SET role_name = ?, description = ? WHERE role_id = ? RETURNING updated_at";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, role.getRoleName());
            stmt.setString(2, role.getDescription());
            stmt.setInt(3, role.getRoleId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    role.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return role;
    }

    public boolean deleteRole(Integer roleId) throws SQLException {
        String sql = "DELETE FROM roles WHERE role_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, roleId);
            return stmt.executeUpdate() > 0;
        }
    }

    // ==================== COUNTRIES ====================
    public List<Country> getAllCountries() throws SQLException {
        List<Country> countries = new ArrayList<>();
        String sql = "SELECT country_code, name, created_at, updated_at FROM countries ORDER BY name";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                countries.add(mapCountry(rs));
            }
        }
        return countries;
    }

    public Optional<Country> getCountryByCode(String countryCode) throws SQLException {
        String sql = "SELECT country_code, name, created_at, updated_at FROM countries WHERE country_code = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, countryCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCountry(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Country createCountry(Country country) throws SQLException {
        String sql = "INSERT INTO countries (country_code, name) VALUES (?, ?) RETURNING created_at, updated_at";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, country.getCountryCode());
            stmt.setString(2, country.getName());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    country.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    country.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return country;
    }

    public Country updateCountry(Country country) throws SQLException {
        String sql = "UPDATE countries SET name = ? WHERE country_code = ? RETURNING updated_at";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, country.getName());
            stmt.setString(2, country.getCountryCode());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    country.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return country;
    }

    public boolean deleteCountry(String countryCode) throws SQLException {
        String sql = "DELETE FROM countries WHERE country_code = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, countryCode);
            return stmt.executeUpdate() > 0;
        }
    }

    // ==================== BRANCHES ====================
    public List<Branch> getAllBranches() throws SQLException {
        List<Branch> branches = new ArrayList<>();
        String sql = """
            SELECT b.branch_id, b.name, b.code, b.country_code, c.name as country_name,
                   b.location_id, l.name as location_name, b.is_active, b.created_at, b.updated_at
            FROM branches b
            LEFT JOIN countries c ON b.country_code = c.country_code
            LEFT JOIN locations l ON b.location_id = l.location_id
            ORDER BY b.name
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                branches.add(mapBranch(rs));
            }
        }
        return branches;
    }

    public Optional<Branch> getBranchById(Integer branchId) throws SQLException {
        String sql = """
            SELECT b.branch_id, b.name, b.code, b.country_code, c.name as country_name,
                   b.location_id, l.name as location_name, b.is_active, b.created_at, b.updated_at
            FROM branches b
            LEFT JOIN countries c ON b.country_code = c.country_code
            LEFT JOIN locations l ON b.location_id = l.location_id
            WHERE b.branch_id = ?
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, branchId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBranch(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Branch createBranch(Branch branch) throws SQLException {
        String sql = """
            INSERT INTO branches (name, code, country_code, location_id, is_active)
            VALUES (?, ?, ?, ?, ?)
            RETURNING branch_id, created_at, updated_at
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, branch.getName());
            stmt.setString(2, branch.getCode());
            stmt.setString(3, branch.getCountryCode());
            if (branch.getLocationId() != null) {
                stmt.setInt(4, branch.getLocationId());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setBoolean(5, branch.getIsActive() != null ? branch.getIsActive() : true);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    branch.setBranchId(rs.getInt("branch_id"));
                    branch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    branch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return branch;
    }

    public Branch updateBranch(Branch branch) throws SQLException {
        String sql = """
            UPDATE branches
            SET name = ?, code = ?, country_code = ?, location_id = ?, is_active = ?
            WHERE branch_id = ?
            RETURNING updated_at
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, branch.getName());
            stmt.setString(2, branch.getCode());
            stmt.setString(3, branch.getCountryCode());
            if (branch.getLocationId() != null) {
                stmt.setInt(4, branch.getLocationId());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setBoolean(5, branch.getIsActive());
            stmt.setInt(6, branch.getBranchId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    branch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return branch;
    }

    public boolean deleteBranch(Integer branchId) throws SQLException {
        String sql = "DELETE FROM branches WHERE branch_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, branchId);
            return stmt.executeUpdate() > 0;
        }
    }

    // ==================== LOCATIONS ====================
    public List<Location> getAllLocations() throws SQLException {
        List<Location> locations = new ArrayList<>();
        String sql = """
            SELECT l.location_id, l.name, l.parent_id, p.name as parent_name, l.location_type,
                   l.created_at, l.updated_at
            FROM locations l
            LEFT JOIN locations p ON l.parent_id = p.location_id
            ORDER BY l.location_type, l.name
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                locations.add(mapLocation(rs));
            }
        }
        return locations;
    }

    public Optional<Location> getLocationById(Integer locationId) throws SQLException {
        String sql = """
            SELECT l.location_id, l.name, l.parent_id, p.name as parent_name, l.location_type,
                   l.created_at, l.updated_at
            FROM locations l
            LEFT JOIN locations p ON l.parent_id = p.location_id
            WHERE l.location_id = ?
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, locationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapLocation(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Location createLocation(Location location) throws SQLException {
        String sql = """
            INSERT INTO locations (name, parent_id, location_type)
            VALUES (?, ?, ?)
            RETURNING location_id, created_at, updated_at
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, location.getName());
            if (location.getParentId() != null) {
                stmt.setInt(2, location.getParentId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, location.getLocationType());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    location.setLocationId(rs.getInt("location_id"));
                    location.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    location.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return location;
    }

    public Location updateLocation(Location location) throws SQLException {
        String sql = """
            UPDATE locations
            SET name = ?, parent_id = ?, location_type = ?
            WHERE location_id = ?
            RETURNING updated_at
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, location.getName());
            if (location.getParentId() != null) {
                stmt.setInt(2, location.getParentId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, location.getLocationType());
            stmt.setInt(4, location.getLocationId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    location.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return location;
    }

    public boolean deleteLocation(Integer locationId) throws SQLException {
        String sql = "DELETE FROM locations WHERE location_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, locationId);
            return stmt.executeUpdate() > 0;
        }
    }

    // ==================== NOTIFICATION TEMPLATES ====================
    public List<NotificationTemplate> getAllTemplates() throws SQLException {
        List<NotificationTemplate> templates = new ArrayList<>();
        String sql = """
            SELECT template_id, name, event_type, channel, subject_template, body_template,
                   is_active, created_by, updated_by, created_at, updated_at
            FROM notification_templates
            ORDER BY event_type, channel
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                templates.add(mapTemplate(rs));
            }
        }
        return templates;
    }

    public Optional<NotificationTemplate> getTemplateById(Integer templateId) throws SQLException {
        String sql = """
            SELECT template_id, name, event_type, channel, subject_template, body_template,
                   is_active, created_by, updated_by, created_at, updated_at
            FROM notification_templates
            WHERE template_id = ?
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, templateId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTemplate(rs));
                }
            }
        }
        return Optional.empty();
    }

    public NotificationTemplate createTemplate(NotificationTemplate template) throws SQLException {
        String sql = """
            INSERT INTO notification_templates (name, event_type, channel, subject_template, body_template, is_active, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING template_id, created_at, updated_at
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, template.getName());
            stmt.setString(2, template.getEventType());
            stmt.setString(3, template.getChannel());
            stmt.setString(4, template.getSubjectTemplate());
            stmt.setString(5, template.getBodyTemplate());
            stmt.setBoolean(6, template.getIsActive() != null ? template.getIsActive() : true);
            if (template.getCreatedBy() != null) {
                stmt.setInt(7, template.getCreatedBy());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    template.setTemplateId(rs.getInt("template_id"));
                    template.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    template.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return template;
    }

    public NotificationTemplate updateTemplate(NotificationTemplate template) throws SQLException {
        String sql = """
            UPDATE notification_templates
            SET name = ?, event_type = ?, channel = ?, subject_template = ?, body_template = ?, is_active = ?, updated_by = ?
            WHERE template_id = ?
            RETURNING updated_at
            """;
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, template.getName());
            stmt.setString(2, template.getEventType());
            stmt.setString(3, template.getChannel());
            stmt.setString(4, template.getSubjectTemplate());
            stmt.setString(5, template.getBodyTemplate());
            stmt.setBoolean(6, template.getIsActive());
            if (template.getUpdatedBy() != null) {
                stmt.setInt(7, template.getUpdatedBy());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }
            stmt.setInt(8, template.getTemplateId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    template.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
            }
        }
        return template;
    }

    public boolean deleteTemplate(Integer templateId) throws SQLException {
        String sql = "DELETE FROM notification_templates WHERE template_id = ?";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, templateId);
            return stmt.executeUpdate() > 0;
        }
    }

    // ==================== MAPPERS ====================
    private Role mapRole(ResultSet rs) throws SQLException {
        Role role = new Role();
        role.setRoleId(rs.getInt("role_id"));
        role.setRoleName(rs.getString("role_name"));
        role.setDescription(rs.getString("description"));
        role.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        role.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return role;
    }

    private Country mapCountry(ResultSet rs) throws SQLException {
        Country country = new Country();
        country.setCountryCode(rs.getString("country_code"));
        country.setName(rs.getString("name"));
        country.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        country.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return country;
    }

    private Branch mapBranch(ResultSet rs) throws SQLException {
        Branch branch = new Branch();
        branch.setBranchId(rs.getInt("branch_id"));
        branch.setName(rs.getString("name"));
        branch.setCode(rs.getString("code"));
        branch.setCountryCode(rs.getString("country_code"));
        branch.setCountryName(rs.getString("country_name"));
        branch.setLocationId((Integer) rs.getObject("location_id"));
        branch.setLocationName(rs.getString("location_name"));
        branch.setIsActive(rs.getBoolean("is_active"));
        branch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        branch.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return branch;
    }

    private Location mapLocation(ResultSet rs) throws SQLException {
        Location location = new Location();
        location.setLocationId(rs.getInt("location_id"));
        location.setName(rs.getString("name"));
        location.setParentId((Integer) rs.getObject("parent_id"));
        location.setParentName(rs.getString("parent_name"));
        location.setLocationType(rs.getString("location_type"));
        location.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        location.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return location;
    }

    private NotificationTemplate mapTemplate(ResultSet rs) throws SQLException {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(rs.getInt("template_id"));
        template.setName(rs.getString("name"));
        template.setEventType(rs.getString("event_type"));
        template.setChannel(rs.getString("channel"));
        template.setSubjectTemplate(rs.getString("subject_template"));
        template.setBodyTemplate(rs.getString("body_template"));
        template.setIsActive(rs.getBoolean("is_active"));
        template.setCreatedBy((Integer) rs.getObject("created_by"));
        template.setUpdatedBy((Integer) rs.getObject("updated_by"));
        template.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        template.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return template;
    }

    // ==================== BULK OPERATIONS ====================
    
    public int bulkInsertCountries(List<Country> countries) throws SQLException {
        if (countries == null || countries.isEmpty()) {
            return 0;
        }
        
        String sql = "INSERT INTO countries (country_code, name) VALUES (?, ?) ON CONFLICT (country_code) DO NOTHING";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (Country country : countries) {
                stmt.setString(1, country.getCountryCode());
                stmt.setString(2, country.getName());
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            int count = 0;
            for (int result : results) {
                if (result > 0) count++;
            }
            return count;
        }
    }
    
    public int bulkInsertBranches(List<Branch> branches) throws SQLException {
        if (branches == null || branches.isEmpty()) {
            return 0;
        }
        
        String sql = "INSERT INTO branches (code, name, country_code) VALUES (?, ?, ?) ON CONFLICT (code) DO NOTHING";
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (Branch branch : branches) {
                stmt.setString(1, branch.getCode());
                stmt.setString(2, branch.getName());
                stmt.setString(3, branch.getCountryCode());
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            int count = 0;
            for (int result : results) {
                if (result > 0) count++;
            }
            return count;
        }
    }
    
    public int bulkInsertLocations(List<Location> locations) throws SQLException {
        if (locations == null || locations.isEmpty()) {
            return 0;
        }
        
        // Sort locations to insert parents before children
        // Locations without parent_id come first, then by parent_id
        locations.sort((a, b) -> {
            if (a.getParentId() == null && b.getParentId() == null) return 0;
            if (a.getParentId() == null) return -1;
            if (b.getParentId() == null) return 1;
            return a.getParentId().compareTo(b.getParentId());
        });
        
        String sql = "INSERT INTO locations (name, parent_id, location_type) VALUES (?, ?, ?) RETURNING location_id";
        int count = 0;
        
        // Map to track inserted location names to their IDs for parent reference
        java.util.Map<String, Integer> nameToIdMap = new java.util.HashMap<>();
        
        try (Connection conn = dbEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (Location location : locations) {
                stmt.setString(1, location.getName());
                
                // If parentId is provided, use it; otherwise try to resolve by name
                if (location.getParentId() != null) {
                    stmt.setInt(2, location.getParentId());
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                
                stmt.setString(3, location.getLocationType());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int locationId = rs.getInt("location_id");
                        nameToIdMap.put(location.getName(), locationId);
                        count++;
                    }
                }
            }
        }
        
        return count;
    }
}
