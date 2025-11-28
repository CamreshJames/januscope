package ke.skyworld.januscope.domain.repositories;

import ke.skyworld.januscope.core.database.DatabaseEngine;
import ke.skyworld.januscope.domain.models.SSLCheckResult;
import ke.skyworld.januscope.utils.Logger;

import java.sql.*;
import java.time.Instant;

/**
 * Repository for SSL certificate check results
 */
public class SSLCheckRepository {
    private static final Logger logger = Logger.getLogger(SSLCheckRepository.class);
    private final DatabaseEngine databaseEngine;

    public SSLCheckRepository(DatabaseEngine databaseEngine) {
        this.databaseEngine = databaseEngine;
    }

    /**
     * Find the latest SSL check for a service
     */
    public SSLCheckResult findLatestByServiceId(Integer serviceId) {
        String sql = """
            SELECT * FROM ssl_checks
            WHERE service_id = ?
            ORDER BY last_checked_at DESC
            LIMIT 1
            """;

        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, serviceId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSSLCheckResult(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding latest SSL check for service " + serviceId, e);
        }

        return null;
    }

    /**
     * Save SSL check result
     */
    public void save(SSLCheckResult result) {
        String sql = """
            INSERT INTO ssl_checks (
                service_id, domain, issuer, subject, valid_from, valid_to,
                days_remaining, serial_number, fingerprint, algorithm, key_size,
                is_self_signed, is_valid, last_checked_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, result.getServiceId());
            stmt.setString(2, result.getDomain());
            stmt.setString(3, result.getIssuer());
            stmt.setString(4, result.getSubject());
            stmt.setTimestamp(5, result.getValidFrom() != null ? Timestamp.from(result.getValidFrom()) : null);
            stmt.setTimestamp(6, result.getValidTo() != null ? Timestamp.from(result.getValidTo()) : null);
            stmt.setObject(7, result.getDaysRemaining(), Types.INTEGER);
            stmt.setString(8, result.getSerialNumber());
            stmt.setString(9, result.getFingerprint());
            stmt.setString(10, result.getAlgorithm());
            stmt.setObject(11, result.getKeySize(), Types.INTEGER);
            stmt.setBoolean(12, result.isSelfSigned());
            stmt.setBoolean(13, result.isValid());
            stmt.setTimestamp(14, Timestamp.from(result.getLastCheckedAt()));

            stmt.executeUpdate();
            logger.info("Saved SSL check for service " + result.getServiceId());

        } catch (SQLException e) {
            logger.error("Error saving SSL check for service " + result.getServiceId(), e);
        }
    }

    /**
     * Count certificates expiring within specified days
     */
    public int countExpiringWithin(int daysThreshold) {
        String sql = """
            SELECT COUNT(DISTINCT service_id)
            FROM ssl_checks sc1
            WHERE days_remaining <= ? AND days_remaining > 0
            AND last_checked_at = (
                SELECT MAX(last_checked_at)
                FROM ssl_checks sc2
                WHERE sc2.service_id = sc1.service_id
            )
            """;

        try (Connection conn = databaseEngine.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, daysThreshold);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting expiring SSL certificates", e);
        }

        return 0;
    }

    private SSLCheckResult mapResultSetToSSLCheckResult(ResultSet rs) throws SQLException {
        SSLCheckResult result = new SSLCheckResult();
        result.setServiceId(rs.getInt("service_id"));
        result.setDomain(rs.getString("domain"));
        result.setIssuer(rs.getString("issuer"));
        result.setSubject(rs.getString("subject"));

        Timestamp validFrom = rs.getTimestamp("valid_from");
        if (validFrom != null) {
            result.setValidFrom(validFrom.toInstant());
        }

        Timestamp validTo = rs.getTimestamp("valid_to");
        if (validTo != null) {
            result.setValidTo(validTo.toInstant());
        }

        result.setDaysRemaining((Integer) rs.getObject("days_remaining"));
        result.setSerialNumber(rs.getString("serial_number"));
        result.setFingerprint(rs.getString("fingerprint"));
        result.setAlgorithm(rs.getString("algorithm"));
        result.setKeySize((Integer) rs.getObject("key_size"));
        result.setSelfSigned(rs.getBoolean("is_self_signed"));
        result.setValid(rs.getBoolean("is_valid"));
        result.setLastCheckedAt(rs.getTimestamp("last_checked_at").toInstant());

        return result;
    }
}
