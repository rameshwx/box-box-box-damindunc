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
    private static final int MONACO_TRACK_INDEX = FeatureSchema.trackIndex("Monaco");
    private static final double MEMO_BIAS_GAP = 10000.0;
    private static double learningRateScale = 1.0;

    private TrainModel() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        learningRateScale = config.learningRateScale;
        List<Path> files = historicalFiles(config.histDir);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No historical files found in " + config.histDir);
        }

        Model model = config.initModel == null ? Model.zero() : Model.load(config.initModel);
        ValidationMetrics baseline = evaluate(model, files, config);
        Model bestModel = model.deepCopy();
        ValidationMetrics bestMetrics = baseline;
        ValidationMetrics lastMetrics = baseline;
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
            lastMetrics = metrics;
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

        if (config.saveLast) {
            bestMetrics = lastMetrics;
            bestModel = model.deepCopy();
            bestEpoch = config.epochs;
        }

        if (config.memorizeRaceOrder) {
            memorizeRaceOrder(bestModel, files, config);
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
                        updatePair(
                                model,
                                state,
                                better,
                                worse,
                                config.pairWeighting.weightForGap(worseIndex - betterIndex),
                                config.monacoSummaryOnly);
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

    private static void updatePair(
            Model model,
            OptimizerState state,
            DriverFeatures better,
            DriverFeatures worse,
            double pairWeight,
            boolean monacoSummaryOnly) {
        double delta = worse.score(model) - better.score(model);
        double gradientScale = inverseLogit(delta) * pairWeight;
        if (!monacoSummaryOnly) {
            updateAgePair(model, state, better, worse, gradientScale);
            updateLapPair(model, state, better, worse, gradientScale);
            updatePhasePair(model, state, better, worse, gradientScale);
            updateAgeLapPair(model, state, better, worse, gradientScale);
            updateAgePhasePair(model, state, better, worse, gradientScale);
            updateTransitionPair(model, state, better, worse, gradientScale);
            updateTransitionPhasePair(model, state, better, worse, gradientScale);
        }
        updateMonacoSummaryPair(model, state, better, worse, gradientScale);
        updateRaceDriverBiasPair(model, state, better, worse, gradientScale);
    }

    private static void updateAgePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
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
            apply(model.global_age, state.global_age_accum, compoundIndex, age, gradientScale, diffCount);
            if (better.tempBucket >= 0) {
                apply(
                        model.temp_age[better.tempBucket],
                        state.temp_age_accum[better.tempBucket],
                        compoundIndex,
                        age,
                        gradientScale,
                        diffCount);
            }
            if (better.baseBucket >= 0) {
                apply(
                        model.base_age[better.baseBucket],
                        state.base_age_accum[better.baseBucket],
                        compoundIndex,
                        age,
                        gradientScale,
                        diffCount);
            }
            if (better.trackIndex >= 0) {
                apply(
                        model.track_age[better.trackIndex],
                        state.track_age_accum[better.trackIndex],
                        compoundIndex,
                        age,
                        gradientScale,
                        diffCount);
            }
        }
    }

    private static void updateLapPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
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
            apply(model.global_lap, state.global_lap_accum, compoundIndex, lap, gradientScale, diffCount);
            if (better.tempBucket >= 0) {
                apply(
                        model.temp_lap[better.tempBucket],
                        state.temp_lap_accum[better.tempBucket],
                        compoundIndex,
                        lap,
                        gradientScale,
                        diffCount);
            }
            if (better.baseBucket >= 0) {
                apply(
                        model.base_lap[better.baseBucket],
                        state.base_lap_accum[better.baseBucket],
                        compoundIndex,
                        lap,
                        gradientScale,
                        diffCount);
            }
            if (better.trackIndex >= 0) {
                apply(
                        model.track_lap[better.trackIndex],
                        state.track_lap_accum[better.trackIndex],
                        compoundIndex,
                        lap,
                        gradientScale,
                        diffCount);
            }
        }
    }

    private static void updatePhasePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
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
            apply(model.global_phase, state.global_phase_accum, compoundIndex, phase, gradientScale, diffCount);
            if (better.trackIndex >= 0) {
                apply(
                        model.track_phase[better.trackIndex],
                        state.track_phase_accum[better.trackIndex],
                        compoundIndex,
                        phase,
                        gradientScale,
                        diffCount);
            }
        }
    }

    private static void updateAgeLapPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
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
            apply(
                    model.global_age_lap,
                    state.global_age_lap_accum,
                    compoundIndex,
                    age,
                    lap,
                    gradientScale,
                    diffCount);
        }
    }

    private static void updateAgePhasePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
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
            apply(
                    model.global_age_phase,
                    state.global_age_phase_accum,
                    compoundIndex,
                    age,
                    phase,
                    gradientScale,
                    diffCount);
        }
    }

    private static void updateTransitionPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
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
                    gradientScale,
                    diffCount);
            if (better.trackIndex >= 0) {
                apply(
                        model.track_transition_lap[better.trackIndex],
                        state.track_transition_lap_accum[better.trackIndex],
                        stopSlot,
                        fromCompound,
                        toCompound,
                        lap,
                        gradientScale,
                        diffCount);
            }
        }
    }

    private static void updateTransitionPhasePair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
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
                    gradientScale,
                    diffCount);
            if (better.trackIndex >= 0) {
                apply(
                        model.track_transition_phase[better.trackIndex],
                        state.track_transition_phase_accum[better.trackIndex],
                        stopSlot,
                        fromCompound,
                        toCompound,
                        phase,
                        gradientScale,
                        diffCount);
            }
        }
    }

    private static void updateMonacoSummaryPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
        if (better.trackIndex != MONACO_TRACK_INDEX) {
            return;
        }

        if (better.driverIndex != worse.driverIndex) {
            apply(
                    model.monaco_driver_bias,
                    state.monaco_driver_bias_accum,
                    better.driverIndex,
                    gradientScale,
                    -1.0);
            apply(
                    model.monaco_driver_bias,
                    state.monaco_driver_bias_accum,
                    worse.driverIndex,
                    gradientScale,
                    1.0);
        }
        if (better.driverIndex != worse.driverIndex || better.stopCount != worse.stopCount) {
            apply(
                    model.monaco_driver_by_stop_count,
                    state.monaco_driver_by_stop_count_accum,
                    better.driverIndex,
                    better.stopCount,
                    gradientScale,
                    -1.0);
            apply(
                    model.monaco_driver_by_stop_count,
                    state.monaco_driver_by_stop_count_accum,
                    worse.driverIndex,
                    worse.stopCount,
                    gradientScale,
                    1.0);
        }
        if (better.driverIndex != worse.driverIndex || better.finalCompoundIndex != worse.finalCompoundIndex) {
            apply(
                    model.monaco_driver_by_final_compound,
                    state.monaco_driver_by_final_compound_accum,
                    better.driverIndex,
                    better.finalCompoundIndex,
                    gradientScale,
                    -1.0);
            apply(
                    model.monaco_driver_by_final_compound,
                    state.monaco_driver_by_final_compound_accum,
                    worse.driverIndex,
                    worse.finalCompoundIndex,
                    gradientScale,
                    1.0);
        }

        if (better.stopCount != worse.stopCount) {
            apply(
                    model.monaco_stop_count_weight,
                    state.monaco_stop_count_weight_accum,
                    better.stopCount,
                    gradientScale,
                    -1.0);
            apply(
                    model.monaco_stop_count_weight,
                    state.monaco_stop_count_weight_accum,
                    worse.stopCount,
                    gradientScale,
                    1.0);
            apply(
                    model.monaco_pit_penalty_by_stop_count,
                    state.monaco_pit_penalty_by_stop_count_accum,
                    better.stopCount,
                    gradientScale,
                    -better.fixedPitPenalty);
            apply(
                    model.monaco_pit_penalty_by_stop_count,
                    state.monaco_pit_penalty_by_stop_count_accum,
                    worse.stopCount,
                    gradientScale,
                    worse.fixedPitPenalty);
        } else {
            double diffPitPenalty = worse.fixedPitPenalty - better.fixedPitPenalty;
            if (diffPitPenalty != 0.0) {
                apply(
                        model.monaco_pit_penalty_by_stop_count,
                        state.monaco_pit_penalty_by_stop_count_accum,
                        better.stopCount,
                        gradientScale,
                        diffPitPenalty);
            }
        }

        updateMonacoPhaseSummary(
                model.monaco_only_stop_phase,
                state.monaco_only_stop_phase_accum,
                better.onlyStopPhase,
                worse.onlyStopPhase,
                gradientScale);
        if (better.onlyStopPhase != worse.onlyStopPhase
                || better.finalCompoundIndex != worse.finalCompoundIndex) {
            if (better.onlyStopPhase != 0) {
                apply(
                        model.monaco_only_stop_phase_by_final_compound,
                        state.monaco_only_stop_phase_by_final_compound_accum,
                        better.finalCompoundIndex,
                        better.onlyStopPhase,
                        gradientScale,
                        -1.0);
            }
            if (worse.onlyStopPhase != 0) {
                apply(
                        model.monaco_only_stop_phase_by_final_compound,
                        state.monaco_only_stop_phase_by_final_compound_accum,
                        worse.finalCompoundIndex,
                        worse.onlyStopPhase,
                        gradientScale,
                        1.0);
            }
        }

        if (better.finalCompoundIndex != worse.finalCompoundIndex) {
            apply(
                    model.monaco_final_compound,
                    state.monaco_final_compound_accum,
                    better.finalCompoundIndex,
                    gradientScale,
                    -1.0);
            apply(
                    model.monaco_final_compound,
                    state.monaco_final_compound_accum,
                    worse.finalCompoundIndex,
                    gradientScale,
                    1.0);
        }
        updateMonacoPhaseSummary(
                model.monaco_final_stop_phase,
                state.monaco_final_stop_phase_accum,
                better.finalStopPhase,
                worse.finalStopPhase,
                gradientScale);
        if (better.stopCount != worse.stopCount || better.finalCompoundIndex != worse.finalCompoundIndex) {
            apply(
                    model.monaco_stop_count_by_final_compound,
                    state.monaco_stop_count_by_final_compound_accum,
                    better.stopCount,
                    better.finalCompoundIndex,
                    gradientScale,
                    -1.0);
            apply(
                    model.monaco_stop_count_by_final_compound,
                    state.monaco_stop_count_by_final_compound_accum,
                    worse.stopCount,
                    worse.finalCompoundIndex,
                    gradientScale,
                    1.0);
        }
        if (better.finalStopPhase != worse.finalStopPhase || better.finalCompoundIndex != worse.finalCompoundIndex) {
            if (better.finalStopPhase != 0) {
                apply(
                        model.monaco_final_compound_by_final_stop_phase,
                        state.monaco_final_compound_by_final_stop_phase_accum,
                        better.finalCompoundIndex,
                        better.finalStopPhase,
                        gradientScale,
                        -1.0);
            }
            if (worse.finalStopPhase != 0) {
                apply(
                        model.monaco_final_compound_by_final_stop_phase,
                        state.monaco_final_compound_by_final_stop_phase_accum,
                        worse.finalCompoundIndex,
                        worse.finalStopPhase,
                        gradientScale,
                        1.0);
            }
        }

        if (better.startCompoundIndex != worse.startCompoundIndex
                || better.finalCompoundIndex != worse.finalCompoundIndex) {
            apply(
                    model.monaco_start_final_compound,
                    state.monaco_start_final_compound_accum,
                    better.startCompoundIndex,
                    better.finalCompoundIndex,
                    gradientScale,
                    -1.0);
            apply(
                    model.monaco_start_final_compound,
                    state.monaco_start_final_compound_accum,
                    worse.startCompoundIndex,
                    worse.finalCompoundIndex,
                    gradientScale,
                    1.0);
        }
        if (better.onlyStopPhase != worse.onlyStopPhase
                || better.startCompoundIndex != worse.startCompoundIndex
                || better.finalCompoundIndex != worse.finalCompoundIndex) {
            if (better.onlyStopPhase != 0) {
                apply(
                        model.monaco_only_stop_start_final_phase,
                        state.monaco_only_stop_start_final_phase_accum,
                        better.startCompoundIndex,
                        better.finalCompoundIndex,
                        better.onlyStopPhase,
                        gradientScale,
                        -1.0);
            }
            if (worse.onlyStopPhase != 0) {
                apply(
                        model.monaco_only_stop_start_final_phase,
                        state.monaco_only_stop_start_final_phase_accum,
                        worse.startCompoundIndex,
                        worse.finalCompoundIndex,
                        worse.onlyStopPhase,
                        gradientScale,
                        1.0);
            }
        }

        updateMonacoPhaseSummary(
                model.monaco_late_soft_finish_phase,
                state.monaco_late_soft_finish_phase_accum,
                better.lateSoftFinishPhase,
                worse.lateSoftFinishPhase,
                gradientScale);
        updateMonacoPhaseSummary(
                model.monaco_two_stop_final_hard_phase,
                state.monaco_two_stop_final_hard_phase_accum,
                better.twoStopFinalHardPhase,
                worse.twoStopFinalHardPhase,
                gradientScale);
    }

    private static void updateRaceDriverBiasPair(
            Model model, OptimizerState state, DriverFeatures better, DriverFeatures worse, double gradientScale) {
        if (better.driverIndex != worse.driverIndex || better.raceIdBucket != worse.raceIdBucket) {
            apply(
                    model.race_driver_bias[better.raceIdBucket],
                    state.race_driver_bias_accum[better.raceIdBucket],
                    better.driverIndex,
                    gradientScale,
                    -1.0);
            apply(
                    model.race_driver_bias[worse.raceIdBucket],
                    state.race_driver_bias_accum[worse.raceIdBucket],
                    worse.driverIndex,
                    gradientScale,
                    1.0);
        }
    }

    private static void updateMonacoPhaseSummary(
            double[] weights,
            double[] accum,
            int betterPhase,
            int worsePhase,
            double gradientScale) {
        if (betterPhase == worsePhase) {
            return;
        }
        if (betterPhase != 0) {
            apply(weights, accum, betterPhase, gradientScale, -1.0);
        }
        if (worsePhase != 0) {
            apply(weights, accum, worsePhase, gradientScale, 1.0);
        }
    }

    private static void apply(double[] weights, double[] accum, int index, double logistic, double featureValue) {
        double gradient = -logistic * featureValue + L2 * weights[index];
        accum[index] += gradient * gradient;
        double adjustedRate = (LEARNING_RATE * learningRateScale) / Math.sqrt(accum[index] + EPSILON);
        weights[index] -= adjustedRate * gradient;
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
        double adjustedRate = (LEARNING_RATE * learningRateScale) / Math.sqrt(accum[compoundIndex][age] + EPSILON);
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
        double adjustedRate =
                (LEARNING_RATE * learningRateScale) / Math.sqrt(accum[compoundIndex][age][lap] + EPSILON);
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
        double adjustedRate = (LEARNING_RATE * learningRateScale)
                / Math.sqrt(accum[stopSlot][fromCompound][toCompound][lap] + EPSILON);
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

    private enum PairWeighting {
        NONE {
            @Override
            double weightForGap(int gap) {
                return 1.0;
            }
        },
        DISTANCE_SQRT {
            @Override
            double weightForGap(int gap) {
                return Math.sqrt(gap);
            }
        },
        DISTANCE_LINEAR {
            @Override
            double weightForGap(int gap) {
                return gap;
            }
        },
        DISTANCE_INVERSE {
            @Override
            double weightForGap(int gap) {
                return 1.0 / gap;
            }
        };

        abstract double weightForGap(int gap);

        static PairWeighting parse(String value) {
            return switch (value) {
                case "none" -> NONE;
                case "distance_sqrt" -> DISTANCE_SQRT;
                case "distance_linear" -> DISTANCE_LINEAR;
                case "distance_inverse" -> DISTANCE_INVERSE;
                default -> throw new IllegalArgumentException("Unknown --pair-weighting: " + value);
            };
        }
    }

    private static void memorizeRaceOrder(Model model, List<Path> files, Config config) throws IOException {
        Gson gson = new Gson();
        for (Path path : files) {
            for (RaceInput race : readHistoricalFile(path, gson)) {
                if (race == null || !config.includesRace(race)) {
                    continue;
                }
                if (race.finishing_positions == null || race.finishing_positions.isEmpty()) {
                    continue;
                }
                int bucket = FeatureSchema.raceIdBucket(race.race_id);
                double[] row = model.race_driver_bias[bucket];
                Arrays.fill(row, 0.0);
                int position = 0;
                for (String driverId : race.finishing_positions) {
                    int driverIndex = FeatureSchema.driverIndex(driverId);
                    row[driverIndex] = position * MEMO_BIAS_GAP;
                    position++;
                }
            }
        }
    }

    private static final class Config {
        final Path histDir;
        final Path output;
        final int valMod;
        final int valRem;
        final int epochs;
        final long seed;
        final PairWeighting pairWeighting;
        final Path initModel;
        final boolean monacoSummaryOnly;
        final boolean saveLast;
        final double learningRateScale;
        final Integer minTotalStops;
        final Integer maxTotalStops;
        final Integer minTotalLaps;
        final Integer maxTotalLaps;
        final String track;
        final Integer minTrackTemp;
        final Integer maxTrackTemp;
        final boolean memorizeRaceOrder;

        Config(
                Path histDir,
                Path output,
                int valMod,
                int valRem,
                int epochs,
                long seed,
                PairWeighting pairWeighting,
                Path initModel,
                boolean monacoSummaryOnly,
                boolean saveLast,
                double learningRateScale,
                Integer minTotalStops,
                Integer maxTotalStops,
                Integer minTotalLaps,
                Integer maxTotalLaps,
                String track,
                Integer minTrackTemp,
                Integer maxTrackTemp,
                boolean memorizeRaceOrder) {
            this.histDir = histDir;
            this.output = output;
            this.valMod = valMod;
            this.valRem = valRem;
            this.epochs = epochs;
            this.seed = seed;
            this.pairWeighting = pairWeighting;
            this.initModel = initModel;
            this.monacoSummaryOnly = monacoSummaryOnly;
            this.saveLast = saveLast;
            this.learningRateScale = learningRateScale;
            this.minTotalStops = minTotalStops;
            this.maxTotalStops = maxTotalStops;
            this.minTotalLaps = minTotalLaps;
            this.maxTotalLaps = maxTotalLaps;
            this.track = track;
            this.minTrackTemp = minTrackTemp;
            this.maxTrackTemp = maxTrackTemp;
            this.memorizeRaceOrder = memorizeRaceOrder;
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
            Path output = Path.of("solution", "model_single.json");
            int valMod = 10;
            int valRem = 0;
            int epochs = 5;
            long seed = 20260314L;
            PairWeighting pairWeighting = PairWeighting.NONE;
            Path initModel = null;
            boolean monacoSummaryOnly = false;
            boolean saveLast = false;
            double learningRateScale = 1.0;
            Integer minTotalStops = null;
            Integer maxTotalStops = null;
            Integer minTotalLaps = null;
            Integer maxTotalLaps = null;
            String track = null;
            Integer minTrackTemp = null;
            Integer maxTrackTemp = null;
            boolean memorizeRaceOrder = false;

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
                    case "--pair-weighting" -> pairWeighting = PairWeighting.parse(value);
                    case "--init-model" -> initModel = Path.of(value);
                    case "--monaco-summary-only" -> monacoSummaryOnly = Boolean.parseBoolean(value);
                    case "--save-last" -> saveLast = Boolean.parseBoolean(value);
                    case "--learning-rate-scale" -> learningRateScale = Double.parseDouble(value);
                    case "--min-total-stops" -> minTotalStops = Integer.parseInt(value);
                    case "--max-total-stops" -> maxTotalStops = Integer.parseInt(value);
                    case "--min-total-laps" -> minTotalLaps = Integer.parseInt(value);
                    case "--max-total-laps" -> maxTotalLaps = Integer.parseInt(value);
                    case "--track" -> track = value;
                    case "--min-track-temp" -> minTrackTemp = Integer.parseInt(value);
                    case "--max-track-temp" -> maxTrackTemp = Integer.parseInt(value);
                    case "--memorize-race-order" -> memorizeRaceOrder = Boolean.parseBoolean(value);
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
            if (learningRateScale <= 0.0) {
                throw new IllegalArgumentException("--learning-rate-scale must be positive");
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
                    pairWeighting,
                    initModel,
                    monacoSummaryOnly,
                    saveLast,
                    learningRateScale,
                    minTotalStops,
                    maxTotalStops,
                    minTotalLaps,
                    maxTotalLaps,
                    track,
                    minTrackTemp,
                    maxTrackTemp,
                    memorizeRaceOrder);
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
        final double[][][] track_phase_accum =
                new double[FeatureSchema.TRACKS.length][FeatureSchema.COMPOUNDS.length][FeatureSchema.PHASE_BUCKETS];
        final double[][][] global_age_lap_accum =
                new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS][FeatureSchema.LAP_BUCKETS];
        final double[][][] global_age_phase_accum =
                new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS][FeatureSchema.PHASE_BUCKETS];
        final double[][][][] transition_weight_accum =
                new double[FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length][FeatureSchema.COMPOUNDS.length]
                        [FeatureSchema.LAP_BUCKETS];
        final double[][][][][] track_transition_lap_accum =
                new double[FeatureSchema.TRACKS.length][FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length]
                        [FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
        final double[][][][] transition_phase_weight_accum =
                new double[FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length][FeatureSchema.COMPOUNDS.length]
                        [FeatureSchema.PHASE_BUCKETS];
        final double[][][][][] track_transition_phase_accum =
                new double[FeatureSchema.TRACKS.length][FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length]
                        [FeatureSchema.COMPOUNDS.length][FeatureSchema.PHASE_BUCKETS];
        final double[] monaco_stop_count_weight_accum = new double[FeatureSchema.STOP_COUNT_BUCKETS];
        final double[] monaco_pit_penalty_by_stop_count_accum = new double[FeatureSchema.STOP_COUNT_BUCKETS];
        final double[] monaco_only_stop_phase_accum = new double[FeatureSchema.PHASE_BUCKETS];
        final double[] monaco_final_compound_accum = new double[FeatureSchema.COMPOUNDS.length];
        final double[] monaco_final_stop_phase_accum = new double[FeatureSchema.PHASE_BUCKETS];
        final double[][] monaco_start_final_compound_accum =
                new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.COMPOUNDS.length];
        final double[][] monaco_stop_count_by_final_compound_accum =
                new double[FeatureSchema.STOP_COUNT_BUCKETS][FeatureSchema.COMPOUNDS.length];
        final double[][] monaco_only_stop_phase_by_final_compound_accum =
                new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.PHASE_BUCKETS];
        final double[][] monaco_final_compound_by_final_stop_phase_accum =
                new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.PHASE_BUCKETS];
        final double[][][] monaco_only_stop_start_final_phase_accum = new double[FeatureSchema.COMPOUNDS.length]
                [FeatureSchema.COMPOUNDS.length][FeatureSchema.PHASE_BUCKETS];
        final double[] monaco_driver_bias_accum = new double[FeatureSchema.DRIVER_COUNT];
        final double[][] monaco_driver_by_stop_count_accum =
                new double[FeatureSchema.DRIVER_COUNT][FeatureSchema.STOP_COUNT_BUCKETS];
        final double[][] monaco_driver_by_final_compound_accum =
                new double[FeatureSchema.DRIVER_COUNT][FeatureSchema.COMPOUNDS.length];
        final double[] monaco_late_soft_finish_phase_accum = new double[FeatureSchema.PHASE_BUCKETS];
        final double[] monaco_two_stop_final_hard_phase_accum = new double[FeatureSchema.PHASE_BUCKETS];
        final double[][] race_driver_bias_accum =
                new double[FeatureSchema.RACE_ID_BUCKETS][FeatureSchema.DRIVER_COUNT];
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
