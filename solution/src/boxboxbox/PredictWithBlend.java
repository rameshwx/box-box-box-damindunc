package boxboxbox;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.file.Path;

final class PredictWithBlend {
    private PredictWithBlend() {
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw new IllegalArgumentException("Usage: PredictWithBlend <weight:model-path>...");
            }

            Model[] models = new Model[args.length];
            double[] weights = new double[args.length];
            for (int index = 0; index < args.length; index++) {
                int separator = args[index].indexOf(':');
                if (separator <= 0 || separator == args[index].length() - 1) {
                    throw new IllegalArgumentException("Each argument must be <weight:model-path>: " + args[index]);
                }
                weights[index] = Double.parseDouble(args[index].substring(0, separator));
                models[index] = Model.load(Path.of(args[index].substring(separator + 1)));
            }

            Gson gson = new Gson();
            RaceInput input = gson.fromJson(new InputStreamReader(System.in), RaceInput.class);
            if (input == null) {
                throw new IllegalArgumentException("No input JSON received");
            }
            PreparedRace prepared = FeatureExtractor.prepareRace(input, false);
            PredictionOutput output =
                    new PredictionOutput(prepared.raceId, Predictor.predictOrder(prepared.drivers, models, weights));
            System.out.println(gson.toJson(output));
        } catch (Exception exception) {
            System.err.println("PredictWithBlend error: " + exception.getMessage());
            System.exit(1);
        }
    }
}
