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
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;

public class HwpRecord_FaceName extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_FaceName.class.getName());
	private HwpDocInfo	parent;

	public boolean		basicFaceExists;	// 속성 - 기본글꼴 존재여부
	public boolean		attrExists;			// 속성 - 글꼴 유형정보 존재여부
	public boolean		substExists;		// 속성 - 대체 글꼴 존재 여부
	
	public String 		faceName;			// 글꼴 이름
	public AltType		substType;			// 대체 글꼴 유형
	public String 		substFace;     		// 대체 글꼴 이름
	public String		basicFaceName;		// 기본 글꼴 이름
	
	public byte			familyType;			// 글꼴 유형정보 - 글꼴 계열
	public byte			serifStyle;			// 글꼴 유형정보 - 세리프 유형
	public short		weight;				// 글꼴 유형정보 - 굵기
	public short		propotion;			// 글꼴 유형정보 - 비례
	public short		contrast;			// 글꼴 유형정보 - 대조
	public short		strokeVariation;	// 글꼴 유형정보 - 스트로크 편차
	public short		armStyle;			// 글꼴 유형정보 - 자획유형
	public short		letterform;			// 글꼴 유형정보 - 글자형
	public short		midLine;			// 글꼴 유형정보 - 중간선
	public short 		xHeight;			// 글꼴 유형정보 - X-높이
	
	HwpRecord_FaceName(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public HwpRecord_FaceName(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		this(tagNum, level, size);
		this.parent = docInfo;

		int offset = off;
		basicFaceExists = (buf[offset]&0x20)==0x20?true:false;
		attrExists 		= (buf[offset]&0x40)==0x40?true:false;
		substExists	= (buf[offset]&0x80)==0x80?true:false;
		offset += 1;
		
		int faceNameLen = 0;
		faceNameLen = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2;
		offset += 2;
		if (faceNameLen > 0) {
			faceName = new String(buf, offset, faceNameLen, StandardCharsets.UTF_16LE);
			offset += faceNameLen;
		}
		if (substExists) {
		    substType = AltType.from(buf[offset++]&0x0F);
			
			faceNameLen = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2;
			offset += 2;
			if (faceNameLen > 0) {
			    substFace = new String(buf, offset, faceNameLen, StandardCharsets.UTF_16LE);
				offset += faceNameLen;
			}
		}
		
		if (attrExists) {
			familyType		= buf[offset++];	// 글꼴 유형정보 - 글꼴 계열
			serifStyle		= buf[offset++];	// 글꼴 유형정보 - 세리프 유형
			weight			= buf[offset++];	// 글꼴 유형정보 - 굵기
			propotion		= buf[offset++];	// 글꼴 유형정보 - 비례
			contrast		= buf[offset++];	// 글꼴 유형정보 - 대조
			strokeVariation	= buf[offset++];	// 글꼴 유형정보 - 스트로크 편차
			armStyle		= buf[offset++];	// 글꼴 유형정보 - 자획유형
			letterform		= buf[offset++];	// 글꼴 유형정보 - 글자형
			midLine			= buf[offset++];	// 글꼴 유형정보 - 중간선
			xHeight			= buf[offset++];	// 글꼴 유형정보 - X-높이
		}
		
		if (basicFaceExists) {
			faceNameLen = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2;
			offset += 2;
			if (faceNameLen > 0) {
				basicFaceName = new String(buf, offset, faceNameLen, StandardCharsets.UTF_16LE);
				offset += faceNameLen;
			}
		}
		log.fine("                                                  "
				+"ID="+(parent.faceNameList.size())
				+",Nm="+faceName
				+(basicFaceName==null?"":",Basic="+basicFaceName)
				+(substExists?",Alt="+substFace+( substType==null?"":"("+substType.toString()+")"):"")
				+(attrExists==false?"":",계열="+familyType)
				+(attrExists==false?"":",세리프="+serifStyle)
				+(attrExists==false?"":",굵기="+weight)
				+(attrExists==false?"":",비례="+propotion)
				+(attrExists==false?"":",대조="+contrast)
				+(attrExists==false?"":",스트로크편차="+strokeVariation)
				+(attrExists==false?"":",자획유형="+armStyle)
				+(attrExists==false?"":",글자형="+letterform)
				+(attrExists==false?"":",중간선="+midLine)
				+(attrExists==false?"":",X높이="+xHeight));
		
		if (offset-off-size != 0) {
			throw new HwpParseException();
		}
	}

	public HwpRecord_FaceName(HwpDocInfo docInfo, Node node, int version) throws HwpParseException {
        super(HwpTag.HWPTAG_FACE_NAME, 0, 0);
        this.parent = docInfo;

        dumpNode(node, 1);
        
        // TagName = hh:font
        // attributes = [face="돋움", id="0", isEmbedded="0", type="TTF"]
        // children = hh:typeInfo
        //            [armStyle="1", contrast="0", familyType="FCAT_GOTHIC", letterform="1", midline="1", proportion="0", strokeVariation="1", weight="6", xHeight="1"]
        
        NamedNodeMap childAttrs = node.getAttributes();
        for (int j=0; j<childAttrs.getLength(); j++) {
            Node childAttr = childAttrs.item(j);
            
            switch(childAttr.getNodeName()) {
            case "id":
                break;
            case "face":
                faceName = childAttr.getNodeValue();
                break;
            case "type":
                break;
            case "isEmbedded":
                break;
            case "binaryItemIDRef":
                break;
            }
        }
        
        NodeList grandChildren = node.getChildNodes();
        for (int j=0; j<grandChildren.getLength(); j++) {
            Node grandChild = grandChildren.item(j);
            
            if (grandChild.getNodeName().equals("hh:substFont")) {
                substExists = true;
                NamedNodeMap grandChildAttrs = grandChild.getAttributes();
                for (int k=0; k<grandChildAttrs.getLength(); k++) {
                    Node substAttr = grandChildAttrs.item(k);
                    
                    switch(substAttr.getNodeName()) {
                    case "face":
                        substFace = substAttr.getNodeValue();
                        break;
                    case "type":
                        if (substAttr.getNodeValue().equals("TTF")) {
                            substType = AltType.FFT;
                        } else if (substAttr.getNodeValue().equals("HFT")) {
                            substType = AltType.HFT;
                        }
                        break;
                    case "isEmbedded":
                        break;
                    case "binaryItemIDRef":
                        break;
                    default:
                        throw new HwpParseException();
                    }
                }
            } else if (grandChild.getNodeName().equals("hh:typeInfo")) {
                attrExists = true;
                NamedNodeMap grandChildAttrs = grandChild.getAttributes();
                for (int k=0; k<grandChildAttrs.getLength(); k++) {
                    Node substAttr = grandChildAttrs.item(k);
                    switch(substAttr.getNodeName()) {
                    case "familyType":
                        basicFaceExists = true;
                        basicFaceName = substAttr.getNodeValue();
                        break;
                    case "serifStyle":
                        serifStyle = Byte.parseByte(substAttr.getNodeValue());
                        break;
                    case "weight":
                        weight = Short.parseShort(substAttr.getNodeValue());
                        break;
                    case "proportion":
                        propotion = Short.parseShort(substAttr.getNodeValue());
                        break;
                    case "contrast":
                        contrast = Short.parseShort(substAttr.getNodeValue());
                        break;
                    case "strokeVariation":
                        strokeVariation = Short.parseShort(substAttr.getNodeValue());
                        break;
                    case "armStyle":
                        armStyle = Short.parseShort(substAttr.getNodeValue());
                        break;
                    case "letterform":
                        letterform = Short.parseShort(substAttr.getNodeValue());
                        break;
                    case "midline":
                        midLine = Short.parseShort(substAttr.getNodeValue());
                        break;
                    case "xHeight":
                        xHeight = Short.parseShort(substAttr.getNodeValue());
                        break;
                    default:
                        log.severe("unhandled attribute name=" + substAttr.getNodeName());
                        throw new HwpParseException();
                    }
                }
            }
        }
    }

    public static enum AltType {
		UNKNOWN		(0x0),
		FFT			(0x1),
		HFT			(0x2);
		
		private int alt;
		
	    private AltType(int alt) { 
	    	this.alt = alt;
	    }

	    public static AltType from(int alt) {
	    	for (AltType altNum: values()) {
	    		if (altNum.alt == alt)
	    			return altNum;
	    	}
	    	return null;
	    }
	}
}
