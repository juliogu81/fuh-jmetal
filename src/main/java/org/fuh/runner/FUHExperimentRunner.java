package org.fuh.runner;

import org.fuh.io.ExcelLoader;
import org.fuh.problem.FUHSchedulingProblem;
import org.fuh.runner.FUHRunner.ExperimentResult;
import org.fuh.io.FixtureSeeder; // Necesario para cargar la solución inicial
import org.fuh.model.Slot; // Necesario para tipado
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Clase que ejecuta una calibración paramétrica (Grid Search)
 * sobre el algoritmo NSGA-II.
 * * Estrategia: Cada corrida utiliza la MISMA SOLUCIÓN INICIAL (FIXTURE) 
 * cargada desde un archivo para ver qué configuración de hiperparámetros 
 * (PopSize, Pc, Pm, Gen) produce el mejor resultado partiendo del mismo punto.
 */
public class FUHExperimentRunner {

    // --- Configuración Global del Experimento ---
    private static final int REPETITIONS = 1; 
    private static final String PROBLEM_NAME = "HandballFixture"; 
    
    // 🔥 AJUSTA ESTAS RUTAS 🔥
    private static final String EXCEL_FILE_PATH = "/Users/juliogu/Documentos/git/ae-fixture/data/entrada/input_v5_4.xlsx"; 
    // RUTA DE LA SOLUCIÓN INICIAL (SEMILLA DE FIXTURE)
    private static final String INITIAL_SEED_PATH = "/Users/juliogu/Documentos/git/ae-fixture/data/salida/output_2025-12-14_23-49.xlsx"; 

    // --- Hiperparámetros a Testear ---
    private static final int[] POPULATION_SIZES = {100, 150, 200};
    private static final double[] CROSSOVER_PROBS = {1.0, 0.6, 0.9};
    private static final double[] MUTATION_PROBS = {0.01, 0.05, 0.001}; 
    private static final int[] GENERATIONS = {1000, 1500}; 

    // Semilla de JMetal para el control de la aleatoriedad de las operaciones
    private static final long ALGORITHM_TEST_SEED = 12345L; 

