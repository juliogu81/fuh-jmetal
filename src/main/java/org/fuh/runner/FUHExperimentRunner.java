package org.fuh.runner;

import org.fuh.io.ExcelLoader;
import org.fuh.problem.FUHSchedulingProblem;
import org.fuh.runner.FUHRunner.ExperimentResult;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class FUHExperimentRunner {

    // --- Configuración Global del Experimento ---
    // NOTA: Se recomienda cambiar REPETITIONS a 50 para la calibración final.
    private static final int REPETITIONS = 1; 
    private static final String PROBLEM_NAME = "HandballFixture"; 
    private static final String EXCEL_FILE_PATH = "input_v5_4xlsx.xlsx"; 

    // --- Hiperparámetros a Testear (54 combinaciones) ---
    private static final int[] POPULATION_SIZES = {100, 150, 200};
    private static final double[] CROSSOVER_PROBS = {1.0, 0.9, 0.6};
    private static final double[] MUTATION_PROBS = {0.1, 0.01, 0.05}; 
    private static final int[] GENERATIONS = {500, 1000}; 

    public static void main(String[] args) {
        
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║     INICIANDO CALIBRACIÓN PARAMÉTRICA        ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.printf("Problema: %s\n", PROBLEM_NAME);
        System.out.printf("Total de Corridas: %d\n", POPULATION_SIZES.length * CROSSOVER_PROBS.length * MUTATION_PROBS.length * GENERATIONS.length * REPETITIONS);
        System.out.println("------------------------------------------------");
        
        // 1. Cargar la instancia del problema una sola vez
        ExcelLoader.DataResult data = loadDataFromExcel(EXCEL_FILE_PATH); 
        FUHSchedulingProblem problem = new FUHSchedulingProblem(
            data.validSlots, 
            data.matchInfos, 
            data.courtConfigs, 
            data.priorities, 
            data.categoryBlocks
        );

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String resultsFile = "calibration_results_" + PROBLEM_NAME + "_" + timestamp + ".csv";
        initializeResultsFile(resultsFile);
        
        int configId = 0;
        
        // 2. Bucles anidados para iterar sobre las 54 configuraciones
        for (int populationSize : POPULATION_SIZES) {
            for (double crossoverProb : CROSSOVER_PROBS) {
                for (double mutationProb : MUTATION_PROBS) {
                    for (int generations : GENERATIONS) { 
                        
                        configId++;
                        String configLabel = String.format("C%02d", configId);
                        int maxEvaluations = populationSize * generations; 

                        System.out.printf("  Corriendo %s: P=%d, Pc=%.1f, Pm=%.3f, G=%d -> Evals: %d\n", 
                                          configLabel, populationSize, crossoverProb, mutationProb, generations, maxEvaluations);
                        
                        // 3. Bucle para las 50 repeticiones
                        for (int rep = 1; rep <= REPETITIONS; rep++) {
                            System.out.print(".");
                            
                            // *** LÓGICA DE LA SEMILLA: GENERACIÓN DE VALOR ÚNICO ***
                            long seed = (long) configId * 100 + rep; 
                            
                            try {
                                // LLAMADA CORREGIDA: Se incluye la semilla
                                ExperimentResult result = FUHRunner.runSingleNSGAII(
                                    problem,
                                    data.validSlots,
                                    populationSize,
                                    crossoverProb,
                                    mutationProb,
                                    maxEvaluations,
                                    seed // <--- ¡Semilla pasada al ejecutor!
                                );
                                
                                // LLAMADA CORREGIDA: Se incluye la semilla
                                saveRawData(resultsFile, PROBLEM_NAME, configId, rep, populationSize, 
                                            crossoverProb, mutationProb, generations, seed, result);
                                
                            } catch (Exception e) {
                                System.err.printf("\n    ❌ Error en %s, Rep %d: %s\n", configLabel, rep, e.getMessage());
                            }
                        }
                        System.out.println(" (Completado)"); 
                    }
                }
            }
        }
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║ EXPERIMENTACIÓN FINALIZADA                   ║");
        System.out.printf("║ Resultados guardados en: %s\n", resultsFile);
        System.out.println("╚══════════════════════════════════════════════╝");
    }
    
    // =========================================================
    // Métodos Auxiliares
    // =========================================================
    
    private static ExcelLoader.DataResult loadDataFromExcel(String filePath) {
        try {
            return FUHRunner.loadDataFromExcel(filePath); 
        } catch (Exception e) {
            System.err.println("❌ FATAL: No se pudieron cargar datos desde " + filePath);
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

	// Y actualizar el encabezado en initializeResultsFile:
	private static void initializeResultsFile(String fileName) {
	    try (FileWriter writer = new FileWriter(fileName, false)) {
	        // AÑADIR LA COLUMNA SolutionID
	        writer.write("RunID,ConfigID,Repetition,Problema,PopSize,CrossoverProb,MutationProb,Generations,TiempoMs,Seed,SolutionID,Objetivo,Valor\n");
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

    /**
     * Guarda el tiempo de ejecución y cada punto (O1, O2) del Frente de Pareto en el archivo CSV.
     */
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
    	        
    	        // --- AÑADIR UN CONTADOR DE SOLUCIÓN DENTRO DE LA CORRIDA ---
    	        int solutionId = 0; 

    	        for (IntegerSolution sol : result.solutions) {
    	            solutionId++; // Incrementar el ID para cada solución no dominada
    	            
    	            // Escritura de O1 (Continuidad Institucional)
    	            writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.3f,%d,%d,%d,%d,O1,%.6f\n",
    	                runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
    	                generations, executionTimeMs, seed, solutionId, sol.objectives()[0])); // <-- solutionId agregada
    	            
    	            // Escritura de O2 (Continuidad por Categoría)
    	            writer.write(String.format("%s,%d,%d,%s,%d,%.3f,%.3f,%d,%d,%d,%d,O2,%.6f\n",
    	                runId, configId, rep, problemName, popSize, crossoverProb, mutationProb, 
    	                generations, executionTimeMs, seed, solutionId, sol.objectives()[1])); // <-- solutionId agregada
    	        }
    	    } catch (IOException e) {
    	        System.err.println("Error al escribir datos crudos para " + runId + ": " + e.getMessage());
    	    }
    }
    }

