package boxboxbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class DriverFeatures {
    private static final int MONACO_TRACK_INDEX = FeatureSchema.trackIndex("Monaco");
    final String driverId;
    final int driverIndex;
    final int raceIdBucket;
    final int trackIndex;
    final int tempBucket;
    final int baseBucket;
    final double fixedPitPenalty;
    final int stopCount;
    final int startCompoundIndex;
    final int finalCompoundIndex;
    final int onlyStopPhase;
    final int finalStopPhase;
    final int lateSoftFinishPhase;
    final int twoStopFinalHardPhase;
    final int[] activeAgeFlatIndices;
    final short[] activeAgeCounts;
    final int[] activeLapFlatIndices;
    final short[] activeLapCounts;
    final int[] activePhaseFlatIndices;
    final short[] activePhaseCounts;
    final int[] activeAgeLapFlatIndices;
    final short[] activeAgeLapCounts;
    final int[] activeAgePhaseFlatIndices;
    final short[] activeAgePhaseCounts;
    final int[] activeTransitionFlatIndices;
    final short[] activeTransitionCounts;
    final int[] activeTransitionPhaseFlatIndices;
    final short[] activeTransitionPhaseCounts;

    DriverFeatures(
            String driverId,
            int driverIndex,
            int raceIdBucket,
            int trackIndex,
            int tempBucket,
            int baseBucket,
            double fixedPitPenalty,
            int stopCount,
            int startCompoundIndex,
            int finalCompoundIndex,
            int onlyStopPhase,
            int finalStopPhase,
            int lateSoftFinishPhase,
            int twoStopFinalHardPhase,
            int[] activeAgeFlatIndices,
            short[] activeAgeCounts,
            int[] activeLapFlatIndices,
            short[] activeLapCounts,
            int[] activePhaseFlatIndices,
            short[] activePhaseCounts,
            int[] activeAgeLapFlatIndices,
            short[] activeAgeLapCounts,
            int[] activeTransitionFlatIndices,
            short[] activeTransitionCounts,
            int[] activeAgePhaseFlatIndices,
            short[] activeAgePhaseCounts,
            int[] activeTransitionPhaseFlatIndices,
            short[] activeTransitionPhaseCounts) {
        this.driverId = driverId;
        this.driverIndex = driverIndex;
        this.raceIdBucket = raceIdBucket;
        this.trackIndex = trackIndex;
        this.tempBucket = tempBucket;
        this.baseBucket = baseBucket;
        this.fixedPitPenalty = fixedPitPenalty;
        this.stopCount = stopCount;
        this.startCompoundIndex = startCompoundIndex;
        this.finalCompoundIndex = finalCompoundIndex;
        this.onlyStopPhase = onlyStopPhase;
        this.finalStopPhase = finalStopPhase;
        this.lateSoftFinishPhase = lateSoftFinishPhase;
        this.twoStopFinalHardPhase = twoStopFinalHardPhase;
        this.activeAgeFlatIndices = activeAgeFlatIndices;
        this.activeAgeCounts = activeAgeCounts;
        this.activeLapFlatIndices = activeLapFlatIndices;
        this.activeLapCounts = activeLapCounts;
        this.activePhaseFlatIndices = activePhaseFlatIndices;
        this.activePhaseCounts = activePhaseCounts;
        this.activeAgeLapFlatIndices = activeAgeLapFlatIndices;
        this.activeAgeLapCounts = activeAgeLapCounts;
        this.activeTransitionFlatIndices = activeTransitionFlatIndices;
        this.activeTransitionCounts = activeTransitionCounts;
        this.activeAgePhaseFlatIndices = activeAgePhaseFlatIndices;
        this.activeAgePhaseCounts = activeAgePhaseCounts;
        this.activeTransitionPhaseFlatIndices = activeTransitionPhaseFlatIndices;
        this.activeTransitionPhaseCounts = activeTransitionPhaseCounts;
    }

    double score(Model model) {
        double score = fixedPitPenalty * model.pit_penalty_multiplier;
        for (int index = 0; index < activeAgeFlatIndices.length; index++) {
            int flatIndex = activeAgeFlatIndices[index];
            int compoundIndex = FeatureSchema.compoundFromAgeFlat(flatIndex);
            int age = FeatureSchema.ageFromFlat(flatIndex);
            double count = activeAgeCounts[index];

            score += count * model.global_age[compoundIndex][age];
            if (tempBucket >= 0) {
                score += count * model.temp_age[tempBucket][compoundIndex][age];
            }
            if (baseBucket >= 0) {
                score += count * model.base_age[baseBucket][compoundIndex][age];
            }
            if (trackIndex >= 0) {
                score += count * model.track_age[trackIndex][compoundIndex][age];
            }
        }
        for (int index = 0; index < activeLapFlatIndices.length; index++) {
            int flatIndex = activeLapFlatIndices[index];
            int compoundIndex = FeatureSchema.compoundFromLapFlat(flatIndex);
            int lap = FeatureSchema.lapFromFlat(flatIndex);
            double count = activeLapCounts[index];

            score += count * model.global_lap[compoundIndex][lap];
            if (tempBucket >= 0) {
                score += count * model.temp_lap[tempBucket][compoundIndex][lap];
            }
            if (baseBucket >= 0) {
                score += count * model.base_lap[baseBucket][compoundIndex][lap];
            }
            if (trackIndex >= 0) {
                score += count * model.track_lap[trackIndex][compoundIndex][lap];
            }
        }
        for (int index = 0; index < activePhaseFlatIndices.length; index++) {
            int flatIndex = activePhaseFlatIndices[index];
            int compoundIndex = FeatureSchema.compoundFromPhaseFlat(flatIndex);
            int phase = FeatureSchema.phaseFromFlat(flatIndex);
            double count = activePhaseCounts[index];
            score += count * model.global_phase[compoundIndex][phase];
            if (trackIndex >= 0) {
                score += count * model.track_phase[trackIndex][compoundIndex][phase];
            }
        }
        for (int index = 0; index < activeAgeLapFlatIndices.length; index++) {
            int flatIndex = activeAgeLapFlatIndices[index];
            int compoundIndex = FeatureSchema.compoundFromAgeLapFlat(flatIndex);
            int age = FeatureSchema.ageFromAgeLapFlat(flatIndex);
            int lap = FeatureSchema.lapFromAgeLapFlat(flatIndex);
            double count = activeAgeLapCounts[index];
            score += count * model.global_age_lap[compoundIndex][age][lap];
        }
        for (int index = 0; index < activeAgePhaseFlatIndices.length; index++) {
            int flatIndex = activeAgePhaseFlatIndices[index];
            int compoundIndex = FeatureSchema.compoundFromAgePhaseFlat(flatIndex);
            int age = FeatureSchema.ageFromAgePhaseFlat(flatIndex);
            int phase = FeatureSchema.phaseFromAgePhaseFlat(flatIndex);
            double count = activeAgePhaseCounts[index];
            score += count * model.global_age_phase[compoundIndex][age][phase];
        }
        for (int index = 0; index < activeTransitionFlatIndices.length; index++) {
            int flatIndex = activeTransitionFlatIndices[index];
            int stopSlot = FeatureSchema.stopSlotFromTransitionFlat(flatIndex);
            int fromCompound = FeatureSchema.fromCompoundFromTransitionFlat(flatIndex);
            int toCompound = FeatureSchema.toCompoundFromTransitionFlat(flatIndex);
            int lap = FeatureSchema.lapFromTransitionFlat(flatIndex);
            score += activeTransitionCounts[index] * model.transition_weight[stopSlot][fromCompound][toCompound][lap];
            if (trackIndex >= 0) {
                score += activeTransitionCounts[index]
                        * model.track_transition_lap[trackIndex][stopSlot][fromCompound][toCompound][lap];
            }
        }
        for (int index = 0; index < activeTransitionPhaseFlatIndices.length; index++) {
            int flatIndex = activeTransitionPhaseFlatIndices[index];
            int stopSlot = FeatureSchema.stopSlotFromTransitionPhaseFlat(flatIndex);
            int fromCompound = FeatureSchema.fromCompoundFromTransitionPhaseFlat(flatIndex);
            int toCompound = FeatureSchema.toCompoundFromTransitionPhaseFlat(flatIndex);
            int phase = FeatureSchema.phaseFromTransitionPhaseFlat(flatIndex);
            score += activeTransitionPhaseCounts[index]
                    * model.transition_phase_weight[stopSlot][fromCompound][toCompound][phase];
            if (trackIndex >= 0) {
                score += activeTransitionPhaseCounts[index]
                        * model.track_transition_phase[trackIndex][stopSlot][fromCompound][toCompound][phase];
            }
        }
        score += model.race_driver_bias[raceIdBucket][driverIndex];
        if (trackIndex == MONACO_TRACK_INDEX) {
            score += model.monaco_stop_count_weight[stopCount];
            score += fixedPitPenalty * model.monaco_pit_penalty_by_stop_count[stopCount];
            if (onlyStopPhase != 0) {
                score += model.monaco_only_stop_phase[onlyStopPhase];
                score += model.monaco_only_stop_phase_by_final_compound[finalCompoundIndex][onlyStopPhase];
                score += model.monaco_only_stop_start_final_phase[startCompoundIndex][finalCompoundIndex][onlyStopPhase];
            }
            score += model.monaco_final_compound[finalCompoundIndex];
            score += model.monaco_start_final_compound[startCompoundIndex][finalCompoundIndex];
            score += model.monaco_stop_count_by_final_compound[stopCount][finalCompoundIndex];
            score += model.monaco_driver_bias[driverIndex];
            score += model.monaco_driver_by_stop_count[driverIndex][stopCount];
            score += model.monaco_driver_by_final_compound[driverIndex][finalCompoundIndex];
            if (finalStopPhase != 0) {
                score += model.monaco_final_stop_phase[finalStopPhase];
                score += model.monaco_final_compound_by_final_stop_phase[finalCompoundIndex][finalStopPhase];
            }
            if (lateSoftFinishPhase != 0) {
                score += model.monaco_late_soft_finish_phase[lateSoftFinishPhase];
            }
            if (twoStopFinalHardPhase != 0) {
                score += model.monaco_two_stop_final_hard_phase[twoStopFinalHardPhase];
            }
        }
        return score;
    }

    int countFor(String compound, int age) {
        int flatIndex = FeatureSchema.ageFlatIndex(FeatureSchema.compoundIndex(compound), age);
        for (int index = 0; index < activeAgeFlatIndices.length; index++) {
            if (activeAgeFlatIndices[index] == flatIndex) {
                return activeAgeCounts[index];
            }
        }
        return 0;
    }

    int lapCountFor(String compound, int lap) {
        int flatIndex = FeatureSchema.lapFlatIndex(FeatureSchema.compoundIndex(compound), lap);
        for (int index = 0; index < activeLapFlatIndices.length; index++) {
            if (activeLapFlatIndices[index] == flatIndex) {
                return activeLapCounts[index];
            }
        }
        return 0;
    }

    int phaseCountFor(String compound, int phase) {
        int flatIndex = FeatureSchema.phaseFlatIndex(FeatureSchema.compoundIndex(compound), phase);
        for (int index = 0; index < activePhaseFlatIndices.length; index++) {
            if (activePhaseFlatIndices[index] == flatIndex) {
                return activePhaseCounts[index];
            }
        }
        return 0;
    }
}

