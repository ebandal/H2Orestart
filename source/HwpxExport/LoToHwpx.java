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
package HwpxExport;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.star.awt.FontSlant;
import com.sun.star.awt.FontStrikeout;
import com.sun.star.awt.FontUnderline;
import com.sun.star.awt.FontWeight;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XEnumerationAccess;
import com.sun.star.container.XNameAccess;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.style.BreakType;
import com.sun.star.style.LineSpacing;
import com.sun.star.style.LineSpacingMode;
import com.sun.star.style.ParagraphAdjust;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.text.XText;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.uno.AnyConverter;
import com.sun.star.uno.UnoRuntime;

import HwpxWriter.WCharPr;
import HwpxWriter.WDocument;
import HwpxWriter.WParaPr;
import HwpxWriter.WParagraph;
import HwpxWriter.WSection;

/**
 * LibreOffice Writer 문서(UNO) 를 HWPX 쓰기용 모델(WDocument) 로 옮긴다.
 * 1단계 범위: 문단 텍스트, 글자 모양(글꼴/크기/굵게/기울임/밑줄/취소선/색), 문단 정렬/여백/줄 간격, 용지 크기/여백.
 * 표는 셀의 문단을 차례로 본문 문단으로 쓴다 (표 구조는 이후 단계).
 * 단위: LibreOffice 1/100 mm → HWPUNIT 은 가져오기(Transform.translateHwp2Office) 의 역변환.
 */
public class LoToHwpx {
    private static final Logger log = Logger.getLogger(LoToHwpx.class.getName());

    private final WDocument doc = WDocument.createDefault();
    private final WSection section = new WSection();
    /** 글꼴 크기 측정용 (가져오기의 글꼴별 줄 간격 보정값을 같은 방식으로 구한다). 없으면 기본값 */
    private com.sun.star.awt.XDevice device;
    private final java.util.Map<String, Double> alphaCache = new java.util.HashMap<String, Double>();

    public static WDocument convert(XComponent component) {
        return convert(component, null);
    }

    public static WDocument convert(XComponent component, com.sun.star.uno.XComponentContext context) {
        LoToHwpx c = new LoToHwpx();
        if (context != null) {
            try {
                Object o = context.getServiceManager().createInstanceWithContext("com.sun.star.awt.Toolkit", context);
                com.sun.star.awt.XToolkit tk = UnoRuntime.queryInterface(com.sun.star.awt.XToolkit.class, o);
                c.device = tk.createScreenCompatibleDevice(0, 0);
            } catch (com.sun.star.uno.Exception e) {
                log.log(Level.WARNING, e.toString(), e);
            }
        }
        XTextDocument textDoc = UnoRuntime.queryInterface(XTextDocument.class, component);
        if (textDoc == null) {
            throw new IllegalArgumentException("not a text document");
        }
        c.doc.sections.add(c.section);
        c.page(textDoc);
        c.text(textDoc.getText());
        return c.doc;
    }

    /** 1/100 mm → HWPUNIT (Transform.translateHwp2Office 의 역) */
    static int hwp(int mm100) {
        return (int) Math.round(mm100 * 59529.0 / 21000.0);
    }

