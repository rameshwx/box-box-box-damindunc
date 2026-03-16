package boxboxbox;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public final class RaceSimulator {
    private static final Path SINGLE_MODEL_PATH = Path.of("solution", "model_single.json");

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
        return Predictor.predict(input, Model.load(SINGLE_MODEL_PATH));
    }
}
