package org.fuh.model;

/**
 * Contiene la información estática del partido.
 * Esto NO cambia durante la evolución.
 */
public class MatchInfo {
    private final String id;           // Ej: "P01"
    private final String institution;  // Ej: "Club Malvin" [cite: 21]
    private final String category;     // Ej: "Juveniles" [cite: 21]

    public MatchInfo(String id, String institution, String category) {
        this.id = id;
        this.institution = institution;
        this.category = category;
    }

    public String getId() { return id; }
    public String getInstitution() { return institution; }
    public String getCategory() { return category; }

    @Override
    public String toString() {
        return id + ": " + institution + " (" + category + ")";
    }
}