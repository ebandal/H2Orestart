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

/**
 * Contents/sectionN.xml.
 * 첫 문단의 첫 run 에 구역 정의(hp:secPr)와 단 정의(hp:colPr)를 넣는다 (한글 2022 출력과 같다).
 * 줄 정보(hp:linesegarray)는 쓰지 않는다. 한글은 줄 정보가 없는 문단을 열 때 다시 계산한다.
 */
final class SectionXml {
    private SectionXml() {}

    static String build(WDocument doc, WSection sec) {
        StringBuilder sb = new StringBuilder(16384);
        sb.append(Xml.DECL);
        sb.append("<hs:sec").append(Xml.NS).append('>');
        if (sec.paragraphs.isEmpty()) {
            sec.paragraphs.add(new WParagraph());
        }
        for (int i = 0; i < sec.paragraphs.size(); i++) {
            paragraph(sb, sec, sec.paragraphs.get(i), i == 0);
        }
        sb.append("</hs:sec>");
        return sb.toString();
    }

    private static void paragraph(StringBuilder sb, WSection sec, WParagraph p, boolean first) {
        sb.append("<hp:p id=\"0\" paraPrIDRef=\"").append(p.paraPrId).append("\" styleIDRef=\"").append(p.styleId)
          .append("\" pageBreak=\"").append(p.pageBreak ? 1 : 0).append("\" columnBreak=\"0\" merged=\"0\">");
        if (first) {
            sb.append("<hp:run charPrIDRef=\"").append(p.runs.isEmpty() ? 0 : p.runs.get(0).charPrId).append("\">");
            secPr(sb, sec);
            sb.append("<hp:ctrl><hp:colPr id=\"\" type=\"NEWSPAPER\" layout=\"LEFT\" colCount=\"1\" sameSz=\"1\" sameGap=\"0\"/></hp:ctrl>");
            sb.append("</hp:run>");
        }
        if (p.runs.isEmpty()) {
            sb.append("<hp:run charPrIDRef=\"").append(p.lastCharPrId()).append("\"><hp:t/></hp:run>");
        }
        for (WParagraph.WRun r : p.runs) {
            run(sb, r);
        }
        sb.append("</hp:p>");
    }

    /** 텍스트를 hp:t 로 쓰고 탭/줄바꿈/묶음 빈칸/하이픈은 hp:t 안의 hp:tab, hp:lineBreak, hp:nbSpace, hp:hyphen 으로 쓴다 */
    private static void run(StringBuilder sb, WParagraph.WRun r) {
        sb.append("<hp:run charPrIDRef=\"").append(r.charPrId).append("\"><hp:t>");
        String t = r.text == null ? "" : r.text;
        int start = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\t' || c == '\n' || c == 0x00A0 || c == 0x2011) {
                sb.append(Xml.esc(t.substring(start, i)));
                if (c == '\t') {
                    sb.append("<hp:tab width=\"4000\" leader=\"0\" type=\"1\"/>");
                } else if (c == '\n') {
                    sb.append("<hp:lineBreak/>");
                } else if (c == 0x00A0) {
                    sb.append("<hp:nbSpace/>");     // 묶음 빈칸 (LibreOffice 의 줄바꿈 없는 빈칸)
                } else {
                    sb.append("<hp:hyphen/>");      // 줄바꿈 없는 하이픈
                }
                start = i + 1;
            }
        }
        sb.append(Xml.esc(t.substring(start)));
        sb.append("</hp:t></hp:run>");
    }

    private static void secPr(StringBuilder sb, WSection s) {
        sb.append("<hp:secPr id=\"\" textDirection=\"HORIZONTAL\" spaceColumns=\"1134\" tabStop=\"8000\" tabStopVal=\"4000\" tabStopUnit=\"HWPUNIT\" outlineShapeIDRef=\"0\" memoShapeIDRef=\"0\" textVerticalWidthHead=\"0\" masterPageCnt=\"0\">");
        sb.append("<hp:grid lineGrid=\"0\" charGrid=\"0\" wonggojiFormat=\"0\"/>");
        sb.append("<hp:startNum pageStartsOn=\"BOTH\" page=\"0\" pic=\"0\" tbl=\"0\" equation=\"0\"/>");
        sb.append("<hp:visibility hideFirstHeader=\"0\" hideFirstFooter=\"0\" hideFirstMasterPage=\"0\" border=\"SHOW_ALL\" fill=\"SHOW_ALL\" hideFirstPageNum=\"0\" hideFirstEmptyLine=\"0\" showLineNumber=\"0\"/>");
        sb.append("<hp:lineNumberShape restartType=\"0\" countBy=\"0\" distance=\"0\" startNumber=\"0\"/>");
        // landscape: WIDELY = 세로 방향 용지 (한글 표기), NARROWLY = 가로 방향
        sb.append("<hp:pagePr landscape=\"").append(s.landscape ? "NARROWLY" : "WIDELY").append("\" width=\"").append(s.pageWidth)
          .append("\" height=\"").append(s.pageHeight).append("\" gutterType=\"LEFT_ONLY\">");
        sb.append("<hp:margin header=\"").append(s.marginHeader).append("\" footer=\"").append(s.marginFooter)
          .append("\" gutter=\"").append(s.marginGutter).append("\" left=\"").append(s.marginLeft)
          .append("\" right=\"").append(s.marginRight).append("\" top=\"").append(s.marginTop)
          .append("\" bottom=\"").append(s.marginBottom).append("\"/>");
        sb.append("</hp:pagePr>");
        sb.append("<hp:footNotePr><hp:autoNumFormat type=\"DIGIT\" userChar=\"\" prefixChar=\"\" suffixChar=\")\" supscript=\"0\"/>")
          .append("<hp:noteLine length=\"-1\" type=\"SOLID\" width=\"0.12 mm\" color=\"#000000\"/>")
          .append("<hp:noteSpacing betweenNotes=\"283\" belowLine=\"567\" aboveLine=\"850\"/>")
          .append("<hp:numbering type=\"ON_SECTION\" newNum=\"1\"/><hp:placement place=\"EACH_COLUMN\" beneathText=\"0\"/></hp:footNotePr>");
        sb.append("<hp:endNotePr><hp:autoNumFormat type=\"DIGIT\" userChar=\"\" prefixChar=\"\" suffixChar=\")\" supscript=\"0\"/>")
          .append("<hp:noteLine length=\"-4\" type=\"SOLID\" width=\"0.12 mm\" color=\"#000000\"/>")
          .append("<hp:noteSpacing betweenNotes=\"0\" belowLine=\"567\" aboveLine=\"850\"/>")
          .append("<hp:numbering type=\"ON_SECTION\" newNum=\"1\"/><hp:placement place=\"END_OF_DOCUMENT\" beneathText=\"0\"/></hp:endNotePr>");
        for (String type : new String[] { "BOTH", "EVEN", "ODD" }) {
            sb.append("<hp:pageBorderFill type=\"").append(type).append("\" borderFillIDRef=\"1\" textBorder=\"PAPER\" headerInside=\"0\" footerInside=\"0\" fillArea=\"PAGE\">")
              .append("<hp:offset left=\"1417\" right=\"1417\" top=\"1417\" bottom=\"1417\"/></hp:pageBorderFill>");
        }
        sb.append("</hp:secPr>");
    }
}
