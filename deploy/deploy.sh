#!/usr/bin/env bash
set -euo pipefail

SERVER="${1:?Usage: deploy/deploy.sh user@server}"
cd "$(dirname "$0")/.."

./gradlew build
scp build/libs/QuizAbfahrt-0.0.1-SNAPSHOT.jar "$SERVER":/tmp/quizabfahrt.jar

echo "Restarting the app on $SERVER (this ends all running quizzes)..."
ssh -t "$SERVER" 'sudo install -m 644 /tmp/quizabfahrt.jar /opt/quizabfahrt/quizabfahrt.jar \
    && rm /tmp/quizabfahrt.jar \
    && sudo systemctl restart quizabfahrt \
    && systemctl --no-pager status quizabfahrt | head -5'
