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

### "command not found"

This usually means:
- Java is not installed, or
- you are not using a terminal, or
- you are not inside the project folder

### "solution/model_single.json" missing

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
