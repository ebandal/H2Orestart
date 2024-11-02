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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpxFile;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapeEllipse extends Ctrl_GeneralShape {
    private static final Logger log = Logger.getLogger(Ctrl_ShapeEllipse.class.getName());
    private int size;
    
    // 타원 개체 속성
    public boolean  intervalDirty;  // 속성 (표 97참조)
    public boolean  hasArcProperty;
    public ArcType  arcType;
    public int      centerX;        // 중심 좌표의 X값
    public int      centerY;        // 중심 좌표의 Y값
    public int      axixX1;         // 제1축 X 좌표의 값
    public int      axixY1;         // 제1축 Y 좌표의 값
    public int      axixX2;         // 제2축 X 좌표의 값
    public int      axixY2;         // 제2축 Y 좌표의 값
    public int      startX1;
    public int      startY1;
    public int      endX1;
    public int      endY1;
    public int      startX2;
    public int      startY2;
    public int      endX2;
    public int      endY2;
    
    public Ctrl_ShapeEllipse(String ctrlId, int size, byte[] buf, int off, int version) {
        super(ctrlId, size, buf, off, version);
        this.size = offset-off;
        
        log.fine("                                                  " + toString());
    }
    
    public Ctrl_ShapeEllipse(Ctrl_GeneralShape shape) {
        super(shape);
        
        this.size = shape.getSize();
    }
    
    public Ctrl_ShapeEllipse(HwpxFile hwpx, String ctrlId, Node node, int version) throws NotImplementedException {
        super(hwpx, ctrlId, node, version);
        
        NamedNodeMap attributes = node.getAttributes();
        // 호로 바뀌었을때 interval을 다시 계산해야 할 필요가 있는지 여부
        switch(attributes.getNamedItem("intervalDirty").getNodeValue()) {   
        case "0":
            intervalDirty = false;      break;
        case "1":
            intervalDirty = true;       break;
        default:
        	if (log.isLoggable(Level.FINE)) {
        		throw new NotImplementedException("Ctrl_ShapeEllipse");
        	}
        };
        // 호로 바뀌었는지 여부
        switch(attributes.getNamedItem("hasArcPr").getNodeValue()) {
        case "0":
            intervalDirty = false;      break;
        case "1":
            intervalDirty = true;       break;
        default:
        	if (log.isLoggable(Level.FINE)) {
        		throw new NotImplementedException("Ctrl_ShapeEllipse");
        	}
        };
        // 호의 종류
        arcType = ArcType.valueOf(attributes.getNamedItem("arcType").getNodeValue());
        
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            NamedNodeMap childAttrs = child.getAttributes();
            switch(child.getNodeName()) {
            case "hc:center":   // 중심좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                centerX = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                centerY = Integer.parseInt(numStr);
                break;
            case "hc:ax1":  // 제1축 좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                axixX1 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                axixY1 = Integer.parseInt(numStr);
                break;
            case "hc:ax2":  // 제2축 좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                axixX2 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                axixY2 = Integer.parseInt(numStr);
                break;
            case "hc:start1":  // 시작지점 1 좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                startX1 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                startY1 = Integer.parseInt(numStr);
                break;
            case "hc:start2":  // 시작지점 2 좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                startX2 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                startY2 = Integer.parseInt(numStr);
                break;
            case "hc:end1":  // 끝지점 1좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                endX1 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                endY1 = Integer.parseInt(numStr);
                break;
            case "hc:end2":  // 끝지점 2좌표
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                endX2 = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                endY2 = Integer.parseInt(numStr);
                break;
            default:
            	log.fine(child.getNodeName() + "=" + child.getNodeValue());
                throw new NotImplementedException("Ctrl_ShapeEllipse");
            }
        }
    }

	public static int parseElement(Ctrl_ShapeEllipse obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        int attr    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        obj.intervalDirty     = (attr&0x01) == 0x01;
        obj.hasArcProperty    = (attr&0x02) == 0x02;
        obj.arcType = ArcType.from(attr<<2&0xFF);
        offset += 4;
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
        obj.startX1 = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.startY1 = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.endX1   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.endY1   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.startX2 = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.startY2 = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.endX2   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.endY2   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;

        if (offset-off-size!=0) {
            log.severe("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            // throw new HwpParseException();
        }
        
        return size;
    }
    
    public static int parseCtrl(Ctrl_ShapeEllipse shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        offset += Ctrl_GeneralShape.parseCtrl(shape, size, buf, off, version);
        
        return offset-off;
    }
    
    public static int parseListHeaderAppend(Ctrl_ShapeEllipse obj, int size, byte[] buf, int off, int version) throws HwpParseException {
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
        // maxTxtWidth가  curWidth와 같으면 가로쓰기, maxTxtWidth가 curHeight와 같으면 세로쓰기.
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
    
   public static enum ArcType {
        NORMAL      (0x0),
        PIE         (0x1),
        CHORD       (0x2);
        
        private int num;
        private ArcType(int num) { 
            this.num = num;
        }
        public static ArcType from(int num) {
            for (ArcType type: values()) {
                if (type.num == num)
                    return type;
            }
            return null;
        }
    }
}
