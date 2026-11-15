#!/bin/sh
# 왕복 비교: 한글 2022(xmlVersion 1.5) HWPX 를 LibreOffice 로 열어 HWPX 로 저장(Hwpx_Writer)한다.
#   gen2/ : 1세대 결과를 다시 열어 저장한 2세대와 1세대를 비교한다 (저장/읽기를 반복해도 같은 화면인지. 주 기준)
#   ./    : 1세대 결과를 원본과 비교한다 (참고. 가져오기가 화면용으로 바꾼 값은 원본과 다를 수 있다)
#   사용법: tools/harness/roundtrip.sh [말뭉치 폴더] [최대 문서 수]
#   출력: tools/harness/work/rt-<시각>/ (in/, out/, pairs.tsv, summary.tsv, diffs.tsv, docs/)
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CORPUS="${1:-D:/eclipse-workspace/HWP}"
LIMIT="${2:-0}"
cd "$REPO"
RUN="tools/harness/work/rt-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN/in" "$RUN/out"

# 1. 대상 선택: version.xml 의 xmlVersion 이 1.5 인 HWPX. LibreOffice 경로 문제를 피하려고 ASCII 이름으로 복사
python -I - "$CORPUS" "$RUN" "$LIMIT" <<'EOF'
import glob, os, shutil, sys, zipfile
corpus, run, limit = sys.argv[1], sys.argv[2], int(sys.argv[3])
files = []
for x in sorted(glob.glob(os.path.join(corpus, '**', '*.hwpx'), recursive=True)):
    try:
        if b'xmlVersion="1.5"' in zipfile.ZipFile(x).read('version.xml'):
            files.append(x)
    except Exception:
        pass
if limit > 0:
    files = files[:limit]
with open(os.path.join(run, 'pairs.tsv'), 'w', encoding='utf-8') as o:
    for i, x in enumerate(files):
        name = '%03d.hwpx' % (i + 1)
        shutil.copyfile(x, os.path.join(run, 'in', name))
        o.write('%s\t%s\n' % (x, os.path.abspath(os.path.join(run, 'out', name))))
print('selected', len(files))
EOF

# 2. 내보내기 (테스트 oxt 를 다시 설치한 격리 프로필 사용)
tools/harness/build-oxt.sh
tools/harness/lo-convert.sh --reinstall 'hwpx:Hwpx_Writer' "$RUN/out" "$RUN"/in/*.hwpx > "$RUN/lo-convert.log" 2>&1 || true
echo "exported: $(ls "$RUN/out" | wc -l)"

# 3. 비교
CLASSES="tools/harness/out/classes"
mkdir -p "$CLASSES"
find source/HwpDoc source/HwpxWriter tools/harness/src -name '*.java' > tools/harness/out/sources.txt
javac --release 8 -encoding UTF-8 -nowarn -d "$CLASSES" @tools/harness/out/sources.txt
java -Xmx2g -cp "$CLASSES" harness.RoundTripCompare "$RUN/pairs.tsv" "$RUN" > "$RUN/compare.log" 2>&1 || true
tail -1 "$RUN/compare.log"

# 4. 2세대: 1세대 결과를 다시 열어 저장하고 1세대와 비교
mkdir -p "$RUN/out2" "$RUN/gen2"
tools/harness/lo-convert.sh 'hwpx:Hwpx_Writer' "$RUN/out2" "$RUN"/out/*.hwpx > "$RUN/lo-convert2.log" 2>&1 || true
python -I - "$RUN" <<'EOF2'
import os, sys
run = sys.argv[1]
with open(os.path.join(run, 'pairs.tsv'), encoding='utf-8') as f, open(os.path.join(run, 'gen2', 'pairs.tsv'), 'w', encoding='utf-8') as o:
    for line in f:
        orig, out1 = line.rstrip(chr(10)).split(chr(9))
        out2 = os.path.join(os.path.dirname(os.path.dirname(out1)), 'out2', os.path.basename(out1))
        o.write(out1 + chr(9) + os.path.abspath(out2) + chr(10))
EOF2
java -Xmx2g -cp "$CLASSES" harness.RoundTripCompare "$RUN/gen2/pairs.tsv" "$RUN/gen2" > "$RUN/gen2/compare.log" 2>&1 || true
echo "gen2: $(tail -1 "$RUN/gen2/compare.log")"
echo "run: $RUN"
