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
package HwpDoc;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecord_CtrlData;
import HwpDoc.HwpElement.HwpRecord_CtrlHeader;
import HwpDoc.HwpElement.HwpRecord_FormObject;
import HwpDoc.HwpElement.HwpRecord_ListHeader;
import HwpDoc.HwpElement.HwpRecord_ParaRangeTag;
import HwpDoc.HwpElement.HwpRecord_ParaText;
import HwpDoc.HwpElement.HwpTag;
import HwpDoc.paragraph.CapParagraph;
import HwpDoc.paragraph.CellParagraph;
import HwpDoc.paragraph.CharShape;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_Character;
import HwpDoc.paragraph.Ctrl_Common;
import HwpDoc.paragraph.Ctrl_Common.VertAlign;
import HwpDoc.paragraph.Ctrl_Container;
import HwpDoc.paragraph.Ctrl_EqEdit;
import HwpDoc.paragraph.Ctrl_Form;
import HwpDoc.paragraph.Ctrl_GeneralShape;
import HwpDoc.paragraph.Ctrl_HeadFoot;
import HwpDoc.paragraph.Ctrl_Note;
import HwpDoc.paragraph.Ctrl_SectionDef;
import HwpDoc.paragraph.Ctrl_ShapeArc;
import HwpDoc.paragraph.Ctrl_ShapeCurve;
import HwpDoc.paragraph.Ctrl_ShapeEllipse;
import HwpDoc.paragraph.Ctrl_ShapeLine;
import HwpDoc.paragraph.Ctrl_ShapeOle;
import HwpDoc.paragraph.Ctrl_ShapePic;
import HwpDoc.paragraph.Ctrl_ShapePolygon;
import HwpDoc.paragraph.Ctrl_ShapeRect;
import HwpDoc.paragraph.Ctrl_ShapeTextArt;
import HwpDoc.paragraph.Ctrl_ShapeVideo;
import HwpDoc.paragraph.Ctrl_Table;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.LineSeg;
import HwpDoc.paragraph.TblCell;
import HwpDoc.paragraph.Ctrl_Character.CtrlCharType;
import HwpDoc.section.NoteShape;
import HwpDoc.section.Page;
import HwpDoc.section.PageBorderFill;

public class HwpSection {
    private static final Logger log = Logger.getLogger(HwpSection.class.getName());
    private HwpFile             parentHwp;
    private HwpxFile            parentHwpx;
    
    public	List<HwpParagraph>	paraList;
    
    public HwpSection(HwpFile hwp) {
        this.parentHwp = hwp;
        paraList = new ArrayList<HwpParagraph>();
    }
    
    public HwpSection(HwpxFile hwpx) {
        this.parentHwpx = hwpx;
        paraList = new ArrayList<HwpParagraph>();
    }
    
