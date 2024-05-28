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

public class HwpRecord_Numbering extends HwpRecord {
    private static final Logger log = Logger.getLogger(HwpRecord_Numbering.class.getName());
    private HwpDocInfo	parent;

    public Numbering[]	numbering = new Numbering[10];       // 문단머리정보+번호형식[1~7]
    public short        start;                               // 시작번호
    public String[]     extLevelFormat	= new String[3];     // 확장 번호 형식
    public int[]        extLevelStart 	= new int[3];        // 확장 수준별 시작번호
	
    HwpRecord_Numbering(int tagNum, int level, int size) {
        super(tagNum, level, size);
    }
	
    public HwpRecord_Numbering(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        this(tagNum, level, size);
        this.parent = docInfo;

        int offset = off;
        for (int i=0; i < 7; i++) {
            numbering[i] = new Numbering();

            int typeBits                = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;

            numbering[i].align          = (byte) ((typeBits)&0x03);
            numbering[i].useInstWidth   = (typeBits&0x40)==0x40?true:false;
            numbering[i].autoIndent     = (typeBits&0x80)==0x80?true:false;
            numbering[i].textOffsetType = (byte) ((typeBits>>>4)&0x01);

            numbering[i].widthAdjust    = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
            numbering[i].textOffset     = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
            numbering[i].charShape      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;

            short len = (short) ((buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2);
            offset += 2;
            numbering[i].numFormat      = new String(buf, offset, len, StandardCharsets.UTF_16LE);
            offset += len;
        }

        // <numbering>의 "start" 속성에 대응
        start 	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;

        if (version > 5025 && offset-off < size) {
            // 하위 <paraHead>태그의 "start" 속성에 대응
            for (int i=0; i < 7; i++) {
                numbering[i].startNumber = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                offset += 4;
            }
        }

        if (version > 5100 && offset-off < size) {
            for (int i=0; i < 3; i++) {
                // 내용은 알수 없으나, 8byte를 포함하고 있음.
                offset += 8;

                // 내용은 알수 없으나, 4byte를 포함하고 있음.
                offset += 4;

                // 내용을 알수 없으나, 글자수를 포함한것으로 보임.
                short len = (short) ((buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2);
                offset += 2;

                // 글자수*2 만큼 건너뜀
                offset += len;
            }

            for (int i=0; i < 3; i++) {
                extLevelStart[i] = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                offset += 4;
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
            throw new HwpParseException();
        }
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

                    // level은 1수준~7수준을 의미
                    numStr = childAttrs.getNamedItem("level").getNodeValue();
                    short level = Short.parseShort(numStr);

                    switch(childAttrs.getNamedItem("numFormat").getNodeValue()) {
                    case "DIGIT":
                        switch(level) {
                        case 1:
                            numbering[j].numFormat = "^1.";
                            break;
                        case 2:
                            numbering[j].numFormat = "^2.";
                            break;
                        case 3:
                            numbering[j].numFormat = "^3.";
                            break;
                        case 4:
                            numbering[j].numFormat = "^4.";
                            break;
                        case 5:
                            numbering[j].numFormat = "^5.";
                            break;
                        case 6:
                            numbering[j].numFormat = "^6.";
                            break;
                        case 7:
                            numbering[j].numFormat = "^7.";
                            break;
                        case 8:
                            numbering[j].numFormat = "^8.";
                            break;
                        case 9:
                            numbering[j].numFormat = "^9.";
                            break;
                        case 10:
                            numbering[j].numFormat = "^10.";
                            break;
                        }
                        break;
                    case "HANGUL_SYLLABLE":
                    case "HANGUL_JAMO":
                        switch(level) {
                        case 1:
                            numbering[j].numFormat = "^가.";
                            break;
                        case 2:
                            numbering[j].numFormat = "^나.";
                            break;
                        case 3:
                            numbering[j].numFormat = "^다.";
                            break;
                        case 4:
                            numbering[j].numFormat = "^라.";
                            break;
                        case 5:
                            numbering[j].numFormat = "^마.";
                            break;
                        case 6:
                            numbering[j].numFormat = "^바.";
                            break;
                        case 7:
                            numbering[j].numFormat = "^사.";
                            break;
                        case 8:
                            numbering[j].numFormat = "^아.";
                            break;
                        case 9:
                            numbering[j].numFormat = "^자.";
                            break;
                        case 10:
                            numbering[j].numFormat = "^차.";
                            break;
                        }
                        break;
                    case "CIRCLED_DIGIT":
                        switch(level) {
                        case 1:
                            numbering[j].numFormat = "^\u2460.";
                            break;
                        case 2:
                            numbering[j].numFormat = "^\u2461.";
                            break;
                        case 3:
                            numbering[j].numFormat = "^\u2462.";
                            break;
                        case 4:
                            numbering[j].numFormat = "^\u2463.";
                            break;
                        case 5:
                            numbering[j].numFormat = "^\u2464.";
                            break;
                        case 6:
                            numbering[j].numFormat = "^\u2465.";
                            break;
                        case 7:
                            numbering[j].numFormat = "^\u2466.";
                            break;
                        case 8:
                            numbering[j].numFormat = "^\u2467.";
                            break;
                        case 9:
                            numbering[j].numFormat = "^\u2468.";
                            break;
                        case 10:
                            numbering[j].numFormat = "^\u2469.";
                            break;
                        }
                        break;
                    case "LATIN_SMALL":
                        switch(level) {
                        case 1:
                            numbering[j].numFormat = "^a.";
                            break;
                        case 2:
                            numbering[j].numFormat = "^b.";
                            break;
                        case 3:
                            numbering[j].numFormat = "^c.";
                            break;
                        case 4:
                            numbering[j].numFormat = "^d.";
                            break;
                        case 5:
                            numbering[j].numFormat = "^e.";
                            break;
                        case 6:
                            numbering[j].numFormat = "^f.";
                            break;
                        case 7:
                            numbering[j].numFormat = "^g.";
                            break;
                        case 8:
                            numbering[j].numFormat = "^h.";
                            break;
                        case 9:
                            numbering[j].numFormat = "^i.";
                            break;
                        case 10:
                            numbering[j].numFormat = "^j.";
                            break;
                        }
                        break;
                    case "CIRCLED_HANGUL_SYLLABLE":
                        switch(level) {
                        case 1:
                            numbering[j].numFormat = "^\u326E.";
                            break;
                        case 2:
                            numbering[j].numFormat = "^\u326F.";
                            break;
                        case 3:
                            numbering[j].numFormat = "^\u3270.";
                            break;
                        case 4:
                            numbering[j].numFormat = "^\u3271.";
                            break;
                        case 5:
                            numbering[j].numFormat = "^\u3272.";
                            break;
                        case 6:
                            numbering[j].numFormat = "^\u3273.";
                            break;
                        case 7:
                            numbering[j].numFormat = "^\u3274.";
                            break;
                        case 8:
                            numbering[j].numFormat = "^\u3275.";
                            break;
                        case 9:
                            numbering[j].numFormat = "^\u3276.";
                            break;
                        case 10:
                            numbering[j].numFormat = "^\u3277.";
                            break;
                        }
                        break;
                    case "ROMAN_SMALL":
                        switch(level) {
                        case 1:
                            numbering[j].numFormat = "^\u2170.";
                            break;
                        case 2:
                            numbering[j].numFormat = "^\u2171.";
                            break;
                        case 3:
                            numbering[j].numFormat = "^\u2172.";
                            break;
                        case 4:
                            numbering[j].numFormat = "^\u2173.";
                            break;
                        case 5:
                            numbering[j].numFormat = "^\u2174.";
                            break;
                        case 6:
                            numbering[j].numFormat = "^\u2175.";
                            break;
                        case 7:
                            numbering[j].numFormat = "^\u2176.";
                            break;
                        case 8:
                            numbering[j].numFormat = "^\u2177.";
                            break;
                        case 9:
                            numbering[j].numFormat = "^\u2178.";
                            break;
                        case 10:
                            numbering[j].numFormat = "^\u2179.";
                            break;
                        }
                        break;
                    default:
                        if (log.isLoggable(Level.FINE)) {
                            throw new NotImplementedException("HwpRecord_Numbering");
                        }
                        break;
                    }

                    numStr = childAttrs.getNamedItem("charPrIDRef").getNodeValue();
                    numbering[j].charShape = (short)Integer.parseUnsignedInt(numStr);

                    numStr = childAttrs.getNamedItem("start").getNodeValue();
                    numbering[j].startNumber = (short)Integer.parseInt(numStr);
                    j++;
                }
            }
        }
    }

    public static class ParaHeadInfo {
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
    }

}
