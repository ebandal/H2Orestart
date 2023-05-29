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

import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecord;
import HwpDoc.section.NoteShape;
import HwpDoc.section.Page;
import HwpDoc.section.PageBorderFill;

public class Ctrl_SectionDef extends Ctrl {
	private static final Logger log = Logger.getLogger(Ctrl_SectionDef.class.getName());
	private int 			size;
	
												// 속성
	public boolean			hideHeader;			// 머리말 감추기 여부
	public boolean			hideFooter;			// 꼬리말 감추기 여부
	public boolean			hideMasterPage;		// 바탕쪽 감추기 여부
	public boolean			hideBorder;			// 테두리 감추기 여부
	public boolean			hideFill;			// 배경 감추기 여부
	public boolean			hidePageNumPos;		// 쪽번호 위치 감추기 여부
	public boolean			showFirstBorder;	// 구역의 첫 쪽에서만 테두리 표시여부
	public boolean			showFirstFill;		// 구역의 첫 쪽에만 배경 표시 여부
	public byte				textDirection;		// 텍스트 방향 (0:가로, 1:세로)
	public boolean			hideEmptyLine;		// 빈줄 감추기 여부
	public byte	 			pageStartOn;		// 구역 나눔으로 새 페이지가 생길대의 페이지 번호 적용할지 여부  Both,Even,Odd
	// public boolean gridPaper;		// 원고지 정서법 적용 여부
	
	public short 			spaceColumns;		// 동일한 페이지에서 서로 다른 단 사이의 간격
	public short 			lineGrid;			// 세로로 줄맞춤을 할지 여부. 0:off, 1-n:간격을 hwpunit 단위로 지정
	public short 			charGrid;			// 가로로 줄맞춤을 할지 여부. 0:off, 1-n:간격을 hwpunit 단위로 지정
	public int				tabStop;			// 기본 탭 간격 (hwpunit or relative characters)
	public int				outlineNumberingID;	// 번호 문단 모양 ID
	public short			pageNum;			// 쪽 번호 (0:앞 구역에 이어, n= 임의의 번호로 시작)
	public short			figure;				// 그림 번호 (0:앞 구역에 이어, n= 임의의 번호로 시작)
	public short			table;				// 표 번호 (0:앞 구역에 이어, n= 임의의 번호로 시작)
	public short			equation;			// 수식 번호 (0:앞 구역에 이어, n= 임의의 번호로 시작)
	public short 			lang;				// 대표 language (0:없음, Application에 지정된 language) 5.0.15 이상
	
	public Page				page;				// HWPTAG_PAGE_DEF 처리
	public List<Ctrl_HeadFoot>	headerFooter;	// 
	public List<NoteShape>	noteShapes;			// HWPTAG_FOOTNOTE_SHAPE 처리
	public List<PageBorderFill> borderFills;	// HWPTAG_PAGE_BORDER_FILL 처리

	public List<HwpParagraph>	paras;			// 바탕쪽
	
	public Ctrl_SectionDef(String ctrlId) {
	    super(ctrlId);
	}
	
	public Ctrl_SectionDef(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId);
		
		int offset = off;
		// 속성 (표 130참조)
		int attr		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		
		hideHeader		= (attr&0x01)==0x01?true:false;
		hideFooter		= (attr&0x02)==0x02?true:false;
		hideMasterPage	= (attr&0x04)==0x04?true:false;
		hideBorder		= (attr&0x08)==0x08?true:false;
		hideFill		= (attr&0x10)==0x10?true:false;
		hidePageNumPos	= (attr&0x20)==0x20?true:false;
		showFirstBorder	= (attr&0x100)==0x100?true:false;
		showFirstFill	= (attr&0x200)==0x200?true:false;
		textDirection	= (byte) (attr>>16&0x07);
		hideEmptyLine	= (attr&0x20000)==0x20000?true:false;
		pageStartOn		= (byte) (attr>>20&0x03);
		
