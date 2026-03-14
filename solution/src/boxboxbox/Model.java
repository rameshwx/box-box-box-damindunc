package boxboxbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

final class Model {
    int version = FeatureSchema.MODEL_VERSION;
    int max_age = FeatureSchema.MAX_AGE;
    int max_lap = FeatureSchema.MAX_LAP;
    String[] compounds = FeatureSchema.COMPOUNDS.clone();
    String[] tracks = FeatureSchema.TRACKS.clone();

    int temp_min = FeatureSchema.TEMP_MIN;
    int temp_max = FeatureSchema.TEMP_MAX;
    int base_min_tenths = FeatureSchema.BASE_MIN_TENTHS;
    int base_max_tenths = FeatureSchema.BASE_MAX_TENTHS;
    int base_step_tenths = FeatureSchema.BASE_STEP_TENTHS;
    double pit_penalty_multiplier = 1.0;

    double[][] global_age = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][][] temp_age =
            new double[FeatureSchema.TEMP_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][][] base_age =
            new double[FeatureSchema.BASE_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][][] track_age =
            new double[FeatureSchema.TRACKS.length][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][] global_lap = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
    double[][][] temp_lap =
            new double[FeatureSchema.TEMP_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
    double[][][] base_lap =
            new double[FeatureSchema.BASE_BUCKETS][FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
    double[][][] track_lap =
            new double[FeatureSchema.TRACKS.length][FeatureSchema.COMPOUNDS.length][FeatureSchema.LAP_BUCKETS];
    double[][] global_phase = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.PHASE_BUCKETS];
    double[][][] global_age_lap =
            new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS][FeatureSchema.LAP_BUCKETS];
    double[][][] global_age_phase =
            new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS][FeatureSchema.PHASE_BUCKETS];
    double[][][][] transition_weight =
            new double[FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length][FeatureSchema.COMPOUNDS.length]
                    [FeatureSchema.LAP_BUCKETS];
    double[][][][] transition_phase_weight =
            new double[FeatureSchema.STOP_SLOTS][FeatureSchema.COMPOUNDS.length][FeatureSchema.COMPOUNDS.length]
                    [FeatureSchema.PHASE_BUCKETS];

    static Model zero() {
        return new Model();
    }

    static Model load(Path path) throws IOException {
        Gson gson = gson();
        try (Reader reader = Files.newBufferedReader(path)) {
            Model model = gson.fromJson(reader, Model.class);
            if (model == null) {
                throw new IOException("Model file is empty: " + path);
            }
            model.validate();
            return model;
        }
    }

    void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            gson().toJson(this, writer);
        }
    }

    void validate() {
        if (version != FeatureSchema.MODEL_VERSION) {
            throw new IllegalArgumentException("Unsupported model version: " + version);
        }
        if (max_age != FeatureSchema.MAX_AGE) {
            throw new IllegalArgumentException("Unsupported max_age: " + max_age);
        }
        if (max_lap != FeatureSchema.MAX_LAP) {
            throw new IllegalArgumentException("Unsupported max_lap: " + max_lap);
        }
        if (!FeatureSchema.isCompoundSet(compounds)) {
            throw new IllegalArgumentException("Compound set mismatch: " + Arrays.toString(compounds));
        }
        if (!FeatureSchema.isTrackSet(tracks)) {
            throw new IllegalArgumentException("Track set mismatch: " + Arrays.toString(tracks));
        }
        if (temp_min != FeatureSchema.TEMP_MIN || temp_max != FeatureSchema.TEMP_MAX) {
            throw new IllegalArgumentException("Temperature metadata mismatch");
        }
        if (base_min_tenths != FeatureSchema.BASE_MIN_TENTHS
                || base_max_tenths != FeatureSchema.BASE_MAX_TENTHS
                || base_step_tenths != FeatureSchema.BASE_STEP_TENTHS) {
            throw new IllegalArgumentException("Base metadata mismatch");
        }
        requireAgeShape(global_age, "global_age");
        requireAgeCube(temp_age, FeatureSchema.TEMP_BUCKETS, "temp_age");
        requireAgeCube(base_age, FeatureSchema.BASE_BUCKETS, "base_age");
        requireAgeCube(track_age, FeatureSchema.TRACKS.length, "track_age");
        requireLapShape(global_lap, "global_lap");
        requireLapCube(temp_lap, FeatureSchema.TEMP_BUCKETS, "temp_lap");
        requireLapCube(base_lap, FeatureSchema.BASE_BUCKETS, "base_lap");
        requireLapCube(track_lap, FeatureSchema.TRACKS.length, "track_lap");
        requirePhaseShape(global_phase, "global_phase");
        requireAgeLapShape(global_age_lap, "global_age_lap");
        requireAgePhaseShape(global_age_phase, "global_age_phase");
        requireTransitionShape(transition_weight, "transition_weight");
        requireTransitionPhaseShape(transition_phase_weight, "transition_phase_weight");
    }

    Model deepCopy() {
        Model copy = new Model();
        copy.version = version;
        copy.max_age = max_age;
        copy.max_lap = max_lap;
        copy.compounds = compounds.clone();
        copy.tracks = tracks.clone();
        copy.temp_min = temp_min;
        copy.temp_max = temp_max;
        copy.base_min_tenths = base_min_tenths;
        copy.base_max_tenths = base_max_tenths;
        copy.base_step_tenths = base_step_tenths;
        copy.pit_penalty_multiplier = pit_penalty_multiplier;
        copy.global_age = deepCopy(global_age);
        copy.temp_age = deepCopy(temp_age);
        copy.base_age = deepCopy(base_age);
        copy.track_age = deepCopy(track_age);
        copy.global_lap = deepCopy(global_lap);
        copy.temp_lap = deepCopy(temp_lap);
        copy.base_lap = deepCopy(base_lap);
        copy.track_lap = deepCopy(track_lap);
        copy.global_phase = deepCopy(global_phase);
        copy.global_age_lap = deepCopy(global_age_lap);
        copy.global_age_phase = deepCopy(global_age_phase);
        copy.transition_weight = deepCopy(transition_weight);
        copy.transition_phase_weight = deepCopy(transition_phase_weight);
        return copy;
    }

    private static void requireAgeShape(double[][] values, String name) {
        if (values == null || values.length != FeatureSchema.COMPOUNDS.length) {
            throw new IllegalArgumentException(name + " has invalid compound dimension");
        }
        for (double[] ages : values) {
            if (ages == null || ages.length != FeatureSchema.AGE_BUCKETS) {
                throw new IllegalArgumentException(name + " has invalid age dimension");
            }
        }
    }

    private static void requireLapShape(double[][] values, String name) {
        if (values == null || values.length != FeatureSchema.COMPOUNDS.length) {
            throw new IllegalArgumentException(name + " has invalid compound dimension");
        }
        for (double[] laps : values) {
            if (laps == null || laps.length != FeatureSchema.LAP_BUCKETS) {
                throw new IllegalArgumentException(name + " has invalid lap dimension");
            }
        }
    }

    private static void requireAgeCube(double[][][] values, int outerLength, String name) {
        if (values == null || values.length != outerLength) {
            throw new IllegalArgumentException(name + " has invalid outer dimension");
        }
        for (int index = 0; index < values.length; index++) {
            requireAgeShape(values[index], name + "[" + index + "]");
        }
    }

    private static void requireLapCube(double[][][] values, int outerLength, String name) {
        if (values == null || values.length != outerLength) {
            throw new IllegalArgumentException(name + " has invalid outer dimension");
        }
        for (int index = 0; index < values.length; index++) {
            requireLapShape(values[index], name + "[" + index + "]");
        }
    }

    private static void requirePhaseShape(double[][] values, String name) {
        if (values == null || values.length != FeatureSchema.COMPOUNDS.length) {
            throw new IllegalArgumentException(name + " has invalid compound dimension");
        }
        for (double[] phases : values) {
            if (phases == null || phases.length != FeatureSchema.PHASE_BUCKETS) {
                throw new IllegalArgumentException(name + " has invalid phase dimension");
            }
        }
    }

    private static void requireAgeLapShape(double[][][] values, String name) {
        if (values == null || values.length != FeatureSchema.COMPOUNDS.length) {
            throw new IllegalArgumentException(name + " has invalid compound dimension");
        }
        for (double[][] ages : values) {
            if (ages == null || ages.length != FeatureSchema.AGE_BUCKETS) {
                throw new IllegalArgumentException(name + " has invalid age dimension");
            }
            for (double[] laps : ages) {
                if (laps == null || laps.length != FeatureSchema.LAP_BUCKETS) {
                    throw new IllegalArgumentException(name + " has invalid lap dimension");
                }
            }
        }
    }

    private static void requireAgePhaseShape(double[][][] values, String name) {
        if (values == null || values.length != FeatureSchema.COMPOUNDS.length) {
            throw new IllegalArgumentException(name + " has invalid compound dimension");
        }
        for (double[][] ages : values) {
            if (ages == null || ages.length != FeatureSchema.AGE_BUCKETS) {
                throw new IllegalArgumentException(name + " has invalid age dimension");
            }
            for (double[] phases : ages) {
                if (phases == null || phases.length != FeatureSchema.PHASE_BUCKETS) {
                    throw new IllegalArgumentException(name + " has invalid phase dimension");
                }
            }
        }
    }

    private static void requireTransitionShape(double[][][][] values, String name) {
        if (values == null || values.length != FeatureSchema.STOP_SLOTS) {
            throw new IllegalArgumentException(name + " has invalid stop-slot dimension");
        }
        for (double[][][] stopSlot : values) {
            if (stopSlot == null || stopSlot.length != FeatureSchema.COMPOUNDS.length) {
                throw new IllegalArgumentException(name + " has invalid from-compound dimension");
            }
            for (double[][] fromCompound : stopSlot) {
                if (fromCompound == null || fromCompound.length != FeatureSchema.COMPOUNDS.length) {
                    throw new IllegalArgumentException(name + " has invalid to-compound dimension");
                }
                for (double[] laps : fromCompound) {
                    if (laps == null || laps.length != FeatureSchema.LAP_BUCKETS) {
                        throw new IllegalArgumentException(name + " has invalid lap dimension");
                    }
                }
            }
        }
    }

    private static void requireTransitionPhaseShape(double[][][][] values, String name) {
        if (values == null || values.length != FeatureSchema.STOP_SLOTS) {
            throw new IllegalArgumentException(name + " has invalid stop-slot dimension");
        }
        for (double[][][] stopSlot : values) {
            if (stopSlot == null || stopSlot.length != FeatureSchema.COMPOUNDS.length) {
                throw new IllegalArgumentException(name + " has invalid from-compound dimension");
            }
            for (double[][] fromCompound : stopSlot) {
                if (fromCompound == null || fromCompound.length != FeatureSchema.COMPOUNDS.length) {
                    throw new IllegalArgumentException(name + " has invalid to-compound dimension");
                }
                for (double[] phases : fromCompound) {
                    if (phases == null || phases.length != FeatureSchema.PHASE_BUCKETS) {
                        throw new IllegalArgumentException(name + " has invalid phase dimension");
                    }
                }
            }
        }
    }

    private static double[][] deepCopy(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index].clone();
        }
        return copy;
    }

    private static double[][][] deepCopy(double[][][] source) {
        double[][][] copy = new double[source.length][][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = deepCopy(source[index]);
        }
        return copy;
    }

    private static double[][][][] deepCopy(double[][][][] source) {
        double[][][][] copy = new double[source.length][][][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = deepCopy(source[index]);
        }
        return copy;
    }

    private static Gson gson() {
        return new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    }
}
