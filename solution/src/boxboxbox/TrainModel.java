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
            }
        }

        bestModel.save(config.output);
        boolean improved = bestMetrics.exactAccuracy() > baseline.exactAccuracy()
                || (bestMetrics.exactAccuracy() == baseline.exactAccuracy()
                        && bestMetrics.pairwiseAccuracy() > baseline.pairwiseAccuracy());

        System.out.printf(
                "Saved model to %s%nBest validation: exact=%d/%d (%.4f) pairwise=%d/%d (%.4f)%n",
                config.output,
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
                if (!config.isValidationRace(race.race_id)) {
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
                if (!config.isValidationRace(race.race_id)) {
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

        int betterCursor = 0;
        int worseCursor = 0;
        int trackIndex = better.trackIndex;
        double tempNorm = better.tempNorm;
        double tempNormSq = better.tempNormSq;
        double baseNorm = better.baseNorm;

        while (betterCursor < better.activeFlatIndices.length || worseCursor < worse.activeFlatIndices.length) {
            int betterFlat = betterCursor < better.activeFlatIndices.length ? better.activeFlatIndices[betterCursor] : Integer.MAX_VALUE;
            int worseFlat = worseCursor < worse.activeFlatIndices.length ? worse.activeFlatIndices[worseCursor] : Integer.MAX_VALUE;

            int flatIndex = Math.min(betterFlat, worseFlat);
            int diffCount = 0;
            if (flatIndex == betterFlat) {
                diffCount -= better.activeCounts[betterCursor];
                betterCursor++;
            }
            if (flatIndex == worseFlat) {
                diffCount += worse.activeCounts[worseCursor];
                worseCursor++;
            }
            if (diffCount == 0) {
                continue;
            }

            int compoundIndex = FeatureSchema.compoundFromFlat(flatIndex);
            int age = FeatureSchema.ageFromFlat(flatIndex);

            apply(model.global_age, state.global_age_accum, compoundIndex, age, logistic, diffCount);
            apply(model.global_age_temp, state.global_age_temp_accum, compoundIndex, age, logistic, diffCount * tempNorm);
            apply(
                    model.global_age_temp_sq,
                    state.global_age_temp_sq_accum,
                    compoundIndex,
                    age,
                    logistic,
                    diffCount * tempNormSq);
            apply(model.global_age_base, state.global_age_base_accum, compoundIndex, age, logistic, diffCount * baseNorm);
            apply(
                    model.track_age[trackIndex],
                    state.track_age_accum[trackIndex],
                    compoundIndex,
                    age,
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

    private static final class Config {
        final Path histDir;
        final Path output;
        final int valMod;
        final int valRem;
        final int epochs;
        final long seed;

        Config(Path histDir, Path output, int valMod, int valRem, int epochs, long seed) {
            this.histDir = histDir;
            this.output = output;
            this.valMod = valMod;
            this.valRem = valRem;
            this.epochs = epochs;
            this.seed = seed;
        }

        boolean isValidationRace(String raceId) {
            return Math.floorMod(raceId.hashCode(), valMod) == valRem;
        }

        static Config parse(String[] args) {
            Path histDir = Path.of("data", "historical_races");
            Path output = Path.of("solution", "model.json");
            int valMod = 10;
            int valRem = 0;
            int epochs = 5;
            long seed = 20260314L;

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
            return new Config(histDir, output, valMod, valRem, epochs, seed);
        }
    }

    private static final class OptimizerState {
        final double[][] global_age_accum = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][] global_age_temp_accum = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][] global_age_temp_sq_accum = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][] global_age_base_accum = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
        final double[][][] track_age_accum =
                new double[FeatureSchema.TRACKS.length][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
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
