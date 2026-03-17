package boxboxbox;

import java.util.Arrays;

final class FeatureSchema {
    static final int MODEL_VERSION = 7;
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
    static final int STOP_COUNT_BUCKETS = STOP_SLOTS + 1;
    static final int DRIVER_COUNT = 20;
    static final int RACE_ID_BUCKETS = 512;

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
        return -1;
    }

    static int driverIndex(String driverId) {
        if (driverId == null || driverId.length() != 4 || driverId.charAt(0) != 'D') {
            throw new IllegalArgumentException("Unknown driver id: " + driverId);
        }
        int parsed = Integer.parseInt(driverId.substring(1));
        if (parsed < 1 || parsed > DRIVER_COUNT) {
            throw new IllegalArgumentException("Unknown driver id: " + driverId);
        }
        return parsed - 1;
    }

    static int raceIdBucket(String raceId) {
        if (raceId == null || raceId.isBlank()) {
            throw new IllegalArgumentException("race_id is required");
        }
        return Math.floorMod(raceId.hashCode(), RACE_ID_BUCKETS);
    }

    static int ageFlatIndex(int compoundIndex, int age) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        int boundedAge = clamp(age, 1, MAX_AGE);
        return compoundIndex * AGE_BUCKETS + boundedAge;
    }

    static int lapFlatIndex(int compoundIndex, int lap) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        int boundedLap = clamp(lap, 1, MAX_LAP);
        return compoundIndex * LAP_BUCKETS + boundedLap;
    }

    static int ageLapFlatIndex(int compoundIndex, int age, int lap) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        int boundedAge = clamp(age, 1, MAX_AGE);
        int boundedLap = clamp(lap, 1, MAX_LAP);
        return compoundIndex * AGE_BUCKETS * LAP_BUCKETS + boundedAge * LAP_BUCKETS + boundedLap;
    }

    static int phaseFlatIndex(int compoundIndex, int phase) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        int boundedPhase = clamp(phase, 1, MAX_PHASE);
        return compoundIndex * PHASE_BUCKETS + boundedPhase;
    }

    static int agePhaseFlatIndex(int compoundIndex, int age, int phase) {
        if (compoundIndex < 0 || compoundIndex >= COMPOUNDS.length) {
            throw new IllegalArgumentException("Invalid compound index: " + compoundIndex);
        }
        int boundedAge = clamp(age, 1, MAX_AGE);
        int boundedPhase = clamp(phase, 1, MAX_PHASE);
        return compoundIndex * AGE_BUCKETS * PHASE_BUCKETS + boundedAge * PHASE_BUCKETS + boundedPhase;
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
        int boundedLap = clamp(lap, 1, MAX_LAP);
        return stopSlot * COMPOUNDS.length * COMPOUNDS.length * LAP_BUCKETS
                + fromCompoundIndex * COMPOUNDS.length * LAP_BUCKETS
                + toCompoundIndex * LAP_BUCKETS
                + boundedLap;
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
        int boundedPhase = clamp(phase, 1, MAX_PHASE);
        return stopSlot * COMPOUNDS.length * COMPOUNDS.length * PHASE_BUCKETS
                + fromCompoundIndex * COMPOUNDS.length * PHASE_BUCKETS
                + toCompoundIndex * PHASE_BUCKETS
                + boundedPhase;
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
            return -1;
        }
        return trackTemp - TEMP_MIN;
    }

    static int baseBucket(double baseLapTime) {
        long tenths = Math.round(baseLapTime * 10.0);
        if (tenths < BASE_MIN_TENTHS || tenths > BASE_MAX_TENTHS) {
            return -1;
        }
        return (int) (tenths - BASE_MIN_TENTHS);
    }

    static int phaseBucket(int lap, int totalLaps) {
        if (totalLaps < 1) {
            throw new IllegalArgumentException("Invalid total laps for phase bucket: " + totalLaps);
        }
        if (totalLaps == 1) {
            return 1;
        }
        int boundedLap = clamp(lap, 1, totalLaps);
        double scaled = 1.0 + ((double) (boundedLap - 1) * (MAX_PHASE - 1)) / (double) (totalLaps - 1);
        int bucket = (int) Math.round(scaled);
        return clamp(bucket, 1, MAX_PHASE);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    static boolean isTrackSet(String[] tracks) {
        return Arrays.equals(TRACKS, tracks);
    }

    static boolean isCompoundSet(String[] compounds) {
        return Arrays.equals(COMPOUNDS, compounds);
    }
}
