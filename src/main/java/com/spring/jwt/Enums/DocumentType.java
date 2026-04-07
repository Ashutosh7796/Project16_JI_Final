package com.spring.jwt.Enums;

public enum DocumentType {
    AADHAAR_CARD("Aadhaar Card"),
    PAN_CARD("PAN Card"),
    PASSPORT("Passport"),
    DRIVING_LICENSE("Driving License"),
    VOTER_ID("Voter ID"),
    BIRTH_CERTIFICATE("Birth Certificate"),
    BANK_PASSBOOK("Bank Passbook"),
    EDUCATION_CERTIFICATE("Education Certificate"),
    INCOME_CERTIFICATE("Income Certificate"),
    CASTE_CERTIFICATE("Caste Certificate"),
    PROFILE_PHOTO("Profile Photo"),
    RESUME("Resume"),
    OTHER("Other");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DocumentType fromString(String text) {
        for (DocumentType type : DocumentType.values()) {
            if (type.displayName.equalsIgnoreCase(text) || type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        return OTHER;
    }
}