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

/** 표 (hp:tbl). 문단의 run 안에 들어간다 */
public class WTable {
    public int rowCnt, colCnt;
    /** 크기 (HWPUNIT) */
    public int width, height;
    /** 쪽 경계에서 나눔: CELL(나눔), TABLE(셀 단위로 나눔), NONE */
    public String pageBreak = "CELL";
    public boolean repeatHeader;
    /** 표 안 여백 (셀에 따로 없을 때의 셀 여백, HWPUNIT): 왼쪽, 오른쪽, 위, 아래 */
    public int[] inMargin = { 141, 141, 141, 141 };
    /** 문단 기준 위치 (HWPUNIT). 글자처럼 취급하는 표는 가로 위치만 (쪽 왼쪽 끝에서) */
    public int horzOffset, vertOffset;
    /** 글자처럼 취급 (문단 안의 큰 글자처럼 놓인다. 쪽에서 나누지 않는다) */
    public boolean treatAsChar;
    /** 글자처럼 취급하는 표의 가로 정렬 = 표 문단의 정렬 (LEFT, RIGHT, CENTER, DISTRIBUTE). null 이면 바꾸지 않는다 */
    public String paraAlign;
    public int borderFillId = 1;
    /** 행 우선 순서 */
    public final List<WCell> cells = new ArrayList<WCell>();

    /** 셀 (hp:tc) */
    public static class WCell {
        public int row, col, rowSpan = 1, colSpan = 1;
        public int width, height;
        public int borderFillId = 1;
        public boolean header;
        /** 셀 여백 지정 여부와 셀 여백 (HWPUNIT): 왼쪽, 오른쪽, 위, 아래 */
        public boolean hasMargin;
        public int[] margin = { 141, 141, 141, 141 };
        /** TOP, CENTER, BOTTOM */
        public String vertAlign = "CENTER";
        public final List<WParagraph> paragraphs = new ArrayList<WParagraph>();
    }
}
