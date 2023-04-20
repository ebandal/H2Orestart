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

class _HwpTag {
	final static int HWPTAG_BEGIN = 0x010;
}

public enum HwpTag {
	HWPTAG_DOCUMENT_PROPERTIES		(_HwpTag.HWPTAG_BEGIN),
	HWPTAG_ID_MAPPINGS				(_HwpTag.HWPTAG_BEGIN+1),
	HWPTAG_BIN_DATA					(_HwpTag.HWPTAG_BEGIN+2),
	HWPTAG_FACE_NAME				(_HwpTag.HWPTAG_BEGIN+3),
	HWPTAG_BORDER_FILL				(_HwpTag.HWPTAG_BEGIN+4),
	HWPTAG_CHAR_SHAPE				(_HwpTag.HWPTAG_BEGIN+5),
	HWPTAG_TAB_DEF					(_HwpTag.HWPTAG_BEGIN+6),
	HWPTAG_NUMBERING				(_HwpTag.HWPTAG_BEGIN+7),
	HWPTAG_BULLET					(_HwpTag.HWPTAG_BEGIN+8),
	HWPTAG_PARA_SHAPE				(_HwpTag.HWPTAG_BEGIN+9),
	HWPTAG_STYLE					(_HwpTag.HWPTAG_BEGIN+10),
	HWPTAG_DOC_DATA					(_HwpTag.HWPTAG_BEGIN+11),
	HWPTAG_DISTRIBUTE_DOC_DATA		(_HwpTag.HWPTAG_BEGIN+12),
	HWPTAG_COMPATIBLE_DOCUMENT		(_HwpTag.HWPTAG_BEGIN+14),
	HWPTAG_LAYOUT_COMPATIBILITY		(_HwpTag.HWPTAG_BEGIN+15),
	HWPTAG_TRACKCHANGE				(_HwpTag.HWPTAG_BEGIN+16),
	
	HWPTAG_PARA_HEADER				(_HwpTag.HWPTAG_BEGIN+50),
	HWPTAG_PARA_TEXT				(_HwpTag.HWPTAG_BEGIN+51),
	HWPTAG_PARA_CHAR_SHAPE			(_HwpTag.HWPTAG_BEGIN+52),
	HWPTAG_PARA_LINE_SEG			(_HwpTag.HWPTAG_BEGIN+53),
	HWPTAG_PARA_RANGE_TAG			(_HwpTag.HWPTAG_BEGIN+54),
	HWPTAG_CTRL_HEADER				(_HwpTag.HWPTAG_BEGIN+55),
	HWPTAG_LIST_HEADER				(_HwpTag.HWPTAG_BEGIN+56),
	HWPTAG_PAGE_DEF					(_HwpTag.HWPTAG_BEGIN+57),
	HWPTAG_FOOTNOTE_SHAPE			(_HwpTag.HWPTAG_BEGIN+58),
	HWPTAG_PAGE_BORDER_FILL			(_HwpTag.HWPTAG_BEGIN+59),
	HWPTAG_SHAPE_COMPONENT			(_HwpTag.HWPTAG_BEGIN+60),
	HWPTAG_TABLE					(_HwpTag.HWPTAG_BEGIN+61),
	HWPTAG_SHAPE_COMPONENT_LINE		(_HwpTag.HWPTAG_BEGIN+62),
	HWPTAG_SHAPE_COMPONENT_RECTANGLE(_HwpTag.HWPTAG_BEGIN+63),
	HWPTAG_SHAPE_COMPONENT_ELLIPSE	(_HwpTag.HWPTAG_BEGIN+64),
	HWPTAG_SHAPE_COMPONENT_ARC		(_HwpTag.HWPTAG_BEGIN+65),
	HWPTAG_SHAPE_COMPONENT_POLYGON	(_HwpTag.HWPTAG_BEGIN+66),
	HWPTAG_SHAPE_COMPONENT_CURVE	(_HwpTag.HWPTAG_BEGIN+67),
	HWPTAG_SHAPE_COMPONENT_OLE		(_HwpTag.HWPTAG_BEGIN+68),
	HWPTAG_SHAPE_COMPONENT_PICTURE	(_HwpTag.HWPTAG_BEGIN+69),
	HWPTAG_SHAPE_COMPONENT_CONTAINER(_HwpTag.HWPTAG_BEGIN+70),
	HWPTAG_CTRL_DATA				(_HwpTag.HWPTAG_BEGIN+71),
	HWPTAG_EQEDIT					(_HwpTag.HWPTAG_BEGIN+72),
	HWPTAG_SHAPE_COMPONENT_TEXTART	(_HwpTag.HWPTAG_BEGIN+74),
	HWPTAG_FORM_OBJECT				(_HwpTag.HWPTAG_BEGIN+75),
	HWPTAG_MEMO_SHAPE				(_HwpTag.HWPTAG_BEGIN+76),
	HWPTAG_MEMO_LIST				(_HwpTag.HWPTAG_BEGIN+77),
	HWPTAG_FORBIDDEN_CHAR			(_HwpTag.HWPTAG_BEGIN+78),
	HWPTAG_CHART_DATA				(_HwpTag.HWPTAG_BEGIN+79),
	HWPTAG_TRACK_CHANGE				(_HwpTag.HWPTAG_BEGIN+80),
	HWPTAG_TRACK_CHANGE_AUTHOR		(_HwpTag.HWPTAG_BEGIN+81),
	HWPTAG_VIDEO_DATA				(_HwpTag.HWPTAG_BEGIN+82),
	HWPTAG_SHAPE_COMPONENT_UNKNOWN	(_HwpTag.HWPTAG_BEGIN+99);

	private int tagNum;
	
    private HwpTag(int tagNum) { 
    	this.tagNum = tagNum;
    }

    private HwpTag(HwpTag tag) { 
    	this.tagNum = tag.tagNum;
    }
    
    public static HwpTag from(int tagNum) {
    	for (HwpTag tag: values()) {
    		if (tag.tagNum == tagNum)
    			return tag;
    	}
    	return null;
    }
}
