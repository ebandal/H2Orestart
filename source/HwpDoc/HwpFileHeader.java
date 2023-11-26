/* Copyright (C) 2023 ebandal
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */
/* 본 제품은 한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다.
 * 개방형 워드프로세서 마크업 언어(OWPML) 문서 구조 KS X 6101:2018 문서를 참고하였습니다.
 * 작성자 : 반희수 ebandal@gmail.com  
 * 작성일 : 2022.10
 */
package HwpDoc;

import java.nio.charset.StandardCharsets;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class HwpFileHeader {
	public	String signature;
	public	String version;
	public	boolean bCompressed;		// 압축여부
	boolean bPasswordEncrypted;			// 암호설정여부
	public	boolean bDistributable;		// 배포용 문서 여부
	boolean bSaveScript;				// 스크립트 저장 여부
	boolean bDRMprotected;				// DRM 보안 문서 여부
	boolean bHasXMLTemplateStorage;		// XMLTemplate 스토리지 존재 여부
	boolean bHasDocumentHistory;		// 문서 이력 관리 존재 여부
	boolean bHasPkiSignature;			// 전자 서명 정보 존재 여부
	boolean bPkiEncrypted;				// 공인 인증서 암호화 여부
	boolean bReservePkiSignature;		// 전자 서명 예비 저장 여부
	boolean bPkiCertificateDRM;			// 공인 인증서 DRM 보안 문서 여부
	boolean bCCLDocument;				// CCL 문서 여부
	boolean bMobileOptimized;			// 모바일 최적화 여부
	boolean bPrivateInformation;		// 개인 정보 보안 문서 여부
	boolean bModifyTracking;			// 변경 추적 문서 여부
	boolean bCopyrightKOGL;				// 공공누리(KOGL) 저작권 문서
	boolean bHasVideoControl;			// 비디오 컨트롤 포함 여부
	boolean bHasMarkFieldControl;		// 차례 필드 컨트롤 포함 여부
	
	boolean bCopyrighted;				// CCL, 공공누리 라이선스 정보
	boolean bCopyProhibited;			// 복제 제한 여부
	boolean bCopyPermitted;				// 동일 조건 하에 복제 허가 여부
	
	int encryptVersion;
	int countryKOGLLicensed;			// 공공누리(KOGL) 라이선스 지원 국가
	
	public HwpFileHeader() {
	}
	
    // Compound형식 (hwp)
	boolean parse(byte[] buf) throws HwpDetectException {
		int offset = 0;
		signature = new String(buf, offset, 32, StandardCharsets.US_ASCII);
		if (signature.trim().equals("HWP Document File")==false) {
			throw new HwpDetectException(ErrCode.SIGANTURE_NOT_MATCH);
		}
		offset += 32;
		version = new Integer(buf[offset+3]).toString() + new Integer(buf[offset+2]).toString() + new Integer(buf[offset+1]).toString() + new Integer(buf[offset+0]).toString();
		offset += 4;
		bCompressed 			= (buf[offset]&0x01)==0x01?true:false;
		bPasswordEncrypted		= (buf[offset]&0x02)==0x02?true:false;
		bDistributable			= (buf[offset]&0x04)==0x04?true:false;
		bSaveScript				= (buf[offset]&0x08)==0x08?true:false;
		bDRMprotected			= (buf[offset]&0x10)==0x10?true:false;
		bHasXMLTemplateStorage	= (buf[offset]&0x20)==0x20?true:false;
		bHasDocumentHistory		= (buf[offset]&0x40)==0x40?true:false;
		bHasPkiSignature		= (buf[offset]&0x80)==0x80?true:false;
		bPkiEncrypted			= (buf[offset+1]&0x01)==0x01?true:false;
		bReservePkiSignature	= (buf[offset+1]&0x02)==0x02?true:false;		// 전자 서명 예비 저장 여부
		bPkiCertificateDRM		= (buf[offset+1]&0x04)==0x04?true:false;
		bCCLDocument			= (buf[offset+1]&0x08)==0x08?true:false;
		bMobileOptimized		= (buf[offset+1]&0x10)==0x10?true:false;
		bPrivateInformation		= (buf[offset+1]&0x20)==0x20?true:false;
		bModifyTracking			= (buf[offset+1]&0x40)==0x40?true:false;
		bCopyrightKOGL			= (buf[offset+1]&0x80)==0x80?true:false;
		bHasVideoControl 		= (buf[offset+2]&0x01)==0x01?true:false;
		bHasMarkFieldControl	= (buf[offset+2]&0x02)==0x02?true:false;
		offset += 4;
		bCopyrighted 			= (buf[offset]&0x01)==0x01?true:false;
		bCopyProhibited			= (buf[offset]&0x02)==0x02?true:false;
		bCopyPermitted			= (buf[offset]&0x04)==0x04?true:false;
		offset += 4;
		encryptVersion = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		countryKOGLLicensed		= (int)buf[offset];
		
		return true;
	}
	
	// Owpml형식 (hwpx)
	boolean parse(Document document) {
        Element element = document.getDocumentElement();
        
        String docVersion = element.getAttribute("major");
        docVersion += element.getAttribute("minor");
        docVersion += element.getAttribute("micro");
        docVersion += element.getAttribute("buildNumber");
        
        version = docVersion;
        /*
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) node;
                String firstname = elem.getElementsByTagName("firstname")
                        .item(0).getChildNodes().item(0).getNodeValue();
                String lastname = elem.getElementsByTagName("lastname").item(0)
                        .getChildNodes().item(0).getNodeValue();
                Double salary = Double.parseDouble(elem.getElementsByTagName("salary")
                        .item(0).getChildNodes().item(0).getNodeValue());
            }
        }
        */
        
        
        return true;
	}

}
