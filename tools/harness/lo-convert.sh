#!/bin/sh
# 분리된 LibreOffice 프로필에 테스트 oxt 를 설치하고 문서를 변환한다 (사용자 프로필은 건드리지 않음).
#   사용법: tools/harness/lo-convert.sh <출력형식(odt|pdf|...)> <출력폴더> <입력파일...>
#   --reinstall 을 첫 인자로 주면 oxt 를 다시 설치한다 (build-oxt.sh 후 필요).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
LO_HOME="${LO_HOME:-C:/Program Files (x86)/LibreOffice}"
PROFILE_DIR="$REPO/tools/harness/out/lo-profile"
# file URL (Windows 경로)
PROFILE_URL="file:///$(cd "$REPO" && pwd -W)/tools/harness/out/lo-profile"
OXT="$(cd "$REPO" && pwd -W)/tools/harness/out/H2Orestart-test.oxt"

if [ "$1" = "--reinstall" ]; then
    shift
    "$LO_HOME/program/unopkg.com" remove ebandal.libreoffice.H2Orestart "-env:UserInstallation=$PROFILE_URL" >/dev/null 2>&1 || true
    rm -rf "$PROFILE_DIR"
fi
if [ ! -d "$PROFILE_DIR/user/uno_packages" ]; then
    mkdir -p "$PROFILE_DIR"
    "$LO_HOME/program/unopkg.com" add -f -s "$OXT" "-env:UserInstallation=$PROFILE_URL"
fi

FMT="$1"; OUTDIR="$2"; shift 2
mkdir -p "$OUTDIR"
"$LO_HOME/program/soffice.com" --headless --norestore "-env:UserInstallation=$PROFILE_URL" \
    --convert-to "$FMT" --outdir "$OUTDIR" "$@"
