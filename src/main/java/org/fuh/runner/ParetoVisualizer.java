package org.fuh.runner;

import org.uma.jmetal.solution.integersolution.IntegerSolution;
import java.util.List;

public class ParetoVisualizer {
    
    public static void displayParetoFront(List<IntegerSolution> solutions) {
        if (solutions.isEmpty()) {
            System.out.println("No hay soluciones para visualizar.");
            return;
        }
        
        System.out.println("\n" + "★".repeat(60));
        System.out.println("VISUALIZACIÓN DEL FRENTE DE PARETO");
        System.out.println("★".repeat(60));
        
        // Crear una matriz simple para visualización
        int width = 50;
        int height = 20;
        char[][] grid = new char[height][width];
        
        // Inicializar con espacios
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                grid[i][j] = ' ';
            }
        }
        
        // Encontrar mínimos y máximos para escalar
        double minO1 = solutions.stream().mapToDouble(s -> s.objectives()[0]).min().orElse(0);
        double maxO1 = solutions.stream().mapToDouble(s -> s.objectives()[0]).max().orElse(1);
        double minO2 = solutions.stream().mapToDouble(s -> s.objectives()[1]).min().orElse(0);
        double maxO2 = solutions.stream().mapToDouble(s -> s.objectives()[1]).max().orElse(1);
        
        // Colocar puntos en la grilla
        for (IntegerSolution sol : solutions) {
            int x = (int) ((sol.objectives()[0] - minO1) / (maxO1 - minO1) * (width - 1));
            int y = (int) ((sol.objectives()[1] - minO2) / (maxO2 - minO2) * (height - 1));
            
            // Invertir Y porque la consola empieza desde arriba
            y = height - 1 - y;
            
            if (x >= 0 && x < width && y >= 0 && y < height) {
                grid[y][x] = '●';
            }
        }
        
        // Dibujar ejes
        for (int i = 0; i < height; i++) {
            grid[i][0] = '│';
        }
        for (int j = 0; j < width; j++) {
            grid[height-1][j] = '─';
        }
        grid[height-1][0] = '└';
        
        // Imprimir la grilla
        for (int i = 0; i < height; i++) {
            System.out.print("  ");
            for (int j = 0; j < width; j++) {
                System.out.print(grid[i][j]);
            }
            System.out.println();
        }
        
        // Leyenda
        System.out.println("\nLeyenda:");
        System.out.println("  ● Cada punto representa una solución no dominada");
        System.out.println("  │ Eje Y: Objetivo 2 (O2) - Continuidad por categorías");
        System.out.println("  ─ Eje X: Objetivo 1 (O1) - Continuidad institucional");
        System.out.println("  Valores: O1 ∈ [" + String.format("%.2f", minO1) + ", " + 
                         String.format("%.2f", maxO1) + "], O2 ∈ [" + 
                         String.format("%.2f", minO2) + ", " + 
                         String.format("%.2f", maxO2) + "]");
    }
}