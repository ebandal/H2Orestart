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

/**
 * HWPX 로 쓸 문서 모델.
 * 개방형 워드프로세서 마크업 언어(OWPML) 문서 구조 KS X 6101 과 한글 2022(xmlVersion 1.5) 출력 형식을 기준으로 한다.
 * ID 는 목록의 위치이다 (글자 모양 0 = charPrList[0]). 테두리/배경만 1 부터.
 */
public class WDocument {
    /** 언어 순서 (fontRef, fontface 의 순서와 같다) */
    public static final String[] LANGS = { "HANGUL", "LATIN", "HANJA", "JAPANESE", "OTHER", "SYMBOL", "USER" };

    /** 언어별 글꼴 이름 목록. fonts[lang].get(id) */
    @SuppressWarnings("unchecked")
    public final List<String>[] fonts = new List[LANGS.length];
    public final List<WCharPr> charPrList = new ArrayList<WCharPr>();
    public final List<WParaPr> paraPrList = new ArrayList<WParaPr>();
    public final List<WStyle> styleList = new ArrayList<WStyle>();
    public final List<WSection> sections = new ArrayList<WSection>();

    public String title = "";
    public String creator = "";

    public WDocument() {
        for (int i = 0; i < LANGS.length; i++) {
            fonts[i] = new ArrayList<String>();
        }
    }

    /** 모든 언어에 같은 글꼴을 등록하고 각 언어의 ID 를 돌려준다 */
    public int[] addFontAllLangs(String face) {
        int[] ids = new int[LANGS.length];
        for (int i = 0; i < LANGS.length; i++) {
            int idx = fonts[i].indexOf(face);
            if (idx < 0) {
                fonts[i].add(face);
                idx = fonts[i].size() - 1;
            }
            ids[i] = idx;
        }
        return ids;
    }

    /** 같은 글자 모양이 있으면 그 ID, 없으면 추가한 ID */
    public int addCharPr(WCharPr cp) {
        int idx = charPrList.indexOf(cp);
        if (idx >= 0) return idx;
        charPrList.add(cp);
        return charPrList.size() - 1;
    }

    /** 같은 문단 모양이 있으면 그 ID, 없으면 추가한 ID */
    public int addParaPr(WParaPr pp) {
        int idx = paraPrList.indexOf(pp);
        if (idx >= 0) return idx;
        paraPrList.add(pp);
        return paraPrList.size() - 1;
    }

    /** 한글의 새 문서와 같은 기본 글꼴/글자 모양/문단 모양/스타일(바탕글) 을 넣은 문서 */
    public static WDocument createDefault() {
        WDocument doc = new WDocument();
        WCharPr cp = new WCharPr();
        cp.fontRef = doc.addFontAllLangs("함초롬바탕");
        doc.addCharPr(cp);
        doc.addParaPr(new WParaPr());
        WStyle normal = new WStyle();
        normal.name = "바탕글";
        normal.engName = "Normal";
        doc.styleList.add(normal);
        return doc;
    }
}
