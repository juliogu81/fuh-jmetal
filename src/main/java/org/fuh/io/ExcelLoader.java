package org.fuh.io;

import org.apache.poi.ss.usermodel.*;
import org.fuh.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ExcelLoader {

    /**
     * Clase contenedora para devolver todos los datos cargados de una sola vez.
     */
    public static class DataResult {
        public List<MatchInfo> matchInfos = new ArrayList<>();
        public List<List<Slot>> validSlots = new ArrayList<>();
        public Map<Integer, CourtConfig> courtConfigs = new HashMap<>();
        public List<InstitutionPriority> priorities = new ArrayList<>();
        public List<CategoryBlock> categoryBlocks = new ArrayList<>(); // Nuevo: Bloques de categorías
    }

    /**
     * Carga el archivo Excel y procesa todas las hojas.
     */
    public DataResult loadFromExcel(String filePath) throws IOException {
        DataResult result = new DataResult();

        FileInputStream file = new FileInputStream(new File(filePath));
        // WorkbookFactory detecta automáticamente si es .xls o .xlsx
        Workbook workbook = WorkbookFactory.create(file);

        // =========================================================
        // FASE 1: CARGAR CONFIGURACIONES (Reglas del Juego)
        // =========================================================

        // --- A. Cargar Disponibilidad de Canchas ---
        // Hoja: "canchas-disponibilidad" (Col: id, inicio, fin, max_horas)
        Sheet sheetCourts = workbook.getSheet("canchas-disponibilidad");
        if (sheetCourts != null) {
            for (Row row : sheetCourts) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                int id = (int) getNumericValue(row, 0);
                int start = (int) getNumericValue(row, 1);
                int end = (int) getNumericValue(row, 2);
                int maxHours = (int) getNumericValue(row, 3);

                if (id != 0) { // Evitar filas vacías
                    result.courtConfigs.put(id, new CourtConfig(id, start, end, maxHours));
                }
            }
        }

        // --- B. Cargar Exclusividades ---
        // Hoja: "exclusividad" (Col: cancha, instituto)
        Map<Integer, String> exclusivityMap = new HashMap<>();
        Sheet sheetExcl = workbook.getSheet("exclusividad");
        if (sheetExcl != null) {
            for (Row row : sheetExcl) {
                if (row.getRowNum() == 0) continue;
                
                int courtId = (int) getNumericValue(row, 0);
                String inst = getStringValue(row, 1);
                
                if (courtId != 0 && !inst.isEmpty()) {
                    exclusivityMap.put(courtId, inst);
                }
            }
        }

        // --- C. Cargar Prioridades Institucionales ---
        // Hoja: "instituciones-prioridad" (Col: institucion, cancha, prioridad)
        Sheet sheetPrio = workbook.getSheet("instituciones-prioridad");
        if (sheetPrio != null) {
            for (Row row : sheetPrio) {
                if (row.getRowNum() == 0) continue;

                String inst = getStringValue(row, 0);
                int courtId = (int) getNumericValue(row, 1);
                double priority = getNumericValue(row, 2); // Ej: 0.5

                if (!inst.isEmpty()) {
                    result.priorities.add(new InstitutionPriority(inst, courtId, priority));
                }
            }
        }

        // --- D. Cargar Bloques de Categorías (NUEVO) ---
        // Hoja: "bloques de categorias" (Col A: Nombre, Col B..Z: Categorías)
        Sheet sheetBlocks = workbook.getSheet("bloques de categorias");
        if (sheetBlocks != null) {
            for (Row row : sheetBlocks) {
                if (row.getRowNum() == 0) continue;

                String blockName = getStringValue(row, 0);
                if (blockName.isEmpty()) continue;

                List<String> catsInBlock = new ArrayList<>();
                // Leemos columnas dinámicamente hasta encontrar una vacía (Límite seguro 20)
                for (int c = 1; c < 20; c++) {
                    String cat = getStringValue(row, c);
                    if (cat.isEmpty()) break; // Fin de la fila
                    catsInBlock.add(cat);
                }

                if (!catsInBlock.isEmpty()) {
                    result.categoryBlocks.add(new CategoryBlock(blockName, catsInBlock));
                }
            }
        }

        // =========================================================
        // FASE 2: CARGAR PARTIDOS Y GENERAR SLOTS (Espacio de Búsqueda)
        // =========================================================

        // Hoja: "partidos" (Col: instituto1, instituto2, categoria)
        Sheet sheetMatches = workbook.getSheet("partidos");
        int matchCounter = 0;

        if (sheetMatches != null) {
            for (Row row : sheetMatches) {
                if (row.getRowNum() == 0) continue;

                String inst1 = getStringValue(row, 0);
                String inst2 = getStringValue(row, 1);
                String category = getStringValue(row, 2);

                // Si la fila está vacía, saltamos
                if (inst1.isEmpty() && inst2.isEmpty()) continue;

                String matchId = "P" + matchCounter++;
                MatchInfo info = new MatchInfo(matchId, inst1, inst2, category);
                result.matchInfos.add(info);

                // --- CALCULAR SLOTS VÁLIDOS ---
                // Aquí cruzamos la información del partido con la config de canchas
                List<Slot> slotsForThisMatch = new ArrayList<>();

                for (CourtConfig court : result.courtConfigs.values()) {
                    
                    // 1. Filtro de Exclusividad
                    // Si la cancha está en la lista de exclusivas, verificamos el dueño
                    if (exclusivityMap.containsKey(court.getId())) {
                        String owner = exclusivityMap.get(court.getId());
                        // Si NINGUNO de los dos equipos es el dueño, no pueden jugar ahí
                        if (!owner.equals(inst1) && !owner.equals(inst2)) {
                            continue; 
                        }
                    }

                    // 2. Filtro de Categoría (Opcional, si tuvieras mapa de Cat->Cancha)
                    // Por ahora asumimos que si pasa la exclusividad, la cancha es válida.

                    // 3. Generar Horarios
                    for (int h = court.getStartHour(); h < court.getEndHour(); h++) {
                        slotsForThisMatch.add(new Slot(court.getId(), h));
                    }
                }

                if (slotsForThisMatch.isEmpty()) {
                    System.err.println("ADVERTENCIA: El partido " + matchId + " (" + inst1 + " vs " + inst2 + ") no tiene canchas disponibles.");
                }

                result.validSlots.add(slotsForThisMatch);
            }
        }

        workbook.close();
        file.close();
        return result;
    }

    // =========================================================
    // MÉTODOS AUXILIARES ROBUSTOS (Corrigen el error de lectura)
    // =========================================================

    private String getStringValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    // Si es numérico (ej: 1.0), lo convertimos a String "1"
                    double val = cell.getNumericCellValue();
                    if (val == (long) val) {
                        return String.format("%d", (long) val);
                    }
                    return String.valueOf(val);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // Intentar evaluar la fórmula
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception e) {
                        return String.valueOf(cell.getNumericCellValue());
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private double getNumericValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return cell.getNumericCellValue();
                case STRING:
                    // Si es texto, intentamos parsear "10" a 10.0
                    String text = cell.getStringCellValue().trim();
                    if (text.isEmpty()) return 0;
                    return Double.parseDouble(text);
                case FORMULA:
                    return cell.getNumericCellValue();
                default:
                    return 0;
            }
        } catch (NumberFormatException e) {
            // No era un número, devolvemos 0
            return 0;
        }
    }
}