package boxboxbox;

import java.util.Arrays;

final class FeatureSchema {
    static final int MODEL_VERSION = 1;
    static final int MAX_AGE = 70;
    static final int AGE_BUCKETS = MAX_AGE + 1;

    static final String[] COMPOUNDS = {"SOFT", "MEDIUM", "HARD"};
    static final String[] TRACKS = {
        "Bahrain",
        "COTA",
        "Monaco",
        "Monza",
        "Silverstone",
        "Spa",
        "Suzuka"
    };

    static final double TEMP_CENTER = 30.0;
    static final double TEMP_SCALE = 10.0;
    static final double BASE_CENTER = 87.5;
    static final double BASE_SCALE = 7.5;

    private FeatureSchema() {
    }

    static int compoundIndex(String name) {
        for (int index = 0; index < COMPOUNDS.length; index++) {
            if (COMPOUNDS[index].equals(name)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unknown compound: " + name);
    }

    static int trackIndex(String track) {
        for (int index = 0; index < TRACKS.length; index++) {
            if (TRACKS[index].equals(track)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unknown track: " + track);
    }

    static int flatIndex(int compoundIndex, int age) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        if (age < 1 || age > MAX_AGE) {
            throw new IllegalArgumentException("Invalid tire age: " + age);
        }
        return compoundIndex * AGE_BUCKETS + age;
    }

    static int compoundFromFlat(int flatIndex) {
        return flatIndex / AGE_BUCKETS;
    }

    static int ageFromFlat(int flatIndex) {
        return flatIndex % AGE_BUCKETS;
    }

    static double normalizeTemp(int trackTemp) {
        return (trackTemp - TEMP_CENTER) / TEMP_SCALE;
    }

    static double normalizeBase(double baseLapTime) {
        return (baseLapTime - BASE_CENTER) / BASE_SCALE;
    }

    static boolean isTrackSet(String[] tracks) {
        return Arrays.equals(TRACKS, tracks);
    }

    static boolean isCompoundSet(String[] compounds) {
        return Arrays.equals(COMPOUNDS, compounds);
    }
}
