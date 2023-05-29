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
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.star.text.ControlCharacter;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.paragraph.Ctrl_Character.CtrlCharType;

public class HwpParagraph {
    private static final Logger log = Logger.getLogger(HwpParagraph.class.getName());

	public short			paraShapeID;	// HWPTAG_PARA_HEADER
	public short			paraStyleID;	// HWPTAG_PARA_HEADER
	public byte 			breakType;		// HWPTAG_PARA_HEADER
	// public List<CharShape>	charShapes;		// HWPTAG_PARA_CHAR_SHAPE
	public LineSeg			lineSegs;		// HWPTAG_PARA_LINE_SEG
	public List<RangeTag>	rangeTags;		// HWPTAG_PARA_RANGE_TAG

	public LinkedList<Ctrl> p;              // HWPTAG_PARA_TEXT + List<Ctrl>  V2
	
	public HwpParagraph() { }

	public HwpParagraph(Node node, int version) throws NotImplementedException {

        NamedNodeMap attributes = node.getAttributes();
        
        // id값은 처리하지 않는다. List<HwpRecord_Style>에 순차적으로 추가한다.
        // String id = attributes.getNamedItem("id").getNodeValue();

        String numStr = attributes.getNamedItem("paraPrIDRef").getNodeValue();
        paraShapeID = (short) Integer.parseInt(numStr);

        numStr = attributes.getNamedItem("styleIDRef").getNodeValue();
        paraStyleID = (short) Integer.parseInt(numStr);

        switch(attributes.getNamedItem("pageBreak").getNodeValue()) {
        case "0":
            breakType &= 0b11111011;   break;      // 0:구역나누기, 2:다단나누기, 4:쪽 나누기, 8:단 나누기
        case "1":
            breakType |= 0b00000100;   break;
        default:
            throw new NotImplementedException("HwpParagraph");
        }

        switch(attributes.getNamedItem("columnBreak").getNodeValue()) {
        case "0":
            breakType &= 0b11110111;   break;      // 0:구역나누기, 2:다단나누기, 4:쪽 나누기, 8:단 나누기
        case "1":
            breakType |= 0b00001000;   break;
        default:
            throw new NotImplementedException("HwpParagraph");
        }

        // attributes.getNamedItem("merged").getNodeValue();
        // attributes.getNamedItem("paraTcId").getNodeValue();

        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "hp:run":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    CharShape charShape = new CharShape();
                    
                    numStr = childAttrs.getNamedItem("charPrIDRef").getNodeValue();
                    charShape.charShapeID = Integer.parseInt(numStr);
                    if (childAttrs.getNamedItem("charTcId")!=null) {
                        numStr = childAttrs.getNamedItem("charTcId").getNodeValue();
                        charShape.start = Integer.parseInt(numStr);
                    }
                    
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        parseHwpParagraph(grandChild, charShape.charShapeID, version);
                    }
                }
                break;
            case "hp:linesegarray":
	            {
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        switch(grandChild.getNodeName()) {
                        case "hp:lineseg":	
                            lineSegs = new LineSeg(grandChild, version);
                        	break;
                        }
                    }
	            }
                break;
            default:
                throw new NotImplementedException("HwpParagraph");
            }
        }
        
        // 마지막에 PARA_BREAK로 끝나지 않았다면 PARA_BREAK를 삽입
        if (p!=null && p.size()>0 && !(p.getLast() instanceof Ctrl_Character)) {
            p.add(new Ctrl_Character("   _", CtrlCharType.PARAGRAPH_BREAK));
        }
	}
	
	private void parseHwpParagraph(Node node, int charShapeId, int version) throws NotImplementedException {

        if (p == null) {
            p = new LinkedList<Ctrl>();
        }

        String numStr;
	    Ctrl ctrl;
	    
	    switch(node.getNodeName()) {
	    case "hp:secPr":
    	    {
    	        ctrl = new Ctrl_SectionDef("dces", node, version);
    	        p.add(ctrl);
    	    }
    	    break;
	    case "hp:ctrl":
    	    {
                NodeList nodeList = node.getChildNodes();
                for (int j=0; j<nodeList.getLength(); j++) {
                    Node child = nodeList.item(j);
                    ctrl = Ctrl.getCtrl(child, version);
                    p.add(ctrl);
                }
    	    }
    	    break;
	    case "hp:t":
            {
                NamedNodeMap attrs = node.getAttributes();

                if (attrs.getNamedItem("charPrIDRef")!=null) {
                    CharShape charShape = new CharShape();
                    numStr = attrs.getNamedItem("charPrIDRef").getNodeValue();
                    charShape.charShapeID = Integer.parseInt(numStr);
                }
                
                NodeList nodeList = node.getChildNodes();
                for (int j=0; j<nodeList.getLength(); j++) {
                    Node child = nodeList.item(j);
                    switch(child.getNodeName()) {
                    case "#text":
                        p.add(new ParaText("____", child.getNodeValue(), 0, charShapeId));
                        break;
                    case "hp:lineBreak":
                        p.add(new Ctrl_Character("   _", CtrlCharType.LINE_BREAK));
                        break;
                    case "hp:hyphen":
                        p.add(new Ctrl_Character("   _", CtrlCharType.HARD_HYPHEN));
                        break;
                    case "hp:nbSpace":
                    case "hp:fwSpace":
                        p.add(new Ctrl_Character("   _", CtrlCharType.HARD_SPACE));
                        break;
                    case "hp:markpenBegin":    // 134 page
                    case "hp:markpenEnd":
                    case "hp:titleMark":
                    case "hp:tab":
                    case "hp:insertBegin":
                    case "hp:insertEnd":
                    case "hp:deleteBegin":
                    case "hp:deleteEnd":
                        throw new NotImplementedException("hp:t");
                    }
                }
            }
            break;
	    case "hp:tbl":
	        ctrl = new Ctrl_Table(" lbt" , node, version);
            p.add(ctrl);
            break;
	    case "hp:pic":
	        ctrl = new Ctrl_ShapePic("cip$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:container":
	        ctrl = new Ctrl_Container("noc$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:ole":
	        ctrl = new Ctrl_ShapeOle("elo$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:equation":
	        ctrl = new Ctrl_EqEdit("deqe", node, version);
            p.add(ctrl);
	        break;
	    case "hp:line":
	        ctrl = new Ctrl_ShapeLine("nil$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:rect":
	        ctrl = new Ctrl_ShapeRect("cer$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:ellipse":
	        ctrl = new Ctrl_ShapeEllipse("lle$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:arc":
	        ctrl = new Ctrl_ShapeArc("cra$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:polygon":
	        ctrl = new Ctrl_ShapePolygon("lop$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:curve":
	        ctrl = new Ctrl_ShapeCurve("ruc$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:connectLine":
	        ctrl = new Ctrl_ShapeConnectLine("loc$", node, version);
            p.add(ctrl);
	        break;
	    case "hp:textart":
	        ctrl = new Ctrl_ShapeTextArt("tat$", node, version);
            p.add(ctrl);
	        break;
        case "hp:video":
            ctrl = new Ctrl_ShapeVideo("div$", node, version);
            p.add(ctrl);
            break;
	    case "hp:compose":
	    case "hp:dutmal":
	    case "hp:btn":
	    case "hp:radioBtn":
	    case "hp:checkBtn":
	    case "hp:comboBox":
	    case "hp:edit":
	    case "hp:listBox":
	    case "hp:scrollBar":
	        break;
        default:
            throw new NotImplementedException("parseHwpPargraph");
	    }
	}

	public static HwpParagraph parse(int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        HwpParagraph para = new HwpParagraph();
        
        int nchars          = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        if ((nchars&0x80000000)!=0) {
            nchars &= 0x7fffffff;
        }
        int controlMask     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        para.paraShapeID    = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        para.paraStyleID    = buf[offset++];
        para.breakType      = buf[offset++];
        short nCharShapeInfo= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        short nRangeTags    = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        short nLineSeg      = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        int paraInstanceID  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        if (version>=5032 && offset-off<size) {
            short changeTrackingMerge= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
        }
        log.fine("                                                  "
                +"instance="+String.format("0x%X", paraInstanceID)
                +",문단모양ID="+para.paraShapeID
                +",스타일ID="+para.paraStyleID
                +",단나누기종류="+para.breakType
                +",nchars="+nchars
                +",nLineSeg="+nLineSeg
                +",controlMask="+controlMask
                +",nCharShapeInfo="+nCharShapeInfo
                +",nRangeTags="+nRangeTags
                +",paraInstanceID="+paraInstanceID
                );

        if (offset-off-size != 0 && offset-off!=24) {
            log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
            throw new HwpParseException();
        }
        
        return para;
    }

    public static int parse(HwpParagraph para, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        int nchars          = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        if ((nchars&0x80000000)!=0) {
            nchars &= 0x7fffffff;
        }
        int controlMask     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        para.paraShapeID    = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        para.paraStyleID    = buf[offset++];
        para.breakType      = buf[offset++];
        short nCharShapeInfo= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        short nRangeTags    = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        short nLineSegs     = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        int paraInstanceID  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        if (version>=5032 && offset-off<size) {
            short changeTrackingMerge= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
        }
        log.fine("                                                  "
                +"instance="+String.format("0x%X", paraInstanceID)
                +",문단모양ID="+para.paraShapeID
                +",스타일ID="+para.paraStyleID
                +",단나누기종류="+para.breakType
                +",nchars="+nchars
                +",nLineSeg="+nLineSegs
                +",controlMask="+controlMask
                +",nCharShapeInfo="+nCharShapeInfo
                +",nRangeTags="+nRangeTags
                +",paraInstanceID="+paraInstanceID
                );

        if (offset-off-size != 0 && offset-off!=24) {
            log.fine("[PARA] size=" + size + ", but currentSize=" + (offset-off));
            throw new HwpParseException();
        }

        return offset-off;
    }
}
