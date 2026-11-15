#!/bin/sh
# 비교 하네스 1단계: HWPX 말뭉치 스캔
#   사용법: tools/harness/scan.sh [말뭉치 폴더] [추가 옵션...]
#   기본 말뭉치: D:/eclipse-workspace/HWP, 출력: tools/harness/work/scan-<시각>/
# JRE 8 호환을 위해 --release 8 로 컴파일한다.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CORPUS="${1:-D:/eclipse-workspace/HWP}"
[ $# -gt 0 ] && shift
# Windows(Git Bash)에서 javac 가 /d/... 경로를 못 읽으므로 저장소 기준 상대 경로를 쓴다
cd "$REPO"
CLASSES="tools/harness/out/classes"

rm -rf "$CLASSES"
mkdir -p "$CLASSES"
find source/HwpDoc source/HwpxWriter tools/harness/src -name '*.java' > tools/harness/out/sources.txt
javac --release 8 -encoding UTF-8 -nowarn -d "$CLASSES" @tools/harness/out/sources.txt

java -Xmx2g -cp "$CLASSES" harness.CorpusScan --root "$CORPUS" --out tools/harness/work "$@"
