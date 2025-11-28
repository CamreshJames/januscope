# Januscope - Uptime & SSL Monitoring System

Januscope is an enterprise-grade monitoring system for tracking service uptime and SSL certificate expiration. Built with a custom engine-based architecture, it provides real-time monitoring, alerting, and comprehensive reporting capabilities.

## Architecture Overview

### Engine-Based Design

Januscope uses a modular engine architecture where each major component is implemented as an independent engine that can be started, stopped, and monitored independently. This design provides:

- **Modularity**: Each engine handles a specific concern (database, monitoring, notifications, jobs, security)
- **Lifecycle Management**: Engines have well-defined initialization, start, and stop phases
- **Health Monitoring**: Each engine reports its health status independently
- **Loose Coupling**: Engines communicate through a shared context, reducing direct dependencies

### Core Engines

1. **DatabaseEngine**: Manages PostgreSQL connections using HikariCP connection pooling
2. **MonitoringEngine**: Orchestrates uptime and SSL certificate checks with concurrent worker threads
3. **NotificationEngine**: Handles multi-channel notifications (Email, Telegram, Console)
4. **JobEngine**: Manages scheduled background tasks using cron expressions
5. **SecurityEngine**: Provides encryption/decryption for sensitive configuration data
6. **UndertowServer**: Lightweight HTTP server for REST API endpoints

### Key Features

- Real-time service uptime monitoring with configurable check intervals
- SSL certificate expiration tracking with advance warnings
- Multi-channel notification system (Email, Telegram)
- JWT-based authentication with role-based access control
- User approval workflow with auto-generated credentials
- Bulk import/export for services and contact groups (JSON, XML, CSV, Excel)
- Comprehensive dashboard with charts and analytics
- Incident tracking and alerting
- Configuration encryption for sensitive data

## Configuration

The application is configured through `config/application.xml`. All configuration can be encrypted for security.

### Configuration Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<januscope-config version="1.0">
    
    <!-- Database Configuration -->
    <database>
        <url status="plaintext">jdbc:postgresql://localhost:5432/januscope</url>
        <username status="plaintext">januscope_user</username>
        <password status="plaintext">your_password</password>
        <pool>
            <maxPoolSize>20</maxPoolSize>
            <minIdle>5</minIdle>
            <connectionTimeout>30000</connectionTimeout>
        </pool>
    </database>
    
    <!-- Security Configuration -->
    <security>
        <jwt>
            <secret status="plaintext">your-jwt-secret-key-min-32-chars</secret>
            <accessTokenExpiry>3600</accessTokenExpiry>
            <refreshTokenExpiry>604800</refreshTokenExpiry>
        </jwt>
        <password status="plaintext">master-encryption-password</password>
    </security>
    
    <!-- Monitoring Configuration -->
    <monitoring>
        <defaults>
            <checkInterval>300</checkInterval>
            <timeout>10000</timeout>
            <maxRetries>3</maxRetries>
            <retryDelay>5000</retryDelay>
        </defaults>
        <ssl>
            <checkInterval>86400</checkInterval>
            <expiryThresholds>30,14,7,3</expiryThresholds>
        </ssl>
        <threadPool>
            <coreSize>10</coreSize>
            <maxSize>50</maxSize>
        </threadPool>
    </monitoring>
    
    <!-- Notification Configuration -->
    <notification>
        <channels>
            <email enabled="true">
                <smtp>
                    <host>smtp.gmail.com</host>
                    <port>587</port>
                    <username status="plaintext">your-email@gmail.com</username>
                    <password status="plaintext">your-app-password</password>
                    <tls>true</tls>
                    <from>noreply@januscope.com</from>
                </smtp>
            </email>
            <telegram enabled="false">
                <botToken status="plaintext">your-bot-token</botToken>
                <chatId>your-chat-id</chatId>
            </telegram>
            <console enabled="true"/>
        </channels>
        <cooldown>
            <defaultPeriod>300</defaultPeriod>
            <perEventType>
                <SERVICE_DOWN>600</SERVICE_DOWN>
                <SSL_EXPIRY_30>86400</SSL_EXPIRY_30>
            </perEventType>
        </cooldown>
    </notification>
    
    <!-- Job Scheduling Configuration -->
    <jobs>
        <scheduler>
            <threadPoolSize>5</threadPoolSize>
        </scheduler>
        <job name="uptime-checker" enabled="true">
            <schedule>*/5 * * * *</schedule>
            <description>Checks uptime of all monitored services</description>
        </job>
        <job name="ssl-checker" enabled="true">
            <schedule>0 */6 * * *</schedule>
            <description>Checks SSL certificates for expiry</description>
        </job>
        <job name="cleanup" enabled="true">
            <schedule>0 2 * * *</schedule>
            <description>Cleans up old data</description>
        </job>
    </jobs>
    
    <!-- Services to Monitor -->
    <services>
        <service>
            <name>Example Service</name>
            <url status="plaintext">https://example.com</url>
            <checkInterval>300</checkInterval>
            <timeout>10000</timeout>
            <maxRetries>3</maxRetries>
            <enabled>true</enabled>
        </service>
    </services>
    
    <!-- Contact Groups -->
    <contactGroups>
        <group>
            <name>DevOps Team</name>
            <description>Primary technical contacts</description>
            <members>
                <member>
                    <name>Admin User</name>
                    <email>admin@example.com</email>
                    <telegram>@admin</telegram>
                </member>
            </members>
        </group>
    </contactGroups>
    
    <!-- Server Configuration -->
    <server>
        <host>0.0.0.0</host>
        <port>9876</port>
        <api>
            <version>v1</version>
            <basePath>/api</basePath>
        </api>
        <auth>
            <enabled>true</enabled>
            <publicEndpoints>
                <endpoint>/</endpoint>
                <endpoint>/api/health</endpoint>
                <endpoint>/api/v1/health</endpoint>
                <endpoint>/api/v1/auth/login</endpoint>
                <endpoint>/api/v1/auth/register</endpoint>
                <endpoint>/api/v1/auth/refresh</endpoint>
                <endpoint>/api/v1/auth/forgot-password</endpoint>
                <endpoint>/api/v1/auth/reset-password</endpoint>
            </publicEndpoints>
        </auth>
    </server>
    
    <!-- Encryption Configuration -->
    <encryption>
        <masterPassword enabled="false" status="plaintext"></masterPassword>
        <apiEncryption>
            <enabled>false</enabled>
            <key status="plaintext"></key>
            <mode>whitelist</mode>
            <whitelist>
                <endpoint>/api/v1/auth/login</endpoint>
                <endpoint>/api/v1/auth/register</endpoint>
            </whitelist>
        </apiEncryption>
    </encryption>
    
    <!-- Email Template Configuration -->
    <emailTemplates>
        <cacheEnabled>true</cacheEnabled>
        <templatePath>/email-templates/</templatePath>
        <defaultSender>
            <name>Januscope Monitoring System</name>
            <email>noreply@januscope.com</email>
        </defaultSender>
        <templates>
            <template name="welcome" file="welcome.html" subject="Welcome to Januscope"/>
            <template name="registration-pending" file="registration-pending.html" subject="Registration Received"/>
            <template name="alert-notification" file="alert-notification.html" subject="Service Alert"/>
            <template name="ssl-expiry-warning" file="ssl-expiry-warning.html" subject="SSL Certificate Expiring Soon"/>
            <template name="password-reset" file="password-reset.html" subject="Password Reset Request"/>
        </templates>
    </emailTemplates>
    
