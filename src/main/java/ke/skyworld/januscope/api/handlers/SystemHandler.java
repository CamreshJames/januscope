package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.api.server.JsonUtil;
import ke.skyworld.januscope.domain.models.*;
import ke.skyworld.januscope.domain.repositories.SystemRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Handler for system/admin reference data APIs
 */
public class SystemHandler extends BaseHandler {
    private final SystemRepository systemRepository;
    private final FormParserFactory formParserFactory;

    public SystemHandler(SystemRepository systemRepository) {
        this.systemRepository = systemRepository;
        this.formParserFactory = FormParserFactory.builder().build();
    }

    @Override
    protected void handleGet(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.contains("/roles")) {
            if (path.matches(".*/roles/\\d+$")) {
                getRoleById(exchange);
            } else {
                getAllRoles(exchange);
            }
        } else if (path.contains("/countries")) {
            if (path.matches(".*/countries/[^/]+$")) {
                getCountryByCode(exchange);
            } else {
                getAllCountries(exchange);
            }
        } else if (path.contains("/branches")) {
            if (path.matches(".*/branches/\\d+$")) {
                getBranchById(exchange);
            } else {
                getAllBranches(exchange);
            }
        } else if (path.contains("/locations")) {
            if (path.matches(".*/locations/\\d+$")) {
                getLocationById(exchange);
            } else {
                getAllLocations(exchange);
            }
        } else if (path.contains("/templates")) {
            if (path.matches(".*/templates/\\d+$")) {
                getTemplateById(exchange);
            } else {
                getAllTemplates(exchange);
            }
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }

    @Override
    protected void handlePost(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.contains("/roles") && !path.matches(".*/roles/\\d+$")) {
            createRole(exchange);
        } else if (path.contains("/countries") && path.endsWith("/bulk-import")) {
            bulkImportCountries(exchange);
        } else if (path.contains("/countries") && !path.matches(".*/countries/[^/]+$")) {
            createCountry(exchange);
        } else if (path.contains("/branches") && path.endsWith("/bulk-import")) {
            bulkImportBranches(exchange);
        } else if (path.contains("/branches") && !path.matches(".*/branches/\\d+$")) {
            createBranch(exchange);
        } else if (path.contains("/locations") && path.endsWith("/bulk-import")) {
            bulkImportLocations(exchange);
        } else if (path.contains("/locations") && !path.matches(".*/locations/\\d+$")) {
            createLocation(exchange);
        } else if (path.contains("/templates") && !path.matches(".*/templates/\\d+$")) {
            createTemplate(exchange);
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }

    @Override
    protected void handlePut(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.contains("/roles")) {
            updateRole(exchange);
        } else if (path.contains("/countries")) {
            updateCountry(exchange);
        } else if (path.contains("/branches")) {
            updateBranch(exchange);
        } else if (path.contains("/locations")) {
            updateLocation(exchange);
        } else if (path.contains("/templates")) {
            updateTemplate(exchange);
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }

    @Override
    protected void handleDelete(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.contains("/roles")) {
            deleteRole(exchange);
        } else if (path.contains("/countries")) {
            deleteCountry(exchange);
        } else if (path.contains("/branches")) {
            deleteBranch(exchange);
        } else if (path.contains("/locations")) {
            deleteLocation(exchange);
        } else if (path.contains("/templates")) {
            deleteTemplate(exchange);
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }

