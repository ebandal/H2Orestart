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
import HwpxWriter.WShape;

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
        c.ctx = context;
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
        c.textDoc = textDoc;
        c.page(textDoc);
        c.text(textDoc.getText(), c.section.paragraphs, c.bodyWidthMm100);
        c.finishParaPrs();
        // 가져오기는 마지막 문단 끝에서 빈 문단을 하나 더 만든다. 저장할 때마다 늘지 않도록 끝의 빈 문단 하나를 뺀다
        java.util.List<WParagraph> body = c.section.paragraphs;
        if (body.size() > 1) {
            WParagraph last = body.get(body.size() - 1);
            if (last.table == null && !last.hasShape() && last.text().isEmpty()) {
                body.remove(body.size() - 1);
                WParagraph prev = body.get(body.size() - 1);
                if (prev.table != null && c.lastBodyPara != null) {
                    // 본문이 표로 끝난다. 가져오기는 표를 넣으며 표 문단의 문단 모양을 고정 보정값(ConvPara.PARA_SPACING)으로
                    // 표 뒤 문단에 적용하고, 그 문단은 따로 설정하지 않는다. 같은 보정값으로 되돌린다
                    try {
                        prev.paraPrId = c.doc.addParaPr(c.paraPr(c.lastBodyPara, TABLE_PARA_ALPHA, false));
                    } catch (com.sun.star.uno.Exception e) {
                        log.log(Level.WARNING, e.toString(), e);
                    }
                }
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

    /** 가져오기가 표 문단의 문단 모양을 적용할 때 쓰는 줄 간격 보정값 (ConvTable.insertTable, ConvPara.PARA_SPACING) */
    private static final double TABLE_PARA_ALPHA = 0.85;

    /**
     * 줄 간격(PERCENT) 보정값을 나중에 정하는 문단 모양. 가져오기는 글자 모양의 보정값을 그 글자 모양이 쓰는 글꼴 중
     * 글꼴 목록(언어 순서)에서 가장 뒤에 있는 글꼴로 정하므로(HwpDocInfo.setFontNameLineSpaceAlpha), 글꼴 목록이
     * 다 만들어진 뒤에 계산한다.
     */
    private static class PendingPr {
        final WParagraph para;
        final WParaPr pr;
        final int rawHeight;        // LibreOffice 비례 줄 간격
        int alphaCp;                // 보정값을 정하는 글자 모양 (가져오기가 문단 속성을 마지막으로 설정할 때의 글자 모양)
        double fixedAlpha;          // 0 보다 크면 글자 모양 대신 이 보정값 (가져오기가 개체를 넣으며 마지막으로 설정)
        PendingPr(WParagraph para, WParaPr pr, int alphaCp) {
            this.para = para;
            this.pr = pr;
            this.rawHeight = pr.lineSpacing;
            this.alphaCp = alphaCp;
        }
    }
    private final java.util.Map<WParagraph, PendingPr> pending = new java.util.LinkedHashMap<WParagraph, PendingPr>();
    /** 표 문단 → 문단 모양을 따라갈 표 뒤 문단 */
    private final java.util.List<WParagraph[]> paraPrCopies = new java.util.ArrayList<WParagraph[]>();
    /** paraPr() 가 줄 간격 보정을 미뤘는지 */
    private boolean deferredAlpha;

    /** 미뤄 둔 문단 모양을 확정한다 */
    private void finishParaPrs() {
        for (PendingPr pp : pending.values()) {
            double alpha = pp.fixedAlpha > 0 ? pp.fixedAlpha : importAlpha(pp.alphaCp);
            pp.pr.lineSpacing = (int) Math.ceil(pp.rawHeight / alpha - 1e-9);    // 가져오기: (short)(v*보정값), 버림의 역
            pp.para.paraPrId = doc.addParaPr(pp.pr);
        }
        pending.clear();
        for (WParagraph[] cp : paraPrCopies) {
            cp[0].paraPrId = cp[1].paraPrId;
        }
        paraPrCopies.clear();
    }

    /** 다시 읽을 때 이 글자 모양에 적용될 줄 간격 보정값 */
    private double importAlpha(int charPrId) {
        WCharPr c = doc.charPrList.get(charPrId);
        String best = null;
        int bestPos = -1;
        for (int l = 0; l < WDocument.LANGS.length; l++) {
            String face = doc.fonts[l].get(c.fontRef[l]);
            int pos = lastFontPosition(face);
            if (pos > bestPos) {
                bestPos = pos;
                best = face;
            }
        }
        return lineSpaceAlpha(best);
    }

    /** 글꼴 목록 전체(언어 순서로 이어 붙인 것)에서 이 이름이 마지막으로 나오는 위치 */
    private int lastFontPosition(String face) {
        int offset = 0, last = -1;
        for (int l = 0; l < WDocument.LANGS.length; l++) {
            int i = doc.fonts[l].lastIndexOf(face);
            if (i >= 0) last = offset + i;
            offset += doc.fonts[l].size();
        }
        return last;
    }

    /** 본문의 마지막 문단 (문단 속성) */
    private XPropertySet lastBodyPara;

    private com.sun.star.uno.XComponentContext ctx;
    private XTextDocument textDoc;
    /** LibreOffice 문단 스타일 이름 → 스타일 ID */
    private final java.util.Map<String, Integer> styleIds = new java.util.HashMap<String, Integer>();

    /**
     * 문단 스타일 ID. 가져오기는 HWP 스타일마다 "HWP  {id} {이름}" 문단 스타일을 만들고 스타일의 글자 모양을 넣는다.
     * 문단에 직접 글자 속성이 없으면(개체만 있는 문단 등) 스타일의 글자 속성이 보이므로, 스타일의 글자 모양도 쓴다.
     */
    private int styleId(String loName) {
        if (loName == null || loName.isEmpty()) return 0;
        Integer id = styleIds.get(loName);
        if (id != null) return id;
        HwpxWriter.WStyle st = new HwpxWriter.WStyle();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^HWP\\s+\\d+\\s(.*)$").matcher(loName);
        st.name = m.matches() ? m.group(1) : loName;
        try {
            XNameAccess fams = UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, textDoc).getStyleFamilies();
            XNameAccess paraStyles = UnoRuntime.queryInterface(XNameAccess.class, fams.getByName("ParagraphStyles"));
            if (paraStyles.hasByName(loName)) {
                XPropertySet sp = UnoRuntime.queryInterface(XPropertySet.class, paraStyles.getByName(loName));
                st.charPrId = doc.addCharPr(charPr(sp));
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.WARNING, e.toString(), e);
        }
        if (styleIds.isEmpty()) {
            // 처음 쓰는 스타일을 0 번(기본 스타일) 자리에 둔다
            st.engName = doc.styleList.get(0).engName;
            doc.styleList.set(0, st);
            id = 0;
        } else {
            doc.styleList.add(st);
            id = doc.styleList.size() - 1;
        }
        st.nextStyleId = id;
        styleIds.put(loName, id);
        return id;
    }
    /** 지금 쓰는 텍스트가 표 셀인가 (셀의 마지막 문단은 가져오기가 문단 끝을 넣지 않는다) */
    private boolean inCellText;
    /** 그리기 개체 순서 (zOrder, id) */
    private int shapeSeq;

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
                    // 가져오기의 표 다음 문단 간격 보정은 본문/머리말/글상자에서 한다 (표 셀은 하지 않는다: ConvTable)
                    paragraph(elem, target, afterTable && !inCellText);
                    if (target == section.paragraphs) {
                        lastBodyPara = UnoRuntime.queryInterface(XPropertySet.class, elem);
                    }
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
        // 셀의 마지막 문단: 가져오기는 문단 끝을 넣지 않으므로 마지막 글자 run 의 모양으로 문단 속성을 마지막 설정한다
        if (inCellText && target.size() > start) {
            WParagraph last = target.get(target.size() - 1);
            PendingPr pp = pending.get(last);
            if (pp != null) {
                for (int k = last.runs.size() - 1; k >= 0; k--) {
                    WParagraph.WRun r = last.runs.get(k);
                    if (r.shape != null && !last.text().isEmpty()) {
                        // 글자 뒤에 개체가 마지막이면 가져오기는 개체를 넣으며 고정 보정값(PARA_SPACING)으로 문단 속성을
                        // 마지막 설정한다. 글자가 없으면 문단 끝(ConvTable)에서 문단 끝 글자 모양으로 설정한다
                        pp.fixedAlpha = TABLE_PARA_ALPHA;
                        break;
                    }
                    if (r.text != null && !r.text.isEmpty()) {
                        pp.alphaCp = r.charPrId;
                        break;
                    }
                }
            }
        }
        for (int i = start; i + 1 < target.size(); i++) {
            WParagraph p = target.get(i);
            if (p.table != null && target.get(i + 1).table == null) {
                paraPrCopies.add(new WParagraph[] { p, target.get(i + 1) });
                // 문단 끝 글자 모양도 같게: 문서 끝 표는 가져오기가 표 문단 모양으로 끝 문단을 만들고, 줄 간격 보정값을
                // 표 문단의 글자 모양(글꼴)으로 계산한다
                p.runs.clear();
                p.runs.add(new WParagraph.WRun(target.get(i + 1).lastCharPrId(), ""));
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
                    boolean savedCell = inCellText;
                    inCellText = true;
                    text(cellText, c.paragraphs, cellMm);
                    inCellText = savedCell;
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
        p.styleId = styleId(AnyConverter.toString(pp.getPropertyValue("ParaStyleName")));

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
            if ("TextField".equals(type)) {
                textField(ps, portion, p);
                continue;
            }
            if ("Frame".equals(type)) {
                // 글자처럼 놓인 개체 (그림, 글상자)
                com.sun.star.container.XContentEnumerationAccess cea =
                        UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, portion);
                if (cea != null) {
                    XEnumeration ce = cea.createContentEnumeration("com.sun.star.text.TextContent");
                    while (ce.hasMoreElements()) {
                        WShape sh = shape(ce.nextElement());
                        if (sh != null) {
                            p.runs.add(new WParagraph.WRun(doc.addCharPr(charPr(ps)), sh));
                        }
                    }
                }
                continue;
            }
            if (!"Text".equals(type)) continue;
            String s = UnoRuntime.queryInterface(XTextRange.class, portion).getString();
            if (s.isEmpty()) continue;
            int cp = doc.addCharPr(charPr(ps));

            // 같은 글자 모양이 이어지면 run 을 합친다 (한글도 같은 모양은 한 run)
            if (!p.runs.isEmpty() && p.runs.get(p.runs.size() - 1).charPrId == cp && p.runs.get(p.runs.size() - 1).shape == null
                    && p.runs.get(p.runs.size() - 1).autoNum == null) {
                p.runs.get(p.runs.size() - 1).text += s;
            } else {
                p.runs.add(new WParagraph.WRun(cp, s));
            }
        }
        floatingShapes(paraObj, p);
        // 문단 끝 글자 모양 = 문단의 글자 속성. 가져오기는 문단 끝(beforeParaBreak)에서 이 모양으로 문단 속성을
        // 마지막으로 설정한다 (줄 간격 보정값 포함). HWPX 파서는 마지막 run 의 모양을 문단 끝 모양으로 쓰므로,
        // 마지막 run 과 다르면 빈 run 을 덧붙인다
        int endCp = doc.addCharPr(charPr(pp));
        if (p.text().isEmpty()) {
            // 글자 없이 개체만 있는 문단: 개체 run 의 글자 모양은 가져오기에서 쓰이지 않고(빈칸 등도 없음) 문단 끝 모양만
            // 문단에 적용된다. 개체 run 도 문단 끝 모양으로 써서 다시 읽어도 문단 글자 모양(줄 간격 보정 글꼴)이 같게 한다
            for (WParagraph.WRun r : p.runs) {
                if (r.shape != null) r.charPrId = endCp;
            }
        }
        if (p.runs.isEmpty() || p.runs.get(p.runs.size() - 1).charPrId != endCp) {
            p.runs.add(new WParagraph.WRun(endCp, ""));
        }
        WParaPr wpp = paraPr(pp, 0, afterTable);
        if (deferredAlpha) {
            pending.put(p, new PendingPr(p, wpp, endCp));
        } else {
            p.paraPrId = doc.addParaPr(wpp);
        }
        target.add(p);
    }

    /**
     * 문단에 붙은(글자처럼 취급하지 않는) 개체를 문단 앞에 넣는다. 가져오기는 이런 개체를 넣은 뒤 빈칸 하나를
     * 덧붙인다 (ConvGraphics, LibreOffice 7.2 이상). 개체를 문단 맨 앞에 쓰므로 다시 읽으면 빈칸이 문단 앞에 생긴다.
     * 개체마다 문단 앞의 빈칸 하나를 뺀다.
     */
    private void floatingShapes(Object paraObj, WParagraph p) throws com.sun.star.uno.Exception {
        com.sun.star.container.XContentEnumerationAccess cea =
                UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, paraObj);
        if (cea == null) return;
        XEnumeration ce = cea.createContentEnumeration("com.sun.star.text.TextContent");
        java.util.List<WShape> shapes = new java.util.ArrayList<WShape>();
        while (ce.hasMoreElements()) {
            WShape sh = shape(ce.nextElement());
            if (sh != null && !sh.treatAsChar) shapes.add(sh);
        }
        if (shapes.isEmpty()) return;
        int cp = p.runs.isEmpty() ? doc.addCharPr(charPr(UnoRuntime.queryInterface(XPropertySet.class, paraObj)))
                                  : p.runs.get(0).charPrId;
        for (int k = 0; k < shapes.size(); k++) {
            // 문단 앞의 빈칸 하나 빼기
            for (WParagraph.WRun r : p.runs) {
                if (r.shape != null || r.text == null || r.text.isEmpty()) continue;
                if (r.text.charAt(0) == ' ') r.text = r.text.substring(1);
                break;
            }
        }
        for (int k = shapes.size() - 1; k >= 0; k--) {
            p.runs.add(0, new WParagraph.WRun(cp, shapes.get(k)));
        }
        // 빈 run 정리 (개체 run 은 남긴다)
        for (java.util.Iterator<WParagraph.WRun> it = p.runs.iterator(); it.hasNext();) {
            WParagraph.WRun r = it.next();
            if (r.shape == null && (r.text == null || r.text.isEmpty())) it.remove();
        }
    }

    /** 그림(TextGraphicObject) 또는 글상자(TextFrame). 다른 개체는 null */
    private WShape shape(Object obj) throws com.sun.star.uno.Exception {
        XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, obj);
        XPropertySet op = UnoRuntime.queryInterface(XPropertySet.class, obj);
        if (si == null || op == null) return null;
        WShape s;
        if (si.supportsService("com.sun.star.text.TextGraphicObject")) {
            s = new WShape(WShape.Kind.PICTURE);
            if (!picture(op, s)) return null;
        } else if (si.supportsService("com.sun.star.text.TextFrame")) {
            s = new WShape(WShape.Kind.TEXTBOX);
        } else {
            return null;
        }
        s.zOrder = shapeSeq++;
        int w = AnyConverter.toInt(op.getPropertyValue("Width"));
        int h = AnyConverter.toInt(op.getPropertyValue("Height"));
        s.width = hwp(w);
        s.height = hwp(h);
        Object at = op.getPropertyValue("AnchorType");
        s.treatAsChar = at == com.sun.star.text.TextContentAnchorType.AS_CHARACTER;
        if (!s.treatAsChar) {
            position(op, s);
            wrap(op, s);
        }
        if (s.kind == WShape.Kind.TEXTBOX) {
            textBox(obj, op, s, w);
        }
        return s;
    }

    /** 위치: 가져오기(ConvGraphics.setPositionLO)의 역. 기준 위치에서의 거리는 모두 위/왼쪽 기준으로 쓴다 */
    private static void position(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        short vo = AnyConverter.toShort(op.getPropertyValue("VertOrient"));
        short vrel = AnyConverter.toShort(op.getPropertyValue("VertOrientRelation"));
        int vpos = AnyConverter.toInt(op.getPropertyValue("VertOrientPosition"));
        s.vertRelTo = vrel == com.sun.star.text.RelOrientation.PAGE_FRAME ? "PAPER"
                    : vrel == com.sun.star.text.RelOrientation.PAGE_PRINT_AREA ? "PAGE" : "PARA";
        if (vo == com.sun.star.text.VertOrientation.NONE) {
            s.vertAlign = "TOP";
            s.vertOffset = "PARA".equals(s.vertRelTo) ? Math.max(0, hwp(vpos)) : hwp(vpos);
        } else {
            s.vertAlign = vo == com.sun.star.text.VertOrientation.CENTER ? "CENTER"
                        : vo == com.sun.star.text.VertOrientation.BOTTOM ? "BOTTOM" : "TOP";
            s.vertOffset = 0;
        }
        short ho = AnyConverter.toShort(op.getPropertyValue("HoriOrient"));
        short hrel = AnyConverter.toShort(op.getPropertyValue("HoriOrientRelation"));
        int hpos = AnyConverter.toInt(op.getPropertyValue("HoriOrientPosition"));
        s.horzRelTo = hrel == com.sun.star.text.RelOrientation.PAGE_FRAME ? "PAPER"
                    : hrel == com.sun.star.text.RelOrientation.PAGE_PRINT_AREA ? "PAGE" : "PARA";
        if (ho == com.sun.star.text.HoriOrientation.NONE) {
            s.horzAlign = "LEFT";
            s.horzOffset = hwp(hpos);
        } else {
            s.horzAlign = ho == com.sun.star.text.HoriOrientation.CENTER ? "CENTER"
                        : ho == com.sun.star.text.HoriOrientation.RIGHT ? "RIGHT" : "LEFT";
            s.horzOffset = 0;
        }
    }

    /** 본문과의 배치: 가져오기(ConvGraphics.setWrapStyle)의 역 */
    private static void wrap(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        Object tw = op.getPropertyValue("TextWrap");
        boolean opaque = AnyConverter.toBoolean(op.getPropertyValue("Opaque"));
        com.sun.star.text.WrapTextMode m = tw instanceof com.sun.star.text.WrapTextMode
                ? (com.sun.star.text.WrapTextMode) tw : com.sun.star.text.WrapTextMode.NONE;
        switch (m.getValue()) {
        case com.sun.star.text.WrapTextMode.THROUGH_value:
            s.textWrap = opaque ? "IN_FRONT_OF_TEXT" : "BEHIND_TEXT";
            break;
        case com.sun.star.text.WrapTextMode.PARALLEL_value:
            s.textWrap = "SQUARE";
            s.textFlow = "BOTH_SIDES";
            break;
        case com.sun.star.text.WrapTextMode.LEFT_value:
            s.textWrap = "SQUARE";
            s.textFlow = "LEFT_ONLY";
            break;
        case com.sun.star.text.WrapTextMode.RIGHT_value:
            s.textWrap = "SQUARE";
            s.textFlow = "RIGHT_ONLY";
            break;
        case com.sun.star.text.WrapTextMode.DYNAMIC_value:
            // 가져오기는 글상자의 "글 앞으로" 와 그림의 "어울림-큰 쪽" 을 DYNAMIC 으로 둔다
            if (s.kind == WShape.Kind.TEXTBOX) {
                s.textWrap = "IN_FRONT_OF_TEXT";
            } else {
                s.textWrap = "SQUARE";
                s.textFlow = "LARGEST_ONLY";
            }
            break;
        default:
            s.textWrap = "TOP_AND_BOTTOM";
            break;
        }
    }

    /** 그림: 그래픽을 PNG 로 저장해 바이너리 항목으로 넣는다 */
    private boolean picture(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        Object g = op.getPropertyValue("Graphic");
        com.sun.star.graphic.XGraphic graphic = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphic.class, g);
        if (graphic == null || ctx == null) return false;
        Object gp = ctx.getServiceManager().createInstanceWithContext("com.sun.star.graphic.GraphicProvider", ctx);
        com.sun.star.graphic.XGraphicProvider prov = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphicProvider.class, gp);
        // 임시 파일로 저장해 읽는다 (가져오기 ConvGraphics 와 같은 방법. 출력 스트림 어댑터로는 0 바이트가 나왔다)
        byte[] data;
        java.io.File tmp = null;
        try {
            tmp = java.io.File.createTempFile("H2O_EXP_", ".png");
            com.sun.star.beans.PropertyValue[] pv = new com.sun.star.beans.PropertyValue[2];
            pv[0] = new com.sun.star.beans.PropertyValue();
            pv[0].Name = "URL";
            pv[0].Value = tmp.toURI().toURL().toExternalForm();
            pv[1] = new com.sun.star.beans.PropertyValue();
            pv[1].Name = "MimeType";
            pv[1].Value = "image/png";
            prov.storeGraphic(graphic, pv);
            data = java.nio.file.Files.readAllBytes(tmp.toPath());
        } catch (java.io.IOException e) {
            log.log(Level.WARNING, e.toString(), e);
            return false;
        } finally {
            if (tmp != null && !tmp.delete()) tmp.deleteOnExit();
        }
        if (data == null || data.length == 0) return false;
        s.binItemId = doc.addBinItem("png", data);
        // 원본 그림 크기: 픽셀을 96 dpi 로 HWPUNIT 으로 (1 픽셀 = 75)
        XPropertySet gps = UnoRuntime.queryInterface(XPropertySet.class, graphic);
        int pw = 0, ph = 0;
        if (gps != null && gps.getPropertySetInfo().hasPropertyByName("SizePixel")) {
            Object sz = gps.getPropertyValue("SizePixel");
            if (sz instanceof com.sun.star.awt.Size) {
                pw = ((com.sun.star.awt.Size) sz).Width;
                ph = ((com.sun.star.awt.Size) sz).Height;
            }
        }
        s.imgWidth = pw > 0 ? pw * 75 : 7500;
        s.imgHeight = ph > 0 ? ph * 75 : 7500;
        return true;
    }

    /** 글상자: 테두리, 채우기, 글 여백, 세로 정렬, 내용 (가져오기 ConvGraphics.insertTextFrame 의 역) */
    private void textBox(Object obj, XPropertySet op, WShape s, int widthMm) throws com.sun.star.uno.Exception {
        Object tb = op.getPropertyValue("TopBorder");
        if (tb instanceof com.sun.star.table.BorderLine2 && ((com.sun.star.table.BorderLine2) tb).LineWidth > 0) {
            com.sun.star.table.BorderLine2 b = (com.sun.star.table.BorderLine2) tb;
            s.lineColor = b.Color & 0xFFFFFF;
            s.lineWidth = hwp(b.LineWidth);
            switch (b.LineStyle) {
            // HWPX 선 이름은 그려지는 모양과 반대 (DOT = 긴 점선, DASH = 점선). LineStyle2.fromXml 의 역
            case com.sun.star.table.BorderLineStyle.DASHED:       s.lineStyle = "DOT";          break;
            case com.sun.star.table.BorderLineStyle.DOTTED:       s.lineStyle = "DASH";         break;
            case com.sun.star.table.BorderLineStyle.DASH_DOT:     s.lineStyle = "DASH_DOT";     break;
            case com.sun.star.table.BorderLineStyle.DASH_DOT_DOT: s.lineStyle = "DASH_DOT_DOT"; break;
            default:                                              s.lineStyle = "SOLID";        break;
            }
        }
        Object fs = op.getPropertyValue("FillStyle");
        if (fs == com.sun.star.drawing.FillStyle.SOLID) {
            s.fillColor = AnyConverter.toInt(op.getPropertyValue("FillColor")) & 0xFFFFFF;
        }
        // 글 여백: 가져오기는 (여백 - 1mm) 를 테두리 간격으로 쓰고 1mm 이하는 0 으로 둔다
        String[] dist = { "LeftBorderDistance", "RightBorderDistance", "TopBorderDistance", "BottomBorderDistance" };
        for (int k = 0; k < 4; k++) {
            int d = AnyConverter.toInt(op.getPropertyValue(dist[k]));
            s.textMargin[k] = d <= 0 ? 283 : hwp(d + 100);
        }
        Object va = op.getPropertyValue("TextVerticalAdjust");
        s.textVertAlign = va == com.sun.star.drawing.TextVerticalAdjust.CENTER ? "CENTER"
                        : va == com.sun.star.drawing.TextVerticalAdjust.BOTTOM ? "BOTTOM" : "TOP";
        com.sun.star.text.XTextFrame frame = UnoRuntime.queryInterface(com.sun.star.text.XTextFrame.class, obj);
        if (frame != null) {
            boolean savedCell = inCellText, savedSingle = singleParaCell;
            inCellText = false;
            singleParaCell = false;
            text(frame.getText(), s.paragraphs, widthMm);
            inCellText = savedCell;
            singleParaCell = savedSingle;
        }
    }

    /**
     * 머리말/꼬리말: 쪽 스타일의 머리말/꼬리말 텍스트 (가져오기 ConvPage.setHeaderFooter 의 역).
     * 공유(HeaderIsShared)면 양쪽(BOTH), 아니면 홀수쪽(오른쪽 텍스트)과 짝수쪽(왼쪽 텍스트).
     * 쪽 번호만 든 머리말/꼬리말은 쪽 번호 위치(hp:pageNum)로 쓴다 (가져오기 ConvPage.putPageNum 의 역).
     */
    private void headersFooters(XPropertySet ps) throws com.sun.star.uno.Exception {
        for (int k = 0; k < 2; k++) {
            boolean header = k == 0;
            String pre = header ? "Header" : "Footer";
            if (!AnyConverter.toBoolean(ps.getPropertyValue(pre + "IsOn"))) continue;
            if (AnyConverter.toBoolean(ps.getPropertyValue(pre + "IsShared"))) {
                headerFooter(header, "BOTH", ps.getPropertyValue(pre + "Text"));
            } else {
                headerFooter(header, "ODD", ps.getPropertyValue(pre + "TextRight"));
                headerFooter(header, "EVEN", ps.getPropertyValue(pre + "TextLeft"));
            }
        }
    }

    private void headerFooter(boolean header, String type, Object textObj) throws com.sun.star.uno.Exception {
        XText xt = UnoRuntime.queryInterface(XText.class, textObj);
        if (xt == null) return;
        WSection.HeaderFooter hf = new WSection.HeaderFooter();
        hf.header = header;
        hf.applyPageType = type;
        boolean savedCell = inCellText, savedSingle = singleParaCell;
        inCellText = false;
        singleParaCell = false;
        text(xt, hf.paragraphs, bodyWidthMm100);
        inCellText = savedCell;
        singleParaCell = savedSingle;

        boolean empty = true;
        for (WParagraph p : hf.paragraphs) {
            if (!p.text().isEmpty() || p.hasShape() || hasAutoNum(p)) empty = false;
        }
        if (empty) return;      // 가져오기는 빈 머리말/꼬리말도 켜 둔다 (쪽 여백)
        if ("BOTH".equals(type) && section.pageNumPos == null && pageNumOnly(xt, hf, header)) return;
        section.headerFooters.add(hf);
    }

    private static boolean hasAutoNum(WParagraph p) {
        for (WParagraph.WRun r : p.runs) {
            if (r.autoNum != null) return true;
        }
        return false;
    }

    /**
     * 쪽 번호만 있는가 ("1" 또는 "- 1 -"). 그렇다면 쪽 번호 위치를 정한다.
     * 가져오기(putPageNum)는 머리말/꼬리말 전체를 쪽 번호 필드로 바꾸고 양옆에 "줄표 " / " 줄표" 를 넣는다.
     */
    private boolean pageNumOnly(XText xt, WSection.HeaderFooter hf, boolean header) throws com.sun.star.uno.Exception {
        if (hf.paragraphs.size() != 1) return false;
        WParagraph p = hf.paragraphs.get(0);
        StringBuilder before = new StringBuilder(), after = new StringBuilder();
        WParagraph.WRun field = null;
        for (WParagraph.WRun r : p.runs) {
            if (r.shape != null) return false;
            if (r.autoNum != null) {
                if (field != null || !"PAGE".equals(r.autoNum)) return false;
                field = r;
            } else if (r.text != null) {
                (field == null ? before : after).append(r.text);
            }
        }
        if (field == null) return false;
        String b = before.toString(), a = after.toString();
        String side;
        if (b.isEmpty() && a.isEmpty()) {
            side = "";
        } else if (b.length() == 2 && b.charAt(1) == ' ' && a.length() == 2 && a.charAt(0) == ' ' && b.charAt(0) == a.charAt(1)) {
            side = String.valueOf(b.charAt(0));
        } else {
            return false;
        }
        short adjust = ParagraphAdjust.CENTER_value;
        XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, xt);
        XEnumeration en = ea.createEnumeration();
        if (en.hasMoreElements()) {
            XPropertySet pp = UnoRuntime.queryInterface(XPropertySet.class, en.nextElement());
            if (pp != null) adjust = (short) AnyConverter.toInt(pp.getPropertyValue("ParaAdjust"));
        }
        String h = adjust == ParagraphAdjust.LEFT_value ? "LEFT" : adjust == ParagraphAdjust.RIGHT_value ? "RIGHT" : "CENTER";
        section.pageNumPos = (header ? "TOP_" : "BOTTOM_") + h;
        section.pageNumFormat = field.autoNumFormat;
        section.pageNumSideChar = side;
        return true;
    }

    /** 쪽 번호 필드 모양 (LibreOffice NumberingType → HWPX 번호 모양). 가져오기 putPageNum 의 역 */
    private static String numberFormat(short nt) {
        switch (nt) {
        case com.sun.star.style.NumberingType.CIRCLE_NUMBER:              return "CIRCLED_DIGIT";
        case com.sun.star.style.NumberingType.ROMAN_UPPER:                return "ROMAN_CAPITAL";
        case com.sun.star.style.NumberingType.ROMAN_LOWER:                return "ROMAN_SMALL";
        case com.sun.star.style.NumberingType.CHARS_UPPER_LETTER:         return "LATIN_CAPITAL";
        case com.sun.star.style.NumberingType.CHARS_LOWER_LETTER:         return "LATIN_SMALL";
        case com.sun.star.style.NumberingType.HANGUL_SYLLABLE_KO:         return "HANGUL_SYLLABLE";
        case com.sun.star.style.NumberingType.HANGUL_CIRCLED_SYLLABLE_KO: return "CIRCLED_HANGUL_SYLLABLE";
        case com.sun.star.style.NumberingType.HANGUL_JAMO_KO:             return "HANGUL_JAMO";
        case com.sun.star.style.NumberingType.HANGUL_CIRCLED_JAMO_KO:     return "CIRCLED_HANGUL_JAMO";
        default:                                                          return "DIGIT";
        }
    }

    /** 텍스트 필드 부분: 쪽 번호/전체 쪽수는 자동 번호 run, 다른 필드는 보이는 글자로 */
    private void textField(XPropertySet ps, Object portion, WParagraph p) throws com.sun.star.uno.Exception {
        Object f = ps.getPropertyValue("TextField");
        XServiceInfo fsi = UnoRuntime.queryInterface(XServiceInfo.class, f);
        int cp = doc.addCharPr(charPr(ps));
        if (fsi != null && (fsi.supportsService("com.sun.star.text.textfield.PageNumber")
                         || fsi.supportsService("com.sun.star.text.textfield.PageCount"))) {
            WParagraph.WRun r = new WParagraph.WRun(cp, "");
            boolean page = fsi.supportsService("com.sun.star.text.textfield.PageNumber");
            r.autoNum = page ? "PAGE" : "TOTAL_PAGE";
            XPropertySet fp = UnoRuntime.queryInterface(XPropertySet.class, f);
            if (fp != null && fp.getPropertySetInfo().hasPropertyByName("NumberingType")) {
                r.autoNumFormat = numberFormat(AnyConverter.toShort(fp.getPropertyValue("NumberingType")));
            }
            p.runs.add(r);
            return;
        }
        String s = UnoRuntime.queryInterface(XTextRange.class, portion).getString();
        if (s.isEmpty()) return;
        if (!p.runs.isEmpty() && p.runs.get(p.runs.size() - 1).charPrId == cp
                && p.runs.get(p.runs.size() - 1).shape == null && p.runs.get(p.runs.size() - 1).autoNum == null) {
            p.runs.get(p.runs.size() - 1).text += s;
        } else {
            p.runs.add(new WParagraph.WRun(cp, s));
        }
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

    /** alpha: 가져오기가 PERCENT 줄 간격에 곱한 보정값. 0 이면 LibreOffice 값을 그대로 두고 deferredAlpha 를 켠다 */
    private WParaPr paraPr(XPropertySet pp, double alpha, boolean afterTable) throws com.sun.star.uno.Exception {
        WParaPr p = new WParaPr();
        deferredAlpha = false;
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
        // 가져오기는 음수 오른쪽/위/아래 여백을 0 으로 읽는다 (ConvPara). 음수(예: 캡션 문단)는 0 으로 쓴다
        p.right = Math.max(0, 2 * hwp(right));
        p.prev = Math.max(0, 2 * hwp(top));
        p.next = Math.max(0, 2 * hwp(bottom));

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
            if (alpha <= 0) {
                p.lineSpacing = ls.Height;
                deferredAlpha = true;
            } else {
                p.lineSpacing = (int) Math.ceil(ls.Height / alpha - 1e-9);    // 가져오기: (short)(v*보정값), 버림의 역
            }
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
            headersFooters(ps);
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }
}
