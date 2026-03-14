package boxboxbox;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.file.Path;

final class PredictWithModel {
    private PredictWithModel() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException("Usage: PredictWithModel <model-path>");
            }
            Gson gson = new Gson();
            RaceInput input = gson.fromJson(new InputStreamReader(System.in), RaceInput.class);
            if (input == null) {
                throw new IllegalArgumentException("No input JSON received");
            }
            Model model = Model.load(Path.of(args[0]));
            PredictionOutput output = Predictor.predict(input, model);
            System.out.println(gson.toJson(output));
        } catch (Exception exception) {
            System.err.println("PredictWithModel error: " + exception.getMessage());
            System.exit(1);
        }
    }
}
