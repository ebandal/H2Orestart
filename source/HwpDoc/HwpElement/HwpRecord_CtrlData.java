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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.paragraph.Ctrl;

public class HwpRecord_CtrlData extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_CtrlData.class.getName());
	
	public static List<ParameterSet>	paramSets;
	
	HwpRecord_CtrlData(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}

	public static int parseCtrl(Ctrl ctrl, int size, byte[] buf, int off, int version) throws HwpParseException {
		int offset = off;

		// 한컴문서의 내용만으로는 어떻게 해석해야 하는지 알수 없다.
		/*
		paramSets = new ArrayList<ParameterSet>();
		while(offset < size) {
			ParameterSet  paramSet = new ParameterSet();
			paramSet.paramSetId = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
			offset += 2;
			paramSet.nItems		= (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
			offset += 2;
			if (paramSet.nItems > 0) {
				paramSet.items 	= new ArrayList<ParameterItem>();
				for (int i=0; i< paramSet.nItems; i++) {
					
					ParameterItem item = new ParameterItem();
					item.itemId	= (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
					offset += 2;
					int itemType = buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
					offset += 2;
					item.itemType = ParamItemType.from(itemType);
				}
			}
		}
		*/
		
    	if (log.isLoggable(Level.FINE)) {
    		log.fine("                                                  ctrlID="+ctrl.ctrlId);
    	}
		// ctrlId를 거꾸로 읽어 비교한다.
		switch(ctrl.ctrlId) {
		case "klc%":	// FIELD_CLICKHERE
		case "dces":	// 구역정의
		case "mrof":	// 양식개체
		case "pngp":	// 쪽 번호 위치
		case "klh%":	// hyperlink
		case "frx%":	// FIELD_CROSSREF
		case "knu%":	// FIELD_UNKNOWN
		case "etd%":	// FIELD_DATE
		case "tdd%":	// FIELD_DOCDATE
		case "tap%":	// FIELD_PATH
		case "kmb%":	// FIELD_BOOKMARK
		case "gmm%":	// FIELD_MAILMERGE
		case "umf%":	// FIELD_FORMULA
		case "mkob":	// ???
        	if (log.isLoggable(Level.FINE)) {
        		log.fine(ctrl.ctrlId+"("+size+")를 해석할 수 없음. Just skipping...");
        	}
			break;
		default:
			log.severe("Neither known ctrlID=" + ctrl.ctrlId+" nor implemented.");
		}

		return size;
	}
	
	public static class ParameterSet {
		short 	paramSetId;
		short	nItems;
		List<ParameterItem> items;
	}
	
	public static class ParameterItem {
		short 			itemId;
		ParamItemType 	itemType;
		byte[]			itemData;
	}

	public static enum ParamItemType {
		PIT_NULL		(0x0),
		PIT_BSTR		(0x1),
		PIT_I1			(0x2),
		PIT_I2			(0x3),
		PIT_I4			(0x4),
		PIT_I			(0x5),
		PIT_UI1			(0x6),
		PIT_UI2			(0x7),
		PIT_UI4			(0x8),
		PIT_UI			(0x9),
		PIT_SET			(0x8000),
		PIT_ARRAY		(0x8001),
		PIT_BINDATA		(0x8002);
		
		private int num;
	    private ParamItemType(int num) { 
	    	this.num = num;
	    }
	    public static ParamItemType from(int num) {
	    	for (ParamItemType type: values()) {
	    		if (type.num == num)
	    			return type;
	    	}
	    	return null;
	    }
	}
	
	
}
