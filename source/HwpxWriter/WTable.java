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
    /** 문단 기준 위치 (HWPUNIT) */
    public int horzOffset, vertOffset;
    public int borderFillId = 1;
    /** 행 우선 순서 */
    public final List<WCell> cells = new ArrayList<WCell>();

    /** 셀 (hp:tc) */
    public static class WCell {
        public int row, col, rowSpan = 1, colSpan = 1;
        public int width, height;
        public int borderFillId = 1;
        public boolean header;
        /** TOP, CENTER, BOTTOM */
        public String vertAlign = "CENTER";
        public final List<WParagraph> paragraphs = new ArrayList<WParagraph>();
    }
}
