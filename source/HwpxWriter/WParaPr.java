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

/** 문단 모양 (hh:paraPr) */
public class WParaPr {
    /** JUSTIFY, LEFT, RIGHT, CENTER, DISTRIBUTE, DISTRIBUTE_SPACE */
    public String align = "JUSTIFY";
    /** 여백 (HWPUNIT). intent 는 첫 줄 들여쓰기(음수면 내어쓰기) */
    public int indent, left, right, prev, next;
    /** 줄 간격 종류: PERCENT, FIXED, BETWEEN_LINES, AT_LEAST */
    public String lineSpacingType = "PERCENT";
    public int lineSpacing = 160;
    /** 탭 정의 ID */
    public int tabPrId = 0;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WParaPr)) return false;
        WParaPr p = (WParaPr) o;
        return align.equals(p.align) && indent == p.indent && left == p.left && right == p.right && prev == p.prev
                && next == p.next && lineSpacingType.equals(p.lineSpacingType) && lineSpacing == p.lineSpacing
                && tabPrId == p.tabPrId;
    }

    @Override
    public int hashCode() {
        return align.hashCode() + indent * 7 + left * 11 + right * 13 + prev * 17 + next * 19 + lineSpacing + tabPrId * 23;
    }
}
