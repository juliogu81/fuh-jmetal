package org.fuh.model;

public class MatchInfo {
    private final String id;
    private final String homeInstitution; 
    private final String awayInstitution; 
    private final String category;

    public MatchInfo(String id, String homeInstitution, String awayInstitution, String category) {
        this.id = id;
        this.homeInstitution = homeInstitution;
        this.awayInstitution = awayInstitution;
        this.category = category;
    }

    // --- AGREGA ESTE MÉTODO QUE FALTABA ---
    public String getId() { return id; }
    // --------------------------------------

    public String getHomeInstitution() { return homeInstitution; }
    public String getAwayInstitution() { return awayInstitution; }
    public String getCategory() { return category; }

    public boolean involvesInstitution(String institution) {
        return homeInstitution.equals(institution) || awayInstitution.equals(institution);
    }
    
    public boolean sharesInstitutionWith(MatchInfo other) {
        return this.homeInstitution.equals(other.homeInstitution) ||
               this.homeInstitution.equals(other.awayInstitution) ||
               this.awayInstitution.equals(other.homeInstitution) ||
               this.awayInstitution.equals(other.awayInstitution);
    }
    
    @Override
    public String toString() {
        return id + ": " + homeInstitution + " vs " + awayInstitution + " (" + category + ")";
    }
}