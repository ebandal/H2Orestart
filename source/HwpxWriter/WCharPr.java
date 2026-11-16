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

import java.util.Arrays;

/** 글자 모양 (hh:charPr) */
public class WCharPr {
    /** 글자 크기 (HWPUNIT, 1000 = 10pt) */
    public int height = 1000;
    /** 0xRRGGBB */
    public int textColor = 0x000000;
    /** 언어별 글꼴 ID (WDocument.fonts[lang] 의 위치) */
    public int[] fontRef = new int[WDocument.LANGS.length];
    public boolean bold;
    public boolean italic;
    /** 밑줄: NONE, BOTTOM, CENTER, TOP */
    public String underline = "NONE";
    public boolean strikeout;
    /** 장평 (%) */
    public int ratio = 100;
    /** 자간 (%) */
    public int spacing = 0;
    /** 서양 글꼴 언어(영문, 기타, 기호, 사용자)의 상대 크기 (%). 한글/한자/일어는 100 */
    public int relSizeLatin = 100;
    public boolean superScript;
    public boolean subScript;
    /** 음영 색 (0xRRGGBB). null 이면 없음 */
    public Integer shadeColor;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WCharPr)) return false;
        WCharPr c = (WCharPr) o;
        return height == c.height && textColor == c.textColor && Arrays.equals(fontRef, c.fontRef)
                && bold == c.bold && italic == c.italic && underline.equals(c.underline) && strikeout == c.strikeout
                && ratio == c.ratio && spacing == c.spacing && relSizeLatin == c.relSizeLatin
                && superScript == c.superScript && subScript == c.subScript
                && (shadeColor == null ? c.shadeColor == null : shadeColor.equals(c.shadeColor));
    }

    @Override
    public int hashCode() {
        return height * 31 + textColor + Arrays.hashCode(fontRef) + (bold ? 1 : 0) + (italic ? 2 : 0)
                + ratio * 7 + spacing * 11 + relSizeLatin * 13 + (superScript ? 4 : 0) + (subScript ? 8 : 0);
    }
}
