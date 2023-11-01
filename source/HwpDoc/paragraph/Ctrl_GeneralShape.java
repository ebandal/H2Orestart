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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecordTypes.LineArrowSize;
import HwpDoc.HwpElement.HwpRecordTypes.LineArrowStyle;
import HwpDoc.HwpElement.HwpRecordTypes.LineStyle2;
import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecord_BorderFill.Fill;
import HwpDoc.paragraph.Ctrl_Character.CtrlCharType;

public class Ctrl_GeneralShape extends Ctrl_ObjElement {
    private static final Logger log = Logger.getLogger(Ctrl_GeneralShape.class.getName());
    private HwpParagraph    parent;
    private int size;
    
    // 테두리선 정보
    public int              lineColor;  // 선색상
    public int              lineThick;  // 선굵기
    // public int           lineAttr;   // 테두리선 정보 속성
    public LineArrowStyle   lineHead;
    public LineArrowStyle   lineTail;
    public LineArrowSize    lineHeadSz;
    public LineArrowSize    lineTailSz;
    public LineStyle2       lineStyle;
    public byte             outline;    // Outline style
    
    // 채우기 정보
    public int              fillType;   // 채우기 종류 (0:없음, 1:단색, 2:이미지, 4:그라데이션)
    public Fill             fill;       // 채우기
    
    // 글상자 텍스트 속성
    public short            leftSpace;  // 글상자 텍스트 왼쪽 여백
    public short            rightSpace; // 글상자 텍스트 오른쪽 여백
    public short            upSpace;    // 글상자 텍스트 위쪽 여백
    public short            downSpace;  // 글상자 텍스트 아래쪽 여백
    public int              maxTxtWidth;// 텍스트 문자열 최대 폭
    
    public Ctrl_GeneralShape() {
        super();
    }
    
    public Ctrl_GeneralShape(String ctrlId) {
        super(ctrlId);
    }
    
    public Ctrl_GeneralShape(String ctrlId, int size, byte[] buf, int off, int version) {
        super(ctrlId, size, buf, off, version);
        this.size = offset-off;
        
        log.fine("                                                  " + toString());
    }
    
    public Ctrl_GeneralShape(Ctrl_GeneralShape shape) {
        super((Ctrl_ObjElement)shape);
        this.parent         = shape.parent;
        
        this.lineColor      = shape.lineColor;
        this.lineThick      = shape.lineThick;
        // this.lineAttr    = shape.lineAttr;
        this.lineHead       = shape.lineHead;
        this.lineTail       = shape.lineTail;	
        this.lineHeadSz     = shape.lineHeadSz;
        this.lineTailSz     = shape.lineTailSz;
        this.lineStyle      = shape.lineStyle;
        this.outline        = shape.outline;
        this.fillType       = shape.fillType;
        this.fill           = shape.fill;
        this.leftSpace      = shape.leftSpace;
        this.rightSpace     = shape.rightSpace;
        this.upSpace        = shape.upSpace;
        this.downSpace      = shape.downSpace;
        this.maxTxtWidth    = shape.maxTxtWidth;
    }
    
