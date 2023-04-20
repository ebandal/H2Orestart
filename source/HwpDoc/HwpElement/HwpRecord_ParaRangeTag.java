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

import java.util.ArrayList;
import java.util.logging.Logger;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.RangeTag;

public class HwpRecord_ParaRangeTag extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_ParaRangeTag.class.getName());

	HwpRecord_ParaRangeTag(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public static int parse(HwpParagraph para, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		int offset = off;
		
		if (para.rangeTags==null) {
			para.rangeTags = new ArrayList<RangeTag>();
		}
		
		while(size-(offset-off) >= 12) {
			RangeTag rangeTag = new RangeTag();
			rangeTag.startPos 	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
			rangeTag.endPos 	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
			rangeTag.tag 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
			para.rangeTags.add(rangeTag);
		}
		
		if (offset-off-size != 0) {
			log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
			dump(buf, off, size);
			throw new HwpParseException();
		}
		
		return offset-off;
	}

}