    boolean read(Document document, int version) throws NotImplementedException {
        Element element = document.getDocumentElement();
        paraList = new ArrayList<HwpParagraph>();
        
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            HwpParagraph para = null;
            switch(node.getNodeName()) {
            case "hp:p":
                para = new HwpParagraph(parentHwpx, node, version);
                paraList.add(para);
                break;
            }
        }
        return true;
    }
    
    boolean parse(byte[] buf, int version) throws HwpParseException {
        int off = 0;
        
        while(off < buf.length) {
            int header = buf[off+3]<<24&0xFF000000 | buf[off+2]<<16&0xFF0000 | buf[off+1]<<8&0xFF00 | buf[off]&0xFF;
            int tagNum = header&0x3FF;				// 10 bits (0 - 9 bit)
            int level = (header&0xFFC00)>>>10;		// 10 bits (10-19 bit)
            int size =  (header&0xFFF00000)>>>20;	// 12 bits (20-31 bit)
            
            if (level>0) {
                HwpParagraph para = paraList.stream().reduce((a,b)->b).get();
                off += parseRecurse(para, level, buf, off, version);
            } else {
                
                if (size==0xFFF) {
                    size = buf[off+7]<<24&0xFF000000 | buf[off+6]<<16&0xFF0000 | buf[off+5]<<8&0xFF00 | buf[off+4]&0xFF;
                    off += 8;
                } else {
                    off += 4;
                }
                
                HwpTag tag = HwpTag.from(tagNum);
                log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())+"[TAG]="+tag.toString()+" ("+size+")");
                
                if (level==0 && tag==HwpTag.HWPTAG_PARA_HEADER) {
                    HwpParagraph currPara = HwpParagraph.parse(tagNum, level, size, buf, off, version);
                    paraList.add(currPara);
                    off += size;
                }
            }
        }
        
        return true;
    }
    
    private int parseRecurse(HwpParagraph currPara, int runLevel, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        while(offset < buf.length) {
            int header = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
            int tagNum = header&0x3FF;				// 10 bits (0 - 9 bit)
            int level = (header&0xFFC00)>>>10;		// 10 bits (10-19 bit)
            int size =  (header&0xFFF00000)>>>20;	// 12 bits (20-31 bit)
            int headerOffset = 0;
            
            if (size==0xFFF) {
                size = buf[offset+7]<<24&0xFF000000 | buf[offset+6]<<16&0xFF0000 | buf[offset+5]<<8&0xFF00 | buf[offset+4]&0xFF;
                headerOffset = 8;
            } else {
                headerOffset = 4;
            }
            
            if (level < runLevel) {
                break;
            }
            
            HwpTag tag = HwpTag.from(tagNum);
            if (level > runLevel) {
                log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())+"[TAG]="+tag.toString()+" > runLevel");
                
                switch(tag) {
                case HWPTAG_PARA_HEADER:
                case HWPTAG_PARA_TEXT:
                case HWPTAG_PARA_CHAR_SHAPE:
                case HWPTAG_PARA_LINE_SEG:
                case HWPTAG_PARA_RANGE_TAG:
                case HWPTAG_CTRL_HEADER:
                    offset += parseRecurse(currPara, level, buf, offset, version);
                    break;
                case HWPTAG_TABLE:
                    {
                        Ctrl_Table table = (Ctrl_Table)currPara.p.stream().filter(c -> (c instanceof Ctrl_Table)).reduce((a,b)->b).get();
                        offset += parseCtrlRecurse(table, level, buf, offset, version);
                    }
                    break;
                case HWPTAG_LIST_HEADER:
                    offset += headerOffset;
                    offset += size;
                    break;
                case HWPTAG_PAGE_DEF:
                case HWPTAG_FOOTNOTE_SHAPE:
                case HWPTAG_PAGE_BORDER_FILL:
                    {   // dces 컨트롤에서만 처리
                        Ctrl_SectionDef ctrlSecd = (Ctrl_SectionDef)currPara.p.stream().filter(c -> (c.ctrlId.equals("dces"))).reduce((a,b)->b).get();
                        offset += parseCtrlRecurse(ctrlSecd, level, buf, offset, version);
                    }
                    break;
                case HWPTAG_SHAPE_COMPONENT:
                case HWPTAG_SHAPE_COMPONENT_PICTURE:
                case HWPTAG_SHAPE_COMPONENT_LINE:
                case HWPTAG_SHAPE_COMPONENT_RECTANGLE:
                case HWPTAG_SHAPE_COMPONENT_ELLIPSE:
                case HWPTAG_SHAPE_COMPONENT_ARC:
                case HWPTAG_SHAPE_COMPONENT_POLYGON:
                case HWPTAG_SHAPE_COMPONENT_CURVE:
                case HWPTAG_SHAPE_COMPONENT_OLE:
                case HWPTAG_EQEDIT:
                case HWPTAG_SHAPE_COMPONENT_TEXTART:
                case HWPTAG_SHAPE_COMPONENT_UNKNOWN:
                    {   // " osg" 컨트롤에서만 처리
                        Ctrl_GeneralShape ctrlGeneral = (Ctrl_GeneralShape)currPara.p.stream().filter(c -> (c.ctrlId.equals(" osg"))).reduce((a,b)->b).get();
                        offset += parseCtrlRecurse(ctrlGeneral, level, buf, offset, version);
                    }
                    break;
                case HWPTAG_CTRL_DATA:
                case HWPTAG_FORM_OBJECT:
                case HWPTAG_MEMO_SHAPE:
                case HWPTAG_MEMO_LIST:
                case HWPTAG_CHART_DATA:
                case HWPTAG_VIDEO_DATA:
                default:
                    {
                        // 마지막 컨트롤을 기준으로 parseRecurse
                        Ctrl_Common ctrlCommon = (Ctrl_Common) currPara.p.stream().filter(c -> (c instanceof Ctrl_Common)).reduce((a,b)->b).get();
                        offset += parseCtrlRecurse(ctrlCommon, level, buf, offset, version);
                    }
                }
            } else if (level==runLevel) {
                offset += headerOffset;
                log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())+"[TAG]="+tag.toString()+" ("+size+") = runLevel");
                
                switch(tag) {
                case HWPTAG_PARA_HEADER:
                    if (currPara instanceof CellParagraph) {
                        
                        return offset-headerOffset-off;  // CELL에 여러개의 PARA가 들어가기 위해 필요.
                    } else if (currPara instanceof CapParagraph) {
                        offset += HwpParagraph.parse(currPara, size, buf, offset, version);	// 캡션을 읽기 위해 필요.
                        break;
                    } else {
                        if (runLevel==1) {	// runLevel=1에서 HWPTAG_PARA_HEADER 예상하지 못함. PARA_HEADER 뒤에 PARA_HEADER 인 경우,
                            return offset-off + size;// runLevel=1일 경우, PARA_HEADER skip하도록 함.
                        }
                        return offset-headerOffset-off;  // CELL에 여러개의 PARA가 들어가기 위해 필요.
                    }
                case HWPTAG_PARA_TEXT:
                    {
                        if (currPara.p==null)   currPara.p = new LinkedList<>();
                        currPara.p.addAll(HwpRecord_ParaText.parse(tagNum, level, size, buf, offset, version));    // paraText를 LinkedList로 변경하자
                        offset += size;
                    }
                    break;
                case HWPTAG_PARA_CHAR_SHAPE:
	                {
	                	if (currPara.p == null)  {
	                		currPara.p = new LinkedList<>();
	                		currPara.p.add(new Ctrl_Character("   _", CtrlCharType.PARAGRAPH_BREAK));
	                	}
	                    CharShape.fillCharShape(tagNum, level, size, buf, offset, version, currPara.p);
	                    offset += size;
	                }
                    break;
                case HWPTAG_PARA_LINE_SEG:
                    currPara.lineSegs = new LineSeg(tagNum, level, size, buf, offset, version);
                    offset += size;
                    break;
                case HWPTAG_PARA_RANGE_TAG:
                    HwpRecord_ParaRangeTag.parse(currPara, tagNum, level, size, buf, offset, version);
                    offset += size;
                    break;
                case HWPTAG_CTRL_HEADER:
                    {
                        Ctrl ctrl = HwpRecord_CtrlHeader.parse(tagNum, level, size, buf, offset, version);
                        if (ctrl instanceof Ctrl_GeneralShape) {
                            ((Ctrl_GeneralShape) ctrl).setParent(currPara);
                        }
                        Optional<Ctrl> ctrlOrigOp = currPara.p.stream().filter(c -> (c.ctrlId.equals(ctrl.ctrlId)))
                                                           .filter(c -> c.fullfilled==false)
                                                           .findFirst();
                        if (ctrlOrigOp.isPresent()) {
                            int linkedListIdx = currPara.p.indexOf(ctrlOrigOp.get());
                            currPara.p.set(linkedListIdx, ctrl);
                        }
                            
                        if (ctrl instanceof Ctrl_HeadFoot) {
                            Optional<Ctrl> secd2Op = currPara.p.stream().filter(c -> (c.ctrlId.equals("dces"))).reduce((a,b)->b);
                            if (secd2Op.isPresent()) {
                                Ctrl_SectionDef secd = (Ctrl_SectionDef) secd2Op.get();
                                if (secd.headerFooter==null) secd.headerFooter = new ArrayList<Ctrl_HeadFoot>();
                                secd.headerFooter.add((Ctrl_HeadFoot)ctrl);
                            }
                        }
                        
                        // HWPTAG_LIST_HEADER를 통해  캡션을 얻어오기 위해 조치. parseCtrlRecurs가 없으면  ParseParaRecurs에서 무한루프.
                        if (ctrl instanceof Ctrl_Table) {
                            offset += size;
                            offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                        } else {
                            offset += size;
                            offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                        }
                        // HWPTAG_LIST_HEADER를 통해  캡션을 얻어오기 위해 조치. parseCtrlRecurs가 없으면  ParseParaRecurs에서 무한루프.
                    }
                    break;
                case HWPTAG_TABLE:
                    return offset-headerOffset-off;
                case HWPTAG_LIST_HEADER:
                    if (runLevel==1) {	// runLevel=1에서 LIST_HEADER 예상하지 못함.  CTRL이 아닌 PARA_HEADER 다음에 PARA_HEADER 
                        return offset-off + size;	// runLevel=1일 경우, LIST_HEADER skip하도록 함.
                    }
                    return offset-headerOffset-off;
                case HWPTAG_SHAPE_COMPONENT:
                case HWPTAG_SHAPE_COMPONENT_PICTURE:
                case HWPTAG_SHAPE_COMPONENT_LINE:
                case HWPTAG_SHAPE_COMPONENT_RECTANGLE:
                case HWPTAG_SHAPE_COMPONENT_ELLIPSE:
                case HWPTAG_SHAPE_COMPONENT_ARC:
                case HWPTAG_SHAPE_COMPONENT_POLYGON:
                case HWPTAG_SHAPE_COMPONENT_CURVE:
                case HWPTAG_SHAPE_COMPONENT_OLE:
                case HWPTAG_EQEDIT:
                case HWPTAG_SHAPE_COMPONENT_TEXTART:
                    return offset-headerOffset-off;
                case HWPTAG_CTRL_DATA:
                case HWPTAG_FORM_OBJECT:
                case HWPTAG_MEMO_SHAPE:
                case HWPTAG_MEMO_LIST:
                case HWPTAG_CHART_DATA:
                case HWPTAG_VIDEO_DATA:
                case HWPTAG_SHAPE_COMPONENT_UNKNOWN:
                    offset += size;
                    break;
                default:
                }
            }
        }
        
        return offset-off;
    }
    
    int parseCtrlRecurse(Ctrl currCtrl, int runLevel, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        Ctrl ctrl = currCtrl;
        
        while (offset < buf.length) {
            int header = buf[offset + 3] << 24 & 0xFF000000 | buf[offset + 2] << 16 & 0xFF0000 | buf[offset + 1] << 8 & 0xFF00 | buf[offset] & 0xFF;
            int tagNum = header & 0x3FF; // 10 bits (0 - 9 bit)
            int level = (header & 0xFFC00) >>> 10; // 10 bits (10-19 bit)
            int size = (header & 0xFFF00000) >>> 20; // 12 bits (20-31 bit)
            int headerOffset = 0;
            
            if (size == 0xFFF) {
                size = buf[off + 7] << 24 & 0xFF000000 | buf[off + 6] << 16 & 0xFF0000 | buf[off + 5] << 8 & 0xFF00 | buf[off + 4] & 0xFF;
                headerOffset = 8;
            } else {
                headerOffset = 4;
            }
            
            if (level < runLevel) {
                break;
            }
            
            HwpTag tag = HwpTag.from(tagNum);
            if (level > runLevel) {
                log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())
                        + "[TAG]=" + tag.toString() + " (" + size + ") > runLevel");
                
                switch (tag) {
                case HWPTAG_PARA_HEADER:
                    if (ctrl instanceof Ctrl_Common) {
                        offset += parseCtrlRecurse((Ctrl_Common) ctrl, level, buf, offset, version);
                    } else {
                        offset += headerOffset;
                        log.severe("Unknown CtrlId=" + ctrl.ctrlId + ". Skip HWPTAG_PARA_HEADER");
                        offset += size;
                    }
                    break;
                case HWPTAG_PARA_TEXT:
                    if (ctrl instanceof Ctrl_Common) {
                        HwpParagraph lastPara = ((Ctrl_Common) ctrl).paras.get(((Ctrl_Common) ctrl).paras.size() - 1);
                        offset += parseRecurse(lastPara, level, buf, offset, version);
                    } else {
                        offset += headerOffset;
                        log.severe("Unknown CtrlId=" + ctrl.ctrlId + ". Skip HWPTAG_PARA_HEADER");
                        offset += size;
                    }
                    break;
                case HWPTAG_LIST_HEADER:
                    // LIST_HEADER만 recursive하게 처리하지 않고, 여기서 처리한다.
                    offset += headerOffset;
                    int subParaCount = HwpRecord_ListHeader.getCount(tagNum, level, size, buf, offset, version);
                    offset += 6; // 문단수 2byte, 속성 4byte
                    
                    if (ctrl instanceof Ctrl_Table) {
                        int len = parseListAppend((Ctrl_Common) ctrl, size - 6, buf, offset, version);
                        offset += len;
                        
                        // 캡션이냐, Cell이냐 무엇으로 해석할지 판단.
                        if (((Ctrl_Table) ctrl).cells != null) { // HWPTAG_TABLE을 지났을때는 Cell로 해석하자.
                            
                        } else { // HWPTAG_TABLE을 거치지 않았을때는 캡션으로 해석.
                            if (((Ctrl_Table) ctrl).caption == null)
                                ((Ctrl_Table) ctrl).caption = new ArrayList<CapParagraph>();
                            CapParagraph newPara = new CapParagraph();
                            ((Ctrl_Table) ctrl).caption.add(newPara);
                            offset += parseRecurse(newPara, level, buf, offset, version);
                        }
                    } else if (ctrl instanceof Ctrl_ShapeRect) {
                        Ctrl_Common ctrlCmn = (Ctrl_Common) ctrl;
                        offset -= 6;
                        ctrlCmn.textVerAlign = HwpRecord_ListHeader.getVertAlign(6, buf, offset, version);
                        offset += 6;
                        offset += parseListAppend(ctrlCmn, size - 6, buf, offset, version);
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                    } else if (ctrl instanceof Ctrl_GeneralShape) {
                        // [그림의 caption 을 넣기 위한 임시 코드]
                        // Ctrl_Common ctrlCmn = (Ctrl_Common)ctrl;
                        // offset -= 6;
                        // ctrlCmn.textVerAlign = HwpRecord_ListHeader.getVertAlign(6, buf, offset,
                        // version);
                        // offset += 6;
                        // offset += parseListAppend(ctrlCmn, size-6, buf, offset, version);
                        // if (subParaCount>0) {
                        // if (ctrlCmn.caption==null) ctrlCmn.caption = new ArrayList<CapParagraph>();
                        // CapParagraph newPara = new CapParagraph();
                        // ctrlCmn.caption.add(newPara);
                        // offset += parseRecurse(newPara, level, buf, offset, version);
                        // }
                        // [그림의 caption 을 넣기 위한 임시 코드]
                        
                        // 글상자속성
                        Ctrl_Common ctrlCmn = (Ctrl_Common) ctrl;
                        offset -= 6;
                        ctrlCmn.textVerAlign = HwpRecord_ListHeader.getVertAlign(6, buf, offset, version);
                        offset += 6;
                        offset += parseListAppend(ctrlCmn, size - 6, buf, offset, version);
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                        // 글상자속성
                    } else if (ctrl instanceof Ctrl_HeadFoot) {
                        int len = parseListAppend(ctrl, size - 6, buf, offset, version);
                        offset += len;
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                    } else if (ctrl instanceof Ctrl_Note) {
                        int len = parseListAppend(ctrl, size - 6, buf, offset, version);
                        offset += len;
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                    } else {
                        offset += (size - 6);
                    }
                    break;
                case HWPTAG_PAGE_DEF:
                case HWPTAG_FOOTNOTE_SHAPE:
                case HWPTAG_PAGE_BORDER_FILL:
                    if (ctrl instanceof Ctrl_SectionDef) { // dces 컨트롤에서만 처리
                        offset += parseCtrlRecurse((Ctrl_SectionDef) ctrl, level, buf, offset, version);
                    }
                    break;
                case HWPTAG_SHAPE_COMPONENT:
                case HWPTAG_SHAPE_COMPONENT_PICTURE:
                case HWPTAG_SHAPE_COMPONENT_LINE:
                case HWPTAG_SHAPE_COMPONENT_RECTANGLE:
                case HWPTAG_SHAPE_COMPONENT_ELLIPSE:
                case HWPTAG_SHAPE_COMPONENT_ARC:
                case HWPTAG_SHAPE_COMPONENT_POLYGON:
                case HWPTAG_SHAPE_COMPONENT_CURVE:
                case HWPTAG_SHAPE_COMPONENT_OLE:
                case HWPTAG_EQEDIT:
                case HWPTAG_SHAPE_COMPONENT_TEXTART:
                case HWPTAG_SHAPE_COMPONENT_UNKNOWN:
                    if (ctrl instanceof Ctrl_GeneralShape) { // " osg" 컨트롤에서만 처리
                        offset += parseCtrlRecurse((Ctrl_GeneralShape) ctrl, level, buf, offset, version);
                    } else {
                        return offset - off;
                    }
                    break;
                case HWPTAG_TABLE:
                    {
                        // 마지막 컨트롤을 기준으로 parseRecurse
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                    }
                    break;
                case HWPTAG_CTRL_HEADER:    // [2024.05.27]
                    return offset - off;
                case HWPTAG_PARA_RANGE_TAG:
                case HWPTAG_CTRL_DATA:
                case HWPTAG_FORM_OBJECT:
                case HWPTAG_MEMO_SHAPE:
                case HWPTAG_MEMO_LIST:
                case HWPTAG_CHART_DATA:
                case HWPTAG_VIDEO_DATA:
                default:
                    {
                        // 마지막 컨트롤을 기준으로 parseRecurse
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                    }
                }
            } else if (level == runLevel) {
                offset += headerOffset;
                log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())
                        + "[TAG]=" + tag.toString() + " (" + size + ") = runLevel");
                
                switch (tag) {
                case HWPTAG_PARA_HEADER:
                    if (ctrl instanceof Ctrl_Table) {
                        if (((Ctrl_Table) ctrl).cells == null) { // 캡션.
                            if (((Ctrl_Table) ctrl).paras == null)
                                ((Ctrl_Table) ctrl).paras = new ArrayList<HwpParagraph>();
                            HwpParagraph newPara = HwpParagraph.parse(tagNum, level, size, buf, offset, version);
                            // HwpParagraph newPara = new HwpParagraph();
                            ((Ctrl_Table) ctrl).paras.add(newPara);
                            offset += size; // HWP_PARA_HEADER의 하위 level부터 읽도록 offset 변경.
                            offset += parseRecurse(newPara, level, buf, offset, version);
                        } else {
                            // 마지막 cell 내 para list에 PARA 추가
                            TblCell cell = ((Ctrl_Table) ctrl).cells.stream().reduce((a, b) -> b).get();
                            if (cell.paras == null)
                                cell.paras = new ArrayList<CellParagraph>();
                            CellParagraph newPara = new CellParagraph();
                            cell.paras.add(newPara);
                            // Cell 내용에 대한 PARA_HEADER 읽어야지요.
                            offset += HwpParagraph.parse(newPara, size, buf, offset, version);
                            // HWP_PARA_HEADER의 하위 level부터 읽도록 offset 변경되었음.
                            offset += parseRecurse(newPara, level, buf, offset, version);
                        }
                    } else if (ctrl instanceof Ctrl_ShapeRect) {
                        if (((Ctrl_Common) ctrl).paras == null)
                            ((Ctrl_Common) ctrl).paras = new ArrayList<HwpParagraph>();
                        HwpParagraph newPara = HwpParagraph.parse(tagNum, level, size, buf, offset, version);
                        ((Ctrl_Common) ctrl).paras.add(newPara);
                        offset += HwpParagraph.parse(newPara, size, buf, offset, version);
                        // parseRecurse에서 PARA_TEXT 부터 읽도록 offset 변경되었음.
                        offset += parseRecurse(newPara, level, buf, offset, version);
                    } else if (ctrl instanceof Ctrl_GeneralShape) {
                        if (((Ctrl_Common) ctrl).captionWidth > 0 && ((Ctrl_Common) ctrl).caption == null) {
                            ((Ctrl_Common) ctrl).caption = new ArrayList<CapParagraph>();
                            CapParagraph newPara = new CapParagraph(); // HwpRecord_ParaHeader.parse(tagNum, level,
                                                                       // size, buf, offset, version);
                            ((Ctrl_Common) ctrl).caption.add(newPara);
                            offset += HwpParagraph.parse(newPara, size, buf, offset, version);
                            // parseRecurse에서 PARA_TEXT 부터 읽도록 offset 변경되었음.
                            offset += parseRecurse(newPara, level, buf, offset, version);
                        } else {
                            if (((Ctrl_Common) ctrl).paras == null)
                                ((Ctrl_Common) ctrl).paras = new ArrayList<HwpParagraph>();
                            HwpParagraph newPara = HwpParagraph.parse(tagNum, level, size, buf, offset, version);
                            ((Ctrl_Common) ctrl).paras.add(newPara);
                            offset += HwpParagraph.parse(newPara, size, buf, offset, version);
                            // parseRecurse에서 PARA_TEXT 부터 읽도록 offset 변경되었음.
                            offset += parseRecurse(newPara, level, buf, offset, version);
                        }
                    } else if (ctrl instanceof Ctrl_HeadFoot) {
                        if (((Ctrl_HeadFoot) ctrl).paras == null)
                            ((Ctrl_HeadFoot) ctrl).paras = new ArrayList<HwpParagraph>();
                        HwpParagraph newPara = HwpParagraph.parse(tagNum, level, size, buf, offset, version);
                        ((Ctrl_HeadFoot) ctrl).paras.add(newPara);
                        offset += size; // parseRecurse에서 PARA_TEXT 부터 읽도록 offset 변경.
                        offset += parseRecurse(newPara, level, buf, offset, version);
                    } else if (ctrl instanceof Ctrl_SectionDef) { // 바탕쪽 (Header/Footer와 유사)
                        if (((Ctrl_SectionDef) ctrl).paras == null)
                            ((Ctrl_SectionDef) ctrl).paras = new ArrayList<HwpParagraph>();
                        HwpParagraph newPara = HwpParagraph.parse(tagNum, level, size, buf, offset, version);
                        ((Ctrl_SectionDef) ctrl).paras.add(newPara);
                        offset += size;
                        offset += parseRecurse(newPara, level, buf, offset, version);
                    } else if (ctrl instanceof Ctrl_Note) {
                        if (((Ctrl_Note) ctrl).paras == null)
                            ((Ctrl_Note) ctrl).paras = new ArrayList<HwpParagraph>();
                        HwpParagraph newPara = HwpParagraph.parse(tagNum, level, size, buf, offset, version);
                        ((Ctrl_Note) ctrl).paras.add(newPara);
                        offset += size;
                        offset += parseRecurse(newPara, level, buf, offset, version);
                    } else {
                        HwpParagraph newPara = HwpParagraph.parse(tagNum, level, size, buf, offset, version);
                        offset += size;
                        parseRecurse(newPara, level, buf, offset, version);
                    }
                    break;
                case HWPTAG_CTRL_HEADER:
                    return offset - headerOffset - off;
                case HWPTAG_PAGE_DEF:
                    if (ctrl instanceof Ctrl_SectionDef) {
                        ((Ctrl_SectionDef) ctrl).page = Page.parse(level, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_FOOTNOTE_SHAPE:
                    if (ctrl instanceof Ctrl_SectionDef) {
                        Ctrl_SectionDef secCtrl = (Ctrl_SectionDef) ctrl;
                        if (secCtrl.noteShapes == null)
                            secCtrl.noteShapes = new ArrayList<NoteShape>();
                        secCtrl.noteShapes.add(NoteShape.parse(level, size, buf, offset, version));
                    }
                    offset += size;
                    break;
                case HWPTAG_PAGE_BORDER_FILL:
                    if (ctrl instanceof Ctrl_SectionDef) {
                        Ctrl_SectionDef secCtrl = (Ctrl_SectionDef) ctrl;
                        if (secCtrl.borderFills == null)
                            secCtrl.borderFills = new ArrayList<PageBorderFill>();
                        secCtrl.borderFills.add(PageBorderFill.parse(level, size, buf, offset, version));
                    }
                    offset += size;
                    break;
                case HWPTAG_TABLE:
                    {
                        // 이후에오는 {LIST_HEADER+...} 들을 cell로 받아야 한다.
                        int len = Ctrl_Table.parseCtrl((Ctrl_Table) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_LIST_HEADER:
                    if (ctrl instanceof Ctrl_Table) {
                        VertAlign verAlign = HwpRecord_ListHeader.getVertAlign(size, buf, offset, version);
                        offset += 6; // 문단수 2byte, 속성 4byte
                        if (((Ctrl_Table) ctrl).cells == null)
                            ((Ctrl_Table) ctrl).cells = new ArrayList<TblCell>();
                        // LIST_HEADER에 붙어서 오는 41byte 먼저 읽고,
                        TblCell cell = new TblCell(size - 6, buf, offset, version);
                        cell.verAlign = verAlign; // 세로 정렬
                        offset += cell.getSize();
                        ((Ctrl_Table) ctrl).cells.add(cell);
                        /*
                         * 그림의 caption을 구하기 위해 임시 코드. } else if (ctrl instanceof Ctrl_Common) {
                         * Ctrl_Common ctrlCmn = (Ctrl_Common)ctrl; offset += parseListAppend(ctrlCmn,
                         * size-6, buf, offset, version);
                         */
                    } else {
                        offset += size;
                    }
                    break;
                case HWPTAG_SHAPE_COMPONENT:
                    if (ctrl instanceof Ctrl_Container) {
                        offset -= headerOffset;
                        if (((Ctrl_Container) ctrl).list == null)
                            ((Ctrl_Container) ctrl).list = new ArrayList<Ctrl_GeneralShape>();
                        offset += parseContainerRecurse((Ctrl_Container) ctrl, level, buf, offset, version);
                    } else if (ctrl instanceof Ctrl_GeneralShape) {
                        Ctrl_GeneralShape newCtrl = Ctrl_GeneralShape.parse((Ctrl_GeneralShape) ctrl, size, buf, offset, version);
                        // replace Ctrl with newCtrl
                        HwpParagraph parentPara = ((Ctrl_GeneralShape) ctrl).getParent();
                        int ctrlIndex = parentPara.p.indexOf(ctrl);
                        if (ctrlIndex>=0) {
                            parentPara.p.set(ctrlIndex, newCtrl);
                        } else {
                            parentPara.p.add(newCtrl);
                        }
                        ctrl = newCtrl;
                        offset += size;
                    }
                    break;
                case HWPTAG_SHAPE_COMPONENT_PICTURE:
                    if (ctrl instanceof Ctrl_ShapePic) {
                        Ctrl_ShapePic.parseElement((Ctrl_ShapePic) ctrl, size, buf, offset, version, parentHwp);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_LINE:
                    if (ctrl instanceof Ctrl_ShapeLine) {
                        Ctrl_ShapeLine.parseElement((Ctrl_ShapeLine) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_RECTANGLE:
                    if (ctrl instanceof Ctrl_ShapeRect) {
                        Ctrl_ShapeRect.parseElement((Ctrl_ShapeRect) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_ELLIPSE:
                    if (ctrl instanceof Ctrl_ShapeEllipse) {
                        Ctrl_ShapeEllipse.parseElement((Ctrl_ShapeEllipse) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_ARC:
                    if (ctrl instanceof Ctrl_ShapeArc) {
                        Ctrl_ShapeArc.parseElement((Ctrl_ShapeArc) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_POLYGON:
                    if (ctrl instanceof Ctrl_ShapePolygon) {
                        Ctrl_ShapePolygon.parseElement((Ctrl_ShapePolygon) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_CURVE:
                    if (ctrl instanceof Ctrl_ShapeCurve) {
                        Ctrl_ShapeCurve.parseElement((Ctrl_ShapeCurve) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_OLE:
                    if (ctrl instanceof Ctrl_ShapeOle) {
                        Ctrl_ShapeOle.parseElement((Ctrl_ShapeOle) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_EQEDIT:
                    if (ctrl instanceof Ctrl_EqEdit) {
                        Ctrl_EqEdit.parseElement((Ctrl_EqEdit) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_VIDEO_DATA:
                    if (ctrl instanceof Ctrl_ShapeVideo) {
                        Ctrl_ShapeVideo.parseElement((Ctrl_ShapeVideo) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_TEXTART:
                    if (ctrl instanceof Ctrl_ShapeTextArt) {
                        Ctrl_ShapeTextArt.parseElement((Ctrl_ShapeTextArt) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_SHAPE_COMPONENT_UNKNOWN:
                    offset += size;
                    break;
                case HWPTAG_FORM_OBJECT:
                    if (ctrl instanceof Ctrl_Form) {
                        HwpRecord_FormObject.parseCtrl((Ctrl_Form) ctrl, size, buf, offset, version);
                    }
                    offset += size;
                    break;
                case HWPTAG_CTRL_DATA:
                    HwpRecord_CtrlData.parseCtrl(ctrl, size, buf, offset, version);
                    offset += size;
                    break;
                default:
                    offset += size;
                }
            }
        }
        return offset - off;
    }

    int parseContainerRecurse(Ctrl_Container container, int runLevel, byte[] buf, int off, int version)
            throws HwpParseException {
        int offset = off;

        while (offset < buf.length) {
            int header = buf[offset + 3] << 24 & 0xFF000000 | buf[offset + 2] << 16 & 0xFF0000 | buf[offset + 1] << 8 & 0xFF00 | buf[offset] & 0xFF;
            int tagNum = header & 0x3FF; // 10 bits (0 - 9 bit)
            int level = (header & 0xFFC00) >>> 10; // 10 bits (10-19 bit)
            int size = (header & 0xFFF00000) >>> 20; // 12 bits (20-31 bit)
            int headerOffset = 0;

            if (size == 0xFFF) {
                size = buf[off + 7] << 24 & 0xFF000000 | buf[off + 6] << 16 & 0xFF0000 | buf[off + 5] << 8 & 0xFF00 | buf[off + 4] & 0xFF;
                headerOffset = 8;
            } else {
                headerOffset = 4;
            }

            if (level < runLevel) {
                break;
            }

            HwpTag tag = HwpTag.from(tagNum);
            if (level > runLevel) {
                offset += headerOffset;
                log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())
                        + "[TAG]=" + tag.toString() + " (" + size + ") > runLevel");

                switch (tag) {
                case HWPTAG_SHAPE_COMPONENT_PICTURE: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapePic)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapePic(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapePic.parseElement((Ctrl_ShapePic) ctrl, size, buf, offset, version, parentHwp);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_LINE: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapeLine)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapeLine(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapeLine.parseElement((Ctrl_ShapeLine) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_RECTANGLE: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapeRect)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapeRect(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapeRect.parseElement((Ctrl_ShapeRect) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_ELLIPSE: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapeEllipse)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapeEllipse(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapeEllipse.parseElement((Ctrl_ShapeEllipse) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_ARC: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapeArc)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapeArc(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapeArc.parseElement((Ctrl_ShapeArc) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_POLYGON: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapePolygon)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapePolygon(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapePolygon.parseElement((Ctrl_ShapePolygon) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_CURVE: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapeCurve)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapeCurve(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapeCurve.parseElement((Ctrl_ShapeCurve) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_OLE: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapeOle)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapeOle(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapeOle.parseElement((Ctrl_ShapeOle) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_EQEDIT: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_EqEdit)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_EqEdit(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_EqEdit.parseElement((Ctrl_EqEdit) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT_TEXTART: {
                    Ctrl_GeneralShape ctrl = null;
                    if (container.list != null) {
                        Optional<Ctrl_GeneralShape> opCtrl = container.list.stream()
                                .filter(c -> (c instanceof Ctrl_ShapeTextArt)).reduce((a, b) -> b);
                        if (opCtrl.isPresent()) {
                            ctrl = opCtrl.get();
                        }
                    }
                    if (ctrl == null) {
                        ctrl = new Ctrl_ShapeTextArt(new Ctrl_GeneralShape());
                        container.list.add(ctrl);
                    }
                    offset += Ctrl_ShapeTextArt.parseElement((Ctrl_ShapeTextArt) ctrl, size, buf, offset, version);
                }
                    break;
                case HWPTAG_LIST_HEADER: {
                    Ctrl_Common ctrl = container.list.stream().reduce((a, b) -> b).get();
                    int subParaCount = HwpRecord_ListHeader.getCount(tagNum, level, size, buf, offset, version);
                    offset += 6; // 문단수 2byte, 속성 4byte

                    if (ctrl instanceof Ctrl_ShapeRect) {
                        Ctrl_Common ctrlCmn = (Ctrl_Common) ctrl;
                        ctrlCmn.ctrlId = "cer$";
                        offset -= 6;
                        ctrlCmn.textVerAlign = HwpRecord_ListHeader.getVertAlign(6, buf, offset, version);
                        offset += 6;
                        offset += parseListAppend(ctrlCmn, size - 6, buf, offset, version);
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                    } else if (ctrl instanceof Ctrl_ShapePolygon) {
                        Ctrl_Common ctrlCmn = (Ctrl_Common) ctrl;
                        ctrlCmn.ctrlId = "lop$";
                        offset -= 6;
                        ctrlCmn.textVerAlign = HwpRecord_ListHeader.getVertAlign(6, buf, offset, version);
                        offset += 6;
                        offset += parseListAppend(ctrlCmn, size - 6, buf, offset, version);
                        offset += parseCtrlRecurse(ctrl, level, buf, offset, version);
                    } else {
                        // container내 도형의 caption은 무시하자.
                        offset += (size - 6);
                    }
                }
                    break;
                case HWPTAG_SHAPE_COMPONENT:
                    Ctrl_GeneralShape baseCtrl = new Ctrl_GeneralShape();
                    Ctrl_GeneralShape newCtrl = Ctrl_GeneralShape.parse(baseCtrl, size, buf, offset, version);
                    offset += size;
                    if (newCtrl instanceof Ctrl_GeneralShape) {
                        container.list.add((Ctrl_GeneralShape) newCtrl);
                        if (newCtrl instanceof Ctrl_Container) {
                            if (((Ctrl_Container) newCtrl).list == null)
                                ((Ctrl_Container) newCtrl).list = new ArrayList<Ctrl_GeneralShape>();
                            offset += parseContainerRecurse((Ctrl_Container) newCtrl, level, buf, offset, version);
                        }
                    }
                    break;
                case HWPTAG_SHAPE_COMPONENT_UNKNOWN:
                case HWPTAG_TABLE:
                case HWPTAG_PARA_HEADER:
                case HWPTAG_PARA_TEXT:
                case HWPTAG_PAGE_DEF:
                case HWPTAG_FOOTNOTE_SHAPE:
                case HWPTAG_PAGE_BORDER_FILL:
                case HWPTAG_PARA_RANGE_TAG:
                case HWPTAG_CTRL_DATA:
                case HWPTAG_FORM_OBJECT:
                case HWPTAG_MEMO_SHAPE:
                case HWPTAG_MEMO_LIST:
                case HWPTAG_CHART_DATA:
                case HWPTAG_VIDEO_DATA:
                default:
                    offset += size;
                    break;
                }
            } else if (level == runLevel) {
                offset += headerOffset;
                log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())
                        + "[TAG]=" + tag.toString() + " (" + size + ") = runLevel");

                switch (tag) {
                case HWPTAG_PARA_HEADER:
                case HWPTAG_CTRL_HEADER:
                case HWPTAG_PAGE_DEF:
                case HWPTAG_FOOTNOTE_SHAPE:
                case HWPTAG_PAGE_BORDER_FILL:
                case HWPTAG_TABLE:
                case HWPTAG_LIST_HEADER:
                case HWPTAG_VIDEO_DATA:
                case HWPTAG_FORM_OBJECT:
                case HWPTAG_CTRL_DATA:
                    offset += size;
                    break;

                case HWPTAG_SHAPE_COMPONENT:
                    Ctrl_GeneralShape baseCtrl = new Ctrl_GeneralShape();
                    Ctrl_GeneralShape newCtrl = Ctrl_GeneralShape.parse(baseCtrl, size, buf, offset, version);
                    offset += size;
                    if (newCtrl instanceof Ctrl_GeneralShape) {
                        container.list.add((Ctrl_GeneralShape) newCtrl);
                        if (newCtrl instanceof Ctrl_Container) {
                            if (((Ctrl_Container) newCtrl).list == null)
                                ((Ctrl_Container) newCtrl).list = new ArrayList<Ctrl_GeneralShape>();
                            offset += parseContainerRecurse((Ctrl_Container) newCtrl, level, buf, offset, version);
                        }
                    }
                    break;
                case HWPTAG_SHAPE_COMPONENT_PICTURE:
                case HWPTAG_SHAPE_COMPONENT_LINE:
                case HWPTAG_SHAPE_COMPONENT_RECTANGLE:
                case HWPTAG_SHAPE_COMPONENT_ELLIPSE:
                case HWPTAG_SHAPE_COMPONENT_ARC:
                case HWPTAG_SHAPE_COMPONENT_POLYGON:
                case HWPTAG_SHAPE_COMPONENT_CURVE:
                case HWPTAG_SHAPE_COMPONENT_OLE:
                case HWPTAG_EQEDIT:
                case HWPTAG_SHAPE_COMPONENT_TEXTART:
                case HWPTAG_SHAPE_COMPONENT_UNKNOWN:
                default:
                    offset += size;
                }
            }
        }
        return offset - off;
    }

    private int parseListAppend(Ctrl_Common obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int len = 0;

        switch (obj.ctrlId) {
        case "cer$":
            len = Ctrl_ShapeRect.parseListHeaderAppend((Ctrl_ShapeRect) obj, size, buf, off, version);
            break;
        case " osg":
            len = Ctrl_GeneralShape.parseListHeaderAppend((Ctrl_GeneralShape) obj, size, buf, off, version);
            break;
        case " lbt":
            len = Ctrl_Table.parseListHeaderAppend((Ctrl_Table) obj, size, buf, off, version);
            break;
        case "deqe":
            len = Ctrl_EqEdit.parseListHeaderAppend((Ctrl_EqEdit) obj, size, buf, off, version);
            break;
        case "lop$":
            len = Ctrl_ShapePolygon.parseListHeaderAppend((Ctrl_ShapePolygon) obj, size, buf, off, version);
            break;
        case "lle$":
            len = Ctrl_ShapeEllipse.parseListHeaderAppend((Ctrl_ShapeEllipse) obj, size, buf, off, version);
            break;
        }

        return len;
    }

    private int parseListAppend(Ctrl obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int len = 0;

        switch (obj.ctrlId) {
        case "dces":
            off += (size - 6);
            len = size;
            break;
        case "daeh":
        case "toof":
            len = Ctrl_HeadFoot.parseListHeaderAppend((Ctrl_HeadFoot) obj, size, buf, off, version);
            // 문서내 14byte 내용은 있으나 28byte는 정의가 되지 않았다. 이중 10byte는 해석이 가능. offset값은 임의로 만든다.
            off += (size - 6);
            len = size;
            break;
        case "  nf":
            len = size;
            break;
        }

        return len;
    }

}
