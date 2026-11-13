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
package HwpDoc.section;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpElement.HwpRecord;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.HwpElement.HwpRecordTypes.LineStyle2;
import HwpDoc.HwpElement.HwpRecordTypes.NumberShape2;

public class NoteShape {
    private static final Logger log = Logger.getLogger(NoteShape.class.getName());

	public NumberShape2		numberShape;
	public byte				placement;
	public NoteNumbering	numbering;
	public boolean			superscript;	// 각주 내용중 번호 코드의 모양을 윗첨자 형식으로 할지 여부
	public boolean			beneathText;	// 텍스트에 이어 바로 출력할지 여부
	public char				userChar;		// 사용자기호
	public char				prefixChar;		// 앞 장식 문자
	public char				suffixChar;		// 뒤 장식 문자
	public short			newNumber;		// 시작 번호
	public int				noteLineLength;	// 구분선길이
	public short			spacingAboveLine;	// 구분선 위 여백
	public short			spacingBelowLine;	// 구분선 아래 여백
	public short			sapcingBetweenNotes;// 주석 사이 여백
	public LineStyle2		noteLineType;	// 테두리 선 종류 (0:없음, 1:실선 ...). 명세 표 25 는 "없음"이 빠져 있어 한 칸 밀려 있음
	public byte				noteLineWidth;
	public int				noteLineColor;

	public NoteShape() { }

	/**
	 * HWPX hp:footNotePr / hp:endNotePr
	 * <pre>
	 * &lt;hp:footNotePr&gt;
	 *   &lt;hp:autoNumFormat type="DIGIT" userChar="" prefixChar="" suffixChar=")" supscript="0"/&gt;
	 *   &lt;hp:noteLine length="-1" type="SOLID" width="0.1 mm" color="#000000"/&gt;
	 *   &lt;hp:noteSpacing betweenNotes="850" belowLine="567" aboveLine="567"/&gt;
	 *   &lt;hp:numbering type="CONTINUOUS" newNum="1"/&gt;
	 *   &lt;hp:placement place="EACH_COLUMN" beneathText="0"/&gt;
	 * &lt;/hp:footNotePr&gt;
	 * </pre>
	 */
	public NoteShape(Node node, int version) {
	    NodeList children = node.getChildNodes();
	    for (int i=0; i<children.getLength(); i++) {
	        Node child = children.item(i);
	        if (child.getNodeType() != Node.ELEMENT_NODE) continue;
	        NamedNodeMap attrs = child.getAttributes();
	        switch(child.getNodeName()) {
	        case "hp:autoNumFormat":
	            numberShape = NumberShape2.fromXml(attr(attrs, "type"));
	            userChar    = firstChar(attr(attrs, "userChar"));
	            prefixChar  = firstChar(attr(attrs, "prefixChar"));
	            suffixChar  = firstChar(attr(attrs, "suffixChar"));
	            superscript = "1".equals(attr(attrs, "supscript"));
	            break;
	        case "hp:noteLine":
	            noteLineLength = parseInt(attr(attrs, "length"), 0);
	            String type = attr(attrs, "type");
	            try {
	                noteLineType = type==null ? LineStyle2.NONE : LineStyle2.fromXml(type);
	            } catch (IllegalArgumentException e) {
	                noteLineType = LineStyle2.SOLID;
	            }
	            noteLineWidth = lineWidthIndex(attr(attrs, "width"));
	            String color = attr(attrs, "color");
	            if (color!=null && color.startsWith("#")) {
	                noteLineColor = (int) Long.parseLong(color.substring(1), 16);  // RGBColor (0xRRGGBB)
	            }
	            break;
	        case "hp:noteSpacing":
	            sapcingBetweenNotes = (short) parseInt(attr(attrs, "betweenNotes"), 0);
	            spacingBelowLine    = (short) parseInt(attr(attrs, "belowLine"), 0);
	            spacingAboveLine    = (short) parseInt(attr(attrs, "aboveLine"), 0);
	            break;
	        case "hp:numbering":
	            String num = attr(attrs, "type");
	            numbering = "ON_SECTION".equals(num) ? NoteNumbering.ON_SECTION
	                      : "ON_PAGE".equals(num)    ? NoteNumbering.ON_PAGE
	                      : NoteNumbering.CONTINUOUS;
	            newNumber = (short) parseInt(attr(attrs, "newNum"), 1);
	            break;
	        case "hp:placement":
	            String place = attr(attrs, "place");
	            if ("MERGED_COLUMN".equals(place) || "END_OF_SECTION".equals(place)) {
	                placement = 1;
	            } else if ("RIGHT_MOST_COLUMN".equals(place)) {
	                placement = 2;
	            } else {    // EACH_COLUMN, END_OF_DOCUMENT
	                placement = 0;
	            }
	            beneathText = "1".equals(attr(attrs, "beneathText"));
	            break;
	        }
	    }
    }

