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
