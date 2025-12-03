package org.fuh.model;

import java.util.Objects;

// Clase simple para representar (Cancha, Hora) compatible con Java 11
public class Slot {
    private final int courtId;
    private final int timeSlotId;

    public Slot(int courtId, int timeSlotId) {
        this.courtId = courtId;
        this.timeSlotId = timeSlotId;
    }

    public int getCourtId() {
        return courtId;
    }

    public int getTimeSlotId() {
        return timeSlotId;
    }

    @Override
    public String toString() {
        return "Cancha: " + courtId + ", Hora: " + timeSlotId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Slot slot = (Slot) o;
        return courtId == slot.courtId && timeSlotId == slot.timeSlotId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtId, timeSlotId);
    }
}