</januscope-config>
```

### Configuration Encryption

Sensitive values can be encrypted using the ConfigEncryptionTool:

```bash
java -cp target/Main-1.0.0-fat.jar ke.skyworld.januscope.utils.ConfigEncryptionTool
```

Change `status="plaintext"` to `status="encrypted"` and replace the value with the encrypted output.

## Building and Running

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- PostgreSQL 12 or higher

### Database Setup

1. Create database and user:
```sql
CREATE DATABASE januscope;
CREATE USER januscope_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE januscope TO januscope_user;
```

2. Run schema from `database/DATABASE_SCHEMA.sql`

### Build

```bash
cd januscope
mvn clean package
```

### Run

```bash
java -jar target/Main-1.0.0-fat.jar
```

The API server will start on `http://localhost:9876`

## API Endpoints

### Authentication
- POST `/api/v1/auth/register` - Register new user (pending approval)
- POST `/api/v1/auth/login` - Login and get JWT tokens
- POST `/api/v1/auth/refresh` - Refresh access token
- POST `/api/v1/auth/forgot-password` - Request password reset
- POST `/api/v1/auth/reset-password` - Reset password with token
- GET `/api/v1/auth/me` - Get current user profile

### Services
- GET `/api/v1/services` - List all monitored services
- POST `/api/v1/services` - Add new service
- GET `/api/v1/services/{id}` - Get service details
- PUT `/api/v1/services/{id}` - Update service
- DELETE `/api/v1/services/{id}` - Remove service
- GET `/api/v1/services/{id}/uptime` - Get uptime history
- GET `/api/v1/services/{id}/ssl` - Get SSL certificate info

### Bulk Operations
- POST `/api/v1/bulk/services/import` - Bulk import services (JSON/XML/CSV/Excel)
- POST `/api/v1/bulk/import` - Bulk import contact groups
- GET `/api/v1/bulk/export?format=json` - Export data

### User Management
- GET `/api/v1/users` - List users
- POST `/api/v1/users` - Create user (admin)
- GET `/api/v1/users/pending` - List pending approvals
- POST `/api/v1/users/{id}/approve` - Approve user registration
- POST `/api/v1/users/{id}/reject` - Reject user registration

### System Administration
- GET `/api/v1/system/roles` - Manage roles
- GET `/api/v1/system/countries` - Manage countries
- GET `/api/v1/system/branches` - Manage branches
- GET `/api/v1/system/locations` - Manage locations
- GET `/api/v1/system/templates` - Manage notification templates

## Technology Stack

### Backend
- Java 17
- Undertow (Lightweight HTTP server)
- HikariCP (Connection pooling)
- PostgreSQL (Database)
- JJWT (JWT tokens)
- Apache Commons CSV (CSV processing)
- Apache POI (Excel processing)
- JavaMail (Email notifications)

### Frontend
- React 18 with TypeScript
- TanStack Router (File-based routing)
- TanStack Query (Data fetching)
- Chart.js (Analytics)
- Custom Form Engine
- Custom Table Engine

## License

Copyright 2025 Sky World Limited. All rights reserved.


