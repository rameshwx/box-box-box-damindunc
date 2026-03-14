package boxboxbox;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.file.Path;

public final class RaceSimulator {
    private RaceSimulator() {
    }

    public static void main(String[] args) {
        try {
            Gson gson = new Gson();
            RaceInput input = gson.fromJson(new InputStreamReader(System.in), RaceInput.class);
            if (input == null) {
                throw new IllegalArgumentException("No input JSON received");
            }
            Model model = Model.load(Path.of("solution", "model.json"));
            PredictionOutput output = Predictor.predict(input, model);
            System.out.println(gson.toJson(output));
        } catch (Exception exception) {
            System.err.println("RaceSimulator error: " + exception.getMessage());
            System.exit(1);
        }
    }
}
