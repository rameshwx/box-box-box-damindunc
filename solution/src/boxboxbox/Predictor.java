package boxboxbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Predictor {
    private Predictor() {
    }

    static PredictionOutput predict(RaceInput input, Model model) {
        PreparedRace prepared = FeatureExtractor.prepareRace(input, false);
        if (prepared.drivers.length != 20) {
            throw new IllegalArgumentException("Expected exactly 20 drivers, found " + prepared.drivers.length);
        }
        List<String> finishingPositions = predictOrder(prepared.drivers, model);
        return new PredictionOutput(prepared.raceId, finishingPositions);
    }

    static List<String> predictOrder(DriverFeatures[] drivers, Model model) {
        List<ScoredDriver> scored = new ArrayList<>(drivers.length);
        for (DriverFeatures driver : drivers) {
            scored.add(new ScoredDriver(driver.driverId, driver.score(model)));
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
