package org.fuh.runner;

import org.fuh.model.*;
import org.fuh.io.ExcelLoader;
import org.fuh.problem.FUHSchedulingProblem;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.fuh.operator.FUHCrossover;
import org.fuh.operator.FUHMutation;
// 🔥 1. IMPORTAMOS LA INTERFAZ QUE EXIGE EL BUILDER
import org.uma.jmetal.util.comparator.dominanceComparator.DominanceComparator;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class FUHRunner {

    // =========================================================
    // 1. CLASES AUXILIARES (Resultados y Reportes)
    // =========================================================
    public static class ExperimentResult {
        public final List<IntegerSolution> solutions;
        public final long executionTimeMs;
        
        public ExperimentResult(List<IntegerSolution> solutions, long executionTimeMs) {
            this.solutions = solutions;
            this.executionTimeMs = executionTimeMs;
        }
    }
    
    private static class FixtureRow {
        MatchInfo info;
        Slot slot;
        public FixtureRow(MatchInfo info, Slot slot) {
            this.info = info;
            this.slot = slot;
        }
    }

    // =========================================================
    // 2. COMPARADOR MANUAL (CORREGIDO DE TIPO)
    // =========================================================
    /**
     * 🔥 CORRECCIÓN CLAVE:
     * Ahora implementamos 'DominanceComparator<IntegerSolution>' en lugar de solo 'Comparator'.
     * Esto satisface el requisito estricto del NSGAIIBuilder.
     */
 // =========================================================
    // COMPARADOR CON DEBUG (Reemplaza la clase anterior)
    // =========================================================
    public static class ManualComparator implements DominanceComparator<IntegerSolution> {
        
        // Un contador estático para no saturar la consola (imprimimos solo las primeras 50 batallas interesantes)
        private static int debugCounter = 0; 
        
        @Override
        public int compare(IntegerSolution s1, IntegerSolution s2) {
            double v1 = sumViolations(s1);
            double v2 = sumViolations(s2);

            // LOGICA DE DEBUG: Queremos ver cuando una Válida mata a una Inválida
            boolean interestingCase = (v1 == 0 && v2 < 0) || (v1 < 0 && v2 == 0);
            
            if (interestingCase && debugCounter < 20) {
                debugCounter++;
                System.out.println("\n⚔️ --- BATALLA DE SOLUCIONES ---");
                System.out.printf("   🥊 S1: [Restr: %.1f | O1: %.1f | O2: %.1f]\n", v1, s1.objectives()[0], s1.objectives()[1]);
                System.out.printf("   🥊 S2: [Restr: %.1f | O1: %.1f | O2: %.1f]\n", v2, s2.objectives()[0], s2.objectives()[1]);
            }

            // Regla A: Válido vs Inválido
            if (v1 == 0 && v2 < 0) {
                if(interestingCase && debugCounter <= 20) System.out.println("   🏆 GANA S1 (Por ser Válida)");
                return -1; 
            }
            if (v1 < 0 && v2 == 0) {
                if(interestingCase && debugCounter <= 20) System.out.println("   🏆 GANA S2 (Por ser Válida)");
                return 1;
            }

            // Regla B: Inválido vs Inválido
            if (v1 < 0 && v2 < 0) {
                if (v1 > v2) return -1;
                if (v2 > v1) return 1;
                return 0;
            }

            // Regla C: Válido vs Válido (Pareto)
            int result = compareObjectives(s1, s2);
            if (v1 == 0 && v2 == 0 && result != 0 && debugCounter < 20) {
                 // Si ambas son validas y una gana a la otra
                 System.out.println("   ⚖️ Ambas Válidas -> Gana " + (result == -1 ? "S1" : "S2") + " por Objetivos");
                 debugCounter++; 
            }
            return result;
        }

        private double sumViolations(IntegerSolution s) {
            double total = 0.0;
            for (double v : s.constraints()) if (v < 0) total += v;
            return total;
        }

        private int compareObjectives(IntegerSolution s1, IntegerSolution s2) {
            int dom1 = 0; int dom2 = 0;
            for (int i = 0; i < s1.objectives().length; i++) {
                double val1 = s1.objectives()[i];
                double val2 = s2.objectives()[i];
                if (val1 < val2) dom1 = 1;
                else if (val2 < val1) dom2 = 1;
            }
            if (dom1 == 1 && dom2 == 0) return -1;
            if (dom2 == 1 && dom1 == 0) return 1;
            return 0;
        }
    }

    // =========================================================
    // 3. MÉTODO DE EJECUCIÓN (NSGA-II)
    // =========================================================
    public static ExperimentResult runSingleNSGAII(
            FUHSchedulingProblem problem,
            List<List<Slot>> slotsData,
            int populationSize, 
            double crossoverProb, 
            double mutationProb, 
            int maxEvaluations,
            long seed) throws Exception {
            
            // Semilla para reproducibilidad
            org.uma.jmetal.util.pseudorandom.JMetalRandom.getInstance().setSeed(seed);
            
            // Definir Operadores
            var crossover = new FUHCrossover(crossoverProb, slotsData);
            var mutation = new FUHMutation(mutationProb, slotsData);
            
            // Construir el Algoritmo con el COMPARADOR MANUAL
            Algorithm<List<IntegerSolution>> algorithm = 
                    new NSGAIIBuilder<>(problem, crossover, mutation, populationSize)
                        .setMaxEvaluations(maxEvaluations)
                        // 🔥 AHORA SÍ: El tipo coincide exactamente con lo que pide el Builder
                        .setDominanceComparator(new ManualComparator())
                        .build();
            
            // Ejecutar y medir tiempo
            long start = System.currentTimeMillis();
            algorithm.run();
            long end = System.currentTimeMillis();
            
            return new ExperimentResult(algorithm.result(), end - start);
    }
    
    // =========================================================
    // 4. MÉTODO MAIN
    // =========================================================
    public static void main(String[] args) {
        // Configuración Recomendada
        int populationSize = 100;
        double crossoverProb = 0.95;
        double mutationProb = 0.1;
        int maxEvaluations = 50000; // 50k es un buen número para empezar
        long testSeed = 12345L; 
        
        try {
            // Cargar datos
            ExcelLoader.DataResult data = loadDataFromExcel("/Users/juliogu/Documentos/git/ae-fixture/data/entrada/06_8-9_ae.xlsx");
            
            if (data.matchInfos.isEmpty()) {
                System.err.println("Error: No se cargaron datos.");
                return;
            }

            // Diagnóstico de espacio real
            Set<String> uniquePhysicalSlots = new HashSet<>();
            for (List<Slot> matchOptions : data.validSlots) {
                for (Slot s : matchOptions) {
                    String uniqueKey = s.getCourtId() + "_" + s.getTimeSlotId();
                    uniquePhysicalSlots.add(uniqueKey);
                }
            }
            int totalPhysicalCapacity = uniquePhysicalSlots.size();

            System.out.println("📊 Datos cargados:");
            System.out.println("   • Partidos: " + data.matchInfos.size());
            System.out.println("   • Canchas: " + data.courtConfigs.size());
            System.out.println("   • Capacidad Real (Slots): " + totalPhysicalCapacity);
            
            // Definir Problema
            FUHSchedulingProblem problem = new FUHSchedulingProblem(
                data.validSlots, data.matchInfos, data.courtConfigs, data.priorities, data.categoryBlocks
            );
            
            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║   NSGA-II - FUH Scheduling (Modo Manual)    ║");
            System.out.println("╚══════════════════════════════════════════════╝");
            System.out.println("▶ Ejecutando algoritmo...");
            
            ExperimentResult resultWrapper = runSingleNSGAII(
                problem, data.validSlots, populationSize, crossoverProb, mutationProb, maxEvaluations, testSeed
            );
            
            List<IntegerSolution> result = resultWrapper.solutions; 
            // Ordenar por Objetivo 1 para mejor visualización
            result.sort(Comparator.comparingDouble(s -> s.objectives()[0]));

            // Mostrar resultados en consola
            displayResults(result, 0, resultWrapper.executionTimeMs);
            
            // Guardar resultados si hay algo válido
            if (!result.isEmpty()) {
                IntegerSolution mejorSolucion = result.get(0);
                saveResultsToFiles(result, "fuh_results");
                saveFixtureToCSV(problem, mejorSolucion, data, "fuh_fixture");
                analyzeAndDisplayFixture(problem, mejorSolucion, data);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error Crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================
    // 5. MÉTODOS DE VISUALIZACIÓN Y REPORTE
    // =========================================================

    private static void displayResults(List<IntegerSolution> solutions, long startTime, long endTime) {
        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("RESULTADOS (" + solutions.size() + " soluciones)");
        System.out.println("══════════════════════════════════════════════");
        System.out.println("⏱️  Tiempo: " + endTime + " ms");
        
        if (solutions.isEmpty()) {
            System.out.println("⚠️ No se encontraron soluciones.");
            return;
        }
        
        System.out.println("\n" + "─".repeat(65));
        System.out.printf("│ %-8s │ %-20s │ %-20s │ %-10s │%n", "ID", "Objetivo 1", "Objetivo 2", "Restr");
        System.out.println("─".repeat(65));
        
        for (int i = 0; i < Math.min(15, solutions.size()); i++) {
            IntegerSolution sol = solutions.get(i);
            System.out.printf("│ %-8d │ %-20.4f │ %-20.4f │ %-10.2f │%n", 
                i + 1, sol.objectives()[0], sol.objectives()[1], sol.constraints()[0]);
        }
        System.out.println("─".repeat(65));
    }

    private static void saveResultsToFiles(List<IntegerSolution> solutions, String baseName) {
        if (solutions.isEmpty()) return;
        try {
            FileWriter csvWriter = new FileWriter(baseName + ".csv");
            csvWriter.write("ID,Objetivo1,Objetivo2,Restriccion\n");
            for (int i = 0; i < solutions.size(); i++) {
                IntegerSolution sol = solutions.get(i);
                csvWriter.write(String.format("%d,%.6f,%.6f,%.6f\n", 
                    i + 1, sol.objectives()[0], sol.objectives()[1], sol.constraints()[0]));
            }
            csvWriter.close();
            System.out.println("💾 Resultados guardados en: " + baseName + ".csv");
        } catch (Exception e) {
            System.err.println("⚠️ Error guardando CSV: " + e.getMessage());
        }
    }

    private static void saveFixtureToCSV(FUHSchedulingProblem problem, 
                                         IntegerSolution solution, 
                                         ExcelLoader.DataResult data,
                                         String baseName) throws Exception {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = baseName + "_" + timestamp + ".csv";
        FileWriter writer = new FileWriter(fileName);
        
        writer.write("Cancha,Hora,ID Partido,Local,Visitante,Categoria\n");
        
        Slot[] assignments = decodeSolution(solution, data.validSlots);
        List<FixtureRow> rows = new ArrayList<>();
        
        for(int i=0; i<assignments.length; i++){
            Slot s = assignments[i];
            MatchInfo m = data.matchInfos.get(i);
            rows.add(new FixtureRow(m, s));
        }
        
        // Ordenar por Cancha y Hora
        rows.sort(Comparator.comparing((FixtureRow r) -> r.slot.getCourtId())
                  .thenComparingInt(r -> r.slot.getTimeSlotId()));

        for (FixtureRow row : rows) {
            writer.write(String.format("%s,%d:00,%s,%s,%s,%s\n",
                row.slot.getCourtId(), row.slot.getTimeSlotId(), row.info.getId(),
                row.info.getHomeInstitution(), row.info.getAwayInstitution(), row.info.getCategory()));
        }
        writer.close();
        System.out.println("💾 Fixture detallado guardado en: " + fileName);
    }

    private static void analyzeAndDisplayFixture(FUHSchedulingProblem problem,
                                                 IntegerSolution solution,
                                                 ExcelLoader.DataResult data) {
        Slot[] assignments = decodeSolution(solution, data.validSlots);
        
        // Verificar superposiciones
        Map<String, List<Integer>> ocupacion = new HashMap<>();
        int superposiciones = 0;
        for (int i = 0; i < assignments.length; i++) {
            Slot slot = assignments[i];
            String clave = slot.getCourtId() + "-" + slot.getTimeSlotId();
            ocupacion.putIfAbsent(clave, new ArrayList<>());
            ocupacion.get(clave).add(i);
        }
        
        System.out.println("\n🔍 ANÁLISIS DE FACTIBILIDAD:");
        for (Map.Entry<String, List<Integer>> entry : ocupacion.entrySet()) {
            if (entry.getValue().size() > 1) {
                superposiciones += entry.getValue().size() - 1;
                System.out.printf("   ❌ CHOQUE en %s: %d partidos (IDs: %s)\n", entry.getKey(), entry.getValue().size(), entry.getValue());
            }
        }
        
        if (superposiciones == 0) System.out.println("   ✅ ¡FIXTURE VÁLIDO! (0 Superposiciones)");
        else System.out.printf("   ❌ Total superposiciones: %d\n", superposiciones);
    }

    // =========================================================
    // 6. UTILIDADES
    // =========================================================
    public static ExcelLoader.DataResult loadDataFromExcel(String filePath) throws Exception {
        ExcelLoader loader = new ExcelLoader();
        return loader.loadFromExcel(filePath);
    }

    private static Slot[] decodeSolution(IntegerSolution solution, List<List<Slot>> validSlots) {
        Slot[] assignments = new Slot[solution.variables().size()];
        for (int i = 0; i < solution.variables().size(); i++) {
            int slotIndex = solution.variables().get(i);
            assignments[i] = validSlots.get(i).get(slotIndex);
        }
        return assignments;
    }
}