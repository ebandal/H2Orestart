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

/** 문단 (hp:p) */
public class WParagraph {
    public int paraPrId = 0;
    public int styleId = 0;
    public boolean pageBreak;
    /** 문단에 든 표 (문단의 첫 run 에 쓴다). 없으면 null */
    public WTable table;
    public final List<WRun> runs = new ArrayList<WRun>();

    /** 글자 모양 하나로 된 문단 */
    public static WParagraph of(String text, int charPrId) {
        WParagraph p = new WParagraph();
        p.runs.add(new WRun(charPrId, text));
        return p;
    }

    /** 문단 끝 글자 모양: 마지막 run 의 글자 모양 (없으면 0) */
    public int lastCharPrId() {
        return runs.isEmpty() ? 0 : runs.get(runs.size() - 1).charPrId;
    }

    /** 같은 run 의 텍스트를 이어 붙인 문자열 */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (WRun r : runs) sb.append(r.text);
        return sb.toString();
    }

    /** 글자 모양이 같은 run (hp:run). 텍스트의 탭(\t)과 줄바꿈(\n)은 hp:tab, hp:lineBreak 로 쓴다 */
    public static class WRun {
        public int charPrId;
        public String text;

        public WRun(int charPrId, String text) {
            this.charPrId = charPrId;
            this.text = text;
        }
    }
}
