package org.fuh.model;

/**
 * Representa la configuración de disponibilidad y restricciones de una cancha.
 * Mapea las columnas de la hoja Excel: "canchas-disponibilidad".
 */
public class CourtConfig {
    private final int id;
    private final int startHour;
    private final int endHour;
    private final int maxContinuousHours; // Restricción dura: máximo de horas seguidas

    public CourtConfig(int id, int startHour, int endHour, int maxContinuousHours) {
        this.id = id;
        this.startHour = startHour;
        this.endHour = endHour;
        this.maxContinuousHours = maxContinuousHours;
    }

    public int getId() { return id; }
    public int getStartHour() { return startHour; }
    public int getEndHour() { return endHour; }
    public int getMaxContinuousHours() { return maxContinuousHours; }
    
    @Override
    public String toString() {
        return "Cancha " + id + " [" + startHour + "-" + endHour + "h, MaxCont: " + maxContinuousHours + "]";
    }
}