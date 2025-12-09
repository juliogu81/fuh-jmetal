package org.fuh.runner;

import org.fuh.io.ExcelLoader;
import org.fuh.model.CategoryBlock;
import org.fuh.model.CourtConfig;
import org.fuh.model.InstitutionPriority;
import org.fuh.model.MatchInfo;
import org.fuh.model.Slot;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DataLoadingTest {

    // Asegúrate de que esta ruta apunte a tu archivo real
    private static final String EXCEL_FILE_PATH = "input_v5.xlsx"; 

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   INICIANDO PRUEBA DE CARGA DE DATOS");
        System.out.println("==========================================\n");

        try {
            // 1. Instanciar Loader y Cargar
            ExcelLoader loader = new ExcelLoader();
            System.out.println("Leyendo archivo: " + EXCEL_FILE_PATH + " ...");
            
            ExcelLoader.DataResult data = loader.loadFromExcel(EXCEL_FILE_PATH);

            System.out.println("--> Carga finalizada sin errores críticos.\n");
            
            // =================================================
            // PREPARACIÓN DE LA LISTA MAESTRA (NORMALIZADA)
            // =================================================
            // Normalizar la lista maestra de instituciones (cargada por ExcelLoader.java)
            Set<String> masterInstitutionsClean = data.allInstitutions.stream()
                .map(DataLoadingTest::cleanName)
                .collect(Collectors.toSet());
            
            // -------------------------------------------------
            // 1.5 IMPRESIÓN DE DEPURACIÓN DE INSTITUCIONES
            // -------------------------------------------------
            printHeader("1.5 LISTA MAESTRA DE INSTITUCIONES VÁLIDAS");
            if (masterInstitutionsClean.isEmpty()) {
                System.err.println("¡ALERTA! El conjunto maestro de instituciones está vacío.");
            } else {
                System.out.println(masterInstitutionsClean); // Imprime los nombres normalizados
                System.out.printf("Total de Instituciones Válidas (Normalizadas): %d%n", masterInstitutionsClean.size());
            }


            // -------------------------------------------------
            // 2. VERIFICACIÓN DE CANCHAS
            // -------------------------------------------------
            printHeader("2. CONFIGURACIÓN DE CANCHAS");
            Map<String, CourtConfig> courts = data.courtConfigs;            
            if (courts.isEmpty()) {
                System.err.println("¡ALERTA! No se cargaron canchas.");
            } else {
            	for (CourtConfig c : courts.values()) {
            	    System.out.printf("- Cancha %s: %02d:00 a %02d:00 (Max Cont: %d)%n", c.getId(), c.getStartHour(), c.getEndHour(), c.getMaxContinuousHours());
            	}
            }

            
            printHeader("2.5 EXCLUSIVIDADES DE CANCHA");
            Map<String, String> exclusivity = data.exclusivityMap;

            if (exclusivity.isEmpty()) {
                System.out.println("(No se encontraron reglas de exclusividad)");
            } else {
                for (Map.Entry<String, String> entry : exclusivity.entrySet()) {
                    String cancha = entry.getKey();
                    String dueno = entry.getValue();
                    System.out.printf("- Cancha %s es exclusiva de: %s%n", cancha, dueno);
                }
            }
            // -------------------------------------------------
            // 3. VERIFICACIÓN DE PRIORIDADES
            // -------------------------------------------------
            printHeader("3. REGLAS DE PRIORIDAD (CUOTAS)");
            List<InstitutionPriority> priorities = data.priorities;
            
            if (priorities.isEmpty()) {
                System.out.println("(No se encontraron reglas de prioridad)");
            } else {
                for (InstitutionPriority p : priorities) {
                	System.out.printf("- %s -> Min %.0f%% en Cancha %s%n",
                	        p.getInstitution(), (p.getMinPercentage() * 100), p.getTargetCourtId());
                }
            }

            // -------------------------------------------------
            // 4. VERIFICACIÓN DE PARTIDOS Y SLOTS
            // -------------------------------------------------
            printHeader("4. LISTADO COMPLETO DE PARTIDOS");
            List<MatchInfo> matches = data.matchInfos;
            List<List<Slot>> allSlots = data.validSlots;

            System.out.println("Total Partidos Cargados: " + matches.size());
            int matchesWithNoSlots = 0;

            for (int i = 0; i < matches.size(); i++) {
                MatchInfo info = matches.get(i);
                List<Slot> options = allSlots.get(i);

                if (options.isEmpty()) {
                    System.err.printf("[ERROR] Partido %s (%s vs %s) - Cat: %s -> NO TIENE CANCHAS VÁLIDAS%n", 
                        info.getId(), info.getHomeInstitution(), info.getAwayInstitution(), info.getCategory());
                    matchesWithNoSlots++;
                } else {
                    System.out.printf("P%s: %s vs %s (%s) -> %d opciones (Ej: %s @ %d:00)%n",
                        info.getId(), 
                        info.getHomeInstitution(), 
                        info.getAwayInstitution(), 
                        info.getCategory(),
                        options.size(), 
                        options.get(0).getCourtId(), 
                        options.get(0).getTimeSlotId());
                }
            }
            
            // -------------------------------------------------
            // 5. VERIFICACIÓN DE BLOQUES DE CATEGORÍAS
            // -------------------------------------------------
            printHeader("5. BLOQUES DE CATEGORÍAS (Objetivo 2)");
            List<CategoryBlock> blocks = data.categoryBlocks;
            
            if (blocks.isEmpty()) {
                System.out.println("(No se encontraron bloques de categorías en el Excel)");
            } else {
                for (CategoryBlock block : blocks) {
                    System.out.println("- " + block.toString());
                }
            }
            
            // -------------------------------------------------
            // 6. VERIFICACIÓN DE INTEGRIDAD INSTITUCIONAL
            // -------------------------------------------------
            int institutionErrors = 0;
            printHeader("6. VERIFICACIÓN DE INTEGRIDAD INSTITUCIONAL");
            
            for (MatchInfo info : matches) {
                String home = info.getHomeInstitution();
                String away = info.getAwayInstitution();
                
                // Normalizar nombres para comparación
                String homeClean = cleanName(home); 
                String awayClean = cleanName(away); 
                
                // Chequeo de Institución Local
                if (!masterInstitutionsClean.contains(homeClean)) {
                    System.err.printf("[ERROR] Partido %s: Institución Local '%s' (Normalizado: %s) no encontrada en la lista maestra.%n", 
                        info.getId(), home, homeClean);
                    institutionErrors++;
                }
                
                // Chequeo de Institución Visitante
                if (!masterInstitutionsClean.contains(awayClean)) {
                    System.err.printf("[ERROR] Partido %s: Institución Visitante '%s' (Normalizado: %s) no encontrada en la lista maestra.%n", 
                        info.getId(), away, awayClean);
                    institutionErrors++;
                }
            }
            
            if (institutionErrors == 0) {
                System.out.println("✅ Éxito: Todas las instituciones de los partidos coinciden con la lista maestra.");
            } else {
                System.err.printf("❌ FALLO: Se encontraron %d errores de instituciones no coincidentes.%n", institutionErrors);
            }


            // -------------------------------------------------
            // RESUMEN FINAL
            // -------------------------------------------------
            System.out.println("\n==========================================");
            if (matchesWithNoSlots > 0 || institutionErrors > 0) {
                System.err.println("FALLO GENERAL: Hay problemas de asignación o integridad de datos.");
            } else {
                System.out.println("ÉXITO: Estructuras listas. Puedes correr el Algoritmo Genético.");
            }
            System.out.println("==========================================");

        } catch (IOException e) {
            System.err.println("ERROR CRÍTICO LEYENDO ARCHIVO: " + e.getMessage());
        }
    }
    
    /**
     * Helper de normalización: convierte a mayúsculas y elimina espacios.
     * Esta función debe usarse tanto para limpiar el maestro como los datos de los partidos.
     */
    private static String cleanName(String name) {
        if (name == null || name.isEmpty()) return "";
        // Quitar espacios extra y convertir a mayúsculas
        return name.toUpperCase().trim(); 
    }

    private static void printHeader(String title) {
        System.out.println("\n------------------------------------------");
        System.out.println(" " + title);
        System.out.println("------------------------------------------");
    }
}