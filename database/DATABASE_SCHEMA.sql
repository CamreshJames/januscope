-- =====================================================================
-- JANUSCOPE - UPTIME & SSL MONITORING SYSTEM
-- =====================================================================
-- Company: Sky World Limited
-- Author : James Mwangi Njenga (Camresh)
-- Supervisor/Mentor : Willliam Ochomo
-- Project: Januscope
-- Version: v1.0.0
-- Date   : 2025-11-10
-- Purpose: A complete DB for Januscope
-- Standards: skyworld infered standards
-- =====================================================================

-----------------------------------------------------------------
-- 0. PRE-REQUISITES (run as super-user)
-----------------------------------------------------------------
-- CREATE DATABASE januscope WITH ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8';
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- for gen_random_uuid(), crypt()

-----------------------------------------------------------------
-- 1. REFERENCE / LOOKUP TABLES
-----------------------------------------------------------------

-- genders -------------------------------------------------------
CREATE TABLE genders (
    gender TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  genders IS 'Gender reference values';
COMMENT ON COLUMN genders.gender IS 'Gender identifier (male, female, other)';
INSERT INTO genders (gender, description) VALUES
    ('male',   'Male'),
    ('female', 'Female'),
    ('other',  'Other / Non-Binary');

-- roles ---------------------------------------------------------
CREATE TABLE roles (
    role_id   SERIAL PRIMARY KEY,
    role_name TEXT UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE roles IS 'RBAC roles';
INSERT INTO roles (role_name, description) VALUES
    ('admin',    'Full system access - users, services, groups, templates, settings'),
    ('operator', 'Operational access - services, reports, dashboards'),
    ('viewer',   'Read-only dashboards & reports');

CREATE INDEX idx_roles_name ON roles(role_name);

-- countries -----------------------------------------------------
CREATE TABLE countries (
    country_code TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE countries IS 'ISO 3166-1 alpha-2 codes';
INSERT INTO countries (country_code, name) VALUES
    ('KE','Kenya'),('UG','Uganda'),('TZ','Tanzania'),('RW','Rwanda'),('BI','Burundi'),
    ('SS','South Sudan'),('SO','Somalia'),('DJ','Djibouti'),('ER','Eritrea');


-- locations (hierarchical) --------------------------------------
CREATE TABLE locations (
    location_id   SERIAL PRIMARY KEY,
    name          TEXT NOT NULL,
    parent_id     INTEGER REFERENCES locations(location_id) ON DELETE CASCADE,
    location_type TEXT NOT NULL CHECK (location_type IN ('country','county','city','site')),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE locations IS 'Self-referencing location hierarchy';
CREATE INDEX idx_locations_parent ON locations(parent_id);

-- branches ------------------------------------------------------
CREATE TABLE branches (
    branch_id   SERIAL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    code        TEXT UNIQUE,
    country_code TEXT REFERENCES countries(country_code) ON DELETE RESTRICT,
    location_id INTEGER REFERENCES locations(location_id) ON DELETE SET NULL,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE branches IS 'Organizational branches/offices';
INSERT INTO branches (name, code, is_active) VALUES ('Headquarters','HQ',TRUE);
CREATE INDEX idx_branches_name   ON branches(name);
CREATE INDEX idx_branches_country ON branches(country_code);

-- users ---------------------------------------------------------
CREATE TABLE users (
    user_id        SERIAL PRIMARY KEY,
    role_id        INTEGER NOT NULL REFERENCES roles(role_id) ON DELETE RESTRICT,
    gender         TEXT REFERENCES genders(gender) ON DELETE SET NULL,
    branch_id      INTEGER REFERENCES branches(branch_id) ON DELETE SET NULL,
    first_name     VARCHAR(100) NOT NULL,
    middle_name    VARCHAR(100),
    last_name      VARCHAR(100) NOT NULL,
    username       VARCHAR(50)  UNIQUE,
    email          VARCHAR(150) UNIQUE NOT NULL,
    phone_number   VARCHAR(20)  UNIQUE,
    national_id    VARCHAR(20),
    date_of_birth  DATE,
    profile_image_url TEXT,
    password_hash  TEXT,                              -- Argon2id (nullable for pending users)
    last_login     TIMESTAMP,
    is_active      BOOLEAN DEFAULT TRUE,
    is_deleted     BOOLEAN DEFAULT FALSE,
    deleted_at     TIMESTAMP,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_approved    BOOLEAN DEFAULT FALSE,
    approved_by    INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    approved_at    TIMESTAMP,
    approval_notes TEXT,
    CONSTRAINT ck_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT ck_approved_users_have_password CHECK (
        (is_approved = FALSE AND password_hash IS NULL) OR 
        (is_approved = TRUE AND password_hash IS NOT NULL)
    )$')
);
COMMENT ON TABLE users IS 'System users with Argon2id password hashes';
COMMENT ON COLUMN users.is_approved IS 'Whether user has been approved by admin';
COMMENT ON COLUMN users.approved_by IS 'Admin user who approved this user';
COMMENT ON COLUMN users.approved_at IS 'Timestamp when user was approved';
COMMENT ON COLUMN users.approval_notes IS 'Admin notes about approval/rejection';
COMMENT ON CONSTRAINT ck_approved_users_have_password ON users IS 
    'Ensures approved users have passwords, pending users can have null passwords';
CREATE INDEX idx_users_email      ON users(email);
CREATE INDEX idx_users_username   ON users(username);
CREATE INDEX idx_users_phone      ON users(phone_number);
CREATE INDEX idx_users_role       ON users(role_id);
CREATE INDEX idx_users_branch     ON users(branch_id);
CREATE INDEX idx_users_approval   ON users(is_approved, is_active) WHERE is_deleted = FALSE;

-- refresh_tokens ------------------------------------------------
CREATE TABLE refresh_tokens (
    token_id   SERIAL PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,                     -- SHA-256 of JWT
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refresh_user   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_expiry ON refresh_tokens(expires_at);

-----------------------------------------------------------------
-- 2. MONITORING CORE
-----------------------------------------------------------------

-- contact_groups ------------------------------------------------
CREATE TABLE contact_groups (
    group_id   SERIAL PRIMARY KEY,
    name       TEXT NOT NULL UNIQUE,
    description TEXT,
    is_active  BOOLEAN DEFAULT TRUE,
    created_by INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    updated_by INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE contact_groups IS 'Notification contact groups';
CREATE INDEX idx_contact_groups_name ON contact_groups(name);

-- contact_members -----------------------------------------------
CREATE TABLE contact_members (
    member_id      SERIAL PRIMARY KEY,
    group_id       INTEGER NOT NULL REFERENCES contact_groups(group_id) ON DELETE CASCADE,
    name           TEXT NOT NULL,
    email          VARCHAR(150),
    telegram_handle VARCHAR(100),
    phone_number   VARCHAR(20),
    is_active      BOOLEAN DEFAULT TRUE,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_one_contact_method CHECK (
        email IS NOT NULL OR telegram_handle IS NOT NULL OR phone_number IS NOT NULL
    )
);
CREATE INDEX idx_contact_members_group  ON contact_members(group_id);
CREATE INDEX idx_contact_members_email  ON contact_members(email);

-- services ------------------------------------------------------
CREATE TABLE services (
    service_id          SERIAL PRIMARY KEY,
    name                TEXT NOT NULL,
    url                 TEXT NOT NULL,
    check_interval_seconds INTEGER NOT NULL DEFAULT 300 CHECK (check_interval_seconds >= 60),
    timeout_ms          INTEGER NOT NULL DEFAULT 10000 CHECK (timeout_ms >= 1000),
    max_retries         INTEGER NOT NULL DEFAULT 3 CHECK (max_retries BETWEEN 0 AND 10),
    retry_delay_ms      INTEGER NOT NULL DEFAULT 5000,
    current_status      TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (current_status IN ('UP','DOWN','UNKNOWN')),
    last_checked_at     TIMESTAMP,
    custom_headers      JSONB,
    is_active           BOOLEAN DEFAULT TRUE,
    is_deleted          BOOLEAN DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    created_by          INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    updated_by          INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE services IS 'Monitored HTTP/HTTPS endpoints';
CREATE INDEX idx_services_name          ON services(name);
CREATE INDEX idx_services_url           ON services(url);
CREATE INDEX idx_services_active        ON services(is_active);
CREATE INDEX idx_services_status        ON services(current_status);
CREATE INDEX idx_services_last_checked  ON services(last_checked_at);

-- service_contact_groups (M-N) ----------------------------------
CREATE TABLE service_contact_groups (
    service_id INTEGER NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    group_id   INTEGER NOT NULL REFERENCES contact_groups(group_id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (service_id, group_id)
);
CREATE INDEX idx_scg_service ON service_contact_groups(service_id);
CREATE INDEX idx_scg_group   ON service_contact_groups(group_id);

-- uptime_checks -------------------------------------------------
CREATE TABLE uptime_checks (
    check_id        BIGSERIAL PRIMARY KEY,
    service_id      INTEGER NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    status          TEXT NOT NULL CHECK (status IN ('UP','DOWN')),
    response_time_ms INTEGER,
    http_code       INTEGER,
    error_message   TEXT,
    checked_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE uptime_checks IS 'Every ping result';
CREATE INDEX idx_uptime_service       ON uptime_checks(service_id);
CREATE INDEX idx_uptime_checked       ON uptime_checks(checked_at DESC);
CREATE INDEX idx_uptime_status        ON uptime_checks(status);
CREATE INDEX idx_uptime_service_checked ON uptime_checks(service_id, checked_at DESC);

-- incidents -----------------------------------------------------
CREATE TABLE incidents (
    incident_id     SERIAL PRIMARY KEY,
    service_id      INTEGER NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    started_at      TIMESTAMP NOT NULL,
    recovered_at    TIMESTAMP,
    duration_seconds INTEGER,
    error_message   TEXT,
    is_resolved     BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_incidents_service   ON incidents(service_id);
CREATE INDEX idx_incidents_started   ON incidents(started_at);
CREATE INDEX idx_incidents_resolved  ON incidents(is_resolved);

-- ssl_checks ----------------------------------------------------
CREATE TABLE ssl_checks (
    ssl_check_id   BIGSERIAL PRIMARY KEY,
    service_id     INTEGER NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    domain         TEXT NOT NULL,
    issuer         TEXT,
    subject        TEXT,
    valid_from     TIMESTAMP,
    valid_to       TIMESTAMP,
    days_remaining INTEGER,
    serial_number  TEXT,
    fingerprint    TEXT,
    algorithm      TEXT,
    key_size       INTEGER,
    is_self_signed BOOLEAN DEFAULT FALSE,
    is_valid       BOOLEAN DEFAULT TRUE,
    last_checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ssl_service      ON ssl_checks(service_id);
CREATE INDEX idx_ssl_domain       ON ssl_checks(domain);
CREATE INDEX idx_ssl_days_rem     ON ssl_checks(days_remaining);
CREATE INDEX idx_ssl_last_checked ON ssl_checks(last_checked_at);

-----------------------------------------------------------------
-- 3. NOTIFICATION SYSTEM
-----------------------------------------------------------------

-- notification_channels -----------------------------------------
CREATE TABLE notification_channels (
    channel     TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    is_enabled  BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO notification_channels (channel, description, is_enabled) VALUES
    ('email',    'SMTP email',      TRUE),
    ('telegram', 'Telegram Bot',    TRUE),
    ('sms',      'SMS via provider',FALSE);

-- event_types ---------------------------------------------------
CREATE TABLE event_types (
    event_type  TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO event_types (event_type, description) VALUES
    ('SERVICE_DOWN',       'Service went DOWN'),
    ('SERVICE_RECOVERED',  'Service recovered'),
    ('SSL_EXPIRY_30',      'SSL expires in 30 days'),
    ('SSL_EXPIRY_14',      'SSL expires in 14 days'),
    ('SSL_EXPIRY_7',       'SSL expires in 7 days'),
    ('SSL_EXPIRY_3',       'SSL expires in 3 days');

-- notification_templates ----------------------------------------
CREATE TABLE notification_templates (
    template_id   SERIAL PRIMARY KEY,
    name          TEXT NOT NULL,
    event_type    TEXT NOT NULL REFERENCES event_types(event_type) ON DELETE CASCADE,
    channel       TEXT NOT NULL REFERENCES notification_channels(channel) ON DELETE CASCADE,
    subject_template TEXT,
    body_template TEXT NOT NULL,
    is_active     BOOLEAN DEFAULT TRUE,
    created_by    INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    updated_by    INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_template_event_channel UNIQUE (event_type, channel)
);
-- Default templates (use {{var}} placeholders)
INSERT INTO notification_templates (name, event_type, channel, subject_template, body_template) VALUES
('Service Down - Email',      'SERVICE_DOWN',      'email',
 'ALERT: {{service_name}} is DOWN',
 'Service: {{service_name}}\nURL: {{service_url}}\nStatus: DOWN\nTime: {{down_time}}\nError: {{error_message}}\nHTTP: {{http_code}}'),

('Service Recovered - Email', 'SERVICE_RECOVERED', 'email',
 'RESOLVED: {{service_name}} is UP',
 'Service: {{service_name}}\nURL: {{service_url}}\nStatus: RECOVERED\nDowntime: {{downtime_duration}}\nRecovered: {{recovered_time}}'),

('SSL Expiry - Email',        'SSL_EXPIRY_30',     'email',
 'WARNING: SSL expires soon - {{service_name}}',
 'Service: {{service_name}}\nDomain: {{domain}}\nDays left: {{days_remaining}}\nExpires: {{expiry_date}}\nIssuer: {{issuer}}');

CREATE INDEX idx_nt_event  ON notification_templates(event_type);
CREATE INDEX idx_nt_channel ON notification_templates(channel);

-- notifications -------------------------------------------------
CREATE TABLE notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    service_id      INTEGER NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    incident_id     INTEGER REFERENCES incidents(incident_id) ON DELETE SET NULL,
    ssl_check_id    BIGINT  REFERENCES ssl_checks(ssl_check_id) ON DELETE SET NULL,
    event_type      TEXT NOT NULL REFERENCES event_types(event_type) ON DELETE RESTRICT,
    channel         TEXT NOT NULL REFERENCES notification_channels(channel) ON DELETE RESTRICT,
    recipient       TEXT NOT NULL,
    subject         TEXT,
    message_body    TEXT NOT NULL,
    delivery_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING','SENT','DELIVERED','FAILED','RETRYING')),
    sent_at         TIMESTAMP,
    delivered_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notif_service   ON notifications(service_id);
CREATE INDEX idx_notif_incident  ON notifications(incident_id);
CREATE INDEX idx_notif_event     ON notifications(event_type);
CREATE INDEX idx_notif_status    ON notifications(delivery_status);
CREATE INDEX idx_notif_sent      ON notifications(sent_at);

-- notification_cooldowns ----------------------------------------
CREATE TABLE notification_cooldowns (
    cooldown_id      SERIAL PRIMARY KEY,
    service_id       INTEGER NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    group_id         INTEGER NOT NULL REFERENCES contact_groups(group_id) ON DELETE CASCADE,
    event_type       TEXT NOT NULL REFERENCES event_types(event_type) ON DELETE CASCADE,
    last_notified_at TIMESTAMP NOT NULL,
    cooldown_until   TIMESTAMP NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cooldown UNIQUE (service_id, group_id, event_type)
);
CREATE INDEX idx_cooldown_service ON notification_cooldowns(service_id);
CREATE INDEX idx_cooldown_until   ON notification_cooldowns(cooldown_until);

-----------------------------------------------------------------
-- 4. REPORTING
-----------------------------------------------------------------

-- report_types --------------------------------------------------
CREATE TABLE report_types (
    report_type TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO report_types (report_type, description) VALUES
    ('daily',   'Daily uptime summary'),
    ('weekly',  'Weekly uptime & performance'),
    ('monthly', 'Monthly comprehensive report'),
    ('custom',  'Custom date range');

-- reports -------------------------------------------------------
CREATE TABLE reports (
    report_id      SERIAL PRIMARY KEY,
    name           TEXT NOT NULL,
    report_type    TEXT NOT NULL REFERENCES report_types(report_type) ON DELETE RESTRICT,
    from_date      DATE NOT NULL,
    to_date        DATE NOT NULL,
    format         TEXT NOT NULL CHECK (format IN ('pdf','xlsx')),
    file_url       TEXT,
    file_size_bytes BIGINT,
    generated_by   INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    generated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dates CHECK (to_date >= from_date)
);
CREATE INDEX idx_reports_type      ON reports(report_type);
CREATE INDEX idx_reports_generated ON reports(generated_at);

-- scheduled_reports ---------------------------------------------
CREATE TABLE scheduled_reports (
    schedule_id   SERIAL PRIMARY KEY,
    name          TEXT NOT NULL,
    report_type   TEXT NOT NULL REFERENCES report_types(report_type) ON DELETE CASCADE,
    cron_expression TEXT NOT NULL,
    format        TEXT NOT NULL CHECK (format IN ('pdf','xlsx')),
    include_charts BOOLEAN DEFAULT TRUE,
    is_active     BOOLEAN DEFAULT TRUE,
    last_run_at   TIMESTAMP,
    next_run_at   TIMESTAMP,
    created_by    INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sched_next ON scheduled_reports(next_run_at);

-- report_recipients ---------------------------------------------
CREATE TABLE report_recipients (
    schedule_id INTEGER NOT NULL REFERENCES scheduled_reports(schedule_id) ON DELETE CASCADE,
    group_id    INTEGER NOT NULL REFERENCES contact_groups(group_id) ON DELETE CASCADE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id, group_id)
);
CREATE INDEX idx_rr_sched ON report_recipients(schedule_id);
CREATE INDEX idx_rr_group ON report_recipients(group_id);

-----------------------------------------------------------------
-- 5. SYSTEM CONFIGURATION
-----------------------------------------------------------------

CREATE TABLE settings (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,
    description TEXT,
    data_type   TEXT NOT NULL DEFAULT 'string' CHECK (data_type IN ('string','integer','boolean','json')),
    is_sensitive BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO settings (key, value, description, data_type) VALUES
('monitoring.default_interval_seconds','300','Default ping interval','integer'),
('monitoring.max_retries','3','Default retries before DOWN','integer'),
('monitoring.retry_delay_ms','5000','Delay between retries','integer'),
('monitoring.timeout_ms','10000','HTTP timeout','integer'),
('ssl.check_interval_hours','24','SSL check frequency','integer'),
('ssl.expiry_thresholds_days','30,14,7,3','Alert thresholds','string'),
('notifications.cooldown_period_seconds','300','Cooldown between duplicate alerts','integer'),
('reports.archive_after_days','180','Keep reports for N days','integer'),
('system.version','1.0.0','Application version','string');

-----------------------------------------------------------------
-- 6. AUDIT & LOGGING
-----------------------------------------------------------------

CREATE TABLE audit_logs (
    audit_id   BIGSERIAL PRIMARY KEY,
    user_id    INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    action     TEXT NOT NULL CHECK (action IN ('CREATE','UPDATE','DELETE','LOGIN','LOGOUT')),
    table_name TEXT NOT NULL,
    row_id     INTEGER,
    old_data   JSONB,
    new_data   JSONB,
    ip_address TEXT,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_user   ON audit_logs(user_id);
CREATE INDEX idx_audit_table  ON audit_logs(table_name);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_ts     ON audit_logs(created_at);

-- user_audit_log -----------------------------------------------
CREATE TABLE user_audit_log (
    audit_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    action_type TEXT NOT NULL CHECK (action_type IN ('created', 'updated', 'approved', 'rejected', 'activated', 'deactivated', 'deleted', 'password_changed')),
    performed_by INTEGER REFERENCES users(user_id) ON DELETE SET NULL,
    old_values JSONB,
    new_values JSONB,
    notes TEXT,
    ip_address INET,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE user_audit_log IS 'Audit trail for all user management actions';
CREATE INDEX idx_user_audit_user ON user_audit_log(user_id);
CREATE INDEX idx_user_audit_action ON user_audit_log(action_type);
CREATE INDEX idx_user_audit_date ON user_audit_log(created_at);

CREATE TABLE system_logs (
    log_id    BIGSERIAL PRIMARY KEY,
    level     TEXT NOT NULL CHECK (level IN ('DEBUG','INFO','WARN','ERROR','FATAL')),
    message   TEXT NOT NULL,
    trace     TEXT,
    source    TEXT,
    metadata  JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_syslog_level ON system_logs(level);
CREATE INDEX idx_syslog_ts    ON system_logs(created_at);
CREATE INDEX idx_syslog_src   ON system_logs(source);

-----------------------------------------------------------------
-- 7. STATISTICS (pre-aggregated)
-----------------------------------------------------------------

CREATE TABLE service_statistics (
    stat_id            SERIAL PRIMARY KEY,
    service_id         INTEGER NOT NULL REFERENCES services(service_id) ON DELETE CASCADE,
    date               DATE NOT NULL,
    total_checks       INTEGER NOT NULL DEFAULT 0,
    successful_checks  INTEGER NOT NULL DEFAULT 0,
    failed_checks      INTEGER NOT NULL DEFAULT 0,
    uptime_percentage  NUMERIC(5,2),
    avg_response_time_ms INTEGER,
    min_response_time_ms INTEGER,
    max_response_time_ms INTEGER,
    p95_response_time_ms INTEGER,
    total_downtime_seconds INTEGER DEFAULT 0,
    incident_count     INTEGER DEFAULT 0,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stat_service_date UNIQUE (service_id, date)
);
CREATE INDEX idx_stats_service ON service_statistics(service_id);
CREATE INDEX idx_stats_date    ON service_statistics(date);

-----------------------------------------------------------------
-- 8. BACKGROUND JOB QUEUE
-----------------------------------------------------------------

CREATE TABLE job_queue (
    job_id       BIGSERIAL PRIMARY KEY,
    job_type     TEXT NOT NULL,
    payload      JSONB NOT NULL,
    priority     INTEGER NOT NULL DEFAULT 5 CHECK (priority BETWEEN 1 AND 10),
    status       TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','RETRYING')),
    attempts     INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    scheduled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at   TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_job_status     ON job_queue(status);
CREATE INDEX idx_job_scheduled  ON job_queue(scheduled_at);
CREATE INDEX idx_job_priority   ON job_queue(priority);
CREATE INDEX idx_job_type       ON job_queue(job_type);

-----------------------------------------------------------------
-- 9. VIEWS (dashboard / reporting)
-----------------------------------------------------------------

-- active services with today stats
CREATE OR REPLACE VIEW v_active_services AS
SELECT
    s.service_id,
    s.name,
    s.url,
    s.current_status,
    s.check_interval_seconds,
    s.last_checked_at,
    s.is_active,
    COALESCE(st.uptime_percentage,0) AS uptime_today,
    COALESCE(inc_today.cnt,0)        AS incidents_today
FROM services s
LEFT JOIN service_statistics st
    ON st.service_id = s.service_id AND st.date = CURRENT_DATE
LEFT JOIN (
    SELECT service_id, COUNT(*) AS cnt
    FROM incidents
    WHERE started_at >= CURRENT_DATE
    GROUP BY service_id
) inc_today ON inc_today.service_id = s.service_id
WHERE s.is_active AND NOT s.is_deleted;

-- 24-hour uptime per service
CREATE OR REPLACE VIEW v_service_uptime_24h AS
SELECT
    s.service_id,
    s.name,
    COUNT(uc.check_id) AS total_checks,
    SUM(CASE WHEN uc.status='UP' THEN 1 ELSE 0 END) AS successful_checks,
    ROUND(
        (SUM(CASE WHEN uc.status='UP' THEN 1 ELSE 0 END)::NUMERIC / NULLIF(COUNT(uc.check_id),0)) * 100,
        2
    ) AS uptime_percentage,
    ROUND(AVG(uc.response_time_ms)) AS avg_response_time_ms
FROM services s
LEFT JOIN uptime_checks uc
    ON uc.service_id = s.service_id
   AND uc.checked_at >= NOW() - INTERVAL '24 hours'
WHERE s.is_active AND NOT s.is_deleted
GROUP BY s.service_id, s.name;

-- SSL expiring within 30 days
CREATE OR REPLACE VIEW v_ssl_expiring_soon AS
SELECT
    s.service_id,
    s.name,
    s.url,
    ssl.domain,
    ssl.issuer,
    ssl.valid_to   AS expiry_date,
    ssl.days_remaining,
    ssl.last_checked_at
FROM services s
JOIN LATERAL (
    SELECT *
    FROM ssl_checks sc
    WHERE sc.service_id = s.service_id
    ORDER BY sc.last_checked_at DESC
    LIMIT 1
) ssl ON TRUE
WHERE s.is_active
  AND NOT s.is_deleted
  AND ssl.days_remaining <= 30
  AND ssl.days_remaining > 0
ORDER BY ssl.days_remaining;

-- recent incidents (last 7 days)
CREATE OR REPLACE VIEW v_recent_incidents AS
SELECT
    i.incident_id,
    s.service_id,
    s.name      AS service_name,
    s.url,
    i.started_at,
    i.recovered_at,
    i.duration_seconds,
    CASE
        WHEN i.duration_seconds IS NOT NULL THEN
            CONCAT(
                FLOOR(i.duration_seconds/3600),'h ',
                FLOOR((i.duration_seconds%3600)/60),'m ',
                (i.duration_seconds%60),'s'
            )
        ELSE 'Ongoing'
    END AS duration_formatted,
    i.is_resolved,
    i.error_message
FROM incidents i
JOIN services s ON i.service_id = s.service_id
WHERE i.started_at >= NOW() - INTERVAL '7 days'
ORDER BY i.started_at DESC;

-- dashboard summary
CREATE OR REPLACE VIEW v_dashboard_summary AS
SELECT
    (SELECT COUNT(*) FROM services WHERE is_active AND NOT is_deleted)                AS total_services,
    (SELECT COUNT(*) FROM services WHERE current_status='UP'   AND is_active AND NOT is_deleted) AS services_up,
    (SELECT COUNT(*) FROM services WHERE current_status='DOWN' AND is_active AND NOT is_deleted) AS services_down,
    (SELECT COUNT(*) FROM v_ssl_expiring_soon)                                        AS ssl_warnings,
    (SELECT ROUND(AVG(uptime_percentage),2) FROM v_service_uptime_24h)               AS avg_uptime_24h,
    (SELECT COUNT(*) FROM incidents WHERE started_at >= CURRENT_DATE)                AS incidents_today,
    (SELECT ROUND(AVG(response_time_ms),0)
     FROM uptime_checks
     WHERE checked_at >= NOW() - INTERVAL '24 hours' AND status='UP')               AS avg_response_time_ms;

-- pending user approvals
CREATE OR REPLACE VIEW pending_user_approvals AS
SELECT 
    u.user_id,
    u.first_name,
    u.middle_name,
    u.last_name,
    u.email,
    u.username,
    u.phone_number,
    r.role_name,
    b.name as branch_name,
    u.created_at,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - u.created_at))/3600 as hours_pending
FROM users u
LEFT JOIN roles r ON u.role_id = r.role_id
LEFT JOIN branches b ON u.branch_id = b.branch_id
WHERE u.is_approved = FALSE 
  AND u.is_deleted = FALSE
  AND u.password_hash IS NULL  -- Only show truly pending users
ORDER BY u.created_at ASC;
COMMENT ON VIEW pending_user_approvals IS 'Users awaiting admin approval';

-----------------------------------------------------------------
-- 10. FUNCTIONS & TRIGGERS
-----------------------------------------------------------------

-- auto-update updated_at
CREATE OR REPLACE FUNCTION fn_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- apply to every table with updated_at
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname='public' AND tablename <> 'pg_catalog'
          AND EXISTS (SELECT 1 FROM information_schema.columns c
                      WHERE c.table_name=pg_tables.tablename AND c.column_name='updated_at')
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%I_upd
             BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();',
            r.tablename, r.tablename);
    END LOOP;
END;
$$;

-- incident duration
CREATE OR REPLACE FUNCTION fn_calc_incident_duration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.recovered_at IS NOT NULL AND NEW.started_at IS NOT NULL THEN
        NEW.duration_seconds = EXTRACT(EPOCH FROM (NEW.recovered_at - NEW.started_at))::INTEGER;
        NEW.is_resolved = TRUE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_incidents_duration
    BEFORE INSERT OR UPDATE ON incidents
    FOR EACH ROW EXECUTE FUNCTION fn_calc_incident_duration();

-- soft-delete services
CREATE OR REPLACE FUNCTION fn_soft_delete_service()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_deleted AND NOT OLD.is_deleted THEN
        NEW.deleted_at = CURRENT_TIMESTAMP;
        NEW.is_active  = FALSE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_services_softdel
    BEFORE UPDATE ON services
    FOR EACH ROW EXECUTE FUNCTION fn_soft_delete_service();

-- soft-delete users
CREATE OR REPLACE FUNCTION fn_soft_delete_user()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_deleted AND NOT OLD.is_deleted THEN
        NEW.deleted_at = CURRENT_TIMESTAMP;
        NEW.is_active  = FALSE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_softdel
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_soft_delete_user();

-- user audit logging
CREATE OR REPLACE FUNCTION log_user_changes()
RETURNS TRIGGER AS $
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO user_audit_log (user_id, action_type, new_values)
        VALUES (NEW.user_id, 'created', row_to_json(NEW)::jsonb);
    ELSIF TG_OP = 'UPDATE' THEN
        -- Detect specific changes
        IF OLD.is_approved = FALSE AND NEW.is_approved = TRUE THEN
            INSERT INTO user_audit_log (user_id, action_type, performed_by, old_values, new_values, notes)
            VALUES (NEW.user_id, 'approved', NEW.approved_by, 
                    jsonb_build_object('is_approved', OLD.is_approved),
                    jsonb_build_object('is_approved', NEW.is_approved),
                    NEW.approval_notes);
        ELSIF OLD.is_active != NEW.is_active THEN
            INSERT INTO user_audit_log (user_id, action_type, old_values, new_values)
            VALUES (NEW.user_id, 
                    CASE WHEN NEW.is_active THEN 'activated' ELSE 'deactivated' END,
                    jsonb_build_object('is_active', OLD.is_active),
                    jsonb_build_object('is_active', NEW.is_active));
        ELSIF OLD.password_hash != NEW.password_hash THEN
            INSERT INTO user_audit_log (user_id, action_type)
            VALUES (NEW.user_id, 'password_changed');
        ELSE
            INSERT INTO user_audit_log (user_id, action_type, old_values, new_values)
            VALUES (NEW.user_id, 'updated', 
                    row_to_json(OLD)::jsonb,
                    row_to_json(NEW)::jsonb);
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO user_audit_log (user_id, action_type, old_values)
        VALUES (OLD.user_id, 'deleted', row_to_json(OLD)::jsonb);
    END IF;
    RETURN NEW;
END;
$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_audit
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW
    EXECUTE FUNCTION log_user_changes();

-----------------------------------------------------------------
-- 11. CLEANUP PROCEDURES
-----------------------------------------------------------------

CREATE OR REPLACE FUNCTION cleanup_old_uptime_checks(p_keep_days INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE deleted INTEGER;
BEGIN
    DELETE FROM uptime_checks WHERE checked_at < NOW() - (p_keep_days || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted = ROW_COUNT;
    INSERT INTO system_logs(level,message,source) VALUES
        ('INFO',format('Cleaned %s old uptime checks',deleted),'cleanup_old_uptime_checks');
    RETURN deleted;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_old_notifications(p_keep_days INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE deleted INTEGER;
BEGIN
    DELETE FROM notifications WHERE created_at < NOW() - (p_keep_days || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted = ROW_COUNT;
    INSERT INTO system_logs(level,message,source) VALUES
        ('INFO',format('Cleaned %s old notifications',deleted),'cleanup_old_notifications');
    RETURN deleted;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_expired_refresh_tokens()
RETURNS INTEGER AS $$
DECLARE deleted INTEGER;
BEGIN
    DELETE FROM refresh_tokens WHERE expires_at < NOW() OR is_revoked;
    GET DIAGNOSTICS deleted = ROW_COUNT;
    INSERT INTO system_logs(level,message,source) VALUES
        ('INFO',format('Cleaned %s expired/revoked tokens',deleted),'cleanup_expired_refresh_tokens');
    RETURN deleted;
END;
$$ LANGUAGE plpgsql;

-----------------------------------------------------------------
-- 12. SAMPLE DATA (admin user - **CHANGE HASH BEFORE PROD**)
-----------------------------------------------------------------

-- Insert initial reference data
INSERT INTO genders (gender, description) VALUES
    ('male',   'Male'),
    ('female', 'Female'),
    ('other',  'Other / Non-Binary');

INSERT INTO roles (role_name, description) VALUES
    ('admin',    'Full system access - users, services, groups, templates, settings'),
    ('operator', 'Operational access - services, reports, dashboards'),
    ('viewer',   'Read-only dashboards & reports');

INSERT INTO countries (country_code, name) VALUES
    ('KE','Kenya'),('UG','Uganda'),('TZ','Tanzania'),('RW','Rwanda'),('BI','Burundi'),
    ('SS','South Sudan'),('SO','Somalia'),('DJ','Djibouti'),('ER','Eritrea');

INSERT INTO branches (name, code, is_active) VALUES ('Headquarters','HQ',TRUE);

INSERT INTO notification_channels (channel, description, is_enabled) VALUES
    ('email',    'SMTP email',      TRUE),
    ('telegram', 'Telegram Bot',    TRUE),
    ('sms',      'SMS via provider',FALSE);

INSERT INTO event_types (event_type, description) VALUES
    ('SERVICE_DOWN',       'Service went DOWN'),
    ('SERVICE_RECOVERED',  'Service recovered'),
    ('SSL_EXPIRY_30',      'SSL expires in 30 days'),
    ('SSL_EXPIRY_14',      'SSL expires in 14 days'),
    ('SSL_EXPIRY_7',       'SSL expires in 7 days'),
    ('SSL_EXPIRY_3',       'SSL expires in 3 days');

INSERT INTO report_types (report_type, description) VALUES
    ('daily',   'Daily uptime summary'),
    ('weekly',  'Weekly uptime & performance'),
    ('monthly', 'Monthly comprehensive report'),
    ('custom',  'Custom date range');

INSERT INTO settings (key, value, description, data_type) VALUES
('monitoring.default_interval_seconds','300','Default ping interval','integer'),
('monitoring.max_retries','3','Default retries before DOWN','integer'),
('monitoring.retry_delay_ms','5000','Delay between retries','integer'),
('monitoring.timeout_ms','10000','HTTP timeout','integer'),
('ssl.check_interval_hours','24','SSL check frequency','integer'),
('ssl.expiry_thresholds_days','30,14,7,3','Alert thresholds','string'),
('notifications.cooldown_period_seconds','300','Cooldown between duplicate alerts','integer'),
('reports.archive_after_days','180','Keep reports for N days','integer'),
('system.version','1.0.0','Application version','string');

-- Admin user (password placeholder - generate Argon2id hash in app)
INSERT INTO users (role_id, first_name, last_name, username, email, password_hash, is_active, is_approved, approved_at)
VALUES (
    (SELECT role_id FROM roles WHERE role_name='admin'),
    'System','Administrator','admin','cnjmtechnologiesinc@gmail.com',
    '$argon2id$v=19$m=65536,t=3,p=1$PLACEHOLDER$CHANGE_IN_PRODUCTION',
    TRUE, TRUE, CURRENT_TIMESTAMP
);

-- Default notification templates (use {{var}} placeholders)
INSERT INTO notification_templates (name, event_type, channel, subject_template, body_template) VALUES
('Service Down - Email',      'SERVICE_DOWN',      'email',
 'ALERT: {{service_name}} is DOWN',
 'Service: {{service_name}}\nURL: {{service_url}}\nStatus: DOWN\nTime: {{down_time}}\nError: {{error_message}}\nHTTP: {{http_code}}'),

('Service Recovered - Email', 'SERVICE_RECOVERED', 'email',
 'RESOLVED: {{service_name}} is UP',
 'Service: {{service_name}}\nURL: {{service_url}}\nStatus: RECOVERED\nDowntime: {{downtime_duration}}\nRecovered: {{recovered_time}}'),

('SSL Expiry - Email',        'SSL_EXPIRY_30',     'email',
 'WARNING: SSL expires soon - {{service_name}}',
 'Service: {{service_name}}\nDomain: {{domain}}\nDays left: {{days_remaining}}\nExpires: {{expiry_date}}\nIssuer: {{issuer}}');

-- Demo contact groups & members
INSERT INTO contact_groups (name, description, created_by) VALUES
('DevOps Team','Primary technical contacts',(SELECT user_id FROM users WHERE username='admin')),
('Management','Executive alerts',(SELECT user_id FROM users WHERE username='admin'));

INSERT INTO contact_members (group_id, name, email, telegram_handle) VALUES
(1,'Camresh James','camreshjames@gmail.com','@camresh');

-----------------------------------------------------------------
-- 13. PERFORMANCE INDEXES (extra)
-----------------------------------------------------------------

CREATE INDEX idx_uptime_service_status_date ON uptime_checks(service_id, status, checked_at DESC);
CREATE INDEX idx_incidents_service_resolved ON incidents(service_id, is_resolved, started_at DESC);
CREATE INDEX idx_notif_service_event_date   ON notifications(service_id, event_type, created_at DESC);
CREATE INDEX idx_services_active_notdel    ON services(service_id) WHERE is_active AND NOT is_deleted;
-- Removed time-based partial index (NOW() is not immutable)
-- CREATE INDEX idx_uptime_recent_up          ON uptime_checks(service_id, checked_at DESC) WHERE status='UP';
CREATE INDEX idx_notif_pending             ON notifications(notification_id, created_at) WHERE delivery_status='PENDING';

-----------------------------------------------------------------
-- 14. PERMISSIONS (execute as super-user)
-----------------------------------------------------------------

-- Create application roles
-- CREATE ROLE januscope_app LOGIN PASSWORD 'STRONG_PASSWORD';
-- CREATE ROLE januscope_readonly LOGIN PASSWORD 'STRONG_PASSWORD';

-- Database connection
-- GRANT CONNECT ON DATABASE januscope TO januscope_app, januscope_readonly;
-- GRANT USAGE ON SCHEMA public TO januscope_app, januscope_readonly;

-- Application role (januscope_app) - Full CRUD access
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO januscope_app;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO januscope_app;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO januscope_app;

-- Views access for application
-- GRANT SELECT ON v_active_services TO januscope_app;
-- GRANT SELECT ON v_service_uptime_24h TO januscope_app;
-- GRANT SELECT ON v_ssl_expiring_soon TO januscope_app;
-- GRANT SELECT ON v_recent_incidents TO januscope_app;
-- GRANT SELECT ON v_dashboard_summary TO januscope_app;
-- GRANT SELECT ON pending_user_approvals TO januscope_app;

-- Read-only role (januscope_readonly) - SELECT only
-- GRANT SELECT ON ALL TABLES IN SCHEMA public TO januscope_readonly;
-- GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO januscope_readonly;
-- GRANT SELECT ON v_active_services TO januscope_readonly;
-- GRANT SELECT ON v_service_uptime_24h TO januscope_readonly;
-- GRANT SELECT ON v_ssl_expiring_soon TO januscope_readonly;
-- GRANT SELECT ON v_recent_incidents TO januscope_readonly;
-- GRANT SELECT ON v_dashboard_summary TO januscope_readonly;
-- GRANT SELECT ON pending_user_approvals TO januscope_readonly;

-- Default privileges for future objects
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO januscope_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO januscope_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO januscope_app;
-- ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO januscope_readonly;

-- Specific function execution permissions
-- GRANT EXECUTE ON FUNCTION cleanup_old_uptime_checks(INTEGER) TO januscope_app;
-- GRANT EXECUTE ON FUNCTION cleanup_old_notifications(INTEGER) TO januscope_app;
-- GRANT EXECUTE ON FUNCTION cleanup_expired_refresh_tokens() TO januscope_app;

-- Revoke public schema access (security hardening)
-- REVOKE ALL ON SCHEMA public FROM PUBLIC;
-- REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;

-----------------------------------------------------------------
-- 15. QUICK SETUP SCRIPT (for development)
-----------------------------------------------------------------

-- Uncomment and run this block to quickly set up permissions:
/*
-- Create roles
CREATE ROLE januscope_app LOGIN PASSWORD 'januscope_dev_password_2024';
CREATE ROLE januscope_readonly LOGIN PASSWORD 'januscope_readonly_2024';

-- Grant database access
GRANT CONNECT ON DATABASE januscope TO januscope_app, januscope_readonly;
GRANT USAGE ON SCHEMA public TO januscope_app, januscope_readonly;

-- Application role permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO januscope_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO januscope_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO januscope_app;

-- Views
GRANT SELECT ON v_active_services, v_service_uptime_24h, v_ssl_expiring_soon, 
             v_recent_incidents, v_dashboard_summary, pending_user_approvals 
TO januscope_app;

-- Read-only role permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO januscope_readonly;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO januscope_readonly;
GRANT SELECT ON v_active_services, v_service_uptime_24h, v_ssl_expiring_soon, 
             v_recent_incidents, v_dashboard_summary, pending_user_approvals 
TO januscope_readonly;

-- Default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO januscope_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO januscope_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO januscope_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO januscope_readonly;

-- Security hardening
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
*/

-----------------------------------------------------------------
-- END OF SCHEMA
-----------------------------------------------------------------