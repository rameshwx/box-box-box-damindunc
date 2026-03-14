package boxboxbox;

import java.util.Arrays;

final class FeatureSchema {
    static final int MODEL_VERSION = 2;
    static final int MAX_AGE = 70;
    static final int AGE_BUCKETS = MAX_AGE + 1;
    static final int MAX_LAP = 70;
    static final int LAP_BUCKETS = MAX_LAP + 1;
    static final int MAX_PHASE = 70;
    static final int PHASE_BUCKETS = MAX_PHASE + 1;
    static final int TEMP_MIN = 18;
    static final int TEMP_MAX = 42;
    static final int TEMP_BUCKETS = TEMP_MAX - TEMP_MIN + 1;
    static final int BASE_MIN_TENTHS = 800;
    static final int BASE_MAX_TENTHS = 950;
    static final int BASE_STEP_TENTHS = 1;
    static final int BASE_BUCKETS = BASE_MAX_TENTHS - BASE_MIN_TENTHS + 1;
    static final int STOP_SLOTS = 2;

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

    static int ageFlatIndex(int compoundIndex, int age) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        if (age < 1 || age > MAX_AGE) {
            throw new IllegalArgumentException("Invalid tire age: " + age);
        }
        return compoundIndex * AGE_BUCKETS + age;
    }

    static int lapFlatIndex(int compoundIndex, int lap) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        if (lap < 1 || lap > MAX_LAP) {
            throw new IllegalArgumentException("Invalid lap: " + lap);
        }
        return compoundIndex * LAP_BUCKETS + lap;
    }

    static int ageLapFlatIndex(int compoundIndex, int age, int lap) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        if (age < 1 || age > MAX_AGE) {
            throw new IllegalArgumentException("Invalid tire age: " + age);
        }
        if (lap < 1 || lap > MAX_LAP) {
            throw new IllegalArgumentException("Invalid lap: " + lap);
        }
        return compoundIndex * AGE_BUCKETS * LAP_BUCKETS + age * LAP_BUCKETS + lap;
    }

    static int phaseFlatIndex(int compoundIndex, int phase) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        if (phase < 1 || phase > MAX_PHASE) {
            throw new IllegalArgumentException("Invalid phase: " + phase);
        }
        return compoundIndex * PHASE_BUCKETS + phase;
    }

    static int agePhaseFlatIndex(int compoundIndex, int age, int phase) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        if (age < 1 || age > MAX_AGE) {
            throw new IllegalArgumentException("Invalid tire age: " + age);
        }
        if (phase < 1 || phase > MAX_PHASE) {
            throw new IllegalArgumentException("Invalid phase: " + phase);
        }
        return compoundIndex * AGE_BUCKETS * PHASE_BUCKETS + age * PHASE_BUCKETS + phase;
    }

    static int transitionFlatIndex(int stopSlot, int fromCompoundIndex, int toCompoundIndex, int lap) {
        if (stopSlot < 0 || stopSlot >= STOP_SLOTS) {
            throw new IllegalArgumentException("Invalid stop slot: " + stopSlot);
        }
        if (fromCompoundIndex < 0 || fromCompoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid from compound index: " + fromCompoundIndex);
        }
        if (toCompoundIndex < 0 || toCompoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid to compound index: " + toCompoundIndex);
        }
        if (lap < 1 || lap > MAX_LAP) {
            throw new IllegalArgumentException("Invalid transition lap: " + lap);
        }
        return stopSlot * COMPOUNDS.length * COMPOUNDS.length * LAP_BUCKETS
                + fromCompoundIndex * COMPOUNDS.length * LAP_BUCKETS
                + toCompoundIndex * LAP_BUCKETS
                + lap;
    }

    static int transitionPhaseFlatIndex(int stopSlot, int fromCompoundIndex, int toCompoundIndex, int phase) {
        if (stopSlot < 0 || stopSlot >= STOP_SLOTS) {
            throw new IllegalArgumentException("Invalid stop slot: " + stopSlot);
        }
        if (fromCompoundIndex < 0 || fromCompoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid from compound index: " + fromCompoundIndex);
        }
        if (toCompoundIndex < 0 || toCompoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid to compound index: " + toCompoundIndex);
        }
        if (phase < 1 || phase > MAX_PHASE) {
            throw new IllegalArgumentException("Invalid transition phase: " + phase);
        }
        return stopSlot * COMPOUNDS.length * COMPOUNDS.length * PHASE_BUCKETS
                + fromCompoundIndex * COMPOUNDS.length * PHASE_BUCKETS
                + toCompoundIndex * PHASE_BUCKETS
                + phase;
    }

    static int compoundFromAgeFlat(int flatIndex) {
        return flatIndex / AGE_BUCKETS;
    }

    static int ageFromFlat(int flatIndex) {
        return flatIndex % AGE_BUCKETS;
    }

    static int compoundFromLapFlat(int flatIndex) {
        return flatIndex / LAP_BUCKETS;
    }

    static int lapFromFlat(int flatIndex) {
        return flatIndex % LAP_BUCKETS;
    }

    static int compoundFromAgeLapFlat(int flatIndex) {
        return flatIndex / (AGE_BUCKETS * LAP_BUCKETS);
    }

    static int ageFromAgeLapFlat(int flatIndex) {
        return (flatIndex / LAP_BUCKETS) % AGE_BUCKETS;
    }

    static int lapFromAgeLapFlat(int flatIndex) {
        return flatIndex % LAP_BUCKETS;
    }

    static int compoundFromPhaseFlat(int flatIndex) {
        return flatIndex / PHASE_BUCKETS;
    }

    static int phaseFromFlat(int flatIndex) {
        return flatIndex % PHASE_BUCKETS;
    }

    static int compoundFromAgePhaseFlat(int flatIndex) {
        return flatIndex / (AGE_BUCKETS * PHASE_BUCKETS);
    }

    static int ageFromAgePhaseFlat(int flatIndex) {
        return (flatIndex / PHASE_BUCKETS) % AGE_BUCKETS;
    }

    static int phaseFromAgePhaseFlat(int flatIndex) {
        return flatIndex % PHASE_BUCKETS;
    }

    static int stopSlotFromTransitionFlat(int flatIndex) {
        return flatIndex / (COMPOUNDS.length * COMPOUNDS.length * LAP_BUCKETS);
    }

    static int fromCompoundFromTransitionFlat(int flatIndex) {
        return (flatIndex / (COMPOUNDS.length * LAP_BUCKETS)) % COMPOUNDS.length;
    }

    static int toCompoundFromTransitionFlat(int flatIndex) {
        return (flatIndex / LAP_BUCKETS) % COMPOUNDS.length;
    }

    static int lapFromTransitionFlat(int flatIndex) {
        return flatIndex % LAP_BUCKETS;
    }

    static int phaseFromTransitionPhaseFlat(int flatIndex) {
        return flatIndex % PHASE_BUCKETS;
    }

    static int stopSlotFromTransitionPhaseFlat(int flatIndex) {
        return flatIndex / (COMPOUNDS.length * COMPOUNDS.length * PHASE_BUCKETS);
    }

    static int fromCompoundFromTransitionPhaseFlat(int flatIndex) {
        return (flatIndex / (COMPOUNDS.length * PHASE_BUCKETS)) % COMPOUNDS.length;
    }

    static int toCompoundFromTransitionPhaseFlat(int flatIndex) {
        return (flatIndex / PHASE_BUCKETS) % COMPOUNDS.length;
    }

    static int tempBucket(int trackTemp) {
        if (trackTemp < TEMP_MIN || trackTemp > TEMP_MAX) {
            throw new IllegalArgumentException("track_temp out of range: " + trackTemp);
        }
        return trackTemp - TEMP_MIN;
    }

    static int baseBucket(double baseLapTime) {
        long tenths = Math.round(baseLapTime * 10.0);
        if (tenths < BASE_MIN_TENTHS || tenths > BASE_MAX_TENTHS) {
            throw new IllegalArgumentException("base_lap_time out of range: " + baseLapTime);
        }
        return (int) (tenths - BASE_MIN_TENTHS);
    }

    static int phaseBucket(int lap, int totalLaps) {
        if (lap < 1 || lap > MAX_LAP) {
            throw new IllegalArgumentException("Invalid lap for phase bucket: " + lap);
        }
        if (totalLaps < 1 || totalLaps > MAX_LAP) {
            throw new IllegalArgumentException("Invalid total laps for phase bucket: " + totalLaps);
        }
        if (totalLaps == 1) {
            return 1;
        }
        double scaled = 1.0 + ((double) (lap - 1) * (MAX_PHASE - 1)) / (double) (totalLaps - 1);
        int bucket = (int) Math.round(scaled);
        if (bucket < 1) {
            return 1;
        }
        if (bucket > MAX_PHASE) {
            return MAX_PHASE;
        }
        return bucket;
    }

    static boolean isTrackSet(String[] tracks) {
        return Arrays.equals(TRACKS, tracks);
    }

    static boolean isCompoundSet(String[] compounds) {
        return Arrays.equals(COMPOUNDS, compounds);
    }
}