    private void text(XText text) {
        XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, text);
        if (ea == null) return;
        XEnumeration en = ea.createEnumeration();
        try {
            while (en.hasMoreElements()) {
                Object elem = en.nextElement();
                XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, elem);
                if (si == null) continue;
                if (si.supportsService("com.sun.star.text.Paragraph")) {
                    paragraph(elem);
                } else if (si.supportsService("com.sun.star.text.TextTable")) {
                    table(elem);
                }
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    /** 1단계: 표는 셀 문단을 본문 문단으로 */
    private void table(Object tableObj) throws com.sun.star.uno.Exception {
        com.sun.star.text.XTextTable table = UnoRuntime.queryInterface(com.sun.star.text.XTextTable.class, tableObj);
        if (table == null) return;
        for (String name : table.getCellNames()) {
            XText cellText = UnoRuntime.queryInterface(XText.class, table.getCellByName(name));
            if (cellText != null) {
                boolean saved = singleParaCell;
                singleParaCell = countParagraphs(cellText) == 1;
                text(cellText);
                singleParaCell = saved;
            }
        }
    }

    /** 지금 쓰는 문단이 문단 하나뿐인 셀 안에 있는가 (가져오기의 셀 줄 간격 보정을 되돌리기 위해) */
    private boolean singleParaCell;

    private static int countParagraphs(XText text) throws com.sun.star.uno.Exception {
        XEnumeration en = UnoRuntime.queryInterface(XEnumerationAccess.class, text).createEnumeration();
        int n = 0;
        while (en.hasMoreElements()) {
            en.nextElement();
            n++;
        }
        return n;
    }

    private void paragraph(Object paraObj) throws com.sun.star.uno.Exception {
        XPropertySet pp = UnoRuntime.queryInterface(XPropertySet.class, paraObj);
        WParagraph p = new WParagraph();
        p.paraPrId = doc.addParaPr(paraPr(pp));
        Object bt = pp.getPropertyValue("BreakType");
        if (bt instanceof BreakType && (bt == BreakType.PAGE_BEFORE || bt == BreakType.PAGE_BOTH)) {
            p.pageBreak = !section.paragraphs.isEmpty();
        }

        XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, paraObj);
        XEnumeration en = ea.createEnumeration();
        while (en.hasMoreElements()) {
            Object portion = en.nextElement();
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, portion);
            String type = AnyConverter.toString(ps.getPropertyValue("TextPortionType"));
            if (!"Text".equals(type)) continue;
            String s = UnoRuntime.queryInterface(XTextRange.class, portion).getString();
            if (s.isEmpty()) continue;
            int cp = doc.addCharPr(charPr(ps));
            // 같은 글자 모양이 이어지면 run 을 합친다 (한글도 같은 모양은 한 run)
            if (!p.runs.isEmpty() && p.runs.get(p.runs.size() - 1).charPrId == cp) {
                p.runs.get(p.runs.size() - 1).text += s;
            } else {
                p.runs.add(new WParagraph.WRun(cp, s));
            }
        }
        if (p.runs.isEmpty()) {
            // 빈 문단도 문단 끝 글자 모양을 갖는다
            p.runs.add(new WParagraph.WRun(doc.addCharPr(charPr(pp)), ""));
        }
        section.paragraphs.add(p);
    }

    private WCharPr charPr(XPropertySet ps) throws com.sun.star.uno.Exception {
        WCharPr c = new WCharPr();
        float pt = AnyConverter.toFloat(ps.getPropertyValue("CharHeight"));
        c.height = Math.round(pt * 100);
        int color = AnyConverter.toInt(ps.getPropertyValue("CharColor"));
        c.textColor = color == -1 ? 0 : color & 0xFFFFFF;
        float weight = AnyConverter.toFloat(ps.getPropertyValue("CharWeight"));
        c.bold = weight >= FontWeight.SEMIBOLD;
        Object posture = ps.getPropertyValue("CharPosture");
        c.italic = posture == FontSlant.ITALIC || posture == FontSlant.OBLIQUE;
        short ul = AnyConverter.toShort(ps.getPropertyValue("CharUnderline"));
        c.underline = ul == FontUnderline.NONE || ul == FontUnderline.DONTKNOW ? "NONE" : "BOTTOM";
        short so = AnyConverter.toShort(ps.getPropertyValue("CharStrikeout"));
        c.strikeout = so != FontStrikeout.NONE && so != FontStrikeout.DONTKNOW;

        String latin = AnyConverter.toString(ps.getPropertyValue("CharFontName"));
        String asian = AnyConverter.toString(ps.getPropertyValue("CharFontNameAsian"));
        int[] ref = new int[WDocument.LANGS.length];
        for (int l = 0; l < WDocument.LANGS.length; l++) {
            // 한글/한자/일본어는 동아시아 글꼴, 나머지는 서양 글꼴
            String face = (l == 0 || l == 2 || l == 3) ? asian : latin;
            if (face == null || face.isEmpty()) face = latin;
            ref[l] = addFont(l, face);
        }
        c.fontRef = ref;
        return c;
    }

    private int addFont(int lang, String face) {
        int idx = doc.fonts[lang].indexOf(face);
        if (idx < 0) {
            doc.fonts[lang].add(face);
            idx = doc.fonts[lang].size() - 1;
        }
        return idx;
    }

    private WParaPr paraPr(XPropertySet pp) throws com.sun.star.uno.Exception {
        WParaPr p = new WParaPr();
        int adjust = AnyConverter.toInt(pp.getPropertyValue("ParaAdjust"));
        // 가져오기는 양쪽 정렬을 왼쪽 정렬로 바꾼다 (ConvPara: 양쪽 정렬이 줄바꿈 때 더 비슷). 왼쪽은 양쪽으로 추정한다
        // (원본의 양쪽:왼쪽 비율은 약 7:3. 원래 왼쪽이던 문단은 양쪽으로 저장된다)
        if (adjust == ParagraphAdjust.LEFT_value) p.align = "JUSTIFY";
        else if (adjust == ParagraphAdjust.RIGHT_value) p.align = "RIGHT";
        else if (adjust == ParagraphAdjust.CENTER_value) p.align = "CENTER";
        else if (adjust == ParagraphAdjust.STRETCH_value) p.align = "DISTRIBUTE";
        else p.align = "JUSTIFY";

        int left = AnyConverter.toInt(pp.getPropertyValue("ParaLeftMargin"));
        int right = AnyConverter.toInt(pp.getPropertyValue("ParaRightMargin"));
        int first = AnyConverter.toInt(pp.getPropertyValue("ParaFirstLineIndent"));
        int top = AnyConverter.toInt(pp.getPropertyValue("ParaTopMargin"));
        int bottom = AnyConverter.toInt(pp.getPropertyValue("ParaBottomMargin"));
        // 문단 여백 단위는 1/200 pt (HWPUNIT 의 절반). 가져오기(ConvPara)는 값/2 를 쓰므로 2 배로 되돌린다.
        // 내어쓰기(음수)는 가져오기가 왼쪽 여백에 더해 두었으므로 빼서 되돌린다
        p.indent = 2 * hwp(first);
        p.left = 2 * hwp(left) + (p.indent < 0 ? p.indent : 0);
        p.right = 2 * hwp(right);
        p.prev = 2 * hwp(top);
        p.next = 2 * hwp(bottom);

        LineSpacing ls = (LineSpacing) AnyConverter.toObject(LineSpacing.class, pp.getPropertyValue("ParaLineSpacing"));
        switch (ls.Mode) {
        // 가져오기(ConvPara.setParagraphProperties) 의 역변환
        case LineSpacingMode.FIX:
            p.lineSpacingType = "FIXED";
            p.lineSpacing = (int) Math.round(ls.Height / 0.352778 * 2);
            break;
        case LineSpacingMode.MINIMUM:
            if (singleParaCell && Math.abs(ls.Height - 100) <= 2) {
                // 가져오기는 문단 하나뿐인 셀의 줄 간격을 "최소 100" 으로 바꾼다 (ConvTable). 원래 값은 남지 않아
                // 한글 기본값 PERCENT 160 으로 추정한다
                p.lineSpacingType = "PERCENT";
                p.lineSpacing = 160;
            } else {
                p.lineSpacingType = "AT_LEAST";
                p.lineSpacing = ls.Height;
            }
            break;
        case LineSpacingMode.LEADING:
            p.lineSpacingType = "BETWEEN_LINES";
            p.lineSpacing = ls.Height;
            break;
        default:
            // 가져오기는 글꼴별 보정값을 곱했다 (ConvPage.adjustFontIfNotExists). 같은 값으로 나눈다
            p.lineSpacingType = "PERCENT";
            p.lineSpacing = (int) Math.round(ls.Height / lineSpaceAlpha(pp));
            break;
        }
        return p;
    }

    /**
     * 글꼴별 줄 간격 보정값. ConvPage.adjustFontIfNotExists 와 같은 규칙:
     * 크기 100 글꼴의 ascent+descent 가 117 미만 0.870, 130 미만 0.857, 133 미만 0.764, 그 이상 0.752.
     * 가져오기는 문단 첫 글자의 글자 모양 보정값을 쓰므로, 문단의 동아시아 글꼴(없으면 서양 글꼴)로 구한다.
     */
    private double lineSpaceAlpha(XPropertySet pp) {
        String face = null;
        try {
            face = AnyConverter.toString(pp.getPropertyValue("CharFontNameAsian"));
            if (face == null || face.isEmpty()) face = AnyConverter.toString(pp.getPropertyValue("CharFontName"));
        } catch (com.sun.star.uno.Exception e) {
            face = null;
        }
        if (face == null || device == null) return 0.870;
        Double cached = alphaCache.get(face);
        if (cached != null) return cached;
        com.sun.star.awt.FontDescriptor fd = new com.sun.star.awt.FontDescriptor();
        fd.Name = face;
        fd.Height = 100;
        com.sun.star.awt.SimpleFontMetric m = device.getFont(fd).getFontMetric();
        double ascDes = m.Ascent + m.Descent;
        double alpha = 0.870;
        if (ascDes >= 117 && ascDes < 130) alpha = 0.857;
        else if (ascDes >= 130 && ascDes < 133) alpha = 0.764;
        else if (ascDes >= 133) alpha = 0.752;
        alphaCache.put(face, alpha);
        return alpha;
    }

    /** 본문 첫 문단의 쪽 스타일로 용지 크기/여백을 정한다 */
    private void page(XTextDocument textDoc) {
        try {
            XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, textDoc.getText());
            XEnumeration en = ea.createEnumeration();
            String pageStyle = "Standard";
            if (en.hasMoreElements()) {
                XPropertySet first = UnoRuntime.queryInterface(XPropertySet.class, en.nextElement());
                if (first != null && first.getPropertySetInfo().hasPropertyByName("PageStyleName")) {
                    pageStyle = AnyConverter.toString(first.getPropertyValue("PageStyleName"));
                }
            }
            XStyleFamiliesSupplier sfs = UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, textDoc);
            XNameAccess families = sfs.getStyleFamilies();
            XNameAccess pages = UnoRuntime.queryInterface(XNameAccess.class, families.getByName("PageStyles"));
            if (!pages.hasByName(pageStyle)) return;
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, pages.getByName(pageStyle));

            int w = AnyConverter.toInt(ps.getPropertyValue("Width"));
            int h = AnyConverter.toInt(ps.getPropertyValue("Height"));
            boolean landscape = AnyConverter.toBoolean(ps.getPropertyValue("IsLandscape"));
            // HWP 는 용지 크기를 세로 방향 기준으로 두고 방향을 따로 쓴다 (가져오기의 역)
            section.landscape = landscape;
            section.pageWidth = hwp(landscape ? h : w);
            section.pageHeight = hwp(landscape ? w : h);
            section.marginLeft = hwp(AnyConverter.toInt(ps.getPropertyValue("LeftMargin")));
            section.marginRight = hwp(AnyConverter.toInt(ps.getPropertyValue("RightMargin")));
            section.marginTop = hwp(AnyConverter.toInt(ps.getPropertyValue("TopMargin")));
            section.marginBottom = hwp(AnyConverter.toInt(ps.getPropertyValue("BottomMargin")));
            // 한글 머리말 여백 = LO 머리말 높이 + 머리말과 본문 간격 (가져오기의 역). 머리말이 없으면 0
            if (AnyConverter.toBoolean(ps.getPropertyValue("HeaderIsOn"))) {
                section.marginHeader = hwp(AnyConverter.toInt(ps.getPropertyValue("HeaderHeight"))
                        + AnyConverter.toInt(ps.getPropertyValue("HeaderBodyDistance")));
            } else {
                section.marginHeader = 0;
            }
            if (AnyConverter.toBoolean(ps.getPropertyValue("FooterIsOn"))) {
                section.marginFooter = hwp(AnyConverter.toInt(ps.getPropertyValue("FooterHeight"))
                        + AnyConverter.toInt(ps.getPropertyValue("FooterBodyDistance")));
            } else {
                section.marginFooter = 0;
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }
}
