package boxboxbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Predictor {
    private Predictor() {
    }

    static PredictionOutput predict(RaceInput input, Model model) {
        return predict(input, new Model[] {model}, new double[] {1.0});
    }

    static PredictionOutput predict(RaceInput input, Model[] models, double[] weights) {
        PreparedRace prepared = FeatureExtractor.prepareRace(input, false);
        if (prepared.drivers.length != 20) {
            throw new IllegalArgumentException("Expected exactly 20 drivers, found " + prepared.drivers.length);
        }
        List<String> finishingPositions = predictOrder(prepared.drivers, models, weights);
        return new PredictionOutput(prepared.raceId, finishingPositions);
    }

    static List<String> predictOrder(DriverFeatures[] drivers, Model model) {
        return predictOrder(drivers, new Model[] {model}, new double[] {1.0});
    }

    static List<String> predictOrder(DriverFeatures[] drivers, Model[] models, double[] weights) {
        if (models.length != weights.length || models.length == 0) {
            throw new IllegalArgumentException("models and weights must have the same non-zero length");
        }
        List<ScoredDriver> scored = new ArrayList<>(drivers.length);
        for (DriverFeatures driver : drivers) {
            double score = 0.0;
            for (int index = 0; index < models.length; index++) {
                score += weights[index] * driver.score(models[index]);
            }
            scored.add(new ScoredDriver(driver.driverId, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredDriver::score).thenComparing(ScoredDriver::driverId));

        List<String> finishingPositions = new ArrayList<>(scored.size());
        for (ScoredDriver driver : scored) {
            finishingPositions.add(driver.driverId());
        }
        return finishingPositions;
    }

    private record ScoredDriver(String driverId, double score) {
    }
}
