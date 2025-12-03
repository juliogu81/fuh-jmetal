package org.fuh.model;

public class InstitutionPriority {
    private final String institution;
    private final String targetCourtId; // <--- CAMBIO: String
    private final double minPercentage;

    public InstitutionPriority(String institution, String targetCourtId, double minPercentage) {
        this.institution = institution;
        this.targetCourtId = targetCourtId;
        this.minPercentage = minPercentage;
    }

    public String getInstitution() { return institution; }
    public String getTargetCourtId() { return targetCourtId; } // <--- Return String
    public double getMinPercentage() { return minPercentage; }
}