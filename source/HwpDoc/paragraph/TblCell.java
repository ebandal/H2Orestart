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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.IContext;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.paragraph.Ctrl_Character.CtrlCharType;
import HwpDoc.paragraph.Ctrl_Common.VertAlign;

public class TblCell {
	private static final Logger log = Logger.getLogger(TblCell.class.getName());

	private int 	size;
	
	public short	colAddr;			// 셀 주소(Column, 맨 왼쪽 셀이 0부터 시작하여 1씩 증가)
	public short	rowAddr;			// 셀 주소(Row, 맨 위쪽 셀이 0부터 시작하여 1씩 증가)
	public short	colSpan;			// 열의 병합 갯수
	public short	rowSpan;			// 행의 병합 갯수
	public int		width;				// 셀의 폭
	public int		height;				// 셀의 높이
	public int[]	margin;				// 셀 4방향 여백
	public short	borderFill;			// 테두리/배경 아이디
	public List<CellParagraph>	paras;	// 내용  HWPTAG_PARA_TEXT + HWPTAG_PARA_CHAR_SHAPE + HWP_PARA_LINE_SEG
	public VertAlign   verAlign;		// LIST_HEADER 에 (문단갯수,텍스트방향,문단줄바꿈,세로정렬) 포함되어 있다.
	
	public String   mergedColName;      // 머지되었을때  칼럼명(알파벳)
	
	public TblCell(int size, byte[] buf, int off, int version) {
		int offset = off;
		
		// 앞 2byte는 무시한다.  해석불가.
		offset += 2;
		
		colAddr	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		rowAddr	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		colSpan	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		rowSpan	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		width	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		height	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		
		margin = new int[4];
		for (int i=0;i<4;i++) {
			margin[i]	= (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
			offset += 2;
		}
		borderFill	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		
		log.fine("                                                  " + "[CELL]" + toString());
		
		// 41byte중 28byte만 해석 가능.  내용을 모르므로 41byte 모두 읽은것 처럼 size 조작한다.
		this.size = size;
	}
	
	public TblCell(Node node, int version, IContext context) throws NotImplementedException {
        NamedNodeMap attrs = node.getAttributes();
        
        // attrs.getNamedItem("name").getNodeValue();
        // attrs.getNamedItem("header").getNodeValue();
        // attrs.getNamedItem("hasMargin").getNodeValue();
        // attrs.getNamedItem("protect").getNodeValue();
        // attrs.getNamedItem("editable").getNodeValue();
        // attrs.getNamedItem("dirty").getNodeValue();
        String numStr = attrs.getNamedItem("borderFillIDRef").getNodeValue();
        borderFill = (short)Integer.parseInt(numStr);
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hp:cellAddr":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("colAddr").getNodeValue();
                    colAddr = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("rowAddr").getNodeValue();
                    rowAddr = (short) Integer.parseInt(numStr);
                }
                break;
            case "hp:cellSpan":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("colSpan").getNodeValue();
                    colSpan = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("rowSpan").getNodeValue();
                    rowSpan = (short) Integer.parseInt(numStr);
                }
                break;
            case "hp:cellSz":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("width").getNodeValue();
                    width = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("height").getNodeValue();
                    height = Integer.parseInt(numStr);
                }
                break;
            case "hp:cellMargin":
                {
                    if (margin==null) {
                        margin = new int[4];
                    }
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("left").getNodeValue();
                    margin[0] = Integer.parseUnsignedInt(numStr);
                    numStr = childAttrs.getNamedItem("right").getNodeValue();
                    margin[1] = Integer.parseUnsignedInt(numStr);
                    numStr = childAttrs.getNamedItem("top").getNodeValue();
                    margin[2] = Integer.parseUnsignedInt(numStr);
                    numStr = childAttrs.getNamedItem("bottom").getNodeValue();
                    margin[3] = Integer.parseUnsignedInt(numStr);
                }
                break;
            case "hp:subList":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    // childAttrs.getNamedItem("id").getNodeValue();
                    // childAttrs.getNamedItem("textDirection").getNodeValue();
                    // childAttrs.getNamedItem("lineWrap").getNodeValue();
                    verAlign = VertAlign.valueOf(childAttrs.getNamedItem("vertAlign").getNodeValue());
                    // childAttrs.getNamedItem("linkListIDRef").getNodeValue();
                    // childAttrs.getNamedItem("linkListNextIDRef").getNodeValue();
                    
                    if (paras==null) {
                        paras = new ArrayList<CellParagraph>();
                    }
                    NodeList childNodeList = child.getChildNodes();
                    int nodeListNum = childNodeList.getLength();
                    for (int j=0; j<nodeListNum; j++) {
                        Node grandChild = childNodeList.item(j);
                        Ctrl lastCtrl = null;
                        switch(grandChild.getNodeName()) {
                        case "hp:p":
                            // HwpRecord.dumpNode(grandChild, 1);
                            CellParagraph cellP = new CellParagraph(grandChild, version, context);
                            paras.add(cellP);
                            lastCtrl = (cellP.p==null||cellP.p.size()==0 ? null : cellP.p.getLast());
                            break;
                        default:
                            if (log.isLoggable(Level.FINE)) {
                            	throw new NotImplementedException("TblCell");
                            }
                        }
                        
                        // ParaBreak를 subList 중간에 하나씩 강제로 넣는다. Paragraph 단위로 다음줄에 써지도록
                        if (lastCtrl!=null && lastCtrl instanceof ParaText && j<nodeListNum-1) {
                            CellParagraph breakP = new CellParagraph(grandChild, version, context);
                            if (breakP.p!=null) {
                                breakP.p.clear();
                            } else {
                                breakP.p = new LinkedList<Ctrl>();
                            }
                            breakP.p.add(new Ctrl_Character("   _", CtrlCharType.PARAGRAPH_BREAK));
                            paras.add(breakP);
                        }
                    }
                }
                break;
            default:
                if (log.isLoggable(Level.FINE)) {
                	throw new NotImplementedException("TblCell");
                }
            }
        }

    }

    public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("ColAddr="+colAddr)
			.append(",RowAddr="+rowAddr)
			.append(",ColSpan="+colSpan)
			.append(",RowSpan="+rowSpan)
			.append(",폭="+width)
			.append(",높이="+height)
			.append(",테두리/배경 아이디="+borderFill);
		if (margin!=null) {
			strb.append(",여백=");
			for (int i=0; i<margin.length;i++) {
				strb.append(margin[i]+":");
			}
		}
		strb.append(",FillID="+borderFill);
		return strb.toString();
	}
	
	public int getSize() {
		return size;
	}
}
