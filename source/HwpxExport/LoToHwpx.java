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
        c.text(textDoc.getText(), c.section.paragraphs, c.bodyWidthMm100);
        // 가져오기는 마지막 문단 끝에서 빈 문단을 하나 더 만든다. 저장할 때마다 늘지 않도록 끝의 빈 문단 하나를 뺀다
        java.util.List<WParagraph> body = c.section.paragraphs;
        if (body.size() > 1) {
            WParagraph last = body.get(body.size() - 1);
            if (last.table == null && last.text().isEmpty()) {
                body.remove(body.size() - 1);
            }
        }
        return c.doc;
    }

    /** 1/100 mm → HWPUNIT (Transform.translateHwp2Office 의 역) */
    static int hwp(int mm100) {
        // 가져오기는 (int)(hwp*21000/59529) 로 소수점을 버린다. 다시 읽었을 때 같은 값이 되도록
        // 양수는 올림, 음수는 내림으로 고른다 (반올림하면 저장할 때마다 값이 줄어든다)
        double v = mm100 * 59529.0 / 21000.0;
        return (int) (mm100 >= 0 ? Math.ceil(v - 1e-9) : Math.floor(v + 1e-9));
    }

    /** 본문 폭 (1/100 mm). 표 가로 위치 계산에 쓴다 */
    private int bodyWidthMm100 = 16000;

    /** text 의 문단과 표를 target 에 넣는다. avail: 이 텍스트 영역의 폭 (1/100 mm) */
    private void text(XText text, java.util.List<WParagraph> target, int avail) {
        XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, text);
        if (ea == null) return;
        XEnumeration en = ea.createEnumeration();
        int start = target.size();
        boolean afterTable = false;
        try {
            while (en.hasMoreElements()) {
                Object elem = en.nextElement();
                XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, elem);
                if (si == null) continue;
                if (si.supportsService("com.sun.star.text.Paragraph")) {
                    paragraph(elem, target, afterTable);
                    afterTable = false;
                } else if (si.supportsService("com.sun.star.text.TextTable")) {
                    table(elem, target, avail);
                    afterTable = true;
                }
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
        // 표 문단의 문단 모양: 가져오기는 표 문단의 문단 모양을 표 뒤 문단에 먼저 적용한다 (그 문단에 글자가 없으면
        // 그대로 남는다). 표 뒤 문단과 같은 모양으로 써서 다시 읽어도 같게 한다
        for (int i = start; i + 1 < target.size(); i++) {
            WParagraph p = target.get(i);
            if (p.table != null && target.get(i + 1).table == null) {
                p.paraPrId = target.get(i + 1).paraPrId;
            }
        }
    }

    /**
     * 표를 문단 하나(표만 담은 문단)로 쓴다. 가져오기는 표만 있는 문단을 본문 표로 만들고 그 문단 끝을 건너뛴다.
     * 격자: 행마다 열 구분선(TableColumnSeparators)으로 칸이 나뉘고, 칸 이름의 알파벳은 그 행의 칸 순서다.
     *       세로 병합은 위 칸의 RowSpan 이고 덮인 칸은 셀 이름 목록에서 빠진다.
     *       모든 행의 구분선 위치를 합쳐 격자 열을 만들고, 칸이 걸친 격자 열 수를 colSpan 으로 한다.
     */
    private void table(Object tableObj, java.util.List<WParagraph> target, int avail) throws com.sun.star.uno.Exception {
        com.sun.star.text.XTextTable xt = UnoRuntime.queryInterface(com.sun.star.text.XTextTable.class, tableObj);
        XPropertySet tp = UnoRuntime.queryInterface(XPropertySet.class, tableObj);
        com.sun.star.table.XCellRange range = UnoRuntime.queryInterface(com.sun.star.table.XCellRange.class, tableObj);
        if (xt == null || tp == null || range == null) return;
        java.util.Set<String> visible = new java.util.HashSet<String>(java.util.Arrays.asList(xt.getCellNames()));
        int relSum = AnyConverter.toShort(tp.getPropertyValue("TableColumnRelativeSum"));
        int widthMm = AnyConverter.toInt(tp.getPropertyValue("Width"));
        com.sun.star.table.XTableRows rows = xt.getRows();
        int nRows = rows.getCount();

        // 행별 칸 경계 (상대 위치)
        int[][] bounds = new int[nRows][];
        java.util.TreeSet<Integer> grid = new java.util.TreeSet<Integer>();
        grid.add(0);
        grid.add(relSum);
        int[] rowHeight = new int[nRows];       // HWPUNIT, 0 = 자동 높이
        boolean rowNoSplit = false;
        for (int r = 0; r < nRows; r++) {
            XPropertySet rp = UnoRuntime.queryInterface(XPropertySet.class, rows.getByIndex(r));
            Object sepObj = rp.getPropertyValue("TableColumnSeparators");
            com.sun.star.text.TableColumnSeparator[] seps = sepObj instanceof com.sun.star.text.TableColumnSeparator[]
                    ? (com.sun.star.text.TableColumnSeparator[]) sepObj : new com.sun.star.text.TableColumnSeparator[0];
            int[] b = new int[seps.length + 2];
            b[0] = 0;
            for (int k = 0; k < seps.length; k++) b[k + 1] = seps[k].Position;
            b[b.length - 1] = relSum;
            bounds[r] = b;
            for (int x : b) grid.add(x);
            int h = AnyConverter.toInt(rp.getPropertyValue("Height"));
            short sizeType = AnyConverter.toShort(rp.getPropertyValue("SizeType"));
            rowHeight[r] = (sizeType == com.sun.star.text.SizeType.VARIABLE || h <= 0) ? 0 : hwp(h);
            if (!AnyConverter.toBoolean(rp.getPropertyValue("IsSplitAllowed"))) rowNoSplit = true;
        }
        Integer[] gx = grid.toArray(new Integer[0]);
        int nCols = gx.length - 1;
        int[] colWidth = new int[nCols];
        for (int j = 0; j < nCols; j++) {
            colWidth[j] = hwp((int) Math.round((gx[j + 1] - gx[j]) * (double) widthMm / relSum));
        }
        java.util.Map<Integer, Integer> gridIndex = new java.util.HashMap<Integer, Integer>();
        for (int j = 0; j < gx.length; j++) gridIndex.put(gx[j], j);
        boolean anyAutoRow = false;
        for (int h : rowHeight) if (h == 0) anyAutoRow = true;

        HwpxWriter.WTable t = new HwpxWriter.WTable();
        t.rowCnt = nRows;
        t.colCnt = nCols;
        for (int w : colWidth) t.width += w;
        boolean repeat = AnyConverter.toBoolean(tp.getPropertyValue("RepeatHeadline"));
        int headerRows = repeat ? AnyConverter.toInt(tp.getPropertyValue("HeaderRowCount")) : 0;
        t.repeatHeader = repeat && headerRows > 0;
        boolean split = AnyConverter.toBoolean(tp.getPropertyValue("Split"));
        t.pageBreak = !split ? "NONE" : (rowNoSplit ? "TABLE" : "CELL");
        t.horzOffset = hwp(Math.max(0, tableLeft(tp, widthMm, avail)));
        int top = AnyConverter.toInt(tp.getPropertyValue("TopMargin"));
        t.vertOffset = top > 0 ? hwp(top) : 0;

        for (int r = 0; r < nRows; r++) {
            int[] b = bounds[r];
            for (int i = 0; i + 1 < b.length; i++) {
                Object cellObj;
                try {
                    cellObj = range.getCellByPosition(i, r);
                } catch (com.sun.star.lang.IndexOutOfBoundsException e) {
                    continue;
                }
                XPropertySet cp = UnoRuntime.queryInterface(XPropertySet.class, cellObj);
                String name = AnyConverter.toString(cp.getPropertyValue("CellName"));
                int rowSpan = cp.getPropertySetInfo().hasPropertyByName("RowSpan") ? AnyConverter.toInt(cp.getPropertyValue("RowSpan")) : 1;
                if (!visible.contains(name) || rowSpan < 1) continue;      // 세로 병합으로 덮인 칸
                HwpxWriter.WTable.WCell c = new HwpxWriter.WTable.WCell();
                c.row = r;
                c.rowSpan = Math.min(rowSpan, nRows - r);
                c.col = gridIndex.get(b[i]);
                c.colSpan = gridIndex.get(b[i + 1]) - c.col;
                for (int j = c.col; j < c.col + c.colSpan; j++) c.width += colWidth[j];
                for (int k = r; k < r + c.rowSpan; k++) c.height += anyAutoRow ? 0 : rowHeight[k];
                c.header = r < headerRows;
                c.borderFillId = doc.addBorderFill(cellBorderFill(cp));
                short vo = AnyConverter.toShort(cp.getPropertyValue("VertOrient"));
                c.vertAlign = vo == com.sun.star.text.VertOrientation.CENTER ? "CENTER"
                            : vo == com.sun.star.text.VertOrientation.BOTTOM ? "BOTTOM" : "TOP";
                XText cellText = UnoRuntime.queryInterface(XText.class, cellObj);
                if (cellText != null) {
                    boolean saved = singleParaCell;
                    singleParaCell = countParagraphs(cellText) == 1;
                    int cellMm = (int) Math.round((b[i + 1] - b[i]) * (double) widthMm / relSum);
                    text(cellText, c.paragraphs, cellMm);
                    singleParaCell = saved;
                }
                t.cells.add(c);
            }
        }
        // 행 높이 합 (자동 높이 행이 있으면 0: 가져오기가 행 높이를 설정하지 않도록)
        if (!anyAutoRow) {
            for (int h : rowHeight) t.height += h;
        }

        WParagraph p = new WParagraph();
        p.table = t;
        target.add(p);
    }

    /** 표의 왼쪽 위치 (1/100 mm). 가져오기는 다시 읽을 때 왼쪽 여백 + 폭(LEFT_AND_WIDTH) 으로 놓는다 */
    private static int tableLeft(XPropertySet tp, int width, int avail) throws com.sun.star.uno.Exception {
        short hori = AnyConverter.toShort(tp.getPropertyValue("HoriOrient"));
        int left = AnyConverter.toInt(tp.getPropertyValue("LeftMargin"));
        int right = AnyConverter.toInt(tp.getPropertyValue("RightMargin"));
        switch (hori) {
        case com.sun.star.text.HoriOrientation.CENTER:
            return (avail - width) / 2;
        case com.sun.star.text.HoriOrientation.RIGHT:
            return avail - width - right;
        default:
            return left;
        }
    }

    /** 셀 테두리/배경. 가져오기(Transform.toBorderLine)는 선 굵기와 색만 쓰므로 선은 실선으로 되돌린다 */
    private static HwpxWriter.WBorderFill cellBorderFill(XPropertySet cp) throws com.sun.star.uno.Exception {
        HwpxWriter.WBorderFill bf = new HwpxWriter.WBorderFill();
        String[] names = { "LeftBorder", "RightBorder", "TopBorder", "BottomBorder" };
        for (int s = 0; s < 4; s++) {
            Object o = cp.getPropertyValue(names[s]);
            int outer = 0, inner = 0, color = 0;
            if (o instanceof com.sun.star.table.BorderLine2) {
                com.sun.star.table.BorderLine2 b = (com.sun.star.table.BorderLine2) o;
                outer = b.OuterLineWidth; inner = b.InnerLineWidth; color = b.Color;
            } else if (o instanceof com.sun.star.table.BorderLine) {
                com.sun.star.table.BorderLine b = (com.sun.star.table.BorderLine) o;
                outer = b.OuterLineWidth; inner = b.InnerLineWidth; color = b.Color;
            }
            int w = outer + inner;
            if (w <= 0) {
                bf.type[s] = "NONE";
            } else {
                bf.type[s] = "SOLID";
                bf.width[s] = HwpxWriter.WBorderFill.nearestWidth(w / 100.0);
                bf.color[s] = color & 0xFFFFFF;
            }
        }
        boolean transparent = AnyConverter.toBoolean(cp.getPropertyValue("BackTransparent"));
        int back = AnyConverter.toInt(cp.getPropertyValue("BackColor"));
        if (!transparent && back != -1) {
            bf.fillColor = back & 0xFFFFFF;
        }
        return bf;
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

    private void paragraph(Object paraObj, java.util.List<WParagraph> target, boolean afterTable) throws com.sun.star.uno.Exception {
        XPropertySet pp = UnoRuntime.queryInterface(XPropertySet.class, paraObj);
        WParagraph p = new WParagraph();

        Object bt = pp.getPropertyValue("BreakType");
        if (bt instanceof BreakType && (bt == BreakType.PAGE_BEFORE || bt == BreakType.PAGE_BOTH)) {
            p.pageBreak = !target.isEmpty();
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
        // 문단 끝 글자 모양 = 문단의 글자 속성. 가져오기는 문단 끝(beforeParaBreak)에서 이 모양으로 문단 속성을
        // 마지막으로 설정한다 (줄 간격 보정값 포함). HWPX 파서는 마지막 run 의 모양을 문단 끝 모양으로 쓰므로,
        // 마지막 run 과 다르면 빈 run 을 덧붙인다
        int endCp = doc.addCharPr(charPr(pp));
        if (p.runs.isEmpty() || p.runs.get(p.runs.size() - 1).charPrId != endCp) {
            p.runs.add(new WParagraph.WRun(endCp, ""));
        }
        p.paraPrId = doc.addParaPr(paraPr(pp, fontForAlpha(pp), afterTable));
        target.add(p);
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

    private static String fontForAlpha(XPropertySet ps) {
        try {
            String face = AnyConverter.toString(ps.getPropertyValue("CharFontNameAsian"));
            if (face == null || face.isEmpty()) face = AnyConverter.toString(ps.getPropertyValue("CharFontName"));
            return face;
        } catch (com.sun.star.uno.Exception e) {
            return null;
        }
    }

    private WParaPr paraPr(XPropertySet pp, String alphaFont, boolean afterTable) throws com.sun.star.uno.Exception {
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
        if (afterTable) {
            // 가져오기는 표 바로 다음 문단의 위 간격에 max(0, 줄 간격 높이-100)*10 을 더한다 (ConvPara, firstAfterTable)
            LineSpacing lsTop = (LineSpacing) AnyConverter.toObject(LineSpacing.class, pp.getPropertyValue("ParaLineSpacing"));
            top = Math.max(0, top - Math.max(0, lsTop.Height - 100) * 10);
        }
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
            p.lineSpacing = 2 * (int) Math.ceil(ls.Height / 0.352778 - 1e-9);    // 가져오기: (short)(v/2*0.352778), 버림의 역
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
            p.lineSpacing = (int) Math.ceil(ls.Height / lineSpaceAlpha(alphaFont) - 1e-9);    // 가져오기: (short)(v*보정값), 버림의 역
            break;
        }
        return p;
    }

    /**
     * 글꼴별 줄 간격 보정값. ConvPage.adjustFontIfNotExists 와 같은 규칙:
     * 크기 100 글꼴의 ascent+descent 가 117 미만 0.870, 130 미만 0.857, 133 미만 0.764, 그 이상 0.752.
     * 가져오기는 문단 끝(beforeParaBreak)에서 문단 끝 글자 모양으로 문단 속성을 마지막으로 설정하므로, 그 글꼴로 구한다.
     */
    private double lineSpaceAlpha(String face) {
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
            bodyWidthMm100 = w - AnyConverter.toInt(ps.getPropertyValue("LeftMargin")) - AnyConverter.toInt(ps.getPropertyValue("RightMargin"));
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
