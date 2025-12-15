package org.fuh.io;

import org.fuh.model.MatchInfo;
import org.fuh.model.Slot;
import org.fuh.problem.FUHSchedulingProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class FixtureSeeder {

    private final static ExcelLoader loader = new ExcelLoader(); 

    private static class FixtureRowData {
        String cancha;
        int horaInicio;
        public FixtureRowData(String cancha, int hora) {
            this.cancha = cancha;
            this.horaInicio = hora;
        }
    }

    public static IntegerSolution createSolutionFromExcel(
            String excelPath, 
            FUHSchedulingProblem problem, 
            List<MatchInfo> matchInfos, 
            List<List<Slot>> validSlotsPerMatch) {

        System.out.println("\n🌱 Iniciando carga de semilla desde Excel: " + excelPath);

        // 1. CARGAR DATOS Y OBTENER CONTEO
        Map<String, FixtureRowData> seedMap = loadExcelToMap(excelPath);
        
        if (seedMap.isEmpty()) return null;
        
        // 🔥 Imprimimos el conteo de la semilla (Respuesta a tu pregunta)
        System.out.println("   • Total de partidas leídas de la Semilla: " + seedMap.size());


        IntegerSolution solution = problem.createSolution(); 
        
        int matchesMapped = 0;
        int matchesNotFound = 0;
        int slotsInvalid = 0; // Contador para los 6 fallos

        // Lista para imprimir los slots inválidos
        List<String> invalidSlotsReport = new ArrayList<>();
        
        // 2. ITERAR SOBRE LAS VARIABLES DEL PROBLEMA (matchInfos)
        for (int i = 0; i < matchInfos.size(); i++) {
            MatchInfo info = matchInfos.get(i);
            String key = generateUniqueKey(info.getCategory(), info.getHomeInstitution(), info.getAwayInstitution());
            
            if (seedMap.containsKey(key)) {
                FixtureRowData target = seedMap.get(key);
                
                // 🔥 PUNTO DE FALLO: Buscamos el índice del Slot
                int slotIndex = findSlotIndex(target.cancha, target.horaInicio, validSlotsPerMatch.get(i));
                
                if (slotIndex != -1) {
                    solution.variables().set(i, slotIndex);
                    matchesMapped++;
                } else {
                    // ¡ENCONTRAMOS UNO DE LOS 6 FALLOS!
                    slotsInvalid++;
                    invalidSlotsReport.add(
                        "Partido: " + info.getHomeInstitution() + " vs " + info.getAwayInstitution() + 
                        " @ " + target.cancha + " " + target.horaInicio + ":00" +
                        " (Opción no permitida por tus reglas de Canchas/Exclusividad)"
                    );
                }
            } else {
                matchesNotFound++;
                // ... (El debug de keys no encontradas se puede omitir, ya que sabemos que es 0)
            }
        }
        
        // --- REPORTE FINAL ---
        System.out.println("✅ Semilla Procesada:");
        System.out.println("   • Mapeados correctamente: " + matchesMapped);
        System.out.println("   • No encontrados en Seed: " + matchesNotFound);
        System.out.println("   • Slots inválidos/dif.:   " + slotsInvalid); // ¡Esto debe ser 6!
        
        if (slotsInvalid > 0) {
            System.out.println("\n--- 🕵️‍♂️ DIAGNÓSTICO DE SLOTS INVÁLIDOS ---");
            System.out.println("Los siguientes " + slotsInvalid + " partidos están asignados en la Semilla a un Slot que NO es válido según 'canchas-disponibilidad' o 'exclusividad':");
            for(String report : invalidSlotsReport) {
                System.out.println("   ❌ INVÁLIDO: " + report);
            }
            System.out.println("----------------------------------------------");
        }

        if (matchesMapped == 0) return null; 
        return solution;
    }

    // --- LECTURA DIRECTA DE EXCEL (.xlsx) ---

    private static Map<String, FixtureRowData> loadExcelToMap(String filePath) {
        Map<String, FixtureRowData> map = new HashMap<>();
        try (FileInputStream file = new FileInputStream(new File(filePath));
             Workbook workbook = WorkbookFactory.create(file)) {

            Sheet sheet = workbook.getSheetAt(0); 
            
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 

                String cancha = loader.getStringValue(row, 0); 
                String horaStr = loader.getStringValue(row, 1);
                String cat = loader.getStringValue(row, 2);
                String eq1 = loader.getStringValue(row, 3);
                String eq2 = loader.getStringValue(row, 4);

                if (cancha.isEmpty() || eq1.isEmpty() || horaStr.isEmpty()) continue;

                int hora = parseStartHour(horaStr);
                String key = generateUniqueKey(cat, eq1, eq2);
                
                map.put(key, new FixtureRowData(cancha, hora));
            }

        } catch (Exception e) {
            System.err.println("ERROR FATAL en loadExcelToMap: " + e.getMessage());
        }
        return map;
    }

    // --- UTILS Y NORMALIZACIÓN ---
    
    private static int parseStartHour(String timeRange) {
        try {
            String start = timeRange.split("-")[0].trim(); 
            String hourOnly = start.split(":")[0].trim();  
            return Integer.parseInt(hourOnly);
        } catch (Exception e) {
            return -1;
        }
    }
    
    private static String generateUniqueKey(String cat, String eq1, String eq2) {
        String c = normalizeName(cat);
        String e1 = normalizeName(eq1);
        String e2 = normalizeName(eq2);
        
        if (e1.compareTo(e2) < 0) return c + "|" + e1 + "|" + e2;
        else return c + "|" + e2 + "|" + e1;
    }
    
    private static String normalizeName(String name) {
        if (name == null) return "";
        return name.trim().toUpperCase()
                   .replaceAll("[\\s\\.\\,\\-]", ""); 
    }

    private static int findSlotIndex(String targetCancha, int targetHora, List<Slot> validOptions) {
        for (int i = 0; i < validOptions.size(); i++) {
            Slot s = validOptions.get(i);
            if (normalizeName(s.getCourtId()).equalsIgnoreCase(normalizeName(targetCancha)) && 
                s.getTimeSlotId() == targetHora) {
                return i;
            }
        }
        return -1;
    }
}