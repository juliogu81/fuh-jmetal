package org.fuh.problem;

import org.fuh.model.Slot; // Asegúrate de que este import coincida con tu paquete
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.ArrayList;
import java.util.List;

public class FUHSchedulingProblem extends AbstractIntegerProblem {

    private final List<List<Slot>> validSlotsPerMatch;
    private final int numberOfMatches;

    public FUHSchedulingProblem(List<List<Slot>> validSlotsPerMatch) {
        this.validSlotsPerMatch = validSlotsPerMatch;
        this.numberOfMatches = validSlotsPerMatch.size();

        // 1. Configurar metadatos usando los nombres SIN "set"
        this.numberOfObjectives(2); 
        this.numberOfConstraints(1); 
        this.name("FUHScheduling");

        // 2. Crear las listas de límites (Bounds)
        List<Integer> lowerLimit = new ArrayList<>();
        List<Integer> upperLimit = new ArrayList<>();

        for (List<Slot> slots : validSlotsPerMatch) {
            lowerLimit.add(0); // El índice empieza en 0
            upperLimit.add(slots.size() - 1); // El índice termina en tamaño - 1
        }

        // 3. Establecer los límites
        // Al llamar a esto, la clase padre calcula automáticamente numberOfVariables()
        this.variableBounds(lowerLimit, upperLimit);
    }

    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        // Decodificar la solución
        Slot[] assignments = decode(solution);

        // --- Calcular Objetivos (Simulados según PDF) ---
        // Objetivo 1: Minimizar huecos institucionales
        solution.objectives()[0] = 0.0; 
        
        // Objetivo 2: Minimizar huecos por categoría
        solution.objectives()[1] = 0.0; 

        // --- Calcular Restricciones ---
        // Si hay superposición (overlaps > 0), penalizamos con un valor negativo
        double overlaps = countOverlaps(assignments);
        solution.constraints()[0] = (overlaps == 0) ? 0.0 : -overlaps;

        return solution;
    }

    // Método auxiliar para traducir indices (0, 1, 2...) a Slots reales (Cancha1-10am, etc)
    private Slot[] decode(IntegerSolution solution) {
        Slot[] assignments = new Slot[numberOfMatches];
        for (int i = 0; i < numberOfMatches; i++) {
            int slotIndex = solution.variables().get(i);
            assignments[i] = validSlotsPerMatch.get(i).get(slotIndex);
        }
        return assignments;
    }

    // Método auxiliar simple para contar choques de horario
    private double countOverlaps(Slot[] assignments) {
        int overlaps = 0;
        for (int i = 0; i < assignments.length; i++) {
            for (int j = i + 1; j < assignments.length; j++) {
                // Si dos partidos distintos tienen el mismo Slot (misma cancha y hora)
                if (assignments[i].equals(assignments[j])) {
                    overlaps++;
                }
            }
        }
        return overlaps;
    }
}