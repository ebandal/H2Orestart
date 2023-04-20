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
import java.util.List;
import java.util.logging.Logger;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;

public class HwpRecord_IdMapping extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_IdMapping.class.getName());
	HwpDocInfo 	parent;
	public List<Integer>	idMappingNum;			// 아이디 매핑 개수

	private int[] counts = new int[Index.MAX.index];

	HwpRecord_IdMapping(int tagNum, int level, int size) {
		super(tagNum, level, size);
		idMappingNum = new ArrayList<Integer>();
	}
	
	public HwpRecord_IdMapping(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		this(tagNum, level, size);
		this.parent = docInfo;
		
		int offset = off;
		for (int i=0; i<(size/4); i++) {
			int count = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			idMappingNum.add(count);
			offset += 4;
			
			counts[i] = count;
			log.finest("Total " + count + " IDs are mapping to " + Index.from(i));

			switch(Index.from(i)) {
			case BIN_DATA:
				if (parent.getParentHwp().getBinData()==null)
					parent.getParentHwp().setBinData(parent.getParentHwp().getOleFile().getChildEntries("BinData"));
				if (count > parent.getParentHwp().getBinData().size()) {
					log.fine("BIN_DATA count mismatch");
				}
				break;
			default:
				
			}
		}
		
		log.fine("                                                  "
				+"BinData="+counts[Index.BIN_DATA.index]
				+",한글Font="+counts[Index.FACENAME_HANGUL.index]
				+",BorderFill="+counts[Index.BORDER_FILL.index]
				+",CharShape="+counts[Index.CHAR_SHAPE.index]
				+",TabDef="+counts[Index.TAB_DEF.index]
				+",Numbering="+counts[Index.NUMBERING.index]
				+",Bullet="+counts[Index.BULLET.index]
				+",ParaShape="+counts[Index.PARA_SHAPE.index]
				+",Style="+counts[Index.STYLE.index]
		 	);

		if (offset-off-size!=0) {
			throw new HwpParseException();
		}
	}
	
	public static enum Index {
		BIN_DATA						(0),
		FACENAME_HANGUL					(1),
		FACENAME_ENGLISH				(2),
		FACENAME_CHINESE				(3),
		FACENAME_JAPANESE				(4),
		FACENAME_ETC					(5),
		FACENAME_SYMBOL					(6),
		FACENAME_USER					(7),
		BORDER_FILL						(8),
		CHAR_SHAPE						(9),
		TAB_DEF							(10),
		NUMBERING						(11),
		BULLET							(12),
		PARA_SHAPE						(13),
		STYLE							(14),
		MEMO_SHAPE						(15),
		TRACK_CHANGE					(16),
		TRACK_CHANGE_USER				(17),
		MAX								(18);

		private int index;
		
	    private Index(int index) { 
	    	this.index = index;
	    }

	    public static Index from(int index) {
	    	for (Index indexNum: values()) {
	    		if (indexNum.index == index)
	    			return indexNum;
	    	}
	    	return null;
	    }
	}
}
