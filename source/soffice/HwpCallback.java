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
package soffice;

import HwpDoc.paragraph.Ctrl_AutoNumber;

public class HwpCallback {
    TableFrame tableFrame;
    public boolean firstParaAfterTable;
	
    public HwpCallback() { this.tableFrame = TableFrame.NONE; }
    public HwpCallback(TableFrame tableFrame) { this.tableFrame = tableFrame; }
	public void onNewNumber(int paraStyleID, int paraShapeID) {};
	public void onAutoNumber(Ctrl_AutoNumber autoNumber, int paraStyleID, int paraShapeID) {};
	public boolean onTab(String info) { return false; };
	public boolean onText(String content,  int charShapeId, int charPos, boolean append) { return false; }
	public boolean onParaBreak() { return false; }
	public TableFrame onTableWithFrame() { return tableFrame; }
	public void changeTableFrame(TableFrame tableFrame) { this.tableFrame = tableFrame; }
	public void onFirstAfterTable(boolean isFirst) {
    	if (isFirst) {
    		firstParaAfterTable = true;
    	} else {
    		firstParaAfterTable = false;
    	}
    }
	
	public static enum TableFrame {
	    NONE      (0),
	    NEVER     (1),    // 프레임 생성하지 말것.
	    MADE      (2),    // 프레임 이미 생성되었음.
	    MAKE      (3),    // 프레임 내부에서 만들것
	    MAKE_PART (4);    // 프레임 내부에서 만들며, 내용을 반투명하게 만들것.
	    
	    private int num;
	    private TableFrame(int num) { 
	        this.num = num;
	    }
	    public static TableFrame from(int num) {
	        for (TableFrame type: values()) {
	            if (type.num == num)
	                return type;
	        }
	        return null;
	    }
	}
}
