#!/bin/sh
# 테스트용 확장(oxt) 빌드: 현재 소스를 --release 8 로 컴파일해 dist/H2Orestart.oxt 의 jar 만 교체한다.
#   결과: tools/harness/out/H2Orestart-test.oxt
#   LO_HOME 환경변수로 LibreOffice 설치 경로를 바꿀 수 있다.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
cd "$REPO"
LO_HOME="${LO_HOME:-C:/Program Files (x86)/LibreOffice}"
LO_CP="$LO_HOME/program/classes/libreoffice.jar"
OUT="tools/harness/out"
CLS="$OUT/oxt-classes"

rm -rf "$CLS" "$OUT/oxt-stage"
mkdir -p "$CLS" "$OUT/oxt-stage"
# 확장에 들어가는 소스 (junit 테스트, working 은 제외)
find source -name '*.java' \
    -not -path 'source/ebandal/libreoffice/comp/tests/*' \
    -not -path 'source/working/*' > "$OUT/oxt-sources.txt"
javac --release 8 -encoding UTF-8 -nowarn -cp "$LO_CP;build" -d "$CLS" @"$OUT/oxt-sources.txt"
cp -r build/ebandal "$CLS/" 2>/dev/null || true
cp source/ebandal/libreoffice/comp/RegistrationHandler.classes "$CLS/ebandal/libreoffice/comp/"

printf 'Manifest-Version: 1.0\nRegistrationClassName: ebandal.libreoffice.comp.RegistrationHandler\nClass-Path: \n' > "$OUT/MANIFEST.MF"
jar cfm "$OUT/oxt-stage/H2Orestart.jar" "$OUT/MANIFEST.MF" -C "$CLS" .

# dist oxt 를 복사하고 jar 만 교체
cp dist/H2Orestart.oxt "$OUT/H2Orestart-test.oxt"
(cd "$OUT/oxt-stage" && jar uf ../H2Orestart-test.oxt H2Orestart.jar)
echo "built: $OUT/H2Orestart-test.oxt"
