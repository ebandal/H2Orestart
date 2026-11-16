/* Copyright (C) 2026 ebandal
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
package HwpxWriter;

import java.util.ArrayList;
import java.util.List;

/** 구역 (sectionN.xml). 용지 설정과 문단 목록 */
public class WSection {
    /** 용지 크기/여백 (HWPUNIT). 기본값은 한글 새 문서의 A4 */
    public int pageWidth = 59528, pageHeight = 84188;
    public boolean landscape = false;
    public int marginLeft = 8504, marginRight = 8504, marginTop = 5668, marginBottom = 4252;
    public int marginHeader = 4252, marginFooter = 4252, marginGutter = 0;

    public final List<WParagraph> paragraphs = new ArrayList<WParagraph>();

    /** 머리말/꼬리말 (hp:header, hp:footer). 첫 문단의 구역 정의 뒤에 쓴다 */
    public static class HeaderFooter {
        public boolean header;
        /** BOTH, EVEN, ODD */
        public String applyPageType = "BOTH";
        public final List<WParagraph> paragraphs = new ArrayList<WParagraph>();
    }

    public final List<HeaderFooter> headerFooters = new ArrayList<HeaderFooter>();

    /** 쪽 번호 위치 (hp:pageNum). null 이면 쓰지 않는다 */
    public String pageNumPos;
    /** 쪽 번호 모양 (DIGIT, ROMAN_CAPITAL, ...) */
    public String pageNumFormat = "DIGIT";
    /** 쪽 번호 양옆 문자 (예: "-" 이면 "- 1 -"). 없으면 "" */
    public String pageNumSideChar = "";
}
