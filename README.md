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

The `deploy/` folder contains everything for a Linux server (Ubuntu/Debian): the app runs as a systemd service on `127.0.0.1:8080`, and [Caddy](https://caddyserver.com) in front of it handles HTTPS with an automatic Let's Encrypt certificate.

**Before you start**, point your domain at the server: an A record for e.g. `quiz.example.de` with the server's IPv4 address. Ports 80 and 443 must be open in the server's firewall.

**One-time setup on the server:**

```bash
sudo apt update
sudo apt install openjdk-21-jre-headless caddy

sudo useradd --system --no-create-home --shell /usr/sbin/nologin quiz
sudo mkdir -p /opt/quizabfahrt
```

Copy `deploy/quizabfahrt.service` to `/etc/systemd/system/` and `deploy/Caddyfile` to `/etc/caddy/Caddyfile`, replacing `quiz.example.de` with your domain. Then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable quizabfahrt
sudo systemctl reload caddy
```

**Deploying (from your computer):**

```bash
deploy/deploy.sh user@your-server
```

This runs the tests, builds the jar, copies it to the server and restarts the service. A restart ends all running quizzes, so don't deploy during a quiz night.

## CI/CD with GitHub Actions

[`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) builds and tests every push and pull request. Deploying only happens when you start it: **Actions → CI/CD → Run workflow** on `main`. It runs the tests, copies the jar to the server over SSH, restarts the service and checks that the site responds.

To deploy automatically on every push to `main` instead, change the deploy job's condition to `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`. Keep in mind that every deploy ends running quizzes.

**One-time setup on the server:** a `deploy` user for GitHub that may only replace the jar and restart the service.

```bash
sudo useradd --create-home --shell /bin/bash deploy
sudo install -m 440 deploy/sudoers-deploy /etc/sudoers.d/deploy   # copy the file from this repo first
sudo visudo -c                                                     # must report "parsed OK"
```

**One-time setup on your computer:** create a key pair only for GitHub and allow it on the server.

```bash
ssh-keygen -t ed25519 -f quizabfahrt-deploy -N "" -C "github-actions"
ssh-copy-id -i quizabfahrt-deploy.pub deploy@your-server   # or append the .pub file to /home/deploy/.ssh/authorized_keys
ssh-keyscan -t ed25519 your-server                         # the server's host key, for DEPLOY_KNOWN_HOSTS
```

**One-time setup on GitHub** (repository **Settings → Secrets and variables → Actions**):

| Name | Kind | Value |
|---|---|---|
| `DEPLOY_SSH_KEY` | Secret | Contents of the private key file `quizabfahrt-deploy` |
| `DEPLOY_HOST` | Variable | Server address, e.g. `quiz.example.de` |
| `DEPLOY_USER` | Variable | `deploy` |
| `DEPLOY_KNOWN_HOSTS` | Variable | Output of the `ssh-keyscan` command above |
| `DEPLOY_URL` | Variable | `https://quiz.example.de` |

Then delete the local private key file. The deploy job runs in the GitHub environment `production`; under **Settings → Environments** you can add yourself as a required reviewer, so every deploy needs one more click to confirm.

**Checking on it:** `systemctl status quizabfahrt` shows whether it runs, `journalctl -u quizabfahrt -f` follows the logs.

**Notes:**

- Run a single instance: quizzes only exist in that process's memory.
- The app trusts `X-Forwarded-*` headers from proxies on the same machine or private network, so cookies get the `Secure` flag and the lobby shows the public join link. It only listens on `127.0.0.1`, so it can't be reached around Caddy.
- Live updates use Server-Sent Events on `/host/events` and `/play/events`. Caddy passes them through without extra configuration. With nginx instead, disable buffering for them (the app also sends `X-Accel-Buffering: no`) and set `proxy_read_timeout` above 30 seconds.
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

deploy/                     systemd service, Caddyfile, deploy script and sudoers rule for a server
.github/workflows/          CI/CD pipeline
```

Run the tests with `./gradlew test`.
