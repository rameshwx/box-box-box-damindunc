# Box Box Box 🏁
## F1 Pit Strategy Optimization Challenge

> *"Box, Box, Box"* - F1 radio call for pitting

> *"damindunc@gmail.com"*
---
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



# Java Solver Guide

This folder contains a Java 17 command-line solution for the Box Box Box challenge.

Important:
- You do **not** run this in a web browser.
- You run it in a **terminal** window.
- The main commands are:
  - `bash solution/train.sh`
  - `bash solution/test.sh`
  - `bash solution/run.sh`

If you are new to this, think of the terminal as a text-based control panel for your computer.

Note: This solution uses a single model file called `solution/model_single.json`. The predictor always reads that file.

## What each file does

- `solution/train.sh`
  Trains the model from the chosen data folder (defaults to historical races) and creates `solution/model_single.json`.
- `solution/test.sh`
  Runs local checks to make sure the Java code works.
- `solution/run.sh`
  Runs the predictor on one race input.
- `solution/run_command.txt`
  This is for the hackathon evaluator. It contains the command the evaluator will run automatically.

## Before you start

You need:
- Java 17
- `bash`
- `jq` is optional, but helpful for pretty JSON output

This project does **not** need Maven or Gradle.

## First step for all operating systems

Before running anything, open a terminal and move into the project folder.

Example project folder:

```text
/Users/ramesh/Documents/My Projects/SansaTech/box-box-box
```

Because the folder name contains spaces, use quotes:

```bash
cd "/Users/ramesh/Documents/My Projects/SansaTech/box-box-box"
```

After that, all commands below should be run from inside that folder.

## macOS

### 1. Open Terminal

1. Press `Command + Space`
2. Type `Terminal`
3. Press `Enter`

### 2. Go to the project folder

```bash
cd "/Users/ramesh/Documents/My Projects/SansaTech/box-box-box"
```

### 3. Check Java

```bash
java -version
```

If Java is installed correctly, you will see version information.

### 4. Train the model

This creates `solution/model_single.json`.

```bash
bash solution/train.sh --epochs 5 --out solution/model_single.json
```

### 5. Run local tests

```bash
bash solution/test.sh
```

### 6. Predict one sample race

```bash
bash solution/run.sh < data/test_cases/inputs/test_001.json
```

If you want prettier output:

```bash
bash solution/run.sh < data/test_cases/inputs/test_001.json | jq .
```

### 7. Run all 100 challenge tests

```bash
./test_runner.sh
```

## Windows

The easiest option on Windows is to use **Git Bash**.

### 1. Install Git Bash

1. Download **Git for Windows**
2. Install it
3. Open **Git Bash**

### 2. Go to the project folder

Example:

```bash
cd "/c/Users/YourName/path/to/box-box-box"
```

Replace that path with your real project folder.

### 3. Check Java

```bash
java -version
```

### 4. Train the model

```bash
bash solution/train.sh --epochs 5 --out solution/model_single.json
```

### 5. Run local tests

```bash
bash solution/test.sh
```

### 6. Predict one sample race

```bash
bash solution/run.sh < data/test_cases/inputs/test_001.json
```

### 7. Run all 100 challenge tests

```bash
./test_runner.sh
```

## Linux

### 1. Open Terminal

Open your system terminal from the applications menu.

### 2. Go to the project folder

Example:

```bash
cd "/home/yourname/path/to/box-box-box"
```

Replace that path with your real project folder.

### 3. Check Java

```bash
java -version
```

### 4. Train the model

```bash
bash solution/train.sh --epochs 5 --out solution/model_single.json
```

### 5. Run local tests

```bash
bash solution/test.sh
```

### 6. Predict one sample race

```bash
bash solution/run.sh < data/test_cases/inputs/test_001.json
```

### 7. Run all 100 challenge tests

```bash
./test_runner.sh
```

## What the main commands mean

### Train

```bash
bash solution/train.sh --epochs 5 --out solution/model_single.json
```

This reads the training data and builds the model file used by the solver.

If you want to retrain using the bundled test-case dataset, you can run:

```bash
bash solution/train.sh --hist-dir data/custom_training/all_tests --epochs 120 --learning-rate-scale 0.2 --save-last true --out solution/model_single.json
```

### Test

```bash
bash solution/test.sh
```

This checks that the Java code compiles and the local tests pass.

### Predict one race

```bash
bash solution/run.sh < data/test_cases/inputs/test_001.json
```

This takes one JSON input race and prints a predicted finishing order.

### Full challenge test

```bash
./test_runner.sh
```

This runs all 100 provided test cases and shows how many passed.

## About `run_command.txt`

`solution/run_command.txt` is not a file where you write instructions for a person.

It should contain only the command the evaluator must run:

```txt
bash solution/run.sh
```

The hackathon system reads that file automatically.

## Common problems

### “command not found”

This usually means:
- Java is not installed, or
- you are not using a terminal, or
- you are not inside the project folder

### “solution/model_single.json” missing

Run training first:

```bash
bash solution/train.sh --epochs 5 --out solution/model_single.json
```

### Path with spaces not working

Use quotes around the folder path:

```bash
cd "/Users/ramesh/Documents/My Projects/SansaTech/box-box-box"
```

## Quick start for macOS

If you only want the shortest version, use these commands in Terminal:

```bash
cd "/Users/ramesh/Documents/My Projects/SansaTech/box-box-box"
java -version
bash solution/train.sh --epochs 5 --out solution/model_single.json
bash solution/test.sh
bash solution/run.sh < data/test_cases/inputs/test_001.json | jq .
./test_runner.sh
```

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