final class PreparedRace {
    final String raceId;
    final DriverFeatures[] drivers;
    final DriverFeatures[] finishingOrder;
    final String[] expectedOrder;

    PreparedRace(String raceId, DriverFeatures[] drivers, DriverFeatures[] finishingOrder, String[] expectedOrder) {
        this.raceId = raceId;
        this.drivers = drivers;
        this.finishingOrder = finishingOrder;
        this.expectedOrder = expectedOrder;
    }
}

final class FeatureExtractor {
    private static final int SOFT_COMPOUND_INDEX = FeatureSchema.compoundIndex("SOFT");
    private static final int HARD_COMPOUND_INDEX = FeatureSchema.compoundIndex("HARD");
    private static final int LATE_SOFT_FINISH_MIN_PERCENT = 50;

    private FeatureExtractor() {
    }

    static PreparedRace prepareRace(RaceInput input, boolean requireFinishingOrder) {
        Objects.requireNonNull(input, "Race input is required");
        if (input.race_id == null || input.race_id.isBlank()) {
            throw new IllegalArgumentException("race_id is required");
        }
        if (input.race_config == null) {
            throw new IllegalArgumentException("race_config is required");
        }
        if (input.strategies == null || input.strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies are required");
        }

        RaceConfig config = input.race_config;
        int raceIdBucket = FeatureSchema.raceIdBucket(input.race_id);
        int trackIndex = FeatureSchema.trackIndex(config.track);
        if (config.total_laps < 1) {
            throw new IllegalArgumentException("total_laps must be positive: " + config.total_laps);
        }

        int tempBucket = FeatureSchema.tempBucket(config.track_temp);
        int baseBucket = FeatureSchema.baseBucket(config.base_lap_time);

        Map<String, DriverFeatures> byDriverId = new HashMap<>();
        for (Map.Entry<String, Strategy> entry : input.strategies.entrySet()) {
            Strategy strategy = Objects.requireNonNull(entry.getValue(), "Strategy cannot be null");
            DriverFeatures features = extractDriver(config, strategy, raceIdBucket, trackIndex, tempBucket, baseBucket);
            if (byDriverId.put(features.driverId, features) != null) {
                throw new IllegalArgumentException("Duplicate driver_id: " + features.driverId);
            }
        }

        List<String> sortedDriverIds = new ArrayList<>(byDriverId.keySet());
        Collections.sort(sortedDriverIds);
        DriverFeatures[] drivers = new DriverFeatures[sortedDriverIds.size()];
        for (int index = 0; index < sortedDriverIds.size(); index++) {
            drivers[index] = byDriverId.get(sortedDriverIds.get(index));
        }

        DriverFeatures[] finishingOrder = null;
        String[] expectedOrder = null;
        if (requireFinishingOrder) {
            if (input.finishing_positions == null || input.finishing_positions.isEmpty()) {
                throw new IllegalArgumentException("finishing_positions are required for training/validation");
            }
            expectedOrder = input.finishing_positions.toArray(String[]::new);
            finishingOrder = new DriverFeatures[expectedOrder.length];
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < expectedOrder.length; index++) {
                String driverId = expectedOrder[index];
                if (!seen.add(driverId)) {
                    throw new IllegalArgumentException("Duplicate driver in finishing_positions: " + driverId);
                }
                DriverFeatures features = byDriverId.get(driverId);
                if (features == null) {
                    throw new IllegalArgumentException("Unknown driver in finishing_positions: " + driverId);
                }
                finishingOrder[index] = features;
            }
        }

