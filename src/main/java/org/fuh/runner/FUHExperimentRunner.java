package org.fuh.runner;

import org.fuh.io.ExcelLoader;
import org.fuh.problem.FUHSchedulingProblem;
import org.fuh.runner.FUHRunner.ExperimentResult; // Importamos la clase interna
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FUHExperimentRunner {

    // --- Configuración Global del Experimento ---
    private static final int REPETITIONS = 50; // Cambiar a 30 o 50 para el paper final
    private static final String PROBLEM_NAME = "HandballFixture"; 
    // Asegúrate de que este nombre sea correcto:
    private static final String EXCEL_FILE_PATH = "06_8-9_ae.xlsx"; 

    // --- Hiperparámetros a Testear ---
    private static final int[] POPULATION_SIZES = {100, 150, 200};
    private static final double[] CROSSOVER_PROBS = {1.0, 0.6, 0.9};
    private static final double[] MUTATION_PROBS = {0.01, 0.05, 0.001}; 
    private static final int[] GENERATIONS = {1000, 1500}; 

    public static void main(String[] args) {
        
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║     INICIANDO CALIBRACIÓN PARAMÉTRICA        ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        
        int totalRuns = POPULATION_SIZES.length * CROSSOVER_PROBS.length * MUTATION_PROBS.length * GENERATIONS.length * REPETITIONS;
        System.out.printf("Problema: %s\n", PROBLEM_NAME);
        System.out.printf("Total de Corridas estimadas: %d\n", totalRuns);
        System.out.println("------------------------------------------------");
        
        // 1. Cargar los datos UNA sola vez (son de solo lectura)
        ExcelLoader.DataResult data = loadDataFromExcel(EXCEL_FILE_PATH); 
        
        if (data == null || data.matchInfos.isEmpty()) {
            System.err.println("❌ ERROR: No hay datos para procesar. Abortando.");
            return;
        }

        // Preparar archivo de salida
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String resultsFile = "calibration_results_" + timestamp + ".csv";
        initializeResultsFile(resultsFile);
        
        int configId = 0;
        int currentRunIndex = 0;
        long startTimeTotal = System.currentTimeMillis();
        
        // 2. Bucles anidados (Grid Search)
        for (int populationSize : POPULATION_SIZES) {
            for (double crossoverProb : CROSSOVER_PROBS) {
                for (double mutationProb : MUTATION_PROBS) {
                    for (int generations : GENERATIONS) { 
                        
                        configId++;
                        String configLabel = String.format("C%02d", configId);
                        int maxEvaluations = populationSize * generations; 

                        System.out.printf("[%d/%d] Config %s: Pop=%d, Pc=%.2f, Pm=%.3f, Gen=%d ... ", 
                                          (configId), (totalRuns/REPETITIONS), configLabel, populationSize, crossoverProb, mutationProb, generations);
                        
                        // 3. Repeticiones
                        for (int rep = 1; rep <= REPETITIONS; rep++) {
                            currentRunIndex++;
                            
                            // Semilla única y reproducible para cada repetición de cada config
                            long seed = (long) configId * 1000 + rep; 
                            
                            try {
                                // A. Instanciamos el problema NUEVO para cada corrida (limpieza total)
                                FUHSchedulingProblem problem = new FUHSchedulingProblem(
                                    data.validSlots, 
                                    data.matchInfos, 
                                    data.courtConfigs, 
                                    data.priorities, 
                                    data.categoryBlocks
                                );

                                // B. Ejecutamos usando el MISMO método que tu runner principal
                                ExperimentResult result = FUHRunner.runSingleNSGAII(
                                    problem,
                                    data.validSlots,
                                    populationSize,
                                    crossoverProb,
                                    mutationProb,
                                    maxEvaluations,
                                    seed 
                                );
                                
                                // C. Guardamos resultados
                                saveRawData(resultsFile, PROBLEM_NAME, configId, rep, populationSize, 
                                            crossoverProb, mutationProb, generations, seed, result);
                                
                            } catch (Exception e) {
                                System.err.printf("\n    ❌ Error en %s, Rep %d: %s\n", configLabel, rep, e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        System.out.println("✅"); 
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
            // Reutilizamos el método estático de tu Runner
            return FUHRunner.loadDataFromExcel(filePath); 
        } catch (Exception e) {
            System.err.println("❌ FATAL: Excepción al cargar Excel: " + e.getMessage());
            return null;
        }
    }

    private static void initializeResultsFile(String fileName) {
        try (FileWriter writer = new FileWriter(fileName, false)) {
            writer.write("RunID,ConfigID,Repetition,Problema,PopSize,CrossoverProb,MutationProb,Generations,TiempoMs,Seed,SolutionID,Objetivo,Valor\n");
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
            long seed, 
            ExperimentResult result) {
            
            String runId = String.format("C%02d_R%02d", configId, rep);
            
            try (FileWriter writer = new FileWriter(fileName, true)) {
                long executionTimeMs = result.executionTimeMs;
                int solutionId = 0; 

                for (IntegerSolution sol : result.solutions) {
                    solutionId++; 
                    
                    // O1
                    writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.3f,%d,%d,%d,%d,O1,%.6f\n",
                        runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
                        generations, executionTimeMs, seed, solutionId, sol.objectives()[0]));
                    
                    // O2
                    writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.3f,%d,%d,%d,%d,O2,%.6f\n",
                        runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
                        generations, executionTimeMs, seed, solutionId, sol.objectives()[1]));
                    
                    // Restricción (Opcional, pero muy útil para filtrar luego)
                    writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.3f,%d,%d,%d,%d,Restriccion,%.6f\n",
                        runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
                        generations, executionTimeMs, seed, solutionId, sol.constraints()[0]));
                }
            } catch (IOException e) {
                System.err.println("Error escritura CSV: " + e.getMessage());
            }
    }
}