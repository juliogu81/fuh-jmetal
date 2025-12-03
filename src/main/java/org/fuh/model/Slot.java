package org.fuh.model;

import java.util.Objects;

public class Slot {
    private final String courtId; // <--- CAMBIO: Ahora es String
    private final int timeSlotId;

    public Slot(String courtId, int timeSlotId) {
        this.courtId = courtId;
        this.timeSlotId = timeSlotId;
    }

    public String getCourtId() { return courtId; }
    public int getTimeSlotId() { return timeSlotId; }

    @Override
    public String toString() {
        return "Cancha: " + courtId + ", Hora: " + timeSlotId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Slot slot = (Slot) o;
        // Importante: usar .equals para Strings
        return timeSlotId == slot.timeSlotId && Objects.equals(courtId, slot.courtId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtId, timeSlotId);
    }
}