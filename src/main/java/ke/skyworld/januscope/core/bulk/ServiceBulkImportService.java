package ke.skyworld.januscope.core.bulk;

import ke.skyworld.januscope.domain.models.BulkImportResult;
import ke.skyworld.januscope.domain.models.Service;
import ke.skyworld.januscope.domain.repositories.ServiceRepository;
import ke.skyworld.januscope.utils.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bulk import service for monitored services
 * Supports JSON, XML, CSV, and Excel formats
 */
public class ServiceBulkImportService {
    private static final Logger logger = Logger.getLogger(ServiceBulkImportService.class);
    
    private final ServiceRepository serviceRepository;
    private final ke.skyworld.januscope.core.monitoring.MonitoringEngine monitoringEngine;
    private final ke.skyworld.januscope.domain.repositories.UptimeCheckRepository uptimeCheckRepository;
    private final ke.skyworld.januscope.domain.repositories.SSLCheckRepository sslCheckRepository;
    private int importCounter = 0; // For staggering checks
    
    public ServiceBulkImportService(ServiceRepository serviceRepository, 
                                   ke.skyworld.januscope.core.monitoring.MonitoringEngine monitoringEngine,
                                   ke.skyworld.januscope.domain.repositories.UptimeCheckRepository uptimeCheckRepository,
                                   ke.skyworld.januscope.domain.repositories.SSLCheckRepository sslCheckRepository) {
        this.serviceRepository = serviceRepository;
        this.monitoringEngine = monitoringEngine;
        this.uptimeCheckRepository = uptimeCheckRepository;
        this.sslCheckRepository = sslCheckRepository;
    }
    
    /**
     * Import services from file
     */
    public BulkImportResult importFromFile(byte[] fileData, FileFormat format, String filename) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting service bulk import from {} file: {}", format, filename);
        
        // Reset counter for staggered checks
        importCounter = 0;
        
        BulkImportResult result = new BulkImportResult();
        
