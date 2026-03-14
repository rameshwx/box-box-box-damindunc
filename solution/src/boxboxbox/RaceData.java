package boxboxbox;

import java.util.List;
import java.util.Map;

final class RaceInput {
    String race_id;
    RaceConfig race_config;
    Map<String, Strategy> strategies;
    List<String> finishing_positions;
}

final class RaceConfig {
    String track;
    int total_laps;
    double base_lap_time;
    double pit_lane_time;
    int track_temp;
    String race_id;
}

final class Strategy {
    String driver_id;
    String starting_tire;
    List<PitStop> pit_stops;
}

final class PitStop {
    int lap;
    String from_tire;
    String to_tire;
}

final class PredictionOutput {
    final String race_id;
    final List<String> finishing_positions;

    PredictionOutput(String raceId, List<String> finishingPositions) {
        this.race_id = raceId;
        this.finishing_positions = finishingPositions;
    }
}
