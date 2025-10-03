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

import HwpDoc.IContext;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapeLine extends Ctrl_GeneralShape {
    private static final Logger log = Logger.getLogger(Ctrl_ShapeLine.class.getName());
    private int size;
    
    // 선 개체 속성
    public int      startX; // 시작점 X 좌표
    public int      startY; // 시작점 Y 좌표
    public int      endX;   // 끝점 X 좌표
    public int      endY;   // 끝점 Y 좌표
    public short    attr;   // 속성
    
    public Ctrl_ShapeLine(String ctrlId, int size, byte[] buf, int off, int version) {
        super(ctrlId, size, buf, off, version);
        this.size = offset-off;
        
        log.fine("                                                  " + toString());
    }
    
    public Ctrl_ShapeLine(Ctrl_GeneralShape shape) {
        super(shape);
        
        this.size = shape.getSize();
    }
    
    public Ctrl_ShapeLine(String ctrlId, Node node, int version, IContext context) throws NotImplementedException {
        super(ctrlId, node, version, context);
        
        NamedNodeMap attributes = node.getAttributes();
        // 처음 생성 시 수직선 또는 수평선일때, 선의 방향이 언제나 오른쪽(위쪽)으로 잡힘으로 인한 현상때문에 방향을 바로 잡아주기 위한 속성
        if (attributes.getNamedItem("isReverseHV")!=null) {
            switch(attributes.getNamedItem("isReverseHV").getNodeValue()) {
            case "0":
                attr = 0;   break;
            case "1":
                attr = 1;   break;
            }
        }
        
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=nodeList.getLength()-1; i>=0; i--) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "hc:startPt":    // 시작점
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    startX = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    startY = Integer.parseInt(numStr);
                }
                node.removeChild(child);
                break;
            case "hc:endPt":      // 끝점
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    endX = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    endY = Integer.parseInt(numStr);
                }
                node.removeChild(child);
                break;
            }
        }
    }

   public static int parseElement(Ctrl_ShapeLine obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        if (obj.ctrlId!=null && obj.ctrlId.equals("loc$")) {
            offset += 4;
        }
        
        obj.startX  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.startY  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.endX    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.endY    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        if (offset-off == size) {
            return size;
        } else {
            obj.attr    = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
            // 18byte가 아닌 20byte가 온다. 따라서 2byte를 임의로 더해준다.
            offset += 2;
            
            if (offset-off-size!=0) {
                log.severe("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
                // throw new HwpParseException();
            }
            return size;
        }
    }

    public static int parseCtrl(Ctrl_ShapeLine shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        offset += Ctrl_GeneralShape.parseCtrl(shape, size, buf, offset, version);
        
        return offset-off;
    }
    
    public static int parseListHeaderAppend(Ctrl_ShapeLine shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        // LIST_HEADER 정의는 6byte이지만, 실제 33byte 넘어온다. 알수 없다.
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
