package org.fuh.problem;

import org.fuh.model.MatchInfo; // <--- IMPORTANTE
import org.fuh.model.Slot;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.ArrayList;
import java.util.List;

public class FUHSchedulingProblem extends AbstractIntegerProblem {

    private final List<List<Slot>> validSlotsPerMatch;
    private final List<MatchInfo> matchInfos; // <--- CAMBIO 1: La lista de identidades
    private final int numberOfMatches;

    // <--- CAMBIO 2: Actualizar Constructor para recibir matchInfos
    public FUHSchedulingProblem(List<List<Slot>> validSlotsPerMatch, List<MatchInfo> matchInfos) {
        this.validSlotsPerMatch = validSlotsPerMatch;
        this.matchInfos = matchInfos; // Guardamos la referencia
        this.numberOfMatches = validSlotsPerMatch.size();

        this.numberOfObjectives(2);
        this.numberOfConstraints(1);
        this.name("FUHScheduling");

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

        // --- CALCULO DE OBJETIVOS ---

        // Objetivo 1: Minimizar huecos de la misma institución (Continuidad Institucional)
        //  "Minimizar los huecos de tiempo entre partidos consecutivos de una misma institución"
        double gapsInstitucion = calculateInstitutionalGaps(assignments);
        solution.objectives()[0] = gapsInstitucion;

        // Objetivo 2: Continuidad por categoría (Simplificado por ahora)
        solution.objectives()[1] = 0.0;

        // Restricciones Duras: Superposiciones
        double overlaps = countOverlaps(assignments);
        solution.constraints()[0] = (overlaps == 0) ? 0.0 : -overlaps;

        return solution;
    }

    // <--- CAMBIO 3: Lógica para calcular huecos usando MatchInfo
    private double calculateInstitutionalGaps(Slot[] assignments) {
        double totalPenalty = 0.0;

        for (int i = 0; i < numberOfMatches; i++) {
            Slot slotA = assignments[i];
            MatchInfo infoA = matchInfos.get(i);

            for (int j = i + 1; j < numberOfMatches; j++) {
                Slot slotB = assignments[j];
                MatchInfo infoB = matchInfos.get(j);

                // CAMBIO CRÍTICO: Usamos el nuevo método sharesInstitutionWith
                // Esto verifica las 4 combinaciones posibles (Local-Local, Local-Visitante, etc.)
                if (infoA.sharesInstitutionWith(infoB)) {
                    
                    // Si comparten equipo y cancha, verificamos el tiempo
                    if (slotA.getCourtId() == slotB.getCourtId()) {
                        
                        int diff = Math.abs(slotA.getTimeSlotId() - slotB.getTimeSlotId());
                        
                        // Si hay un hueco mayor a 1 hora (no son consecutivos)
                        if (diff > 1) {
                            // Sumamos penalización. 
                            // Nota: Podríamos sumar penalización doble si AMBOS equipos repiten,
                            // pero por ahora penalizamos el hueco simple.
                            totalPenalty += (diff - 1); 
                        }
                    }
                }
            }
        }
        return totalPenalty;
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
        for (int i = 0; i < assignments.length; i++) {
            for (int j = i + 1; j < assignments.length; j++) {
                if (assignments[i].equals(assignments[j])) {
                    overlaps++;
                }
            }
        }
        return overlaps;
    }
}