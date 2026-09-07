## H2O restart

한컴오피스의 한글파일을 LibreOffice에서 읽을 수 있는 확장 바이너리입니다.

바이너리를 다운로드 받아서 LibreOffice를 실행시키고, "확장 관리자"에서 추가를 하면 됩니다.

확장을 추가한 후에는 
- 파일 열기창에서 "Hwp2002_Reader (*.hwpx)" 파일 유형을 필터링하거나, 
- hwpx파일을 끌어오기를 하여

hwpx파일을 OpenDocumentText (ODT)형식으로 변환 할 수 있습니다.

저장은 ODT 형식으로만 저장할 수 있습니다.

확장을 설치하면 LibreOffice headless 명령으로 한글파일을 PDF로 변환할 수 있습니다.
```
예1) $ soffice.exe --headless --infilter="Hwp2002_File" --convert-to pdf:writer_pdf_Export YOUR_HANCOM_FILE
예2) $ soffice.exe --headless --convert-to pdf:writer_pdf_Export YOUR_HANCOM_FILE
```

* 확장 바이너리의 사용은 무료이며, 자유롭게 사용하시면 됩니다.
* 오류나 불편사항은 이 github의 issue에 등록해주시면 주기적으로 개선하겠습니다.

## 설치
### LibreOffice Extension
https://extensions.libreoffice.org/en/extensions/show/27504

### ArchLinux (AUR)
https://aur.archlinux.org/packages/libreoffice-extension-h2orestart

### Debian / Ubuntu
```sh
sudo apt install libreoffice-h2orestart
```
https://packages.debian.org/h2orestart
https://packages.ubuntu.com/h2orestart

### 직접 설치 (Manual installation)
Release에서 직접 oxt 파일 다운로드 후 LibreOffice 확장 관리자를 통해 설치

## 버그 신고

변환에 실패하거나 결과가 이상하면, 1) 아래 로그 폴더를 **통째로 압축해서**, 2) 원본 한컴 파일을 issue 에 첨부해주세요.
예외가 발생한 위치와 원인(레코드 태그, 속성명, 속성값, 원본 바이트)이 로그에 기록됩니다.

| OS | 로그 폴더 |
|---|---|
| Linux | `$XDG_CACHE_HOME/H2Orestart/` 또는 `~/.cache/H2Orestart/` |
| macOS | `~/Library/Caches/H2Orestart/` |
| Windows | `%USERPROFILE%\.H2Orestart\` |

문서를 열지 못한 경우, 로그에는 파싱에 실패한 지점의 정보가 됩니다. 문서 내용 일부가 로그에 남을 수 있으니, 민감한 문서라면 첨부 전에 확인해주세요.
원본 한컴 파일도 첨부 전에 개인정보, 민감정보를 삭제하고 첨부해주세요.

## 버전정보
[Release](https://github.com/ebandal/H2Orestart/releases)에 별도 표기합니다.


## 라이선스
소스코드는 GNU GPLv3 라이선스로 공개합니다.
