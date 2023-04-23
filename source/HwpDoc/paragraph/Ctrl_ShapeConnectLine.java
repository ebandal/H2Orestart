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

import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecord_ParaShape.VerticalAlign;

public class Ctrl_ShapeConnectLine extends Ctrl_GeneralShape {
	private static final Logger log = Logger.getLogger(Ctrl_ShapeConnectLine.class.getName());
	private int size;
	
	public ConnectLineType type;       // 연결선 형식
	public ConnectPoint    startPt;    // 연결선 시작점 정보
	public ConnectPoint    endPt;      // 연결선 끝점 정보
	
	public Ctrl_ShapeConnectLine(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId, size, buf, off, version);
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}
	
	public Ctrl_ShapeConnectLine(Ctrl_GeneralShape shape) {
		super(shape);
		
		this.size = shape.getSize();
	}
	
	public Ctrl_ShapeConnectLine(String ctrlId, Node node, int version) throws NotImplementedException {
	    super(ctrlId, node, version);
	    
        NamedNodeMap attributes = node.getAttributes();
        if (attributes.getNamedItem("type")!=null) {
            type = ConnectLineType.valueOf(attributes.getNamedItem("type").getNodeValue());
        }
        
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "hp:startPt":    // 시작점
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    startPt.x = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    startPt.y = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("subjectIDRef").getNodeValue();
                    startPt.subjectIDRef = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("subjectIdx").getNodeValue();
                    startPt.subjectIdx = (short) Integer.parseInt(numStr);
                }
                node.removeChild(child);
                break;
            case "hp:endPt":      // 끝점
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    endPt.x = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    endPt.y = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("subjectIDRef").getNodeValue();
                    endPt.subjectIDRef = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("subjectIdx").getNodeValue();
                    endPt.subjectIdx = (short) Integer.parseInt(numStr);
                }
                node.removeChild(child);
                break;
            default:
            	log.warning(child.getNodeName() + "=" + child.getNodeValue());
                throw new NotImplementedException("Ctrl_ShapeConnectLine");
            }
        }
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
	
	public static class ConnectPoint extends Point {
	    public short subjectIDRef;
	    public short subjectIdx;
	}
	
	public static enum ConnectLineType {
	    STRAIGHT_NOARROW   (0x0),
	    STRAIGHT_ONEWAY    (0x1),
	    STRAIGHT_BOTH      (0x2),
	    STROKE_NOARROW     (0x3),
	    STROKE_ONEWAY      (0x4),
	    STROKE_BOTH        (0x5),
	    ARC_NOARROW        (0x6),
	    ARC_ONEWAY         (0x7),
	    ARC_BOTH           (0x8);
	    
        private int num;
        
        private ConnectLineType(int num) { 
            this.num = num;
        }

        public static ConnectLineType from(int num) {
            for (ConnectLineType type: values()) {
                if (type.num == num)
                    return type;
            }
            return null;
        }
	    

	}
}
