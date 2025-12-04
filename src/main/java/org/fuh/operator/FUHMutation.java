package org.fuh.operator;

import org.fuh.model.Slot;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import java.util.List;

public class FUHMutation implements MutationOperator<IntegerSolution> {
    
    private final double mutationProbability;
    private final List<List<Slot>> validSlotsPerMatch;

    public FUHMutation(double mutationProbability, List<List<Slot>> validSlotsPerMatch) {
        this.mutationProbability = mutationProbability;
        this.validSlotsPerMatch = validSlotsPerMatch;
    }

    // FIX CRÍTICO 1: Método obligatorio sin 'get'
    @Override
    public double mutationProbability() {
        return mutationProbability;
    }


    @Override
    public IntegerSolution execute(IntegerSolution solution) {
        for (int i = 0; i < solution.variables().size(); i++) {
            if (JMetalRandom.getInstance().nextDouble() < mutationProbability) {
                
                // 1. Obtener la lista de opciones válidas para este partido (match 'i')
                List<Slot> validOptions = validSlotsPerMatch.get(i);
                
                int maxIndex = validOptions.size() - 1; 

                if (maxIndex >= 0) {
                    // 2. Generar un nuevo índice de slot VÁLIDO al azar dentro de los límites
                    int newIndex = JMetalRandom.getInstance().nextInt(0, maxIndex);
                    
                    // 3. Aplicar la mutación, el nuevo valor sigue siendo factible
                    solution.variables().set(i, newIndex);
                }
            }
        }
        return solution;
    }
}