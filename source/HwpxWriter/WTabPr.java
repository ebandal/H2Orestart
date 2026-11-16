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

/** 탭 정의 (hh:tabPr) */
public class WTabPr {
    /** 내어쓰기용 자동 탭 */
    public boolean autoTabLeft;
    /** 문단 오른쪽 끝 자동 탭 */
    public boolean autoTabRight;
    /** 탭 위치 (HWPUNIT) */
    public final List<Integer> pos = new ArrayList<Integer>();
    /** 탭 종류: LEFT, RIGHT, CENTER, DECIMAL */
    public final List<String> type = new ArrayList<String>();

    public void add(int p, String t) {
        pos.add(p);
        type.add(t);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WTabPr)) return false;
        WTabPr t = (WTabPr) o;
        return autoTabLeft == t.autoTabLeft && autoTabRight == t.autoTabRight && pos.equals(t.pos) && type.equals(t.type);
    }

    @Override
    public int hashCode() {
        return (autoTabLeft ? 1 : 0) + (autoTabRight ? 2 : 0) + pos.hashCode() * 31 + type.hashCode();
    }
}
