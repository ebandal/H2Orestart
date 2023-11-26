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
package HwpDoc.HwpElement;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_AutoNumber;
import HwpDoc.paragraph.Ctrl_Click;
import HwpDoc.paragraph.Ctrl_ColumnDef;
import HwpDoc.paragraph.Ctrl_EqEdit;
import HwpDoc.paragraph.Ctrl_Form;
import HwpDoc.paragraph.Ctrl_GeneralShape;
import HwpDoc.paragraph.Ctrl_HeadFoot;
import HwpDoc.paragraph.Ctrl_NewNumber;
import HwpDoc.paragraph.Ctrl_Note;
import HwpDoc.paragraph.Ctrl_PageNumPos;
import HwpDoc.paragraph.Ctrl_SectionDef;
import HwpDoc.paragraph.Ctrl_Table;

public class HwpRecord_CtrlHeader extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_CtrlHeader.class.getName());

//	public String		ctrlId;		// 컨트롤 ID
//	public Ctrl			ctrl;
	
	HwpRecord_CtrlHeader(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public static Ctrl parse(int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {

		int offset = off;
		// hwp포맷에는  역순으로 ctrlId를 구성한다. 여기서는 순방향으로 구성한다.
		String ctrlId = new String(buf, offset, 4, StandardCharsets.US_ASCII);
		offset += 4;
		Ctrl ctrl = null;
		
		log.fine("                                                  ctrlID="+ctrlId);
		// ctrlId를 거꾸로 읽어 비교한다.
		switch(ctrlId) {
		case "dces":	// 구역 정의
			ctrl = new Ctrl_SectionDef(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case "dloc":
			ctrl = new Ctrl_ColumnDef(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case "daeh":	// 머리말
			ctrl = new Ctrl_HeadFoot(ctrlId, size-(offset-off), buf, offset, version, true);
			offset += ctrl.getSize();
			break;
		case "toof":	// 꼬리말
			ctrl = new Ctrl_HeadFoot(ctrlId, size-(offset-off), buf, offset, version, false);
			offset += ctrl.getSize();
			break;
		case "  nf":	// 각주
		case "  ne":	// 미주
			ctrl = new Ctrl_Note(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case " lbt":	// table
			ctrl = new Ctrl_Table(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case "onta":	// 자동 번호
			ctrl = new Ctrl_AutoNumber(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case "onwn":	// 새 번호 지정
			ctrl = new Ctrl_NewNumber(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case " osg":	// GeneralShapeObject
			ctrl = new Ctrl_GeneralShape(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case "deqe":
			ctrl = new Ctrl_EqEdit(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			break;
		case "dhgp":	// 감추기
			{
				int tmpSize = size-(offset-off);
				ctrl = new Ctrl(ctrlId) {	public int getSize() { return tmpSize; }	};
				ctrl.ctrlId = ctrlId;
				offset += ctrl.getSize();
				ctrl.fullfilled = true;
			}
			log.fine("Known ctrlID="+ctrlId+", but is not implemented. Just skipping...");
			break;
		case "cot%":	// table of content
			{
				// 내용을 UTF_16LE로 읽었을때 아래 내용 같음.
				// ¥TableOfContents:set:140:ContentsMake:uint:17 ContentsStyles:wstring:0: ContentsLevel:int:5 ContentsAutoTabRight:int:0 ContentsLeader:int:3 ContentsHyperlink:bool:1  
				int tmpSize = size-(offset-off);
				// offset+=5;
				// String text = new String(buf, offset, tmpSize-13, StandardCharsets.UTF_16LE);
				// log.finest("TableOfContent:"+text);
				ctrl = new Ctrl(ctrlId) {	public int getSize() { return tmpSize; }	};
				ctrl.ctrlId = ctrlId;
				offset += ctrl.getSize();
				ctrl.fullfilled = true;
			}
			break;
		case "klc%":	// FIELD_CLICKHERE
			ctrl = new Ctrl_Click(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
			ctrl.fullfilled = true;
			break;
		case "mrof":	// 양식개체
			ctrl = new Ctrl_Form(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
            ctrl.fullfilled = true;
			break;
		case "pngp":	// 쪽 번호 위치
			ctrl = new Ctrl_PageNumPos(ctrlId, size-(offset-off), buf, offset, version);
			offset += ctrl.getSize();
            ctrl.fullfilled = true;
			break;
		case "klh%":	// hyperlink
		case "frx%":	// FIELD_CROSSREF
		case "knu%":	// FIELD_UNKNOWN
		case "etd%":	// FIELD_DATE
		case "tdd%":	// FIELD_DOCDATE
		case "tap%":	// FIELD_PATH
		case "kmb%":	// FIELD_BOOKMARK
		case "gmm%":	// FIELD_MAILMERGE
		case "umf%":	// FIELD_FORMULA
		case "mxdi":	// ???
		case "mkob":	// ???
		case "spct":	// ???
		case "tmct":	// ???
		case "tcgp":	// ???
		case "tudt":	// ???
		default:
			{
				int tmpSize = size-(offset-off);
				ctrl = new Ctrl(ctrlId) {	public int getSize() { return tmpSize; }	};
				ctrl.ctrlId = ctrlId;
				offset += ctrl.getSize();
	            ctrl.fullfilled = true;
			}
			log.fine("Known ctrlID="+ctrlId+", but is not implemented. Just skipping...");
			break;
		}

		return ctrl;
	}
}
