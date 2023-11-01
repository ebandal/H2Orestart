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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_HeadFoot extends Ctrl {
    private static final Logger log = Logger.getLogger(Ctrl_HeadFoot.class.getName());
    
    private int         size;
    public  boolean     isHeader;
    public  int         attr;
    public PageRange    whichPage;
    public int          serialInSec;
    
    public int textWidth;
    public int textHeight;
    public byte refLevelText;
    public byte refLevelNum;
    
    public List<HwpParagraph> paras;
    
    public Ctrl_HeadFoot(String ctrlId) {
        super(ctrlId);
    }
    
    public Ctrl_HeadFoot(String ctrlId, int size, byte[] buf, int off, int version, boolean isHeader) {
        super(ctrlId);
        
        int offset = off;
        this.isHeader = isHeader;
        
        // 속성(표 141참조) (머리말이 적용 0:양쪽, 1:짝수쪽만, 2:홀수쪽만)
        attr            = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        whichPage       = PageRange.from(attr);
        
        serialInSec     =  buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        
        log.fine("                                                  " + toString());
        
        this.size = offset-off;
    }
    
    public static int parseListHeaderAppend(Ctrl_HeadFoot obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        offset += 2;
        
        // 텍스트 영역의 폭  (1/7200inch로 계산하는가?)
        obj.textWidth       = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        // 텍스트 영역의 높이  (1/7200inch로 계산하는가?)
        obj.textHeight      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        // 각 비트가 해당 레벨의 텍스트에 대한 참조를 했는지 여부
        obj.refLevelText    = buf[offset++];
        // 각 비트가 해당 레벨의 번호에 대한 참조를 했는지 여부
        obj.refLevelNum     = buf[offset++];
        
        if (size-(offset-off)>0) {
            log.fine("                                                  [CtrlId]=" + obj.ctrlId + "," + size + " bytes를 해석하지 못함.");
            offset += (size-(offset-off));
        }
        obj.fullfilled = true;
        
        return offset-off;
    }
    
    public Ctrl_HeadFoot(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId);
        
        if (ctrlId.equals("daeh")) {
            isHeader = true;
        } else {
            isHeader = false;
        }
        
        NamedNodeMap attributes = node.getAttributes();
        
        // id는 처리하지 않는다. List<Ctrl_HeadFoot>에 순차적으로 추가한다. 
        // String id = attributes.getNamedItem("id").getNodeValue();
        
        whichPage = PageRange.valueOf(attributes.getNamedItem("applyPageType").getNodeValue());
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hp:subList":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    // [hasNumRef="0", hasTextRef="0", id="", lineWrap="BREAK", linkListIDRef="0", 
                    // linkListNextIDRef="0", textDirection="HORIZONTAL", textHeight="7087", 
                    // textWidth="45354", vertAlign="TOP"]
                    String numStr;
                    
                    for (int j=0; j<childAttrs.getLength(); j++) {
                        Node childNodeAttr = childAttrs.item(j);
                        switch(childNodeAttr.getNodeName()) {
                        case "hasNumRef":
                            numStr = childNodeAttr.getNodeValue();
                            refLevelNum = (byte)Short.parseShort(numStr);
                            break;
                        case "hasTextRef":
                            numStr = childNodeAttr.getNodeValue();
                            refLevelText = (byte)Short.parseShort(numStr);
                            break;
                        case "id":
                            break;
                        case "lineWrap":
                            break;
                        case "linkListIDRef":
                            break;
                        case "linkListNextIDRef":
                            break;
                        case "textDirection":
                            break;
                        case "textHeight":
                            numStr = childNodeAttr.getNodeValue();
                            textHeight = Integer.parseInt(numStr);
                            break;
                        case "textWidth":
                            numStr = childNodeAttr.getNodeValue();
                            textWidth = Integer.parseInt(numStr);
                            break;
                        case "vertAlign":
                            break;
                        default:
                            log.fine(childNodeAttr.getNodeName() + ":" + childNodeAttr.getNodeValue());
                            if (log.isLoggable(Level.FINE)) {
                                throw new NotImplementedException("Ctrl_HeadFoot");
                            }
                            break;
                        }
                    }
                    
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        switch(grandChild.getNodeName()) {
                        case "hp:p":
                            if (paras==null) {
                                paras = new ArrayList<HwpParagraph>();
                            }
                            HwpParagraph p = new HwpParagraph(grandChild, version);
                            paras.add(p);
                            break;
                        default:
                            log.fine(grandChild.getNodeName() + ":" + grandChild.getNodeValue());
                            if (log.isLoggable(Level.FINE)) {
                                throw new NotImplementedException("Ctrl_HeadFoot");
                            }
                            break;
                        }
                    }
                }
                break;
            default:
                log.fine(child.getNodeName() + ":" + child.getNodeValue());
                if (log.isLoggable(Level.FINE)) {
                    throw new NotImplementedException("Ctrl_HeadFoot");
                }
                break;
            }
        }
        this.fullfilled = true;
    }
    
    
    public String toString() {
        StringBuffer strb = new StringBuffer();
        strb.append("CTRL("+ctrlId+")")
            .append("=속성:"+(whichPage==null?"null":whichPage.toString()))
            .append(",텍스트폭:"+textWidth)
            .append(",텍스트높이:"+textHeight)
            .append(",레벨의텍스트참조여부:"+refLevelText)
            .append(",레벨의번호참조여부:"+refLevelNum);
        return strb.toString();
    }
    
    public int getSize() {
        return size;
    }
    
    public static enum PageRange {
        BOTH        (0),    // 양쪽 
        EVEN        (1),    // 짝수쪽
        ODD         (2);    // 홀수쪽
        
        private int range;
        
        private PageRange(int range) { 
            this.range = range;
        }
        
        public static PageRange from(int range) {
            for (PageRange typeNum: values()) {
                if (typeNum.range == range)
                    return typeNum;
            }
            return null;
        }
    }
}
