package org.fuh.runner;

import org.fuh.model.Slot;
import org.fuh.model.MatchInfo;
import org.fuh.problem.FUHSchedulingProblem;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FUHRunner {

    public static void main(String[] args) {
        // Configuración
        int numMatches = 20;
        int populationSize = 100;
        double crossoverProb = 0.9;
        double mutationProb = 0.01;
        int maxEvaluations = 5000;
        
        // 1. Cargar datos de prueba
        List<List<Slot>> slotsData = loadDummyData(numMatches);
        List<MatchInfo> infoData = loadDummyInfo(numMatches);
        
        // 2. Definir el Problema
        FUHSchedulingProblem problem = new FUHSchedulingProblem(slotsData, infoData);
        
        // 3. Definir Operadores
        var crossover = new IntegerSBXCrossover(crossoverProb, 20.0);
        var mutation = new IntegerPolynomialMutation(mutationProb, 20.0);
        
        // 4. Construir el Algoritmo (NSGA-II)
        Algorithm<List<IntegerSolution>> algorithm = 
            new NSGAIIBuilder<>(problem, crossover, mutation, populationSize)
                .setMaxEvaluations(maxEvaluations)
                .build();
        
        // 5. Mostrar encabezado
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║     NSGA-II - FUH Scheduling                ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.println("║ Partidos: " + numMatches + "                                  ║");
        System.out.println("║ Población: " + populationSize + "                                ║");
        System.out.println("║ Evaluaciones: " + maxEvaluations + "                            ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        
        // 6. Ejecutar
        System.out.println("Ejecutando algoritmo...");
        long start = System.currentTimeMillis();
        algorithm.run();
        long end = System.currentTimeMillis();
        
        // 7. Obtener Resultado
        List<IntegerSolution> result = algorithm.result(); 
        
        // 8. Ordenar por primer objetivo
        result.sort(Comparator.comparingDouble(s -> s.objectives()[0]));
        
        // 9. Mostrar resultados detallados
        displayResults(result, start, end);
        
        // 10. Guardar en archivos
        saveResultsToFiles(result, "fuh_results");
        
        // 11. Mostrar frente de Pareto en consola
        displayParetoConsole(result);
    }
    
    // --- Generador de Datos Falsos ---
    private static List<List<Slot>> loadDummyData(int nMatches) {
        List<List<Slot>> list = new ArrayList<>();
        for (int i = 0; i < nMatches; i++) {
            List<Slot> options = new ArrayList<>();
            // CORRECCIÓN: Slot ahora espera String para courtId
            options.add(new Slot("1", 10)); // Cancha 1, 10:00
            options.add(new Slot("2", 11)); // Cancha 2, 11:00
            options.add(new Slot("1", 12)); // Cancha 1, 12:00
            list.add(options);
        }
        return list;
    }
    
    // --- Método para generar datos de prueba ---
    private static List<MatchInfo> loadDummyInfo(int nMatches) {
        List<MatchInfo> list = new ArrayList<>();
        // Generamos equipos rotativos
        String[] instituciones = {"Club A", "Club B", "Club C", "Club D", "Club E"};
        String[] categorias = {"Juveniles", "Formativas", "Mayores"};
        
        for (int i = 0; i < nMatches; i++) {
            // CORRECCIÓN: MatchInfo necesita 4 parámetros (id, home, away, category)
            String home = instituciones[i % instituciones.length];
            String away = instituciones[(i + 1) % instituciones.length];
            String category = categorias[i % categorias.length];
            
            list.add(new MatchInfo("P" + i, home, away, category));
        }
        return list;
    }
    
    // --- Mostrar resultados en consola ---
    private static void displayResults(List<IntegerSolution> solutions, long startTime, long endTime) {
        long executionTime = endTime - startTime;
        
        System.out.println("\n══════════════════════════════════════════════");
        System.out.println("RESULTADOS");
        System.out.println("══════════════════════════════════════════════");
        System.out.println("Tiempo de ejecución: " + executionTime + " ms");
        System.out.println("Soluciones no dominadas: " + solutions.size());
        
        if (solutions.isEmpty()) {
            System.out.println("No se encontraron soluciones válidas.");
            return;
        }
        
        // Tabla de soluciones
        System.out.println("\n" + "─".repeat(65));
        System.out.printf("│ %-8s │ %-20s │ %-20s │ %-10s │%n", 
            "ID", "Objetivo 1 (O1)", "Objetivo 2 (O2)", "Restricción");
        System.out.println("─".repeat(65));
        
        for (int i = 0; i < Math.min(10, solutions.size()); i++) {
            IntegerSolution sol = solutions.get(i);
            System.out.printf("│ %-8d │ %-20.4f │ %-20.4f │ %-10.2f │%n", 
                i + 1, sol.objectives()[0], sol.objectives()[1], sol.constraints()[0]);
        }
        
        if (solutions.size() > 10) {
            System.out.println("│ " + "..." + " ".repeat(57) + "│");
            System.out.printf("│ %-8s │ %-20s │ %-20s │ %-10s │%n",
                "...", "y " + (solutions.size() - 10) + " más", "", "");
        }
        
        System.out.println("─".repeat(65));
        
        // Estadísticas
        if (solutions.size() > 0) {
            double minO1 = solutions.stream().mapToDouble(s -> s.objectives()[0]).min().orElse(0);
            double maxO1 = solutions.stream().mapToDouble(s -> s.objectives()[0]).max().orElse(0);
            double avgO1 = solutions.stream().mapToDouble(s -> s.objectives()[0]).average().orElse(0);
            
            double minO2 = solutions.stream().mapToDouble(s -> s.objectives()[1]).min().orElse(0);
            double maxO2 = solutions.stream().mapToDouble(s -> s.objectives()[1]).max().orElse(0);
            double avgO2 = solutions.stream().mapToDouble(s -> s.objectives()[1]).average().orElse(0);
            
            System.out.println("\nESTADÍSTICAS:");
            System.out.println("   O1 (Inst.): Min=" + String.format("%.2f", minO1) + 
                             ", Max=" + String.format("%.2f", maxO1) + 
                             ", Avg=" + String.format("%.2f", avgO1));
            System.out.println("   O2 (Cat.):  Min=" + String.format("%.2f", minO2) + 
                             ", Max=" + String.format("%.2f", maxO2) + 
                             ", Avg=" + String.format("%.2f", avgO2));
            
            // Mejores soluciones
            IntegerSolution bestO1 = solutions.stream()
                .min(Comparator.comparingDouble(s -> s.objectives()[0]))
                .orElse(null);
            
            IntegerSolution bestO2 = solutions.stream()
                .min(Comparator.comparingDouble(s -> s.objectives()[1]))
                .orElse(null);
            
            if (bestO1 != null && bestO2 != null) {
                System.out.println("\nMEJORES SOLUCIONES:");
                System.out.println("   • Mejor O1: " + String.format("%.2f", bestO1.objectives()[0]) + 
                                 " (O2=" + String.format("%.2f", bestO1.objectives()[1]) + ")");
                System.out.println("   • Mejor O2: " + String.format("%.2f", bestO2.objectives()[1]) + 
                                 " (O1=" + String.format("%.2f", bestO2.objectives()[0]) + ")");
            }
        }
    }
    
    // --- Guardar resultados en archivos ---
    private static void saveResultsToFiles(List<IntegerSolution> solutions, String baseName) {
        if (solutions.isEmpty()) return;
        
        try {
            // 1. CSV con todas las soluciones
            FileWriter csvWriter = new FileWriter(baseName + ".csv");
            csvWriter.write("ID,Objetivo1,Objetivo2,Restriccion\n");
            
            for (int i = 0; i < solutions.size(); i++) {
                IntegerSolution sol = solutions.get(i);
                csvWriter.write(String.format("%d,%.6f,%.6f,%.6f\n", 
                    i + 1, sol.objectives()[0], sol.objectives()[1], sol.constraints()[0]));
            }
            csvWriter.close();
            
            // 2. TXT con resumen
            PrintWriter txtWriter = new PrintWriter(baseName + "_summary.txt");
            txtWriter.println("=".repeat(50));
            txtWriter.println("RESUMEN EJECUCIÓN - FUH SCHEDULING");
            txtWriter.println("=".repeat(50));
            txtWriter.println("Fecha: " + new java.util.Date());
            txtWriter.println("Soluciones encontradas: " + solutions.size());
            txtWriter.println();
            
            txtWriter.println("MEJORES 5 SOLUCIONES:");
            txtWriter.println("-".repeat(60));
            txtWriter.printf("%-8s %-15s %-15s %-12s%n", 
                "ID", "O1", "O2", "Restricción");
            txtWriter.println("-".repeat(60));
            
            for (int i = 0; i < Math.min(5, solutions.size()); i++) {
                IntegerSolution sol = solutions.get(i);
                txtWriter.printf("%-8d %-15.4f %-15.4f %-12.2f%n", 
                    i + 1, sol.objectives()[0], sol.objectives()[1], sol.constraints()[0]);
            }
            txtWriter.close();
            
            System.out.println("\nArchivos guardados:");
            System.out.println("   • " + baseName + ".csv (datos completos)");
            System.out.println("   • " + baseName + "_summary.txt (resumen)");
            
        } catch (Exception e) {
            System.err.println("Error al guardar archivos: " + e.getMessage());
        }
    }
    
    // --- Visualización simple del frente de Pareto en consola ---
    private static void displayParetoConsole(List<IntegerSolution> solutions) {
        if (solutions.size() < 2) return;
        
        System.out.println("\nFRENTE DE PARETO (visualización simple):");
        System.out.println("   O1 ↑");
        
        // Ordenar por O2 para visualización
        solutions.sort(Comparator.comparingDouble(s -> s.objectives()[1]));
        
        // Escalar valores para la visualización
        double minO1 = solutions.stream().mapToDouble(s -> s.objectives()[0]).min().orElse(0);
        double maxO1 = solutions.stream().mapToDouble(s -> s.objectives()[0]).max().orElse(1);
        double minO2 = solutions.stream().mapToDouble(s -> s.objectives()[1]).min().orElse(0);
        double maxO2 = solutions.stream().mapToDouble(s -> s.objectives()[1]).max().orElse(1);
        
        // Crear representación simple
        int chartHeight = 12;
        int chartWidth = 50;
        
        // Matriz para el gráfico
        char[][] chart = new char[chartHeight][chartWidth];
        for (int i = 0; i < chartHeight; i++) {
            for (int j = 0; j < chartWidth; j++) {
                chart[i][j] = ' ';
            }
        }
        
        // Ejes
        for (int i = 0; i < chartHeight; i++) {
            chart[i][0] = '│';
        }
        for (int j = 0; j < chartWidth; j++) {
            chart[chartHeight-1][j] = '─';
        }
        chart[chartHeight-1][0] = '└';
        
        // Puntos del frente de Pareto
        for (IntegerSolution sol : solutions) {
            int x = (int) ((sol.objectives()[0] - minO1) / (maxO1 - minO1 + 0.0001) * (chartWidth - 2));
            int y = (int) ((sol.objectives()[1] - minO2) / (maxO2 - minO2 + 0.0001) * (chartHeight - 2));
            
            // Invertir Y para que valores mayores estén arriba
            y = chartHeight - 2 - y;
            
            if (x >= 0 && x < chartWidth && y >= 0 && y < chartHeight) {
                chart[y][x+1] = '●'; // +1 para evitar el eje Y
            }
        }
        
        // Imprimir gráfico
        System.out.println();
        for (int i = 0; i < chartHeight; i++) {
            System.out.print("   ");
            for (int j = 0; j < chartWidth; j++) {
                System.out.print(chart[i][j]);
            }
            
            // Etiquetas del eje Y (solo en algunos puntos)
            if (i == 0) {
                System.out.print(" " + String.format("%.1f", maxO2));
            } else if (i == chartHeight/2) {
                System.out.print(" " + String.format("%.1f", (maxO2 + minO2)/2));
            } else if (i == chartHeight-1) {
                System.out.print(" " + String.format("%.1f", minO2));
            }
            System.out.println();
        }
        
        // Etiqueta del eje X
        System.out.print("   └");
        for (int j = 1; j < chartWidth; j++) {
            System.out.print("─");
        }
        System.out.println("→ O2");
        
        System.out.print("    ");
        System.out.print(String.format("%.1f", minO1));
        for (int j = 0; j < chartWidth - 10; j++) System.out.print(" ");
        System.out.println(String.format("%.1f", maxO1));
        
        System.out.println("\nLeyenda:");
        System.out.println("   = Solución no dominada");
        System.out.println("   O1 = Continuidad institucional (menor es mejor)");
        System.out.println("   O2 = Continuidad por categorías (menor es mejor)");
    }
}