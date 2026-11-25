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

import java.util.List;

/** Contents/header.xml. 요소 순서와 기본값은 한글 2022 출력을 따른다 */
final class HeaderXml {
    private HeaderXml() {}

    static String build(WDocument doc) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append(Xml.DECL);
        sb.append("<hh:head").append(Xml.NS).append(" version=\"1.5\" secCnt=\"").append(doc.sections.size()).append("\">");
        sb.append("<hh:beginNum page=\"1\" footnote=\"1\" endnote=\"1\" pic=\"1\" tbl=\"1\" equation=\"1\"/>");
        sb.append("<hh:refList>");
        fontfaces(sb, doc);
        borderFills(sb, doc);
        charProperties(sb, doc);
        tabProperties(sb, doc);
        numberings(sb, doc);
        paraProperties(sb, doc);
        styles(sb, doc);
        sb.append("</hh:refList>");
        sb.append("<hh:compatibleDocument targetProgram=\"HWP201X\"><hh:layoutCompatibility/></hh:compatibleDocument>");
        sb.append("<hh:docOption><hh:linkinfo path=\"\" pageInherit=\"0\" footnoteInherit=\"0\"/></hh:docOption>");
        sb.append("<hh:trackchageConfig flags=\"56\"/>");
        sb.append("</hh:head>");
        return sb.toString();
    }

    private static void fontfaces(StringBuilder sb, WDocument doc) {
        sb.append("<hh:fontfaces itemCnt=\"").append(WDocument.LANGS.length).append("\">");
        for (int l = 0; l < WDocument.LANGS.length; l++) {
            List<String> list = doc.fonts[l];
            sb.append("<hh:fontface lang=\"").append(WDocument.LANGS[l]).append("\" fontCnt=\"").append(list.size()).append("\">");
            for (int i = 0; i < list.size(); i++) {
                sb.append("<hh:font id=\"").append(i).append("\" face=\"").append(Xml.esc(list.get(i)))
                  .append("\" type=\"TTF\" isEmbedded=\"0\">")
                  .append("<hh:typeInfo familyType=\"FCAT_GOTHIC\" weight=\"6\" proportion=\"4\" contrast=\"0\" strokeVariation=\"1\" armStyle=\"1\" letterform=\"1\" midline=\"1\" xHeight=\"1\"/>")
                  .append("</hh:font>");
            }
            sb.append("</hh:fontface>");
        }
        sb.append("</hh:fontfaces>");
    }

    /** 1: 선/채우기 없음, 2: 글자 모양 기본(채우기 none) — 한글 새 문서와 같다. 이후는 표 셀 등 */
    private static void borderFills(StringBuilder sb, WDocument doc) {
        String[] sides = { "leftBorder", "rightBorder", "topBorder", "bottomBorder" };
        sb.append("<hh:borderFills itemCnt=\"").append(doc.borderFillList.size()).append("\">");
        for (int i = 0; i < doc.borderFillList.size(); i++) {
            WBorderFill b = doc.borderFillList.get(i);
            sb.append("<hh:borderFill id=\"").append(i + 1).append("\" threeD=\"0\" shadow=\"0\" centerLine=\"NONE\" breakCellSeparateLine=\"0\">")
              .append("<hh:slash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/><hh:backSlash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>");
            for (int s = 0; s < 4; s++) {
                sb.append("<hh:").append(sides[s]).append(" type=\"").append(b.type[s]).append("\" width=\"")
                  .append(WBorderFill.WIDTHS[b.width[s]]).append("\" color=\"").append(Xml.color(b.color[s])).append("\"/>");
            }
            sb.append("<hh:diagonal type=\"SOLID\" width=\"0.1 mm\" color=\"#000000\"/>");
            if (b.fillColor != null) {
                String face = b.fillColor < 0 ? "none" : Xml.color(b.fillColor);
                sb.append("<hc:fillBrush><hc:winBrush faceColor=\"").append(face).append("\" hatchColor=\"#999999\" alpha=\"0\"/></hc:fillBrush>");
            }
            sb.append("</hh:borderFill>");
        }
        sb.append("</hh:borderFills>");
    }

    private static String langAttrs(String v) {
        return " hangul=\"" + v + "\" latin=\"" + v + "\" hanja=\"" + v + "\" japanese=\"" + v + "\" other=\"" + v + "\" symbol=\"" + v + "\" user=\"" + v + "\"";
    }

    private static void charProperties(StringBuilder sb, WDocument doc) {
        sb.append("<hh:charProperties itemCnt=\"").append(doc.charPrList.size()).append("\">");
        for (int i = 0; i < doc.charPrList.size(); i++) {
            WCharPr c = doc.charPrList.get(i);
            sb.append("<hh:charPr id=\"").append(i).append("\" height=\"").append(c.height)
              .append("\" textColor=\"").append(Xml.color(c.textColor))
              .append("\" shadeColor=\"").append(c.shadeColor == null ? "none" : Xml.color(c.shadeColor)).append("\" useFontSpace=\"0\" useKerning=\"0\" symMark=\"NONE\" borderFillIDRef=\"2\">");
            sb.append("<hh:fontRef");
            for (int l = 0; l < WDocument.LANGS.length; l++) {
                sb.append(' ').append(WDocument.LANGS[l].toLowerCase()).append("=\"").append(c.fontRef[l]).append('"');
            }
            sb.append("/>");
            sb.append("<hh:ratio").append(langAttrs(String.valueOf(c.ratio))).append("/>");
            sb.append("<hh:spacing").append(langAttrs(String.valueOf(c.spacing))).append("/>");
            // 상대 크기: 한글/한자/일어는 기준 크기, 서양 글꼴 언어는 서양 글꼴 크기 비율
            String rl = String.valueOf(c.relSizeLatin);
            sb.append("<hh:relSz hangul=\"100\" latin=\"").append(rl).append("\" hanja=\"100\" japanese=\"100\" other=\"")
              .append(rl).append("\" symbol=\"").append(rl).append("\" user=\"").append(rl).append("\"/>");
            sb.append("<hh:offset").append(langAttrs("0")).append("/>");
            if (c.italic) sb.append("<hh:italic/>");
            if (c.bold) sb.append("<hh:bold/>");
            if (c.superScript) sb.append("<hh:supscript/>");
            if (c.subScript) sb.append("<hh:subscript/>");
            sb.append("<hh:underline type=\"").append(c.underline).append("\" shape=\"SOLID\" color=\"#000000\"/>");
            // 취소선 없음은 한글처럼 shape="NONE"
            sb.append("<hh:strikeout shape=\"").append(c.strikeout ? "SOLID" : "NONE").append("\" color=\"#000000\"/>");
            sb.append("<hh:outline type=\"NONE\"/>");
            // 그림자 (가져오기는 종류가 NONE 이 아니면 CharShadowed 로 놓는다. 색/간격은 쓰지 않는다)
            sb.append("<hh:shadow type=\"").append(c.shadow ? "DROP" : "NONE").append("\" color=\"#C0C0C0\" offsetX=\"10\" offsetY=\"10\"/>");
            sb.append("</hh:charPr>");
        }
        sb.append("</hh:charProperties>");
    }

    /**
     * 여백과 줄 간격. WParaPr 의 값은 기본값(hp:default) 단위(HWPUNIT 의 두 배)이다. HwpUnitChar 분기(hp:case)는 HWPUNIT 이라
     * 여백과 고정 줄 간격을 절반으로 쓴다 (한글 2022 와 같다. 한글은 분기 값을 읽고, 가져오기는 기본값을 읽는다).
     * 기존: 분기에도 같은 값을 써서 한글에서 문단 여백/들여쓰기가 두 배로 보였다
     */
    private static void margin(StringBuilder sb, WParaPr p, boolean unitChar) {
        int d = unitChar ? 2 : 1;
        sb.append("<hh:margin>")
          .append("<hc:intent value=\"").append(p.indent / d).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:left value=\"").append(p.left / d).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:right value=\"").append(p.right / d).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:prev value=\"").append(p.prev / d).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:next value=\"").append(p.next / d).append("\" unit=\"HWPUNIT\"/>")
          .append("</hh:margin>")
          .append("<hh:lineSpacing type=\"").append(p.lineSpacingType).append("\" value=\"")
          .append("FIXED".equals(p.lineSpacingType) ? p.lineSpacing / d : p.lineSpacing).append("\" unit=\"HWPUNIT\"/>");
    }

    /** 탭 정의. 탭마다 hp:switch 로 HWPUNIT 값과 기본값(두 배)을 쓴다 (한글 2022 와 같다. 가져오기는 기본값을 읽는다) */
    private static void tabProperties(StringBuilder sb, WDocument doc) {
        sb.append("<hh:tabProperties itemCnt=\"").append(doc.tabPrList.size()).append("\">");
        for (int i = 0; i < doc.tabPrList.size(); i++) {
            WTabPr t = doc.tabPrList.get(i);
            sb.append("<hh:tabPr id=\"").append(i).append("\" autoTabLeft=\"").append(t.autoTabLeft ? 1 : 0)
              .append("\" autoTabRight=\"").append(t.autoTabRight ? 1 : 0).append('"');
            if (t.pos.isEmpty()) {
                sb.append("/>");
                continue;
            }
            sb.append('>');
            for (int k = 0; k < t.pos.size(); k++) {
                int pos = t.pos.get(k);
                String type = t.type.get(k);
                sb.append("<hp:switch><hp:case hp:required-namespace=\"http://www.hancom.co.kr/hwpml/2016/HwpUnitChar\">")
                  .append("<hh:tabItem pos=\"").append(pos).append("\" type=\"").append(type).append("\" leader=\"NONE\" unit=\"HWPUNIT\"/>")
                  .append("</hp:case><hp:default>")
                  .append("<hh:tabItem pos=\"").append(pos * 2).append("\" type=\"").append(type).append("\" leader=\"NONE\"/>")
                  .append("</hp:default></hp:switch>");
            }
            sb.append("</hh:tabPr>");
        }
        sb.append("</hh:tabProperties>");
    }

    /** 문단 번호와 글머리표 정의 (한글 2022 와 같은 요소 구성) */
    private static void numberings(StringBuilder sb, WDocument doc) {
        if (!doc.numberings.isEmpty()) {
            sb.append("<hh:numberings itemCnt=\"").append(doc.numberings.size()).append("\">");
            for (int i = 0; i < doc.numberings.size(); i++) {
                WNumbering n = doc.numberings.get(i);
                sb.append("<hh:numbering id=\"").append(i + 1).append("\" start=\"0\">");
                for (int l = 0; l < WNumbering.LEVELS; l++) {
                    sb.append("<hh:paraHead start=\"").append(n.start[l]).append("\" level=\"").append(l + 1)
                      .append("\" align=\"").append(n.align[l]).append("\" useInstWidth=\"0\" autoIndent=\"1\" widthAdjust=\"0\"")
                      .append(" textOffsetType=\"HWPUNIT\" textOffset=\"").append(n.textOffset[l])
                      .append("\" numFormat=\"").append(n.numFormat[l]).append("\" charPrIDRef=\"")
                      .append(n.charPrId[l] < 0 ? 4294967295L : n.charPrId[l]).append("\" checkable=\"0\"");
                    if (n.format[l].isEmpty()) {
                        sb.append("/>");
                    } else {
                        sb.append('>').append(Xml.esc(n.format[l])).append("</hh:paraHead>");
                    }
                }
                sb.append("</hh:numbering>");
            }
            sb.append("</hh:numberings>");
        }
        if (!doc.bullets.isEmpty()) {
            sb.append("<hh:bullets itemCnt=\"").append(doc.bullets.size()).append("\">");
            for (int i = 0; i < doc.bullets.size(); i++) {
                WNumbering b = doc.bullets.get(i);
                sb.append("<hh:bullet id=\"").append(i + 1).append("\" char=\"").append(Xml.esc(b.bulletChar))
                  .append("\" useImage=\"").append(b.imageId != null ? 1 : 0).append("\">");
                if (b.imageId != null) {
                    sb.append("<hc:img binaryItemIDRef=\"").append(b.imageId).append("\" bright=\"0\" contrast=\"0\" effect=\"REAL_PIC\" alpha=\"0\"/>");
                }
                sb.append("<hh:paraHead level=\"0\" align=\"").append(b.align[0])
                  .append("\" useInstWidth=\"0\" autoIndent=\"1\" widthAdjust=\"0\" textOffsetType=\"HWPUNIT\" textOffset=\"")
                  .append(b.textOffset[0]).append("\" numFormat=\"DIGIT\" charPrIDRef=\"4294967295\" checkable=\"0\"/>");
                sb.append("</hh:bullet>");
            }
            sb.append("</hh:bullets>");
        }
    }

    private static void paraProperties(StringBuilder sb, WDocument doc) {
        sb.append("<hh:paraProperties itemCnt=\"").append(doc.paraPrList.size()).append("\">");
        for (int i = 0; i < doc.paraPrList.size(); i++) {
            WParaPr p = doc.paraPrList.get(i);
            sb.append("<hh:paraPr id=\"").append(i).append("\" tabPrIDRef=\"").append(p.tabPrId).append("\" condense=\"").append(p.condense).append("\" fontLineHeight=\"0\" snapToGrid=\"1\" suppressLineNumbers=\"0\" checked=\"0\">");
            sb.append("<hh:align horizontal=\"").append(p.align).append("\" vertical=\"BASELINE\"/>");
            sb.append("<hh:heading type=\"").append(p.headingType).append("\" idRef=\"").append(p.headingId)
              .append("\" level=\"").append(p.headingLevel).append("\"/>");
            // 줄 나눔 기준. 한글은 이름과 달리 KEEP_WORD 가 글자, BREAK_WORD 가 어절이다 (HwpRecord_ParaShape)
            sb.append("<hh:breakSetting breakLatinWord=\"")
              .append(p.breakLatin == 2 ? "BREAK_WORD" : p.breakLatin == 1 ? "HYPHENATION" : "KEEP_WORD")
              .append("\" breakNonLatinWord=\"").append(p.breakNonLatin == 0 ? "BREAK_WORD" : "KEEP_WORD")
              .append("\" widowOrphan=\"0\" keepWithNext=\"").append(p.keepWithNext ? 1 : 0)
              .append("\" keepLines=\"").append(p.keepLines ? 1 : 0).append("\" pageBreakBefore=\"0\" lineWrap=\"BREAK\"/>");
            sb.append("<hh:autoSpacing eAsianEng=\"0\" eAsianNum=\"0\"/>");
            // 한글 2022 는 여백/줄 간격을 HwpUnitChar 분기(HWPUNIT)와 기본값(두 배)으로 두 번 쓴다
            sb.append("<hp:switch><hp:case hp:required-namespace=\"http://www.hancom.co.kr/hwpml/2016/HwpUnitChar\">");
            margin(sb, p, true);
            sb.append("</hp:case><hp:default>");
            margin(sb, p, false);
            sb.append("</hp:default></hp:switch>");
            sb.append("<hh:border borderFillIDRef=\"").append(p.borderFillId).append("\" offsetLeft=\"0\" offsetRight=\"0\" offsetTop=\"0\" offsetBottom=\"0\" connect=\"0\" ignoreMargin=\"0\"/>");
            sb.append("</hh:paraPr>");
        }
        sb.append("</hh:paraProperties>");
    }

    private static void styles(StringBuilder sb, WDocument doc) {
        sb.append("<hh:styles itemCnt=\"").append(doc.styleList.size()).append("\">");
        for (int i = 0; i < doc.styleList.size(); i++) {
            WStyle s = doc.styleList.get(i);
            sb.append("<hh:style id=\"").append(i).append("\" type=\"PARA\" name=\"").append(Xml.esc(s.name))
              .append("\" engName=\"").append(Xml.esc(s.engName)).append("\" paraPrIDRef=\"").append(s.paraPrId)
              .append("\" charPrIDRef=\"").append(s.charPrId).append("\" nextStyleIDRef=\"").append(s.nextStyleId)
              .append("\" langID=\"1042\" lockForm=\"0\"/>");
        }
        sb.append("</hh:styles>");
    }
}