        try {
            switch (format) {
                case JSON:
                    importFromJson(fileData, result);
                    break;
                case XML:
                    importFromXml(fileData, result);
                    break;
                case CSV:
                    importFromCsv(fileData, result);
                    break;
                case EXCEL:
                    importFromExcel(fileData, result);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported format: " + format);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(duration);
            
            logger.info("Service bulk import completed: {} successful, {} failed, {} ms", 
                       result.getSuccessCount(), result.getFailureCount(), duration);
            
        } catch (Exception e) {
            logger.error("Service bulk import failed", e);
            result.addError(0, "Import failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Import from JSON format
     */
    private void importFromJson(byte[] data, BulkImportResult result) throws Exception {
        String json = new String(data, StandardCharsets.UTF_8);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) 
            ke.skyworld.januscope.api.server.JsonUtil.fromJson(json, Map.class);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) root.get("services");
        
        if (services == null) {
            result.addError(0, "No 'services' array found in JSON");
            return;
        }
        
        result.setTotalRecords(services.size());
        
        for (int i = 0; i < services.size(); i++) {
            try {
                Map<String, Object> serviceData = services.get(i);
                importService(serviceData, i + 1, result);
            } catch (Exception e) {
                result.addError(i + 1, "Failed to import service: " + e.getMessage());
                logger.warn("Failed to import service at row {}", i + 1, e);
            }
        }
    }
    
    /**
     * Import from XML format
     */
    private void importFromXml(byte[] data, BulkImportResult result) throws Exception {
        String xml = new String(data, StandardCharsets.UTF_8);
        logger.debug("Parsing XML data: {} bytes", data.length);
        
        List<String> serviceBlocks = extractXmlBlocks(xml, "service");
        result.setTotalRecords(serviceBlocks.size());
        
        for (int i = 0; i < serviceBlocks.size(); i++) {
            try {
                String serviceXml = serviceBlocks.get(i);
                Map<String, Object> serviceData = parseXmlService(serviceXml);
                importService(serviceData, i + 1, result);
            } catch (Exception e) {
                result.addError(i + 1, "Failed to import service: " + e.getMessage());
                logger.warn("Failed to import service at row {}", i + 1, e);
            }
        }
    }
    
    /**
     * Import from CSV format
     */
    private void importFromCsv(byte[] data, BulkImportResult result) throws Exception {
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build())) {
            
            List<CSVRecord> records = parser.getRecords();
            result.setTotalRecords(records.size());
            
            for (int i = 0; i < records.size(); i++) {
                try {
                    CSVRecord record = records.get(i);
                    Map<String, Object> serviceData = new HashMap<>();
                    
                    serviceData.put("name", record.get("name"));
                    serviceData.put("url", record.get("url"));
                    
                    if (record.isMapped("checkIntervalSeconds") && !record.get("checkIntervalSeconds").isEmpty()) {
                        serviceData.put("checkIntervalSeconds", Integer.parseInt(record.get("checkIntervalSeconds")));
                    }
                    if (record.isMapped("timeoutMs") && !record.get("timeoutMs").isEmpty()) {
                        serviceData.put("timeoutMs", Integer.parseInt(record.get("timeoutMs")));
                    }
                    if (record.isMapped("maxRetries") && !record.get("maxRetries").isEmpty()) {
                        serviceData.put("maxRetries", Integer.parseInt(record.get("maxRetries")));
                    }
                    if (record.isMapped("active") && !record.get("active").isEmpty()) {
                        serviceData.put("active", Boolean.parseBoolean(record.get("active")));
                    }
                    
                    importService(serviceData, i + 2, result);
                    
                } catch (Exception e) {
                    result.addError(i + 2, "Failed to import record: " + e.getMessage());
                    logger.warn("Failed to import CSV record at row {}", i + 2, e);
                }
            }
        }
    }
    
    /**
     * Import from Excel format
     */
    private void importFromExcel(byte[] data, BulkImportResult result) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            
            if (sheet == null) {
                result.addError(0, "No sheet found in Excel file");
                return;
            }
            
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                result.addError(0, "No header row found");
                return;
            }
            
            Map<String, Integer> columnMap = buildColumnMap(headerRow);
            
            int totalRows = sheet.getLastRowNum();
            result.setTotalRecords(totalRows);
            
            for (int i = 1; i <= totalRows; i++) {
                try {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    Map<String, Object> serviceData = new HashMap<>();
                    
                    String name = getCellValue(row, columnMap.get("name"));
                    String url = getCellValue(row, columnMap.get("url"));
                    
                    if (name == null || name.isEmpty() || url == null || url.isEmpty()) {
                        result.addError(i + 1, "Name and URL are required");
                        continue;
                    }
                    
                    serviceData.put("name", name);
                    serviceData.put("url", url);
                    
                    String checkInterval = getCellValue(row, columnMap.get("checkIntervalSeconds"));
                    if (checkInterval != null && !checkInterval.isEmpty()) {
                        serviceData.put("checkIntervalSeconds", Integer.parseInt(checkInterval));
                    }
                    
                    String timeout = getCellValue(row, columnMap.get("timeoutMs"));
                    if (timeout != null && !timeout.isEmpty()) {
                        serviceData.put("timeoutMs", Integer.parseInt(timeout));
                    }
                    
                    String maxRetries = getCellValue(row, columnMap.get("maxRetries"));
                    if (maxRetries != null && !maxRetries.isEmpty()) {
                        serviceData.put("maxRetries", Integer.parseInt(maxRetries));
                    }
                    
                    String active = getCellValue(row, columnMap.get("active"));
                    if (active != null && !active.isEmpty()) {
                        serviceData.put("active", Boolean.parseBoolean(active));
                    }
                    
                    importService(serviceData, i + 1, result);
                    
                } catch (Exception e) {
                    result.addError(i + 1, "Failed to import row: " + e.getMessage());
                    logger.warn("Failed to import Excel row {}", i + 1, e);
                }
            }
        }
    }
    
    // Helper methods
    
    private void importService(Map<String, Object> serviceData, int row, BulkImportResult result) {
        try {
            if (!serviceData.containsKey("name") || !serviceData.containsKey("url")) {
                result.addError(row, "Service name and URL are required");
                return;
            }
            
            String url = (String) serviceData.get("url");
            String name = (String) serviceData.get("name");
            
            // Check for duplicate by URL
            List<Service> existingServices = serviceRepository.findAll();
            Service duplicate = existingServices.stream()
                .filter(s -> s.getUrl().equalsIgnoreCase(url))
                .findFirst()
                .orElse(null);
            
            if (duplicate != null) {
                result.addError(row, "Duplicate service: URL '" + url + "' already exists (Service ID: " + duplicate.getServiceId() + ", Name: " + duplicate.getName() + ")");
                logger.warn("Skipping duplicate service at row {}: URL '{}' already exists", row, url);
                return;
            }
            
            Service service = new Service();
            service.setName(name);
            service.setUrl(url);
            
            if (serviceData.containsKey("checkIntervalSeconds")) {
                service.setCheckIntervalSeconds((Integer) serviceData.get("checkIntervalSeconds"));
            }
            if (serviceData.containsKey("timeoutMs")) {
                service.setTimeoutMs((Integer) serviceData.get("timeoutMs"));
            }
            if (serviceData.containsKey("maxRetries")) {
                service.setMaxRetries((Integer) serviceData.get("maxRetries"));
            }
            if (serviceData.containsKey("retryDelayMs")) {
                service.setRetryDelayMs((Integer) serviceData.get("retryDelayMs"));
            }
            if (serviceData.containsKey("active")) {
                service.setActive((Boolean) serviceData.get("active"));
            }
            
            Service created = serviceRepository.create(service);
            if (created != null && created.getServiceId() != null) {
                result.addSuccess(created.getServiceId());
                logger.debug("Created service: {} (ID: {})", service.getName(), created.getServiceId());
                
                // Trigger immediate check with staggered delay to avoid thundering herd
                if (monitoringEngine != null) {
                    final int staggerDelaySeconds = importCounter * 2; // 2 seconds between each service check
                    final Service serviceToCheck = created;
                    final boolean isHttps = serviceToCheck.getUrl().toLowerCase().startsWith("https://");
                    importCounter++;
                    
                    // Schedule immediate check in background thread with stagger
                    new Thread(() -> {
                        try {
                            if (staggerDelaySeconds > 0) {
                                Thread.sleep(staggerDelaySeconds * 1000L);
                            }
                            
                            // Check uptime
                            ke.skyworld.januscope.domain.models.UptimeCheckResult uptimeResult = monitoringEngine.checkUptime(serviceToCheck);
                            
                            // Save uptime check result to database
                            if (uptimeResult != null && uptimeCheckRepository != null) {
                                uptimeCheckRepository.save(uptimeResult);
                                logger.debug("Saved initial uptime check for service: {}", serviceToCheck.getName());
                            }
                            
                            // Update service status
                            if (uptimeResult != null && serviceRepository != null) {
                                serviceRepository.updateStatus(serviceToCheck.getServiceId(), uptimeResult.getStatus());
                                logger.debug("Updated service status to: {}", uptimeResult.getStatus());
                            }
                            
                            logger.info("✓ Completed initial uptime check for service: {} - Status: {} (stagger: {}s)", 
                                       serviceToCheck.getName(), 
                                       uptimeResult != null ? uptimeResult.getStatus() : "UNKNOWN",
                                       staggerDelaySeconds);
                            
                            // Also check SSL for HTTPS services
                            if (isHttps) {
                                ke.skyworld.januscope.domain.models.SSLCheckResult sslResult = monitoringEngine.checkSSL(serviceToCheck);
                                
                                // Save SSL check result to database
                                if (sslResult != null && sslCheckRepository != null) {
                                    sslCheckRepository.save(sslResult);
                                    logger.debug("Saved initial SSL check for service: {}", serviceToCheck.getName());
                                }
                                
                                logger.info("✓ Completed initial SSL check for service: {} - Valid: {}, Days remaining: {}", 
                                           serviceToCheck.getName(), 
                                           sslResult != null ? sslResult.isValid() : false,
                                           sslResult != null ? sslResult.getDaysRemaining() : 0);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to trigger initial check for service {}: {}", serviceToCheck.getServiceId(), e.getMessage());
                        }
                    }).start();
                }
            } else {
                result.addError(row, "Failed to create service");
            }
            
        } catch (Exception e) {
            result.addError(row, "Failed to create service: " + e.getMessage());
            logger.warn("Failed to create service at row {}", row, e);
        }
    }
    
    private List<String> extractXmlBlocks(String xml, String tagName) {
        List<String> blocks = new ArrayList<>();
        String openTag = "<" + tagName;
        String closeTag = "</" + tagName + ">";
        
        int pos = 0;
        while ((pos = xml.indexOf(openTag, pos)) != -1) {
            int endPos = xml.indexOf(closeTag, pos);
            if (endPos != -1) {
                blocks.add(xml.substring(pos, endPos + closeTag.length()));
                pos = endPos + closeTag.length();
            } else {
                break;
            }
        }
        
        return blocks;
    }
    
    private Map<String, Object> parseXmlService(String xml) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", extractXmlValue(xml, "name"));
        data.put("url", extractXmlValue(xml, "url"));
        
        String checkInterval = extractXmlValue(xml, "checkIntervalSeconds");
        if (checkInterval != null && !checkInterval.isEmpty()) {
            data.put("checkIntervalSeconds", Integer.parseInt(checkInterval));
        }
        
        String timeout = extractXmlValue(xml, "timeoutMs");
        if (timeout != null && !timeout.isEmpty()) {
            data.put("timeoutMs", Integer.parseInt(timeout));
        }
        
        String maxRetries = extractXmlValue(xml, "maxRetries");
        if (maxRetries != null && !maxRetries.isEmpty()) {
            data.put("maxRetries", Integer.parseInt(maxRetries));
        }
        
        String active = extractXmlValue(xml, "active");
        if (active != null && !active.isEmpty()) {
            data.put("active", Boolean.parseBoolean(active));
        }
        
        return data;
    }
    
    private String extractXmlValue(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        
        int start = xml.indexOf(openTag);
        if (start == -1) return null;
        
        start += openTag.length();
        int end = xml.indexOf(closeTag, start);
        if (end == -1) return null;
        
        return xml.substring(start, end).trim();
    }
    
    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String header = cell.getStringCellValue().trim();
                map.put(header, i);
            }
        }
        
        return map;
    }
    
    private String getCellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }
}
