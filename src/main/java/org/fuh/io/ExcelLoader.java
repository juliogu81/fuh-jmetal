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
        // Mapa cambiado a <String, CourtConfig>
        public Map<String, CourtConfig> courtConfigs = new HashMap<>();
        public List<InstitutionPriority> priorities = new ArrayList<>();
        public List<CategoryBlock> categoryBlocks = new ArrayList<>();
    }

    public DataResult loadFromExcel(String filePath) throws IOException {
        DataResult result = new DataResult();

        FileInputStream file = new FileInputStream(new File(filePath));
        Workbook workbook = WorkbookFactory.create(file);

        // --- FASE 1: CARGAR CONFIGURACIONES ---

        // A. Disponibilidad de Canchas
        Sheet sheetCourts = workbook.getSheet("canchas-disponibilidad");
        if (sheetCourts != null) {
            for (Row row : sheetCourts) {
                if (row.getRowNum() == 0) continue;

                // CAMBIO: Leemos ID como String
                String id = getStringValue(row, 0); 
                int start = (int) getNumericValue(row, 1);
                int end = (int) getNumericValue(row, 2);
                int maxHours = (int) getNumericValue(row, 3);

                if (!id.isEmpty()) {
                    result.courtConfigs.put(id, new CourtConfig(id, start, end, maxHours));
                }
            }
        }

        // B. Exclusividades (Mapa String -> String)
        Map<String, String> exclusivityMap = new HashMap<>();
        Sheet sheetExcl = workbook.getSheet("exclusividad");
        if (sheetExcl != null) {
            for (Row row : sheetExcl) {
                if (row.getRowNum() == 0) continue;
                
                String courtId = getStringValue(row, 0); // Cancha (Nombre)
                String inst = getStringValue(row, 1);    // Instituto Dueño
                
                if (!courtId.isEmpty() && !inst.isEmpty()) {
                    exclusivityMap.put(courtId, inst);
                }
            }
        }

        // C. Prioridades
        Sheet sheetPrio = workbook.getSheet("instituciones-prioridad");
        if (sheetPrio != null) {
            for (Row row : sheetPrio) {
                if (row.getRowNum() == 0) continue;

                String inst = getStringValue(row, 0);
                String courtId = getStringValue(row, 1); // CAMBIO: Leemos nombre
                double priority = getNumericValue(row, 2);

                if (!inst.isEmpty()) {
                    result.priorities.add(new InstitutionPriority(inst, courtId, priority));
                }
            }
        }

        // D. Bloques (Sin cambios, ya funcionaba bien)
        Sheet sheetBlocks = workbook.getSheet("bloques de categorias");
        if (sheetBlocks != null) {
            for (Row row : sheetBlocks) {
                if (row.getRowNum() == 0) continue;
                String blockName = getStringValue(row, 0);
                if (blockName.isEmpty()) continue;
                List<String> catsInBlock = new ArrayList<>();
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

        // --- FASE 2: GENERAR SLOTS ---

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

                List<Slot> slotsForThisMatch = new ArrayList<>();

                // Recorremos las canchas cargadas (que ahora son String)
                for (CourtConfig court : result.courtConfigs.values()) {
                    
                    // --- LÓGICA DE EXCLUSIVIDAD CORREGIDA ---
                    // "Si dice cancha sporthalle instituto colegio aleman... 
                    // siempre tiene que haber un participante que lo sea"
                    
                    if (exclusivityMap.containsKey(court.getId())) {
                        String owner = exclusivityMap.get(court.getId());
                        
                        boolean inst1IsOwner = inst1.equalsIgnoreCase(owner);
                        boolean inst2IsOwner = inst2.equalsIgnoreCase(owner);
                        
                        // Si NINGUNO es el dueño, NO pueden jugar aquí.
                        if (!inst1IsOwner && !inst2IsOwner) {
                            continue; // Saltar cancha
                        }
                    }

                    // Generar Horarios
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

    // --- MÉTODOS AUXILIARES (Los robustos que te pasé antes) ---
    private String getStringValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double val = cell.getNumericCellValue();
                if (val == (long) val) return String.format("%d", (long) val);
                return String.valueOf(val);
            }
            return cell.getStringCellValue().trim();
        } catch (Exception e) { return ""; }
    }

    private double getNumericValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0;
        try {
            if (cell.getCellType() == CellType.STRING) {
                String text = cell.getStringCellValue().trim();
                if (text.isEmpty()) return 0;
                return Double.parseDouble(text);
            }
            return cell.getNumericCellValue();
        } catch (Exception e) { return 0; }
    }
}