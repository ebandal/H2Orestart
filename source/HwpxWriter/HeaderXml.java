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
        borderFills(sb);
        charProperties(sb, doc);
        sb.append("<hh:tabProperties itemCnt=\"1\"><hh:tabPr id=\"0\" autoTabLeft=\"0\" autoTabRight=\"0\"/></hh:tabProperties>");
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

    /** 1: 선/채우기 없음, 2: 글자 모양 기본(채우기 없음) — 한글 새 문서와 같다 */
    private static void borderFills(StringBuilder sb) {
        sb.append("<hh:borderFills itemCnt=\"2\">");
        for (int id = 1; id <= 2; id++) {
            sb.append("<hh:borderFill id=\"").append(id).append("\" threeD=\"0\" shadow=\"0\" centerLine=\"NONE\" breakCellSeparateLine=\"0\">")
              .append("<hh:slash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/><hh:backSlash type=\"NONE\" Crooked=\"0\" isCounter=\"0\"/>")
              .append("<hh:leftBorder type=\"NONE\" width=\"0.1 mm\" color=\"#000000\"/><hh:rightBorder type=\"NONE\" width=\"0.1 mm\" color=\"#000000\"/>")
              .append("<hh:topBorder type=\"NONE\" width=\"0.1 mm\" color=\"#000000\"/><hh:bottomBorder type=\"NONE\" width=\"0.1 mm\" color=\"#000000\"/>")
              .append("<hh:diagonal type=\"SOLID\" width=\"0.1 mm\" color=\"#000000\"/>");
            if (id == 2) {
                sb.append("<hc:fillBrush><hc:winBrush faceColor=\"none\" hatchColor=\"#999999\" alpha=\"0\"/></hc:fillBrush>");
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
              .append("\" shadeColor=\"none\" useFontSpace=\"0\" useKerning=\"0\" symMark=\"NONE\" borderFillIDRef=\"2\">");
            sb.append("<hh:fontRef");
            for (int l = 0; l < WDocument.LANGS.length; l++) {
                sb.append(' ').append(WDocument.LANGS[l].toLowerCase()).append("=\"").append(c.fontRef[l]).append('"');
            }
            sb.append("/>");
            sb.append("<hh:ratio").append(langAttrs("100")).append("/>");
            sb.append("<hh:spacing").append(langAttrs("0")).append("/>");
            sb.append("<hh:relSz").append(langAttrs("100")).append("/>");
            sb.append("<hh:offset").append(langAttrs("0")).append("/>");
            if (c.italic) sb.append("<hh:italic/>");
            if (c.bold) sb.append("<hh:bold/>");
            sb.append("<hh:underline type=\"").append(c.underline).append("\" shape=\"SOLID\" color=\"#000000\"/>");
            // 취소선 없음은 한글처럼 shape="NONE"
            sb.append("<hh:strikeout shape=\"").append(c.strikeout ? "SOLID" : "NONE").append("\" color=\"#000000\"/>");
            sb.append("<hh:outline type=\"NONE\"/>");
            sb.append("<hh:shadow type=\"NONE\" color=\"#C0C0C0\" offsetX=\"10\" offsetY=\"10\"/>");
            sb.append("</hh:charPr>");
        }
        sb.append("</hh:charProperties>");
    }

    private static void margin(StringBuilder sb, WParaPr p) {
        sb.append("<hh:margin>")
          .append("<hc:intent value=\"").append(p.indent).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:left value=\"").append(p.left).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:right value=\"").append(p.right).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:prev value=\"").append(p.prev).append("\" unit=\"HWPUNIT\"/>")
          .append("<hc:next value=\"").append(p.next).append("\" unit=\"HWPUNIT\"/>")
          .append("</hh:margin>")
          .append("<hh:lineSpacing type=\"").append(p.lineSpacingType).append("\" value=\"").append(p.lineSpacing).append("\" unit=\"HWPUNIT\"/>");
    }

    private static void paraProperties(StringBuilder sb, WDocument doc) {
        sb.append("<hh:paraProperties itemCnt=\"").append(doc.paraPrList.size()).append("\">");
        for (int i = 0; i < doc.paraPrList.size(); i++) {
            WParaPr p = doc.paraPrList.get(i);
            sb.append("<hh:paraPr id=\"").append(i).append("\" tabPrIDRef=\"0\" condense=\"0\" fontLineHeight=\"0\" snapToGrid=\"1\" suppressLineNumbers=\"0\" checked=\"0\">");
            sb.append("<hh:align horizontal=\"").append(p.align).append("\" vertical=\"BASELINE\"/>");
            sb.append("<hh:heading type=\"NONE\" idRef=\"0\" level=\"0\"/>");
            sb.append("<hh:breakSetting breakLatinWord=\"KEEP_WORD\" breakNonLatinWord=\"KEEP_WORD\" widowOrphan=\"0\" keepWithNext=\"0\" keepLines=\"0\" pageBreakBefore=\"0\" lineWrap=\"BREAK\"/>");
            sb.append("<hh:autoSpacing eAsianEng=\"0\" eAsianNum=\"0\"/>");
            // 한글 2022 는 여백/줄 간격을 HwpUnitChar 분기와 기본값에 같은 내용으로 두 번 쓴다
            sb.append("<hp:switch><hp:case hp:required-namespace=\"http://www.hancom.co.kr/hwpml/2016/HwpUnitChar\">");
            margin(sb, p);
            sb.append("</hp:case><hp:default>");
            margin(sb, p);
            sb.append("</hp:default></hp:switch>");
            sb.append("<hh:border borderFillIDRef=\"2\" offsetLeft=\"0\" offsetRight=\"0\" offsetTop=\"0\" offsetBottom=\"0\" connect=\"0\" ignoreMargin=\"0\"/>");
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
