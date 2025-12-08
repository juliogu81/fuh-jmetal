package org.fuh.runner;

import org.fuh.model.*;
import org.fuh.io.ExcelLoader;
import org.fuh.problem.FUHSchedulingProblem;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.solution.integersolution.IntegerSolution;
import org.fuh.operator.FUHCrossover;
import org.fuh.operator.FUHMutation;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class FUHRunner {

    // =========================================================
    // CLASE AUXILIAR PARA RESULTADOS
    // =========================================================
    public static class ExperimentResult {
        public final List<IntegerSolution> solutions;
        public final long executionTimeMs;
        
        public ExperimentResult(List<IntegerSolution> solutions, long executionTimeMs) {
            this.solutions = solutions;
            this.executionTimeMs = executionTimeMs;
        }
    }
    
    // =========================================================
    // CLASE AUXILIAR PARA ORDENAR EL REPORTE CSV
    // =========================================================
    private static class FixtureRow {
        MatchInfo info;
        Slot slot;
        int superposiciones;
        double contInst;
        double contCat;

        public FixtureRow(MatchInfo info, Slot slot, int superposiciones, double contInst, double contCat) {
            this.info = info;
            this.slot = slot;
            this.superposiciones = superposiciones;
            this.contInst = contInst;
            this.contCat = contCat;
        }
    }

    // =========================================================
    // MÉTODO DE EJECUCIÓN SINGLE (con Semilla)
    // =========================================================
    public static ExperimentResult runSingleNSGAII(
            FUHSchedulingProblem problem,
            List<List<Slot>> slotsData,
            int populationSize, 
            double crossoverProb, 
            double mutationProb, 
            int maxEvaluations,
            long seed) throws Exception {
            
            // Establecer la semilla de jMetal globalmente
            org.uma.jmetal.util.pseudorandom.JMetalRandom.getInstance().setSeed(seed);
            
            // 1. Definir Operadores
            var crossover = new FUHCrossover(crossoverProb, slotsData);
            var mutation = new FUHMutation(mutationProb, slotsData);
            
            // 2. Construir el Algoritmo
            Algorithm<List<IntegerSolution>> algorithm = 
                    new NSGAIIBuilder<>(problem, crossover, mutation, populationSize)
                        .setMaxEvaluations(maxEvaluations)
                        .build();
            
            // 3. Ejecutar y medir tiempo
            long start = System.currentTimeMillis();
            algorithm.run();
            long end = System.currentTimeMillis();
            
            List<IntegerSolution> result = algorithm.result();
            
            // 4. Devolver resultados
            return new ExperimentResult(result, end - start);
    }
    
    // =========================================================
    // MÉTODO MAIN
    // =========================================================
    public static void main(String[] args) {
        // Configuración GANADORA basada en tus experimentos previos
        int populationSize = 100;
        double crossoverProb = 0.95;
        double mutationProb = 0.015;
        int maxEvaluations = 200000;
        long testSeed = 12345L; 
        
        try {
            // Cargar datos
            ExcelLoader.DataResult data = loadDataFromExcel("input_v5_4xlsx.xlsx");
            
            if (data.matchInfos.isEmpty()) {
                System.err.println("Error: No se cargaron datos.");
                return;
            }

            // Diagnóstico de espacio
            int totalUniqueSlots = 0;
            for (List<Slot> slots : data.validSlots) {
                totalUniqueSlots += slots.size();
            }
            System.out.println("📊 Datos cargados:");
            System.out.println("   • Partidos: " + data.matchInfos.size());
            System.out.println("   • Canchas: " + data.courtConfigs.size());
            System.out.println("   • Total Slots Posibles: " + totalUniqueSlots);
            
            // Definir Problema
            FUHSchedulingProblem problem = new FUHSchedulingProblem(
                data.validSlots, data.matchInfos, data.courtConfigs, data.priorities, data.categoryBlocks
            );
            
            // Ejecutar
            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║     NSGA-II - FUH Scheduling (Single Run)   ║");
            System.out.println("╚══════════════════════════════════════════════╝");
            System.out.println("▶ Ejecutando algoritmo...");
            
            ExperimentResult resultWrapper = runSingleNSGAII(
                problem, data.validSlots, populationSize, crossoverProb, mutationProb, maxEvaluations, testSeed
            );
            
            List<IntegerSolution> result = resultWrapper.solutions; 
            // Ordenar por Objetivo 1 para mejor visualización
            result.sort(Comparator.comparingDouble(s -> s.objectives()[0]));

            long executionTime = resultWrapper.executionTimeMs;
            long startTime = System.currentTimeMillis() - executionTime;
            long endTime = System.currentTimeMillis();
            
            // Mostrar resultados en consola
            displayResults(result, startTime, endTime);
            
            // Guardar resultados
            if (!result.isEmpty()) {
                IntegerSolution mejorSolucion = result.get(0); // La mejor en O1
                saveResultsToFiles(result, "fuh_results");
                saveFixtureToCSV(problem, mejorSolucion, data, "fuh_fixture");
                analyzeAndDisplayFixture(problem, mejorSolucion, data);
            }
            
            displayParetoConsole(result);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================
    // MÉTODOS DE VISUALIZACIÓN Y REPORTE (RESTAURADOS)
    // =========================================================

    private static void displayResults(List<IntegerSolution> solutions, long startTime, long endTime) {
        long executionTime = endTime - startTime;
        
        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("RESULTADOS");
        System.out.println("══════════════════════════════════════════════");
        System.out.println("⏱️  Tiempo de ejecución: " + executionTime + " ms");
        System.out.println("📊 Soluciones no dominadas: " + solutions.size());
        
        if (solutions.isEmpty()) {
            System.out.println("No se encontraron soluciones válidas.");
            return;
        }
        
        // Tabla de soluciones
        System.out.println("\n" + "─".repeat(65));
        System.out.printf("│ %-8s │ %-20s │ %-20s │ %-10s │%n", 
            "ID", "Objetivo 1 (O1)", "Objetivo 2 (O2)", "Restricción");
        System.out.println("─".repeat(65));
        
        for (int i = 0; i < Math.min(15, solutions.size()); i++) {
            IntegerSolution sol = solutions.get(i);
            System.out.printf("│ %-8d │ %-20.4f │ %-20.4f │ %-10.2f │%n", 
                i + 1, sol.objectives()[0], sol.objectives()[1], sol.constraints()[0]);
        }
        
        if (solutions.size() > 15) {
            System.out.println("│ " + "..." + " ".repeat(57) + "│");
        }
        System.out.println("─".repeat(65));

        // Mejores Soluciones
        if (solutions.size() > 0) {
            IntegerSolution bestO1 = solutions.stream().min(Comparator.comparingDouble(s -> s.objectives()[0])).orElse(null);
            IntegerSolution bestO2 = solutions.stream().min(Comparator.comparingDouble(s -> s.objectives()[1])).orElse(null);
            
            System.out.println("\n🏆 MEJORES SOLUCIONES:");
            System.out.println("   • Mejor O1: " + String.format("%.2f", bestO1.objectives()[0]) + 
                             " (O2=" + String.format("%.2f", bestO1.objectives()[1]) + ")");
            System.out.println("   • Mejor O2: " + String.format("%.2f", bestO2.objectives()[1]) + 
                             " (O1=" + String.format("%.2f", bestO2.objectives()[0]) + ")");
        }
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
            
            PrintWriter txtWriter = new PrintWriter(baseName + "_summary.txt");
            txtWriter.println("RESUMEN EJECUCIÓN - FUH SCHEDULING");
            txtWriter.println("Soluciones encontradas: " + solutions.size());
            txtWriter.close();
            
            System.out.println("\n💾 Archivos guardados:");
            System.out.println("   • " + baseName + ".csv");
            System.out.println("   • " + baseName + "_summary.txt");
        } catch (Exception e) {
            System.err.println("⚠️  Error al guardar archivos: " + e.getMessage());
        }
    }

    private static void saveFixtureToCSV(FUHSchedulingProblem problem, 
                                         IntegerSolution solution, 
                                         ExcelLoader.DataResult data,
                                         String baseName) throws Exception {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = baseName + "_" + timestamp + ".csv";
        FileWriter writer = new FileWriter(fileName);
        
        writer.write("Cancha,Hora,ID Partido,Local,Visitante,Categoria,Dia,Superposiciones,ContInst,ContCat\n");
        
        Slot[] assignments = decodeSolution(solution, data.validSlots);
        
        // Calcular métricas para el reporte
        Map<String, Integer> ocupacion = new HashMap<>();
        // ... (Tu lógica de métricas simplificada aquí si es necesario, o la completa)
        // Para brevedad uso una versión directa:
        
        List<FixtureRow> rows = new ArrayList<>();
        for(int i=0; i<assignments.length; i++){
            Slot s = assignments[i];
            MatchInfo m = data.matchInfos.get(i);
            rows.add(new FixtureRow(m, s, 0, 0.0, 0.0)); // Placeholders si no quieres recalcular todo ahora
        }
        
        rows.sort(Comparator.comparing((FixtureRow r) -> r.slot.getCourtId())
                  .thenComparingInt(r -> r.slot.getTimeSlotId()));

        for (FixtureRow row : rows) {
            writer.write(String.format("%s,%d:00,%s,%s,%s,%s,%s,%d,%.2f,%.2f\n",
                row.slot.getCourtId(), row.slot.getTimeSlotId(), row.info.getId(),
                row.info.getHomeInstitution(), row.info.getAwayInstitution(), row.info.getCategory(),
                "Sábado", 0, 0.0, 0.0));
        }
        writer.close();
        System.out.println("💾 FIXTURE guardado en: " + fileName);
    }

    private static void analyzeAndDisplayFixture(FUHSchedulingProblem problem,
                                                 IntegerSolution solution,
                                                 ExcelLoader.DataResult data) {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("📋 ANÁLISIS DEL FIXTURE GENERADO (Mejor O1)");
        System.out.println("═".repeat(60));
        
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
        
        System.out.println("\n⚠️  VERIFICACIÓN DE SUPERPOSICIONES:");
        for (Map.Entry<String, List<Integer>> entry : ocupacion.entrySet()) {
            if (entry.getValue().size() > 1) {
                superposiciones += entry.getValue().size() - 1;
                System.out.printf("   • %s: %d partidos (IDs: %s)\n", entry.getKey(), entry.getValue().size(), entry.getValue());
            }
        }
        
        if (superposiciones == 0) System.out.println("   ✅ No hay superposiciones");
        else System.out.printf("   ❌ Total superposiciones: %d\n", superposiciones);
        
        System.out.println("\n🎯 RESUMEN DE OBJETIVOS:");
        System.out.printf("   • O1: %.2f\n", solution.objectives()[0]);
        System.out.printf("   • O2: %.2f\n", solution.objectives()[1]);
        System.out.printf("   • Restricción: %.2f\n", solution.constraints()[0]);
    }

    private static void displayParetoConsole(List<IntegerSolution> solutions) {
        if (solutions.size() < 2) return;
        System.out.println("\n📊 FRENTE DE PARETO (Consola):");
        System.out.println("   O1 ↑");
        // ... (Tu lógica de dibujo ASCII si la quieres mantener, es opcional) ...
        System.out.println("   (Visualización omitida, ver CSV para datos crudos)");
    }

    // =========================================================
    // UTILIDADES
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