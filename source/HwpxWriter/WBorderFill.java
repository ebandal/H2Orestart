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

/** 테두리/배경 (hh:borderFill). 변 순서: 왼쪽, 오른쪽, 위, 아래 */
public class WBorderFill {
    /** 한글의 선 굵기 목록 (mm) */
    public static final String[] WIDTHS = { "0.1 mm", "0.12 mm", "0.15 mm", "0.2 mm", "0.25 mm", "0.3 mm", "0.4 mm",
            "0.5 mm", "0.6 mm", "0.7 mm", "1.0 mm", "1.5 mm", "2.0 mm", "3.0 mm", "4.0 mm", "5.0 mm" };
    static final double[] WIDTH_MM = { 0.1, 0.12, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0 };

    /** 선 종류 (NONE, SOLID, ...) */
    public final String[] type = { "NONE", "NONE", "NONE", "NONE" };
    /** WIDTHS 의 위치 */
    public final int[] width = new int[4];
    public final int[] color = new int[4];
    /** 채우기 색 0xRRGGBB, 없으면 null */
    public Integer fillColor;

    /** mm 굵기에 가장 가까운 한글 굵기 위치 */
    public static int nearestWidth(double mm) {
        int best = 0;
        for (int i = 1; i < WIDTH_MM.length; i++) {
            if (Math.abs(WIDTH_MM[i] - mm) < Math.abs(WIDTH_MM[best] - mm)) best = i;
        }
        return best;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WBorderFill)) return false;
        WBorderFill b = (WBorderFill) o;
        return Arrays.equals(type, b.type) && Arrays.equals(width, b.width) && Arrays.equals(color, b.color)
                && (fillColor == null ? b.fillColor == null : fillColor.equals(b.fillColor));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(type) * 31 + Arrays.hashCode(width) * 7 + Arrays.hashCode(color) + (fillColor == null ? 0 : fillColor);
    }
}
