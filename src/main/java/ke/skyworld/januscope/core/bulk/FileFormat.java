package ke.skyworld.januscope.core.bulk;

/**
 * Supported file formats for bulk operations
 */
public enum FileFormat {
    JSON("application/json", ".json"),
    XML("application/xml", ".xml"),
    CSV("text/csv", ".csv"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
    
    private final String mimeType;
    private final String extension;
    
    FileFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }
    
    public String getMimeType() { return mimeType; }
    public String getExtension() { return extension; }
    
    public static FileFormat fromContentType(String contentType) {
        if (contentType == null) return null;
        
        String type = contentType.toLowerCase().split(";")[0].trim();
        for (FileFormat format : values()) {
            if (format.mimeType.equals(type)) {
                return format;
            }
        }
        return null;
    }
    
    public static FileFormat fromFilename(String filename) {
        if (filename == null) return null;
        
        String lower = filename.toLowerCase();
        for (FileFormat format : values()) {
            if (lower.endsWith(format.extension)) {
                return format;
            }
        }
        return null;
    }
}
