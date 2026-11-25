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
    /** 문단 번호 정의 (ID 는 위치+1) */
    public final List<WNumbering> numberings = new ArrayList<WNumbering>();
    /** 글머리표 정의 (ID 는 위치+1) */
    public final List<WNumbering> bullets = new ArrayList<WNumbering>();
    /** 탭 정의. 0 번은 탭 없음 */
    public final List<WTabPr> tabPrList = new ArrayList<WTabPr>();
    public final List<WStyle> styleList = new ArrayList<WStyle>();
    /** 테두리/배경. ID 는 위치+1. 1: 선/채우기 없음, 2: 글자 모양 기본 (한글 새 문서와 같다) */
    public final List<WBorderFill> borderFillList = new ArrayList<WBorderFill>();
    public final List<WSection> sections = new ArrayList<WSection>();

    /** 문서 정보 (content.hpf). 날짜는 UTC 의 yyyy-MM-dd'T'HH:mm:ss'Z', 비어 있으면 저장 시각 */
    public String title = "";
    public String creator = "";
    public String subject = "";
    public String description = "";
    public String keyword = "";
    public String lastSaveBy = "";
    public String createdDate = "";
    /** 만든 프로그램 버전 (version.xml appVersion). 예: "0, 8, 1, 0" */
    public String appVersion = "0, 0, 0, 0";

    public WDocument() {
        for (int i = 0; i < LANGS.length; i++) {
            fonts[i] = new ArrayList<String>();
        }
        borderFillList.add(new WBorderFill());
        WBorderFill charDefault = new WBorderFill();
        charDefault.fillColor = -1;     // 채우기 "none" (글자 모양 기본). 1 번과 구분하기 위한 표시
        borderFillList.add(charDefault);
        tabPrList.add(new WTabPr());
    }

    /** 같은 탭 정의가 있으면 그 ID, 없으면 추가한 ID */
    public int addTabPr(WTabPr t) {
        int idx = tabPrList.indexOf(t);
        if (idx >= 0) return idx;
        tabPrList.add(t);
        return tabPrList.size() - 1;
    }

    /** 같은 테두리/배경이 있으면 그 ID, 없으면 추가한 ID (1 부터) */
    public int addBorderFill(WBorderFill bf) {
        int idx = borderFillList.indexOf(bf);
        if (idx < 0) {
            borderFillList.add(bf);
            idx = borderFillList.size() - 1;
        }
        return idx + 1;
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

    /** 그림 등 바이너리 항목 (BinData/{id}.{ext}) */
    public static class BinItem {
        public final String id;
        public final String ext;
        public final byte[] data;

        public BinItem(String id, String ext, byte[] data) {
            this.id = id;
            this.ext = ext;
            this.data = data;
        }

        public String path() {
            return "BinData/" + id + "." + ext;
        }

        /** content.hpf 의 media-type. 한글은 jpg 를 image/jpg 로 쓴다 (가져오기는 image/ 뒤를 형식 이름으로 쓴다) */
        public String mediaType() {
            return "image/" + ext;
        }
    }

    public final List<BinItem> binItems = new ArrayList<BinItem>();

    /** 바이너리 항목을 추가하고 id (image1, image2, ...) 를 돌려준다 */
    public String addBinItem(String ext, byte[] data) {
        String id = "image" + (binItems.size() + 1);
        binItems.add(new BinItem(id, ext, data));
        return id;
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