    public Ctrl_GeneralShape(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId, node, version);
        
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=nodeList.getLength()-1; i>=0; i--) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "hp:lineShape":    // 그리기 객체의 테두리선 정보
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("color").getNodeValue().replaceAll("#", "");   // 선색상
                    if (!numStr.equals("none")) {
                    	lineColor = Integer.parseInt(numStr, 16);
                    }
                    numStr = childAttrs.getNamedItem("width").getNodeValue();                       // 선 굵기
                    lineThick = Integer.parseUnsignedInt(numStr);
                    lineStyle = LineStyle2.valueOf(childAttrs.getNamedItem("style").getNodeValue()); // 선 종류
                    
                    /*
                    childAttrs.getNamedItem("endCap").getNodeValue();                      // 선 끝 모양
                    childAttrs.getNamedItem("outlineStyle").getNodeValue();                // 테두리선의 형태
                    childAttrs.getNamedItem("alpha").getNodeValue();                       // 투명도
                    */       
                    boolean headFill = true;
                    if (childAttrs.getNamedItem("headfill")!=null) {
                    	switch(childAttrs.getNamedItem("headfill").getNodeValue()) {
                    	case "1":	headFill = true; 	break;
                    	case "0":	headFill = false;	break;
                    	}
                    }
                    if (childAttrs.getNamedItem("headStyle")!=null) {
                        switch(childAttrs.getNamedItem("headStyle").getNodeValue()) {                   // 화살표 시작 모양
                        case "ARROW":
                        	lineHead = LineArrowStyle.ARROW;
                        	break;
                        case "SPEAR":
                        	lineHead = LineArrowStyle.SPEAR;
                        	break;
                        case "CONCAVE_ARROW":
                        	lineHead = LineArrowStyle.CONCAVE_ARROW;
                        	break;
                        case "EMPTY_DIAMOND":
                        	lineHead = headFill?LineArrowStyle.DIAMOND:LineArrowStyle.EMPTY_DIAMOND;
                        	break;
                        case "EMPTY_CIRCLE":
                        	lineHead = headFill?LineArrowStyle.CIRCLE:LineArrowStyle.EMPTY_CIRCLE;
                        	break;
                        case "EMPTY_BOX":
                        	lineHead = headFill?LineArrowStyle.BOX:LineArrowStyle.EMPTY_BOX;
                        	break;
                        case "NORMAL":	
                        default:
                        	lineHead = LineArrowStyle.NORMAL;
                        	break;
                        }
                    }
                    if (childAttrs.getNamedItem("headSz")!=null) {
                        switch(childAttrs.getNamedItem("headSz").getNodeValue()) {                   // 화살표 시작 모양
                        case "SMALL_SMALL":
                        	lineHeadSz = LineArrowSize.SMALL_SMALL;		break;
                        case "SMALL_MEDIUM":
                        	lineHeadSz = LineArrowSize.SMALL_MEDIUM;	break;
                        case "SMALL_LARGE":
                        	lineHeadSz = LineArrowSize.SMALL_LARGE;		break;
                        case "MEDIUM_SMALL":
                        	lineHeadSz = LineArrowSize.MEDIUM_SMALL;	break;
                        case "MEDIUM_MEDIUM":
                        	lineHeadSz = LineArrowSize.MEDIUM_MEDIUM;	break;
                        case "MEDIUM_LARGE":
                        	lineHeadSz = LineArrowSize.MEDIUM_LARGE;	break;
                        case "LARGE_SMALL":
                        	lineHeadSz = LineArrowSize.LARGE_SMALL;		break;
                        case "LARGE_MEDIUM":
                        	lineHeadSz = LineArrowSize.LARGE_MEDIUM;	break;
                        case "LARGE_LARGE":
                        	lineHeadSz = LineArrowSize.LARGE_LARGE;		break;
                        default:
                        	lineHeadSz = LineArrowSize.MEDIUM_MEDIUM;	break;
                        }
                    }
                    
                    boolean tailFill = true;
                    if (childAttrs.getNamedItem("tailfill")!=null) {
                    	switch(childAttrs.getNamedItem("tailfill").getNodeValue()) {
                    	case "1":	tailFill = true; 	break;
                    	case "0":	tailFill = false;	break;
                    	}
                    }
                    if (childAttrs.getNamedItem("tailStyle")!=null) {
                        switch(childAttrs.getNamedItem("tailStyle").getNodeValue()) {                   // 화살표 시작 모양
                        case "ARROW":
                        	lineTail = LineArrowStyle.ARROW;
                        	break;
                        case "SPEAR":
                        	lineTail = LineArrowStyle.SPEAR;
                        	break;
                        case "CONCAVE_ARROW":
                        	lineTail = LineArrowStyle.CONCAVE_ARROW;
                        	break;
                        case "EMPTY_DIAMOND":
                        	lineTail = headFill?LineArrowStyle.DIAMOND:LineArrowStyle.EMPTY_DIAMOND;
                        	break;
                        case "EMPTY_CIRCLE":
                        	lineTail = headFill?LineArrowStyle.CIRCLE:LineArrowStyle.EMPTY_CIRCLE;
                        	break;
                        case "EMPTY_BOX":
                        	lineTail = headFill?LineArrowStyle.BOX:LineArrowStyle.EMPTY_BOX;
                        	break;
                        case "NORMAL":	
                        default:
                        	lineTail = LineArrowStyle.NORMAL;
                        	break;
                        }
                    }
                    if (childAttrs.getNamedItem("tailSz")!=null) {
                        switch(childAttrs.getNamedItem("tailSz").getNodeValue()) {                   // 화살표 시작 모양
                        case "SMALL_SMALL":
                        	lineTailSz = LineArrowSize.SMALL_SMALL;		break;
                        case "SMALL_MEDIUM":
                        	lineTailSz = LineArrowSize.SMALL_MEDIUM;	break;
                        case "SMALL_LARGE":
                        	lineTailSz = LineArrowSize.SMALL_LARGE;		break;
                        case "MEDIUM_SMALL":
                        	lineTailSz = LineArrowSize.MEDIUM_SMALL;	break;
                        case "MEDIUM_MEDIUM":
                        	lineTailSz = LineArrowSize.MEDIUM_MEDIUM;	break;
                        case "MEDIUM_LARGE":
                        	lineTailSz = LineArrowSize.MEDIUM_LARGE;	break;
                        case "LARGE_SMALL":
                        	lineTailSz = LineArrowSize.LARGE_SMALL;		break;
                        case "LARGE_MEDIUM":
                        	lineTailSz = LineArrowSize.LARGE_MEDIUM;	break;
                        case "LARGE_LARGE":
                        	lineTailSz = LineArrowSize.LARGE_LARGE;		break;
                        default:
                        	lineTailSz = LineArrowSize.MEDIUM_MEDIUM;	break;
                        }
                    }
                    node.removeChild(child);
                }
                break;
            case "hc:fillBrush":    // 그리기 객체의 채우기 정보
                fill = HwpRecord_BorderFill.readFillBrush(child);
                node.removeChild(child);
                break;
            case "hp:drawText":     // 그리기 객체 글상자용 텍스트   178 page
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("lastWidth").getNodeValue();   // 텍스트 문자열의 최대 폭
                    maxTxtWidth = Integer.parseInt(numStr);
                    /*
                    childAttrs.getNamedItem("width").getNodeValue();  // 글 상자 이름
                    childAttrs.getNamedItem("style").getNodeValue()); // 편집 가능 여부
                    */
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        switch(grandChild.getNodeName()) {  // 179 page
                        case "hp:textMargin":
                            NamedNodeMap grandAttrs = grandChild.getAttributes();
                            numStr = grandAttrs.getNamedItem("left").getNodeValue();
                            leftSpace = (short) Integer.parseInt(numStr);
                            numStr = grandAttrs.getNamedItem("right").getNodeValue();
                            rightSpace = (short) Integer.parseInt(numStr);
                            numStr = grandAttrs.getNamedItem("top").getNodeValue();
                            upSpace = (short) Integer.parseInt(numStr);
                            numStr = grandAttrs.getNamedItem("bottom").getNodeValue();
                            downSpace = (short) Integer.parseInt(numStr);
                            break;
                        case "hp:subList":
                            do_subList(grandChild, version);
                            break;
                        default:
                        	if (log.isLoggable(Level.FINE)) {
                        		throw new NotImplementedException("Ctrl_GeneralShape");
                        	}
                        	break;
                        }
                    }
                    node.removeChild(child);
                }
                break;
            case "hp:shadow":       // 그리기 객체의 그림자 정보
                /*
                NamedNodeMap childAttrs = child.getAttributes();
                childAttrs.getNamedItem("type").getNodeValue();
                childAttrs.getNamedItem("color").getNodeValue();
                childAttrs.getNamedItem("offsetX").getNodeValue();
                childAttrs.getNamedItem("offsetY").getNodeValue();
                childAttrs.getNamedItem("alpha").getNodeValue();
                */
                node.removeChild(child);
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
            case "hc:pt0":
                
                break;
            case "hc:pt1":
                
                break;
            case "hc:pt2":
                
                break;
            case "hc:pt3":
                
                break;
            case "hp:sz":
                
                break;
            case "hp:pos":
                
                break;
            case "hp:outMargin":
                
                break;
            case "hp:imgRect":
                
                break;
            case "hp:imgClip":
                
                break;
            case "hp:inMargin":
                
                break;
            case "hp:imgDim":
                
                break;
            case "hc:img":
                
                break;
            case "hp:effects":
                
                break;
            case "hp:shapeComment":
            	
                node.removeChild(child);
                break;
            default:
            	log.fine(child.getNodeName() + "=" + child.getNodeValue());
            	if (log.isLoggable(Level.FINE)) {
            		throw new NotImplementedException("Ctrl_GeneralShape");
            	}
            	break;
            }
        }
    }


    private void do_subList(Node node, int version) throws NotImplementedException {
        if (paras==null) {
            paras = new ArrayList<HwpParagraph>();
        }
        
        NamedNodeMap attrs = node.getAttributes();
        if (attrs.getNamedItem("textDirection")!=null) {
        	switch(attrs.getNamedItem("textDirection").getNodeValue()) {
        	case "HORIZONTAL":
        		break;
            case "VERTICAL":
                break;
        	}
        }
        if (attrs.getNamedItem("lineWrap")!=null) {
        	switch(attrs.getNamedItem("lineWrap").getNodeValue()) {
        	case "BREAK":	break;
            case "SQUEEZE":	break;
        	}
        }
        if (attrs.getNamedItem("vertAlign")!=null) {
        	textVerAlign = VertAlign.valueOf(attrs.getNamedItem("vertAlign").getNodeValue());
        }
        // attrs.getNamedItem("linkListIDRef").getNodeValue()
        // attrs.getNamedItem("linkListNextIDRef").getNodeValue()
        // attrs.getNamedItem("textWidth").getNodeValue()
        // attrs.getNamedItem("textHeight").getNodeValue()
        // attrs.getNamedItem("hasTextRef").getNodeValue()
        // attrs.getNamedItem("hasNumRef").getNodeValue()
        
        NodeList nodeList = node.getChildNodes();
        int nodeLength = nodeList.getLength();
        for (int i=0; i<nodeLength; i++) {
            Node child = nodeList.item(i);
            Ctrl latestCtrl = null;
            switch(child.getNodeName()) {
            case "hp:p":
                HwpParagraph p = new HwpParagraph(child, version);
                paras.add(p);
                latestCtrl = (p.p==null ? null : p.p.getLast());
                break;
            }
        
            // ParaBreak를 subList 중간에 하나씩 강제로 넣는다. Paragraph 단위로 다음줄에 써지도록
            if (latestCtrl != null && latestCtrl instanceof ParaText && i<nodeLength-1) {
                HwpParagraph breakP = new HwpParagraph(child, version);
                if (breakP.p != null) {
                    breakP.p.clear();
                } else {
                    breakP.p = new LinkedList<Ctrl>();
                }
                breakP.p.add(new Ctrl_Character("   _", CtrlCharType.PARAGRAPH_BREAK));
                paras.add(breakP);
            }
        }
	}
	
	public static Ctrl_GeneralShape parse(Ctrl_GeneralShape obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        // hwp포맷에는  역순으로 ctrlId를 구성한다. 여기서는 순방향으로 구성한다.
        String ctrlId = new String(buf, offset, 4, StandardCharsets.US_ASCII);
        offset += 4;
        Ctrl_GeneralShape shape = null;
        
        log.fine("                                                  ctrlID="+ctrlId);
        // ctrlId를 거꾸로 읽어 비교한다.
        switch(ctrlId) {
        case "cip$":    // 그림       ShapePic obj = new ShapePic(shape);
            shape = new Ctrl_ShapePic(obj);
            offset += Ctrl_ShapePic.parseCtrl((Ctrl_ShapePic)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "cer$":    // 사각형
            shape = new Ctrl_ShapeRect(obj);
            offset += Ctrl_ShapeRect.parseCtrl((Ctrl_ShapeRect)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "nil$":    // 선
        case "loc$":    // 개체연결선
            shape = new Ctrl_ShapeLine(obj);
            offset += Ctrl_ShapeLine.parseCtrl((Ctrl_ShapeLine)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "noc$":    // 묶음 개체
            shape = new Ctrl_Container(obj);
            offset += Ctrl_Container.parseCtrl((Ctrl_Container)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "lle$":    // 타원
            shape = new Ctrl_ShapeEllipse(obj);
            offset += Ctrl_ShapeEllipse.parseCtrl((Ctrl_ShapeEllipse)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "lop$":    // 다각형
            shape = new Ctrl_ShapePolygon(obj);
            offset += Ctrl_ShapePolygon.parseCtrl((Ctrl_ShapePolygon)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "cra$":    // 호
            shape = new Ctrl_ShapeArc(obj);
            offset += Ctrl_ShapeArc.parseCtrl((Ctrl_ShapeArc)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "ruc$":    // 곡선
            shape = new Ctrl_ShapeCurve(obj);
            offset += Ctrl_ShapeCurve.parseCtrl((Ctrl_ShapeCurve)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "deqe":    // 한글97 수식
            shape = new Ctrl_EqEdit(obj);
            offset += Ctrl_EqEdit.parseCtrl((Ctrl_EqEdit)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
        case "elo$":    // OLE
            shape = new Ctrl_ShapeOle(obj);
            offset += Ctrl_ShapeOle.parseCtrl((Ctrl_ShapeOle)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "div$":    // Video
            shape = new Ctrl_ShapeVideo(obj);
            offset += Ctrl_ShapeVideo.parseCtrl((Ctrl_ShapeVideo)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        case "tat$":    // TextArt(글맵시)
            shape = new Ctrl_ShapeTextArt(obj);
            offset += Ctrl_ShapeTextArt.parseCtrl((Ctrl_ShapeTextArt)shape, size-(offset-off), buf, offset, version);
            shape.ctrlId = ctrlId;
            break;
        default:
            log.severe("Neither known ctrlID=" + ctrlId+" nor implemented.");
        	break;
        }
        
        if (offset-off-size!=0) {
            log.fine("[CtrlID]=" + ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
        }
        
        return shape;
    }

    public static int parseListHeaderAppend(Ctrl_GeneralShape obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        if (size>=16) {
            offset += 2;
            obj.captionAttr     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            obj.captionWidth    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            obj.captionSpacing  = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
            obj.captionMaxW     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
        }
        if (size-(offset-off)==8) {
            offset += 8;
        }
        
        if (log.isLoggable(Level.FINE)) {
            log.fine("                                                  ctrlID="+obj.ctrlId+", 캡션 parsing이지만, 정확한 parsing은 어떻게 해야 하는지 알 수 없음.");
        }
        
        if (offset-off-size!=0) {
            if (log.isLoggable(Level.SEVERE)) {
                log.severe("[CtrlID]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            }
            // throw new HwpParseException();
        }
        
        return size;
    }
    
    public static int parseCtrl(Ctrl_GeneralShape obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        int len = Ctrl_ObjElement.parseCtrl((Ctrl_ObjElement)obj, size, buf, offset, version);
        offset += len;
        
        obj.lineColor   = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
        offset += 4;
        obj.lineThick   = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        
        // 문서와 다르게  선 굵기에서 4byte 후에 선 속성이 온다.
        offset += 2;
        
        int lineAttr    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.lineStyle   = LineStyle2.from(lineAttr&0x3F);
        obj.lineHead	= LineArrowStyle.from((lineAttr>>10)&0x3F, ((lineAttr>>30)&0x1)==1);
        obj.lineHeadSz	= LineArrowSize.from((lineAttr>>22)&0x0F);
        obj.lineTail	= LineArrowStyle.from((lineAttr>>16)&0x3F, ((lineAttr>>31)&0x1)==1);
        obj.lineTailSz  = LineArrowSize.from((lineAttr>>26)&0x0F);
        obj.outline     = buf[offset++];
        
        obj.fill = new Fill(buf, offset, size-(offset-off)-22);
        offset += obj.fill.getSize();
        
        // 글상자 텍스트 속성.  아래 내용대로 읽히지 않는다.  알 수 없는 22bytes가 온다.
        offset += 22;
        
        if (log.isLoggable(Level.FINEST)) {
            log.finest("[그리기 개체 공통 속성]을 읽었습니다.");
            log.finest("[그리기 개체 글상자용 텍스트 속성]을 읽었습니다. [문단 리스트 헤더]를 읽어야 합니다.");
        }
        
        return offset-off;
    }
    
    public String toString() {
        StringBuffer strb = new StringBuffer();
        strb.append("CTRL("+ctrlId+")")
            .append("=공통속성:"+super.toString());
        return strb.toString();
    }
    
    public void setParent(HwpParagraph para) {
        this.parent = para;
    }
    public HwpParagraph getParent() {
        return parent;
    }
    
    @Override
    public int getSize() {
        return size;
    }
}
