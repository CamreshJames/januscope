package ke.skyworld.januscope.core.bulk;

import ke.skyworld.januscope.domain.models.BulkImportResult;
import ke.skyworld.januscope.domain.repositories.ContactGroupRepository;
import ke.skyworld.januscope.domain.repositories.ContactMemberRepository;
import ke.skyworld.januscope.utils.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Enterprise-grade bulk import service for contact groups and members
 * Supports JSON, XML, CSV, and Excel formats
 */
public class BulkImportService {
    private static final Logger logger = Logger.getLogger(BulkImportService.class);
    
    private final ContactGroupRepository groupRepository;
    private final ContactMemberRepository memberRepository;
    
    public BulkImportService(ContactGroupRepository groupRepository, 
                            ContactMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }
    
    /**
     * Import contact groups and members from file
     */
    public BulkImportResult importFromFile(byte[] fileData, FileFormat format, String filename) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting bulk import from {} file: {}", format, filename);
        
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
            
            logger.info("Bulk import completed: {} successful, {} failed, {} ms", 
                       result.getSuccessCount(), result.getFailureCount(), duration);
            
        } catch (Exception e) {
            logger.error("Bulk import failed", e);
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
        List<Map<String, Object>> groups = (List<Map<String, Object>>) root.get("groups");
        
        if (groups == null) {
            result.addError(0, "No 'groups' array found in JSON");
            return;
        }
        
        result.setTotalRecords(groups.size());
        
        for (int i = 0; i < groups.size(); i++) {
            try {
                Map<String, Object> groupData = groups.get(i);
                int groupId = importGroup(groupData, i + 1, result);
                
                if (groupId > 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> members = 
                        (List<Map<String, Object>>) groupData.get("members");
                    
                    if (members != null) {
                        importMembers(groupId, members, i + 1, result);
                    }
                }
            } catch (Exception e) {
                result.addError(i + 1, "Failed to import group: " + e.getMessage());
                logger.warn("Failed to import group at row {}", i + 1, e);
            }
        }
    }
    
    /**
     * Import from XML format
     */
    private void importFromXml(byte[] data, BulkImportResult result) throws Exception {
        // Simple XML parsing - for production, use proper XML parser
        String xml = new String(data, StandardCharsets.UTF_8);
        logger.debug("Parsing XML data: {} bytes", data.length);
        
        // Extract groups using simple string parsing
        List<String> groupBlocks = extractXmlBlocks(xml, "group");
        result.setTotalRecords(groupBlocks.size());
        
        for (int i = 0; i < groupBlocks.size(); i++) {
            try {
                String groupXml = groupBlocks.get(i);
                Map<String, Object> groupData = parseXmlGroup(groupXml);
                
                int groupId = importGroup(groupData, i + 1, result);
                
                if (groupId > 0) {
                    List<String> memberBlocks = extractXmlBlocks(groupXml, "member");
                    List<Map<String, Object>> members = new ArrayList<>();
                    
                    for (String memberXml : memberBlocks) {
                        members.add(parseXmlMember(memberXml));
                    }
                    
                    if (!members.isEmpty()) {
                        importMembers(groupId, members, i + 1, result);
                    }
                }
            } catch (Exception e) {
                result.addError(i + 1, "Failed to import group: " + e.getMessage());
                logger.warn("Failed to import group at row {}", i + 1, e);
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
            
            Map<String, Integer> groupCache = new HashMap<>();
            
            for (int i = 0; i < records.size(); i++) {
                try {
                    CSVRecord record = records.get(i);
                    String groupName = record.get("groupName");
                    
                    // Get or create group
                    int groupId = groupCache.computeIfAbsent(groupName, name -> {
                        Map<String, Object> groupData = new HashMap<>();
                        groupData.put("name", name);
                        groupData.put("description", record.isMapped("groupDescription") ? 
                            record.get("groupDescription") : "");
                        
                        try {
                            return groupRepository.create(groupData);
                        } catch (Exception e) {
                            logger.error("Failed to create group: {}", name, e);
                            return -1;
                        }
                    });
                    
                    if (groupId > 0) {
                        Map<String, Object> memberData = new HashMap<>();
                        memberData.put("groupId", groupId);
                        memberData.put("name", record.get("memberName"));
                        
                        if (record.isMapped("email") && !record.get("email").isEmpty()) {
                            memberData.put("email", record.get("email"));
                        }
                        if (record.isMapped("phoneNumber") && !record.get("phoneNumber").isEmpty()) {
                            memberData.put("phoneNumber", record.get("phoneNumber"));
                        }
                        if (record.isMapped("telegramHandle") && !record.get("telegramHandle").isEmpty()) {
                            memberData.put("telegramHandle", record.get("telegramHandle"));
                        }
                        
                        int memberId = memberRepository.create(memberData);
                        result.addSuccess(memberId);
                        logger.debug("Imported member: {} to group: {}", memberData.get("name"), groupName);
                    }
                    
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
            
            // Read header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                result.addError(0, "No header row found");
                return;
            }
            
            Map<String, Integer> columnMap = buildColumnMap(headerRow);
            
            int totalRows = sheet.getLastRowNum();
            result.setTotalRecords(totalRows);
            
            Map<String, Integer> groupCache = new HashMap<>();
            
            for (int i = 1; i <= totalRows; i++) {
                try {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    String groupName = getCellValue(row, columnMap.get("groupName"));
                    if (groupName == null || groupName.isEmpty()) continue;
                    
                    // Get or create group
                    int groupId = groupCache.computeIfAbsent(groupName, name -> {
                        Map<String, Object> groupData = new HashMap<>();
                        groupData.put("name", name);
                        
                        String desc = getCellValue(row, columnMap.get("groupDescription"));
                        if (desc != null && !desc.isEmpty()) {
                            groupData.put("description", desc);
                        }
                        
                        try {
                            return groupRepository.create(groupData);
                        } catch (Exception e) {
                            logger.error("Failed to create group: {}", name, e);
                            return -1;
                        }
                    });
                    
                    if (groupId > 0) {
                        Map<String, Object> memberData = new HashMap<>();
                        memberData.put("groupId", groupId);
                        memberData.put("name", getCellValue(row, columnMap.get("memberName")));
                        
                        String email = getCellValue(row, columnMap.get("email"));
                        if (email != null && !email.isEmpty()) {
                            memberData.put("email", email);
                        }
                        
                        String phone = getCellValue(row, columnMap.get("phoneNumber"));
                        if (phone != null && !phone.isEmpty()) {
                            memberData.put("phoneNumber", phone);
                        }
                        
                        String telegram = getCellValue(row, columnMap.get("telegramHandle"));
                        if (telegram != null && !telegram.isEmpty()) {
                            memberData.put("telegramHandle", telegram);
                        }
                        
                        int memberId = memberRepository.create(memberData);
                        result.addSuccess(memberId);
                        logger.debug("Imported member from Excel row {}", i + 1);
                    }
                    
                } catch (Exception e) {
                    result.addError(i + 1, "Failed to import row: " + e.getMessage());
                    logger.warn("Failed to import Excel row {}", i + 1, e);
                }
            }
        }
    }
    
    // Helper methods
    
    private int importGroup(Map<String, Object> groupData, int row, BulkImportResult result) {
        try {
            if (!groupData.containsKey("name")) {
                result.addError(row, "Group name is required");
                return -1;
            }
            
            int groupId = groupRepository.create(groupData);
            logger.debug("Created group: {} (ID: {})", groupData.get("name"), groupId);
            return groupId;
            
        } catch (Exception e) {
            result.addError(row, "Failed to create group: " + e.getMessage());
            logger.warn("Failed to create group at row {}", row, e);
            return -1;
        }
    }
    
    private void importMembers(int groupId, List<Map<String, Object>> members, 
                              int groupRow, BulkImportResult result) {
        for (int i = 0; i < members.size(); i++) {
            try {
                Map<String, Object> memberData = members.get(i);
                memberData.put("groupId", groupId);
                
                if (!memberData.containsKey("name")) {
                    result.addError(groupRow, "Member " + (i + 1) + ": name is required");
                    continue;
                }
                
                int memberId = memberRepository.create(memberData);
                result.addSuccess(memberId);
                logger.debug("Created member: {} (ID: {})", memberData.get("name"), memberId);
                
            } catch (Exception e) {
                result.addError(groupRow, "Member " + (i + 1) + ": " + e.getMessage());
                logger.warn("Failed to create member {} for group row {}", i + 1, groupRow, e);
            }
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
    
    private Map<String, Object> parseXmlGroup(String xml) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", extractXmlValue(xml, "name"));
        
        String desc = extractXmlValue(xml, "description");
        if (desc != null && !desc.isEmpty()) {
            data.put("description", desc);
        }
        
        return data;
    }
    
    private Map<String, Object> parseXmlMember(String xml) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", extractXmlValue(xml, "name"));
        
        String email = extractXmlValue(xml, "email");
        if (email != null && !email.isEmpty()) {
            data.put("email", email);
        }
        
        String phone = extractXmlValue(xml, "phoneNumber");
        if (phone != null && !phone.isEmpty()) {
            data.put("phoneNumber", phone);
        }
        
        String telegram = extractXmlValue(xml, "telegramHandle");
        if (telegram != null && !telegram.isEmpty()) {
            data.put("telegramHandle", telegram);
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
