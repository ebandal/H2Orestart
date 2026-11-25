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
    /** 문단 테두리/배경 ID (1 부터). 2 = 테두리/배경 없음 (한글 기본) */
    public int borderFillId = 2;
    /** 문단 머리: NONE, NUMBER, BULLET */
    public String headingType = "NONE";
    /** 번호/글머리표 정의 ID (1 부터) */
    public int headingId = 0;
    /** 수준 (0 부터) */
    public int headingLevel = 0;
    /** 다음 문단과 함께, 문단 보호 (쪽 경계에서 나누지 않음) */
    public boolean keepWithNext, keepLines;
    /** 줄 나눔 기준 (HWP 값). 영문 0:단어 1:하이픈 2:글자, 한글 0:어절 1:글자 */
    public int breakLatin = 0, breakNonLatin = 1;
    /** 공백 최소값 (%) */
    public int condense = 0;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WParaPr)) return false;
        WParaPr p = (WParaPr) o;
        return align.equals(p.align) && indent == p.indent && left == p.left && right == p.right && prev == p.prev
                && next == p.next && lineSpacingType.equals(p.lineSpacingType) && lineSpacing == p.lineSpacing
                && tabPrId == p.tabPrId && borderFillId == p.borderFillId
                && headingType.equals(p.headingType) && headingId == p.headingId && headingLevel == p.headingLevel
                && keepWithNext == p.keepWithNext && keepLines == p.keepLines
                && breakLatin == p.breakLatin && breakNonLatin == p.breakNonLatin && condense == p.condense;
    }

    @Override
    public int hashCode() {
        return align.hashCode() + indent * 7 + left * 11 + right * 13 + prev * 17 + next * 19 + lineSpacing + tabPrId * 23 + borderFillId * 29 + headingId * 31 + headingLevel * 37
                + (keepWithNext ? 41 : 0) + (keepLines ? 43 : 0) + breakLatin * 47 + breakNonLatin * 53 + condense * 59;
    }
}
