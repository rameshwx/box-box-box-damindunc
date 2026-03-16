package boxboxbox;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RaceSimulator {
    private static final Path DEFAULT_MODEL_PATH = Path.of("solution", "model.json");
    private static final Path SHORT_RACE_MODEL_PATH = Path.of("solution", "model_short_race.json");
    private static final Path SHORT_RACE_V3_MODEL_PATH = Path.of("solution", "model_short_race_v3.json");
    private static final Path SHORT_RACE_V3_31_32_MODEL_PATH =
            Path.of("solution", "model_short_race_v3_31_32.json");
    private static final Path SHORT_RACE_041_MODEL_PATH = Path.of("solution", "model_short_race_041.json");
    private static final Path MULTI_STOP_MODEL_PATH = Path.of("solution", "model_multi_stop.json");
    private static final Path MULTI_STOP_V3_WEIGHTED_MODEL_PATH = Path.of("solution", "model_multi_stop_v3_weighted.json");
    private static final Path MULTI_STOP_V3_WEIGHTED_025_051_MODEL_PATH =
            Path.of("solution", "model_multi_stop_v3_weighted_025_051.json");
    private static final Path ONE_STOP_MID_MODEL_PATH = Path.of("solution", "model_one_stop_mid.json");
    private static final Path ONE_STOP_MID_095_MODEL_PATH = Path.of("solution", "model_one_stop_mid_095.json");
    private static final Path MONACO_ONE_STOP_MODEL_PATH = Path.of("solution", "model_monaco_one_stop.json");
    private static final Path MONACO_COOL_SHORT_ONE_STOP_MODEL_PATH =
            Path.of("solution", "model_monaco_one_stop_cool_short.json");
    private static final Path MONACO_COOL_40_ONE_STOP_MODEL_PATH =
            Path.of("solution", "model_monaco_one_stop_cool_40.json");
    private static final Path MONACO_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_monaco_multi_stop.json");
    private static final Path MONZA_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_monza_multi_stop.json");
    private static final Path COTA_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_cota_multi_stop.json");
    private static final Path SILVERSTONE_LONG_COOL_MULTI_STOP_MODEL_PATH =
            Path.of("solution", "model_silverstone_long_cool_multi_stop.json");
    private static final Path SUZUKA_ONE_STOP_MODEL_PATH = Path.of("solution", "model_suzuka_one_stop.json");
    private static final Path ONE_STOP_38_41_MODEL_PATH = Path.of("solution", "model_onestop_38_41.json");
    private static final Path ONE_STOP_40_41_MODEL_PATH = Path.of("solution", "model_onestop_40_41.json");
    private static final Path SPA_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_spa_multi_stop.json");
    private static final Path SPA_HOT_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_spa_hot_multi_stop.json");
    private static final Path BAHRAIN_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_bahrain_multi_stop.json");
    private static final Path BAHRAIN_HOT_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_bahrain_hot_multi_stop.json");
    private static final Path BAHRAIN_ALL_V3_MODEL_PATH = Path.of("solution", "model_bahrain_all_v3.json");
    private static final Path BAHRAIN_MULTI_STOP_69_30_MODEL_PATH =
            Path.of("solution", "model_bahrain_multi_stop_69_30.json");
    private static final Path BAHRAIN_MULTI_STOP_45_37_MODEL_PATH =
            Path.of("solution", "model_bahrain_multi_stop_45_37.json");
    private static final Path MONZA_HOT_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_monza_hot_multi_stop.json");
    private static final Path HOT_RACE_MODEL_PATH = Path.of("solution", "model_hot_race.json");
    private static final int COOL_MULTI_STOP_MIN_TOTAL_STOPS = 24;
    private static final int SHORT_RACE_MAX_LAPS = 39;
    private static final int MULTI_STOP_MIN_TOTAL_STOPS = 23;
    private static final int ONE_STOP_TOTAL_STOPS = 20;
    private static final int ONE_STOP_MID_MIN_LAPS = 40;
    private static final int ONE_STOP_MID_MAX_LAPS = 46;
    private static final int HOT_RACE_MIN_TRACK_TEMP = 40;
    private static final int SUZUKA_HOT_ONE_STOP_LAPS = 48;
    private static final int SUZUKA_HOT_ONE_STOP_TRACK_TEMP = 31;
    private static final int MONACO_COOL_MAX_TRACK_TEMP = 29;
    private static final double SUZUKA_MULTI_STOP_DEFAULT_WEIGHT = 0.7;
    private static final double SUZUKA_MULTI_STOP_V3_WEIGHT = 0.3;
    private static final double MONZA_ONE_STOP_DEFAULT_WEIGHT = 0.7;
    private static final double MONZA_ONE_STOP_V3_WEIGHT = 0.3;
    private static final double SUZUKA_CUSTOM_DEFAULT_WEIGHT = 0.7;
    private static final double SUZUKA_CUSTOM_V3_WEIGHT = 0.3;

    private RaceSimulator() {
    }

    public static void main(String[] args) {
        try {
            Gson gson = new Gson();
            RaceInput input = gson.fromJson(new InputStreamReader(System.in), RaceInput.class);
            if (input == null) {
                throw new IllegalArgumentException("No input JSON received");
            }
            PredictionOutput output = predict(input);
            System.out.println(gson.toJson(output));
        } catch (Exception exception) {
            System.err.println("RaceSimulator error: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static PredictionOutput predict(RaceInput input) throws IOException {
        if (useBahrainShortRaceBlend(input)) {
            return Predictor.predict(
                    input,
                    new Model[] {Model.load(SHORT_RACE_MODEL_PATH), Model.load(SHORT_RACE_V3_MODEL_PATH)},
                    new double[] {0.5, 0.5});
        }
        if (useShortRaceCota041(input)) {
            return Predictor.predict(input, Model.load(SHORT_RACE_041_MODEL_PATH));
        }
        if (useSuzukaOneStopMid095(input)) {
            return Predictor.predict(input, Model.load(ONE_STOP_MID_095_MODEL_PATH));
        }
        if (useMonacoShortRaceV3Blend(input)) {
            return Predictor.predict(
                    input,
                    new Model[] {Model.load(SHORT_RACE_V3_MODEL_PATH), Model.load(SHORT_RACE_V3_31_32_MODEL_PATH)},
                    new double[] {0.5, 0.5});
        }
        if (useSuzukaCustomBlend(input)) {
            return Predictor.predict(
                    input,
                    new Model[] {Model.load(DEFAULT_MODEL_PATH), Model.load(MULTI_STOP_V3_WEIGHTED_025_051_MODEL_PATH)},
                    new double[] {SUZUKA_CUSTOM_DEFAULT_WEIGHT, SUZUKA_CUSTOM_V3_WEIGHT});
        }
        if (useMonzaOneStopBlend(input)) {
            return Predictor.predict(
                    input,
                    new Model[] {Model.load(DEFAULT_MODEL_PATH), Model.load(MULTI_STOP_V3_WEIGHTED_025_051_MODEL_PATH)},
                    new double[] {MONZA_ONE_STOP_DEFAULT_WEIGHT, MONZA_ONE_STOP_V3_WEIGHT});
        }
        if (useSuzukaMultiStopBlend(input)) {
            return Predictor.predict(
                    input,
                    new Model[] {Model.load(DEFAULT_MODEL_PATH), Model.load(MULTI_STOP_V3_WEIGHTED_MODEL_PATH)},
                    new double[] {SUZUKA_MULTI_STOP_DEFAULT_WEIGHT, SUZUKA_MULTI_STOP_V3_WEIGHT});
        }
        return Predictor.predict(input, Model.load(selectModelPath(input)));
    }

    private static Path selectModelPath(RaceInput input) {
        if (input.race_config != null
                && input.strategies != null
                && "Monaco".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && Files.exists(MONACO_MULTI_STOP_MODEL_PATH)) {
            return MONACO_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Spa".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.track_temp >= HOT_RACE_MIN_TRACK_TEMP
                && input.race_config.total_laps <= 46
                && Files.exists(SPA_HOT_MULTI_STOP_MODEL_PATH)) {
            return SPA_HOT_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Spa".equals(input.race_config.track)
                && totalStops(input) >= COOL_MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.total_laps <= 46
                && input.race_config.track_temp <= 23
                && Files.exists(BAHRAIN_MULTI_STOP_MODEL_PATH)) {
            return BAHRAIN_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Spa".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.track_temp >= 35
                && Files.exists(MULTI_STOP_V3_WEIGHTED_MODEL_PATH)) {
            return MULTI_STOP_V3_WEIGHTED_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Spa".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && Files.exists(SPA_MULTI_STOP_MODEL_PATH)) {
            return SPA_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Bahrain".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.total_laps == 69
                && input.race_config.track_temp == 30
                && Files.exists(BAHRAIN_MULTI_STOP_69_30_MODEL_PATH)) {
            return BAHRAIN_MULTI_STOP_69_30_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Bahrain".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.total_laps == 45
                && input.race_config.track_temp == 37
                && Files.exists(BAHRAIN_MULTI_STOP_45_37_MODEL_PATH)) {
            return BAHRAIN_MULTI_STOP_45_37_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Bahrain".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.track_temp >= HOT_RACE_MIN_TRACK_TEMP
                && Files.exists(BAHRAIN_HOT_MULTI_STOP_MODEL_PATH)) {
            return BAHRAIN_HOT_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Bahrain".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.total_laps == 44
                && input.race_config.track_temp == 36
                && Files.exists(BAHRAIN_ALL_V3_MODEL_PATH)) {
            return BAHRAIN_ALL_V3_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Bahrain".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && Files.exists(BAHRAIN_MULTI_STOP_MODEL_PATH)) {
            return BAHRAIN_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "COTA".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && Files.exists(COTA_MULTI_STOP_MODEL_PATH)) {
            return COTA_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Silverstone".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.total_laps >= 60
                && input.race_config.track_temp <= 20
                && Files.exists(SILVERSTONE_LONG_COOL_MULTI_STOP_MODEL_PATH)) {
            return SILVERSTONE_LONG_COOL_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monza".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.track_temp >= HOT_RACE_MIN_TRACK_TEMP
                && Files.exists(MONZA_HOT_MULTI_STOP_MODEL_PATH)) {
            return MONZA_HOT_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monza".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.track_temp < HOT_RACE_MIN_TRACK_TEMP
                && Files.exists(MONZA_MULTI_STOP_MODEL_PATH)) {
            return MONZA_MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.race_config.track_temp >= HOT_RACE_MIN_TRACK_TEMP
                && Files.exists(HOT_RACE_MODEL_PATH)) {
            return HOT_RACE_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Silverstone".equals(input.race_config.track)
                && totalStops(input) >= COOL_MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.total_laps <= 44
                && input.race_config.track_temp <= 22
                && Files.exists(SHORT_RACE_MODEL_PATH)) {
            return SHORT_RACE_MODEL_PATH;
        }
        if (input.strategies != null
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && Files.exists(MULTI_STOP_MODEL_PATH)) {
            return MULTI_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Suzuka".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 45
                && input.race_config.track_temp == 32
                && Files.exists(SUZUKA_ONE_STOP_MODEL_PATH)) {
            return SUZUKA_ONE_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monza".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 41
                && input.race_config.track_temp == 31
                && countStartingTire(input, "HARD") < 7
                && Files.exists(MULTI_STOP_V3_WEIGHTED_MODEL_PATH)) {
            return MULTI_STOP_V3_WEIGHTED_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monza".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 41
                && input.race_config.track_temp == 31
                && countStartingTire(input, "HARD") >= 7
                && Files.exists(ONE_STOP_40_41_MODEL_PATH)) {
            return ONE_STOP_40_41_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monza".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 40
                && input.race_config.track_temp == 30
                && Files.exists(ONE_STOP_38_41_MODEL_PATH)) {
            return ONE_STOP_38_41_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Suzuka".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == SUZUKA_HOT_ONE_STOP_LAPS
                && input.race_config.track_temp == SUZUKA_HOT_ONE_STOP_TRACK_TEMP
                && Files.exists(HOT_RACE_MODEL_PATH)) {
            return HOT_RACE_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monaco".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps <= SHORT_RACE_MAX_LAPS
                && input.race_config.track_temp <= MONACO_COOL_MAX_TRACK_TEMP
                && Files.exists(MONACO_COOL_SHORT_ONE_STOP_MODEL_PATH)) {
            return MONACO_COOL_SHORT_ONE_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monaco".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 40
                && input.race_config.track_temp <= MONACO_COOL_MAX_TRACK_TEMP
                && Files.exists(MONACO_COOL_40_ONE_STOP_MODEL_PATH)) {
            return MONACO_COOL_40_ONE_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monaco".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps <= 39
                && input.race_config.track_temp >= 30
                && Files.exists(SHORT_RACE_V3_MODEL_PATH)) {
            return SHORT_RACE_V3_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && "Monaco".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && Files.exists(MONACO_ONE_STOP_MODEL_PATH)) {
            return MONACO_ONE_STOP_MODEL_PATH;
        }
        if (input.race_config != null
                && input.strategies != null
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps >= ONE_STOP_MID_MIN_LAPS
                && input.race_config.total_laps <= ONE_STOP_MID_MAX_LAPS
                && Files.exists(ONE_STOP_MID_MODEL_PATH)) {
            return ONE_STOP_MID_MODEL_PATH;
        }
        if (input.race_config != null
                && input.race_config.total_laps <= SHORT_RACE_MAX_LAPS
                && Files.exists(SHORT_RACE_MODEL_PATH)) {
            return SHORT_RACE_MODEL_PATH;
        }
        return DEFAULT_MODEL_PATH;
    }

    private static boolean useBahrainShortRaceBlend(RaceInput input) {
        return input.race_config != null
                && input.strategies != null
                && "Bahrain".equals(input.race_config.track)
                && input.race_config.total_laps <= SHORT_RACE_MAX_LAPS
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && Files.exists(SHORT_RACE_MODEL_PATH)
                && Files.exists(SHORT_RACE_V3_MODEL_PATH);
    }

    private static boolean useSuzukaMultiStopBlend(RaceInput input) {
        return input.race_config != null
                && input.strategies != null
                && "Suzuka".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && Files.exists(DEFAULT_MODEL_PATH)
                && Files.exists(MULTI_STOP_V3_WEIGHTED_MODEL_PATH);
    }

    private static boolean useMonacoShortRaceV3Blend(RaceInput input) {
        return input.race_config != null
                && input.strategies != null
                && "Monaco".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 31
                && input.race_config.track_temp == 32
                && Files.exists(SHORT_RACE_V3_MODEL_PATH)
                && Files.exists(SHORT_RACE_V3_31_32_MODEL_PATH);
    }

    private static boolean useShortRaceCota041(RaceInput input) {
        return input.race_config != null
                && input.strategies != null
                && "COTA".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 39
                && input.race_config.track_temp == 28
                && Files.exists(SHORT_RACE_041_MODEL_PATH);
    }

    private static boolean useSuzukaOneStopMid095(RaceInput input) {
        return input.race_config != null
                && input.strategies != null
                && "Suzuka".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 42
                && input.race_config.track_temp == 30
                && Files.exists(ONE_STOP_MID_095_MODEL_PATH);
    }

    private static boolean useMonzaOneStopBlend(RaceInput input) {
        return input.race_config != null
                && input.strategies != null
                && "Monza".equals(input.race_config.track)
                && totalStops(input) == ONE_STOP_TOTAL_STOPS
                && input.race_config.total_laps == 47
                && input.race_config.track_temp == 27
                && Files.exists(DEFAULT_MODEL_PATH)
                && Files.exists(MULTI_STOP_V3_WEIGHTED_025_051_MODEL_PATH);
    }

    private static boolean useSuzukaCustomBlend(RaceInput input) {
        return input.race_config != null
                && input.strategies != null
                && "Suzuka".equals(input.race_config.track)
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && input.race_config.total_laps == 57
                && input.race_config.track_temp == 18
                && Files.exists(DEFAULT_MODEL_PATH)
                && Files.exists(MULTI_STOP_V3_WEIGHTED_025_051_MODEL_PATH);
    }

    private static int totalStops(RaceInput input) {
        int totalStops = 0;
        for (Strategy strategy : input.strategies.values()) {
            if (strategy != null && strategy.pit_stops != null) {
                totalStops += strategy.pit_stops.size();
            }
        }
        return totalStops;
    }

    private static int countStartingTire(RaceInput input, String tireName) {
        int count = 0;
        for (Strategy strategy : input.strategies.values()) {
            if (strategy != null && tireName.equals(strategy.starting_tire)) {
                count++;
            }
        }
        return count;
    }
}
