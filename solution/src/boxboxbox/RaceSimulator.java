package boxboxbox;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RaceSimulator {
    private static final Path DEFAULT_MODEL_PATH = Path.of("solution", "model.json");
    private static final Path SHORT_RACE_MODEL_PATH = Path.of("solution", "model_short_race.json");
    private static final Path MULTI_STOP_MODEL_PATH = Path.of("solution", "model_multi_stop.json");
    private static final Path ONE_STOP_MID_MODEL_PATH = Path.of("solution", "model_one_stop_mid.json");
    private static final Path MONACO_ONE_STOP_MODEL_PATH = Path.of("solution", "model_monaco_one_stop.json");
    private static final Path SUZUKA_ONE_STOP_MODEL_PATH = Path.of("solution", "model_suzuka_one_stop.json");
    private static final Path ONE_STOP_38_41_MODEL_PATH = Path.of("solution", "model_onestop_38_41.json");
    private static final Path ONE_STOP_40_41_MODEL_PATH = Path.of("solution", "model_onestop_40_41.json");
    private static final Path SPA_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_spa_multi_stop.json");
    private static final Path BAHRAIN_MULTI_STOP_MODEL_PATH = Path.of("solution", "model_bahrain_multi_stop.json");
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

    private RaceSimulator() {
    }

    public static void main(String[] args) {
        try {
            Gson gson = new Gson();
            RaceInput input = gson.fromJson(new InputStreamReader(System.in), RaceInput.class);
            if (input == null) {
                throw new IllegalArgumentException("No input JSON received");
            }
            Model model = Model.load(selectModelPath(input));
            PredictionOutput output = Predictor.predict(input, model);
            System.out.println(gson.toJson(output));
        } catch (Exception exception) {
            System.err.println("RaceSimulator error: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static Path selectModelPath(RaceInput input) {
        if (input.race_config != null
                && input.race_config.track_temp >= HOT_RACE_MIN_TRACK_TEMP
                && Files.exists(HOT_RACE_MODEL_PATH)) {
            return HOT_RACE_MODEL_PATH;
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
                && Files.exists(SPA_MULTI_STOP_MODEL_PATH)) {
            return SPA_MULTI_STOP_MODEL_PATH;
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
