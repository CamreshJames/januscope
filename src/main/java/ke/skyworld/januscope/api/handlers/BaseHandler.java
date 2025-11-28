package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.api.dto.ApiResponse;
import ke.skyworld.januscope.api.server.JsonUtil;
import ke.skyworld.januscope.utils.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Base handler with common functionality
 */
public abstract class BaseHandler implements HttpHandler {
    protected static final Logger logger = Logger.getLogger(BaseHandler.class);
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        
        try {
            if (exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }
            
            String method = exchange.getRequestMethod().toString();
            String path = exchange.getRequestPath();
            
            logger.debug("Handling {} {}", method, path);
            
            // Route to appropriate method
            switch (method) {
                case "GET":
                    handleGet(exchange);
                    break;
                case "POST":
                    handlePost(exchange);
                    break;
                case "PUT":
                    handlePut(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "Method not allowed");
            }
            
        } catch (Exception e) {
            logger.error("Error handling request", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage());
        }
    }
    
    protected void handleGet(HttpServerExchange exchange) throws Exception {
        sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "GET not supported");
    }
    
    protected void handlePost(HttpServerExchange exchange) throws Exception {
        sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "POST not supported");
    }
    
    protected void handlePut(HttpServerExchange exchange) throws Exception {
        sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "PUT not supported");
    }
    
    protected void handleDelete(HttpServerExchange exchange) throws Exception {
        sendError(exchange, StatusCodes.METHOD_NOT_ALLOWED, "DELETE not supported");
    }
    
    /**
     * Send success response
     */
    protected void sendSuccess(HttpServerExchange exchange, Object data) {
        sendSuccess(exchange, data, "Success");
    }
    
    protected void sendSuccess(HttpServerExchange exchange, Object data, String message) {
        ApiResponse response = ApiResponse.success(message, data);
        sendJson(exchange, StatusCodes.OK, response);
    }
    
    /**
     * Send error response
     */
    protected void sendError(HttpServerExchange exchange, int statusCode, String message) {
        ApiResponse response = ApiResponse.error(message);
        sendJson(exchange, statusCode, response);
    }
    
    /**
     * Send JSON response
     */
    protected void sendJson(HttpServerExchange exchange, int statusCode, Object data) {
        try {
            String json = JsonUtil.toJson(data);
            exchange.setStatusCode(statusCode);
            exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, "application/json");
            
            // Use dispatch to avoid blocking IO on IO thread
            if (exchange.isInIoThread()) {
                exchange.dispatch(() -> {
                    try {
                        exchange.getResponseSender().send(json, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.error("Failed to send response", e);
                    }
                });
            } else {
                exchange.getResponseSender().send(json, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.error("Failed to send JSON response", e);
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            if (exchange.isInIoThread()) {
                exchange.dispatch(() -> {
                    exchange.getResponseSender().send("{\"success\":false,\"message\":\"Failed to serialize response\"}");
                });
            } else {
                exchange.getResponseSender().send("{\"success\":false,\"message\":\"Failed to serialize response\"}");
            }
        }
    }
    
    /**
     * Read request body as string
     */
    protected String readRequestBody(HttpServerExchange exchange) throws IOException {
        exchange.startBlocking();
        StringBuilder body = new StringBuilder();
        byte[] buffer = new byte[1024];
        int read;
        
        while ((read = exchange.getInputStream().read(buffer)) != -1) {
            body.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        
        return body.toString();
    }
    
    /**
     * Parse JSON request body
     */
    protected <T> T parseRequestBody(HttpServerExchange exchange, Class<T> clazz) throws IOException {
        String body = readRequestBody(exchange);
        return JsonUtil.fromJson(body, clazz);
    }
    
    /**
     * Get path parameter
     */
    protected String getPathParam(HttpServerExchange exchange, String paramName) {
        String path = exchange.getRequestPath();
        // Simple path parameter extraction
        // For /api/services/123, extract "123"
        String[] parts = path.split("/");
        // This is a simple implementation - in production use a proper router
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }
    
    /**
     * Get query parameter
     */
    protected String getQueryParam(HttpServerExchange exchange, String paramName) {
        return exchange.getQueryParameters().get(paramName) != null 
            ? exchange.getQueryParameters().get(paramName).getFirst() 
            : null;
    }
    
    /**
     * Get query parameter as integer
     */
    protected Integer getQueryParamAsInt(HttpServerExchange exchange, String paramName, Integer defaultValue) {
        String value = getQueryParam(exchange, paramName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Extract user-friendly error messages from SQL exceptions
     */
    protected String extractUserFriendlyError(java.sql.SQLException e) {
        String message = e.getMessage();
        
        // Check for duplicate key violations
        if (message.contains("duplicate key value violates unique constraint")) {
            if (message.contains("users_email_key")) {
                return "Email address is already in use";
            } else if (message.contains("users_phone_number_key")) {
                return "Phone number is already in use";
            } else if (message.contains("users_username_key")) {
                return "Username is already taken";
            } else if (message.contains("users_national_id_key")) {
                return "National ID is already registered";
            } else if (message.contains("services_name_key")) {
                return "Service name is already in use";
            } else if (message.contains("contacts_email_key")) {
                return "Contact email is already registered";
            }
            return "This value is already in use";
        }
        
        // Check for foreign key violations
        if (message.contains("violates foreign key constraint")) {
            if (message.contains("users_role_id_fkey")) {
                return "Invalid role selected";
            } else if (message.contains("users_branch_id_fkey")) {
                return "Invalid branch selected";
            } else if (message.contains("service_id_fkey")) {
                return "Invalid service reference";
            } else if (message.contains("contact_id_fkey")) {
                return "Invalid contact reference";
            }
            return "Invalid reference to related data";
        }
        
        // Check for not null violations
        if (message.contains("violates not-null constraint")) {
            String column = extractColumnName(message);
            if (column != null) {
                return "Required field '" + column + "' is missing";
            }
            return "Required field is missing";
        }
        
        // Check for check constraint violations
        if (message.contains("violates check constraint")) {
            if (message.contains("valid_email")) {
                return "Invalid email format";
            } else if (message.contains("valid_phone")) {
                return "Invalid phone number format";
            } else if (message.contains("valid_url")) {
                return "Invalid URL format";
            }
            return "Invalid data format or value";
        }
        
        // Default message
        return "Database error: " + message;
    }
    
    /**
     * Extract column name from SQL error message
     */
    private String extractColumnName(String message) {
        // Try to extract column name from error message
        // Example: "null value in column "email" violates not-null constraint"
        int start = message.indexOf("column \"");
        if (start != -1) {
            start += 8; // length of "column \""
            int end = message.indexOf("\"", start);
            if (end != -1) {
                return message.substring(start, end);
            }
        }
        return null;
    }
}
