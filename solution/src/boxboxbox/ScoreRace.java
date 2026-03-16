package boxboxbox;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ScoreRace {
    private ScoreRace() {
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw new IllegalArgumentException("Usage: ScoreRace <model-path>...");
            }

            Gson gson = new Gson();
            RaceInput input = gson.fromJson(new InputStreamReader(System.in), RaceInput.class);
            if (input == null) {
                throw new IllegalArgumentException("No input JSON received");
            }

            PreparedRace prepared = FeatureExtractor.prepareRace(input, false);
            Model[] models = new Model[args.length];
            for (int index = 0; index < args.length; index++) {
                models[index] = Model.load(Path.of(args[index]));
            }

            Map<String, double[]> scores = new LinkedHashMap<>();
            for (DriverFeatures driver : prepared.drivers) {
                double[] driverScores = new double[models.length];
                for (int index = 0; index < models.length; index++) {
                    driverScores[index] = driver.score(models[index]);
                }
                scores.put(driver.driverId, driverScores);
            }

            System.out.println(gson.toJson(scores));
        } catch (Exception exception) {
            System.err.println("ScoreRace error: " + exception.getMessage());
            System.exit(1);
        }
    }
}