	private static String attr(NamedNodeMap attrs, String name) {
	    Node n = attrs==null ? null : attrs.getNamedItem(name);
	    return n==null ? null : n.getNodeValue();
	}

	private static char firstChar(String s) {
	    return (s==null || s.isEmpty()) ? '\0' : s.charAt(0);
	}

	private static int parseInt(String s, int def) {
	    if (s==null) return def;
	    try {
	        return Integer.parseInt(s.trim());
	    } catch (NumberFormatException e) {
	        return def;
	    }
	}

	/** "0.1 mm" 형태의 선 굵기를 HWP 선 굵기 인덱스(0:0.1mm ~ 15:5.0mm)로 변환 */
	private static byte lineWidthIndex(String width) {
	    if (width==null) return 0;
	    String w = width.replace("mm", "").trim();
	    String[] table = { "0.1", "0.12", "0.15", "0.2", "0.25", "0.3", "0.4", "0.5", "0.6", "0.7", "1.0", "1.5", "2.0", "3.0", "4.0", "5.0" };
	    for (int i=0; i<table.length; i++) {
	        if (table[i].equals(w)) return (byte) i;
	    }
	    return 0;
	}

    public static NoteShape parse(int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;

        NoteShape noteShape = new NoteShape();
        
        // 속성 4 bytes
        noteShape.numberShape           = NumberShape2.from(buf[offset++]);
        byte     attr                   = buf[offset++];
        noteShape.placement             = (byte) (attr&0x03);
        noteShape.numbering             = NoteNumbering.from(attr>>2&0x03);
        noteShape.superscript           = (attr>>4&0x01)==0x01?true:false;
        noteShape.beneathText           = (attr>>5&0x01)==0x01?true:false;
        offset += 2;
        
        noteShape.userChar              = ByteBuffer.wrap(buf, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getChar();
        offset += 2;
        noteShape.prefixChar            = ByteBuffer.wrap(buf, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getChar();
        offset += 2;
        noteShape.suffixChar            = ByteBuffer.wrap(buf, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getChar();
        offset += 2;
        noteShape.newNumber             = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        noteShape.noteLineLength        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        noteShape.spacingAboveLine      = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        noteShape.spacingBelowLine      = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        noteShape.sapcingBetweenNotes   = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        noteShape.noteLineType          = LineStyle2.from(buf[offset++]);
        noteShape.noteLineWidth         = buf[offset++];
        noteShape.noteLineColor         = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
        offset += 4;          
        
        log.fine("                                                  "
                +"번호모양="+(noteShape.numberShape==null?"":noteShape.numberShape.toString())
                +",구분선(길이:여백)="+noteShape.noteLineLength+":"+noteShape.spacingAboveLine+":"+noteShape.spacingBelowLine+":"+noteShape.sapcingBetweenNotes
                +",구분선(종류:굵기:색상)="+noteShape.noteLineType.toString()+":"+noteShape.noteLineWidth+":"+noteShape.noteLineColor
            );

        if (offset-off-size!=0) {
            throw new HwpParseException(HwpRecord.parseError("NoteShape[level="+level+"]", buf, off, size, offset));
        }
        
        return noteShape;
    }

    public static enum NoteNumbering {
        CONTINUOUS          (0),    // 앞 구역에 이어서
        ON_SECTION          (1),    // 현재 구역부터 새로 시작
        ON_PAGE             (2);    //쪽마다 새로 시작, 각주 전용
        
        private int num;
        private NoteNumbering(int num) { 
            this.num = num;
        }
        public static NoteNumbering from(int num) {
            for (NoteNumbering shape: values()) {
                if (shape.num == num)
                    return shape;
            }
            return null;
        }
    }

    public static enum NotePlacement {
        EachColumn          (0), // 각 단마다 따로 배열
        MergedColumn        (1), // 통단으로 배열
        RightMostColumn     (2), // 가장 오른쪽 단에 배열

        EndOfDocument       (0), // 문서의 마지막
        EndOfSection        (1); // 구역의 마지막
        
        private int num;
        private NotePlacement(int num) { 
            this.num = num;
        }
        public static NotePlacement from(int num) {
            for (NotePlacement shape: values()) {
                if (shape.num == num)
                    return shape;
            }
            return null;
        }
    }

}
