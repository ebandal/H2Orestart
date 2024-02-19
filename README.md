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

## 버전정보
[Release](https://github.com/ebandal/H2Orestart/releases)에 별도 표기합니다.


## 라이선스
소스코드는 GNU GPLv3 라이선스로 공개합니다.
