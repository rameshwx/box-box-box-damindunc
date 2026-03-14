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
    final String driverId;
    final int trackIndex;
    final double tempNorm;
    final double tempNormSq;
    final double baseNorm;
    final double fixedPitPenalty;
    final int[] activeFlatIndices;
    final short[] activeCounts;

    DriverFeatures(
            String driverId,
            int trackIndex,
            double tempNorm,
            double tempNormSq,
            double baseNorm,
            double fixedPitPenalty,
            int[] activeFlatIndices,
            short[] activeCounts) {
        this.driverId = driverId;
        this.trackIndex = trackIndex;
        this.tempNorm = tempNorm;
        this.tempNormSq = tempNormSq;
        this.baseNorm = baseNorm;
        this.fixedPitPenalty = fixedPitPenalty;
        this.activeFlatIndices = activeFlatIndices;
        this.activeCounts = activeCounts;
    }

    double score(Model model) {
        double score = fixedPitPenalty * model.pit_penalty_multiplier;
        for (int index = 0; index < activeFlatIndices.length; index++) {
            int flatIndex = activeFlatIndices[index];
            int compoundIndex = FeatureSchema.compoundFromFlat(flatIndex);
            int age = FeatureSchema.ageFromFlat(flatIndex);
            double count = activeCounts[index];

            score += count * model.global_age[compoundIndex][age];
            score += count * tempNorm * model.global_age_temp[compoundIndex][age];
            score += count * tempNormSq * model.global_age_temp_sq[compoundIndex][age];
            score += count * baseNorm * model.global_age_base[compoundIndex][age];
            score += count * model.track_age[trackIndex][compoundIndex][age];
        }
        return score;
    }

    int countFor(String compound, int age) {
        int flatIndex = FeatureSchema.flatIndex(FeatureSchema.compoundIndex(compound), age);
        for (int index = 0; index < activeFlatIndices.length; index++) {
            if (activeFlatIndices[index] == flatIndex) {
                return activeCounts[index];
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
        int trackIndex = FeatureSchema.trackIndex(config.track);
        if (config.total_laps < 1 || config.total_laps > FeatureSchema.MAX_AGE) {
            throw new IllegalArgumentException(
                    "total_laps must be between 1 and " + FeatureSchema.MAX_AGE + ": " + config.total_laps);
        }

        double tempNorm = FeatureSchema.normalizeTemp(config.track_temp);
        double tempNormSq = tempNorm * tempNorm;
        double baseNorm = FeatureSchema.normalizeBase(config.base_lap_time);

        Map<String, DriverFeatures> byDriverId = new HashMap<>();
        for (Map.Entry<String, Strategy> entry : input.strategies.entrySet()) {
            Strategy strategy = Objects.requireNonNull(entry.getValue(), "Strategy cannot be null");
            DriverFeatures features = extractDriver(config, strategy, trackIndex, tempNorm, tempNormSq, baseNorm);
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
            int trackIndex,
            double tempNorm,
            double tempNormSq,
            double baseNorm) {
        if (strategy.driver_id == null || strategy.driver_id.isBlank()) {
            throw new IllegalArgumentException("driver_id is required");
        }
        int currentCompound = FeatureSchema.compoundIndex(strategy.starting_tire);
        List<PitStop> pitStops = strategy.pit_stops == null ? List.of() : strategy.pit_stops;
        short[] flatCounts = new short[FeatureSchema.COMPOUNDS.length * FeatureSchema.AGE_BUCKETS];

        int currentLap = 1;
        int previousLap = 0;
        Set<String> usedCompounds = new HashSet<>();
        usedCompounds.add(strategy.starting_tire);

        for (PitStop stop : pitStops) {
            if (stop == null) {
                throw new IllegalArgumentException("pit_stops contains null entries for " + strategy.driver_id);
            }
            if (stop.lap <= previousLap) {
                throw new IllegalArgumentException("pit_stops must be strictly increasing for " + strategy.driver_id);
            }
            if (stop.lap < currentLap || stop.lap >= config.total_laps) {
                throw new IllegalArgumentException("Invalid pit stop lap " + stop.lap + " for " + strategy.driver_id);
            }
            if (FeatureSchema.compoundIndex(stop.from_tire) != currentCompound) {
                throw new IllegalArgumentException(
                        "Pit stop from_tire does not match current tire for " + strategy.driver_id);
            }

            int stintLength = stop.lap - currentLap + 1;
            addStint(flatCounts, currentCompound, stintLength);
            currentCompound = FeatureSchema.compoundIndex(stop.to_tire);
            usedCompounds.add(stop.to_tire);
            currentLap = stop.lap + 1;
            previousLap = stop.lap;
        }

        int finalStintLength = config.total_laps - currentLap + 1;
        if (finalStintLength <= 0) {
            throw new IllegalArgumentException("Strategy ends after the race for driver " + strategy.driver_id);
        }
        addStint(flatCounts, currentCompound, finalStintLength);

        if (usedCompounds.size() < 2) {
            throw new IllegalArgumentException("Driver must use at least 2 compounds: " + strategy.driver_id);
        }

        int activeCount = 0;
        for (short count : flatCounts) {
            if (count != 0) {
                activeCount++;
            }
        }

        int[] activeFlatIndices = new int[activeCount];
        short[] activeCounts = new short[activeCount];
        int cursor = 0;
        for (int flatIndex = 0; flatIndex < flatCounts.length; flatIndex++) {
            short count = flatCounts[flatIndex];
            if (count != 0) {
                activeFlatIndices[cursor] = flatIndex;
                activeCounts[cursor] = count;
                cursor++;
            }
        }

        return new DriverFeatures(
                strategy.driver_id,
                trackIndex,
                tempNorm,
                tempNormSq,
                baseNorm,
                pitStops.size() * config.pit_lane_time,
                activeFlatIndices,
                activeCounts);
    }

    private static void addStint(short[] flatCounts, int compoundIndex, int stintLength) {
        if (stintLength < 1) {
            throw new IllegalArgumentException("Stint length must be positive");
        }
        if (stintLength > FeatureSchema.MAX_AGE) {
            throw new IllegalArgumentException("Stint length exceeds max supported age: " + stintLength);
        }
        for (int age = 1; age <= stintLength; age++) {
            int flatIndex = FeatureSchema.flatIndex(compoundIndex, age);
            flatCounts[flatIndex]++;
        }
    }
}
