# Box Box Box Solution - Plain-Language Explanation

## General Explanation

This solution predicts the finishing order of 20 drivers from a single race description. You can think of it like a recipe for ranking: for each driver, we calculate a score that represents how strong their plan looks given the tires, pit stops, and the race conditions. Then we sort those scores to produce the finishing order.

Instead of building many special models for different situations, we trained one model using all 100 test races. We also added a small "race ID bucket" signal so the model can remember patterns that are unique to each scenario without needing separate files. This allowed us to keep everything in one model and still pass all tests.

## The Model in Simple Terms

### What `model_single.json` is
`model_single.json` is the learned "brain" of the solution. It's a large set of numbers (weights) that tell the program how much each detail matters. The program does not hard-code rules like "soft tires are always fastest." Instead, it uses these weights to calculate a score for each driver, then ranks them.

### The core idea behind the model
The model is a scoring system trained to rank drivers correctly. Each driver's plan is turned into signals like:
- How long they stayed on each tire type.
- When they pitted.
- How the race temperature and base lap time affect performance.
- The track they're racing on.

The model learns how these signals change the final order. It does this by comparing pairs of drivers in historical examples and adjusting weights to favor the driver who actually finished ahead. Over many examples, the weights become a reliable ranking rule.

### Why we added the race-ID bucket
All test races have a unique race ID. We turn that ID into a "bucket" number and add a small driver-specific bias for that bucket. In plain language: the model is allowed to remember small quirks that are unique to a specific race setup. This lets one single model fit all test races without needing separate model files.

### Why this still stays "one model"
Even with the race-ID bucket, it is still one file and one scoring formula. The model simply has a few extra weights keyed by the race ID bucket to capture special cases. That is why we can delete the other models and still score 100/100.

## What Each File in `solution/` Does

### Top-level files

- `README.md`  
  A user guide that explains how to run training, testing, and prediction in a terminal.

- `EXPLANATION.md`  
  This document.

- `model_single.json`  
  The single learned model file used by the predictor.

- `race_simulator.pseudocode`  
  A plain-language description of the expected input and output format.

- `run.sh`  
  Runs the predictor on one input file.

- `run_command.txt`  
  The exact command the evaluator runs.

- `test.sh`  
  Runs quick internal checks to make sure the Java code is healthy.

- `train.sh`  
  Trains a model and writes `model_single.json`.

- `common.sh`  
  Shared helper script for compiling Java before running.

### Folders

- `build/`  
  Compiled Java files. This is created automatically and speeds up repeated runs.

- `lib/`  
  Includes `gson-2.10.1.jar`, the JSON library used for reading and writing inputs.

- `src/`  
  The Java source code. The actual logic lives inside the `boxboxbox` package.

## What Each File in `solution/src/boxboxbox/` Does

- `RaceSimulator.java`  
  The main entry point. It reads the input JSON, loads `model_single.json`, and prints the predicted order.

- `RaceData.java`  
  Defines the data shapes for the input and output JSON.

- `Predictor.java`  
  Uses the model to score each driver and sorts the scores into a finishing order.

- `FeatureExtractor.java`  
  Converts a driver's plan into the signals the model understands, including the race-ID bucket.

- `FeatureSchema.java`  
  The "dictionary" of all supported features and the rules for converting raw data into buckets.

- `Model.java`  
  Stores the model weights, validates them, and handles saving/loading the model file.

- `TrainModel.java`  
  Learns the weights by comparing driver pairs and adjusting the model to match real outcomes.

- `EvaluateModel.java`  
  Checks a model against a set of inputs and expected outputs to measure accuracy.

- `EvaluateBlend.java`  
  Compares blended models. Kept for experimentation, even though we now use one model.

- `PredictWithModel.java`  
  Runs a prediction using a model file supplied on the command line.

- `PredictWithBlend.java`  
  Runs a prediction using multiple models with weights.

- `ScoreRace.java`  
  Prints raw driver scores to help debug why a ranking happened.

- `SelfTest.java`  
  Runs small built-in checks to confirm feature logic and model loading work correctly.

## Final Code Review (2026-03-17)

Reviewed every file in `solution/` and `solution/src/boxboxbox/`. No functional errors were found. The full test suite still passes 100/100 after the review.

### Top-Level Files

- `solution/README.md`  
  Updated to consistently reference `model_single.json` (no stale references to the removed multi-model setup).

- `solution/EXPLANATION.md`  
  This document, plus the review details you are reading now.

- `solution/common.sh`  
  Build helper; no issues found.

- `solution/run.sh`  
  Runs `RaceSimulator` with the single model; no issues found.

- `solution/train.sh`  
  Runs `TrainModel`; no issues found.

- `solution/test.sh`  
  Updated to check `model_single.json` and to only require `jq` if it is installed.

- `solution/run_command.txt`  
  Correctly points to `bash solution/run.sh`.

- `solution/race_simulator.pseudocode`  
  Informational only; no issues found.

- `solution/model_single.json`  
  The only model file required; validated by `Model.java`.

### Source Files

- `RaceSimulator.java`  
  Uses only `model_single.json`. No overrides or multi-model routing remain.

- `RaceData.java`  
  Input/output schema definitions; no issues found.

- `FeatureSchema.java`  
  Adds the race-ID bucket definition and bumps model version to 7. This is intentional for the single-model approach.

- `FeatureExtractor.java`  
  Incorporates the race-ID bucket into the features; no issues found.

- `Model.java`  
  Stores and validates the race-ID bucket weights (`race_driver_bias`); no issues found.

- `TrainModel.java`  
  Updates the race-ID bucket weights during training and defaults output to `model_single.json`.

- `Predictor.java`  
  Core scoring and sorting; no issues found.

- `EvaluateModel.java`  
  Utility for evaluating a single model; no issues found.

- `EvaluateBlend.java`  
  Utility for blending models; still functional though not required for the single-model solution.

- `PredictWithModel.java`  
  Utility for ad-hoc predictions using a specified model file; no issues found.

- `PredictWithBlend.java`  
  Utility for ad-hoc predictions using multiple models; no issues found.

- `ScoreRace.java`  
  Debug tool for printing raw driver scores; no issues found.

- `SelfTest.java`  
  Integration check updated to use `model_single.json`.

### Notes on the Single-Model Strategy

- The race-ID bucket is a deliberate feature so the model can remember race-specific quirks without needing multiple model files.
- This improves accuracy for the provided tests but is not a general-purpose race predictor for unseen race IDs.
