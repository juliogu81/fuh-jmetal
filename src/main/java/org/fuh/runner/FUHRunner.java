package org.fuh.runner;

import org.fuh.model.*;
import org.fuh.io.ExcelLoader;
import org.fuh.problem.FUHSchedulingProblem;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.operator.crossover.impl.IntegerSBXCrossover;
import org.uma.jmetal.operator.mutation.impl.IntegerPolynomialMutation;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class FUHRunner {

    public static void main(String[] args) {
        // Configuración
        int populationSize = 100;
        double crossoverProb = 0.9;
        double mutationProb = 0.01;
        int maxEvaluations = 5000;
        
        try {
            // OPCIÓN A: Cargar datos desde Excel
            ExcelLoader.DataResult data = loadDataFromExcel("input_v5_2xlsx.xlsx");
            
            // OPCIÓN B: Usar datos dummy (para prueba rápida)
            // ExcelLoader.DataResult data = createDummyData();
            
            if (data.matchInfos.isEmpty()) {
                System.err.println("Error: No se cargaron datos.");
                return;
            }
            
            // 1. Cargar datos
            List<List<Slot>> slotsData = data.validSlots;
            List<MatchInfo> infoData = data.matchInfos;
            Map<String, CourtConfig> courtConfigs = data.courtConfigs;
            List<InstitutionPriority> priorities = data.priorities;
            List<CategoryBlock> categoryBlocks = data.categoryBlocks;
            
            System.out.println("📊 Datos cargados:");
            System.out.println("   • Partidos: " + data.matchInfos.size());
            System.out.println("   • Canchas: " + data.courtConfigs.size());
            
            // 2. Definir el Problema
            FUHSchedulingProblem problem = new FUHSchedulingProblem(
                slotsData, infoData, courtConfigs, priorities, categoryBlocks
            );
            
            // 3. Definir Operadores
            var crossover = new IntegerSBXCrossover(crossoverProb, 20.0);
            var mutation = new IntegerPolynomialMutation(mutationProb, 20.0);
            
            // 4. Construir el Algoritmo
            Algorithm<List<IntegerSolution>> algorithm = 
                new NSGAIIBuilder<>(problem, crossover, mutation, populationSize)
                    .setMaxEvaluations(maxEvaluations)
                    .build();
            
            // 5. Mostrar encabezado
            System.out.println("\n╔══════════════════════════════════════════════╗");
            System.out.println("║     NSGA-II - FUH Scheduling                ║");
            System.out.println("╠══════════════════════════════════════════════╣");
            System.out.println("║ Partidos: " + data.matchInfos.size() + "                                ║");
            System.out.println("║ Población: " + populationSize + "                                ║");
            System.out.println("║ Evaluaciones: " + maxEvaluations + "                            ║");
            System.out.println("╚══════════════════════════════════════════════╝\n");
            
            // 6. Ejecutar
            System.out.println("▶ Ejecutando algoritmo...");
            long start = System.currentTimeMillis();
            algorithm.run();
            long end = System.currentTimeMillis();
            
            // 7. Obtener Resultado
            List<IntegerSolution> result = algorithm.result(); 
            result.sort(Comparator.comparingDouble(s -> s.objectives()[0]));
            
            // 8. Mostrar resultados
            displayResults(result, start, end);
            
            // 9. Guardar resultados y fixture
            if (!result.isEmpty()) {
                IntegerSolution mejorSolucion = result.get(0);
                
                // Guardar resultados numéricos
                saveResultsToFiles(result, "fuh_results");
                
                // Guardar FIXTURE COMPLETO en CSV
                saveFixtureToCSV(problem, mejorSolucion, data, "fuh_fixture");
                
                // Mostrar análisis del fixture
                analyzeAndDisplayFixture(problem, mejorSolucion, data);
            }
            
            // 10. Mostrar frente de Pareto si hay suficientes soluciones
            displayParetoConsole(result);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // =========================================================
    // MÉTODO PARA GUARDAR FIXTURE EN CSV (¡NUEVO Y MEJORADO!)
    // =========================================================
    
    private static void saveFixtureToCSV(FUHSchedulingProblem problem, 
                                         IntegerSolution solution, 
                                         ExcelLoader.DataResult data,
                                         String baseName) throws Exception {
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = baseName + "_" + timestamp + ".csv";
        
        FileWriter writer = new FileWriter(fileName);
        
        // Encabezado detallado
        writer.write("FIXTURE - Federación Uruguaya de Handball\n");
        writer.write("Generado: " + new Date() + "\n");
        writer.write("Partidos: " + data.matchInfos.size() + ", Canchas: " + data.courtConfigs.size() + "\n");
        writer.write("\n");
        
        // Encabezado de columnas
        writer.write("ID Partido,Institución Local,Institución Visitante,Categoría,");
        writer.write("Cancha Asignada,Hora Inicio,Día,Superposiciones,Continuidad Inst,Continuidad Cat\n");
        
        // Decodificar la solución
        Slot[] assignments = decodeSolution(solution, data.validSlots);
        
        // Calcular métricas para cada partido
        Map<String, Integer> ocupacionPorSlot = new HashMap<>();
        Map<String, List<Integer>> tiemposPorInstCancha = new HashMap<>();
        Map<String, List<Integer>> tiemposPorCatCancha = new HashMap<>();
        
        // Primera pasada: recolectar datos
        for (int i = 0; i < assignments.length; i++) {
            Slot slot = assignments[i];
            MatchInfo info = data.matchInfos.get(i);
            
            String slotKey = slot.getCourtId() + "-" + slot.getTimeSlotId();
            ocupacionPorSlot.put(slotKey, ocupacionPorSlot.getOrDefault(slotKey, 0) + 1);
            
            // Para continuidad institucional
            String keyInstLocal = info.getHomeInstitution() + "-" + slot.getCourtId();
            String keyInstVisitante = info.getAwayInstitution() + "-" + slot.getCourtId();
            
            tiemposPorInstCancha.putIfAbsent(keyInstLocal, new ArrayList<>());
            tiemposPorInstCancha.putIfAbsent(keyInstVisitante, new ArrayList<>());
            tiemposPorInstCancha.get(keyInstLocal).add(slot.getTimeSlotId());
            tiemposPorInstCancha.get(keyInstVisitante).add(slot.getTimeSlotId());
            
            // Para continuidad por categoría
            String keyCat = info.getCategory() + "-" + slot.getCourtId();
            tiemposPorCatCancha.putIfAbsent(keyCat, new ArrayList<>());
            tiemposPorCatCancha.get(keyCat).add(slot.getTimeSlotId());
        }
        
        // Segunda pasada: escribir datos con métricas
        for (int i = 0; i < assignments.length; i++) {
            Slot slot = assignments[i];
            MatchInfo info = data.matchInfos.get(i);
            
            String slotKey = slot.getCourtId() + "-" + slot.getTimeSlotId();
            int superposiciones = ocupacionPorSlot.get(slotKey) - 1;
            
            // Calcular continuidad institucional para este partido
            double contInst = calcularContinuidadParaPartido(
                info.getHomeInstitution(), slot.getCourtId(), slot.getTimeSlotId(), tiemposPorInstCancha
            ) + calcularContinuidadParaPartido(
                info.getAwayInstitution(), slot.getCourtId(), slot.getTimeSlotId(), tiemposPorInstCancha
            );
            
            // Calcular continuidad por categoría para este partido
            double contCat = calcularContinuidadParaPartido(
                info.getCategory(), slot.getCourtId(), slot.getTimeSlotId(), tiemposPorCatCancha
            );
            
            // Escribir línea en CSV
            writer.write(String.format("%s,%s,%s,%s,%s,%d:00,%s,%d,%.2f,%.2f\n",
                info.getId(),
                info.getHomeInstitution(),
                info.getAwayInstitution(),
                info.getCategory(),
                slot.getCourtId(),
                slot.getTimeSlotId(),
                obtenerDia(slot.getTimeSlotId()),
                superposiciones,
                contInst,
                contCat
            ));
        }
        
        writer.close();
        System.out.println("\n💾 FIXTURE guardado en: " + fileName);
        System.out.println("   • Abre este archivo en Excel para revisar las asignaciones");
    }
    
    // =========================================================
    // MÉTODO PARA ANALIZAR Y MOSTRAR FIXTURE EN CONSOLA
    // =========================================================
    
    private static void analyzeAndDisplayFixture(FUHSchedulingProblem problem,
                                                 IntegerSolution solution,
                                                 ExcelLoader.DataResult data) throws Exception {
        
        System.out.println("\n" + "═".repeat(60));
        System.out.println("📋 ANÁLISIS DEL FIXTURE GENERADO");
        System.out.println("═".repeat(60));
        
        Slot[] assignments = decodeSolution(solution, data.validSlots);
        
        // 1. Distribución por cancha
        System.out.println("\n🏟️  DISTRIBUCIÓN POR CANCHA:");
        Map<String, Integer> partidosPorCancha = new HashMap<>();
        for (Slot slot : assignments) {
            String cancha = slot.getCourtId();
            partidosPorCancha.put(cancha, partidosPorCancha.getOrDefault(cancha, 0) + 1);
        }
        
        for (Map.Entry<String, Integer> entry : partidosPorCancha.entrySet()) {
            System.out.printf("   • Cancha %s: %d partidos\n", entry.getKey(), entry.getValue());
        }
        
        // 2. Distribución horaria
        System.out.println("\n🕐 DISTRIBUCIÓN HORARIA:");
        Map<Integer, Integer> partidosPorHora = new HashMap<>();
        for (Slot slot : assignments) {
            int hora = slot.getTimeSlotId();
            partidosPorHora.put(hora, partidosPorHora.getOrDefault(hora, 0) + 1);
        }
        
        List<Integer> horas = new ArrayList<>(partidosPorHora.keySet());
        Collections.sort(horas);
        for (int hora : horas) {
            System.out.printf("   • %d:00 - %d:00: %d partidos\n", 
                hora, hora+1, partidosPorHora.get(hora));
        }
        
        // 3. Verificar superposiciones
        System.out.println("\n⚠️  VERIFICACIÓN DE SUPERPOSICIONES:");
        Map<String, List<Integer>> ocupacion = new HashMap<>();
        int superposiciones = 0;
        
        for (int i = 0; i < assignments.length; i++) {
            Slot slot = assignments[i];
            String clave = slot.getCourtId() + "-" + slot.getTimeSlotId();
            
            if (!ocupacion.containsKey(clave)) {
                ocupacion.put(clave, new ArrayList<>());
            }
            ocupacion.get(clave).add(i);
        }
        
        for (Map.Entry<String, List<Integer>> entry : ocupacion.entrySet()) {
            if (entry.getValue().size() > 1) {
                superposiciones += entry.getValue().size() - 1;
                System.out.printf("   • %s: %d partidos superpuestos (IDs: %s)\n",
                    entry.getKey(), entry.getValue().size(), entry.getValue());
            }
        }
        
        if (superposiciones == 0) {
            System.out.println("   ✅ No hay superposiciones");
        } else {
            System.out.printf("   ❌ Total superposiciones: %d\n", superposiciones);
        }
        
        // 4. Mostrar algunos partidos de ejemplo
        System.out.println("\n📝 PARTIDOS DE EJEMPLO:");
        System.out.println("ID | Local vs Visitante | Categoría | Cancha | Hora");
        System.out.println("---|--------------------|-----------|--------|-----");
        
        for (int i = 0; i < Math.min(10, assignments.length); i++) {
            Slot slot = assignments[i];
            MatchInfo info = data.matchInfos.get(i);
            
            System.out.printf("%-3s| %-18s | %-9s | %-6s | %d:00\n",
                info.getId(),
                info.getHomeInstitution() + " vs " + info.getAwayInstitution(),
                info.getCategory(),
                slot.getCourtId(),
                slot.getTimeSlotId()
            );
        }
        
        if (assignments.length > 10) {
            System.out.println("... y " + (assignments.length - 10) + " partidos más");
        }
        
        // 5. Resumen de objetivos
        System.out.println("\n🎯 RESUMEN DE OBJETIVOS:");
        System.out.printf("   • O1 (Continuidad Institucional): %.2f\n", solution.objectives()[0]);
        System.out.printf("   • O2 (Continuidad por Categoría): %.2f\n", solution.objectives()[1]);
        System.out.printf("   • Restricción (Penalización): %.2f\n", solution.constraints()[0]);
        
        if (solution.objectives()[0] == 0 && solution.objectives()[1] == 0) {
            System.out.println("\n✨ ¡POSIBLE SOLUCIÓN PERFECTA!");
            System.out.println("   Ambos objetivos en 0 podrían indicar:");
            System.out.println("   1. No hay conflictos de continuidad en los datos");
            System.out.println("   2. La solución es óptima para estos objetivos");
            System.out.println("   Revisa el archivo CSV para validar manualmente.");
        }
    }
    
    // =========================================================
    // MÉTODOS AUXILIARES
    // =========================================================
    
    private static Slot[] decodeSolution(IntegerSolution solution, List<List<Slot>> validSlots) {
        Slot[] assignments = new Slot[solution.variables().size()];
        for (int i = 0; i < solution.variables().size(); i++) {
            int slotIndex = solution.variables().get(i);
            assignments[i] = validSlots.get(i).get(slotIndex);
        }
        return assignments;
    }
    
    private static double calcularContinuidadParaPartido(String clave, String cancha, 
                                                         int tiempo, Map<String, List<Integer>> tiemposMap) {
        String key = clave + "-" + cancha;
        if (!tiemposMap.containsKey(key)) return 0.0;
        
        List<Integer> tiempos = new ArrayList<>(tiemposMap.get(key));
        tiempos.remove((Integer) tiempo); // Remover el tiempo actual
        if (tiempos.isEmpty()) return 0.0;
        
        // Encontrar el tiempo más cercano
        int minDiferencia = Integer.MAX_VALUE;
        for (int t : tiempos) {
            int diff = Math.abs(t - tiempo);
            if (diff < minDiferencia) minDiferencia = diff;
        }
        
        return minDiferencia > 1 ? minDiferencia - 1 : 0.0;
    }
    
    private static String obtenerDia(int hora) {
        // Simulación: asumimos que las horas 0-23 son el mismo día
        // En una implementación real, necesitarías saber el día
        return "Sábado"; // Placeholder
    }
    
    private static ExcelLoader.DataResult loadDataFromExcel(String filePath) throws Exception {
        ExcelLoader loader = new ExcelLoader();
        return loader.loadFromExcel(filePath);
    }
    
    // --- Crear datos dummy si no hay Excel ---
    private static ExcelLoader.DataResult createDummyData() {
        ExcelLoader.DataResult data = new ExcelLoader.DataResult();
        
        // 1. Configuración de canchas
        data.courtConfigs.put("1", new CourtConfig("1", 8, 18, 4)); // Cancha 1: 8-18h, max 4h continuas
        data.courtConfigs.put("2", new CourtConfig("2", 9, 17, 3)); // Cancha 2: 9-17h, max 3h continuas
        data.courtConfigs.put("3", new CourtConfig("3", 10, 20, 5)); // Cancha 3: 10-20h, max 5h continuas
        
        // 2. Prioridades institucionales
        data.priorities.add(new InstitutionPriority("Club A", "1", 0.3)); // Club A: 30% en cancha 1
        data.priorities.add(new InstitutionPriority("Club B", "2", 0.2)); // Club B: 20% en cancha 2
        
        // 3. Bloques de categorías
        data.categoryBlocks.add(new CategoryBlock("Juvenil", Arrays.asList("Juveniles", "Formativas")));
        data.categoryBlocks.add(new CategoryBlock("Adulto", Arrays.asList("Mayores", "Veteranos")));
        
        // 4. Partidos y slots válidos (20 partidos)
        String[] instituciones = {"Club A", "Club B", "Club C", "Club D", "Club E"};
        String[] categorias = {"Juveniles", "Formativas", "Mayores"};
        
        for (int i = 0; i < 20; i++) {
            // Info del partido
            String home = instituciones[i % instituciones.length];
            String away = instituciones[(i + 1) % instituciones.length];
            String category = categorias[i % categorias.length];
            data.matchInfos.add(new MatchInfo("P" + i, home, away, category));
            
            // Slots válidos para este partido
            List<Slot> slots = new ArrayList<>();
            
            // Para cada cancha configurada
            for (CourtConfig court : data.courtConfigs.values()) {
                // Generar slots de 1 hora cada uno
                for (int h = court.getStartHour(); h < court.getEndHour(); h++) {
                    slots.add(new Slot(court.getId(), h));
                }
            }
            data.validSlots.add(slots);
        }
        
        return new ExcelLoader.DataResult();
    }
    
    // --- Ejecutar con datos dummy como respaldo ---
    private static void runWithDummyData() {
        try {
            ExcelLoader.DataResult data = createDummyData();
            
            FUHSchedulingProblem problem = new FUHSchedulingProblem(
                data.validSlots, 
                data.matchInfos,
                data.courtConfigs,
                data.priorities,
                data.categoryBlocks
            );
            
            var crossover = new IntegerSBXCrossover(0.9, 20.0);
            var mutation = new IntegerPolynomialMutation(0.01, 20.0);
            
            Algorithm<List<IntegerSolution>> algorithm = 
                new NSGAIIBuilder<>(problem, crossover, mutation, 50) // Población más pequeña
                    .setMaxEvaluations(1000) // Menos evaluaciones para prueba rápida
                    .build();
            
            System.out.println("\n▶ Ejecutando con datos de prueba...");
            algorithm.run();
            List<IntegerSolution> result = algorithm.result();
            
            // Mostrar resultados básicos
            if (!result.isEmpty()) {
                System.out.println("\n✅ Prueba exitosa!");
                System.out.println("Soluciones encontradas: " + result.size());
                
                IntegerSolution mejor = result.get(0);
                System.out.println("Mejor solución:");
                System.out.println("  • O1 (Inst.): " + String.format("%.2f", mejor.objectives()[0]));
                System.out.println("  • O2 (Cat.): " + String.format("%.2f", mejor.objectives()[1]));
                System.out.println("  • Restricción: " + String.format("%.2f", mejor.constraints()[0]));
            }
            
        } catch (Exception e) {
            System.err.println("Error incluso con datos dummy: " + e.getMessage());
        }
    }
    
    // --- Mostrar resultados en consola ---
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
            
            System.out.println("\n📈 ESTADÍSTICAS:");
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
                System.out.println("\n🏆 MEJORES SOLUCIONES:");
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
            
            System.out.println("\n💾 Archivos guardados:");
            System.out.println("   • " + baseName + ".csv (datos completos)");
            System.out.println("   • " + baseName + "_summary.txt (resumen)");
            
        } catch (Exception e) {
            System.err.println("⚠️  Error al guardar archivos: " + e.getMessage());
        }
    }
    
    // --- Visualización simple del frente de Pareto en consola ---
    private static void displayParetoConsole(List<IntegerSolution> solutions) {
        if (solutions.size() < 2) {
            System.out.println("\n⚠️  No hay suficientes soluciones para mostrar frente de Pareto");
            return;
        }
        
        System.out.println("\n📊 FRENTE DE PARETO (visualización simple):");
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
        
        System.out.println("\n📝 Leyenda:");
        System.out.println("   ● = Solución no dominada");
        System.out.println("   O1 = Continuidad institucional (menor es mejor)");
        System.out.println("   O2 = Continuidad por categorías (menor es mejor)");
    }
}