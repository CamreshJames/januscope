package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.domain.repositories.SettingsRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings Management API Handler
 * Endpoints: /api/settings/*
 */
public class SettingsHandler extends BaseHandler {
    private final SettingsRepository settingsRepository;
    
    public SettingsHandler(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }
    
    @Override
    protected void handleGet(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        
        if (path.matches(".*/settings/[^/]+$")) {
            handleGetSetting(exchange);
        } else {
            handleGetAllSettings(exchange);
        }
    }
    
    @Override
    protected void handlePut(HttpServerExchange exchange) throws Exception {
        handleUpdateSetting(exchange);
    }
    
    // GET /api/settings
    private void handleGetAllSettings(HttpServerExchange exchange) {
        try {
            List<Map<String, Object>> settings = settingsRepository.findAll();
            sendSuccess(exchange, settings);
        } catch (Exception e) {
            logger.error("Failed to get settings", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve settings");
        }
    }
    
    // GET /api/settings/{key}
    private void handleGetSetting(HttpServerExchange exchange) {
        try {
            String key = extractKeyFromPath(exchange.getRequestPath());
            Map<String, Object> setting = settingsRepository.findByKey(key);
            
            if (setting == null) {
                sendError(exchange, StatusCodes.NOT_FOUND, "Setting not found");
                return;
            }
            
            sendSuccess(exchange, setting);
        } catch (Exception e) {
            logger.error("Failed to get setting", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to retrieve setting");
        }
    }
    
    // PUT /api/settings/{key}
    private void handleUpdateSetting(HttpServerExchange exchange) {
        try {
            String key = extractKeyFromPath(exchange.getRequestPath());
            Map<String, Object> data = parseRequestBody(exchange, Map.class);
            
            if (!data.containsKey("value")) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "Value is required");
                return;
            }
            
            boolean updated = settingsRepository.update(key, (String) data.get("value"));
            
            if (!updated) {
                sendError(exchange, StatusCodes.NOT_FOUND, "Setting not found");
                return;
            }
            
            sendSuccess(exchange, Map.of("message", "Setting updated successfully"));
        } catch (Exception e) {
            logger.error("Failed to update setting", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to update setting");
        }
    }
    
    /**
     * Extract key from path like /api/settings/monitoring.timeout_ms
     */
    private String extractKeyFromPath(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }
}
