package org.fuh.runner;

import org.fuh.model.Slot;
import org.fuh.problem.FUHSchedulingProblem;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.uma.jmetal.util.errorchecking.JMetalException; // Por si acaso

import java.util.ArrayList;
import java.util.List;

public class FUHRunner {

    public static void main(String[] args) {
        // 1. Datos de prueba (20 partidos simulados)
        List<List<Slot>> data = loadDummyData(20);

        // 2. Definir el Problema
        FUHSchedulingProblem problem = new FUHSchedulingProblem(data);

        // 3. Definir Operadores
        double crossoverProb = 0.9;
        var crossover = new IntegerSBXCrossover(crossoverProb, 20.0);

        var mutation = new IntegerPolynomialMutation(0.01, 20.0);

        // 4. Construir el Algoritmo (NSGA-II)
        // Nota: Si 'maxEvaluations' te da error, prueba 'setMaxEvaluations'. 
        // Pero dado tu 'Problem', es probable que sea sin 'set'.
        Algorithm<List<IntegerSolution>> algorithm = 
            new NSGAIIBuilder<>(problem, crossover, mutation, 100)
                .setMaxEvaluations(5000)
                .build();

        // 5. Ejecutar
        System.out.println("Ejecutando NSGA-II...");
        
        long start = System.currentTimeMillis();
        algorithm.run();
        long end = System.currentTimeMillis();

        // 6. Obtener Resultado
        // Cambio CRÍTICO: .result() en lugar de .getResult()
        List<IntegerSolution> result = algorithm.result(); 
        
        System.out.println("Fin. Tiempo: " + (end - start) + "ms");
        System.out.println("Soluciones encontradas: " + result.size());
        
        // Imprimir la primera solución como ejemplo si existe
        if (!result.isEmpty()) {
            IntegerSolution best = result.get(0);
            System.out.println("Mejor Solucion - Obj1 (Inst): " + best.objectives()[0]);
            System.out.println("Mejor Solucion - Obj2 (Cat): " + best.objectives()[1]);
            
            // Ver asignaciones (Genotipo)
            System.out.println("Variables: " + best.variables());
        }
    }

    // --- Generador de Datos Falsos ---
    private static List<List<Slot>> loadDummyData(int nMatches) {
        List<List<Slot>> list = new ArrayList<>();
        for (int i = 0; i < nMatches; i++) {
            List<Slot> options = new ArrayList<>();
            // Simulamos que cada partido tiene 3 opciones posibles
            options.add(new Slot(1, 10)); // Cancha 1, 10:00
            options.add(new Slot(2, 11)); // Cancha 2, 11:00
            options.add(new Slot(1, 12)); // Cancha 1, 12:00
            list.add(options);
        }
        return list;
    }
}