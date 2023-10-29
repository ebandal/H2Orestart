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
package compare;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_Numbering;
import HwpDoc.HwpElement.HwpRecord_Numbering.Numbering;
import HwpDoc.paragraph.Ctrl_SectionDef;

public class CompNumbering {
	private static final Logger log = Logger.getLogger(CompNumbering.class.getName());

	public static Map<Integer, String> numberingStyleNameMap = new HashMap<Integer, String>();
	public static Map<Integer, String> bulletStyleNameMap = new HashMap<Integer, String>();
	private static final String NUMBERING_STYLE_PREFIX = "HWP numbering ";
	private static final String BULLET_STYLE_PREFIX = "HWP bullet ";
	
	// UNOAPI를 사용하지 않는 경우, 현재 numbering 값을 가져오기 위해 사용
	private static Map<String, Integer[]> numberingNumbersMap = new HashMap<String, Integer[]>();
	private static Map<String, Integer> prevNumberingLevelMap = new HashMap<String, Integer>();
	private static Map<String, Integer[]> bulletNumbersMap = new HashMap<String, Integer[]>();
	
	public static String getNumberingHead(String hwpStyleName, HwpRecord_Numbering numbering, int headingLevel) {
        
	    Integer[] curNumbers = numberingNumbersMap.get(hwpStyleName);
	    Integer prevLevel = prevNumberingLevelMap.get(hwpStyleName);
	    if (prevLevel == null) {
	        prevLevel = 0;
	    }
	    curNumbers[headingLevel] += 1;
	    
        StringBuffer sb = new StringBuffer();
        
        // Hwp에서 수준(level)은 7개+확장3개,  LibreOffice에서 level은  10개이다.
        if (headingLevel<7) {
            Numbering numb = numbering.numbering[headingLevel];
            
            // ""
            // "^2."
            // "^2.^3."
            // "^2.^3.^4"
            
            if (numb.numFormat!=null && numb.numFormat.equals("")==false) {
                Pattern pattern = Pattern.compile("\\^\\d+");
                Matcher m = pattern.matcher(numb.numFormat);
    
                int prevEnd = 0;
                
                while(m.find()) {
                    int s = m.start();
                    int e = m.end();
    
                    int num = Integer.parseInt(numb.numFormat.substring(s+1, e));
                    if (num <= headingLevel+1) {
                        sb.append(numb.numFormat.subSequence(prevEnd, s));
                        sb.append(curNumbers[num-1]);
                    }
                    prevEnd = e;
                }
                sb.append(numb.numFormat.substring(prevEnd));
            }
        } else {
            //
        }
        
        // 현재 headingLevel값은 1증가
        // curNumbers[headingLevel] += 1;
        
        // 하위 headingLevel은 1로 초기화
        for (int i=headingLevel+1; i<curNumbers.length; i++) {
            curNumbers[i] = 0;
        }
        prevNumberingLevelMap.put(hwpStyleName, headingLevel);
        
        return sb.toString();
	}
	
	public static void makeCustomNumberingStyle(int id, HwpRecord_Numbering numbering) {
        String hwpStyleName = NUMBERING_STYLE_PREFIX + id;
        numberingStyleNameMap.put(id, hwpStyleName);

        Integer[] curNumbers = new Integer[10];
        for (int i=0; i< curNumbers.length; i++) {
            curNumbers[i] = 0;
        }
        numberingNumbersMap.put(hwpStyleName, curNumbers);
    }
	
	public static void makeCustomBulletStyle(int id, HwpRecord_Bullet bullet) {
        String hwpStyleName = BULLET_STYLE_PREFIX + id;
        bulletStyleNameMap.put(id, hwpStyleName);
        Integer[] curNumbers = new Integer[10];
        for (int i=0; i< curNumbers.length; i++) {
            curNumbers[i] = 0;
        }
        bulletNumbersMap.put(hwpStyleName, curNumbers);
    }
	   
	public static String getOutlineStyleName() {
		Ctrl_SectionDef secd = CompPage.getCurrentPage();
		return numberingStyleNameMap.get(secd.outlineNumberingID);
	}
}
