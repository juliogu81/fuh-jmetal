package org.fuh.runner;

import org.fuh.io.ExcelLoader;
import org.fuh.model.CategoryBlock; // <--- Importante
import org.fuh.model.CourtConfig;
import org.fuh.model.InstitutionPriority;
import org.fuh.model.MatchInfo;
import org.fuh.model.Slot;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DataLoadingTest {

    // Asegúrate de que esta ruta apunte a tu archivo real
    private static final String EXCEL_FILE_PATH = "/Users/juliogu/Documentos/git/ae-fixture/data/tests/input_001.xlsx"; 

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   INICIANDO PRUEBA DE CARGA DE DATOS");
        System.out.println("==========================================\n");

        try {
            // 1. Instanciar Loader y Cargar
            ExcelLoader loader = new ExcelLoader();
            System.out.println("Leyendo archivo: " + EXCEL_FILE_PATH + " ...");
            
            // Si te sale el aviso rojo de Log4j aquí, IGNÓRALO, es normal en pruebas simples.
            ExcelLoader.DataResult data = loader.loadFromExcel(EXCEL_FILE_PATH);

            System.out.println("--> Carga finalizada sin errores críticos.\n");

            // -------------------------------------------------
            // 2. VERIFICACIÓN DE CANCHAS
            // -------------------------------------------------
            printHeader("2. CONFIGURACIÓN DE CANCHAS");
            Map<String, CourtConfig> courts = data.courtConfigs;            
            if (courts.isEmpty()) {
                System.err.println("¡ALERTA! No se cargaron canchas.");
            } else {
            	for (CourtConfig c : courts.values()) {
            	    // Usamos %s para el ID
            	    System.out.printf("- Cancha %s: %02d:00 a %02d:00%n", c.getId(), c.getStartHour(), c.getEndHour());
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
                    System.out.printf("- %s -> Min %.0f%% en Cancha %d%n",
                            p.getInstitution(), (p.getMinPercentage() * 100), p.getTargetCourtId());
                }
            }

            // -------------------------------------------------
            // 4. VERIFICACIÓN DE PARTIDOS Y SLOTS
            // -------------------------------------------------
            printHeader("4. PARTIDOS Y ESPACIO DE BÚSQUEDA");
            List<MatchInfo> matches = data.matchInfos;
            List<List<Slot>> allSlots = data.validSlots;

            System.out.println("Total Partidos: " + matches.size());
            int matchesWithNoSlots = 0;

            // Mostramos solo los primeros 5 y los últimos 5 para no saturar la consola
            // O si hay errores, los mostramos todos.
            for (int i = 0; i < matches.size(); i++) {
                MatchInfo info = matches.get(i);
                List<Slot> options = allSlots.get(i);

                if (options.isEmpty()) {
                    System.err.printf("[ERROR] Partido %s (%s vs %s) NO TIENE CANCHAS VÁLIDAS%n", 
                        info.getId(), info.getHomeInstitution(), info.getAwayInstitution());
                    matchesWithNoSlots++;
                } else {
                    // Solo imprimimos detalle de algunos para verificar visualmente
                    if (i < 3) { 
                        System.out.printf("Partido %s (%s): %d opciones (Ej: Cancha %d @ %d:00)%n",
                            info.getId(), info.getCategory(), options.size(), 
                            options.get(0).getCourtId(), options.get(0).getTimeSlotId());
                    }
                }
            }
            
            // -------------------------------------------------
            // 5. VERIFICACIÓN DE BLOQUES DE CATEGORÍAS (NUEVO)
            // -------------------------------------------------
            printHeader("5. BLOQUES DE CATEGORÍAS (Objetivo 2)");
            List<CategoryBlock> blocks = data.categoryBlocks;
            
            if (blocks.isEmpty()) {
                System.out.println("(No se encontraron bloques de categorías en el Excel)");
            } else {
                for (CategoryBlock block : blocks) {
                    // El toString() de CategoryBlock ya lo imprime bonito
                    System.out.println("- " + block.toString());
                }
            }

            // -------------------------------------------------
            // RESUMEN
            // -------------------------------------------------
            System.out.println("\n==========================================");
            if (matchesWithNoSlots > 0) {
                System.err.println("FALLO: Hay " + matchesWithNoSlots + " partidos imposibles de asignar.");
            } else {
                System.out.println("ÉXITO: Estructuras listas. Puedes correr el Algoritmo Genético.");
            }
            System.out.println("==========================================");

        } catch (IOException e) {
            System.err.println("ERROR CRÍTICO LEYENDO ARCHIVO: " + e.getMessage());
        }
    }

    private static void printHeader(String title) {
        System.out.println("\n------------------------------------------");
        System.out.println(" " + title);
        System.out.println("------------------------------------------");
    }
}