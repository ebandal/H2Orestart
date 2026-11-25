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
package HwpDoc.HwpElement;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecordTypes.NumberShape2;

public class HwpRecord_Numbering extends HwpRecord {
    private static final Logger log = Logger.getLogger(HwpRecord_Numbering.class.getName());
    private HwpDocInfo	parent;

    public Numbering[]	numbering = new Numbering[10];       // 문단머리정보+번호형식[1~7]
    public short        start;                               // 시작번호
    public String[]     extLevelFormat	= new String[3];     // 확장 번호 형식
    public int[]        extLevelStart 	= { 1, 1, 1 };       // 확장 수준별 시작번호 (없으면 1. 기존 0 은 번호가 0 부터 시작)
	
    HwpRecord_Numbering(int tagNum, int level, int size) {
        super(tagNum, level, size);
    }
	
    public HwpRecord_Numbering(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        this(tagNum, level, size);
        this.parent = docInfo;

        int offset = off;
        for (int i=0; i < 7; i++) {
            numbering[i] = new Numbering();
            offset = readLevel(numbering[i], buf, offset);
        }

        // <numbering>의 "start" 속성에 대응
        start 	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;

        // 수준별 시작번호 (명세: 5.0.2.5 이상). 버전이 아니라 레코드에 남은 크기로 판단한다
        // (5.0.2.4 문서에도 들어 있는 경우가 있고, 5.0.3.3 문서에 없는 경우도 있음)
        if (offset-off+4*7 <= size) {
            for (int i=0; i < 7; i++) {
                numbering[i].startNumber = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                offset += 4;
            }
        } else {
            // 없으면 한글은 1수준을 start(0 이면 1), 나머지 수준을 1 로 본다 (HWP->HWPX 변환 쌍에서 확인)
            for (int i=0; i < 7; i++) {
                numbering[i].startNumber = 1;
            }
            if (start > 0) {
                numbering[0].startNumber = start;
            }
        }

        // 확장 수준(8~10). 구조는 1~7 수준과 같다 (문단 머리 정보 12 bytes + 번호 형식)
        // 명세 표 38 에는 번호 형식만 적혀 있고, 표 39 의 문단 머리 정보 길이(8)도 실제(12)와 다르다
        if (offset-off+14*3 <= size) {
            for (int i=0; i < 3; i++) {
                numbering[7+i] = new Numbering();
                offset = readLevel(numbering[7+i], buf, offset);
                numbering[7+i].startNumber = 1;
                extLevelFormat[i] = numbering[7+i].numFormat;
                extLevelStart[i] = 1;
            }
            if (offset-off+4*3 <= size) {
                for (int i=0; i < 3; i++) {
                    extLevelStart[i] = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                    numbering[7+i].startNumber = extLevelStart[i];
                    offset += 4;
                }
            }
        }

        log.fine("                                                  "
                +"ID="+(parent.numberingList.size()+1)
                +",포맷1="+numbering[0].numFormat
                    +(numbering[0].charShape!=-1?"("+((HwpRecord_CharShape)(parent.charShapeList.get(numbering[0].charShape))).fontName[0]+")":"")
                +",포맷2="+numbering[1].numFormat
                    +(numbering[1].charShape!=-1?"("+((HwpRecord_CharShape)(parent.charShapeList.get(numbering[1].charShape))).fontName[0]+")":"")
                +",포맷3="+numbering[2].numFormat
                    +(numbering[2].charShape!=-1?"("+((HwpRecord_CharShape)(parent.charShapeList.get(numbering[2].charShape))).fontName[0]+")":"")
                +",포맷4="+numbering[3].numFormat
                    +(numbering[3].charShape!=-1?"("+((HwpRecord_CharShape)(parent.charShapeList.get(numbering[3].charShape))).fontName[0]+")":"")
                +",포맷5="+numbering[4].numFormat
                    +(numbering[4].charShape!=-1?"("+((HwpRecord_CharShape)(parent.charShapeList.get(numbering[4].charShape))).fontName[0]+")":"")
                +",포맷6="+numbering[5].numFormat
                    +(numbering[5].charShape!=-1?"("+((HwpRecord_CharShape)(parent.charShapeList.get(numbering[5].charShape))).fontName[0]+")":"")
                +",포맷7="+numbering[6].numFormat
                    +(numbering[6].charShape!=-1?"("+((HwpRecord_CharShape)(parent.charShapeList.get(numbering[6].charShape))).fontName[0]+")":"")	
                +",시작번호="+start
                +",수준별시작번호=("+numbering[0].startNumber+","+numbering[1].startNumber+","+numbering[2].startNumber+","
                               +numbering[3].startNumber+","+numbering[4].startNumber+","+numbering[5].startNumber+","+numbering[6].startNumber+")"
                );

        if (offset-off-size != 0) {
            dump(buf, off, size);
            // (한컴 작성 아닌 문서? AI 작성?) 파일형식이 틀려도 예외로 처리하지 않도록 함. 한컴에서도 파일형식이 틀리다고 멈추진 안음.
            // throw new HwpParseException(parseError(buf, off, size, offset));
        }
    }
	
