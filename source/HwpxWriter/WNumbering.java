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

/**
 * 문단 번호 (hh:numbering) 또는 글머리표 (hh:bullet).
 * 번호는 수준 10개(1~7 + 확장 3)마다 형식 문자열(예: "^1.", "^1.^2)")과 번호 모양을 가진다.
 */
public class WNumbering {
    public static final int LEVELS = 10;

    /** 글머리표인가 */
    public boolean bullet;
    /** 글머리표 문자 */
    public String bulletChar = "●";
    /** 그림 글머리표의 바이너리 항목 id (없으면 null) */
    public String imageId;

    /** 수준별 형식 문자열. ^n 은 n 수준 번호 */
    public final String[] format = new String[LEVELS];
    /** 수준별 번호 모양 (DIGIT, HANGUL_SYLLABLE, ...) */
    public final String[] numFormat = new String[LEVELS];
    public final int[] start = new int[LEVELS];
    /** LEFT, CENTER, RIGHT */
    public final String[] align = new String[LEVELS];
    /** 본문과의 거리 (HWPUNIT, 절대값) */
    public final int[] textOffset = new int[LEVELS];

    public WNumbering() {
        for (int i = 0; i < LEVELS; i++) {
            format[i] = "";
            numFormat[i] = "DIGIT";
            start[i] = 1;
            align[i] = "LEFT";
        }
    }
}