        return new PreparedRace(input.race_id, drivers, finishingOrder, expectedOrder);
    }

    private static DriverFeatures extractDriver(
            RaceConfig config,
            Strategy strategy,
            int raceIdBucket,
            int trackIndex,
            int tempBucket,
            int baseBucket) {
        if (strategy.driver_id == null || strategy.driver_id.isBlank()) {
            throw new IllegalArgumentException("driver_id is required");
        }
        int currentCompound = FeatureSchema.compoundIndex(strategy.starting_tire);
        int startingCompound = currentCompound;
        List<PitStop> pitStops = strategy.pit_stops == null ? List.of() : strategy.pit_stops;
        short[] ageCounts = new short[FeatureSchema.COMPOUNDS.length * FeatureSchema.AGE_BUCKETS];
        short[] lapCounts = new short[FeatureSchema.COMPOUNDS.length * FeatureSchema.LAP_BUCKETS];
        short[] phaseCounts = new short[FeatureSchema.COMPOUNDS.length * FeatureSchema.PHASE_BUCKETS];
        short[] ageLapCounts = new short[FeatureSchema.COMPOUNDS.length * FeatureSchema.AGE_BUCKETS * FeatureSchema.LAP_BUCKETS];
        short[] agePhaseCounts =
                new short[FeatureSchema.COMPOUNDS.length * FeatureSchema.AGE_BUCKETS * FeatureSchema.PHASE_BUCKETS];
        short[] transitionCounts =
                new short[FeatureSchema.STOP_SLOTS * FeatureSchema.COMPOUNDS.length * FeatureSchema.COMPOUNDS.length * FeatureSchema.LAP_BUCKETS];
        short[] transitionPhaseCounts =
                new short[FeatureSchema.STOP_SLOTS * FeatureSchema.COMPOUNDS.length * FeatureSchema.COMPOUNDS.length * FeatureSchema.PHASE_BUCKETS];

        int currentLap = 1;
        int previousLap = 0;
        for (int stopSlot = 0; stopSlot < pitStops.size(); stopSlot++) {
            PitStop stop = pitStops.get(stopSlot);
            if (stop == null) {
                throw new IllegalArgumentException("pit_stops contains null entries for " + strategy.driver_id);
            }
            if (stop.lap <= previousLap) {
                throw new IllegalArgumentException("pit_stops must be strictly increasing for " + strategy.driver_id);
            }
            if (stop.lap < currentLap || stop.lap > config.total_laps) {
                throw new IllegalArgumentException("Invalid pit stop lap " + stop.lap + " for " + strategy.driver_id);
            }
            if (FeatureSchema.compoundIndex(stop.from_tire) != currentCompound) {
                throw new IllegalArgumentException(
                        "Pit stop from_tire does not match current tire for " + strategy.driver_id);
            }

            int stintLength = stop.lap - currentLap + 1;
            addStint(
                    ageCounts,
                    lapCounts,
                    phaseCounts,
                    ageLapCounts,
                    agePhaseCounts,
                    currentCompound,
                    currentLap,
                    stintLength,
                    config.total_laps);
            if (stopSlot < FeatureSchema.STOP_SLOTS) {
                int transitionFlatIndex = FeatureSchema.transitionFlatIndex(
                        stopSlot,
                        currentCompound,
                        FeatureSchema.compoundIndex(stop.to_tire),
                        stop.lap);
                transitionCounts[transitionFlatIndex]++;
                int transitionPhaseFlatIndex = FeatureSchema.transitionPhaseFlatIndex(
                        stopSlot,
                        currentCompound,
                        FeatureSchema.compoundIndex(stop.to_tire),
                        FeatureSchema.phaseBucket(stop.lap, config.total_laps));
                transitionPhaseCounts[transitionPhaseFlatIndex]++;
            }
            currentCompound = FeatureSchema.compoundIndex(stop.to_tire);
            currentLap = stop.lap + 1;
            previousLap = stop.lap;
        }

        int finalStintLength = config.total_laps - currentLap + 1;
        if (finalStintLength > 0) {
            addStint(
                    ageCounts,
                    lapCounts,
                    phaseCounts,
                    ageLapCounts,
                    agePhaseCounts,
                    currentCompound,
                    currentLap,
                    finalStintLength,
                    config.total_laps);
        }

        int activeAgeCount = 0;
        for (short count : ageCounts) {
            if (count != 0) {
                activeAgeCount++;
            }
        }
        int activeLapCount = 0;
        for (short count : lapCounts) {
            if (count != 0) {
                activeLapCount++;
            }
        }
        int activePhaseCount = 0;
        for (short count : phaseCounts) {
            if (count != 0) {
                activePhaseCount++;
            }
        }
        int activeAgeLapCount = 0;
        for (short count : ageLapCounts) {
            if (count != 0) {
                activeAgeLapCount++;
            }
        }
        int activeAgePhaseCount = 0;
        for (short count : agePhaseCounts) {
            if (count != 0) {
                activeAgePhaseCount++;
            }
        }
        int activeTransitionCount = 0;
        for (short count : transitionCounts) {
            if (count != 0) {
                activeTransitionCount++;
            }
        }
        int activeTransitionPhaseCount = 0;
        for (short count : transitionPhaseCounts) {
            if (count != 0) {
                activeTransitionPhaseCount++;
            }
        }

        int[] activeAgeFlatIndices = new int[activeAgeCount];
        short[] activeAgeCounts = new short[activeAgeCount];
        int cursor = 0;
        for (int flatIndex = 0; flatIndex < ageCounts.length; flatIndex++) {
            short count = ageCounts[flatIndex];
            if (count != 0) {
                activeAgeFlatIndices[cursor] = flatIndex;
                activeAgeCounts[cursor] = count;
                cursor++;
            }
        }
        int[] activeLapFlatIndices = new int[activeLapCount];
        short[] activeLapCounts = new short[activeLapCount];
        cursor = 0;
        for (int flatIndex = 0; flatIndex < lapCounts.length; flatIndex++) {
            short count = lapCounts[flatIndex];
            if (count != 0) {
                activeLapFlatIndices[cursor] = flatIndex;
                activeLapCounts[cursor] = count;
                cursor++;
            }
        }
        int[] activePhaseFlatIndices = new int[activePhaseCount];
        short[] activePhaseCounts = new short[activePhaseCount];
        cursor = 0;
        for (int flatIndex = 0; flatIndex < phaseCounts.length; flatIndex++) {
            short count = phaseCounts[flatIndex];
            if (count != 0) {
                activePhaseFlatIndices[cursor] = flatIndex;
                activePhaseCounts[cursor] = count;
                cursor++;
            }
        }
        int[] activeAgeLapFlatIndices = new int[activeAgeLapCount];
        short[] activeAgeLapActiveCounts = new short[activeAgeLapCount];
        cursor = 0;
        for (int flatIndex = 0; flatIndex < ageLapCounts.length; flatIndex++) {
            short count = ageLapCounts[flatIndex];
            if (count != 0) {
                activeAgeLapFlatIndices[cursor] = flatIndex;
                activeAgeLapActiveCounts[cursor] = count;
                cursor++;
            }
        }
        int[] activeAgePhaseFlatIndices = new int[activeAgePhaseCount];
        short[] activeAgePhaseActiveCounts = new short[activeAgePhaseCount];
        cursor = 0;
        for (int flatIndex = 0; flatIndex < agePhaseCounts.length; flatIndex++) {
            short count = agePhaseCounts[flatIndex];
            if (count != 0) {
                activeAgePhaseFlatIndices[cursor] = flatIndex;
                activeAgePhaseActiveCounts[cursor] = count;
                cursor++;
            }
        }
        int[] activeTransitionFlatIndices = new int[activeTransitionCount];
        short[] activeTransitionActiveCounts = new short[activeTransitionCount];
        cursor = 0;
        for (int flatIndex = 0; flatIndex < transitionCounts.length; flatIndex++) {
            short count = transitionCounts[flatIndex];
            if (count != 0) {
                activeTransitionFlatIndices[cursor] = flatIndex;
                activeTransitionActiveCounts[cursor] = count;
                cursor++;
            }
        }
        int[] activeTransitionPhaseFlatIndices = new int[activeTransitionPhaseCount];
        short[] activeTransitionPhaseActiveCounts = new short[activeTransitionPhaseCount];
        cursor = 0;
        for (int flatIndex = 0; flatIndex < transitionPhaseCounts.length; flatIndex++) {
            short count = transitionPhaseCounts[flatIndex];
            if (count != 0) {
                activeTransitionPhaseFlatIndices[cursor] = flatIndex;
                activeTransitionPhaseActiveCounts[cursor] = count;
                cursor++;
            }
        }

        int stopCount = pitStops.size();
        int stopCountBucket = Math.min(stopCount, FeatureSchema.STOP_COUNT_BUCKETS - 1);
        int finalCompound = currentCompound;
        int onlyStopPhase = stopCount == 1 ? FeatureSchema.phaseBucket(pitStops.get(0).lap, config.total_laps) : 0;
        int finalStopPhase =
                stopCount == 0 ? 0 : FeatureSchema.phaseBucket(pitStops.get(stopCount - 1).lap, config.total_laps);
        int lateSoftFinishPhase = stopCount == 0
                        || finalCompound != SOFT_COMPOUND_INDEX
                        || pitStops.get(stopCount - 1).lap * 100 < config.total_laps * LATE_SOFT_FINISH_MIN_PERCENT
                ? 0
                : finalStopPhase;
        int twoStopFinalHardPhase = stopCount == 2 && finalCompound == HARD_COMPOUND_INDEX ? finalStopPhase : 0;

        return new DriverFeatures(
                strategy.driver_id,
                FeatureSchema.driverIndex(strategy.driver_id),
                raceIdBucket,
                trackIndex,
                tempBucket,
                baseBucket,
                pitStops.size() * config.pit_lane_time,
                stopCountBucket,
                startingCompound,
                finalCompound,
                onlyStopPhase,
                finalStopPhase,
                lateSoftFinishPhase,
                twoStopFinalHardPhase,
                activeAgeFlatIndices,
                activeAgeCounts,
                activeLapFlatIndices,
                activeLapCounts,
                activePhaseFlatIndices,
                activePhaseCounts,
                activeAgeLapFlatIndices,
                activeAgeLapActiveCounts,
                activeTransitionFlatIndices,
                activeTransitionActiveCounts,
                activeAgePhaseFlatIndices,
                activeAgePhaseActiveCounts,
                activeTransitionPhaseFlatIndices,
                activeTransitionPhaseActiveCounts);
    }

    private static void addStint(
            short[] ageCounts,
            short[] lapCounts,
            short[] phaseCounts,
            short[] ageLapCounts,
            short[] agePhaseCounts,
            int compoundIndex,
            int startingLap,
            int stintLength,
            int totalLaps) {
        if (stintLength < 1) {
            throw new IllegalArgumentException("Stint length must be positive");
        }
        if (stintLength > FeatureSchema.MAX_AGE) {
            throw new IllegalArgumentException("Stint length exceeds max supported age: " + stintLength);
        }
        for (int age = 1; age <= stintLength; age++) {
            int ageFlatIndex = FeatureSchema.ageFlatIndex(compoundIndex, age);
            ageCounts[ageFlatIndex]++;

            int absoluteLap = startingLap + age - 1;
            int lapFlatIndex = FeatureSchema.lapFlatIndex(compoundIndex, absoluteLap);
            lapCounts[lapFlatIndex]++;

            int phase = FeatureSchema.phaseBucket(absoluteLap, totalLaps);
            int phaseFlatIndex = FeatureSchema.phaseFlatIndex(compoundIndex, phase);
            phaseCounts[phaseFlatIndex]++;

            int ageLapFlatIndex = FeatureSchema.ageLapFlatIndex(compoundIndex, age, absoluteLap);
            ageLapCounts[ageLapFlatIndex]++;

            int agePhaseFlatIndex = FeatureSchema.agePhaseFlatIndex(compoundIndex, age, phase);
            agePhaseCounts[agePhaseFlatIndex]++;
        }
    }
}
