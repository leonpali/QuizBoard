# QuizAbfahrt

A Jeopardy-style quiz board for game nights. The host projects the board on a big screen; participants join from their phones and use them as buzzers. The host awards points after each question, and the game ends with a podium.

Built with Kotlin, Spring Boot and Thymeleaf. The UI is in German.

## Features

- **Quizzes from text files**: write boards in a simple `.txt` format and upload them together with the images they use.
- **Join with a code**: every quiz gets a 6-digit code; participants enter it and a name at `/join`.
- **Phone buzzers**: buzzers unlock the moment the host opens a question. Each participant can buzz once per question, and the buzz order appears live on the big screen.
- **Scoring**: after each answer, the host marks participants as correct (full points) or wrong (minus half the points). Several participants can score on the same question.
- **Final scoreboard**: a podium for places 1–3 and a list of everyone else. Ties share a place, and the following places are skipped (1, 1, 3).
- **No database**: quizzes live in memory and expire after 24 hours, along with their images and the device cookies.

## Running locally

Requirements: JDK 21. The Gradle 8.7 wrapper doesn't run on newer JDKs.

```bash
chmod +x gradlew          # only needed once
./gradlew bootRun
```

Then open <http://localhost:8080>. To use another port: `./gradlew bootRun --args='--server.port=8099'`.

If your default Java is newer than 21, point Gradle at a JDK 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew bootRun
```

To build a jar instead: `./gradlew build`, then `java -jar build/libs/QuizAbfahrt-0.0.1-SNAPSHOT.jar`.

## How a quiz works

1. **Create**: on the start page, upload one `.txt` file per board and all images the boards reference. Boards are ordered by file name (`Board_1`, `Board_2`, … `Board_10`). The page lists which referenced images are still missing before you submit.
2. **Lobby**: the host lands on `/host`, which shows the join link, the code and the participants as they join. Put this on the projector.
3. **Join**: participants open `/join` on their phones, enter the code and a name, and keep the buzzer page open.
4. **Play**: the host picks a question on the board. Buzzers open, and the buzz order shows in the corner. "Weiter" reveals the answer and closes the buzzers.
5. **Score**: after the answer, the host marks who was right or wrong. Clicking a highlighted button again undoes it.
6. **Finish**: "Nächstes Board →" moves on. After the last board it leads to the final scoreboard.

The host is recognised by a cookie set when creating the quiz; only that device can open the board, questions, answers and uploaded images. Participants only ever see their buzzer.

Without any participants, the app still works as a plain projected quiz board: the buzz panel and scoring screen are skipped.

## Board file format

Each file is one board. Every category needs exactly 6 questions; there can be any number of categories. Keys must appear in exactly this order (indentation is optional, blank lines are ignored):

```
category_1:
    name: Geography
    questions:
        question_1:
            type: text
            text: What is the capital of Estonia?
            img: null
            points: 100
            answer:
                type: text
                text: Tallinn
                img: null
        question_2:
            type: img
            text:
            img: skyline.jpg
            points: 200
            answer:
                type: textimage
                text: Space Needle, Seattle
                img: skylineAnswer.jpg
        ...
category_2:
    ...
```

| Field | Values |
|---|---|
| Question `type` | `text` (shows `text`), `img` (shows `img`), `lineup` (`text` is a comma-separated list, revealed item by item with "Aufdecken") |
| Answer `type` | `text`, `img`, `textimage` (image and text) |
| `img` | File name of an uploaded image, or `null` / empty |
| `points` | Any whole number. A wrong answer deducts half (rounded down). |

Errors in a file are reported on upload with the file name and line number.

## Configuration

Set in `src/main/resources/application.properties` or as command-line arguments (`--quiz.ttl=12h`):

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `quiz.ttl` | `24h` | How long a quiz, its images and its cookies stay alive after creation |
| `quiz.max-sessions` | `10` | Quizzes that can run at the same time |
| `quiz.max-players` | `30` | Participants per quiz |
| `quiz.upload-dir` | `${java.io.tmpdir}/quizabfahrt/images` | Where uploaded images are stored; cleared on startup |
| `spring.servlet.multipart.max-file-size` | `25MB` | Largest single upload |
| `spring.servlet.multipart.max-request-size` | `500MB` | Largest total upload per quiz |

## Deploying on a server

- Run a single instance: quizzes only exist in that process's memory, and restarting the app ends all running quizzes.
- Put it behind a reverse proxy (e.g. nginx or Caddy) with HTTPS. The app reads `X-Forwarded-*` headers from proxies on the same machine or private network, so cookies get the `Secure` flag and the lobby shows the public join link.
- Live updates use Server-Sent Events on `/host/events` and `/play/events`. With nginx, disable buffering for them (the app also sends `X-Accel-Buffering: no`) and set `proxy_read_timeout` above 30 seconds.
- Anyone who can reach the server can create a quiz. `quiz.max-sessions` and the upload limits keep this in check, but there is no password for creating quizzes.

## Project structure

```
src/main/kotlin/projects/kotlin/quizabfahrt/
├── model/                  Board, Category, Question, Answer
├── application/
│   ├── service/            QuizService (upload + validation), QuestionService
│   │   └── parser/         CompilationEngine: parses the .txt board files
│   └── session/            QuizSession and QuizSessionService: codes, players, buzzing, scores, ranking
└── web/                    Controllers, cookies, live updates (QuizEventBroadcaster)

src/main/resources/
├── templates/              Thymeleaf pages (start, lobby, board, questions, answers, scoring, scoreboard, join, buzzer)
├── static/css/             Styles
├── static/images/          Bundled images, usable by any quiz without uploading them
└── boards/                 Example board file
```

Run the tests with `./gradlew test`.
