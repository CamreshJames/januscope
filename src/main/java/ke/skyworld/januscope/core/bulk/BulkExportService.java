package ke.skyworld.januscope.core.bulk;

import ke.skyworld.januscope.domain.repositories.ContactGroupRepository;
import ke.skyworld.januscope.domain.repositories.ContactMemberRepository;
import ke.skyworld.januscope.utils.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade bulk export service for contact groups and members
 */
public class BulkExportService {
    private static final Logger logger = Logger.getLogger(BulkExportService.class);
    
    private final ContactGroupRepository groupRepository;
    private final ContactMemberRepository memberRepository;
    
    public BulkExportService(ContactGroupRepository groupRepository,
                            ContactMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }
    
    /**
     * Export all contact groups and members to specified format
     */
    public byte[] exportToFile(FileFormat format) throws Exception {
        long startTime = System.currentTimeMillis();
        logger.info("Starting bulk export to {} format", format);
        
        byte[] result;
        
        switch (format) {
            case JSON:
                result = exportToJson();
                break;
            case XML:
                result = exportToXml();
                break;
            case CSV:
                result = exportToCsv();
                break;
            case EXCEL:
                result = exportToExcel();
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Bulk export completed: {} bytes, {} ms", result.length, duration);
        
        return result;
    }
    
    /**
     * Export to JSON format
     */
    private byte[] exportToJson() throws Exception {
        List<Map<String, Object>> groups = groupRepository.findAll();
        
        // Attach members to each group
        for (Map<String, Object> group : groups) {
            int groupId = (int) group.get("groupId");
            List<Map<String, Object>> members = memberRepository.findByGroupId(groupId);
            group.put("members", members);
        }
        
        Map<String, Object> root = Map.of("groups", groups);
        String json = ke.skyworld.januscope.api.server.JsonUtil.toJson(root);
        
        logger.debug("Exported {} groups to JSON", groups.size());
        return json.getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Export to XML format
     */
    private byte[] exportToXml() throws Exception {
        List<Map<String, Object>> groups = groupRepository.findAll();
        
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<contactGroups>\n");
        
        for (Map<String, Object> group : groups) {
            xml.append("  <group>\n");
            xml.append("    <groupId>").append(group.get("groupId")).append("</groupId>\n");
            xml.append("    <name>").append(escapeXml(String.valueOf(group.get("name")))).append("</name>\n");
            
            if (group.get("description") != null) {
                xml.append("    <description>")
                   .append(escapeXml(String.valueOf(group.get("description"))))
                   .append("</description>\n");
            }
            
            int groupId = (int) group.get("groupId");
            List<Map<String, Object>> members = memberRepository.findByGroupId(groupId);
            
            if (!members.isEmpty()) {
                xml.append("    <members>\n");
                
                for (Map<String, Object> member : members) {
                    xml.append("      <member>\n");
                    xml.append("        <memberId>").append(member.get("memberId")).append("</memberId>\n");
                    xml.append("        <name>").append(escapeXml(String.valueOf(member.get("name")))).append("</name>\n");
                    
                    if (member.get("email") != null) {
                        xml.append("        <email>").append(escapeXml(String.valueOf(member.get("email")))).append("</email>\n");
                    }
                    if (member.get("phoneNumber") != null) {
                        xml.append("        <phoneNumber>").append(escapeXml(String.valueOf(member.get("phoneNumber")))).append("</phoneNumber>\n");
                    }
                    if (member.get("telegramHandle") != null) {
                        xml.append("        <telegramHandle>").append(escapeXml(String.valueOf(member.get("telegramHandle")))).append("</telegramHandle>\n");
                    }
                    
                    xml.append("      </member>\n");
                }
                
                xml.append("    </members>\n");
            }
            
            xml.append("  </group>\n");
        }
        
        xml.append("</contactGroups>\n");
        
        logger.debug("Exported {} groups to XML", groups.size());
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Export to CSV format
     */
    private byte[] exportToCsv() throws Exception {
        List<Map<String, Object>> groups = groupRepository.findAll();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(baos, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder()
                    .setHeader("groupId", "groupName", "groupDescription", 
                              "memberId", "memberName", "email", "phoneNumber", "telegramHandle")
                    .build())) {
            
            int totalMembers = 0;
            
            for (Map<String, Object> group : groups) {
                int groupId = (int) group.get("groupId");
                String groupName = String.valueOf(group.get("name"));
                String groupDesc = group.get("description") != null ? 
                    String.valueOf(group.get("description")) : "";
                
                List<Map<String, Object>> members = memberRepository.findByGroupId(groupId);
                
                if (members.isEmpty()) {
                    // Export group without members
                    printer.printRecord(groupId, groupName, groupDesc, "", "", "", "", "");
                } else {
                    for (Map<String, Object> member : members) {
                        printer.printRecord(
                            groupId,
                            groupName,
                            groupDesc,
                            member.get("memberId"),
                            member.get("name"),
                            member.get("email") != null ? member.get("email") : "",
                            member.get("phoneNumber") != null ? member.get("phoneNumber") : "",
                            member.get("telegramHandle") != null ? member.get("telegramHandle") : ""
                        );
                        totalMembers++;
                    }
                }
            }
            
            printer.flush();
            logger.debug("Exported {} groups with {} members to CSV", groups.size(), totalMembers);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Export to Excel format
     */
    private byte[] exportToExcel() throws Exception {
        List<Map<String, Object>> groups = groupRepository.findAll();
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Contact Groups");
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"groupId", "groupName", "groupDescription", 
                               "memberId", "memberName", "email", "phoneNumber", "telegramHandle"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Fill data
            int rowNum = 1;
            int totalMembers = 0;
            
            for (Map<String, Object> group : groups) {
                int groupId = (int) group.get("groupId");
                String groupName = String.valueOf(group.get("name"));
                String groupDesc = group.get("description") != null ? 
                    String.valueOf(group.get("description")) : "";
                
                List<Map<String, Object>> members = memberRepository.findByGroupId(groupId);
                
                if (members.isEmpty()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(groupId);
                    row.createCell(1).setCellValue(groupName);
                    row.createCell(2).setCellValue(groupDesc);
                } else {
                    for (Map<String, Object> member : members) {
                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(groupId);
                        row.createCell(1).setCellValue(groupName);
                        row.createCell(2).setCellValue(groupDesc);
                        row.createCell(3).setCellValue((int) member.get("memberId"));
                        row.createCell(4).setCellValue(String.valueOf(member.get("name")));
                        row.createCell(5).setCellValue(member.get("email") != null ? 
                            String.valueOf(member.get("email")) : "");
                        row.createCell(6).setCellValue(member.get("phoneNumber") != null ? 
                            String.valueOf(member.get("phoneNumber")) : "");
                        row.createCell(7).setCellValue(member.get("telegramHandle") != null ? 
                            String.valueOf(member.get("telegramHandle")) : "");
                        totalMembers++;
                    }
                }
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            
            logger.debug("Exported {} groups with {} members to Excel", groups.size(), totalMembers);
            return baos.toByteArray();
        }
    }
    
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