    /** 문단 머리 정보(12 bytes) + 번호 형식 길이(2) + 번호 형식 */
    private static int readLevel(Numbering numb, byte[] buf, int offset) {
        int typeBits            = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;

        numb.attrBits       = typeBits;
        // bit5~ : 번호 모양 (명세 표 41 값. 표 40 에는 없지만 HWP/HWPX 쌍 문서로 확인)
        numb.numberShape    = NumberShape2.from((typeBits>>>5) & 0x1F);
        numb.align          = (byte) ((typeBits)&0x03);
        numb.useInstWidth   = (typeBits&0x04)==0x04?true:false;
        numb.autoIndent     = (typeBits&0x08)==0x08?true:false;
        numb.textOffsetType = (byte) ((typeBits>>>4)&0x01);

        numb.widthAdjust    = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        numb.textOffset     = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        numb.charShape      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;

        int len = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2;
        offset += 2;
        numb.numFormat      = new String(buf, offset, len, StandardCharsets.UTF_16LE);
        offset += len;
        return offset;
    }

    public HwpRecord_Numbering(HwpDocInfo docInfo, Node node, int version) throws NotImplementedException {
        super(HwpTag.HWPTAG_NUMBERING, 0, 0);
        this.parent = docInfo;
        
        NamedNodeMap attributes = node.getAttributes();

        // id값은 처리하지 않는다. List<HwpRecord_CharShape>에 순차적으로 추가한다.
        // String id = attributes.getNamedItem("height").getNodeValue();
        
        start = 1;
        String numStr = attributes.getNamedItem("start").getNodeValue();
        start = (short) Integer.parseInt(numStr);
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0,j=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "hh:paraHead":
            case "paraHead":
                {
                    numbering[j] = new Numbering();
                    
                    NamedNodeMap childAttrs = child.getAttributes();
                    if (childAttrs.getNamedItem("align")!=null) {
                        switch(childAttrs.getNamedItem("align").getNodeValue()) {
                        case "LEFT":
                            numbering[j].align = 0;
                            break;
                        case "RIGHT":
                            numbering[j].align = 2;
                            break;
                        case "CENTER":
                            numbering[j].align = 1;
                            break;
                        }
                    }

                    switch(childAttrs.getNamedItem("useInstWidth").getNodeValue()) {
                    case "0":
                        numbering[j].useInstWidth = false;
                        break;
                    case "1":
                        numbering[j].useInstWidth = true;
                        break;
                    }
                    
                    switch(childAttrs.getNamedItem("autoIndent").getNodeValue()) {
                    case "0":
                        numbering[j].autoIndent = false;
                        break;
                    case "1":
                        numbering[j].autoIndent = true;
                        break;
                    }
                    
                    numStr = childAttrs.getNamedItem("widthAdjust").getNodeValue();
                    numbering[j].widthAdjust = (short)Integer.parseInt(numStr);

                    switch(childAttrs.getNamedItem("textOffsetType").getNodeValue()) {
                    case "PERCENT":
                        numbering[j].textOffsetType = 0;
                        break;
                    case "HWPUNIT":
                        numbering[j].textOffsetType = 1;
                        break;
                    }
                    
                    numStr = childAttrs.getNamedItem("textOffset").getNodeValue();
                    numbering[j].textOffset = Short.parseShort(numStr);

                    // 번호 모양 (DIGIT, HANGUL_SYLLABLE, ...)
                    numbering[j].numberShape = NumberShape2.fromXml(childAttrs.getNamedItem("numFormat").getNodeValue());
                    // 번호 형식 문자열은 요소의 텍스트 (예: "^1.", "^3)", "(^5)", "^1.^2."). 비어 있으면 번호를 표시하지 않음
                    String formatText = child.getTextContent();
                    numbering[j].numFormat = formatText==null ? "" : formatText;

                    numStr = childAttrs.getNamedItem("charPrIDRef").getNodeValue();
                    numbering[j].charShape = (short)Integer.parseUnsignedInt(numStr);

                    numStr = childAttrs.getNamedItem("start").getNodeValue();
                    numbering[j].startNumber = (short)Integer.parseInt(numStr);
                    if (j >= 7 && j < 10) {     // 확장 수준: 변환기는 extLevel* 를 쓴다
                        extLevelFormat[j-7] = numbering[j].numFormat;
                        extLevelStart[j-7] = numbering[j].startNumber;
                    }
                    j++;
                }
            }
        }
    }

    public static class ParaHeadInfo {
        public int          attrBits;               // (HWP) 문단 머리 정보 속성 원본 비트
        public byte         align;                  // 문단머리정보 - 문단의 정렬 종류 - 기본글꼴 존재여부
        public boolean      useInstWidth;           // 문단머리정보 - 번호 너비를 실제 인스턴스 문자열의 너비에 따를지 여부
        public boolean      autoIndent;             // 문단머리정보 - 자동 내어 쓰기 여부
        public byte         textOffsetType;         // 문단머리정보 - 수준별 본문과의 거리 종류
        public short        widthAdjust;            // 문단머리정보 - 너비 보정값
        public short        textOffset;             // 문단머리정보 - 본문과의 거리
        public int          charShape;              // 문단머리정보 - 글자 모양 아이디 참조
        public int          startNumber;            // 사용자 지정 문단 시작번호
    }
	
    public static class Numbering extends ParaHeadInfo {
        public String       numFormat;               // 번호 형식
        public NumberShape2 numberShape;             // 번호 모양 (HWPX 에서만 설정. null 이면 numFormat 으로 추정)
    }

}
