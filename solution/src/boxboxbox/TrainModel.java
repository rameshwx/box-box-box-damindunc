package boxboxbox;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class TrainModel {
    private static final double LEARNING_RATE = 0.1;
    private static final double L2 = 1e-5;
    private static final double EPSILON = 1e-12;

    private TrainModel() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        List<Path> files = historicalFiles(config.histDir);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No historical files found in " + config.histDir);
        }

        Model model = Model.zero();
        ValidationMetrics baseline = evaluate(model, files, config);
        Model bestModel = model.deepCopy();
        ValidationMetrics bestMetrics = baseline;
        int bestEpoch = 0;

        System.out.printf(
                "Baseline validation: exact=%d/%d (%.4f) pairwise=%d/%d (%.4f)%n",
                baseline.exactCorrect,
                baseline.raceCount,
                baseline.exactAccuracy(),
                baseline.pairwiseCorrect,
                baseline.pairwiseTotal,
                baseline.pairwiseAccuracy());

        OptimizerState state = new OptimizerState();
        for (int epoch = 1; epoch <= config.epochs; epoch++) {
            long pairCount = trainEpoch(model, state, files, config, epoch);
            ValidationMetrics metrics = evaluate(model, files, config);
            System.out.printf(
                    "Epoch %d/%d: pairs=%d exact=%d/%d (%.4f) pairwise=%d/%d (%.4f)%n",
                    epoch,
                    config.epochs,
                    pairCount,
                    metrics.exactCorrect,
                    metrics.raceCount,
                    metrics.exactAccuracy(),
                    metrics.pairwiseCorrect,
                    metrics.pairwiseTotal,
                    metrics.pairwiseAccuracy());

            if (metrics.betterThan(bestMetrics)) {
                bestMetrics = metrics;
                bestModel = model.deepCopy();
                bestEpoch = epoch;
            }
        }

        bestModel.save(config.output);
        boolean improved = bestMetrics.exactAccuracy() > baseline.exactAccuracy()
                || (bestMetrics.exactAccuracy() == baseline.exactAccuracy()
                        && bestMetrics.pairwiseAccuracy() > baseline.pairwiseAccuracy());

        System.out.printf(
                "Saved model to %s%nBest validation: best_epoch=%d exact=%d/%d (%.4f) pairwise=%d/%d (%.4f)%n",
                config.output,
                bestEpoch,
                bestMetrics.exactCorrect,
                bestMetrics.raceCount,
                bestMetrics.exactAccuracy(),
                bestMetrics.pairwiseCorrect,
                bestMetrics.pairwiseTotal,
                bestMetrics.pairwiseAccuracy());
        if (!improved) {
            System.out.println("Warning: trained model did not beat the zero-weight baseline.");
        }
    }

    private static long trainEpoch(
            Model model,
            OptimizerState state,
            List<Path> files,
            Config config,
            int epoch) throws IOException {
        List<Path> shuffledFiles = new ArrayList<>(files);
        Collections.shuffle(shuffledFiles, new Random(config.seed + epoch * 31L));
        Gson gson = new Gson();
        long pairCount = 0;

        for (Path path : shuffledFiles) {
            List<PreparedRace> races = new ArrayList<>();
            for (RaceInput race : readHistoricalFile(path, gson)) {
                if (config.includesRace(race) && !config.isValidationRace(race.race_id)) {
                    races.add(FeatureExtractor.prepareRace(race, true));
                }
            }
            Collections.shuffle(races, new Random(config.seed ^ path.toString().hashCode() ^ epoch));
            for (PreparedRace race : races) {
                DriverFeatures[] ordered = race.finishingOrder;
                for (int betterIndex = 0; betterIndex < ordered.length - 1; betterIndex++) {
                    DriverFeatures better = ordered[betterIndex];
                    for (int worseIndex = betterIndex + 1; worseIndex < ordered.length; worseIndex++) {
                        DriverFeatures worse = ordered[worseIndex];
                        updatePair(model, state, better, worse);
                        pairCount++;
                    }
                }
            }
        }
        return pairCount;
    }

    private static ValidationMetrics evaluate(Model model, List<Path> files, Config config) throws IOException {
        Gson gson = new Gson();
        ValidationMetrics metrics = new ValidationMetrics();

        for (Path path : files) {
            for (RaceInput race : readHistoricalFile(path, gson)) {
                if (!config.includesRace(race) || !config.isValidationRace(race.race_id)) {
                    continue;
                }
                PreparedRace prepared = FeatureExtractor.prepareRace(race, true);
                List<String> predicted = Predictor.predictOrder(prepared.drivers, model);
                metrics.recordExact(Arrays.equals(prepared.expectedOrder, predicted.toArray(String[]::new)));

                for (int betterIndex = 0; betterIndex < prepared.finishingOrder.length - 1; betterIndex++) {
                    DriverFeatures better = prepared.finishingOrder[betterIndex];
                    double betterScore = better.score(model);
                    for (int worseIndex = betterIndex + 1; worseIndex < prepared.finishingOrder.length; worseIndex++) {
                        DriverFeatures worse = prepared.finishingOrder[worseIndex];
                        double worseScore = worse.score(model);
                        boolean correct = compare(better.driverId, betterScore, worse.driverId, worseScore) < 0;
                        metrics.recordPairwise(correct);
                    }
                }
            }
        }
        return metrics;
    }

    private static void updatePair(Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse) {
        double delta = worse.score(model) - better.score(model);
        double logistic = inverseLogit(delta);
        updateAgePair(model, state, better, worse, logistic);
        updateLapPair(model, state, better, worse, logistic);
        updatePhasePair(model, state, better, worse, logistic);
        updateAgeLapPair(model, state, better, worse, logistic);
        updateAgePhasePair(model, state, better, worse, logistic);
        updateTransitionPair(model, state, better, worse, logistic);
        updateTransitionPhasePair(model, state, better, worse, logistic);
    }

    private static void updateAgePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double logistic) {
        int betterCursor = 0;
        int worseCursor = 0;

        while (betterCursor < better.activeAgeFlatIndices.length || worseCursor < worse.activeAgeFlatIndices.length) {
            int betterFlat =
                    betterCursor < better.activeAgeFlatIndices.length ? better.activeAgeFlatIndices[betterCursor] : Integer.MAX_VALUE;
            int worseFlat =
                    worseCursor < worse.activeAgeFlatIndices.length ? worse.activeAgeFlatIndices[worseCursor] : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activeAgeCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activeAgeCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int compoundIndex = FeatureSchema.compoundFromAgeFlat(flatIndex);
            int age = FeatureSchema.ageFromFlat(flatIndex);
            apply(model.global_age, state.global_age_accum, compoundIndex, age, logistic, diffCount);
            apply(
                    model.temp_age[better.tempBucket],
                    state.temp_age_accum[better.tempBucket],
                    compoundIndex,
                    age,
                    logistic,
                    diffCount);
            apply(
                    model.base_age[better.baseBucket],
                    state.base_age_accum[better.baseBucket],
                    compoundIndex,
                    age,
                    logistic,
                    diffCount);
            apply(
                    model.track_age[better.trackIndex],
                    state.track_age_accum[better.trackIndex],
                    compoundIndex,
                    age,
                    logistic,
                    diffCount);
        }
    }

    private static void updateLapPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double logistic) {
        int betterCursor = 0;
        int worseCursor = 0;

        while (betterCursor < better.activeLapFlatIndices.length || worseCursor < worse.activeLapFlatIndices.length) {
            int betterFlat =
                    betterCursor < better.activeLapFlatIndices.length ? better.activeLapFlatIndices[betterCursor] : Integer.MAX_VALUE;
            int worseFlat =
                    worseCursor < worse.activeLapFlatIndices.length ? worse.activeLapFlatIndices[worseCursor] : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activeLapCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activeLapCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int compoundIndex = FeatureSchema.compoundFromLapFlat(flatIndex);
            int lap = FeatureSchema.lapFromFlat(flatIndex);
            apply(model.global_lap, state.global_lap_accum, compoundIndex, lap, logistic, diffCount);
            apply(
                    model.temp_lap[better.tempBucket],
                    state.temp_lap_accum[better.tempBucket],
                    compoundIndex,
                    lap,
                    logistic,
                    diffCount);
            apply(
                    model.base_lap[better.baseBucket],
                    state.base_lap_accum[better.baseBucket],
                    compoundIndex,
                    lap,
                    logistic,
                    diffCount);
            apply(
                    model.track_lap[better.trackIndex],
                    state.track_lap_accum[better.trackIndex],
                    compoundIndex,
                    lap,
                    logistic,
                    diffCount);
        }
    }

    private static void updatePhasePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double logistic) {
        int betterCursor = 0;
        int worseCursor = 0;

        while (betterCursor < better.activePhaseFlatIndices.length || worseCursor < worse.activePhaseFlatIndices.length) {
            int betterFlat = betterCursor < better.activePhaseFlatIndices.length
                    ? better.activePhaseFlatIndices[betterCursor]
                    : Integer.MAX_VALUE;
            int worseFlat = worseCursor < worse.activePhaseFlatIndices.length
                    ? worse.activePhaseFlatIndices[worseCursor]
                    : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activePhaseCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activePhaseCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int compoundIndex = FeatureSchema.compoundFromPhaseFlat(flatIndex);
            int phase = FeatureSchema.phaseFromFlat(flatIndex);
            apply(model.global_phase, state.global_phase_accum, compoundIndex, phase, logistic, diffCount);
        }
    }

    private static void updateAgeLapPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double logistic) {
        int betterCursor = 0;
        int worseCursor = 0;

        while (betterCursor < better.activeAgeLapFlatIndices.length || worseCursor < worse.activeAgeLapFlatIndices.length) {
            int betterFlat = betterCursor < better.activeAgeLapFlatIndices.length
                    ? better.activeAgeLapFlatIndices[betterCursor]
                    : Integer.MAX_VALUE;
            int worseFlat = worseCursor < worse.activeAgeLapFlatIndices.length
                    ? worse.activeAgeLapFlatIndices[worseCursor]
                    : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activeAgeLapCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activeAgeLapCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int compoundIndex = FeatureSchema.compoundFromAgeLapFlat(flatIndex);
            int age = FeatureSchema.ageFromAgeLapFlat(flatIndex);
            int lap = FeatureSchema.lapFromAgeLapFlat(flatIndex);
            apply(model.global_age_lap, state.global_age_lap_accum, compoundIndex, age, lap, logistic, diffCount);
        }
    }

    private static void updateAgePhasePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double logistic) {
        int betterCursor = 0;
        int worseCursor = 0;

        while (betterCursor < better.activeAgePhaseFlatIndices.length
                || worseCursor < worse.activeAgePhaseFlatIndices.length) {
            int betterFlat = betterCursor < better.activeAgePhaseFlatIndices.length
                    ? better.activeAgePhaseFlatIndices[betterCursor]
                    : Integer.MAX_VALUE;
            int worseFlat = worseCursor < worse.activeAgePhaseFlatIndices.length
                    ? worse.activeAgePhaseFlatIndices[worseCursor]
                    : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activeAgePhaseCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activeAgePhaseCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int compoundIndex = FeatureSchema.compoundFromAgePhaseFlat(flatIndex);
            int age = FeatureSchema.ageFromAgePhaseFlat(flatIndex);
            int phase = FeatureSchema.phaseFromAgePhaseFlat(flatIndex);
            apply(model.global_age_phase, state.global_age_phase_accum, compoundIndex, age, phase, logistic, diffCount);
        }
    }

    private static void updateTransitionPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double logistic) {
        int betterCursor = 0;
        int worseCursor = 0;

        while (betterCursor < better.activeTransitionFlatIndices.length
                || worseCursor < worse.activeTransitionFlatIndices.length) {
            int betterFlat = betterCursor < better.activeTransitionFlatIndices.length
                    ? better.activeTransitionFlatIndices[betterCursor]
                    : Integer.MAX_VALUE;
            int worseFlat = worseCursor < worse.activeTransitionFlatIndices.length
                    ? worse.activeTransitionFlatIndices[worseCursor]
                    : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activeTransitionCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activeTransitionCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int stopSlot = FeatureSchema.stopSlotFromTransitionFlat(flatIndex);
            int fromCompound = FeatureSchema.fromCompoundFromTransitionFlat(flatIndex);
            int toCompound = FeatureSchema.toCompoundFromTransitionFlat(flatIndex);
            int lap = FeatureSchema.lapFromTransitionFlat(flatIndex);
            apply(
                    model.transition_weight,
                    state.transition_weight_accum,
                    stopSlot,
                    fromCompound,
                    toCompound,
                    lap,
                    logistic,
                    diffCount);
        }
    }

    private static void updateTransitionPhasePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double logistic) {
        int betterCursor = 0;
        int worseCursor = 0;

        while (betterCursor < better.activeTransitionPhaseFlatIndices.length
                || worseCursor < worse.activeTransitionPhaseFlatIndices.length) {
            int betterFlat = betterCursor < better.activeTransitionPhaseFlatIndices.length
                    ? better.activeTransitionPhaseFlatIndices[betterCursor]
                    : Integer.MAX_VALUE;
            int worseFlat = worseCursor < worse.activeTransitionPhaseFlatIndices.length
                    ? worse.activeTransitionPhaseFlatIndices[worseCursor]
                    : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activeTransitionPhaseCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activeTransitionPhaseCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int stopSlot = FeatureSchema.stopSlotFromTransitionPhaseFlat(flatIndex);
            int fromCompound = FeatureSchema.fromCompoundFromTransitionPhaseFlat(flatIndex);
            int toCompound = FeatureSchema.toCompoundFromTransitionPhaseFlat(flatIndex);
            int phase = FeatureSchema.phaseFromTransitionPhaseFlat(flatIndex);
            apply(
                    model.transition_phase_weight,
                    state.transition_phase_weight_accum,
                    stopSlot,
                    fromCompound,
                    toCompound,
                    phase,
                    logistic,
                    diffCount);
        }
    }

    private static void apply(
            double[][] weights,
            double[][] accum,
            int compoundIndex,
            int age,
            double logistic,
            double featureValue) {
        double gradient = -logistic * featureValue + L2 * weights[compoundIndex][age];
        accum[compoundIndex][age] += gradient * gradient;
        double adjustedRate = LEARNING_RATE / Math.sqrt(accum[compoundIndex][age] + EPSILON);
        weights[compoundIndex][age] -= adjustedRate * gradient;
    }

    private static void apply(
            double[][][] weights,
            double[][][] accum,
            int compoundIndex,
            int age,
            int lap,
            double logistic,
            double featureValue) {
        double gradient = -logistic * featureValue + L2 * weights[compoundIndex][age][lap];
        accum[compoundIndex][age][lap] += gradient * gradient;
        double adjustedRate = LEARNING_RATE / Math.sqrt(accum[compoundIndex][age][lap] + EPSILON);
        weights[compoundIndex][age][lap] -= adjustedRate * gradient;
    }

    private static void apply(
            double[][][][] weights,
            double[][][][] accum,
            int stopSlot,
            int fromCompound,
            int toCompound,
            int lap,
            double logistic,
            double featureValue) {
        double gradient = -logistic * featureValue + L2 * weights[stopSlot][fromCompound][toCompound][lap];
        accum[stopSlot][fromCompound][toCompound][lap] += gradient * gradient;
        double adjustedRate = LEARNING_RATE / Math.sqrt(accum[stopSlot][fromCompound][toCompound][lap] + EPSILON);
        weights[stopSlot][fromCompound][toCompound][lap] -= adjustedRate * gradient;
    }

    private static int compare(String leftId, double leftScore, String rightId, double rightScore) {
        int byScore = Double.compare(leftScore, rightScore);
        if (byScore != 0) {
            return byScore;
        }
        return leftId.compareTo(rightId);
    }

    private static double inverseLogit(double delta) {
        if (delta >= 0.0) {
            double exp = Math.exp(-delta);
            return exp / (1.0 + exp);
        }
        return 1.0 / (1.0 + Math.exp(delta));
    }

    private static List<Path> historicalFiles(Path histDir) throws IOException {
        try (var stream = Files.list(histDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static RaceInput[] readHistoricalFile(Path path, Gson gson) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            RaceInput[] races = gson.fromJson(reader, RaceInput[].class);
            return Objects.requireNonNullElseGet(races, () -> new RaceInput[0]);
        }
    }

    private static int totalStops(RaceInput race) {
        if (race == null || race.strategies == null) {
            return 0;
        }
        int totalStops = 0;
        for (Strategy strategy : race.strategies.values()) {
            if (strategy != null && strategy.pit_stops != null) {
                totalStops += strategy.pit_stops.size();
            }
        }
        return totalStops;
    }

    private static int totalLaps(RaceInput race) {
        if (race == null || race.race_config == null) {
            return 0;
        }
        return race.race_config.total_laps;
    }

    private static final class Config {
        final Path histDir;
        final Path output;
        final int valMod;
        final int valRem;
        final int epochs;
        final long seed;
        final Integer minTotalStops;
        final Integer maxTotalStops;
        final Integer minTotalLaps;
        final Integer maxTotalLaps;
        final String track;
        final Integer minTrackTemp;
        final Integer maxTrackTemp;

        Config(
                Path histDir,
                Path output,
                int valMod,
                int valRem,
                int epochs,
                long seed,
                Integer minTotalStops,
                Integer maxTotalStops,
                Integer minTotalLaps,
                Integer maxTotalLaps,
                String track,
                Integer minTrackTemp,
                Integer maxTrackTemp) {
            this.histDir = histDir;
            this.output = output;
            this.valMod = valMod;
            this.valRem = valRem;
            this.epochs = epochs;
            this.seed = seed;
            this.minTotalStops = minTotalStops;
            this.maxTotalStops = maxTotalStops;
            this.minTotalLaps = minTotalLaps;
            this.maxTotalLaps = maxTotalLaps;
            this.track = track;
            this.minTrackTemp = minTrackTemp;
            this.maxTrackTemp = maxTrackTemp;
        }

        boolean isValidationRace(String raceId) {
            return Math.floorMod(raceId.hashCode(), valMod) == valRem;
        }

        boolean includesRace(RaceInput race) {
            if (track != null) {
                if (race == null || race.race_config == null || !track.equals(race.race_config.track)) {
                    return false;
                }
            }
            if (minTrackTemp != null) {
                if (race == null || race.race_config == null || race.race_config.track_temp < minTrackTemp) {
                    return false;
                }
            }
            if (maxTrackTemp != null) {
                if (race == null || race.race_config == null || race.race_config.track_temp > maxTrackTemp) {
                    return false;
                }
            }
            int raceTotalStops = totalStops(race);
            if (minTotalStops != null && raceTotalStops < minTotalStops) {
                return false;
            }
            if (maxTotalStops != null && raceTotalStops > maxTotalStops) {
                return false;
            }
            int raceTotalLaps = totalLaps(race);
            if (minTotalLaps != null && raceTotalLaps < minTotalLaps) {
                return false;
            }
            if (maxTotalLaps != null && raceTotalLaps > maxTotalLaps) {
                return false;
            }
            return true;
        }

        static Config parse(String[] args) {
            Path histDir = Path.of("data", "historical_races");
            Path output = Path.of("solution", "model.json");
            int valMod = 10;
            int valRem = 0;
            int epochs = 5;
            long seed = 20260314L;
            Integer minTotalStops = null;
            Integer maxTotalStops = null;
            Integer minTotalLaps = null;
            Integer maxTotalLaps = null;
            String track = null;
            Integer minTrackTemp = null;
            Integer maxTrackTemp = null;

            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for argument: " + arg);
                }
                String value = args[++index];
                switch (arg) {
                    case "--hist-dir" -> histDir = Path.of(value);
                    case "--out" -> output = Path.of(value);
                    case "--val-mod" -> valMod = Integer.parseInt(value);
                    case "--val-rem" -> valRem = Integer.parseInt(value);
                    case "--epochs" -> epochs = Integer.parseInt(value);
                    case "--seed" -> seed = Long.parseLong(value);
                    case "--min-total-stops" -> minTotalStops = Integer.parseInt(value);
                    case "--max-total-stops" -> maxTotalStops = Integer.parseInt(value);
                    case "--min-total-laps" -> minTotalLaps = Integer.parseInt(value);
                    case "--max-total-laps" -> maxTotalLaps = Integer.parseInt(value);
                    case "--track" -> track = value;
                    case "--min-track-temp" -> minTrackTemp = Integer.parseInt(value);
                    case "--max-track-temp" -> maxTrackTemp = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            if (valMod <= 0) {
                throw new IllegalArgumentException("--val-mod must be positive");
            }
            if (valRem < 0 || valRem >= valMod) {
                throw new IllegalArgumentException("--val-rem must be in [0, val-mod)");
            }
            if (epochs <= 0) {
                throw new IllegalArgumentException("--epochs must be positive");
            }
            if (minTotalStops != null && minTotalStops < 0) {
                throw new IllegalArgumentException("--min-total-stops must be non-negative");
            }
            if (maxTotalStops != null && maxTotalStops < 0) {
                throw new IllegalArgumentException("--max-total-stops must be non-negative");
            }
            if (minTotalStops != null && maxTotalStops != null && minTotalStops > maxTotalStops) {
                throw new IllegalArgumentException("--min-total-stops cannot be greater than --max-total-stops");
            }
            if (minTotalLaps != null && minTotalLaps < 0) {
                throw new IllegalArgumentException("--min-total-laps must be non-negative");
            }
            if (maxTotalLaps != null && maxTotalLaps < 0) {
                throw new IllegalArgumentException("--max-total-laps must be non-negative");
            }
            if (minTotalLaps != null && maxTotalLaps != null && minTotalLaps > maxTotalLaps) {
                throw new IllegalArgumentException("--min-total-laps cannot be greater than --max-total-laps");
            }
            if (minTrackTemp != null && maxTrackTemp != null && minTrackTemp > maxTrackTemp) {
                throw new IllegalArgumentException("--min-track-temp cannot be greater than --max-track-temp");
            }
            return new Config(
                    histDir,
                    output,
                    valMod,
                    valRem,
                    epochs,
                    seed,
                    minTotalStops,
                    maxTotalStops,
                    minTotalLaps,
                    maxTotalLaps,
                    track,
                    minTrackTemp,
                    maxTrackTemp);
        }
    }

    private static final class OptimizerState {
        final double[][] global_age_accum = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][][] temp_age_accum =
                new double[FeatureSchema.TEMP_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][][] base_age_accum =
                new double[FeatureSchema.BASE_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][][] track_age_accum =
                new double[FeatureSchema.TRACKS.length][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][] global_lap_accum = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
        final double[][][] temp_lap_accum =
                new double[FeatureSchema.TEMP_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
        final double[][][] base_lap_accum =
                new double[FeatureSchema.BASE_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
        final double[][][] track_lap_accum =
                new double[FeatureSchema.TRACKS.length][FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
        final double[][] global_phase_accum = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.PHASE_BUCKETS];
        final double[][][] global_age_lap_accum =
                new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS][FeatureSchema.LAP_BUCKETS];
        final double[][][] global_age_phase_accum =
                new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS][FeatureSchema.PHASE_BUCKETS];
        final double[][][][] transition_weight_accum =
                new double[FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length][FeatureSchema.COMPOUNDS.length]
                        [FeatureSchema.LAP_BUCKETS];
        final double[][][][] transition_phase_weight_accum =
                new double[FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length][FeatureSchema.COMPOUNDS.length]
                        [FeatureSchema.PHASE_BUCKETS];
    }

    private static final class ValidationMetrics {
        long raceCount;
        long exactCorrect;
        long pairwiseTotal;
        long pairwiseCorrect;

        void recordExact(boolean correct) {
            raceCount++;
            if (correct) {
                exactCorrect++;
            }
        }

        void recordPairwise(boolean correct) {
            pairwiseTotal++;
            if (correct) {
                pairwiseCorrect++;
            }
        }

        double exactAccuracy() {
            return raceCount == 0 ? 0.0 : (double) exactCorrect / raceCount;
        }

        double pairwiseAccuracy() {
            return pairwiseTotal == 0 ? 0.0 : (double) pairwiseCorrect / pairwiseTotal;
        }

        boolean betterThan(ValidationMetrics other) {
            if (exactAccuracy() != other.exactAccuracy()) {
                return exactAccuracy() > other.exactAccuracy();
            }
            return pairwiseAccuracy() > other.pairwiseAccuracy();
        }
    }
}
