# Spring AI in Action

A collection of independent Spring Boot projects using Spring AI. Each project
has its own Gradle build and wrapper and can be built and run separately.

## Projects

| Project | Description |
| --- | --- |
| [board-game-buddy](board-game-buddy/) | Answers questions about board game rules using Spring AI and Ollama. |
| [vector-store-loader](vector-store-loader/) | Spring AI starter project with Qdrant vector store and document reader dependencies. |

## Requirements

- JDK 25.
- Use each project's Gradle wrapper; a system Gradle installation is unnecessary.
- Start the external services required by the selected project. Check its
  `src/main/resources/application.yaml` for the Ollama model and service settings.
- Docker with Docker Compose is needed to run the provided Qdrant container.

## Build and run

From the repository root, enter the project you want to use:

```bash
cd board-game-buddy
# Or: cd vector-store-loader
```

Build the project, including its tests:

```bash
./gradlew build
```

Run only the tests:

```bash
./gradlew test
```

Start the application:

```bash
./gradlew bootRun
```

On Windows, use `.\gradlew.bat` in place of `./gradlew`.

To start the provided Qdrant service, run this from the repository root:

```bash
docker compose -f board-game-buddy/docker-compose.yaml up -d
```

Qdrant stores its local data in `board-game-buddy/qdrant_storage/`, which Git
ignores. Start Ollama separately with the model configured for your project.

## Adding a project

Create a new Spring Boot project in its own directory directly under this root.
Include its build definition, settings, Gradle wrapper scripts and JAR, and source
files. Keep the project independently buildable and add it to the table above.

Use the root Git repository for every project. Shared ignore and line-ending
rules apply to projects at every directory depth. Build output, IDE files, local
environment files, logs, and Qdrant storage are excluded; wrapper JARs and
environment example files remain eligible for tracking.
