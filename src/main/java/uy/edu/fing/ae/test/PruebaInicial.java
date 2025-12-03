package uy.edu.fing.ae.test;

import java.util.Arrays;
import java.util.List;

// Imports exactos para jMetal 6
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

// 1. DEFINICIÓN DEL PROBLEMA
class ProblemaPrueba extends AbstractIntegerProblem {

    public ProblemaPrueba() {
        this.numberOfObjectives(2);
        this.numberOfConstraints(0);
        this.name("ProblemaSimpleV6");

        List<Integer> lower = Arrays.asList(-1000, -1000);
        List<Integer> upper = Arrays.asList(1000, 1000);

        this.variableBounds(lower, upper);
    }

    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        int val1 = solution.variables().get(0);
        int val2 = solution.variables().get(1);

        solution.objectives()[0] = Math.abs(val1 - 10);
        solution.objectives()[1] = Math.abs(val2 - 100);

        return solution; 
    }
}

// 2. EJECUCIÓN
public class PruebaInicial {
    public static void main(String[] args) {
        System.out.println("Iniciando prueba (Fix getPopulation)...");

        ProblemaPrueba problem = new ProblemaPrueba();

        double crossoverProb = 0.9;
        double mutationProb = 1.0 / problem.numberOfVariables();

        IntegerSBXCrossover crossover = new IntegerSBXCrossover(crossoverProb, 20.0);
        IntegerPolynomialMutation mutation = new IntegerPolynomialMutation(mutationProb, 20.0);

        // Construimos el algoritmo con el tamaño de población (100) en el constructor
        NSGAII<IntegerSolution> algorithm = new NSGAIIBuilder<>(problem, crossover, mutation, 100)
                .setMaxEvaluations(2500)
                .build();

        // Ejecutar
        long start = System.currentTimeMillis();
        algorithm.run();
        long end = System.currentTimeMillis();

        // TRUCO: Si getResult() falla, usamos getPopulation(). 
        // En NSGA-II el resultado ES la población final.
        List<IntegerSolution> result = algorithm.getPopulation();

        System.out.println("¡Éxito! (Usando getPopulation)");
        System.out.println("Tiempo: " + (end - start) + " ms");
        
        if (result != null) {
            System.out.println("Soluciones encontradas: " + result.size());
            // Imprimir la primera para validar
            IntegerSolution sol = result.get(0);
            System.out.println("Ejemplo -> Obj1: " + sol.objectives()[0] + " | Obj2: " + sol.objectives()[1]);
        }
    }
}