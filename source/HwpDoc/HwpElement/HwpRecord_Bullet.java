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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.HwpElement.HwpRecord_Numbering.Numbering;

public class HwpRecord_Bullet extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_Bullet.class.getName());
	private HwpDocInfo	parent;

	public Numbering	headerInfo;							// 문단 머리의 정보
	public char			bulletChar;							// 글머리표 문자
	public int			bulletImage;						// 이미지 글머리표 여부 (글머리표:0, 이미지글머리표: ID)
															// 이미지 글머리
	public byte			bright;								// 	밝기
	public byte			contrast;							// 	대비
	public byte			imageEffect;						// 	효과
	public String		binItemRefID;						// 	ID
	public char			checkBulletChar;					// 체크 글머리표 문자
	
	HwpRecord_Bullet(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public HwpRecord_Bullet(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		this(tagNum, level, size);
		this.parent = docInfo;
		this.headerInfo = new Numbering();

		int offset = off;
		
		int typeBits	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		headerInfo.align	  	= (byte) ((typeBits)&0x03);
		headerInfo.useInstWidth		= (typeBits&0x40)==0x40?true:false;
		headerInfo.autoIndent		= (typeBits&0x80)==0x80?true:false;
		headerInfo.textOffsetType	= (byte) ((typeBits>>>4)&0x01);
		
		headerInfo.widthAdjust		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		headerInfo.textOffset		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		headerInfo.charShape		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		bulletChar					= ByteBuffer.wrap(buf, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getChar();
		offset += 2;
		
		if (size-(offset-off) > 0) {
    		bulletImage					= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
    		offset += 4;
		}
        if (size-(offset-off) > 0) {
            bright 						= buf[offset++];
        }
        if (size-(offset-off) > 0) {
            contrast 					= buf[offset++];
        }
        if (size-(offset-off) > 0) {
            imageEffect 				= buf[offset++];
        }
        if (size-(offset-off) > 0) {
            binItemRefID 				= String.valueOf(buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
            offset += 2;
        }
		// size가 23byte일 경우, 아래 2byte는 읽지 않도록 한다. 문서도 정확하지 않으니 이게 맞는 것인지는 알 수 없다.
		if (size-(offset-off) >0 ) {
			checkBulletChar				= ByteBuffer.wrap(buf, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getChar();
			offset += 2;
		}

		log.fine("                                                  "
				// +"ID="+(parent.bulletList.size())
				+"문단머리정보속성="+String.format("0x%08X", headerInfo.align)
				+(headerInfo.charShape!=-1?",글자모양="+((HwpRecord_CharShape)(parent.charShapeList.get(headerInfo.charShape))).fontName[0]:"")
				+",글머리표문자="+String.format("%c", bulletChar)
				+",글머리표="+(bulletImage==0?"글머리표":"이미지글머리표("+String.valueOf(bulletImage)+")")
				+(bulletImage==0?"":",이미지ID="+bulletImage)
				+",밝기="+bright
				+",대비="+contrast
				+(binItemRefID!=null?",BinData="+binItemRefID:"")
				+",체크글머리표문자="+String.format("%c", checkBulletChar) 	);
		
		if (offset-off-size!=0) {
			log.finest("[TAG]=" + tag.toString() + ", size=" + size + ", but currentSize=" + (offset-off));
			dump(buf, off, size);
			throw new HwpParseException();
		}
	}

    public HwpRecord_Bullet(HwpDocInfo docInfo, Node node, int version) throws HwpParseException {
        super(HwpTag.HWPTAG_BULLET, 0, 0);
        this.parent = docInfo;
        
        NamedNodeMap attributes = node.getAttributes();
        
        for (int i=0; i<attributes.getLength(); i++) {
        	// [char="", id="1", useImage="0"]
            Node attr = attributes.item(i);
            switch(attr.getNodeName()) {
            case "id":
            	// ID = Short.parseShort(attr.getNodeValue());
                break;
            case "char":
            	bulletChar = attr.getNodeValue().charAt(0);
                break;
            case "checkedChar":
            	checkBulletChar = attr.getNodeValue().charAt(0);
                break;
            case "useImage":
            	bulletImage = Integer.parseInt(attr.getNodeValue());
                break;
            default:
            }
        }
        
        NodeList children = node.getChildNodes();
        for (int i=0; i<children.getLength(); i++) {
        	Node childNode = children.item(i);
        	log.info(childNode.getNodeName() + ":" + childNode.getNodeValue());
        	switch(childNode.getNodeName()) {
        	case "hc:img":
	        	{
	                NamedNodeMap childAttrs = childNode.getAttributes();
	                // alpha:0, binaryItemIDRef:image1, bright:0, contrast:0, effect:REAL_PIC
	                
	                for (int j=0; j<childAttrs.getLength(); j++) {
	                	Node grandChildNode = childAttrs.item(j);
	                	switch(grandChildNode.getNodeName()) {
	                	case "alpha":
	                		break;
	                	case "binaryItemIDRef":
	                		binItemRefID = grandChildNode.getNodeValue();
	                		break;
	                	case "bright":
	                		break;
	                	case "contrast":
	                		break;
	                	case "effect":
	                		break;
                		default:
    	                	log.info(grandChildNode.getNodeName() + ":" + grandChildNode.getNodeValue());
    	                	break;
	                	}
	                }
	        	}
        		break;
            case "hh:paraHead":
            	{
	                NamedNodeMap childAttrs = childNode.getAttributes();
	                // align:LEFT, autoIndent:1, charPrIDRef:4294967295, checkable:0, level:0
	                // numFormat:DIGIT, textOffset:50, textOffsetType:PERCENT, useInstWidth:0, widthAdjust:0
	                
	                for (int j=0; j<childAttrs.getLength(); j++) {
	                	Node grandChildNode = childAttrs.item(j);
	                	log.info(grandChildNode.getNodeName() + ":" + grandChildNode.getNodeValue());
	                }
                }
            	break;
            default:
        	}
        }
        
    }

}