    private String extractIdFromPath(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    // ==================== ROLES ====================
    private void getAllRoles(HttpServerExchange exchange) {
        try {
            List<Role> roles = systemRepository.getAllRoles();
            sendSuccess(exchange, roles);
        } catch (Exception e) {
            logger.error("Failed to get roles", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch roles: " + e.getMessage());
        }
    }

    private void getRoleById(HttpServerExchange exchange) {
        try {
            Integer roleId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            Optional<Role> role = systemRepository.getRoleById(roleId);
            
            if (role.isPresent()) {
                sendSuccess(exchange, role.get());
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Role not found");
            }
        } catch (Exception e) {
            logger.error("Failed to get role", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch role: " + e.getMessage());
        }
    }

    private void createRole(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Role role = JsonUtil.fromJson(body, Role.class);
            
            Role created = systemRepository.createRole(role);
            sendSuccess(exchange, created, "Role created successfully");
        } catch (Exception e) {
            logger.error("Failed to create role", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create role: " + e.getMessage());
        }
    }

    private void updateRole(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Role role = JsonUtil.fromJson(body, Role.class);
            
            Role updated = systemRepository.updateRole(role);
            sendSuccess(exchange, updated, "Role updated successfully");
        } catch (Exception e) {
            logger.error("Failed to update role", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update role: " + e.getMessage());
        }
    }

    private void deleteRole(HttpServerExchange exchange) {
        try {
            Integer roleId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            boolean deleted = systemRepository.deleteRole(roleId);
            
            if (deleted) {
                sendSuccess(exchange, null, "Role deleted successfully");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Role not found");
            }
        } catch (Exception e) {
            logger.error("Failed to delete role", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete role: " + e.getMessage());
        }
    }

    // ==================== COUNTRIES ====================
    private void getAllCountries(HttpServerExchange exchange) {
        try {
            List<Country> countries = systemRepository.getAllCountries();
            sendSuccess(exchange, countries);
        } catch (Exception e) {
            logger.error("Failed to get countries", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch countries: " + e.getMessage());
        }
    }

    private void getCountryByCode(HttpServerExchange exchange) {
        try {
            String countryCode = extractIdFromPath(exchange.getRequestPath());
            Optional<Country> country = systemRepository.getCountryByCode(countryCode);
            
            if (country.isPresent()) {
                sendSuccess(exchange, country.get());
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Country not found");
            }
        } catch (Exception e) {
            logger.error("Failed to get country", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch country: " + e.getMessage());
        }
    }

    private void createCountry(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Country country = JsonUtil.fromJson(body, Country.class);
            
            Country created = systemRepository.createCountry(country);
            sendSuccess(exchange, created, "Country created successfully");
        } catch (Exception e) {
            logger.error("Failed to create country", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create country: " + e.getMessage());
        }
    }

    private void updateCountry(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Country country = JsonUtil.fromJson(body, Country.class);
            
            Country updated = systemRepository.updateCountry(country);
            sendSuccess(exchange, updated, "Country updated successfully");
        } catch (Exception e) {
            logger.error("Failed to update country", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update country: " + e.getMessage());
        }
    }

    private void deleteCountry(HttpServerExchange exchange) {
        try {
            String countryCode = extractIdFromPath(exchange.getRequestPath());
            boolean deleted = systemRepository.deleteCountry(countryCode);
            
            if (deleted) {
                sendSuccess(exchange, null, "Country deleted successfully");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Country not found");
            }
        } catch (Exception e) {
            logger.error("Failed to delete country", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete country: " + e.getMessage());
        }
    }

    private void bulkImportCountries(HttpServerExchange exchange) {
        try {
            // Handle file upload
            if (exchange.isInIoThread()) {
                exchange.dispatch(this::bulkImportCountries);
                return;
            }
            exchange.startBlocking();
            
            FormDataParser parser = formParserFactory.createParser(exchange);
            if (parser == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Content-Type must be multipart/form-data");
                return;
            }
            
            FormData formData = parser.parseBlocking();
            FormData.FormValue fileValue = formData.getFirst("file");
            
            if (fileValue == null || !fileValue.isFileItem()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "No file uploaded");
                return;
            }
            
            // Read CSV file
            byte[] fileData;
            try (InputStream is = fileValue.getFileItem().getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                fileData = baos.toByteArray();
            }
            
            // Parse CSV
            List<Country> countries = new ArrayList<>();
            int rowNumber = 1; // Start at 1 for header
            try (CSVParser csvParser = CSVParser.parse(
                    new InputStreamReader(new ByteArrayInputStream(fileData), StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build())) {
                
                for (CSVRecord record : csvParser) {
                    rowNumber++;
                    
                    // Validate required fields
                    String countryCode = record.get("countryCode");
                    String name = record.get("name");
                    
                    if (countryCode == null || countryCode.trim().isEmpty()) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": 'countryCode' is required");
                    }
                    
                    if (name == null || name.trim().isEmpty()) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": 'name' is required");
                    }
                    
                    Country country = new Country();
                    country.setCountryCode(countryCode.trim());
                    country.setName(name.trim());
                    countries.add(country);
                }
            }
            
            int count = systemRepository.bulkInsertCountries(countries);
            
            Map<String, Object> response = new HashMap<>();
            response.put("imported", count);
            response.put("message", count + " countries imported successfully");
            
            sendSuccess(exchange, response);
        } catch (Exception e) {
            logger.error("Failed to bulk import countries", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to bulk import countries: " + e.getMessage());
        }
    }

    // ==================== BRANCHES ====================
    private void getAllBranches(HttpServerExchange exchange) {
        try {
            List<Branch> branches = systemRepository.getAllBranches();
            sendSuccess(exchange, branches);
        } catch (Exception e) {
            logger.error("Failed to get branches", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch branches: " + e.getMessage());
        }
    }

    private void getBranchById(HttpServerExchange exchange) {
        try {
            Integer branchId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            Optional<Branch> branch = systemRepository.getBranchById(branchId);
            
            if (branch.isPresent()) {
                sendSuccess(exchange, branch.get());
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Branch not found");
            }
        } catch (Exception e) {
            logger.error("Failed to get branch", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch branch: " + e.getMessage());
        }
    }

    private void createBranch(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Branch branch = JsonUtil.fromJson(body, Branch.class);
            
            Branch created = systemRepository.createBranch(branch);
            sendSuccess(exchange, created, "Branch created successfully");
        } catch (Exception e) {
            logger.error("Failed to create branch", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create branch: " + e.getMessage());
        }
    }

    private void updateBranch(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Branch branch = JsonUtil.fromJson(body, Branch.class);
            
            Branch updated = systemRepository.updateBranch(branch);
            sendSuccess(exchange, updated, "Branch updated successfully");
        } catch (Exception e) {
            logger.error("Failed to update branch", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update branch: " + e.getMessage());
        }
    }

    private void deleteBranch(HttpServerExchange exchange) {
        try {
            Integer branchId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            boolean deleted = systemRepository.deleteBranch(branchId);
            
            if (deleted) {
                sendSuccess(exchange, null, "Branch deleted successfully");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Branch not found");
            }
        } catch (Exception e) {
            logger.error("Failed to delete branch", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete branch: " + e.getMessage());
        }
    }

    private void bulkImportBranches(HttpServerExchange exchange) {
        try {
            // Handle file upload
            if (exchange.isInIoThread()) {
                exchange.dispatch(this::bulkImportBranches);
                return;
            }
            exchange.startBlocking();
            
            FormDataParser parser = formParserFactory.createParser(exchange);
            if (parser == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Content-Type must be multipart/form-data");
                return;
            }
            
            FormData formData = parser.parseBlocking();
            FormData.FormValue fileValue = formData.getFirst("file");
            
            if (fileValue == null || !fileValue.isFileItem()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "No file uploaded");
                return;
            }
            
            // Read CSV file
            byte[] fileData;
            try (InputStream is = fileValue.getFileItem().getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                fileData = baos.toByteArray();
            }
            
            // Parse CSV
            List<Branch> branches = new ArrayList<>();
            int rowNumber = 1; // Start at 1 for header
            try (CSVParser csvParser = CSVParser.parse(
                    new InputStreamReader(new ByteArrayInputStream(fileData), StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build())) {
                
                for (CSVRecord record : csvParser) {
                    rowNumber++;
                    
                    // Validate required fields
                    String code = record.get("code");
                    String name = record.get("name");
                    
                    if (code == null || code.trim().isEmpty()) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": 'code' is required");
                    }
                    
                    if (name == null || name.trim().isEmpty()) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": 'name' is required");
                    }
                    
                    Branch branch = new Branch();
                    branch.setCode(code.trim());
                    branch.setName(name.trim());
                    
                    if (record.isMapped("countryCode") && !record.get("countryCode").isEmpty()) {
                        branch.setCountryCode(record.get("countryCode").trim());
                    }
                    
                    branches.add(branch);
                }
            }
            
            int count = systemRepository.bulkInsertBranches(branches);
            
            Map<String, Object> response = new HashMap<>();
            response.put("imported", count);
            response.put("message", count + " branches imported successfully");
            
            sendSuccess(exchange, response);
        } catch (Exception e) {
            logger.error("Failed to bulk import branches", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to bulk import branches: " + e.getMessage());
        }
    }

    // ==================== LOCATIONS ====================
    private void getAllLocations(HttpServerExchange exchange) {
        try {
            List<Location> locations = systemRepository.getAllLocations();
            sendSuccess(exchange, locations);
        } catch (Exception e) {
            logger.error("Failed to get locations", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch locations: " + e.getMessage());
        }
    }

    private void getLocationById(HttpServerExchange exchange) {
        try {
            Integer locationId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            Optional<Location> location = systemRepository.getLocationById(locationId);
            
            if (location.isPresent()) {
                sendSuccess(exchange, location.get());
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Location not found");
            }
        } catch (Exception e) {
            logger.error("Failed to get location", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch location: " + e.getMessage());
        }
    }

    private void createLocation(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Location location = JsonUtil.fromJson(body, Location.class);
            
            Location created = systemRepository.createLocation(location);
            sendSuccess(exchange, created, "Location created successfully");
        } catch (Exception e) {
            logger.error("Failed to create location", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create location: " + e.getMessage());
        }
    }

    private void updateLocation(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            Location location = JsonUtil.fromJson(body, Location.class);
            
            Location updated = systemRepository.updateLocation(location);
            sendSuccess(exchange, updated, "Location updated successfully");
        } catch (Exception e) {
            logger.error("Failed to update location", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update location: " + e.getMessage());
        }
    }

    private void deleteLocation(HttpServerExchange exchange) {
        try {
            Integer locationId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            boolean deleted = systemRepository.deleteLocation(locationId);
            
            if (deleted) {
                sendSuccess(exchange, null, "Location deleted successfully");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Location not found");
            }
        } catch (Exception e) {
            logger.error("Failed to delete location", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete location: " + e.getMessage());
        }
    }

    private void bulkImportLocations(HttpServerExchange exchange) {
        try {
            // Handle file upload
            if (exchange.isInIoThread()) {
                exchange.dispatch(this::bulkImportLocations);
                return;
            }
            exchange.startBlocking();
            
            FormDataParser parser = formParserFactory.createParser(exchange);
            if (parser == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Content-Type must be multipart/form-data");
                return;
            }
            
            FormData formData = parser.parseBlocking();
            FormData.FormValue fileValue = formData.getFirst("file");
            
            if (fileValue == null || !fileValue.isFileItem()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "No file uploaded");
                return;
            }
            
            // Read CSV file
            byte[] fileData;
            try (InputStream is = fileValue.getFileItem().getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                fileData = baos.toByteArray();
            }
            
            // Parse CSV
            List<Location> locations = new ArrayList<>();
            int rowNumber = 1; // Start at 1 for header
            try (CSVParser csvParser = CSVParser.parse(
                    new InputStreamReader(new ByteArrayInputStream(fileData), StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build())) {
                
                for (CSVRecord record : csvParser) {
                    rowNumber++;
                    
                    // Validate required fields
                    String name = record.get("name");
                    String locationType = record.get("locationType");
                    
                    if (name == null || name.trim().isEmpty()) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": 'name' is required");
                    }
                    
                    if (locationType == null || locationType.trim().isEmpty()) {
                        throw new IllegalArgumentException("Row " + rowNumber + ": 'locationType' is required");
                    }
                    
                    Location location = new Location();
                    location.setName(name.trim());
                    location.setLocationType(locationType.trim());
                    
                    if (record.isMapped("parentId") && !record.get("parentId").isEmpty()) {
                        try {
                            location.setParentId(Integer.parseInt(record.get("parentId").trim()));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Row " + rowNumber + ": 'parentId' must be a valid number");
                        }
                    }
                    
                    locations.add(location);
                }
            }
            
            int count = systemRepository.bulkInsertLocations(locations);
            
            Map<String, Object> response = new HashMap<>();
            response.put("imported", count);
            response.put("message", count + " locations imported successfully");
            
            sendSuccess(exchange, response);
        } catch (Exception e) {
            logger.error("Failed to bulk import locations", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to bulk import locations: " + e.getMessage());
        }
    }

    // ==================== NOTIFICATION TEMPLATES ====================
    private void getAllTemplates(HttpServerExchange exchange) {
        try {
            List<NotificationTemplate> templates = systemRepository.getAllTemplates();
            sendSuccess(exchange, templates);
        } catch (Exception e) {
            logger.error("Failed to get templates", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch templates: " + e.getMessage());
        }
    }

    private void getTemplateById(HttpServerExchange exchange) {
        try {
            Integer templateId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            Optional<NotificationTemplate> template = systemRepository.getTemplateById(templateId);
            
            if (template.isPresent()) {
                sendSuccess(exchange, template.get());
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Template not found");
            }
        } catch (Exception e) {
            logger.error("Failed to get template", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to fetch template: " + e.getMessage());
        }
    }

    private void createTemplate(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            NotificationTemplate template = JsonUtil.fromJson(body, NotificationTemplate.class);
            
            NotificationTemplate created = systemRepository.createTemplate(template);
            sendSuccess(exchange, created, "Template created successfully");
        } catch (Exception e) {
            logger.error("Failed to create template", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to create template: " + e.getMessage());
        }
    }

    private void updateTemplate(HttpServerExchange exchange) {
        try {
            String body = readRequestBody(exchange);
            NotificationTemplate template = JsonUtil.fromJson(body, NotificationTemplate.class);
            
            NotificationTemplate updated = systemRepository.updateTemplate(template);
            sendSuccess(exchange, updated, "Template updated successfully");
        } catch (Exception e) {
            logger.error("Failed to update template", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update template: " + e.getMessage());
        }
    }

    private void deleteTemplate(HttpServerExchange exchange) {
        try {
            Integer templateId = Integer.parseInt(extractIdFromPath(exchange.getRequestPath()));
            boolean deleted = systemRepository.deleteTemplate(templateId);
            
            if (deleted) {
                sendSuccess(exchange, null, "Template deleted successfully");
            } else {
                sendError(exchange, StatusCodes.NOT_FOUND, "Template not found");
            }
        } catch (Exception e) {
            logger.error("Failed to delete template", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete template: " + e.getMessage());
        }
    }
}
