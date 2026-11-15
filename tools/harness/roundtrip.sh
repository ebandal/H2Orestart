#!/bin/sh
# 왕복 비교: 한글 2022(xmlVersion 1.5) HWPX 를 LibreOffice 로 열어 HWPX 로 저장(Hwpx_Writer)하고 원본과 비교한다.
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
echo "run: $RUN"
