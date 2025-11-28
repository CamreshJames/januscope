package ke.skyworld.januscope.api.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import ke.skyworld.januscope.core.bulk.BulkExportService;
import ke.skyworld.januscope.core.bulk.BulkImportService;
import ke.skyworld.januscope.core.bulk.FileFormat;
import ke.skyworld.januscope.domain.models.BulkImportResult;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise-grade bulk import/export handler
 * Endpoints: /api/v1/bulk/*
 */
public class BulkOperationsHandler extends BaseHandler {
    private final BulkImportService importService;
    private final BulkExportService exportService;
    private final ke.skyworld.januscope.core.bulk.ServiceBulkImportService serviceImportService;
    private final FormParserFactory formParserFactory;
    
    public BulkOperationsHandler(BulkImportService importService, 
                                BulkExportService exportService,
                                ke.skyworld.januscope.core.bulk.ServiceBulkImportService serviceImportService) {
        this.importService = importService;
        this.exportService = exportService;
        this.serviceImportService = serviceImportService;
        this.formParserFactory = FormParserFactory.builder().build();
    }
    
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();
        String method = exchange.getRequestMethod().toString();
        
        // Check more specific paths first!
        if (path.endsWith("/services/import") && "POST".equals(method)) {
            handleServiceImport(exchange);
        } else if (path.endsWith("/import") && "POST".equals(method)) {
            handleImport(exchange);
        } else if (path.endsWith("/export") && "GET".equals(method)) {
            handleExport(exchange);
        } else if (path.endsWith("/template") && "GET".equals(method)) {
            handleTemplate(exchange);
        } else {
            sendError(exchange, StatusCodes.NOT_FOUND, "Endpoint not found");
        }
    }
    
    /**
     * POST /api/v1/bulk/services/import
     * Import services from file
     * Supports: JSON, XML, CSV, Excel
     */
    private void handleServiceImport(HttpServerExchange exchange) throws Exception {
        logger.info("Service bulk import request received from {}", exchange.getSourceAddress());
        
        try {
            // Start blocking mode for file upload
            if (exchange.isInIoThread()) {
                exchange.dispatch(this::handleServiceImport);
                return;
            }
            exchange.startBlocking();
            
            FormDataParser parser = formParserFactory.createParser(exchange);
            if (parser == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Content-Type must be multipart/form-data");
                return;
            }
            
            FormData formData = parser.parseBlocking();
            FormData.FormValue fileValue = formData.getFirst("file");
            
            if (fileValue == null || !fileValue.isFileItem()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "No file uploaded");
                return;
            }
            
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
            
            String filename = fileValue.getFileName();
            logger.info("Processing service upload: {} ({} bytes)", filename, fileData.length);
            
            FileFormat format = FileFormat.fromFilename(filename);
            if (format == null) {
                String contentType = fileValue.getHeaders().getFirst("Content-Type");
                format = FileFormat.fromContentType(contentType);
            }
            
            if (format == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Unsupported file format. Supported: JSON, XML, CSV, Excel (.xlsx)");
                return;
            }
            
            logger.info("Detected format: {}", format);
            
            BulkImportResult result = serviceImportService.importFromFile(fileData, format, filename);
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalRecords", result.getTotalRecords());
            response.put("successCount", result.getSuccessCount());
            response.put("failureCount", result.getFailureCount());
            response.put("processingTimeMs", result.getProcessingTimeMs());
            response.put("createdIds", result.getCreatedIds());
            
            if (!result.getErrors().isEmpty()) {
                response.put("errors", result.getErrors());
            }
            
            if (result.getFailureCount() > 0) {
                logger.warn("Service bulk import completed with {} errors", result.getFailureCount());
                sendSuccess(exchange, response, 
                    "Import completed with " + result.getFailureCount() + " errors");
            } else {
                logger.info("Service bulk import successful: {} records imported", result.getSuccessCount());
                sendSuccess(exchange, response, "Import successful");
            }
            
        } catch (Exception e) {
            logger.error("Service bulk import failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, 
                "Import failed: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/v1/bulk/import
     * Import contact groups and members from file
     * Supports: JSON, XML, CSV, Excel
     */
    private void handleImport(HttpServerExchange exchange) throws Exception {
        logger.info("Bulk import request received from {}", exchange.getSourceAddress());
        
        try {
            // Start blocking mode for file upload
            if (exchange.isInIoThread()) {
                exchange.dispatch(this::handleImport);
                return;
            }
            exchange.startBlocking();
            
            // Parse multipart form data
            FormDataParser parser = formParserFactory.createParser(exchange);
            if (parser == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Content-Type must be multipart/form-data");
                return;
            }
            
            FormData formData = parser.parseBlocking();
            FormData.FormValue fileValue = formData.getFirst("file");
            
            if (fileValue == null || !fileValue.isFileItem()) {
                sendError(exchange, StatusCodes.BAD_REQUEST, "No file uploaded");
                return;
            }
            
            // Read file data
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
            
            String filename = fileValue.getFileName();
            logger.info("Processing upload: {} ({} bytes)", filename, fileData.length);
            
            // Detect format
            FileFormat format = FileFormat.fromFilename(filename);
            if (format == null) {
                String contentType = fileValue.getHeaders().getFirst("Content-Type");
                format = FileFormat.fromContentType(contentType);
            }
            
            if (format == null) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Unsupported file format. Supported: JSON, XML, CSV, Excel (.xlsx)");
                return;
            }
            
            logger.info("Detected format: {}", format);
            
            // Perform import
            BulkImportResult result = importService.importFromFile(fileData, format, filename);
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("totalRecords", result.getTotalRecords());
            response.put("successCount", result.getSuccessCount());
            response.put("failureCount", result.getFailureCount());
            response.put("processingTimeMs", result.getProcessingTimeMs());
            response.put("createdIds", result.getCreatedIds());
            
            if (!result.getErrors().isEmpty()) {
                response.put("errors", result.getErrors());
            }
            
            if (result.getFailureCount() > 0) {
                logger.warn("Bulk import completed with {} errors", result.getFailureCount());
                sendSuccess(exchange, response, 
                    "Import completed with " + result.getFailureCount() + " errors");
            } else {
                logger.info("Bulk import successful: {} records imported", result.getSuccessCount());
                sendSuccess(exchange, response, "Import successful");
            }
            
        } catch (Exception e) {
            logger.error("Bulk import failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, 
                "Import failed: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/v1/bulk/export?format=json|xml|csv|excel
     * Export all contact groups and members
     */
    private void handleExport(HttpServerExchange exchange) throws Exception {
        logger.info("Bulk export request received from {}", exchange.getSourceAddress());
        
        try {
            // Get format from query parameter
            String formatParam = exchange.getQueryParameters().get("format").getFirst();
            
            FileFormat format;
            try {
                format = FileFormat.valueOf(formatParam.toUpperCase());
            } catch (Exception e) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Invalid format. Supported: json, xml, csv, excel");
                return;
            }
            
            logger.info("Exporting to format: {}", format);
            
            // Perform export
            byte[] data = exportService.exportToFile(format);
            
            // Set response headers
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, format.getMimeType());
            exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION, 
                "attachment; filename=\"contact-groups" + format.getExtension() + "\"");
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, data.length);
            
            // Send file
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(ByteBuffer.wrap(data));
            
            logger.info("Bulk export successful: {} bytes", data.length);
            
        } catch (Exception e) {
            logger.error("Bulk export failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, 
                "Export failed: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/v1/bulk/template?format=csv|excel
     * Download template file for bulk import
     */
    private void handleTemplate(HttpServerExchange exchange) throws Exception {
        logger.info("Template download request from {}", exchange.getSourceAddress());
        
        try {
            String formatParam = exchange.getQueryParameters().get("format").getFirst();
            
            FileFormat format;
            try {
                format = FileFormat.valueOf(formatParam.toUpperCase());
            } catch (Exception e) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Invalid format. Supported: csv, excel");
                return;
            }
            
            if (format != FileFormat.CSV && format != FileFormat.EXCEL) {
                sendError(exchange, StatusCodes.BAD_REQUEST, 
                    "Templates only available for CSV and Excel formats");
                return;
            }
            
            byte[] template = generateTemplate(format);
            
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, format.getMimeType());
            exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION, 
                "attachment; filename=\"contact-groups-template" + format.getExtension() + "\"");
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, template.length);
            
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(ByteBuffer.wrap(template));
            
            logger.info("Template downloaded: {} format", format);
            
        } catch (Exception e) {
            logger.error("Template generation failed", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, 
                "Template generation failed: " + e.getMessage());
        }
    }
    
    private byte[] generateTemplate(FileFormat format) throws Exception {
        if (format == FileFormat.CSV) {
            return ("groupName,groupDescription,memberName,email,phoneNumber,telegramHandle\n" +
                   "Support Team,24/7 Support,John Doe,john@example.com,+1234567890,@johndoe\n" +
                   "Support Team,,Jane Smith,jane@example.com,+0987654321,@janesmith\n")
                   .getBytes();
        } else {
            // Excel template
            return exportService.exportToFile(FileFormat.EXCEL);
        }
    }
}