		// 동일한 페이지에서 서로 다른 단 사이의 간격.  기본값:1134. 기본설정 11.3pt=4mm=0.158inch
		spaceColumns	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		// 세로로 줄맞춤을 할지 여부 (0=off, 1-n=간격을 HWPUNIT 단위로 지정)
		lineGrid		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		// 가로로 줄맞춤을 할지 여부 (0=off, 1-n=간격을 HWPUNIT 단위로 지정)
		charGrid		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		// 기본 탭 간격 (hwpunit 또는 relative characters) 기본값:8000. 기본설정 40.0pt=14.11mm=0.5556inch
		tabStop 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		// 번호 문단 모양 ID
		outlineNumberingID	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		// 쪽 번호 (0=앞 구역에 이어, n=임의의 번호로 시작)
		pageNum			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		// 그림,표,수식 번호 (0=앞 구역에 이어, n = 임의의 번호로 시작)
		figure			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		table			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		equation		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		if (version>=5015) {
		// 대표 language(language값이 없으면(==0), Application에 지정된 language) 5.0.1.5 이상
			lang		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
			offset += 2;
		}

		log.fine("                                                  " + toString(attr));
		
		if (offset-off < size) {
			
		}
		this.size = offset-off;
		this.fullfilled = true;
	}
	
	public Ctrl_SectionDef(String ctrlId, Node node, int version) throws NotImplementedException {
	    super(ctrlId);
	    
        NamedNodeMap attributes = node.getAttributes();

        // id값은 처리하지 않는다. List<HwpRecord_CharShape>에 순차적으로 추가한다.
        // String id = attributes.getNamedItem("height").getNodeValue();

        switch(attributes.getNamedItem("textDirection").getNodeValue()) {
        case "HORIZONTAL":
            textDirection = 0;  break; // 0:가로, 1:세로
        case "VERTICAL":
            textDirection = 1;  break; // 0:가로, 1:세로
        default:
        	if (log.isLoggable(Level.FINE)) {
        		throw new NotImplementedException("Ctrl_SectionDef");
        	}
        	break;
        }

        String numStr = attributes.getNamedItem("spaceColumns").getNodeValue();
        spaceColumns = (short) Integer.parseInt(numStr);
        
        numStr = attributes.getNamedItem("tabStop").getNodeValue();
        tabStop = Integer.parseInt(numStr);

        numStr = attributes.getNamedItem("outlineShapeIDRef").getNodeValue();
        outlineNumberingID = Integer.parseInt(numStr);

        // attributes.getNamedItem("memoShapeIDRef").getNodeValue();
        // attributes.getNamedItem("textVerticalWidthHead").getNodeValue();
        // attributes.getNamedItem("masterPageCnt").getNodeValue();
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hp:startNum":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    switch(childAttrs.getNamedItem("pageStartsOn").getNodeValue()) {// Both,Even,Odd
                    case "BOTH":
                        pageStartOn = 0;   break;
                    case "EVEN":
                        pageStartOn = 1;   break;
                    case "ODD":
                        pageStartOn = 2;   break;
                    default:
                    	if (log.isLoggable(Level.FINE)) {
                    		throw new NotImplementedException("Ctrl_SectionDef");
                    	}
                    	break;
                    }
                    
                    numStr = childAttrs.getNamedItem("page").getNodeValue();
                    pageNum = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("pic").getNodeValue();
                    figure = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("tbl").getNodeValue();
                    table = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("equation").getNodeValue();
                    equation = (short) Integer.parseInt(numStr);
                }
                break;
            case "hp:grid":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    // 세로로 줄맞춤을 할지 여부. 0:off, 1-n:간격을 hwpunit 단위로 지정
                    switch(childAttrs.getNamedItem("lineGrid").getNodeValue()) {
                    case "0":
                        lineGrid = 0;   break;
                    default :
                    	if (log.isLoggable(Level.FINE)) {
                    		throw new NotImplementedException("lineGrid");
                    	}
                    	break;
                    }

                    switch(childAttrs.getNamedItem("charGrid").getNodeValue()) {
                    case "0":
                        charGrid = 0;   break;
                    default :
                    	if (log.isLoggable(Level.FINE)) {
                    		throw new NotImplementedException("charGrid");
                    	}
                    	break;
                    }
                }
                break;
            case "hp:visibility":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    switch(childAttrs.getNamedItem("hideFirstHeader").getNodeValue()) {
                    case "0":
                        hideHeader = false;   break;
                    case "1":
                        hideHeader = true;   break;
                    }

                    switch(childAttrs.getNamedItem("hideFirstFooter").getNodeValue()) {
                    case "0":
                        hideFooter = false;   break;
                    case "1":
                        hideFooter = true;   break;
                    }

                    switch(childAttrs.getNamedItem("hideFirstMasterPage").getNodeValue()) {
                    case "0":
                        hideMasterPage = false;   break;
                    case "1":
                        hideMasterPage = true;   break;
                    }
                    
                    switch(childAttrs.getNamedItem("border").getNodeValue()) {
                    case "HIDE_FIRST":
                        hideBorder = true;  showFirstBorder = false;    break;
                    case "SHOW_FIRST":
                        hideBorder = true;  showFirstBorder = false;    break;
                    case "SHOW_ALL":
                        hideBorder = false; showFirstBorder = false;    break;
                    default:
                    	if (log.isLoggable(Level.FINE)) {
                    		throw new NotImplementedException("Ctrl_SectionDef");
                    	}
                    	break;
                    }
                    
                    switch(childAttrs.getNamedItem("fill").getNodeValue()) {
                    case "HIDE_FIRST":
                        hideFill = true;  showFirstFill = false;  break;
                    case "SHOW_FIRST":
                        hideFill = true;  showFirstFill = true;  break;
                    case "SHOW_ALL":
                        hideFill = false;  showFirstFill = true;  break;
                    default:
                    	if (log.isLoggable(Level.FINE)) {
                    		throw new NotImplementedException("Ctrl_SectionDef");
                    	}
                    	break;
                    }

                    //childAttrs.getNamedItem("showLineNumber").getNodeValue()
                            
                    switch(childAttrs.getNamedItem("hideFirstPageNum").getNodeValue()) {
                    case "0":
                        hidePageNumPos = false;   break;
                    case "1":
                        hidePageNumPos = true;   break;
                    }
                    
                    switch(childAttrs.getNamedItem("hideFirstEmptyLine").getNodeValue()) {
                    case "0":
                        hideEmptyLine = false;   break;
                    case "1":
                        hideEmptyLine = true;   break;
                    }
                }
                break;
            case "hp:pagePr":
                {
                    page = new Page(child);
                }
                break;
            case "hp:footNotePr":
            case "hp:endNotePr":
                {
                    if (noteShapes==null) {
                        noteShapes = new ArrayList<>();
                    }
                    NoteShape noteShape = new NoteShape(child, version);
                    noteShapes.add(noteShape);
                }
                break;
            case "hp:pageBorderFill":
                {
                    if (borderFills==null) {
                        borderFills = new ArrayList<PageBorderFill>();
                    }
                    PageBorderFill borderFill = new PageBorderFill(child);
                    borderFills.add(borderFill);
                }
                break;
            case "hp:masterPage":
                {
                    // childAttrs.getNamedItem("idRef").getNodeValue()
                }
                break;
            case "hp:presentation":
            case "hp:parameterset":
            case "hp:lineNumberShape":
                break;
            default:
            	log.warning("Not implmented:" + child.getNodeName());
            	if (log.isLoggable(Level.FINE)) {
            		throw new NotImplementedException("Ctrl_SectionDef");
            	}
            	break;
            }
        }
        this.fullfilled = true;
	}

	public String toString(int attr) {
		StringBuffer strb = new StringBuffer();
		strb.append("CTRL("+ctrlId+")")
			.append("=속성:"+Integer.toBinaryString(attr))
			.append(",머리글:"+(hideHeader?"감추기":"보이기"))
			.append(",꼬리글:"+(hideFooter?"감추기":"보이기"))
			.append(",단사이간격:"+spaceColumns)
			.append(",세로줄맞춤:"+lineGrid)
			.append(",가로줄맞춤:"+charGrid)
			.append(",기본탭간격:"+tabStop)
			.append(",개요-번호문단모양ID:"+outlineNumberingID)
			.append(",쪽번호:"+page)
			.append(",그림번호:"+figure)
			.append(",표번호:"+table)
			.append(",수식번호:"+equation)
			.append(",언어:"+lang);
		return strb.toString();
	}
	
	public int getSize() {
		return size;
	}
}
