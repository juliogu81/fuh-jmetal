package org.fuh.io;

import org.apache.poi.ss.usermodel.*;
import org.fuh.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ExcelLoader {

    public static class DataResult {
        public List<MatchInfo> matchInfos = new ArrayList<>();
        public List<List<Slot>> validSlots = new ArrayList<>();
        public Map<String, CourtConfig> courtConfigs = new HashMap<>(); // ID es String
        public List<InstitutionPriority> priorities = new ArrayList<>();
        public List<CategoryBlock> categoryBlocks = new ArrayList<>();
        public Map<String, String> exclusivityMap = new HashMap<>();
        public Set<String> allInstitutions = new HashSet<>(); // Conjunto maestro de instituciones
    }

    private static class CourtRowData {
        private final int start;
        private final int end;
        private final int maxHours;

        public CourtRowData(int start, int end, int maxHours) {
            this.start = start;
            this.end = end;
            this.maxHours = maxHours;
        }

        public int getStart() { return start; }
        public int getEnd() { return end; }
        public int getMaxHours() { return maxHours; }
    }

    public DataResult loadFromExcel(String filePath) throws IOException {
        DataResult result = new DataResult();

        // Aseguramos leer el archivo
        FileInputStream file = new FileInputStream(new File(filePath));
        Workbook workbook = WorkbookFactory.create(file);

        // =========================================================
        // FASE 1: CARGAR CONFIGURACIONES
        // =========================================================

        // --- A. Disponibilidad de Canchas ---
        // Columnas según tus imagenes: 
        // A(0): Cancha | B(1): Dia (IGNORAR) | C(2): Inicio | D(3): Fin | E(4): Max
        Sheet sheetCourts = workbook.getSheet("canchas-disponibilidad");
        if (sheetCourts != null) {
            // Mapa temporal para agrupar por cancha
            Map<String, List<CourtRowData>> courtDataMap = new HashMap<>();
            
            for (Row row : sheetCourts) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                String id = getStringValue(row, 0); // Col 0: Nombre Cancha

                int start = getHourFromCell(row, 1); // Col 2: Inicio (Lee formato hh:mm)
                int end = getHourFromCell(row, 2);   // Col 3: Fin (Lee formato hh:mm)
                int maxHours = (int) getNumericValue(row, 3); // Col 4: Horas Max

                if (!id.isEmpty() && end > start) {
                    courtDataMap.putIfAbsent(id, new ArrayList<>());
                    courtDataMap.get(id).add(new CourtRowData(start, end, maxHours));
                }
            }

            // Procesar cada cancha: tomar el mínimo inicio, el máximo fin, y el mínimo de horas máximas
            for (Map.Entry<String, List<CourtRowData>> entry : courtDataMap.entrySet()) {
                String id = entry.getKey();
                List<CourtRowData> dataList = entry.getValue();
                
                int minStart = dataList.stream().mapToInt(CourtRowData::getStart).min().getAsInt();
                int maxEnd = dataList.stream().mapToInt(CourtRowData::getEnd).max().getAsInt();
                
                // Si hay horas máximas especificadas, tomar el mínimo (el límite más restrictivo)
                // Si no hay, usar un valor por defecto (por ejemplo, 24)
                int maxHours = dataList.stream()
                        .mapToInt(CourtRowData::getMaxHours)
                        .filter(h -> h > 0) // Ignorar los que son 0 o negativos
                        .min()
                        .orElse(24); // Valor por defecto

                result.courtConfigs.put(id, new CourtConfig(id, minStart, maxEnd, maxHours));
            }
        }

        // --- B. Exclusividades ---
        // Hoja: "exclusividad" (Col: cancha, instituto)
        Map<String, String> exclusivityMap = result.exclusivityMap;
        Sheet sheetExcl = workbook.getSheet("exclusividad");
        if (sheetExcl != null) {
            for (Row row : sheetExcl) {
                if (row.getRowNum() == 0) continue;
                
                String courtId = getStringValue(row, 0); 
                String inst = getStringValue(row, 1);    
                
                if (!courtId.isEmpty() && !inst.isEmpty()) {
                    exclusivityMap.put(courtId, inst);
                }
            }
        }

        // --- C. Prioridades ---
        // Hoja: "instituciones-prioridad" (Col: institucion, cancha, prioridad)
        Sheet sheetPrio = workbook.getSheet("instituciones-prioridad");
        if (sheetPrio != null) {
            for (Row row : sheetPrio) {
                if (row.getRowNum() == 0) continue;

                String inst = getStringValue(row, 0);
                String courtId = getStringValue(row, 1); 
                double priority = getNumericValue(row, 2);

                // --- CORRECCIÓN AUTOMÁTICA DE PORCENTAJE ---
                // Si el usuario puso "90" (entero), lo convertimos a "0.9" (decimal)
                if (priority > 1.0) {
                    priority = priority / 100.0;
                }
                // -------------------------------------------

                if (!inst.isEmpty()) {
                    result.priorities.add(new InstitutionPriority(inst, courtId, priority));
                }
            }
        }

        // --- D. Bloques de Categorías ---
        // Hoja: "bloques de categorias" (Col A: Nombre, Col B..Z: Categorías)
        Sheet sheetBlocks = workbook.getSheet("bloques de categorias");
        if (sheetBlocks != null) {
            for (Row row : sheetBlocks) {
                if (row.getRowNum() == 0) continue;

                String blockName = getStringValue(row, 0);
                if (blockName.isEmpty()) continue;

                List<String> catsInBlock = new ArrayList<>();
                // Leemos columnas hasta encontrar vacío (Columna B es índice 1)
                for (int c = 1; c < 20; c++) {
                    String cat = getStringValue(row, c);
                    if (cat.isEmpty()) break; 
                    catsInBlock.add(cat);
                }

                if (!catsInBlock.isEmpty()) {
                    result.categoryBlocks.add(new CategoryBlock(blockName, catsInBlock));
                }
            }
        }
        
        
        Sheet sheetInst = workbook.getSheet("instituciones"); 
        if (sheetInst != null) {
            for (Row row : sheetInst) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera
                
                String instName = getStringValue(row, 0); // Asumiendo que la columna 0 contiene el nombre
                
                if (!instName.isEmpty()) {
                    // Normalizar a mayúsculas y quitar espacios para evitar errores de integridad
                    result.allInstitutions.add(instName.toUpperCase().trim()); 
                }
            }
        } else {
            System.err.println("ADVERTENCIA: No se encontró la hoja 'instituciones'. La lista maestra estará incompleta.");
        }
        // =========================================================
        // FASE 2: CARGAR PARTIDOS Y GENERAR SLOTS
        // =========================================================

        Sheet sheetMatches = workbook.getSheet("partidos");
        int matchCounter = 0;

        if (sheetMatches != null) {
            for (Row row : sheetMatches) {
                if (row.getRowNum() == 0) continue;

                String inst1 = getStringValue(row, 0);
                String inst2 = getStringValue(row, 1);
                String category = getStringValue(row, 2);

                if (inst1.isEmpty() && inst2.isEmpty()) continue;

                String matchId = "P" + matchCounter++;
                MatchInfo info = new MatchInfo(matchId, inst1, inst2, category);
                result.matchInfos.add(info);

                // --- Generar Slots Válidos ---
                List<Slot> slotsForThisMatch = new ArrayList<>();

                for (CourtConfig court : result.courtConfigs.values()) {
                    
                    // 1. Filtro Exclusividad
                    if (exclusivityMap.containsKey(court.getId())) {
                        String owner = exclusivityMap.get(court.getId());
                        // Si NINGUNO de los dos equipos es el dueño, saltamos
                        if (!inst1.equalsIgnoreCase(owner) && !inst2.equalsIgnoreCase(owner)) {
                            continue; 
                        }
                    }

                    // 2. Generar Horarios
                    for (int h = court.getStartHour(); h < court.getEndHour(); h++) {
                        slotsForThisMatch.add(new Slot(court.getId(), h));
                    }
                }

                if (slotsForThisMatch.isEmpty()) {
                    System.err.println("ADVERTENCIA: Partido " + matchId + " (" + inst1 + " vs " + inst2 + ") no tiene canchas válidas.");
                }

                result.validSlots.add(slotsForThisMatch);
            }
        }

        workbook.close();
        file.close();
        return result;
    }

    // =========================================================
    // MÉTODOS AUXILIARES INTELIGENTES (Para leer hh:mm)
    // =========================================================

    /**
     * Este método es la CLAVE. Detecta si Excel guardó la hora como fecha (0.375)
     * o como número entero (9).
     */
    private int getHourFromCell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0;

        try {
            // Si es numérico (el caso de tu imagen hh:mm)
            if (cell.getCellType() == CellType.NUMERIC) {
                
                // Opción A: Apache POI detecta que es formato fecha
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().getHour();
                } 
                
                // Opción B: Es un decimal pequeño (ej: 0.375) pero POI no detectó el formato fecha
                double val = cell.getNumericCellValue();
                if (val < 1.0 && val > 0.0) {
                    // Convertimos la fracción de día a horas (0.5 * 24 = 12 horas)
                    return (int) Math.round(val * 24); 
                }
                
                // Opción C: Es un número entero normal (ej: 14)
                return (int) val;
            } 
            // Si es texto (alguien escribió "14:00" como texto)
            else if (cell.getCellType() == CellType.STRING) {
                String text = cell.getStringCellValue().trim();
                if (text.contains(":")) {
                    return Integer.parseInt(text.split(":")[0]);
                }
                return Integer.parseInt(text);
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    private String getStringValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
            if (cell.getCellType() == CellType.NUMERIC) {
                double val = cell.getNumericCellValue();
                if (val == (long) val) return String.format("%d", (long) val);
                return String.valueOf(val);
            }
        } catch (Exception e) {}
        return "";
    }

    private double getNumericValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
            if (cell.getCellType() == CellType.STRING) return Double.parseDouble(cell.getStringCellValue());
        } catch (Exception e) {}
        return 0;
    }
}