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
import java.util.logging.Level;
import java.util.logging.Logger;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.paragraph.Ctrl_Form;

public class HwpRecord_FormObject extends HwpRecord {
    private static final Logger log = Logger.getLogger(HwpRecord_FormObject.class.getName());
    public static String formStr;
    
    HwpRecord_FormObject(int tagNum, int level, int size) {
        super(tagNum, level, size);
    }
    
    public static int parseCtrl(Ctrl_Form form,  int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        offset += 4;	// tbp+
        offset += 4;	// tbp+
        offset += 4;	// 문자열 길이?
        
        short strLen	 = (short) ((buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2);
        offset += 2;
        if (strLen > 0) {
            formStr = new String(buf, offset, strLen, StandardCharsets.UTF_16LE);
            offset += strLen;
        }
        
        if (log.isLoggable(Level.FINE)) {
            log.fine("                                                  "
                    +"ctrlID="+form.ctrlId
                    +",문자열="+(formStr==null?"":formStr));
        }
        
        return offset-off;
    }
}
