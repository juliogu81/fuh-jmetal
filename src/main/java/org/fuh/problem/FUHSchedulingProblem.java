package org.fuh.problem;

import org.fuh.model.*;
import org.uma.jmetal.problem.integerproblem.impl.AbstractIntegerProblem;
import org.uma.jmetal.solution.integersolution.IntegerSolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class FUHSchedulingProblem extends AbstractIntegerProblem {

    private final List<List<Slot>> validSlotsPerMatch;
    private final List<MatchInfo> matchInfos;
    private final Map<String, CourtConfig> courtConfigs;
    private final List<InstitutionPriority> priorities;
    private final List<CategoryBlock> categoryBlocks;
    private final int numberOfMatches;

    private IntegerSolution seedSolution = null; // Variable para la semilla

    public FUHSchedulingProblem(
            List<List<Slot>> validSlotsPerMatch, 
            List<MatchInfo> matchInfos,
            Map<String, CourtConfig> courtConfigs,
            List<InstitutionPriority> priorities,
            List<CategoryBlock> categoryBlocks) {

        this.validSlotsPerMatch = validSlotsPerMatch;
        this.matchInfos = matchInfos;
        this.courtConfigs = courtConfigs;
        this.priorities = priorities;
        this.categoryBlocks = categoryBlocks;
        this.numberOfMatches = validSlotsPerMatch.size();

        this.numberOfObjectives(2);
        this.numberOfConstraints(1 + courtConfigs.size() + priorities.size());

        List<Integer> lowerLimit = new ArrayList<>();
        List<Integer> upperLimit = new ArrayList<>();

        for (List<Slot> slots : validSlotsPerMatch) {
            lowerLimit.add(0);
            upperLimit.add(slots.size() - 1);
        }
        this.variableBounds(lowerLimit, upperLimit);
    }
    
    public void setSeedSolution(IntegerSolution seed) {
        this.seedSolution = seed;
    }

    // 🔥 Método crucial: Inicialización
    @Override
    public IntegerSolution createSolution() {
        // 1. Si hay semilla, la usamos UNA SOLA VEZ (para el primer individuo)
        if (this.seedSolution != null) {
            IntegerSolution copy = (IntegerSolution) this.seedSolution.copy();
            this.seedSolution = null; 
            return copy;
        }

        // 2. LÓGICA ALEATORIA INTELIGENTE (Smart Random) para el resto de la población
        IntegerSolution solution = super.createSolution();
        Set<String> occupied = new HashSet<>();
        List<Integer> matchOrder = new ArrayList<>();
        for(int i=0; i<numberOfMatches; i++) matchOrder.add(i);
        Collections.shuffle(matchOrder);

        for (int matchIndex : matchOrder) {
            List<Slot> options = validSlotsPerMatch.get(matchIndex);
            int selectedSlotIndex = -1;
            
            List<Integer> optionIndices = new ArrayList<>();
            for(int k=0; k<options.size(); k++) optionIndices.add(k);
            Collections.shuffle(optionIndices);
            
            for (int optIdx : optionIndices) {
                Slot candidate = options.get(optIdx);
                String key = candidate.getCourtId() + "_" + candidate.getTimeSlotId();
                if (!occupied.contains(key)) {
                    selectedSlotIndex = optIdx;
                    occupied.add(key);
                    break;
                }
            }
            if (selectedSlotIndex == -1) selectedSlotIndex = optionIndices.get(0);
            solution.variables().set(matchIndex, selectedSlotIndex);
        }
        return solution;
    }
    
    @Override
    public IntegerSolution evaluate(IntegerSolution solution) {
        Slot[] assignments = decode(solution);
        int constraintIndex = 0;

        // --- RESTRICCIONES DURAS ---
        double overlaps = countOverlaps(assignments);
        solution.constraints()[constraintIndex++] = (overlaps == 0) ? 0.0 : -overlaps; // Superposición

        for (CourtConfig court : courtConfigs.values()) {
            double violation = checkMaxContinuousHours(assignments, court);
            solution.constraints()[constraintIndex++] = (violation == 0) ? 0.0 : -violation;
        }

        for (InstitutionPriority rule : priorities) {
            double violation = checkPriorityQuota(assignments, rule);
            solution.constraints()[constraintIndex++] = (violation == 0) ? 0.0 : -violation;
        }

        // --- OBJETIVOS (Blandas) ---
        solution.objectives()[0] = calculateInstitutionalContinuity(assignments);
        solution.objectives()[1] = calculateCategoryContinuity(assignments);
        
        return solution;
    }

    private Slot[] decode(IntegerSolution solution) {
        Slot[] assignments = new Slot[numberOfMatches];
        for (int i = 0; i < numberOfMatches; i++) {
            int slotIndex = solution.variables().get(i);
            assignments[i] = validSlotsPerMatch.get(i).get(slotIndex);
        }
        return assignments;
    }

    private double countOverlaps(Slot[] assignments) {
        int overlaps = 0;
        for (int i = 0; i < numberOfMatches; i++) {
            for (int j = i + 1; j < numberOfMatches; j++) {
                if (assignments[i].equals(assignments[j])) overlaps++;
            }
        }
        return overlaps;
    }
    
    private double calculateInstitutionalContinuity(Slot[] assignments) {
        double totalPenalty = 0.0;
        for (int i = 0; i < numberOfMatches; i++) {
            MatchInfo infoA = matchInfos.get(i);
            Slot slotA = assignments[i];
            for (int j = i + 1; j < numberOfMatches; j++) {
                MatchInfo infoB = matchInfos.get(j);
                Slot slotB = assignments[j];
                if (infoA.sharesInstitutionWith(infoB) && slotA.getCourtId().equals(slotB.getCourtId())) {
                    int diff = Math.abs(slotA.getTimeSlotId() - slotB.getTimeSlotId());
                    if (diff > 1) totalPenalty += (diff - 1); 
                }
            }
        }
        return totalPenalty;
    }
    
    private double calculateCategoryContinuity(Slot[] assignments) {
        double totalPenalty = 0.0;
        for (int i = 0; i < numberOfMatches; i++) {
            String catA = matchInfos.get(i).getCategory();
            Slot slotA = assignments[i];
            for (int j = i + 1; j < numberOfMatches; j++) {
                String catB = matchInfos.get(j).getCategory();
                Slot slotB = assignments[j];
                if (slotA.getCourtId().equals(slotB.getCourtId())) {
                    if (isSameBlock(catA, catB)) {
                        int diff = Math.abs(slotA.getTimeSlotId() - slotB.getTimeSlotId());
                        if (diff > 1) totalPenalty += (diff - 1);
                    }
                }
            }
        }
        return totalPenalty;
    }
    
    private boolean isSameBlock(String cat1, String cat2) {
        if (cat1.equals(cat2)) return true;
        for (CategoryBlock block : categoryBlocks) {
            if (block.match(cat1, cat2)) return true; 
        }
        return false;
    }

    private double checkMaxContinuousHours(Slot[] assignments, CourtConfig court) {
        List<Integer> times = new ArrayList<>();
        for (Slot s : assignments) {
            if (s.getCourtId().equals(court.getId())) times.add(s.getTimeSlotId());
        }
        if (times.size() <= 1) return 0.0; 
        Collections.sort(times);
        int currentStreak = 1;
        int maxStreakFound = 1;
        for (int i = 1; i < times.size(); i++) {
            if (times.get(i) == times.get(i - 1) + 1) currentStreak++;
            else currentStreak = 1;
            maxStreakFound = Math.max(maxStreakFound, currentStreak);
        }
        if (maxStreakFound > court.getMaxContinuousHours()) return (maxStreakFound - court.getMaxContinuousHours()); 
        return 0.0;
    }

    private double checkPriorityQuota(Slot[] assignments, InstitutionPriority rule) {
        int totalMatchesOfInst = 0;
        int matchesOnTargetCourt = 0;
        for (int i = 0; i < numberOfMatches; i++) {
            MatchInfo info = matchInfos.get(i);
            if (info.getHomeInstitution().equals(rule.getInstitution()) || info.getAwayInstitution().equals(rule.getInstitution())) {
                totalMatchesOfInst++;
                if (assignments[i].getCourtId().equals(rule.getTargetCourtId())) matchesOnTargetCourt++;
            }
        }
        if (totalMatchesOfInst == 0) return 0.0; 
        double actualPct = (double) matchesOnTargetCourt / totalMatchesOfInst;
        if (actualPct < rule.getMinPercentage()) return (rule.getMinPercentage() - actualPct) * 100; 
        return 0.0;
    }
}