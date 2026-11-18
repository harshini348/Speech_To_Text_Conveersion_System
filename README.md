# Speech-to-Text Conversion System (Java)

A Java Swing desktop application that converts microphone speech into text using Vosk and stores user details and conversion history in SQLite.

## Requirements
- JDK 17 or later
- Maven 3.8+
- Microphone
- Vosk English model: `vosk-model-small-en-us-0.15`

## Project structure
```text
SpeechToText-GitHub-Project/
├── src/main/java/com/example/speechtotext/App.java
├── pom.xml
└── README.md
```

## Download the Vosk model
Download and extract `vosk-model-small-en-us-0.15` from the official Vosk model page:
https://alphacephei.com/vosk/models

Place the extracted folder here:
```text
model/vosk-model-small-en-us-0.15/
```

The model folder is not included in this repository because it is a large external model file.

## Build
```bash
mvn clean package
```

## Run
```bash
mvn exec:java
```

Register a user in the application before starting recording. Allow microphone access when prompted.

## Notes
- The application creates `speech_history.db` automatically in the working directory.
- The GitHub repository stores the source code and build configuration. Run the desktop application locally on a computer with a microphone and the Vosk model installed.
