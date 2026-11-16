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
            headerFooters(sb, sec);
            sb.append("</hp:run>");
        }
        if (p.table != null) {
            // 표는 문단의 run 안에 넣고 빈 텍스트를 붙인다 (한글 2022 와 같다)
            sb.append("<hp:run charPrIDRef=\"").append(p.lastCharPrId()).append("\">");
            table(sb, sec, p.table);
            sb.append("<hp:t/></hp:run>");
        } else if (p.runs.isEmpty()) {
            sb.append("<hp:run charPrIDRef=\"").append(p.lastCharPrId()).append("\"><hp:t/></hp:run>");
        }
        for (WParagraph.WRun r : p.runs) {
            if (p.table != null && r.shape == null && (r.text == null || r.text.isEmpty())) continue;
            run(sb, sec, r);
        }
        sb.append("</hp:p>");
    }

    /**
     * 표 (hp:tbl). 위치는 문단 기준 왼쪽에서부터 (가져오기가 LEFT_AND_WIDTH 로 놓는다).
     * 셀 여백은 한글 기본값 (가져오기는 셀 여백을 쓰지 않는다).
     */
    private static void table(StringBuilder sb, WSection sec, WTable t) {
        sb.append("<hp:tbl id=\"0\" zOrder=\"0\" numberingType=\"TABLE\" textWrap=\"TOP_AND_BOTTOM\" textFlow=\"BOTH_SIDES\" lock=\"0\" dropcapstyle=\"None\"")
          .append(" pageBreak=\"").append(t.pageBreak).append("\" repeatHeader=\"").append(t.repeatHeader ? 1 : 0)
          .append("\" rowCnt=\"").append(t.rowCnt).append("\" colCnt=\"").append(t.colCnt)
          .append("\" cellSpacing=\"0\" borderFillIDRef=\"").append(t.borderFillId).append("\" noAdjust=\"0\">");
        sb.append("<hp:sz width=\"").append(t.width).append("\" widthRelTo=\"ABSOLUTE\" height=\"").append(t.height)
          .append("\" heightRelTo=\"ABSOLUTE\" protect=\"0\"/>");
        sb.append("<hp:pos treatAsChar=\"").append(t.treatAsChar ? 1 : 0).append("\" affectLSpacing=\"0\" flowWithText=\"1\" allowOverlap=\"0\" holdAnchorAndSO=\"0\"")
          .append(" vertRelTo=\"PARA\" horzRelTo=\"PARA\" vertAlign=\"TOP\" horzAlign=\"LEFT\" vertOffset=\"").append(t.vertOffset)
          .append("\" horzOffset=\"").append(t.horzOffset).append("\"/>");
        sb.append("<hp:outMargin left=\"0\" right=\"0\" top=\"0\" bottom=\"0\"/>");
        sb.append("<hp:inMargin left=\"510\" right=\"510\" top=\"141\" bottom=\"141\"/>");
        for (int r = 0; r < t.rowCnt; r++) {
            sb.append("<hp:tr>");
            for (WTable.WCell c : t.cells) {
                if (c.row != r) continue;
                sb.append("<hp:tc name=\"\" header=\"").append(c.header ? 1 : 0)
                  .append("\" hasMargin=\"0\" protect=\"0\" editable=\"0\" dirty=\"0\" borderFillIDRef=\"").append(c.borderFillId).append("\">");
                sb.append("<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"").append(c.vertAlign)
                  .append("\" linkListIDRef=\"0\" linkListNextIDRef=\"0\" textWidth=\"0\" textHeight=\"0\" hasTextRef=\"0\" hasNumRef=\"0\">");
                if (c.paragraphs.isEmpty()) {
                    c.paragraphs.add(new WParagraph());
                }
                for (WParagraph cp : c.paragraphs) {
                    paragraph(sb, sec, cp, false);
                }
                sb.append("</hp:subList>");
                sb.append("<hp:cellAddr colAddr=\"").append(c.col).append("\" rowAddr=\"").append(c.row).append("\"/>");
                sb.append("<hp:cellSpan colSpan=\"").append(c.colSpan).append("\" rowSpan=\"").append(c.rowSpan).append("\"/>");
                sb.append("<hp:cellSz width=\"").append(c.width).append("\" height=\"").append(c.height).append("\"/>");
                sb.append("<hp:cellMargin left=\"510\" right=\"510\" top=\"141\" bottom=\"141\"/>");
                sb.append("</hp:tc>");
            }
            sb.append("</hp:tr>");
        }
        sb.append("</hp:tbl>");
    }

    /** 텍스트를 hp:t 로 쓰고 탭/줄바꿈/묶음 빈칸/하이픈은 hp:t 안의 hp:tab, hp:lineBreak, hp:nbSpace, hp:hyphen 으로 쓴다 */
    private static void run(StringBuilder sb, WSection sec, WParagraph.WRun r) {
        sb.append("<hp:run charPrIDRef=\"").append(r.charPrId).append("\">");
        String t = r.text == null ? "" : r.text;
        if (r.autoNum != null) {
            // 자동 번호 (쪽 번호, 전체 쪽수)
            sb.append("<hp:ctrl><hp:autoNum num=\"1\" numType=\"").append(r.autoNum).append("\">")
              .append("<hp:autoNumFormat type=\"").append(r.autoNumFormat).append("\" userChar=\"\" prefixChar=\"\" suffixChar=\"\" supscript=\"0\"/>")
              .append("</hp:autoNum></hp:ctrl>");
            if (t.isEmpty()) {
                sb.append("</hp:run>");
                return;
            }
        }
        if (r.shape != null) {
            shape(sb, sec, r.shape);
            if (t.isEmpty()) {
                sb.append("</hp:run>");
                return;
            }
        }
        sb.append("<hp:t>");
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

    /** 그림(hp:pic) 또는 글상자(hp:rect + hp:drawText). 요소 구성은 한글 2022 출력과 같다 */
    private static void shape(StringBuilder sb, WSection sec, WShape s) {
        int id = 1000000000 + s.zOrder;
        String tag = s.kind == WShape.Kind.PICTURE ? "hp:pic" : s.kind == WShape.Kind.LINE ? "hp:line" : "hp:rect";
        sb.append('<').append(tag).append(" id=\"").append(id).append("\" zOrder=\"").append(s.zOrder)
          .append("\" numberingType=\"PICTURE\" textWrap=\"").append(s.textWrap).append("\" textFlow=\"").append(s.textFlow)
          .append("\" lock=\"0\" dropcapstyle=\"None\" href=\"\" groupLevel=\"0\" instid=\"").append(id + 1)
          .append(s.kind == WShape.Kind.PICTURE ? "\" reverse=\"0\">" : s.kind == WShape.Kind.LINE ? "\" isReverseHV=\"0\">" : "\" ratio=\"0\">");
        sb.append("<hp:offset x=\"0\" y=\"0\"/>");
        sb.append("<hp:orgSz width=\"").append(s.width).append("\" height=\"").append(s.height).append("\"/>");
        sb.append("<hp:curSz width=\"").append(s.width).append("\" height=\"").append(s.height).append("\"/>");
        sb.append("<hp:flip horizontal=\"0\" vertical=\"0\"/>");
        sb.append("<hp:rotationInfo angle=\"0\" centerX=\"").append(s.width / 2).append("\" centerY=\"").append(s.height / 2)
          .append("\" rotateimage=\"1\"/>");
        sb.append("<hp:renderingInfo><hc:transMatrix e1=\"1\" e2=\"0\" e3=\"0\" e4=\"0\" e5=\"1\" e6=\"0\"/>")
          .append("<hc:scaMatrix e1=\"1\" e2=\"0\" e3=\"0\" e4=\"0\" e5=\"1\" e6=\"0\"/>")
          .append("<hc:rotMatrix e1=\"1\" e2=\"0\" e3=\"0\" e4=\"0\" e5=\"1\" e6=\"0\"/></hp:renderingInfo>");
        if (s.kind == WShape.Kind.PICTURE) {
            sb.append("<hc:img binaryItemIDRef=\"").append(s.binItemId).append("\" bright=\"0\" contrast=\"0\" effect=\"REAL_PIC\" alpha=\"0\"/>");
            sb.append("<hp:imgRect>");
            points(sb, s.width, s.height);
            sb.append("</hp:imgRect>");
            // 자르기 없음 = 원본 그림 전체
            sb.append("<hp:imgClip left=\"0\" right=\"").append(s.imgWidth).append("\" top=\"0\" bottom=\"").append(s.imgHeight).append("\"/>");
            sb.append("<hp:inMargin left=\"0\" right=\"0\" top=\"0\" bottom=\"0\"/>");
            sb.append("<hp:imgDim dimwidth=\"").append(s.imgWidth).append("\" dimheight=\"").append(s.imgHeight).append("\"/>");
            sb.append("<hp:effects/>");
        } else if (s.kind == WShape.Kind.LINE || s.kind == WShape.Kind.RECT) {
            // 선, 글 없는 사각형
            sb.append("<hp:lineShape color=\"").append(Xml.color(s.lineColor)).append("\" width=\"").append(s.lineWidth)
              .append("\" style=\"").append(s.lineStyle)
              .append("\" endCap=\"FLAT\" headStyle=\"NORMAL\" tailStyle=\"NORMAL\" headfill=\"1\" tailfill=\"1\" headSz=\"MEDIUM_MEDIUM\" tailSz=\"MEDIUM_MEDIUM\" outlineStyle=\"NORMAL\" alpha=\"0\"/>");
            if (s.kind == WShape.Kind.RECT && s.fillColor != null) {
                sb.append("<hc:fillBrush><hc:winBrush faceColor=\"").append(Xml.color(s.fillColor))
                  .append("\" hatchColor=\"#000000\" alpha=\"0\"/></hc:fillBrush>");
            }
            sb.append("<hp:shadow type=\"NONE\" color=\"#B2B2B2\" offsetX=\"0\" offsetY=\"0\" alpha=\"0\"/>");
            if (s.kind == WShape.Kind.LINE) {
                sb.append("<hc:startPt x=\"0\" y=\"0\"/><hc:endPt x=\"").append(s.width).append("\" y=\"").append(s.height).append("\"/>");
            } else {
                points(sb, s.width, s.height);
            }
        } else {
            sb.append("<hp:lineShape color=\"").append(Xml.color(s.lineColor)).append("\" width=\"").append(s.lineWidth)
              .append("\" style=\"").append(s.lineStyle)
              .append("\" endCap=\"FLAT\" headStyle=\"NORMAL\" tailStyle=\"NORMAL\" headfill=\"1\" tailfill=\"1\" headSz=\"SMALL_SMALL\" tailSz=\"SMALL_SMALL\" outlineStyle=\"NORMAL\" alpha=\"0\"/>");
            if (s.fillColor != null) {
                sb.append("<hc:fillBrush><hc:winBrush faceColor=\"").append(Xml.color(s.fillColor))
                  .append("\" hatchColor=\"#000000\" alpha=\"0\"/></hc:fillBrush>");
            }
            sb.append("<hp:shadow type=\"NONE\" color=\"#B2B2B2\" offsetX=\"0\" offsetY=\"0\" alpha=\"0\"/>");
            sb.append("<hp:drawText lastWidth=\"").append(s.width).append("\" name=\"\" editable=\"0\">");
            sb.append("<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"").append(s.textVertAlign)
              .append("\" linkListIDRef=\"0\" linkListNextIDRef=\"0\" textWidth=\"0\" textHeight=\"0\" hasTextRef=\"0\" hasNumRef=\"0\">");
            if (s.paragraphs.isEmpty()) {
                s.paragraphs.add(new WParagraph());
            }
            for (WParagraph p : s.paragraphs) {
                paragraph(sb, sec, p, false);
            }
            sb.append("</hp:subList>");
            sb.append("<hp:textMargin left=\"").append(s.textMargin[0]).append("\" right=\"").append(s.textMargin[1])
              .append("\" top=\"").append(s.textMargin[2]).append("\" bottom=\"").append(s.textMargin[3]).append("\"/>");
            sb.append("</hp:drawText>");
            points(sb, s.width, s.height);
        }
        sb.append("<hp:sz width=\"").append(s.width).append("\" widthRelTo=\"ABSOLUTE\" height=\"").append(s.height)
          .append("\" heightRelTo=\"ABSOLUTE\" protect=\"0\"/>");
        sb.append("<hp:pos treatAsChar=\"").append(s.treatAsChar ? 1 : 0)
          // 개체가 쪽(셀) 영역 안에 머물도록 (한글 기본값. 0 이면 셀 안의 개체가 셀 밖 기준으로 놓인다)
          .append("\" affectLSpacing=\"0\" flowWithText=\"1")
          .append("\" allowOverlap=\"").append(s.treatAsChar ? 0 : 1)
          .append("\" holdAnchorAndSO=\"0\" vertRelTo=\"").append(s.vertRelTo).append("\" horzRelTo=\"").append(s.horzRelTo)
          .append("\" vertAlign=\"").append(s.vertAlign).append("\" horzAlign=\"").append(s.horzAlign)
          // 거리는 부호 없는 32비트로 쓴다 (한글: -1 → 4294967295. 가져오기도 parseUnsignedInt)
          .append("\" vertOffset=\"").append(s.vertOffset & 0xFFFFFFFFL).append("\" horzOffset=\"").append(s.horzOffset & 0xFFFFFFFFL).append("\"/>");
        sb.append("<hp:outMargin left=\"0\" right=\"0\" top=\"0\" bottom=\"0\"/>");
        sb.append("<hp:shapeComment>").append(s.kind == WShape.Kind.PICTURE ? "그림입니다." : s.kind == WShape.Kind.LINE ? "선입니다." : "사각형입니다.").append("</hp:shapeComment>");
        sb.append("</").append(tag).append('>');
    }

    /** 머리말/꼬리말과 쪽 번호 위치. 구역 정의 다음에 둔다 (가져오기가 구역의 쪽 스타일을 먼저 만든다) */
    private static void headerFooters(StringBuilder sb, WSection sec) {
        int textWidth = sec.pageWidth - sec.marginLeft - sec.marginRight;
        for (WSection.HeaderFooter hf : sec.headerFooters) {
            String tag = hf.header ? "hp:header" : "hp:footer";
            sb.append("<hp:ctrl><").append(tag).append(" id=\"\" applyPageType=\"").append(hf.applyPageType).append("\">");
            sb.append("<hp:subList id=\"\" textDirection=\"HORIZONTAL\" lineWrap=\"BREAK\" vertAlign=\"").append(hf.header ? "TOP" : "BOTTOM")
              .append("\" linkListIDRef=\"0\" linkListNextIDRef=\"0\" textWidth=\"").append(textWidth)
              .append("\" textHeight=\"").append(hf.header ? sec.marginHeader : sec.marginFooter)
              .append("\" hasTextRef=\"0\" hasNumRef=\"0\">");
            if (hf.paragraphs.isEmpty()) {
                hf.paragraphs.add(new WParagraph());
            }
            for (WParagraph p : hf.paragraphs) {
                paragraph(sb, sec, p, false);
            }
            sb.append("</hp:subList></").append(tag).append("></hp:ctrl>");
        }
        if (sec.pageNumPos != null) {
            sb.append("<hp:ctrl><hp:pageNum pos=\"").append(sec.pageNumPos).append("\" formatType=\"").append(sec.pageNumFormat)
              .append("\" sideChar=\"").append(Xml.esc(sec.pageNumSideChar)).append("\"/></hp:ctrl>");
        }
    }

    private static void points(StringBuilder sb, int w, int h) {
        sb.append("<hc:pt0 x=\"0\" y=\"0\"/><hc:pt1 x=\"").append(w).append("\" y=\"0\"/><hc:pt2 x=\"").append(w)
          .append("\" y=\"").append(h).append("\"/><hc:pt3 x=\"0\" y=\"").append(h).append("\"/>");
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
