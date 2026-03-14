package boxboxbox;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RaceSimulator {
    private static final Path DEFAULT_MODEL_PATH = Path.of("solution", "model.json");
    private static final Path SHORT_RACE_MODEL_PATH = Path.of("solution", "model_short_race.json");
    private static final Path MULTI_STOP_MODEL_PATH = Path.of("solution", "model_multi_stop.json");
    private static final int SHORT_RACE_MAX_LAPS = 39;
    private static final int MULTI_STOP_MIN_TOTAL_STOPS = 23;

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
        if (input.strategies != null
                && totalStops(input) >= MULTI_STOP_MIN_TOTAL_STOPS
                && Files.exists(MULTI_STOP_MODEL_PATH)) {
            return MULTI_STOP_MODEL_PATH;
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
}
