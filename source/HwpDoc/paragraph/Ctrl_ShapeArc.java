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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpxFile;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.paragraph.Ctrl_ShapeEllipse.ArcType;

public class Ctrl_ShapeArc extends Ctrl_GeneralShape {
	private static final Logger log = Logger.getLogger(Ctrl_ShapeArc.class.getName());
	private int size;
	
	// 타원 개체 속성
	public ArcType	type;		// 속성 (표 97참조)
	public int		centerX;	// 중심 좌표의 X값
	public int		centerY;	// 중심 좌표의 Y값
	public int		axixX1;		// 제1축 X 좌표의 값
	public int		axixY1;		// 제1축 Y 좌표의 값
	public int		axixX2;		// 제2축 X 좌표의 값
	public int		axixY2;		// 제2축 Y 좌표의 값

	public Ctrl_ShapeArc(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId, size, buf, off, version);
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}

	public Ctrl_ShapeArc(Ctrl_GeneralShape shape) {
		super(shape);
		
		this.size = shape.getSize();
	}
	
	public Ctrl_ShapeArc(HwpxFile hwpx, String ctrlId, Node node, int version) throws NotImplementedException {
        super(hwpx, ctrlId, node, version);
        
        NamedNodeMap attributes = node.getAttributes();
        
        // 호의 종류
        type = ArcType.valueOf(attributes.getNamedItem("type").getNodeValue());
        
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            NamedNodeMap childAttrs = child.getAttributes();
            switch(child.getNodeName()) {
            case "hp:center":   // 중심좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                centerX = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                centerY = Integer.parseInt(numStr);
                break;
            case "hp:ax1":  // 제1축 좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                axixX1 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                axixY1 = Integer.parseInt(numStr);
                break;
            case "hp:ax2":  // 제2축 좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                axixX2 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                axixY2 = Integer.parseInt(numStr);
                break;
            default:
            	if (log.isLoggable(Level.FINE)) {
            		throw new NotImplementedException("Ctrl_ShapeArc");
            	}
            }
        }

    }

	public static int parseElement(Ctrl_ShapeArc obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        obj.type    = ArcType.from(buf[offset++]);
        obj.centerX = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.centerY = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.axixX1  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.axixY1  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.axixX2  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.axixY2  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
    
        log.fine("                                                  "
                +"Center(X,Y)=("+obj.centerX+","+obj.centerY+")"
                +",Point1(X,Y)=("+obj.axixX1+","+obj.axixY1+")"
                +",Point2(X,Y)=("+obj.axixX2+","+obj.axixY2+")"
                );
    
        if (offset-off-size!=0) {
            log.severe("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            // throw new HwpParseException();
        }
        
        return size;
    }

    public static int parseCtrl(Ctrl_ShapeArc shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        offset += Ctrl_GeneralShape.parseCtrl(shape, size, buf, off, version);
        
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