    public static void main(String[] args) {
        
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║     INICIANDO CALIBRACIÓN PARAMÉTRICA        ║");
        System.out.println("║     Modo: Semilla de Fixture Única           ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        
        int totalConfigurations = POPULATION_SIZES.length * CROSSOVER_PROBS.length * MUTATION_PROBS.length * GENERATIONS.length;
        int totalRuns = totalConfigurations * REPETITIONS;
        
        System.out.printf("Problema: %s\n", PROBLEM_NAME);
        System.out.printf("Total de Corridas estimadas: %d\n", totalRuns);
        System.out.println("------------------------------------------------");
        
        // 1. Cargar los datos del problema UNA sola vez
        ExcelLoader.DataResult data = loadDataFromExcel(EXCEL_FILE_PATH); 
        
        if (data == null || data.matchInfos.isEmpty()) {
            System.err.println("❌ ERROR: No hay datos para procesar. Abortando.");
            return;
        }

        // 2. Cargar la Solución Inicial (Fixture Semilla) UNA sola vez
        IntegerSolution fixedInitialSeed = loadAndVerifyInitialSeed(data);
        
        if (fixedInitialSeed == null) {
            System.err.println("❌ ERROR: Falló la carga de la Solución Inicial. Abortando.");
            return;
        }

        // 3. Preparar archivo de salida
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String resultsFile = "calibration_results_" + timestamp + ".csv";
        initializeResultsFile(resultsFile);
        
        int configId = 0;
        long startTimeTotal = System.currentTimeMillis();
        
        // 4. Bucles anidados (Grid Search)
        for (int populationSize : POPULATION_SIZES) {
            for (double crossoverProb : CROSSOVER_PROBS) {
                for (double mutationProb : MUTATION_PROBS) {
                    for (int generations : GENERATIONS) { 
                        
                        configId++;
                        String configLabel = String.format("C%03d", configId);
                        int maxEvaluations = populationSize * generations; 

                        System.out.printf("\n---> Config %s: Pop=%d, Pc=%.2f, Pm=%.4f, Gen=%d ... \n", 
                                          configLabel, populationSize, crossoverProb, mutationProb, generations);
                        
                        // 5. Repeticiones
                        for (int rep = 1; rep <= REPETITIONS; rep++) {
                            
                            // La semilla de JMetal para la aleatoriedad de los operadores
                            // Usaremos una seed única para cada (config, rep) para la reproducibilidad de la corrida.
                            long runSeed = ALGORITHM_TEST_SEED + (long) configId * 1000 + rep; 
                            
                            try {
                                // A. Instanciamos el problema NUEVO para cada corrida
                                FUHSchedulingProblem problem = new FUHSchedulingProblem(
                                    data.validSlots, 
                                    data.matchInfos, 
                                    data.courtConfigs, 
                                    data.priorities, 
                                    data.categoryBlocks
                                );

                                // B. INYECTAR LA SOLUCIÓN INICIAL FIJA
                                problem.setSeedSolution(fixedInitialSeed);
                                
                                // C. Ejecutamos usando el método en FUHRunner
                                ExperimentResult result = FUHRunner.runSingleNSGAII(
                                    problem,
                                    data.validSlots,
                                    populationSize,
                                    crossoverProb,
                                    mutationProb,
                                    maxEvaluations,
                                    runSeed // Seed de JMetal para el control de operadores
                                );
                                
                                // D. Guardamos resultados
                                saveRawData(resultsFile, PROBLEM_NAME, configId, rep, populationSize, 
                                            crossoverProb, mutationProb, generations, runSeed, result);
                                
                                System.out.print("✅");
                                
                            } catch (Exception e) {
                                System.err.printf("\n    ❌ Error en %s, Rep %d: %s\n", configLabel, rep, e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        System.out.println(" (Finalizadas " + REPETITIONS + " repeticiones)"); 
                    }
                }
            }
        }
        
        long endTimeTotal = System.currentTimeMillis();
        long durationTotal = (endTimeTotal - startTimeTotal) / 1000;
        
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║ EXPERIMENTACIÓN FINALIZADA                   ║");
        System.out.printf("║ Tiempo Total: %d segundos                    ║\n", durationTotal);
        System.out.printf("║ Resultados: %s           ║\n", resultsFile);
        System.out.println("╚══════════════════════════════════════════════╝");
    }
    
    // =========================================================
    // Métodos Auxiliares
    // =========================================================
    
    private static ExcelLoader.DataResult loadDataFromExcel(String filePath) {
        try {
            return FUHRunner.loadDataFromExcel(filePath); 
        } catch (Exception e) {
            System.err.println("❌ FATAL: Excepción al cargar Excel: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Carga la solución inicial (fixture) desde el archivo especificado.
     * Esta solución se usará para inyectar al Problem en cada corrida.
     */
    private static IntegerSolution loadAndVerifyInitialSeed(ExcelLoader.DataResult data) {
        System.out.println("⏳ Cargando solución inicial desde: " + INITIAL_SEED_PATH);
        try {
            // El problema necesita ser instanciado solo para validar la solución
            FUHSchedulingProblem tempProblem = new FUHSchedulingProblem(
                data.validSlots, data.matchInfos, data.courtConfigs, data.priorities, data.categoryBlocks
            );
            
            IntegerSolution seed = FixtureSeeder.createSolutionFromExcel(
                    INITIAL_SEED_PATH, 
                    tempProblem, // Se usa para validar el tamaño y slots
                    data.matchInfos, 
                    data.validSlots
            );
            
            if (seed != null) {
                System.out.println("✅ Solución inicial cargada correctamente. Size: " + seed.variables().size() + " variables.");
                return seed;
            } else {
                System.err.println("❌ FixtureSeeder no pudo generar la solución inicial. El archivo puede ser inválido o no existe.");
                return null;
            }

        } catch (Exception e) {
            System.err.println("❌ Excepción al cargar la solución inicial: " + e.getMessage());
            return null;
        }
    }

    private static void initializeResultsFile(String fileName) {
        try (FileWriter writer = new FileWriter(fileName, false)) {
            writer.write("RunID,ConfigID,Repetition,Problema,PopSize,CrossoverProb,MutationProb,Generations,TiempoMs,SeedJMetal,SolutionID,Objetivo,Valor\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveRawData(
            String fileName,
            String problemName,
            int configId, 
            int rep, 
            int popSize, 
            double crossoverProb, 
            double mutationProb, 
            int generations, 
            long seedJMetal, // La semilla usada por JMetal
            ExperimentResult result) {
            
            String runId = String.format("C%03d_R%02d", configId, rep);
            
            try (FileWriter writer = new FileWriter(fileName, true)) {
                long executionTimeMs = result.executionTimeMs;
                int solutionId = 0; 

                for (IntegerSolution sol : result.solutions) {
                    solutionId++; 
                    
                    // O1
                    writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.4f,%d,%d,%d,%d,O1,%.6f\n",
                        runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
                        generations, executionTimeMs, seedJMetal, solutionId, sol.objectives()[0]));
                    
                    // O2
                    writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.4f,%d,%d,%d,%d,O2,%.6f\n",
                        runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
                        generations, executionTimeMs, seedJMetal, solutionId, sol.objectives()[1]));
                    
                    // Restricción
                    // Se asume que constraints()[0] es la violación total de restricciones
                    writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.4f,%d,%d,%d,%d,Restriccion,%.6f\n",
                        runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
                        generations, executionTimeMs, seedJMetal, solutionId, sol.constraints()[0]));
                }
            } catch (IOException e) {
                System.err.println("Error escritura CSV: " + e.getMessage());
            }
    }
}