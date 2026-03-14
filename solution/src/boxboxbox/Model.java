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
    String[] compounds = FeatureSchema.COMPOUNDS.clone();
    String[] tracks = FeatureSchema.TRACKS.clone();

    double temp_center = FeatureSchema.TEMP_CENTER;
    double temp_scale = FeatureSchema.TEMP_SCALE;
    double base_center = FeatureSchema.BASE_CENTER;
    double base_scale = FeatureSchema.BASE_SCALE;
    double pit_penalty_multiplier = 1.0;

    double[][] global_age = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][] global_age_temp = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][] global_age_temp_sq = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][] global_age_base = new double[FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];
    double[][][] track_age =
            new double[FeatureSchema.TRACKS.length][FeatureSchema.COMPOUNDS.length][FeatureSchema.AGE_BUCKETS];

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
        if (!FeatureSchema.isCompoundSet(compounds)) {
            throw new IllegalArgumentException("Compound set mismatch: " + Arrays.toString(compounds));
        }
        if (!FeatureSchema.isTrackSet(tracks)) {
            throw new IllegalArgumentException("Track set mismatch: " + Arrays.toString(tracks));
        }
        requireShape(global_age, "global_age");
        requireShape(global_age_temp, "global_age_temp");
        requireShape(global_age_temp_sq, "global_age_temp_sq");
        requireShape(global_age_base, "global_age_base");
        if (track_age == null || track_age.length != FeatureSchema.TRACKS.length) {
            throw new IllegalArgumentException("track_age has invalid outer shape");
        }
        for (int trackIndex = 0; trackIndex < track_age.length; trackIndex++) {
            requireShape(track_age[trackIndex], "track_age[" + trackIndex + "]");
        }
    }

    Model deepCopy() {
        Model copy = new Model();
        copy.version = version;
        copy.max_age = max_age;
        copy.compounds = compounds.clone();
        copy.tracks = tracks.clone();
        copy.temp_center = temp_center;
        copy.temp_scale = temp_scale;
        copy.base_center = base_center;
        copy.base_scale = base_scale;
        copy.pit_penalty_multiplier = pit_penalty_multiplier;
        copy.global_age = deepCopy(global_age);
        copy.global_age_temp = deepCopy(global_age_temp);
        copy.global_age_temp_sq = deepCopy(global_age_temp_sq);
        copy.global_age_base = deepCopy(global_age_base);
        copy.track_age = deepCopy(track_age);
        return copy;
    }

    private static void requireShape(double[][] values, String name) {
        if (values == null || values.length != FeatureSchema.COMPOUNDS.length) {
            throw new IllegalArgumentException(name + " has invalid compound dimension");
        }
        for (double[] ages : values) {
            if (ages == null || ages.length != FeatureSchema.AGE_BUCKETS) {
                throw new IllegalArgumentException(name + " has invalid age dimension");
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

    private static Gson gson() {
        return new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    }
}
