package boxboxbox;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class EvaluateModel {
    private EvaluateModel() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: EvaluateModel <model-path> <inputs-dir> <expected-dir>");
        }

        Path modelPath = Path.of(args[0]);
        Path inputsDir = Path.of(args[1]);
        Path expectedDir = Path.of(args[2]);

        Gson gson = new Gson();
        Model model = Model.load(modelPath);
        List<Path> inputs = new ArrayList<>();
        try (var stream = Files.list(inputsDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(inputs::add);
        }

        int passed = 0;
        for (Path inputPath : inputs) {
            RaceInput input = readJson(gson, inputPath, RaceInput.class);
            PredictionOutput prediction = Predictor.predict(input, model);
            PredictionOutput expected = readJson(gson, expectedDir.resolve(inputPath.getFileName()), PredictionOutput.class);
            boolean correct = prediction.finishing_positions.equals(expected.finishing_positions);
            if (correct) {
                passed++;
            }
            System.out.printf(
                    "%s\t%s\ttrack=%s\tlaps=%d\ttemp=%d%n",
                    inputPath.getFileName(),
                    correct ? "PASS" : "FAIL",
                    input.race_config.track,
                    input.race_config.total_laps,
                    input.race_config.track_temp);
        }

        System.out.printf("SUMMARY\t%d/%d%n", passed, inputs.size());
    }

    private static <T> T readJson(Gson gson, Path path, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            T value = gson.fromJson(reader, type);
            if (value == null) {
                throw new IllegalArgumentException("No JSON content in " + path);
            }
            return value;
        }
    }
}
