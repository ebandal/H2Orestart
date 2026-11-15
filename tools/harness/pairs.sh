#!/bin/sh
# HWP / HWPX 쌍 모델 비교
#   기본: 말뭉치 폴더 안의 같은 이름 쌍 + hwp2hwpx.ps1 출력(work/hwp2hwpx)과 원본 HWP 의 쌍
#   사용법: tools/harness/pairs.sh [말뭉치 폴더]
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CORPUS="${1:-D:/eclipse-workspace/HWP}"
cd "$REPO"
CLASSES="tools/harness/out/classes"
mkdir -p "$CLASSES"
find source/HwpDoc source/HwpxWriter tools/harness/src -name '*.java' > tools/harness/out/sources.txt
javac --release 8 -encoding UTF-8 -nowarn -d "$CLASSES" @tools/harness/out/sources.txt

ARGS="--same-dir $CORPUS"
if [ -d tools/harness/work/hwp2hwpx ]; then
    ARGS="$ARGS --mirror $CORPUS tools/harness/work/hwp2hwpx"
fi
java -Xmx2g -cp "$CLASSES" harness.PairCompare $ARGS --out tools/harness/work
