package org.fuh.model;

/**
 * Representa una regla de prioridad institucional.
 * Mapea las columnas de la hoja Excel: "instituciones-prioridad".
 * Ejemplo: "El Club A debe jugar el 50% de sus partidos en la Cancha 1".
 */
public class InstitutionPriority {
    private final String institution;
    private final int targetCourtId;
    private final double minPercentage; // Ejemplo: 0.5 para el 50%

    public InstitutionPriority(String institution, int targetCourtId, double minPercentage) {
        this.institution = institution;
        this.targetCourtId = targetCourtId;
        this.minPercentage = minPercentage;
    }

    public String getInstitution() { return institution; }
    public int getTargetCourtId() { return targetCourtId; }
    public double getMinPercentage() { return minPercentage; }

    @Override
    public String toString() {
        return institution + " -> Cancha " + targetCourtId + " (Min: " + (minPercentage * 100) + "%)";
    }
}