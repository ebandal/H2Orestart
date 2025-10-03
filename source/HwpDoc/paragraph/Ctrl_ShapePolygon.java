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
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.IContext;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapePolygon extends Ctrl_GeneralShape {
    private static final Logger log = Logger.getLogger(Ctrl_ShapePolygon.class.getName());
    private int size;
    
    // 타원 개체 속성
    public int      nPoints;    // count of points
    public LinkedList<Point>  points;     // x,y 좌표 * n
    
    public Ctrl_ShapePolygon(String ctrlId, int size, byte[] buf, int off, int version) {
        super(ctrlId, size, buf, off, version);
        this.size = offset-off;
        
        log.fine("                                                  " + toString());
    }
    
    public Ctrl_ShapePolygon(Ctrl_GeneralShape shape) {
        super(shape);
        
        this.size = shape.getSize();
    }
    
    public Ctrl_ShapePolygon(String ctrlId, Node node, int version, IContext context) throws NotImplementedException {
        super(ctrlId, node, version, context);
        
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        points = new LinkedList<Point>();
        for (int i=nodeList.getLength()-1; i>=0; i--) {
            Node child = nodeList.item(i);
            NamedNodeMap childAttrs = child.getAttributes();
            switch(child.getNodeName()) {
            case "hc:pt":   // 다각형 좌표
                {
                    Point pt = new Point();
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    pt.x = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    pt.y = Integer.parseInt(numStr);
                    points.add(0,pt);
                }
                node.removeChild(child);
                break;
            case "#text":
                break;
            default:
                log.fine(child.getNodeName() + "=" + child.getNodeValue());
                if (log.isLoggable(Level.FINE)) {
                    throw new NotImplementedException("Ctrl_ShapePolygon");
                }
                break;
            }
        }
        nPoints = points.size();
    }

	public static int parseElement(Ctrl_ShapePolygon obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        obj.nPoints     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        
        if (obj.nPoints > 0) {
            obj.points = new LinkedList<Point>(); // [obj.nPoints];
            for (int i=0; i<obj.nPoints; i++) {
                Point pt = new Point();
                pt.x = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                offset += 4;
                pt.y = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
                offset += 4;
                obj.points.push(pt);
            }
        }

        if (size-(offset-off)==4) {
            // [HWP ambiguous] following 4bytes are unknown.
            // 따라오는 4bytes 정보를 알 수 없음. 문서에는 없는 내용. 4byte 추가함.
            offset += 4;
        }
        
        if (offset-off-size!=0) {
            log.severe("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            // throw new HwpParseException();
        }
        
        return size;
    }
    
    public static int parseCtrl(Ctrl_ShapePolygon shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        offset += Ctrl_GeneralShape.parseCtrl(shape, size,  buf,  off,  version);
        
        return offset-off;
    }
    
    public static int parseListHeaderAppend(Ctrl_ShapePolygon obj, int size, byte[] buf, int off, int version) throws HwpParseException {
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
