/* MIT License
 *  
 * Copyright (c) 2022 ebandal
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * 작성자 : 반희수 ebandal@gmail.com  
 * 작성일 : 2022.10
 */
package soffice;

import HwpDoc.paragraph.HwpParagraph;

public class HwpCallback {
    TableFrame tableFrame;
	
    public HwpCallback() { this.tableFrame = TableFrame.NONE; }
    public HwpCallback(TableFrame tableFrame) { this.tableFrame = tableFrame; }
	public void onNewNumber(int paraStyleID, int paraShapeID) {};
	public void onAutoNumber(int paraStyleID, int paraShapeID) {};
	public boolean onTab(String info) { return false; };
	public boolean onText(String content,  int charShapeId, int charPos, boolean append) { return false; }
	public boolean onParaBreak() { return false; }
	public TableFrame onTableWithFrame() { return tableFrame; }
	public void changeTableFrame(TableFrame tableFrame) { this.tableFrame = tableFrame; }
	
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
