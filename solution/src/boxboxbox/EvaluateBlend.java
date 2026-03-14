package boxboxbox;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class EvaluateBlend {
    private EvaluateBlend() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "Usage: EvaluateBlend <inputs-dir> <expected-dir> <input-names|*> <weight:model-path>...");
        }

        Path inputsDir = Path.of(args[0]);
        Path expectedDir = Path.of(args[1]);
        String inputNamesArg = args[2];

        Model[] models = new Model[args.length - 3];
        double[] weights = new double[args.length - 3];
        for (int index = 3; index < args.length; index++) {
            int separator = args[index].indexOf(':');
            if (separator <= 0 || separator == args[index].length() - 1) {
                throw new IllegalArgumentException("Each model argument must be <weight:model-path>: " + args[index]);
            }
            weights[index - 3] = Double.parseDouble(args[index].substring(0, separator));
            models[index - 3] = Model.load(Path.of(args[index].substring(separator + 1)));
        }

        Gson gson = new Gson();
        List<Path> inputs = resolveInputs(inputsDir, inputNamesArg);
        int passed = 0;
        for (Path inputPath : inputs) {
            RaceInput input = readJson(gson, inputPath, RaceInput.class);
            PreparedRace prepared = FeatureExtractor.prepareRace(input, false);
            List<String> prediction = Predictor.predictOrder(prepared.drivers, models, weights);
            PredictionOutput expected = readJson(gson, expectedDir.resolve(inputPath.getFileName()), PredictionOutput.class);
            boolean correct = prediction.equals(expected.finishing_positions);
            if (correct) {
                passed++;
            }
            System.out.printf("%s\t%s%n", inputPath.getFileName(), correct ? "PASS" : "FAIL");
        }
        System.out.printf("SUMMARY\t%d/%d%n", passed, inputs.size());
    }

    private static List<Path> resolveInputs(Path inputsDir, String inputNamesArg) throws IOException {
        if ("*".equals(inputNamesArg)) {
            try (var stream = Files.list(inputsDir)) {
                return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        }

        List<Path> inputs = new ArrayList<>();
        for (String inputName : Arrays.stream(inputNamesArg.split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList()) {
            Path inputPath = inputsDir.resolve(inputName);
            if (!Files.exists(inputPath)) {
                throw new IllegalArgumentException("Input file does not exist: " + inputPath);
            }
            inputs.add(inputPath);
        }
        return inputs;
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
