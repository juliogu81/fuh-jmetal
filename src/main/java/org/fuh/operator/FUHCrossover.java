package org.fuh.operator;

import org.fuh.model.Slot;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.errorchecking.Check;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FUHCrossover implements CrossoverOperator<IntegerSolution> {
    
    private final double crossoverProbability;
    private final Slot[][] preCalculatedAssignments; 

    public FUHCrossover(double crossoverProbability, List<List<Slot>> validSlotsPerMatch) {
        this.crossoverProbability = crossoverProbability;
        
        // Creamos un mapa 2D para la decodificación rápida de índices a Slots
        this.preCalculatedAssignments = new Slot[validSlotsPerMatch.size()][];
        for (int i = 0; i < validSlotsPerMatch.size(); i++) {
            preCalculatedAssignments[i] = validSlotsPerMatch.get(i).toArray(new Slot[0]);
        }
    }

    // --- MÉTODOS DE CONFIGURACIÓN OBLIGATORIOS ---
    
    // FIX CRÍTICO: Método requerido por la interfaz sin el prefijo 'get'
    @Override
    public double crossoverProbability() {
        return crossoverProbability;
    }
    
    @Override
    public int numberOfRequiredParents() {
        return 2; 
    }
    
    @Override
    public int numberOfGeneratedChildren() {
        return 2; 
    }
    
    // ---------------------------------------------

    @Override
    public List<IntegerSolution> execute(List<IntegerSolution> parents) {
        Check.that(parents.size() == 2, "Crossover requires two parents");
        
        IntegerSolution parent1 = parents.get(0);
        IntegerSolution parent2 = parents.get(1);

        if (JMetalRandom.getInstance().nextDouble() < crossoverProbability) {
            
            // 1. Obtener la lista de canchas asignadas en el Padre 1
            List<String> assignedCourts = IntStream.range(0, parent1.variables().size())
                .mapToObj(i -> preCalculatedAssignments[i][parent1.variables().get(i)].getCourtId())
                .distinct() 
                .collect(Collectors.toList());

            if (assignedCourts.isEmpty()) return parents;

            // 2. Elegir una cancha de corte al azar
            int randomCourtIndex = JMetalRandom.getInstance().nextInt(0, assignedCourts.size() - 1);
            String pivotCourt = assignedCourts.get(randomCourtIndex);

            // 3. Crear los hijos como copias de los padres
            IntegerSolution child1 = (IntegerSolution) parent1.copy();
            IntegerSolution child2 = (IntegerSolution) parent2.copy();

            // 4. Aplicar Cruce por Cancha
            for (int i = 0; i < parent1.variables().size(); i++) {
                String matchCourt1 = preCalculatedAssignments[i][parent1.variables().get(i)].getCourtId();
                String matchCourt2 = preCalculatedAssignments[i][parent2.variables().get(i)].getCourtId();

                // Intercambio 1: El Hijo 1 hereda del Padre 2 la asignación de la cancha pivote
                if (matchCourt2.equals(pivotCourt)) {
                    child1.variables().set(i, parent2.variables().get(i));
                }
                
                // Intercambio 2: El Hijo 2 hereda del Padre 1 la asignación de la cancha pivote
                if (matchCourt1.equals(pivotCourt)) {
                    child2.variables().set(i, parent1.variables().get(i));
                }
            }
            
            List<IntegerSolution> result = new ArrayList<>();
            result.add(child1);
            result.add(child2);
            return result;
        }

        return parents;
    }
}