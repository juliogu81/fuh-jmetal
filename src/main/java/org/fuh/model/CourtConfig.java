package org.fuh.model;

public class CourtConfig {
    private final String id; // <--- CAMBIO: String
    private final int startHour;
    private final int endHour;
    private final int maxContinuousHours;

    public CourtConfig(String id, int startHour, int endHour, int maxContinuousHours) {
        this.id = id;
        this.startHour = startHour;
        this.endHour = endHour;
        this.maxContinuousHours = maxContinuousHours;
    }

    public String getId() { return id; } // <--- Return String
    public int getStartHour() { return startHour; }
    public int getEndHour() { return endHour; }
    public int getMaxContinuousHours() { return maxContinuousHours; }
}