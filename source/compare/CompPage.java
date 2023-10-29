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

import HwpDoc.paragraph.Ctrl_SectionDef;

public class CompPage {
    private static final Logger log = Logger.getLogger(CompPage.class.getName());

    private static Map<Integer, String> pageStyleNameMap = new HashMap<Integer, String>();
    private static Map<Integer, Ctrl_SectionDef> pageMap = new HashMap<Integer, Ctrl_SectionDef>();
    private static int customIndex = 0;
    private static int secdIndex = 0;
    private static final String PAGE_STYLE_PREFIX = "HWP ";

    public static int getSectionIndex() {
        return secdIndex;
    }

    public static void setSectionIndex(int index) {
        secdIndex = index;
    }

    public static Ctrl_SectionDef getCurrentPage() {
        return pageMap.get(secdIndex);
    }

    public static String makeCustomPageStyle(Ctrl_SectionDef secd) {
        String styleName = PAGE_STYLE_PREFIX + customIndex;
        pageStyleNameMap.put(customIndex, styleName);
        pageMap.put(customIndex, secd);
        customIndex++;
        return styleName;
    }

}
