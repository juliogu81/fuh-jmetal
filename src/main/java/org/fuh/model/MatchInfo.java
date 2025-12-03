package org.fuh.model;

public class MatchInfo {
    private final String id;
    private final String homeInstitution; // Institución Local
    private final String awayInstitution; // Institución Visitante
    private final String category;

    public MatchInfo(String id, String homeInstitution, String awayInstitution, String category) {
        this.id = id;
        this.homeInstitution = homeInstitution;
        this.awayInstitution = awayInstitution;
        this.category = category;
    }

    public String getHomeInstitution() { return homeInstitution; }
    public String getAwayInstitution() { return awayInstitution; }
    public String getCategory() { return category; }

    // Método auxiliar para saber si una institución juega en este partido
    public boolean involvesInstitution(String institution) {
        return homeInstitution.equals(institution) || awayInstitution.equals(institution);
    }
    
    // Método auxiliar para saber si dos partidos comparten ALGUNA institución
    public boolean sharesInstitutionWith(MatchInfo other) {
        return this.homeInstitution.equals(other.homeInstitution) ||
               this.homeInstitution.equals(other.awayInstitution) ||
               this.awayInstitution.equals(other.homeInstitution) ||
               this.awayInstitution.equals(other.awayInstitution);
    }
}