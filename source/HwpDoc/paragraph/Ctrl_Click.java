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
 * 본 제품은 한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다.
 * 개방형 워드프로세서 마크업 언어(OWPML) 문서 구조 KS X 6101:2018 문서를 참고하였습니다.
 * 작성자 : 반희수 ebandal@gmail.com  
 * 작성일 : 2022.10
 */
package HwpDoc.paragraph;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class Ctrl_Click extends Ctrl {
	private static final Logger log = Logger.getLogger(Ctrl_Click.class.getName());
	private int size;
	public  String clickHereStr;
	
	public Ctrl_Click(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId);
		int offset = off;
		
		offset += 4;
		offset += 1;
		short len 	= (short) ((buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2);
		offset += 2;
		
		if (len > 0) {
			clickHereStr = new String(buf, offset, len, StandardCharsets.UTF_16LE);
			offset += len;
		}
		
		offset += 4;
		offset += 4;
		
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}
	
	public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("CTRL("+ctrlId+")")
			.append("=문자열:"+(clickHereStr==null?"":clickHereStr));
		return strb.toString();
	}

	@Override
	public int getSize() {
		return size;
	}
}
