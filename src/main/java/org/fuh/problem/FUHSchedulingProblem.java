package org.fuh.problem;

import org.fuh.model.*;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FUHSchedulingProblem extends AbstractIntegerProblem {

    private final List<List<Slot>> validSlotsPerMatch;
    private final List<MatchInfo> matchInfos;
    private final Map<String, CourtConfig> courtConfigs;
    private final List<InstitutionPriority> priorities;
    private final List<CategoryBlock> categoryBlocks;
    private final int numberOfMatches;

    public FUHSchedulingProblem(
            List<List<Slot>> validSlotsPerMatch, 
            List<MatchInfo> matchInfos,
            Map<String, CourtConfig> courtConfigs,
            List<InstitutionPriority> priorities,
            List<CategoryBlock> categoryBlocks) {

        this.validSlotsPerMatch = validSlotsPerMatch;
        this.matchInfos = matchInfos;
        this.courtConfigs = courtConfigs;
        this.priorities = priorities;
        this.categoryBlocks = categoryBlocks;
        this.numberOfMatches = validSlotsPerMatch.size();

        // 2 Objetivos: Continuidad Institucional (O1) y Continuidad Categoría (O2)
        this.numberOfObjectives(2);
        
        // Número de restricciones = 1 (Superposición) + N Canchas (Max Horas) + N Reglas (Prioridad)
        this.numberOfConstraints(1 + courtConfigs.size() + priorities.size());

        List<Integer> lowerLimit = new ArrayList<>();
        List<Integer> upperLimit = new ArrayList<>();

        for (List<Slot> slots : validSlotsPerMatch) {
            lowerLimit.add(0);
            upperLimit.add(slots.size() - 1);
        }

        this.variableBounds(lowerLimit, upperLimit);
    }

    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        Slot[] assignments = decode(solution);

        // --- RESTRICCIONES DURAS ---
        int constraintIndex = 0;

        // 1. Superposición (Overlap)
        double overlaps = countOverlaps(assignments);
        solution.constraints()[constraintIndex++] = (overlaps == 0) ? 0.0 : -overlaps; // Penalización

        // 2. Máximo de Horas Continuas por Cancha
        double maxHoursViolation = 0.0;
        for (CourtConfig court : courtConfigs.values()) {
            double violation = checkMaxContinuousHours(assignments, court);
            maxHoursViolation += violation;
            solution.constraints()[constraintIndex++] = (violation == 0) ? 0.0 : -violation;
        }

        // 3. Cuotas de Prioridad Institucional (%)
        double priorityViolation = 0.0;
        for (InstitutionPriority rule : priorities) {
            double violation = checkPriorityQuota(assignments, rule);
            priorityViolation += violation;
            solution.constraints()[constraintIndex++] = (violation == 0) ? 0.0 : -violation;
        }

        // --- OBJETIVOS (Blandas) ---
        double o1 = calculateInstitutionalContinuity(assignments);
        double o2 = calculateCategoryContinuity(assignments);
        
        // Penalización grande por violaciones de restricciones duras
        double hardPenalty = 0.0;
        if (overlaps > 0) {
            hardPenalty += 10000.0 * overlaps;
        }
        if (maxHoursViolation > 0) {
            hardPenalty += 10000.0 * maxHoursViolation;
        }
        if (priorityViolation > 0) {
            hardPenalty += 10000.0 * priorityViolation;
        }

        solution.objectives()[0] = o1 + hardPenalty;
        solution.objectives()[1] = o2 + hardPenalty;
        
        return solution;
    }

    private Slot[] decode(IntegerSolution solution) {
        Slot[] assignments = new Slot[numberOfMatches];
        for (int i = 0; i < numberOfMatches; i++) {
            int slotIndex = solution.variables().get(i);
            assignments[i] = validSlotsPerMatch.get(i).get(slotIndex);
        }
        return assignments;
    }

    private double countOverlaps(Slot[] assignments) {
        int overlaps = 0;
        for (int i = 0; i < numberOfMatches; i++) {
            for (int j = i + 1; j < numberOfMatches; j++) {
                // Si dos partidos distintos tienen el mismo Slot (misma cancha y hora)
                if (assignments[i].equals(assignments[j])) {
                    overlaps++;
                }
            }
        }
        return overlaps;
    }
    
    // =========================================================
    // IMPLEMENTACIONES DE OBJETIVOS (Continuidad)
    // =========================================================
    
    // O1: Continuidad Institucional (Minimizar huecos)
    private double calculateInstitutionalContinuity(Slot[] assignments) {
        double totalPenalty = 0.0;

        for (int i = 0; i < numberOfMatches; i++) {
            MatchInfo infoA = matchInfos.get(i);
            Slot slotA = assignments[i];

            for (int j = i + 1; j < numberOfMatches; j++) {
                MatchInfo infoB = matchInfos.get(j);
                Slot slotB = assignments[j];
                
                // Si comparten institución Y están en la misma cancha
                if (infoA.sharesInstitutionWith(infoB) && slotA.getCourtId().equals(slotB.getCourtId())) {
                    
                    int diff = Math.abs(slotA.getTimeSlotId() - slotB.getTimeSlotId());
                    
                    if (diff > 1) {
                        totalPenalty += (diff - 1); 
                    }
                }
            }
        }
        return totalPenalty;
    }
    
    // O2: Continuidad por Categorías (Bloques)
    private double calculateCategoryContinuity(Slot[] assignments) {
        double totalPenalty = 0.0;

        for (int i = 0; i < numberOfMatches; i++) {
            String catA = matchInfos.get(i).getCategory();
            Slot slotA = assignments[i];

            for (int j = i + 1; j < numberOfMatches; j++) {
                String catB = matchInfos.get(j).getCategory();
                Slot slotB = assignments[j];

                // Solo si están en la MISMA cancha
                if (slotA.getCourtId().equals(slotB.getCourtId())) {
                    
                    // Verificamos si pertenecen al mismo bloque de categorías (Objetivo 2)
                    boolean sameBlock = isSameBlock(catA, catB);
                    
                    if (sameBlock) {
                        int diff = Math.abs(slotA.getTimeSlotId() - slotB.getTimeSlotId());
                        
                        if (diff > 1) {
                            totalPenalty += (diff - 1); // Penalización por hueco
                        }
                    }
                }
            }
        }
        return totalPenalty;
    }
    
    // Helper para verificar si dos categorías son "compatibles" (están en el mismo bloque)
    private boolean isSameBlock(String cat1, String cat2) {
        // Si son la misma categoría exacta, cuenta como bloque implícito
        if (cat1.equals(cat2)) return true;

        // Buscar en los bloques definidos en el Excel
        for (CategoryBlock block : categoryBlocks) {
            if (block.match(cat1, cat2)) {
                return true; 
            }
        }
        return false;
    }

    // =========================================================
    // IMPLEMENTACIONES DE RESTRICCIONES DURAS
    // =========================================================

    // R2: Verifica que no se exceda el máximo de horas continuas asignadas a una cancha
    private double checkMaxContinuousHours(Slot[] assignments, CourtConfig court) {
        // 1. Obtener todos los horarios asignados a ESTA cancha
        List<Integer> times = new ArrayList<>();
        for (Slot s : assignments) {
            // Usamos .equals para String
            if (s.getCourtId().equals(court.getId())) {
                times.add(s.getTimeSlotId());
            }
        }
        // No hay partidos o hay solo uno, no puede haber violación
        if (times.size() <= 1) return 0.0; 
        
        Collections.sort(times); // Ordenar cronológicamente

        // 2. Buscar la secuencia más larga
        int currentStreak = 1;
        int maxStreakFound = 1;

        for (int i = 1; i < times.size(); i++) {
            // Si el tiempo actual es el inmediatamente siguiente al anterior
            if (times.get(i) == times.get(i - 1) + 1) { 
                currentStreak++;
            } else {
                currentStreak = 1; // Reiniciar cuenta
            }
            maxStreakFound = Math.max(maxStreakFound, currentStreak);
        }

        // 3. Verificar límite
        if (maxStreakFound > court.getMaxContinuousHours()) {
            // Penalizamos el exceso de horas continuas
            return (maxStreakFound - court.getMaxContinuousHours()); 
        }
        return 0.0;
    }

    // R3: Verifica que las instituciones cumplan su cuota mínima de partidos en una cancha objetivo
    private double checkPriorityQuota(Slot[] assignments, InstitutionPriority rule) {
        int totalMatchesOfInst = 0;
        int matchesOnTargetCourt = 0;

        for (int i = 0; i < numberOfMatches; i++) {
            MatchInfo info = matchInfos.get(i);
            
            // ¿El partido involucra a la institución de la regla?
            if (info.getHomeInstitution().equals(rule.getInstitution()) || info.getAwayInstitution().equals(rule.getInstitution())) {
                totalMatchesOfInst++;
                
                // ¿Está en la cancha objetivo?
                // Usamos .equals para String
                if (assignments[i].getCourtId().equals(rule.getTargetCourtId())) {
                    matchesOnTargetCourt++;
                }
            }
        }

        if (totalMatchesOfInst == 0) return 0.0; // No hay partidos que verificar

        double actualPct = (double) matchesOnTargetCourt / totalMatchesOfInst;
        
        // Si no llega al porcentaje mínimo, penalizamos la diferencia.
        if (actualPct < rule.getMinPercentage()) {
            // Escalamos la penalización para que tenga peso (ej: 0.10 de violación = 10 puntos)
            return (rule.getMinPercentage() - actualPct) * 100; 
        }
        return 0.0;
    }
}