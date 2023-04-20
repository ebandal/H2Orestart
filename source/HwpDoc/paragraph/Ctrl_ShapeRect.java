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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapeRect extends Ctrl_GeneralShape {
	private static final Logger log = Logger.getLogger(Ctrl_ShapeRect.class.getName());
	private int size;	
	
	// 사각형 개체 속성
	public byte		curv;	// 사각형 모서리 곡률(%)  직각은 0, 둥근모양 20, 반원 50, 그외 % 단위
	public Point[]	points;	// 사각형의 좌표 x, 사각형의 좌표 y
	
	public Ctrl_ShapeRect(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId, size, buf, off, version);
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}
	
	public Ctrl_ShapeRect(Ctrl_GeneralShape shape) {
		super(shape);
		
		this.size = shape.getSize();
	}
	
	public Ctrl_ShapeRect(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId, node, version);
        
        NamedNodeMap attributes = node.getAttributes();
        String numStr = attributes.getNamedItem("ratio").getNodeValue();
        curv = (byte) Integer.parseInt(numStr);
        
        NodeList nodeList = node.getChildNodes();
        if (nodeList!=null && nodeList.getLength()>0) {
            points = new Point[4];
        }
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            NamedNodeMap childAttrs = child.getAttributes();
            switch(child.getNodeName()) {
            case "hc:pt0":  // 첫번째 좌표
                points[0] = new Point();
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                points[0].x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                points[0].y = Integer.parseInt(numStr);
                break;
            case "hc:pt1":  // 두번째 좌표
                points[1] = new Point();
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                points[1].x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                points[1].y = Integer.parseInt(numStr);
                break;
            case "hc:pt2":  // 세번째 좌표
                points[2] = new Point();
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                points[2].x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                points[2].y = Integer.parseInt(numStr);
                break;
            case "hc:pt3":  // 네번째 좌표
                points[3] = new Point();
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                points[3].x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                points[3].y = Integer.parseInt(numStr);
                break;
            case "hp:offset":
                
                break;
            case "hp:orgSz":
                
                break;
            case "hp:curSz":
                
                break;
            case "hp:flip":
                
                break;
            case "hp:rotationInfo":
                
                break;
            case "hp:renderingInfo":
                
                break;
            case "hp:lineShape":
                
                break;
            case "hc:fillBrush":
                
                break;
            case "hp:shadow":
                
                break;
            case "hp:drawText":
                
                break;
            case "hp:sz":
                
                break;
            case "hp:pos":
                
                break;
            case "hp:outMargin":
                
                break;
            default:
                throw new NotImplementedException("Ctrl_ShapeRect");
            }
        }
    }

	public static int parseElement(Ctrl_ShapeRect obj, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;

        // 사각형 개체 속성
        obj.curv        = buf[offset++];
        obj.points = new Point[4];
        for (int i=0;i<4;i++) {
            obj.points[i]   = new Point();
            obj.points[i].x = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            obj.points[i].y = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
        }

        log.fine("                                                  "
                +"(X,Y)=("+obj.xGrpOffset+","+obj.yGrpOffset+")"
                +",Width="+obj.curWidth+",Height="+obj.curHeight
                +",Points=["+Arrays.stream(obj.points).map(p -> "{"+String.valueOf(p.x)+","+String.valueOf(p.y)+"}").collect(Collectors.joining(","))+"]"
                );

        if (offset-off-size!=0) {
            log.fine("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            throw new HwpParseException();
        }
        
        return offset-off;
    }
    
    public static int parseListHeaderAppend(Ctrl_ShapeRect obj, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;
        offset += 2;
        
        // 글상자 속성
        obj.leftSpace   = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.rightSpace  = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.upSpace     = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.downSpace   = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.maxTxtWidth = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        
        if (size-12 <= 13) {
            // 사이즈가 작을때는 그냥 넘김.
            return size;
        }
        
        // 알 수 없는 23byte
        offset += 13;
        
        if (size-(offset-off)>0) {
            offset += 10;
            // 필드이름 정보 (앞의 23byte 때문에  시작위치가 여기부터인지도 확실하지 않음)
            int strLen      = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
            String fieldName= new String(buf, offset, strLen*2, StandardCharsets.UTF_16LE);
            offset += (strLen*2);
            log.fine("                                                  [CtrlId]=" + obj.ctrlId + ", fieldName=" + fieldName);
            
            offset += (size-(offset-off));
        }
        
        return offset-off;
    }

    public static int parseCtrl(Ctrl_ShapeRect shape, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;
        offset += Ctrl_GeneralShape.parseCtrl(shape, size, buf, offset, version);

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
