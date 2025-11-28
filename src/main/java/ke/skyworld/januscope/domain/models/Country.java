package ke.skyworld.januscope.domain.models;

import java.time.LocalDateTime;

public class Country {
    private String countryCode;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Country() {}

    public Country(String countryCode, String name) {
        this.countryCode = countryCode;
        this.name = name;
    }

    // Getters and Setters
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
