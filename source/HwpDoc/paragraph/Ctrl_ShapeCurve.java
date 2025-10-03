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

import HwpDoc.IContext;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapeCurve extends Ctrl_GeneralShape {
    private static final Logger log = Logger.getLogger(Ctrl_ShapeCurve.class.getName());
    private int size;
    
    // 타원 개체 속성
    public int		nPoints;		// count of points
    public Point[]	points;			// x,y 좌표 * n
    public byte[]	segmentType;	// segment type (0:line, 1:curve)
    
    public Ctrl_ShapeCurve(String ctrlId, int size, byte[] buf, int off, int version) {
        super(ctrlId, size, buf, off, version);
        this.size = offset-off;
        
        log.fine("                                                  " + toString());
    }
    
    public Ctrl_ShapeCurve(Ctrl_GeneralShape shape) {
        super(shape);
        
        this.size = shape.getSize();
    }
    
    public Ctrl_ShapeCurve(String ctrlId, Node node, int version, IContext context) throws NotImplementedException {
        super(ctrlId, node, version, context);
        
        String numStr;
        NodeList nodeList = node.getChildNodes();
        
        nPoints = nodeList.getLength();
        points = new Point[nodeList.getLength()];
        segmentType = new byte[nodeList.getLength()];
        
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            NamedNodeMap childAttrs = child.getAttributes();
            switch(child.getNodeName()) {
            case "hp:seg":   // 곡선 세그먼트
                switch(childAttrs.getNamedItem("type").getNodeValue()) {    // 곡선 세그먼트 형식
                case "CURVE":
                    segmentType[i] = 1;     break;
                case "LINE":
                    segmentType[i] = 0;     break;
                default:
                    if (log.isLoggable(Level.FINE)) {
                        throw new NotImplementedException("Ctrl_ShapeCurve");
                    }
                    break;
                }
                points[i] = new Point();
                numStr = childAttrs.getNamedItem("x1").getNodeValue();
                points[i].x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y1").getNodeValue();
                points[i].y = Integer.parseInt(numStr);
                // childAttrs.getNamedItem("x2").getNodeValue();
                // childAttrs.getNamedItem("y2").getNodeValue();
                break;
            default:
                if (log.isLoggable(Level.FINE)) {
                    throw new NotImplementedException("Ctrl_ShapeCurve");
                }
                break;
            }
        }
    }

	public static int parseElement(Ctrl_ShapeCurve obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        obj.nPoints             = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        
        if (obj.nPoints > 0) {
            obj.points = new Point[obj.nPoints];
            for (int i=0;i<obj.nPoints;i++) {
                obj.points[i] = new Point();
                obj.points[i].x = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                offset += 4;
                obj.points[i].y = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                offset += 4;
            }
        }
        if (obj.nPoints>1) {
            obj.segmentType = new byte[obj.nPoints-1];
            for (int i=0;i<obj.nPoints-1;i++) {
                obj.segmentType[i]  = buf[offset++];
            }
        }
        
        // [HWP ambiguous] following 4bytes are unknown.
        // 4byte를 알 수 없음. 점이 4개여서 4byte인지, 4byte 필드가 존재하는지 알 수 없음.
        offset += 4;
        
        if (offset-off-size!=0) {
            log.severe("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            // throw new HwpParseException();
        }
        
        return size;
    }
    
    public static int parseCtrl(Ctrl_ShapeCurve shape,  int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        int len = Ctrl_GeneralShape.parseCtrl(shape, size,  buf,  off,  version);
        offset += len;
        
        return offset-off;
    }
    
    public static int parseListHeaderAppend(Ctrl_ShapeCurve shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        // LIST_HEADER 정의는 6byte이지만, 실제 33byte 넘어온다. 6byte 뒤의 27byte는 알 수 없다.
        offset += size;
        
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
