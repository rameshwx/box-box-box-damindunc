package boxboxbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

public final class SelfTest {
    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        testOneStopAgeExpansion();
        testTwoStopAgeExpansion();
        testPenultimateStop();
        testModelRoundTrip();
        testRankingPredictor();
        maybeRunIntegrationChecks();
        System.out.println("SelfTest: all checks passed");
    }

    private static void testOneStopAgeExpansion() {
        RaceInput race = buildRace(
                "UNIT_ONE_STOP",
                10,
                strategy("D001", "SOFT", pitStop(4, "SOFT", "HARD")));
        DriverFeatures driver = FeatureExtractor.prepareRace(race, false).drivers[0];
        assertEquals(driver.countFor("SOFT", 1), 1, "soft age 1");
        assertEquals(driver.countFor("SOFT", 4), 1, "soft age 4");
        assertEquals(driver.countFor("HARD", 1), 1, "hard age 1");
        assertEquals(driver.countFor("HARD", 6), 1, "hard age 6");
        assertEquals(driver.countFor("SOFT", 5), 0, "soft age 5 absent");
        assertEquals(driver.lapCountFor("SOFT", 1), 1, "soft lap 1");
        assertEquals(driver.lapCountFor("SOFT", 4), 1, "soft lap 4");
        assertEquals(driver.lapCountFor("HARD", 5), 1, "hard lap 5");
        assertEquals(driver.lapCountFor("HARD", 10), 1, "hard lap 10");
        assertEquals(
                driver.phaseCountFor("SOFT", FeatureSchema.phaseBucket(1, 10)),
                1,
                "soft opening phase");
        assertEquals(
                driver.phaseCountFor("HARD", FeatureSchema.phaseBucket(10, 10)),
                1,
                "hard closing phase");
    }

    private static void testTwoStopAgeExpansion() {
        RaceInput race = buildRace(
                "UNIT_TWO_STOP",
                9,
                strategy(
                        "D001",
                        "MEDIUM",
                        pitStop(3, "MEDIUM", "SOFT"),
                        pitStop(7, "SOFT", "HARD")));
        DriverFeatures driver = FeatureExtractor.prepareRace(race, false).drivers[0];
        assertEquals(driver.countFor("MEDIUM", 1), 1, "medium age 1");
        assertEquals(driver.countFor("MEDIUM", 3), 1, "medium age 3");
        assertEquals(driver.countFor("SOFT", 4), 1, "soft age 4");
        assertEquals(driver.countFor("HARD", 2), 1, "hard age 2");
        assertEquals(driver.countFor("SOFT", 5), 0, "soft age 5 absent");
        assertEquals(driver.lapCountFor("MEDIUM", 3), 1, "medium lap 3");
        assertEquals(driver.lapCountFor("SOFT", 4), 1, "soft lap 4");
        assertEquals(driver.lapCountFor("SOFT", 7), 1, "soft lap 7");
        assertEquals(driver.lapCountFor("HARD", 8), 1, "hard lap 8");
        assertEquals(
                driver.phaseCountFor("SOFT", FeatureSchema.phaseBucket(4, 9)),
                1,
                "soft phase after stop");
    }

    private static void testPenultimateStop() {
        RaceInput race = buildRace(
                "UNIT_PENULTIMATE",
                10,
                strategy("D001", "MEDIUM", pitStop(9, "MEDIUM", "HARD")));
        DriverFeatures driver = FeatureExtractor.prepareRace(race, false).drivers[0];
        assertEquals(driver.countFor("MEDIUM", 9), 1, "medium age 9");
        assertEquals(driver.countFor("HARD", 1), 1, "hard age 1");
        assertEquals(driver.countFor("HARD", 2), 0, "hard age 2 absent");
        assertEquals(driver.lapCountFor("MEDIUM", 9), 1, "medium lap 9");
        assertEquals(driver.lapCountFor("HARD", 10), 1, "hard lap 10");
    }

    private static void testModelRoundTrip() throws Exception {
        Model model = Model.zero();
        model.global_age[FeatureSchema.compoundIndex("SOFT")][1] = -0.75;
        model.temp_age[FeatureSchema.tempBucket(30)][FeatureSchema.compoundIndex("HARD")][3] = 0.25;
        model.global_phase[FeatureSchema.compoundIndex("SOFT")][FeatureSchema.phaseBucket(1, 5)] = -0.1;

        Path tempFile = Files.createTempFile("boxboxbox-model", ".json");
        try {
            model.save(tempFile);
            Model loaded = Model.load(tempFile);
            DriverFeatures driver = FeatureExtractor.prepareRace(
                            buildRace("UNIT_ROUND_TRIP", 5, strategy("D001", "SOFT", pitStop(2, "SOFT", "HARD"))),
                            false)
                    .drivers[0];
            assertClose(driver.score(model), driver.score(loaded), 1e-9, "score round trip");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void testRankingPredictor() {
        Model model = Model.zero();
        model.global_age[FeatureSchema.compoundIndex("SOFT")][1] = -2.0;
        model.global_age[FeatureSchema.compoundIndex("SOFT")][2] = -2.0;
        model.global_age[FeatureSchema.compoundIndex("SOFT")][3] = -2.0;
        model.global_age[FeatureSchema.compoundIndex("HARD")][1] = 1.0;
        model.global_age[FeatureSchema.compoundIndex("HARD")][2] = 1.0;
        model.global_age[FeatureSchema.compoundIndex("HARD")][3] = 1.0;
        model.global_age[FeatureSchema.compoundIndex("MEDIUM")][1] = 2.0;
        model.global_age[FeatureSchema.compoundIndex("MEDIUM")][2] = 2.0;
        model.global_age[FeatureSchema.compoundIndex("MEDIUM")][3] = 2.0;
        model.global_lap[FeatureSchema.compoundIndex("SOFT")][1] = -0.5;
        model.global_lap[FeatureSchema.compoundIndex("SOFT")][2] = -0.5;
        model.global_lap[FeatureSchema.compoundIndex("SOFT")][3] = -0.5;

        RaceInput race = buildRace(
                "UNIT_RANKING",
                6,
                strategy("D001", "SOFT", pitStop(3, "SOFT", "HARD")),
                strategy("D002", "HARD", pitStop(3, "HARD", "SOFT")),
                strategy("D003", "MEDIUM", pitStop(3, "MEDIUM", "SOFT")));
        PreparedRace prepared = FeatureExtractor.prepareRace(race, false);
        List<String> order = Predictor.predictOrder(prepared.drivers, model);
        assertEquals(order.get(0), "D001", "expected fastest driver");
    }

    private static void maybeRunIntegrationChecks() throws Exception {
        Path modelPath = Path.of("solution", "model.json");
        if (!Files.exists(modelPath)) {
            System.out.println("SelfTest: skipping integration checks because solution/model.json does not exist yet");
            return;
        }
        Model model;
        try {
            model = Model.load(modelPath);
        } catch (IllegalArgumentException exception) {
            System.out.println(
                    "SelfTest: skipping integration checks because solution/model.json is incompatible with this code");
            return;
        }
        com.google.gson.Gson gson = new com.google.gson.Gson();
        for (String name : List.of("test_001.json", "test_050.json")) {
            try (var reader = Files.newBufferedReader(Path.of("data", "test_cases", "inputs", name))) {
                RaceInput input = gson.fromJson(reader, RaceInput.class);
                PredictionOutput output = Predictor.predict(input, model);
                assertEquals(output.race_id, input.race_id, "race_id echo");
                assertEquals(output.finishing_positions.size(), 20, "finishing positions size");
                assertEquals(
                        output.finishing_positions.stream().distinct().count(),
                        20L,
                        "finishing positions uniqueness");
                PredictionOutput repeat = Predictor.predict(input, model);
                assertEquals(output.finishing_positions, repeat.finishing_positions, "deterministic output");
            }
        }
    }

    private static RaceInput buildRace(String raceId, int totalLaps, Strategy... strategies) {
        RaceInput race = new RaceInput();
        race.race_id = raceId;
        race.race_config = new RaceConfig();
        race.race_config.track = "Monza";
        race.race_config.total_laps = totalLaps;
        race.race_config.base_lap_time = 85.0;
        race.race_config.pit_lane_time = 22.0;
        race.race_config.track_temp = 30;

        race.strategies = new LinkedHashMap<>();
        for (int index = 0; index < strategies.length; index++) {
            race.strategies.put("pos" + (index + 1), strategies[index]);
        }
        return race;
    }

    private static Strategy strategy(String driverId, String startingTire, PitStop... pitStops) {
        Strategy strategy = new Strategy();
        strategy.driver_id = driverId;
        strategy.starting_tire = startingTire;
        strategy.pit_stops = List.of(pitStops);
        return strategy;
    }

    private static PitStop pitStop(int lap, String fromTire, String toTire) {
        PitStop pitStop = new PitStop();
        pitStop.lap = lap;
        pitStop.from_tire = fromTire;
        pitStop.to_tire = toTire;
        return pitStop;
    }

    private static void assertEquals(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertClose(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
