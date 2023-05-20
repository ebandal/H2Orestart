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
package HwpDoc.paragraph;

import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapeOle extends Ctrl_GeneralShape {
	private static final Logger log = Logger.getLogger(Ctrl_ShapeOle.class.getName());
	private int size;
	
	public int			attr;			// 속성
	public int			extentX;		// 오브젝트 자체의 extent x크기
	public int			extentY;		// 오브젝트 자체의 extent y크기
	public String		binDataID;		// 오브젝트가 사용하는 스토리지의 BinData ID
	public int			borderColor;	// 테두리 색
	public int			borderThick;	// 테두리 두께
	public int			borderAttr;		// 테두리 속성 (표 87참조)

	public Ctrl_ShapeOle(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId, size, buf, off, version);
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}

	public Ctrl_ShapeOle(Ctrl_GeneralShape shape) {
		super(shape);
		
		this.size = shape.getSize();
	}

	public Ctrl_ShapeOle(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId, node, version);
        
        NamedNodeMap attributes = node.getAttributes();
        switch(attributes.getNamedItem("objectType").getNodeValue()) {  // OLE 객체 종류
        case "STATIC":
        	break;
        default:
            throw new NotImplementedException("ShpaeOLE");
        }

        String numStr = attributes.getNamedItem("binaryItemIDRef").getNodeValue();   // OLE 객체 바이너리 데이터에 대한 아이디 참조값
        binDataID = numStr;
        /*
        switch(attributes.getNamedItem("hasMoniker").getNodeValue()) {   // moniker가 설정되어 있는지 여부
        case "0":
        case "1":
        }
        attributes.getNamedItem("drawAspect").getNodeValue();   // 화면에 어떤 형태로 표시될지에 대한 설정; "CONTENT"
        attributes.getNamedItem("dropcapstyle").getNodeValue();   //  "None"
        attributes.getNamedItem("eqBaseLine").getNodeValue();   // 베이스라인; "0"
        */
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "hc:extent":    // 오브젝트 자체의 extent 크기
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    extentX = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    extentY = Integer.parseInt(numStr);
                }
                break;
            /*
            case "hp:lineShape":      // 테두리선 모양
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("color").getNodeValue().replaceAll("#", "");   // 선색상
                    borderColor = Integer.parseInt(numStr, 16);
                    numStr = childAttrs.getNamedItem("width").getNodeValue();                       // 선 굵기
                    borderThick = Integer.parseInt(numStr);
                    // childAttrs.getNamedItem("style").getNodeValue();                       // 선 종류
                    // childAttrs.getNamedItem("endCap").getNodeValue();                      // 선 끝 모양
                    // childAttrs.getNamedItem("headStyle").getNodeValue();                   // 화살표 시작 모양
                    // childAttrs.getNamedItem("tailStyle").getNodeValue();                   // 화살표 끝 모양
                    // childAttrs.getNamedItem("headSz").getNodeValue();                      // 화살표 시작 크기
                    // childAttrs.getNamedItem("tailSz").getNodeValue();                      // 화살표 끝 크기
                    // childAttrs.getNamedItem("outlineStyle").getNodeValue();                // 테두리선의 형태
                    // childAttrs.getNamedItem("alpha").getNodeValue();                       // 투명도      
                }
                break;
            */
            default:
                throw new NotImplementedException("ShpaeOLE");
            }
        }
    }

	public static int parseElement(Ctrl_ShapeOle obj, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;
        
        obj.attr        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.extentX     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.extentY     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.binDataID	= String.valueOf((short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF));   
        offset += 2;
        obj.borderColor = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
        offset += 4;
        obj.borderThick = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.borderAttr  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        // 8 bytes가 남지만,  OLE 미지원으로 할 것으므로 무시한다. 
        if (offset-off-size!=0) {
            log.fine("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            // size 계산 무시
            // throw new HwpParseException();
        }
        
        return offset-off;
    }
    
    public static int parseCtrl(Ctrl_ShapeOle shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        int len = Ctrl_ObjElement.parseCtrl(shape, size, buf, offset, version);
        offset += len;
        
        return offset-off;
    }

    public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("CTRL("+ctrlId+")")
			.append("=공통속성:"+super.toString());
		return strb.toString();
	}

	@Override
	public int getSize() {
		return size;
	}
}
