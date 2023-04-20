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

import org.w3c.dom.Node;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecordTypes.LineType1;
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
	public LineType1		noteLineType;
	public byte				noteLineWidth;
	public int				noteLineColor;

	public NoteShape() { }

	public NoteShape(Node child, int version) {
    }

    public static NoteShape parse(int level, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
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
        noteShape.noteLineType          = LineType1.from(buf[offset++]);
        noteShape.noteLineWidth         = buf[offset++];
        noteShape.noteLineColor         = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
        offset += 4;          
        
        log.fine("                                                  "
                +"번호모양="+(noteShape.numberShape==null?"":noteShape.numberShape.toString())
                +",구분선(길이:여백)="+noteShape.noteLineLength+":"+noteShape.spacingAboveLine+":"+noteShape.spacingBelowLine+":"+noteShape.sapcingBetweenNotes
                +",구분선(종류:굵기:색상)="+noteShape.noteLineType.toString()+":"+noteShape.noteLineWidth+":"+noteShape.noteLineColor
            );

        if (offset-off-size!=0) {
            throw new HwpParseException();
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
