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
import HwpxWriter.WNumbering;
import HwpxWriter.WParagraph;
import HwpxWriter.WSection;
import HwpxWriter.WShape;
import HwpxWriter.WNote;

/**
 * LibreOffice Writer 문서(UNO) 를 HWPX 쓰기용 모델(WDocument) 로 옮긴다.
 * 1단계 범위: 문단 텍스트, 글자 모양(글꼴/크기/굵게/기울임/밑줄/취소선/색), 문단 정렬/여백/줄 간격, 용지 크기/여백.
 * 표는 셀의 문단을 차례로 본문 문단으로 쓴다 (표 구조는 이후 단계).
 * 단위: LibreOffice 1/100 mm → HWPUNIT 은 가져오기(Transform.translateHwp2Office) 의 역변환.
 */
public class LoToHwpx {
    private static final Logger log = Logger.getLogger(LoToHwpx.class.getName());

    private final WDocument doc = WDocument.createDefault();
    /** 지금 쓰는 구역 */
    private WSection section = new WSection();
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
        c.textDoc = textDoc;
        c.documentInfo(component, context);
        c.defaultStyle();
        c.body(textDoc);
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
                        if (prev.table.paraAlign != null) {
                            prev.paraPrId = c.withAlign(prev.paraPrId, prev.table.paraAlign);
                        }
                    } catch (com.sun.star.uno.Exception e) {
                        log.log(Level.WARNING, e.toString(), e);
                    }
                }
            }
        }
        c.fixFloatTableOffsets();
        return c.doc;
    }

    /** 이 확장의 식별자 (description.xml) */
    private static final String EXTENSION_ID = "ebandal.libreoffice.H2Orestart";

    /**
     * 문서 정보: 만든 프로그램 버전(설치된 확장의 버전)과 LibreOffice 문서 속성(제목, 작성자, 주제, 설명, 키워드, 마지막 저장자,
     * 작성일). 기존: 버전은 늘 "1, 0, 0, 0", 작성자 등은 비어 있었다
     */
    private void documentInfo(Object component, com.sun.star.uno.XComponentContext context) {
        doc.appVersion = appVersion(context);
        try {
            com.sun.star.document.XDocumentPropertiesSupplier dps =
                    UnoRuntime.queryInterface(com.sun.star.document.XDocumentPropertiesSupplier.class, component);
            if (dps == null) return;
            com.sun.star.document.XDocumentProperties dp = dps.getDocumentProperties();
            doc.title = nz(dp.getTitle());
            doc.creator = nz(dp.getAuthor());
            doc.subject = nz(dp.getSubject());
            doc.description = nz(dp.getDescription());
            String[] kw = dp.getKeywords();
            doc.keyword = kw == null ? "" : String.join(", ", kw);
            doc.lastSaveBy = nz(dp.getModifiedBy());
            doc.createdDate = utcText(dp.getCreationDate());
        } catch (RuntimeException e) {
            log.log(Level.WARNING, e.toString(), e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 설치된 확장의 버전을 한글 형식("0, 8, 1, 0")으로. 모르면 "0, 0, 0, 0" */
    static String appVersion(com.sun.star.uno.XComponentContext context) {
        String v = null;
        try {
            if (context != null) {
                Object o = context.getValueByName("/singletons/com.sun.star.deployment.PackageInformationProvider");
                com.sun.star.deployment.XPackageInformationProvider pip =
                        UnoRuntime.queryInterface(com.sun.star.deployment.XPackageInformationProvider.class, o);
                if (pip != null) {
                    for (String[] e : pip.getExtensionList()) {
                        if (e.length >= 2 && EXTENSION_ID.equals(e[0])) v = e[1];
                    }
                }
            }
        } catch (RuntimeException e) {
            log.log(Level.FINE, e.toString(), e);
        }
        String[] parts = (v == null ? "" : v).split("[^0-9]+");
        int[] n = new int[4];
        int k = 0;
        for (String p : parts) {
            if (p.isEmpty() || k >= 4) continue;
            try { n[k++] = Integer.parseInt(p); } catch (NumberFormatException e) { k++; }
        }
        return n[0] + ", " + n[1] + ", " + n[2] + ", " + n[3];
    }

    /** LibreOffice 날짜를 한글 형식(UTC, yyyy-MM-dd'T'HH:mm:ss'Z')으로. 없으면 "" */
    private static String utcText(com.sun.star.util.DateTime d) {
        if (d == null || d.Year <= 0) return "";
        java.util.Calendar cal = java.util.Calendar.getInstance(d.IsUTC ? java.util.TimeZone.getTimeZone("UTC")
                                                                         : java.util.TimeZone.getDefault());
        cal.clear();
        cal.set(d.Year, d.Month - 1, d.Day, d.Hours, d.Minutes, d.Seconds);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(cal.getTime());
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
    /** 떠 있는 표의 문단과 왼쪽 위치(1/100 mm). 문단 모양이 정해진 뒤 가로 위치를 정한다 (fixFloatTableOffsets) */
    private final java.util.List<Object[]> floatTables = new java.util.ArrayList<Object[]>();

    /**
     * 떠 있는 표의 가로 위치에서 표 문단의 왼쪽 여백을 뺀다. 가져오기(ConvTable.setParaPosition, 문단 기준 왼쪽)는
     * 가로 위치에 문단 왼쪽 여백(값/2)을 더해 왼쪽 여백(LeftMargin)으로 쓴다.
     * 기존: 왼쪽 여백이 있는 문단의 표는 다시 읽을 때마다 그만큼 오른쪽으로 밀렸다
     */
    private void fixFloatTableOffsets() {
        for (Object[] ft : floatTables) {
            WParagraph p = (WParagraph) ft[0];
            int leftMm = (Integer) ft[1];
            if (p.paraPrId < 0 || p.paraPrId >= doc.paraPrList.size()) continue;
            int margin = doc.paraPrList.get(p.paraPrId).left / 2;
            int marginMm = (int) (margin * ((double) 21000 / 59529));     // Transform.translateHwp2Office
            if (margin <= 0) continue;
            p.table.horzOffset = hwp(Math.max(0, leftMm - marginMm));
        }
        floatTables.clear();
    }

    /** 앞 표 문단의 모양을 뒤 표 문단에서 가져올 표 문단 (elements) */
    private final java.util.List<WParagraph> copyFromNextTable = new java.util.ArrayList<WParagraph>();

    /** 가져오기가 넣은 표시(HwpRecurs.INSERTED_PARA_ATTR)가 있는 빈 문단 */
    private static boolean isInsertedEmptyParagraph(Object paraObj) throws com.sun.star.uno.Exception {
        XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, paraObj);
        if (ps == null || !ps.getPropertySetInfo().hasPropertyByName("ParaUserDefinedAttributes")) return false;
        com.sun.star.container.XNameContainer attrs = UnoRuntime.queryInterface(com.sun.star.container.XNameContainer.class,
                ps.getPropertyValue("ParaUserDefinedAttributes"));
        if (attrs == null || !attrs.hasByName(soffice.HwpRecurs.INSERTED_PARA_ATTR)) return false;
        return isEmptyParagraph(paraObj);
    }

    /** 글자가 있는 문단 (가져오기가 글자를 넣은 문단: HwpRecurs 의 append) */
    private static boolean hasText(Object paraObj) {
        XTextRange tr = UnoRuntime.queryInterface(XTextRange.class, paraObj);
        return tr != null && !tr.getString().isEmpty();
    }

    /** 글자 없이 비어 있는 문단 (개체, 필드 등도 없음) */
    private static boolean isEmptyParagraph(Object paraObj) throws com.sun.star.uno.Exception {
        XTextRange tr = UnoRuntime.queryInterface(XTextRange.class, paraObj);
        if (tr == null || !tr.getString().isEmpty()) return false;
        com.sun.star.container.XContentEnumerationAccess cea =
                UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, paraObj);
        if (cea != null && cea.createContentEnumeration("com.sun.star.text.TextContent").hasMoreElements()) return false;
        XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, paraObj);
        if (ea == null) return true;
        XEnumeration en = ea.createEnumeration();
        while (en.hasMoreElements()) {
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, en.nextElement());
            String type = AnyConverter.toString(ps.getPropertyValue("TextPortionType"));
            if (!"Text".equals(type) && !"SoftPageBreak".equals(type)) return false;
        }
        return true;
    }

    /** table() 가 글자처럼 취급하는 표로 쓸 표인지 (앞이 표가 아닐 때의 조건) */
    private boolean willBeCharTable(Object elem) throws com.sun.star.uno.Exception {
        XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, elem);
        if (si == null || !si.supportsService("com.sun.star.text.TextTable")) return false;
        XPropertySet tp = UnoRuntime.queryInterface(XPropertySet.class, elem);
        boolean split = AnyConverter.toBoolean(tp.getPropertyValue("Split"));
        int top = AnyConverter.toInt(tp.getPropertyValue("TopMargin"));
        return (!split || inFrameText) && top <= 0;
    }

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
            if (cp[0].table != null && cp[0].table.paraAlign != null) {
                cp[0].paraPrId = withAlign(cp[0].paraPrId, cp[0].table.paraAlign);
            }
        }
        paraPrCopies.clear();
    }

    /**
     * 탭 정의: 가져오기(ConvPara.setParagraphProperties)의 역.
     * 가져오기는 탭 위치를 mm 단위로 버림해 쓰고(최대 14.5cm), 채움 문자는 늘 '-' 이다.
     * 자동 탭(채움 문자 빈칸)은 오른쪽 끝 자동 탭(오른쪽 정렬)과 내어쓰기용 자동 탭(왼쪽, 위치 0) 이다.
     */
    private static HwpxWriter.WTabPr tabPr(XPropertySet pp) throws com.sun.star.uno.Exception {
        HwpxWriter.WTabPr t = new HwpxWriter.WTabPr();
        Object o = pp.getPropertyValue("ParaTabStops");
        if (!(o instanceof com.sun.star.style.TabStop[])) return t;
        com.sun.star.style.TabStop[] tss = (com.sun.star.style.TabStop[]) o;
        if (tss.length == 1 && tss[0].FillChar == ' ') {
            if (tss[0].Alignment == com.sun.star.style.TabAlign.RIGHT) {
                t.autoTabRight = true;
                return t;
            }
            if (tss[0].Alignment == com.sun.star.style.TabAlign.LEFT && tss[0].Position == 0) {
                t.autoTabLeft = true;
                return t;
            }
        }
        for (com.sun.star.style.TabStop ts : tss) {
            if (ts.Alignment == com.sun.star.style.TabAlign.DEFAULT) continue;
            String type = ts.Alignment == com.sun.star.style.TabAlign.RIGHT ? "RIGHT"
                        : ts.Alignment == com.sun.star.style.TabAlign.CENTER ? "CENTER"
                        : ts.Alignment == com.sun.star.style.TabAlign.DECIMAL ? "DECIMAL" : "LEFT";
            // 가져오기: Position = (int)(기본값/200 HWPUNIT 을 1/100 mm 로) * 100. mm 를 HWPUNIT 으로 올림해 되돌린다
            int mm = ts.Position / 100;
            t.add(100 * hwp(mm), type);
        }
        return t;
    }

    /** LibreOffice 목록 스타일(또는 목록 ID) → 번호/글머리표 정의 ID */
    private final java.util.Map<String, Integer> numberingIds = new java.util.HashMap<String, Integer>();
    private final java.util.Map<String, Integer> bulletIds = new java.util.HashMap<String, Integer>();

    /**
     * 문단 번호/글머리표: 가져오기(ConvPara.setNumberingProperties, ConvNumbering)의 역.
     * 번호가 보이는(ListLabelString) 문단만. 수준의 번호 종류가 특수 문자면 글머리표, 아니면 문단 번호.
     * 정의는 LibreOffice 목록 스타일마다 하나 (가져오기는 정의마다 목록 스타일을 만든다).
     */
    private void heading(XPropertySet pp, WParaPr p) throws com.sun.star.uno.Exception {
        com.sun.star.beans.XPropertySetInfo info = pp.getPropertySetInfo();
        if (!info.hasPropertyByName("NumberingRules") || !info.hasPropertyByName("ListLabelString")) return;
        Object lab = pp.getPropertyValue("ListLabelString");
        boolean labeled = lab instanceof String && !((String) lab).isEmpty();
        if (info.hasPropertyByName("NumberingIsNumber")) {
            Object isNum = pp.getPropertyValue("NumberingIsNumber");
            if (!(isNum instanceof Boolean) || !((Boolean) isNum)) return;     // 목록 항목이 아님
        } else if (!labeled) {
            return;
        }
        com.sun.star.container.XIndexAccess rules = UnoRuntime.queryInterface(com.sun.star.container.XIndexAccess.class,
                pp.getPropertyValue("NumberingRules"));
        if (rules == null) return;
        int level = AnyConverter.toShort(pp.getPropertyValue("NumberingLevel"));
        if (level < 0 || level >= rules.getCount()) return;
        String style = info.hasPropertyByName("NumberingStyleName") ? AnyConverter.toString(pp.getPropertyValue("NumberingStyleName")) : "";
        String key = style;
        if (key.isEmpty() && info.hasPropertyByName("ListId")) key = "list:" + AnyConverter.toString(pp.getPropertyValue("ListId"));
        com.sun.star.beans.PropertyValue[] lv = (com.sun.star.beans.PropertyValue[]) AnyConverter.toArray(rules.getByIndex(level));
        short type = AnyConverter.toShort(prop(lv, "NumberingType", (short) com.sun.star.style.NumberingType.ARABIC));
        if (type == com.sun.star.style.NumberingType.NUMBER_NONE) return;
        // 번호는 보이는 번호가 있을 때만 (글머리표는 LibreOffice 가 표시 문자열을 비워 둔다)
        if (type != com.sun.star.style.NumberingType.CHAR_SPECIAL && type != com.sun.star.style.NumberingType.BITMAP && !labeled) return;
        if (type == com.sun.star.style.NumberingType.CHAR_SPECIAL || type == com.sun.star.style.NumberingType.BITMAP) {
            Integer id = bulletIds.get(key);
            if (id == null) {
                WNumbering b = new WNumbering();
                b.bullet = true;
                if (type == com.sun.star.style.NumberingType.BITMAP) {
                    // 그림 글머리표: 그림을 바이너리 항목으로 (가져오기는 그림을 GraphicURL 로 넣는다)
                    Object gv = prop(lv, "Graphic", null);
                    if (gv instanceof com.sun.star.uno.Any) gv = ((com.sun.star.uno.Any) gv).getObject();
                    com.sun.star.graphic.XGraphic g = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphic.class, gv);
                    if (g == null) {
                        // 가져오기의 그림 글머리표는 GraphicBitmap(XBitmap) 으로 남는다. 그래픽으로 바꾼다
                        Object bm = prop(lv, "GraphicBitmap", null);
                        if (bm instanceof com.sun.star.uno.Any) bm = ((com.sun.star.uno.Any) bm).getObject();
                        g = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphic.class, bm);
                        if (g == null && bm != null && ctx != null) {
                            Object gp = ctx.getServiceManager().createInstanceWithContext("com.sun.star.graphic.GraphicProvider", ctx);
                            com.sun.star.graphic.XGraphicProvider prov = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphicProvider.class, gp);
                            com.sun.star.beans.PropertyValue[] q = new com.sun.star.beans.PropertyValue[1];
                            q[0] = new com.sun.star.beans.PropertyValue();
                            q[0].Name = "Bitmap";
                            q[0].Value = bm;
                            g = prov.queryGraphic(q);
                        }
                    }
                    byte[] data = graphicPng(g);
                    if (data == null || data.length == 0) {
                        log.info("그림 글머리표의 그림을 얻지 못함: " + key);
                        return;
                    }
                    b.imageId = doc.addBinItem("png", data);
                }
                Object ch = prop(lv, "BulletChar", "●");
                b.bulletChar = ch instanceof String && !((String) ch).isEmpty() ? (String) ch : "●";
                b.align[0] = align(lv);
                b.textOffset[0] = hwp(toInt(prop(lv, "IndentAt", 0)));
                doc.bullets.add(b);
                id = doc.bullets.size();
                bulletIds.put(key, id);
            }
            p.headingType = "BULLET";
            p.headingId = id;
            p.headingLevel = 0;
            return;
        }
        Integer id = numberingIds.get(key);
        if (id == null) {
            WNumbering n = new WNumbering();
            for (int l = 0; l < WNumbering.LEVELS && l < rules.getCount(); l++) {
                com.sun.star.beans.PropertyValue[] v = (com.sun.star.beans.PropertyValue[]) AnyConverter.toArray(rules.getByIndex(l));
                short t = AnyConverter.toShort(prop(v, "NumberingType", (short) com.sun.star.style.NumberingType.NUMBER_NONE));
                n.numFormat[l] = numberShape(t);
                n.start[l] = Math.max(0, toInt(prop(v, "StartWith", 1)));
                n.align[l] = align(v);
                n.textOffset[l] = Math.min(Short.MAX_VALUE, hwp(toInt(prop(v, "IndentAt", 0))));
                n.format[l] = t == com.sun.star.style.NumberingType.NUMBER_NONE ? "" : hwpFormat(v, l);
                n.charPrId[l] = numberCharPr(v);
            }
            doc.numberings.add(n);
            id = doc.numberings.size();
            numberingIds.put(key, id);
        }
        p.headingType = "NUMBER";
        p.headingId = id;
        p.headingLevel = level;
    }

    /**
     * 번호의 글자 모양: 가져오기는 번호의 글자 모양을 글자 스타일로 만들어 수준의 CharStyleName 에 넣는다
     * (ConvNumbering). 그 글자 스타일의 글자 모양을 쓴다. 없으면 -1
     */
    private int numberCharPr(com.sun.star.beans.PropertyValue[] v) {
        Object name = prop(v, "CharStyleName", "");
        if (!(name instanceof String) || ((String) name).isEmpty()) return -1;
        try {
            XNameAccess fams = UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, textDoc).getStyleFamilies();
            XNameAccess charStyles = UnoRuntime.queryInterface(XNameAccess.class, fams.getByName("CharacterStyles"));
            if (!charStyles.hasByName((String) name)) return -1;
            XPropertySet sp = UnoRuntime.queryInterface(XPropertySet.class, charStyles.getByName((String) name));
            return doc.addCharPr(charPr(sp));
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.WARNING, e.toString(), e);
            return -1;
        }
    }

    private static Object prop(com.sun.star.beans.PropertyValue[] v, String name, Object def) {
        for (com.sun.star.beans.PropertyValue x : v) {
            if (name.equals(x.Name)) return x.Value == null ? def : x.Value;
        }
        return def;
    }

    private static int toInt(Object o) {
        try {
            return AnyConverter.toInt(o);
        } catch (com.sun.star.lang.IllegalArgumentException e) {
            return o instanceof Number ? ((Number) o).intValue() : 0;
        }
    }

    private static String align(com.sun.star.beans.PropertyValue[] v) {
        int a = toInt(prop(v, "Adjust", (short) com.sun.star.text.HoriOrientation.LEFT));
        return a == com.sun.star.text.HoriOrientation.CENTER ? "CENTER" : a == com.sun.star.text.HoriOrientation.RIGHT ? "RIGHT" : "LEFT";
    }

    /** 수준 형식: ListFormat("%1%.%2%)") 을 HWP 형식("^1.^2)") 으로. 없으면 접두/접미와 상위 수준 수로 만든다 */
    private static String hwpFormat(com.sun.star.beans.PropertyValue[] v, int level) {
        Object lf = prop(v, "ListFormat", "");
        if (lf instanceof String && !((String) lf).isEmpty()) {
            return ((String) lf).replaceAll("%(\\d+)%", "^$1");
        }
        String prefix = String.valueOf(prop(v, "Prefix", ""));
        String suffix = String.valueOf(prop(v, "Suffix", ""));
        int parents = Math.max(1, toInt(prop(v, "ParentNumbering", (short) 1)));
        StringBuilder sb = new StringBuilder(prefix);
        for (int k = level + 2 - parents; k <= level + 1; k++) {
            if (k < 1) continue;
            sb.append('^').append(k);
            if (k < level + 1) sb.append('.');
        }
        return sb.append(suffix).toString();
    }

    /** LibreOffice 번호 종류 → HWPX 번호 모양 (ConvNumbering.toNumberingType 의 역) */
    private static String numberShape(short t) {
        switch (t) {
        case com.sun.star.style.NumberingType.CIRCLE_NUMBER:              return "CIRCLED_DIGIT";
        case com.sun.star.style.NumberingType.ROMAN_UPPER:                return "ROMAN_CAPITAL";
        case com.sun.star.style.NumberingType.ROMAN_LOWER:                return "ROMAN_SMALL";
        case com.sun.star.style.NumberingType.CHARS_UPPER_LETTER:         return "LATIN_CAPITAL";
        case com.sun.star.style.NumberingType.CHARS_LOWER_LETTER:         return "LATIN_SMALL";
        case com.sun.star.style.NumberingType.HANGUL_SYLLABLE_KO:         return "HANGUL_SYLLABLE";
        case com.sun.star.style.NumberingType.HANGUL_CIRCLED_SYLLABLE_KO: return "CIRCLED_HANGUL_SYLLABLE";
        case com.sun.star.style.NumberingType.HANGUL_JAMO_KO:             return "HANGUL_JAMO";
        case com.sun.star.style.NumberingType.HANGUL_CIRCLED_JAMO_KO:     return "CIRCLED_HANGUL_JAMO";
        case com.sun.star.style.NumberingType.NUMBER_HANGUL_KO:           return "HANGUL_PHONETIC";
        case com.sun.star.style.NumberingType.NUMBER_LOWER_ZH:            return "IDEOGRAPH";
        default:                                                          return "DIGIT";
        }
    }

    /** 문단 모양 id 의 정렬만 바꾼 문단 모양 id */
    private int withAlign(int paraPrId, String align) {
        WParaPr src = doc.paraPrList.get(paraPrId);
        if (align.equals(src.align)) return paraPrId;
        WParaPr p = copyParaPr(src);
        p.align = align;
        return doc.addParaPr(p);
    }

    private static WParaPr copyParaPr(WParaPr src) {
        WParaPr p = new WParaPr();
        p.align = src.align;
        p.indent = src.indent;
        p.left = src.left;
        p.right = src.right;
        p.prev = src.prev;
        p.next = src.next;
        p.lineSpacingType = src.lineSpacingType;
        p.lineSpacing = src.lineSpacing;
        p.tabPrId = src.tabPrId;
        p.borderFillId = src.borderFillId;
        p.headingType = src.headingType;
        p.headingId = src.headingId;
        p.headingLevel = src.headingLevel;
        p.keepWithNext = src.keepWithNext;
        p.keepLines = src.keepLines;
        return p;
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
    /**
     * 문단 배경색을 커서로 읽는다. 가져오기가 커서로 지정한 ParaBackColor 는 문단 개체로 읽으면 보이지 않고
     * (투명으로 나온다. ODT 저장에도 빠진다) 커서로 읽어야 나온다. 문단 스타일의 배경과 같으면 이어받은 것이므로 null.
     */
    private Integer cursorBackColor(XPropertySet pp) {
        try {
            com.sun.star.text.XTextRange range = UnoRuntime.queryInterface(com.sun.star.text.XTextRange.class, pp);
            if (range == null) return null;     // 스타일
            XPropertySet cp = UnoRuntime.queryInterface(XPropertySet.class,
                    range.getText().createTextCursorByRange(range.getStart()));
            if (cp == null || AnyConverter.toBoolean(cp.getPropertyValue("ParaBackTransparent"))) return null;
            int color = AnyConverter.toInt(cp.getPropertyValue("ParaBackColor"));
            if (color == -1) return null;
            color &= 0xFFFFFF;
            String styleName = AnyConverter.toString(pp.getPropertyValue("ParaStyleName"));
            XNameAccess fams = UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, textDoc).getStyleFamilies();
            XNameAccess paraStyles = UnoRuntime.queryInterface(XNameAccess.class, fams.getByName("ParagraphStyles"));
            if (paraStyles.hasByName(styleName)) {
                XPropertySet sp = UnoRuntime.queryInterface(XPropertySet.class, paraStyles.getByName(styleName));
                if (!AnyConverter.toBoolean(sp.getPropertyValue("ParaBackTransparent"))
                        && (AnyConverter.toInt(sp.getPropertyValue("ParaBackColor")) & 0xFFFFFF) == color) {
                    return null;
                }
            }
            return color;
        } catch (com.sun.star.uno.Exception e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 0 번 스타일(기본 스타일)을 먼저 정한다. 가져오기는 스타일을 지정하지 않는 문단(개체만 있는 문단, 표 문단 등)에
     * 0 번 스타일("HWP  0 이름", ConvPara.setDefaultParaStyle)을 쓴다. HWP 에서 온 문서면 그 스타일을, 아니면
     * LibreOffice 기본 문단 스타일(Standard)을 0 번에 둔다.
     * 기존: 처음 만난 스타일(머리말의 Header 등)이 0 번이 되어, 다시 읽으면 그런 문단의 스타일이 Header 로 바뀌었다
     */
    private void defaultStyle() {
        String name = "Standard";
        try {
            XNameAccess fams = UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, textDoc).getStyleFamilies();
            XNameAccess paraStyles = UnoRuntime.queryInterface(XNameAccess.class, fams.getByName("ParagraphStyles"));
            for (String n : paraStyles.getElementNames()) {
                if (n.matches("^HWP\\s+0\\s.*$")) {
                    name = n;
                    break;
                }
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.WARNING, e.toString(), e);
        }
        styleId(name);
    }

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
                // 스타일의 문단 모양 (배경색 포함). 가져오기는 스타일 문단 모양을 스타일에 적용하고, 문단에 직접 지정하지
                // 않은 배경은 스타일에서 이어받는다 (엑셀에서 붙인 셀 스타일 등)
                st.paraPrId = doc.addParaPr(paraPr(sp, importAlpha(st.charPrId), false));
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
    /** 글상자(틀) 안의 글을 쓰는 중인가 */
    private boolean inFrameText;
    /** 그리기 개체 순서 (zOrder, id) */
    private int shapeSeq;

    /** 본문 폭 (1/100 mm). 표 가로 위치 계산에 쓴다 */
    private int bodyWidthMm100 = 16000;

    /**
     * 본문을 구역으로 나눠 쓴다. 가져오기는 구역마다 쪽 스타일("HWP n")을 만들고 구역 첫 문단에 쪽 스타일을
     * 지정한다(PageDescName). 쪽 스타일이 바뀌는 문단/표에서 새 구역을 시작한다.
     */
    private void body(XTextDocument textDoc) {
        java.util.List<Object> all = elementsOf(textDoc.getText());
        java.util.List<java.util.List<Object>> parts = new java.util.ArrayList<java.util.List<Object>>();
        java.util.List<String> styles = new java.util.ArrayList<String>();
        String cur = null;
        for (Object elem : all) {
            String pd = pageDescName(elem);
            if (parts.isEmpty()) {
                cur = !pd.isEmpty() ? pd : pageStyleName(elem);
                parts.add(new java.util.ArrayList<Object>());
                styles.add(cur);
            } else if (!pd.isEmpty() && !pd.equals(cur)) {
                cur = pd;
                parts.add(new java.util.ArrayList<Object>());
                styles.add(cur);
            }
            parts.get(parts.size() - 1).add(elem);
        }
        if (parts.isEmpty()) {
            parts.add(new java.util.ArrayList<Object>());
            styles.add("Standard");
        }
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) section = new WSection();
            doc.sections.add(section);
            page(styles.get(i));
            elements(parts.get(i), section.paragraphs, bodyWidthMm100);
            if (i > 0 && !section.paragraphs.isEmpty()) {
                // 구역 시작은 새 쪽이다. 쪽 나눔을 함께 쓰면 가져오기가 빈 문단을 하나 더 만든다 (ConvPage.makeNextPage)
                section.paragraphs.get(0).pageBreak = false;
            }
        }
    }

    private static String pageDescName(Object elem) {
        try {
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, elem);
            if (ps != null && ps.getPropertySetInfo().hasPropertyByName("PageDescName")) {
                Object v = ps.getPropertyValue("PageDescName");
                if (v instanceof String) return (String) v;
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.FINE, e.toString(), e);
        }
        return "";
    }

    private static String pageStyleName(Object elem) {
        try {
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, elem);
            if (ps != null && ps.getPropertySetInfo().hasPropertyByName("PageStyleName")) {
                return AnyConverter.toString(ps.getPropertyValue("PageStyleName"));
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.FINE, e.toString(), e);
        }
        return "Standard";
    }

    private static java.util.List<Object> elementsOf(XText text) {
        java.util.List<Object> list = new java.util.ArrayList<Object>();
        XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, text);
        if (ea == null) return list;
        XEnumeration en = ea.createEnumeration();
        try {
            while (en.hasMoreElements()) {
                list.add(en.nextElement());
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
        return list;
    }

    /**
     * 글자 없이 틀 하나만 있는 문단이고, 그 틀의 내용이 표 하나(와 빈 문단)뿐이면 그 틀의 텍스트. 아니면 null.
     * 가져오기는 글상자 안의 표를 틀로 감싸므로(ConvTable.makeOuterFrame) 이런 틀은 글상자 안에서 생긴다.
     */
    private static XText tableWrapperFrame(Object paraObj) throws com.sun.star.uno.Exception {
        XTextRange pr = UnoRuntime.queryInterface(XTextRange.class, paraObj);
        if (pr == null || !pr.getString().trim().isEmpty()) return null;
        java.util.List<Object> frames = new java.util.ArrayList<Object>();
        // 글자처럼 놓인 개체
        XEnumerationAccess ea = UnoRuntime.queryInterface(XEnumerationAccess.class, paraObj);
        XEnumeration en = ea.createEnumeration();
        while (en.hasMoreElements()) {
            Object portion = en.nextElement();
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, portion);
            String type = AnyConverter.toString(ps.getPropertyValue("TextPortionType"));
            if ("Frame".equals(type)) {
                com.sun.star.container.XContentEnumerationAccess cea =
                        UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, portion);
                if (cea == null) continue;
                XEnumeration ce = cea.createContentEnumeration("com.sun.star.text.TextContent");
                while (ce.hasMoreElements()) frames.add(ce.nextElement());
            } else if (!"Text".equals(type)) {
                return null;
            }
        }
        // 문단에 붙은 개체
        com.sun.star.container.XContentEnumerationAccess cea =
                UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, paraObj);
        if (cea != null) {
            XEnumeration ce = cea.createContentEnumeration("com.sun.star.text.TextContent");
            while (ce.hasMoreElements()) frames.add(ce.nextElement());
        }
        if (frames.size() != 1) return null;
        com.sun.star.text.XTextFrame frame = UnoRuntime.queryInterface(com.sun.star.text.XTextFrame.class, frames.get(0));
        if (frame == null) return null;
        XText ft = frame.getText();
        int tables = 0;
        for (Object e : elementsOf(ft)) {
            XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, e);
            if (si == null) continue;
            if (si.supportsService("com.sun.star.text.TextTable")) {
                tables++;
            } else if (si.supportsService("com.sun.star.text.Paragraph")) {
                // 빈 문단만 (개체도 없어야 한다)
                XTextRange r = UnoRuntime.queryInterface(XTextRange.class, e);
                if (!r.getString().trim().isEmpty()) return null;
                com.sun.star.container.XContentEnumerationAccess pc =
                        UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, e);
                if (pc != null && pc.createContentEnumeration("com.sun.star.text.TextContent").hasMoreElements()) return null;
            }
        }
        return tables == 1 ? ft : null;
    }

    /** text 의 문단과 표를 target 에 넣는다. avail: 이 텍스트 영역의 폭 (1/100 mm) */
    private void text(XText text, java.util.List<WParagraph> target, int avail) {
        elements(elementsOf(text), target, avail);
    }

    private void elements(java.util.List<Object> elems, java.util.List<WParagraph> target, int avail) {
        int start = target.size();
        boolean afterTable = false;
        try {
            for (int ei = 0; ei < elems.size(); ei++) {
                Object elem = elems.get(ei);
                XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, elem);
                if (si == null) continue;
                if (si.supportsService("com.sun.star.text.Paragraph") && afterTable && !target.isEmpty()
                        && target.get(target.size() - 1).table != null && ei + 1 < elems.size()
                        && willBeCharTable(elems.get(ei + 1)) && isInsertedEmptyParagraph(elem)) {
                    // 가져오기가 표와 글자처럼 취급하는 표 사이에 넣은 빈 문단 (HwpRecurs.markInsertedPara): 원본에 없으므로
                    // 쓰지 않는다. 다시 읽으면 가져오기가 다시 넣는다. 앞 표 문단의 모양은 뒤 표 문단에서 가져온다
                    // (이 문단의 모양은 문단 시작에서 기본 스타일로 돌리는지에 따라 달라져 쓸 수 없다).
                    // 기존: 표마다 빈 줄이 하나씩 늘어 한글에서 큰 표가 다음 쪽으로 밀렸다
                    copyFromNextTable.add(target.get(target.size() - 1));
                    afterTable = false;     // 다음 표는 글자처럼 취급하는 표로 쓴다
                    continue;
                }
                if (si.supportsService("com.sun.star.text.Paragraph")) {
                    XText wrapped = inFrameText ? tableWrapperFrame(elem) : null;
                    if (wrapped != null) {
                        // 표만 감싼 틀: 가져오기가 글상자 안의 표를 틀로 감싼다 (ConvTable.insertTable, TableFrame.MAKE).
                        // 틀 없이 표만 써서 다시 읽어도 틀이 늘어나지 않게 한다
                        elements(elementsOf(wrapped), target, avail);
                        afterTable = false;
                        continue;
                    }
                    // 가져오기의 표 다음 문단 간격 보정은 본문/머리말/글상자에서 한다 (표 셀은 하지 않는다: ConvTable).
                    // 글자가 없는 문단은 보정하지 않는다 (HwpRecurs: firstParaAfterTable && append)
                    paragraph(elem, target, afterTable && !inCellText && hasText(elem));
                    if (target == section.paragraphs) {
                        lastBodyPara = UnoRuntime.queryInterface(XPropertySet.class, elem);
                    }
                    afterTable = false;
                } else if (si.supportsService("com.sun.star.text.TextTable")) {
                    table(elem, target, avail, afterTable);
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
        java.util.List<Integer> fromNext = new java.util.ArrayList<Integer>();
        for (int i = start; i + 1 < target.size(); i++) {
            WParagraph p = target.get(i);
            if (copyFromNextTable.contains(p) && target.get(i + 1).table != null) fromNext.add(i);
            if (p.table != null && target.get(i + 1).table == null) {
                paraPrCopies.add(new WParagraph[] { p, target.get(i + 1) });
                // 문단 끝 글자 모양도 같게: 문서 끝 표는 가져오기가 표 문단 모양으로 끝 문단을 만들고, 줄 간격 보정값을
                // 표 문단의 글자 모양(글꼴)으로 계산한다
                p.runs.clear();
                p.runs.add(new WParagraph.WRun(target.get(i + 1).lastCharPrId(), ""));
                // 스타일도 같게: 가져오기는 표 문단의 스타일을 표 뒤 문단에 둔다 (빈 문단이면 그대로 남는다)
                p.styleId = target.get(i + 1).styleId;
            }
        }
        // 뒤 표 문단의 모양을 따르는 표 문단 (가져오기가 넣은 빈 문단을 뺀 곳). 뒤 표 문단이 먼저 정해지도록 뒤에서부터
        for (int k = fromNext.size() - 1; k >= 0; k--) {
            int i = fromNext.get(k);
            WParagraph p = target.get(i);
            WParagraph next = target.get(i + 1);
            paraPrCopies.add(new WParagraph[] { p, next });
            p.runs.clear();
            p.runs.add(new WParagraph.WRun(next.lastCharPrId(), ""));
            p.styleId = next.styleId;
        }
        copyFromNextTable.removeAll(target.subList(start, target.size()));
    }

    /**
     * 표를 문단 하나(표만 담은 문단)로 쓴다. 가져오기는 표만 있는 문단을 본문 표로 만들고 그 문단 끝을 건너뛴다.
     * 격자: 행마다 열 구분선(TableColumnSeparators)으로 칸이 나뉘고, 칸 이름의 알파벳은 그 행의 칸 순서다.
     *       세로 병합은 위 칸의 RowSpan 이고 덮인 칸은 셀 이름 목록에서 빠진다.
     *       모든 행의 구분선 위치를 합쳐 격자 열을 만들고, 칸이 걸친 격자 열 수를 colSpan 으로 한다.
     */
    private void table(Object tableObj, java.util.List<WParagraph> target, int avail, boolean afterTable) throws com.sun.star.uno.Exception {
        com.sun.star.text.XTextTable xt = UnoRuntime.queryInterface(com.sun.star.text.XTextTable.class, tableObj);
        XPropertySet tp = UnoRuntime.queryInterface(XPropertySet.class, tableObj);
        com.sun.star.table.XCellRange range = UnoRuntime.queryInterface(com.sun.star.table.XCellRange.class, tableObj);
        if (xt == null || tp == null || range == null) return;
        java.util.Set<String> visible = new java.util.HashSet<String>(java.util.Arrays.asList(xt.getCellNames()));
        int relSum = AnyConverter.toShort(tp.getPropertyValue("TableColumnRelativeSum"));
        int widthMm = AnyConverter.toInt(tp.getPropertyValue("Width"));
        // 양쪽 여백에 맞춘 표(HoriOrient FULL)는 배치가 계산되기 전에는 폭이 상대값(65535 twip = 115.6cm)으로 나온다
        // (글상자 안의 표 등). 그때는 놓일 영역의 폭을 쓴다
        if (avail > 0 && widthMm > avail
                && AnyConverter.toShort(tp.getPropertyValue("HoriOrient")) == com.sun.star.text.HoriOrientation.FULL) {
            widthMm = avail;
        }
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
        // 열 경계를 1/100 mm 로 바꾸고, 열마다 그 폭(1/100 mm)을 HWPUNIT 으로 바꾼다. 가져오기(ConvTable.insertTable)는
        // 열 폭을 열마다 1/100 mm 로 바꿔(소수점 버림) 누적하고 표 폭(sz)에 대한 비율로 구분선 위치를 정한다.
        // hwp() 는 버림의 역이므로 열 폭과 표 폭이 정확히 같은 1/100 mm 로 돌아와 구분선 위치가 그대로 유지된다.
        // 기존: 누적 경계를 HWPUNIT 으로 바꾼 차이를 열 폭으로 써서, 다시 읽을 때 열마다 1/100 mm 가 1 씩 어긋나
        //       저장할 때마다 열 폭이 몇 HWPUNIT 씩 바뀌었다
        for (int j = 0; j < nCols; j++) {
            int x0 = (int) Math.round(gx[j] * (double) widthMm / relSum);
            int x1 = (int) Math.round(gx[j + 1] * (double) widthMm / relSum);
            colWidth[j] = hwp(x1 - x0);
        }
        java.util.Map<Integer, Integer> gridIndex = new java.util.HashMap<Integer, Integer>();
        for (int j = 0; j < gx.length; j++) gridIndex.put(gx[j], j);
        boolean anyAutoRow = false;
        for (int h : rowHeight) if (h == 0) anyAutoRow = true;

        HwpxWriter.WTable t = new HwpxWriter.WTable();
        t.rowCnt = nRows;
        t.colCnt = nCols;
        // 표 폭은 열 폭 합이 아니라 표 폭(1/100 mm)을 바꾼 값 (열마다 올림한 합은 몇 HWPUNIT 크다. 그 합을 쓰면
        // 다시 읽을 때 표가 넓어져 구분선 비율이 바뀐다)
        t.width = hwp(widthMm);
        boolean repeat = AnyConverter.toBoolean(tp.getPropertyValue("RepeatHeadline"));
        int headerRows = repeat ? AnyConverter.toInt(tp.getPropertyValue("HeaderRowCount")) : 0;
        t.repeatHeader = repeat && headerRows > 0;
        int[] tm = grabBagInts(tp, "TableInteropGrabBag", 4);   // 표 안 여백 (가져오기 ConvTable)
        if (tm != null) t.inMargin = tm;
        boolean split = AnyConverter.toBoolean(tp.getPropertyValue("Split"));
        // 글상자 안의 표는 쪽에서 나뉘지 않는다. 가져오기도 글자처럼 취급하는 표는 나누지 않으므로 NONE 으로 쓴다
        // (기존: 원본에서 온 나눔 값을 써서 다시 읽으면 NONE 으로 바뀌었다)
        t.pageBreak = !split || inFrameText ? "NONE" : (rowNoSplit ? "TABLE" : "CELL");
        int leftMm = Math.max(0, tableLeft(tp, widthMm, avail));
        t.horzOffset = hwp(leftMm);
        int top = AnyConverter.toInt(tp.getPropertyValue("TopMargin"));
        t.vertOffset = top > 0 ? hwp(top) : 0;
        // 표 바로 뒤의 표는 떠 있는 표로 둔다. 가져오기는 표 다음의 글자처럼 취급하는 표 앞에 빈 문단을 넣는다
        // (HwpRecurs: firstParaAfterTable && treatAsChar)
        // 글상자 안의 표는 나뉘지 않으므로 글자처럼 취급하는 표로 쓴다 (떠 있는 표로 쓰면 가져오기가 표를 틀로 감싸
        // 저장할 때마다 틀이 하나씩 늘어난다)
        if ((!split || inFrameText) && top <= 0 && !afterTable) {
            // 글자처럼 취급하는 표로 쓴다 (한글 문서의 표는 대부분 이렇다. 떠 있는 표는 한글에서 쪽 경계의 글과 겹칠 수
            // 있다). 가져오기(ConvTable.setParaPosition)는 이런 표를 나누지 않고, 가로 위치를 표 문단의 정렬과
            // "가로 위치 - 쪽 왼쪽 여백" 으로 정한다
            t.treatAsChar = true;
            t.vertOffset = 0;
            // 쪽 경계에서 셀 단위로 나눈다. 가져오기는 글자처럼 취급하는 표를 나누지 않지만(나눔 값은 쓰지 않음), 한글은 나누지 않는
            // 표를 통째로 다음 쪽으로 넘겨 큰 표 앞에 빈 공간이 생기고 쪽 수가 늘었다. 원본 한글 문서도 대부분 CELL 이다
            if (!inFrameText) t.pageBreak = "CELL";
            short hori = AnyConverter.toShort(tp.getPropertyValue("HoriOrient"));
            int leftMargin = AnyConverter.toInt(tp.getPropertyValue("LeftMargin"));
            t.horzOffset = 0;
            if (hori == com.sun.star.text.HoriOrientation.CENTER || hori == com.sun.star.text.HoriOrientation.FULL) {
                t.paraAlign = "CENTER";
            } else if (hori == com.sun.star.text.HoriOrientation.RIGHT) {
                t.paraAlign = "RIGHT";
            } else if (leftMargin > 0 && !inCellText) {
                t.paraAlign = "DISTRIBUTE";     // 가져오기: 왼쪽 여백(LeftMargin)으로 놓는다 (HoriOrient NONE)
                t.horzOffset = section.marginLeft + hwp(leftMargin);
            } else {
                t.paraAlign = "LEFT";
            }
        }

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
                int[] cm = grabBagInts(cp, "CellInteropGrabBag", 5);     // 셀 여백 (가져오기 ConvTable)
                if (cm != null) {
                    c.hasMargin = cm[0] != 0;
                    c.margin = new int[] { cm[1], cm[2], cm[3], cm[4] };
                }
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
        if (!t.treatAsChar) floatTables.add(new Object[] { p, leftMm });
    }

    /** InteropGrabBag 의 여백 항목(ConvTable.MARGIN_KEY, "a|b|..." 정수 n 개). 없으면 null */
    private static int[] grabBagInts(XPropertySet ps, String propName, int n) {
        try {
            if (!ps.getPropertySetInfo().hasPropertyByName(propName)) return null;
            Object o = ps.getPropertyValue(propName);
            if (!(o instanceof com.sun.star.beans.PropertyValue[])) return null;
            for (com.sun.star.beans.PropertyValue pv : (com.sun.star.beans.PropertyValue[]) o) {
                if (!soffice.ConvTable.MARGIN_KEY.equals(pv.Name) || !(pv.Value instanceof String)) continue;
                String[] v = ((String) pv.Value).split("[|]");
                if (v.length != n) return null;
                int[] r = new int[n];
                for (int i = 0; i < n; i++) r[i] = Integer.parseInt(v[i]);
                return r;
            }
        } catch (com.sun.star.uno.Exception | RuntimeException e) {
            log.log(Level.FINE, e.toString(), e);
        }
        return null;
    }

    /** 표의 왼쪽 위치 (1/100 mm). 가져오기는 다시 읽을 때 왼쪽 여백 + 폭(LEFT_AND_WIDTH) 으로 놓는다 */
    private static int tableLeft(XPropertySet tp, int width, int avail) throws com.sun.star.uno.Exception {
        short hori = AnyConverter.toShort(tp.getPropertyValue("HoriOrient"));
        int left = AnyConverter.toInt(tp.getPropertyValue("LeftMargin"));
        int right = AnyConverter.toInt(tp.getPropertyValue("RightMargin"));
        switch (hori) {
        case com.sun.star.text.HoriOrientation.CENTER:
            return twipRound((avail - width) / 2);
        case com.sun.star.text.HoriOrientation.RIGHT:
            return twipRound(avail - width - right);
        default:
            return left;
        }
    }

    /**
     * 1/100 mm 값을 LibreOffice 가 저장하는 twip 단위로 맞춘다. 다시 읽으면 왼쪽 여백(LeftMargin)으로 들어가 twip 으로
     * 저장되므로, 계산한 위치는 그 값으로 써야 저장/다시 읽기를 반복해도 같다 (기존: 299 가 다시 읽으면 300 이 되었다)
     */
    private static int twipRound(int mm100) {
        long twip = Math.round(mm100 * 72.0 / 127.0);
        return (int) Math.round(twip * 127.0 / 72.0);
    }

    /** 셀 테두리/배경. 가져오기(Transform.toBorderLine)는 선 굵기와 색만 쓰므로 선은 실선으로 되돌린다 */
    private static HwpxWriter.WBorderFill cellBorderFill(XPropertySet cp) throws com.sun.star.uno.Exception {
        return borderFill(cp, "BackColor", "BackTransparent");
    }

    /**
     * 테두리 4변과 배경색. 셀은 BackColor, 문단은 ParaBackColor (가져오기 ConvPara: 문단 모양의 테두리/배경에서
     * 선 굵기/색과 채우기 색만 쓴다)
     */
    private static HwpxWriter.WBorderFill borderFill(XPropertySet cp, String backProp, String transProp) throws com.sun.star.uno.Exception {
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
        boolean transparent = AnyConverter.toBoolean(cp.getPropertyValue(transProp));
        int back = AnyConverter.toInt(cp.getPropertyValue(backProp));
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
            if ("Footnote".equals(type)) {
                Object fo = ps.getPropertyValue("Footnote");
                WNote n = note(fo);
                if (n != null) {
                    WParagraph.WRun r = new WParagraph.WRun(doc.addCharPr(charPr(ps)), "");
                    r.note = n;
                    p.runs.add(r);
                }
                continue;
            }
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
                    && p.runs.get(p.runs.size() - 1).autoNum == null && p.runs.get(p.runs.size() - 1).note == null) {
                p.runs.get(p.runs.size() - 1).text += s;
            } else {
                p.runs.add(new WParagraph.WRun(cp, s));
            }
        }
        boolean floating = floatingShapes(paraObj, p, target == section.paragraphs);
        // 문단 끝 글자 모양 = 문단의 글자 속성. 가져오기는 문단 끝(beforeParaBreak)에서 이 모양으로 문단 속성을
        // 마지막으로 설정한다 (줄 간격 보정값 포함). HWPX 파서는 마지막 run 의 모양을 문단 끝 모양으로 쓰므로,
        // 마지막 run 과 다르면 빈 run 을 덧붙인다
        WCharPr endPr = charPr(pp);
        boolean endOrig = origFont(pp, endPr, "ParaUserDefinedAttributes");   // 문단 끝 글자 모양의 원래 글꼴 (가져오기 setParaOrigFontAttr)
        int endCp = doc.addCharPr(endPr);
        if (!endOrig && isSubstFont(AnyConverter.toString(pp.getPropertyValue("CharFontNameAsian")))) {
            // 문단 끝 글자 모양이 대체 글꼴인데 원래 글꼴을 알 수 없다: 마지막 글자 run 의 모양을 쓴다 (한글에 없는 대체 글꼴을
            // 쓰지 않고, 다시 읽어도 같게)
            for (int k = p.runs.size() - 1; k >= 0; k--) {
                WParagraph.WRun r = p.runs.get(k);
                if (r.shape == null && r.text != null && !r.text.isEmpty()) {
                    endCp = r.charPrId;
                    break;
                }
            }
        }
        if (!p.runs.isEmpty() && p.runs.get(0).shape != null && !p.text().isEmpty()) {
            // 개체로 시작하고 글자가 있는 문단: 가져오기는 개체 뒤 첫 글자를 넣으며 문단 스타일을 다시 설정해 문단의
            // 글자 속성을 스타일 값으로 되돌리고, 문단 끝 모양은 줄 간격 보정에만 쓴다. 문단의 글자 속성은 다시 읽을 때마다
            // 달라질 수 있으므로 마지막 글자 run 의 모양을 문단 끝 모양으로 쓴다 (글자 모양은 다시 읽어도 같다)
            for (int k = p.runs.size() - 1; k >= 0; k--) {
                WParagraph.WRun r = p.runs.get(k);
                if (r.shape == null && r.text != null && !r.text.isEmpty()) {
                    endCp = r.charPrId;
                    break;
                }
            }
        }
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
        if (deferredAlpha && floating && inCellText && singleParaCell && !p.text().isEmpty()) {
            // 문단 하나뿐인 셀에서 글자 뒤에 떠 있는 개체가 있던 문단: 개체를 문단 앞에 쓰므로 다시 읽으면 가져오기는
            // 글자를 마지막에 넣고 줄 간격이 "최소 100" 이 되어(개체로 시작하는 문단과 같다) 한글 기본값 160 으로
            // 저장된다. 처음부터 그 값으로 쓴다 (기존: 157 로 저장했다가 다시 저장하면 160 으로 바뀌었다)
            wpp.lineSpacingType = "PERCENT";
            wpp.lineSpacing = 160;
            deferredAlpha = false;
        }
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
    private boolean floatingShapes(Object paraObj, WParagraph p, boolean body) throws com.sun.star.uno.Exception {
        com.sun.star.container.XContentEnumerationAccess cea =
                UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, paraObj);
        if (cea == null) return false;
        XEnumeration ce = cea.createContentEnumeration("com.sun.star.text.TextContent");
        java.util.List<WShape> shapes = new java.util.ArrayList<WShape>();
        while (ce.hasMoreElements()) {
            WShape sh = shape(ce.nextElement());
            if (sh != null && !sh.treatAsChar) shapes.add(sh);
        }
        if (shapes.isEmpty()) return false;
        int cp = p.runs.isEmpty() ? doc.addCharPr(charPr(UnoRuntime.queryInterface(XPropertySet.class, paraObj)))
                                  : p.runs.get(0).charPrId;
        for (int k = 0; k < shapes.size(); k++) {
            // 본문의 선은 가져오기가 빈칸을 넣지 않는다 (ConvGraphics.insertLINE: step 2 에서만)
            if (shapes.get(k).kind == WShape.Kind.LINE && body) continue;
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
        return true;
    }

    /** 그림(TextGraphicObject) 또는 글상자(TextFrame). 다른 개체는 null */
    private WShape shape(Object obj) throws com.sun.star.uno.Exception {
        XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, obj);
        XPropertySet op = UnoRuntime.queryInterface(XPropertySet.class, obj);
        if (si == null || op == null) return null;
        WShape s;
        // 그리기 도형(SdrObject)으로 만드는 그림인가 (크기/위치를 도형에서 읽는다)
        boolean drawingShape = false;
        if (si.supportsService("com.sun.star.text.TextGraphicObject")) {
            s = new WShape(WShape.Kind.PICTURE);
            if (!picture(op, s)) return null;
        } else if (si.supportsService("com.sun.star.text.TextFrame")) {
            WShape captioned = captionedShape(obj, op);
            if (captioned != null) return captioned;
            s = new WShape(WShape.Kind.TEXTBOX);
        } else if (si.supportsService("com.sun.star.drawing.LineShape")) {
            s = new WShape(WShape.Kind.LINE);
            drawLine(op, s);
            // 선의 방향: 시작점과 끝점의 좌표 순서
            if (op.getPropertySetInfo().hasPropertyByName("PolyPolygon")) {
                Object pv = op.getPropertyValue("PolyPolygon");
                if (pv instanceof com.sun.star.awt.Point[][]) {
                    com.sun.star.awt.Point[][] lp = (com.sun.star.awt.Point[][]) pv;
                    if (lp.length > 0 && lp[0].length >= 2) {
                        com.sun.star.awt.Point a = lp[0][0], b = lp[0][lp[0].length - 1];
                        s.lineReverseX = b.X < a.X;
                        s.lineReverseY = b.Y < a.Y;
                    }
                }
            }
        } else if (si.supportsService("com.sun.star.drawing.RectangleShape")) {
            com.sun.star.text.XText st = UnoRuntime.queryInterface(com.sun.star.text.XText.class, obj);
            if (st != null && !st.getString().isEmpty()) return null;     // 글이 든 도형은 아직 쓰지 않는다
            Object fs = op.getPropertyValue("FillStyle");
            if (fs == com.sun.star.drawing.FillStyle.BITMAP) {
                // 그림으로 채운 사각형: 가져오기가 묶음 안의 그림을 이렇게 만든다 (ConvGraphics.insertPictureRECTAGLE).
                // 그림 개체로 쓴다
                s = new WShape(WShape.Kind.PICTURE);
                drawingShape = true;
                com.sun.star.graphic.XGraphic fg = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphic.class,
                        op.getPropertyValue("FillBitmap"));
                WShape ln = new WShape(WShape.Kind.RECT);
                drawLine(op, ln);
                if (pictureData(fg, s)) {
                    s.lineStyle = "NONE".equals(ln.lineStyle) ? "NONE" : "SOLID";
                    s.lineColor = ln.lineColor;
                    s.lineWidth = ln.lineWidth;
                } else {
                    // 그림을 얻지 못하면 (예: 채우기로 읽히지 않는 형식) 개체가 사라지지 않게 빈 사각형으로 쓴다
                    drawingShape = false;
                    s = ln;
                }
            } else {
                s = new WShape(WShape.Kind.RECT);
                drawLine(op, s);
                fill(op, s);
            }
        } else if (si.supportsService("com.sun.star.drawing.EllipseShape")) {
            // 타원, 또는 호/부채꼴/활 (가져오기 insertELLIPSE, insertARC 의 역)
            Object kind = op.getPropertyValue("CircleKind");
            if (kind == com.sun.star.drawing.CircleKind.FULL) {
                s = new WShape(WShape.Kind.ELLIPSE);
            } else {
                s = new WShape(WShape.Kind.ARC);
                s.arcType = kind == com.sun.star.drawing.CircleKind.SECTION ? "PIE"
                          : kind == com.sun.star.drawing.CircleKind.CUT ? "CHORD" : "NORMAL";
            }
            drawLine(op, s);
            fill(op, s);
        } else if (si.supportsService("com.sun.star.drawing.OpenBezierShape")
                || si.supportsService("com.sun.star.drawing.ClosedBezierShape")) {
            s = new WShape(WShape.Kind.CURVE);
            drawLine(op, s);
            if (si.supportsService("com.sun.star.drawing.ClosedBezierShape")) fill(op, s);
            if (!curvePoints(op, s)) return null;
        } else if (si.supportsService("com.sun.star.drawing.PolyPolygonShape")) {
            s = new WShape(WShape.Kind.POLYGON);
            drawLine(op, s);
            fill(op, s);
            if (!polygonPoints(op, s)) return null;
        } else {
            return null;
        }
        s.zOrder = shapeSeq++;
        int w, h;
        com.sun.star.drawing.XShape xs = UnoRuntime.queryInterface(com.sun.star.drawing.XShape.class, obj);
        if (s.kind == WShape.Kind.ARC) {
            // 가져오기는 호를 가로/세로 2배 크기의 타원으로 만든다
            w = xs.getSize().Width / 2;
            h = xs.getSize().Height / 2;
        } else if (drawingShape || (s.kind != WShape.Kind.PICTURE && s.kind != WShape.Kind.TEXTBOX)) {
            drawingShape = true;
            w = xs.getSize().Width;
            h = xs.getSize().Height;
        } else {
            w = AnyConverter.toInt(op.getPropertyValue("Width"));
            h = AnyConverter.toInt(op.getPropertyValue("Height"));
        }
        s.width = hwp(w);
        s.height = hwp(h);
        if (s.kind == WShape.Kind.POLYGON) {
            // 크기를 점의 경계 상자에 맞춘다. 가져오기(insertPOLYGON)는 점을 넣고 개체 크기로 늘이는데, LibreOffice 가
            // 돌려주는 크기는 점의 경계보다 1/100 mm 커서 다시 읽을 때마다 점이 조금씩 늘어났다
            int bw = 0, bh = 0;
            for (int[] pt : s.points) {
                bw = Math.max(bw, pt[0]);
                bh = Math.max(bh, pt[1]);
            }
            final int tol = 12;
            if (bw > 0 && Math.abs(bw - s.width) <= tol) s.width = bw;
            if (bh > 0 && Math.abs(bh - s.height) <= tol) s.height = bh;
        }
        place(op, s, drawingShape ? xs : null);
        if (s.kind == WShape.Kind.TEXTBOX) {
            textBox(obj, op, s, w);
        }
        if (s.kind == WShape.Kind.ARC) {
            arcAxes(op, s);
        }
        return s;
    }

    /**
     * 글자처럼 취급하는지, 위치, 배치. op: 놓이는 개체의 속성 (캡션 틀이면 틀).
     * drawing: 그리기 도형이면 그 도형 (위치를 도형 좌표에서 읽을 수 있다), 아니면 null
     */
    private static void place(XPropertySet op, WShape s, com.sun.star.drawing.XShape drawing) throws com.sun.star.uno.Exception {
        Object at = op.getPropertyValue("AnchorType");
        s.treatAsChar = at == com.sun.star.text.TextContentAnchorType.AS_CHARACTER;
        if (!s.treatAsChar) {
            position(op, s);
            if (drawing != null) {
                com.sun.star.drawing.XShape xs = drawing;
                // 그리기 도형은 위치 속성(HoriOrientPosition)이 0 이고 도형 좌표에만 위치가 들어 있을 수 있다
                // (가져오기가 묶음 안의 도형을 좌표로 놓는다). 그때는 도형 좌표를 쓴다
                com.sun.star.awt.Point pos = xs.getPosition();
                if (s.horzByOffset && s.horzOffset == 0 && pos.X != 0) s.horzOffset = hwp(pos.X);
                if (s.vertByOffset && s.vertOffset == 0 && pos.Y != 0) s.vertOffset = hwp(pos.Y);
            }
            // 가져오기는 거리가 0 이면 정렬(왼쪽/위)로 놓는다. 정렬된 개체는 서로 겹치지 않게 밀려나므로,
            // 거리 0 으로 놓인 개체는 1 HWPUNIT(= 0mm 로 읽힌다)으로 써서 다시 읽어도 거리로 놓이게 한다
            if (s.horzByOffset && s.horzOffset == 0) s.horzOffset = 1;
            if (s.vertByOffset && s.vertOffset == 0) s.vertOffset = 1;
            wrap(op, s);
        }
    }

    /**
     * 캡션 틀: 가져오기는 캡션이 있는 개체를 틀로 감싸고, 틀 첫 문단에 개체(글자처럼 취급)를, 그 뒤 문단들에 캡션 글을
     * 넣는다 (ConvGraphics.makeOuterFrame, addCaptionString). 캡션은 늘 개체 아래, 캡션 문단의 위 여백이 캡션 간격이다.
     * 이런 틀이면 안의 개체를 틀의 위치/배치로 놓고 캡션(hp:caption)을 붙여 돌려준다. 아니면 null
     */
    private WShape captionedShape(Object obj, XPropertySet op) throws com.sun.star.uno.Exception {
        com.sun.star.text.XTextFrame frame = UnoRuntime.queryInterface(com.sun.star.text.XTextFrame.class, obj);
        if (frame == null) return null;
        // makeOuterFrame: 테두리/채우기 없음, 캡션이 있으면 자동 높이
        if (!AnyConverter.toBoolean(op.getPropertyValue("FrameIsAutomaticHeight"))) return null;
        if (op.getPropertyValue("FillStyle") != com.sun.star.drawing.FillStyle.NONE) return null;
        Object tb = op.getPropertyValue("TopBorder");
        if (tb instanceof com.sun.star.table.BorderLine2 && ((com.sun.star.table.BorderLine2) tb).LineWidth > 0) return null;
        java.util.List<Object> elems = elementsOf(frame.getText());
        if (elems.size() < 2) return null;
        for (Object e : elems) {
            XServiceInfo esi = UnoRuntime.queryInterface(XServiceInfo.class, e);
            if (esi == null || !esi.supportsService("com.sun.star.text.Paragraph")) return null;
        }
        Object objPara = elems.get(0);
        java.util.List<Object> capParas = elems.subList(1, elems.size());
        XTextRange r0 = UnoRuntime.queryInterface(XTextRange.class, objPara);
        if (r0 == null || !r0.getString().trim().isEmpty()) return null;
        java.util.List<Object> contents = contentsOf(objPara);
        if (contents.size() != 1) return null;
        for (Object cpo : capParas) {
            if (!contentsOf(cpo).isEmpty()) return null;
        }
        WShape s = shape(contents.get(0));
        if (s == null || s.kind == WShape.Kind.TEXTBOX || !s.treatAsChar) return null;
        place(op, s, null);
        // 캡션 글
        boolean savedCell = inCellText, savedSingle = singleParaCell, savedFrame = inFrameText;
        inCellText = false;
        singleParaCell = false;
        inFrameText = true;
        for (Object cpo : capParas) {
            paragraph(cpo, s.captionParagraphs, false);
        }
        inCellText = savedCell;
        singleParaCell = savedSingle;
        inFrameText = savedFrame;
        XPropertySet cp = UnoRuntime.queryInterface(XPropertySet.class, capParas.get(0));
        s.captionGap = hwp(AnyConverter.toInt(cp.getPropertyValue("ParaTopMargin")));
        // 캡션 간격은 gap 으로 쓰므로 캡션 문단의 위 여백은 0 (가져오기는 위 여백을 gap 으로 덮어쓴다)
        for (WParagraph p : s.captionParagraphs) {
            PendingPr pp = pending.get(p);
            if (pp != null) {
                pp.pr.prev = 0;
            } else if (doc.paraPrList.get(p.paraPrId).prev != 0) {
                WParaPr z = copyParaPr(doc.paraPrList.get(p.paraPrId));
                z.prev = 0;
                p.paraPrId = doc.addParaPr(z);
            }
        }
        return s;
    }

    /** 문단의 개체: 글자처럼 놓인 개체와 문단에 붙은 개체 */
    private static java.util.List<Object> contentsOf(Object paraObj) throws com.sun.star.uno.Exception {
        java.util.List<Object> list = new java.util.ArrayList<Object>();
        XEnumeration en = UnoRuntime.queryInterface(XEnumerationAccess.class, paraObj).createEnumeration();
        while (en.hasMoreElements()) {
            Object portion = en.nextElement();
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, portion);
            if (!"Frame".equals(AnyConverter.toString(ps.getPropertyValue("TextPortionType")))) continue;
            com.sun.star.container.XContentEnumerationAccess cea =
                    UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, portion);
            if (cea == null) continue;
            XEnumeration ce = cea.createContentEnumeration("com.sun.star.text.TextContent");
            while (ce.hasMoreElements()) addOnce(list, ce.nextElement());
        }
        com.sun.star.container.XContentEnumerationAccess cea =
                UnoRuntime.queryInterface(com.sun.star.container.XContentEnumerationAccess.class, paraObj);
        if (cea != null) {
            XEnumeration ce = cea.createContentEnumeration("com.sun.star.text.TextContent");
            while (ce.hasMoreElements()) addOnce(list, ce.nextElement());
        }
        return list;
    }

    private static void addOnce(java.util.List<Object> list, Object o) {
        for (Object x : list) {
            if (UnoRuntime.areSame(x, o)) return;
        }
        list.add(o);
    }

    /** 채우기: 단색 또는 그러데이션 (가져오기 ConvGraphics.setFillStyle 의 역) */
    private static void fill(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        Object fs = op.getPropertyValue("FillStyle");
        if (fs == com.sun.star.drawing.FillStyle.SOLID) {
            s.fillColor = AnyConverter.toInt(op.getPropertyValue("FillColor")) & 0xFFFFFF;
        } else if (fs == com.sun.star.drawing.FillStyle.GRADIENT) {
            Object gv = op.getPropertyValue("FillGradient");
            if (!(gv instanceof com.sun.star.awt.Gradient)) return;
            com.sun.star.awt.Gradient g = (com.sun.star.awt.Gradient) gv;
            // 가져오기: LINEAR→LINEAR, RADIAL→RADIAL, CONICAL→AXIAL, SQUARE→RECT
            s.gradType = g.Style == com.sun.star.awt.GradientStyle.RADIAL ? "RADIAL"
                       : g.Style == com.sun.star.awt.GradientStyle.AXIAL ? "CONICAL"
                       : g.Style == com.sun.star.awt.GradientStyle.RECT ? "SQUARE" : "LINEAR";
            s.gradAngle = g.Angle / 10;             // 가져오기: 각도 x 10
            s.gradCenterX = hwp(g.XOffset);
            s.gradCenterY = hwp(g.YOffset);
            s.gradStep = g.StepCount;
            s.gradColors[0] = g.StartColor & 0xFFFFFF;
            s.gradColors[1] = g.EndColor & 0xFFFFFF;
        }
    }

    /**
     * 호의 중심과 두 축: 가져오기(ConvGraphics.insertARC)는 중심에서 제1축, 제2축을 본 각도를 시작/끝 각도로 쓴다.
     * 90도 단위의 1/4 호는 한글의 좌표 구성(개체 영역의 모서리와 변)으로, 그 밖의 각도는 방향만 맞춘다.
     */
    private static void arcAxes(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        int a1 = AnyConverter.toInt(op.getPropertyValue("CircleStartAngle")) / 100;
        int a2 = AnyConverter.toInt(op.getPropertyValue("CircleEndAngle")) / 100;
        a1 = ((a1 % 360) + 360) % 360;
        a2 = ((a2 % 360) + 360) % 360;
        int w = s.width, h = s.height;
        if (a1 % 90 == 0 && a2 == (a1 + 90) % 360) {
            switch (a1) {
            case 0:     arcSet(s, 0, h, w, h, 0, 0);   break;
            case 90:    arcSet(s, w, h, w, 0, 0, h);   break;
            case 180:   arcSet(s, w, 0, 0, 0, w, h);   break;
            default:    arcSet(s, 0, 0, 0, h, w, 0);   break;
            }
            return;
        }
        int[] d1 = arcDirection(a1), d2 = arcDirection(a2);
        arcSet(s, w, h, w + d1[0], h + d1[1], w + d2[0], h + d2[1]);
    }

    private static void arcSet(WShape s, int cx, int cy, int x1, int y1, int x2, int y2) {
        s.arcCenter[0] = cx;    s.arcCenter[1] = cy;
        s.arcAxis1[0] = x1;     s.arcAxis1[1] = y1;
        s.arcAxis2[0] = x2;     s.arcAxis2[1] = y2;
    }

    /** 각도(도) 방향의 벡터 (y 는 아래쪽이 +). 가져오기가 각도를 정수로 버리므로 버려도 같은 값이 되게 반 도 치우친다 */
    private static int[] arcDirection(int deg) {
        final int r = 100000;
        if (deg % 180 == 0) return new int[] { deg == 0 ? r : -r, 0 };
        double a = Math.toRadians(deg < 180 ? deg + 0.5 : deg - 0.5);
        return new int[] { (int) Math.round(r * Math.cos(a)), (int) Math.round(-r * Math.sin(a)) };
    }

    /** 다각형 꼭짓점: 도형 왼쪽 위 기준으로 옮겨 쓴다 (가져오기 insertPOLYGON 의 역) */
    private static boolean polygonPoints(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        Object v = op.getPropertyValue("PolyPolygon");
        if (!(v instanceof com.sun.star.awt.Point[][])) return false;
        com.sun.star.awt.Point[][] pp = (com.sun.star.awt.Point[][]) v;
        if (pp.length == 0 || pp[0].length < 2) return false;
        addPoints(s, pp[0], null, pp[0]);
        return true;
    }

    /** 곡선이 지나는 점: 조절점은 가져오기가 다시 계산하므로 지나는 점만 쓴다 (가져오기 insertCURVE 의 역) */
    private static boolean curvePoints(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        Object v = op.getPropertyValue("PolyPolygonBezier");
        if (!(v instanceof com.sun.star.drawing.PolyPolygonBezierCoords)) return false;
        com.sun.star.drawing.PolyPolygonBezierCoords c = (com.sun.star.drawing.PolyPolygonBezierCoords) v;
        if (c.Coordinates.length == 0 || c.Coordinates[0].length < 2) return false;
        addPoints(s, c.Coordinates[0], c.Flags[0], c.Coordinates[0]);
        return s.points.size() >= 2;
    }

    /** flags 가 있으면 NORMAL 인 점만 넣는다. 원점은 all 의 가장 왼쪽 위 */
    private static void addPoints(WShape s, com.sun.star.awt.Point[] pts, com.sun.star.drawing.PolygonFlags[] flags,
                                  com.sun.star.awt.Point[] all) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int i = 0; i < all.length; i++) {
            // 원점은 지나는 점 기준 (조절점은 가져오기가 다시 계산한다)
            if (flags != null && flags[i] == com.sun.star.drawing.PolygonFlags.CONTROL) continue;
            minX = Math.min(minX, all[i].X);
            minY = Math.min(minY, all[i].Y);
        }
        for (int i = 0; i < pts.length; i++) {
            // 조절점만 뺀다 (LibreOffice 는 지나는 점을 SMOOTH/SYMMETRIC 으로 돌려주기도 한다)
            if (flags != null && flags[i] == com.sun.star.drawing.PolygonFlags.CONTROL) continue;
            s.points.add(new int[] { hwp(pts[i].X - minX), hwp(pts[i].Y - minY) });
        }
    }

    /** 위치: 가져오기(ConvGraphics.setPositionLO)의 역. 기준 위치에서의 거리는 모두 위/왼쪽 기준으로 쓴다 */
    private static void position(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        short vo = AnyConverter.toShort(op.getPropertyValue("VertOrient"));
        short vrel = AnyConverter.toShort(op.getPropertyValue("VertOrientRelation"));
        int vpos = AnyConverter.toInt(op.getPropertyValue("VertOrientPosition"));
        s.vertRelTo = vrel == com.sun.star.text.RelOrientation.PAGE_FRAME ? "PAPER"
                    : vrel == com.sun.star.text.RelOrientation.PAGE_PRINT_AREA ? "PAGE" : "PARA";
        if (vo == com.sun.star.text.VertOrientation.NONE) {
            s.vertByOffset = true;
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
            s.horzByOffset = true;
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
            // 가져오기는 글상자/사각형의 "글 앞으로" 와 그림의 "어울림-큰 쪽" 을 DYNAMIC 으로 둔다
            if (s.kind == WShape.Kind.TEXTBOX || s.kind == WShape.Kind.RECT) {
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

    /** 그리기 개체의 선: 가져오기(ConvGraphics.setLineStyle)의 역 (선 종류, 색, 굵기) */
    private static void drawLine(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        Object ls = op.getPropertyValue("LineStyle");
        if (ls == com.sun.star.drawing.LineStyle.NONE) {
            s.lineStyle = "NONE";
            return;
        }
        s.lineColor = AnyConverter.toInt(op.getPropertyValue("LineColor")) & 0xFFFFFF;
        s.lineWidth = hwp(AnyConverter.toInt(op.getPropertyValue("LineWidth")));
        if (ls == com.sun.star.drawing.LineStyle.DASH) {
            String dn = op.getPropertySetInfo().hasPropertyByName("LineDashName")
                    ? AnyConverter.toString(op.getPropertyValue("LineDashName")) : "";
            // HWPX 선 이름은 그려지는 모양과 반대 (DOT = 긴 점선, DASH = 점선). LineStyle2.fromXml 의 역
            if ("Dot".equals(dn)) s.lineStyle = "DASH";
            else if ("Dash Dot".equals(dn)) s.lineStyle = "DASH_DOT";
            else if ("Dash Dot Dot".equals(dn)) s.lineStyle = "DASH_DOT_DOT";
            else s.lineStyle = "DOT";
        } else {
            s.lineStyle = "SOLID";
        }
    }

    /** 그림: 그래픽을 PNG 로 저장해 바이너리 항목으로 넣는다 */
    private boolean picture(XPropertySet op, WShape s) throws com.sun.star.uno.Exception {
        Object g = op.getPropertyValue("Graphic");
        com.sun.star.graphic.XGraphic graphic = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphic.class, g);
        if (!pictureData(graphic, s)) return false;
        pictureCrop(op, graphic, s);
        // 테두리 (가져오기 Transform.toBorderLine2: 실선, 색, 굵기)
        Object tb = op.getPropertySetInfo().hasPropertyByName("TopBorder") ? op.getPropertyValue("TopBorder") : null;
        if (tb instanceof com.sun.star.table.BorderLine2) {
            com.sun.star.table.BorderLine2 b = (com.sun.star.table.BorderLine2) tb;
            int bw = b.LineWidth > 0 ? b.LineWidth : b.OuterLineWidth;
            if (bw > 0 && b.LineStyle != com.sun.star.table.BorderLineStyle.NONE) {
                s.lineStyle = "SOLID";
                s.lineColor = b.Color & 0xFFFFFF;
                s.lineWidth = hwp(bw);
            }
        }
        return true;
    }

    /**
     * 자르기(GraphicCrop: 그림 크기 기준으로 네 변에서 잘라낸 길이)를 한글 자르기(imgClip: 원본 그림 크기 기준의 보이는
     * 사각형)로 바꾼다. 가져오기(ConvGraphics.cropOf)의 역
     */
    private void pictureCrop(XPropertySet op, com.sun.star.graphic.XGraphic graphic, WShape s) throws com.sun.star.uno.Exception {
        if (!op.getPropertySetInfo().hasPropertyByName("GraphicCrop")) return;
        Object co = op.getPropertyValue("GraphicCrop");
        if (!(co instanceof com.sun.star.text.GraphicCrop)) return;
        com.sun.star.text.GraphicCrop c = (com.sun.star.text.GraphicCrop) co;
        if (c.Left == 0 && c.Right == 0 && c.Top == 0 && c.Bottom == 0) return;
        // 다시 읽을 때의 그림 크기: PNG 로 바꿔 넣은 그림은 해상도 정보가 바뀌어 크기가 1/100 mm 쯤 달라진다
        com.sun.star.awt.Size g = writtenGraphicSize != null ? writtenGraphicSize : soffice.ConvGraphics.graphicSize100thMM(graphic);
        if (g == null || s.imgWidth <= 0 || s.imgHeight <= 0) return;
        double rx = (double) g.Width / s.imgWidth;     // 1/100 mm per 한글 단위 (가져오기와 같이 가로/세로 따로)
        double ry = (double) g.Height / s.imgHeight;
        // 원본 그림 크기 안으로 둔다. 벗어나면 가져오기(Ctrl_ShapePic.fixClip)가 잘못된 값으로 보고 자르기를 버린다
        // 가져오기는 자르기 = round(clip x 비율) 로 넣고 LibreOffice 는 twip 으로 저장한다. 다시 읽어 같은 값이 되는 clip 을 고른다
        final int gw = g.Width, gh = g.Height;
        s.clipLeft = fitClip(Math.max(0, c.Left), (int) Math.round(Math.max(0, c.Left) / rx), s.imgWidth,
                v -> (int) Math.round(v * rx));
        s.clipTop = fitClip(Math.max(0, c.Top), (int) Math.round(Math.max(0, c.Top) / ry), s.imgHeight,
                v -> (int) Math.round(v * ry));
        s.clipRight = fitClip(Math.max(0, c.Right), (int) Math.round((gw - Math.max(0, c.Right)) / rx), s.imgWidth,
                v -> (int) Math.round(Math.max(0, gw - v * rx)));
        s.clipBottom = fitClip(Math.max(0, c.Bottom), (int) Math.round((gh - Math.max(0, c.Bottom)) / ry), s.imgHeight,
                v -> (int) Math.round(Math.max(0, gh - v * ry)));
        if (s.clipRight <= s.clipLeft || s.clipBottom <= s.clipTop) {
            s.clipLeft = s.clipTop = 0;
            s.clipRight = s.clipBottom = -1;
        }
    }

    /**
     * 자르기 한 변의 clip 값: guess 주변에서 가져오기가 계산할 자르기(toCrop)를 twip 으로 맞춘 값이 want(지금 자르기)와 같은 값.
     * 없으면 guess. 0..max 안으로 둔다
     */
    private static int fitClip(int want, int guess, int max, java.util.function.IntUnaryOperator toCrop) {
        for (int d = 0; d <= 3; d++) {
            for (int sgn = 1; sgn >= -1; sgn -= 2) {
                int v = guess + sgn * d;
                if (v < 0 || v > max) continue;
                if (twipRound(toCrop.applyAsInt(v)) == want) return v;
                if (d == 0) break;
            }
        }
        return clamp(guess, 0, max);
    }

    /** pictureData 가 원래 형식이 아닌 형식으로 바꿔 넣은 그림의 크기 (1/100 mm). 원래 데이터를 넣었으면 null */
    private com.sun.star.awt.Size writtenGraphicSize;

    /** 바이트로 그래픽을 만들어 크기(1/100 mm)를 얻는다. 다시 읽을 때 가져오기가 보는 크기와 같다 */
    private com.sun.star.awt.Size writtenSize(byte[] data, String mime) {
        try {
            Object gp = ctx.getServiceManager().createInstanceWithContext("com.sun.star.graphic.GraphicProvider", ctx);
            com.sun.star.graphic.XGraphicProvider prov = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphicProvider.class, gp);
            com.sun.star.beans.PropertyValue[] pv = new com.sun.star.beans.PropertyValue[2];
            pv[0] = new com.sun.star.beans.PropertyValue();
            pv[0].Name = "InputStream";
            pv[0].Value = new com.sun.star.lib.uno.adapter.ByteArrayToXInputStreamAdapter(data);
            pv[1] = new com.sun.star.beans.PropertyValue();
            pv[1].Name = "MimeType";
            pv[1].Value = mime;
            return soffice.ConvGraphics.graphicSize100thMM(prov.queryGraphic(pv));
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.FINE, e.toString(), e);
            return null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * 그래픽을 바이너리 항목으로 넣고 원본 크기를 정한다. JPEG/PNG/GIF/WMF 는 원래 데이터 그대로 넣고, 그 밖의 형식(BMP 등:
     * LibreOffice 가 원래 데이터를 PNG 로 저장한다)은 PNG 로 바꾼다 (기존: 모두 PNG 로 바꿔 JPEG 문서가 몇 배로 커졌다)
     */
    private boolean pictureData(com.sun.star.graphic.XGraphic graphic, WShape s) throws com.sun.star.uno.Exception {
        writtenGraphicSize = null;
        if (graphic == null || ctx == null) return false;
        String mime = graphicMime(graphic);
        String ext = "image/jpeg".equals(mime) ? "jpg" : "image/png".equals(mime) ? "png"
                   : "image/bmp".equals(mime) ? "bmp" : "image/gif".equals(mime) ? "gif"
                   : "image/x-wmf".equals(mime) || "image/wmf".equals(mime) ? "wmf" : null;
        byte[] data = ext != null ? nativeGraphicData(graphic, ext) : null;
        if (data == null || data.length == 0) {
            data = graphicPng(graphic);
            ext = "png";
            writtenGraphicSize = writtenSize(data, "image/png");
        }
        if (data == null || data.length == 0) return false;
        s.binItemId = doc.addBinItem(ext, data);
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


    /**
     * 그래픽의 원래 데이터 (가져올 때의 파일 바이트). ODF 저장과 같이 그래픽 저장 처리기(GraphicExportHelper)로 임시 저장소에
     * 저장하면 LibreOffice 는 원래 데이터를 그대로 쓴다 (GraphicProvider.storeGraphic 은 다시 인코딩해 JPEG 화질이 떨어진다).
     * 저장된 파일의 확장자가 ext 와 다르면 null
     */
    private byte[] nativeGraphicData(com.sun.star.graphic.XGraphic graphic, String ext) {
        java.io.File tmp = null;
        try {
            tmp = java.io.File.createTempFile("H2O_GRF_", ".zip");
            if (!tmp.delete()) return null;
            String url = tmp.toURI().toURL().toExternalForm();
            com.sun.star.lang.XMultiComponentFactory smgr = ctx.getServiceManager();
            com.sun.star.lang.XSingleServiceFactory sf = UnoRuntime.queryInterface(com.sun.star.lang.XSingleServiceFactory.class,
                    smgr.createInstanceWithContext("com.sun.star.embed.StorageFactory", ctx));
            com.sun.star.embed.XStorage storage = UnoRuntime.queryInterface(com.sun.star.embed.XStorage.class,
                    sf.createInstanceWithArguments(new Object[] { url,
                            Integer.valueOf(com.sun.star.embed.ElementModes.READWRITE) }));
            Object helper = smgr.createInstanceWithArgumentsAndContext("com.sun.star.comp.Svx.GraphicExportHelper",
                    new Object[] { storage }, ctx);
            com.sun.star.document.XGraphicStorageHandler h =
                    UnoRuntime.queryInterface(com.sun.star.document.XGraphicStorageHandler.class, helper);
            if (h == null) return null;
            String name = h.saveGraphic(graphic);
            com.sun.star.lang.XComponent hc = UnoRuntime.queryInterface(com.sun.star.lang.XComponent.class, helper);
            if (hc != null) hc.dispose();
            com.sun.star.embed.XTransactedObject tr = UnoRuntime.queryInterface(com.sun.star.embed.XTransactedObject.class, storage);
            if (tr != null) tr.commit();
            UnoRuntime.queryInterface(com.sun.star.lang.XComponent.class, storage).dispose();
            if (name == null || name.isEmpty()) return null;
            String e = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            if ("jpeg".equals(e)) e = "jpg";
            if (!e.equals(ext)) return null;
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(tmp)) {
                java.util.zip.ZipEntry ze = z.getEntry(name);
                if (ze == null) return null;
                try (java.io.InputStream in = z.getInputStream(ze)) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    return bo.toByteArray();
                }
            }
        } catch (java.lang.Exception e) {
            log.log(Level.FINE, e.toString(), e);
            return null;
        } finally {
            if (tmp != null && tmp.exists() && !tmp.delete()) tmp.deleteOnExit();
        }
    }

    /** 그래픽의 원래 형식 (예: image/jpeg). 모르면 "" */
    private static String graphicMime(com.sun.star.graphic.XGraphic graphic) {
        try {
            XPropertySet gps = UnoRuntime.queryInterface(XPropertySet.class, graphic);
            if (gps != null && gps.getPropertySetInfo().hasPropertyByName("MimeType")) {
                Object m = gps.getPropertyValue("MimeType");
                if (m instanceof String) return ((String) m).toLowerCase();
            }
        } catch (com.sun.star.uno.Exception e) {
            log.log(Level.FINE, e.toString(), e);
        }
        return "";
    }

    /** 그래픽을 PNG 바이트로 (실패하면 null) */
    private byte[] graphicPng(com.sun.star.graphic.XGraphic graphic) throws com.sun.star.uno.Exception {
        return graphicData(graphic, "image/png");
    }

    /** 그래픽을 주어진 형식의 바이트로 (실패하면 null). 원래 형식과 같으면 LibreOffice 는 원래 데이터를 쓴다 */
    private byte[] graphicData(com.sun.star.graphic.XGraphic graphic, String mimeType) throws com.sun.star.uno.Exception {
        if (graphic == null || ctx == null) return null;
        Object gp = ctx.getServiceManager().createInstanceWithContext("com.sun.star.graphic.GraphicProvider", ctx);
        com.sun.star.graphic.XGraphicProvider prov = UnoRuntime.queryInterface(com.sun.star.graphic.XGraphicProvider.class, gp);
        // 임시 파일로 저장해 읽는다 (가져오기 ConvGraphics 와 같은 방법. 출력 스트림 어댑터로는 0 바이트가 나왔다)
        byte[] data;
        java.io.File tmp = null;
        try {
            tmp = java.io.File.createTempFile("H2O_EXP_", "." + mimeType.substring(mimeType.indexOf('/') + 1));
            com.sun.star.beans.PropertyValue[] pv = new com.sun.star.beans.PropertyValue[2];
            pv[0] = new com.sun.star.beans.PropertyValue();
            pv[0].Name = "URL";
            pv[0].Value = tmp.toURI().toURL().toExternalForm();
            pv[1] = new com.sun.star.beans.PropertyValue();
            pv[1].Name = "MimeType";
            pv[1].Value = mimeType;
            prov.storeGraphic(graphic, pv);
            data = java.nio.file.Files.readAllBytes(tmp.toPath());
        } catch (java.io.IOException e) {
            log.log(Level.WARNING, e.toString(), e);
            return null;
        } finally {
            if (tmp != null && !tmp.delete()) tmp.deleteOnExit();
        }
        return data;
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
        fill(op, s);
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
            boolean savedCell = inCellText, savedSingle = singleParaCell, savedFrame = inFrameText;
            inCellText = false;
            singleParaCell = false;
            inFrameText = true;
            text(frame.getText(), s.paragraphs, widthMm);
            // 가져오기는 표를 감싼 틀이 문단에 붙어 있으면 틀 안의 표 뒤에 빈칸 하나를 넣는다
            // (ConvTable.insertTable, TableFrame.MAKE). 그 빈칸을 뺀다
            int n = s.paragraphs.size();
            if (n >= 2 && s.paragraphs.get(n - 2).table != null
                    && op.getPropertyValue("AnchorType") == com.sun.star.text.TextContentAnchorType.AT_PARAGRAPH) {
                for (WParagraph.WRun r : s.paragraphs.get(n - 1).runs) {
                    if (r.shape != null || r.text == null || r.text.isEmpty()) continue;
                    if (r.text.charAt(0) == ' ') r.text = r.text.substring(1);
                    break;
                }
            }
            inCellText = savedCell;
            singleParaCell = savedSingle;
            inFrameText = savedFrame;
            // 가져오기(ConvGraphics)는 글상자 안의 개체(표 등)가 글상자보다 크면 일부만 보이게 만들며 표 셀 배경을
            // 투명으로 둔다 (TableFrame.MAKE_PART). 표를 감싼 틀은 표와 같은 크기인데 단위 변환으로 몇 HWPUNIT 작게
            // 나올 수 있다. 그 정도 차이는 개체 크기에 맞춘다 (기존: 다시 읽으면 셀 배경이 사라졌다)
            final int tol = 20;
            for (WParagraph p : s.paragraphs) {
                java.util.List<int[]> sizes = new java.util.ArrayList<int[]>();
                if (p.table != null) sizes.add(new int[] { p.table.width, p.table.height });
                for (WParagraph.WRun r : p.runs) {
                    if (r.shape != null) sizes.add(new int[] { r.shape.width, r.shape.height });
                }
                for (int[] wh : sizes) {
                    if (wh[0] > s.width && wh[0] - s.width <= tol) s.width = wh[0];
                    if (wh[1] > s.height && wh[1] - s.height <= tol) s.height = wh[1];
                }
            }
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

        // 표로 끝나는 머리말/꼬리말: LibreOffice 는 표 뒤에 빈 문단을 꼭 두지만, 가져오기는 표 문단의 끝으로 그 문단을
        // 만든다. 따로 쓰면 다시 읽을 때 "표 다음 첫 문단" 간격 보정이 붙어 머리말/꼬리말이 커지므로 쓰지 않는다
        int n = hf.paragraphs.size();
        if (n >= 2 && hf.paragraphs.get(n - 2).table != null) {
            WParagraph last = hf.paragraphs.get(n - 1);
            if (last.table == null && last.text().isEmpty() && !last.hasShape() && !hasAutoNum(last)) {
                hf.paragraphs.remove(n - 1);
            }
        }

        boolean empty = true;
        for (WParagraph p : hf.paragraphs) {
            // 표만 든 머리말/꼬리말도 내용이 있는 것이다 (표 안에 그림, 쪽 번호가 들어 있을 수 있다)
            if (!p.text().isEmpty() || p.hasShape() || hasAutoNum(p) || p.table != null) empty = false;
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

    private int footNoteSeq, endNoteSeq;

    /**
     * 각주/미주 (가져오기 ConvFootnote.insertFootnote 의 역). 가져오기는 자동 번호가 있으면 "번호)" 를 각주 표시로
     * 지정한다. 지정된 표시가 있으면 첫 문단 앞에 자동 번호를 넣어 다시 읽어도 같은 표시가 되게 한다.
     * 각주 문단은 표 셀처럼 마지막 문단에 문단 끝을 넣지 않는다.
     */
    private WNote note(Object fo) throws com.sun.star.uno.Exception {
        com.sun.star.text.XFootnote fn = UnoRuntime.queryInterface(com.sun.star.text.XFootnote.class, fo);
        XText ft = UnoRuntime.queryInterface(XText.class, fo);
        if (fn == null || ft == null) return null;
        XServiceInfo si = UnoRuntime.queryInterface(XServiceInfo.class, fo);
        WNote n = new WNote();
        n.endNote = si != null && si.supportsService("com.sun.star.text.Endnote");
        n.number = n.endNote ? ++endNoteSeq : ++footNoteSeq;
        String label = fn.getLabel();
        if (label != null && !label.isEmpty()) {
            n.autoNumber = true;
            String digits = label.replaceAll("[^0-9]", "");
            n.suffixChar = label.endsWith(")") ? ")" : label.substring(label.length() - 1).replaceAll("[0-9]", "");
            if (!digits.isEmpty()) n.number = Integer.parseInt(digits);
        }
        boolean savedCell = inCellText, savedSingle = singleParaCell;
        inCellText = true;
        singleParaCell = false;
        text(ft, n.paragraphs, bodyWidthMm100);
        inCellText = savedCell;
        singleParaCell = savedSingle;
        return n;
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
        // 기준 크기 = 동아시아 글꼴 크기. 서양 글꼴 크기는 상대 크기로 (가져오기: CharHeight = 기준 x 영문 상대 크기,
        // CharHeightAsian = 기준 x 한글 상대 크기)
        float ptAsian = ps.getPropertySetInfo().hasPropertyByName("CharHeightAsian")
                ? AnyConverter.toFloat(ps.getPropertyValue("CharHeightAsian")) : pt;
        if (ptAsian <= 0) ptAsian = pt;
        c.height = Math.round(ptAsian * 100);
        if (Math.abs(pt - ptAsian) > 0.01f) {
            c.relSizeLatin = Math.round(pt * 100f / ptAsian);
        }
        // 장평 (가져오기: CharScaleWidth = 한글 장평)
        if (ps.getPropertySetInfo().hasPropertyByName("CharScaleWidth")) {
            int sw = AnyConverter.toShort(ps.getPropertyValue("CharScaleWidth"));
            if (sw > 0) c.ratio = Math.max(50, Math.min(200, sw));
        }
        // 자간 (가져오기 getCharKerning: 1/100 mm = 기준 크기(pt) x 자간% x 0.35278)
        if (ps.getPropertySetInfo().hasPropertyByName("CharKerning")) {
            int k = AnyConverter.toShort(ps.getPropertyValue("CharKerning"));
            if (k != 0 && ptAsian > 0) {
                c.spacing = spacingFor(k, ptAsian);
            }
        }
        // 위/아래 첨자 (가져오기: CharEscapement +-14000 / 자동)
        short esc = AnyConverter.toShort(ps.getPropertyValue("CharEscapement"));
        c.superScript = esc > 0;
        c.subScript = esc < 0;
        // 음영 (가져오기: 음영 색을 CharBackColor 로)
        boolean backTransparent = !ps.getPropertySetInfo().hasPropertyByName("CharBackTransparent")
                || AnyConverter.toBoolean(ps.getPropertyValue("CharBackTransparent"));
        int back = AnyConverter.toInt(ps.getPropertyValue("CharBackColor"));
        if (!backTransparent && back != -1) {
            c.shadeColor = back & 0xFFFFFF;
        }
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
        if (ps.getPropertySetInfo().hasPropertyByName("CharShadowed")) {
            Object sh = ps.getPropertyValue("CharShadowed");
            c.shadow = sh instanceof Boolean && ((Boolean) sh);
        }

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
        origFont(ps, c, "TextUserDefinedAttributes");
        return c;
    }

    /**
     * 가져오기가 대체 글꼴로 바꾼 글자: 원래 글꼴/자간/장평으로 쓴다 (ConvPara.setOrigFontAttr). 대체 글꼴의 폭 보정 자간은
     * 글자 종류마다 달라 run 이 잘게 나뉘었는데, 원래 값으로 쓰면 하나로 합쳐진다
     */
    /** 가져오기가 없는 한컴 글꼴 대신 쓰는 글꼴 (FontWidthCorrection.SUBST_FONT_NAME 과 그 영문 이름) */
    private static boolean isSubstFont(String face) {
        return "나눔고딕".equals(face) || "NanumGothic".equals(face);
    }

    private boolean origFont(XPropertySet ps, WCharPr c, String propName) {
        try {
            if (!ps.getPropertySetInfo().hasPropertyByName(propName)) return false;
            com.sun.star.container.XNameContainer attrs = UnoRuntime.queryInterface(com.sun.star.container.XNameContainer.class,
                    ps.getPropertyValue(propName));
            if (attrs == null || !attrs.hasByName(soffice.ConvPara.ORIG_FONT_ATTR)) return false;
            Object o = attrs.getByName(soffice.ConvPara.ORIG_FONT_ATTR);
            if (!(o instanceof com.sun.star.xml.AttributeData)) return false;
            String[] v = ((com.sun.star.xml.AttributeData) o).Value.split("\\|", -1);
            if (v.length < 3 + WDocument.LANGS.length) return false;
            // 실제 글꼴이 기록한 대체 글꼴과 다르면 이 글자 모양의 기록이 아니다
            String subst = v[2 + WDocument.LANGS.length];
            if (ps.getPropertySetInfo().hasPropertyByName("CharFontNameAsian")
                    && !subst.equals(AnyConverter.toString(ps.getPropertyValue("CharFontNameAsian")))) return false;
            c.spacing = Integer.parseInt(v[0]);
            c.ratio = Integer.parseInt(v[1]);
            for (int l = 0; l < WDocument.LANGS.length; l++) {
                if (!v[2 + l].isEmpty()) c.fontRef[l] = addFont(l, v[2 + l]);
            }
            return true;
        } catch (com.sun.star.uno.Exception | RuntimeException e) {
            log.log(Level.FINE, e.toString(), e);
        }
        return false;
    }

    /**
     * 자간(%): 다시 읽었을 때 같은 CharKerning 이 되는 값. 가져오기는 round(크기 x 자간 x 0.35278) (1/100 mm) 로
     * 넣고, LibreOffice 는 자간을 twip 으로 저장해 읽을 때 1/100 mm 로 다시 바꾼다 (예: -40 → -23 twip → -41).
     * 이 과정을 거쳐 k 가 되는 자간을 고른다 (없으면 가장 가까운 값).
     */
    static int spacingFor(int k, float pt) {
        int naive = (int) Math.round(k * 100.0 / (pt * 35.278));
        int best = naive, bestErr = Integer.MAX_VALUE;
        for (int d = 0; d <= 3; d++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int sp = Math.max(-50, Math.min(50, naive + sgn * d));
                int k1 = (int) Math.round(pt * sp / 100.0 * 35.278);
                int tw = (int) Math.round(k1 * 1440.0 / 2540.0);
                int k2 = (int) Math.round(tw * 2540.0 / 1440.0);
                int err = Math.abs(k2 - k);
                if (err < bestErr) {
                    bestErr = err;
                    best = sp;
                }
            }
        }
        return best;
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
        // 다음 문단과 함께, 문단 보호 (가져오기 ConvPara.setParagraphProperties 의 역)
        p.keepWithNext = AnyConverter.toBoolean(pp.getPropertyValue("ParaKeepTogether"));
        p.keepLines = !AnyConverter.toBoolean(pp.getPropertyValue("ParaSplit"));
        // 줄 나눔 기준/최소 공백/원래 줄 간격 (가져오기 ConvPara.setParaBreakAttr)
        int origLineSpacingType = -1, origLineSpacing = 0;
        if (pp.getPropertySetInfo().hasPropertyByName("ParaUserDefinedAttributes")) {
            com.sun.star.container.XNameContainer ua = UnoRuntime.queryInterface(com.sun.star.container.XNameContainer.class,
                    pp.getPropertyValue("ParaUserDefinedAttributes"));
            if (ua != null && ua.hasByName(soffice.ConvPara.PARA_BREAK_ATTR)) {
                Object o = ua.getByName(soffice.ConvPara.PARA_BREAK_ATTR);
                if (o instanceof com.sun.star.xml.AttributeData) {
                    String[] v = ((com.sun.star.xml.AttributeData) o).Value.split("[|]");
                    try {
                        p.breakLatin = Integer.parseInt(v[0]);
                        p.breakNonLatin = Integer.parseInt(v[1]);
                        p.condense = Integer.parseInt(v[2]);
                        if (v.length >= 5) {
                            origLineSpacingType = Integer.parseInt(v[3]);
                            origLineSpacing = Integer.parseInt(v[4]);
                        }
                    } catch (RuntimeException e) {
                        log.log(Level.FINE, e.toString(), e);
                    }
                }
            }
        }
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
            int comp = Math.max(0, lsTop.Height - 100) * 10;
            int t = Math.max(0, top - comp);
            // LibreOffice 는 위 간격을 twip 으로 저장해 넣은 값과 1 정도 다르게 돌려준다 (190 → 191). 같은 값으로 저장되는
            // 가장 작은 값을 쓴다 (기존: 남은 1 을 위 간격으로 써서 다시 읽을 때 위 간격이 생겼다)
            for (int cand = 0; cand < t; cand++) {
                if (twipRound(cand + comp) == top) {
                    t = cand;
                    break;
                }
            }
            top = t;
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

        p.tabPrId = doc.addTabPr(tabPr(pp));
        heading(pp, p);
        // 문단 테두리/배경. 없으면 한글 기본(2)
        HwpxWriter.WBorderFill pbf = borderFill(pp, "ParaBackColor", "ParaBackTransparent");
        // 스타일에서 이어받은 배경은 문단에 쓰지 않는다 (스타일 문단 모양에 있다). 한글은 문단 자신의 문단 모양만
        // 그리므로, 원본에서 스타일에만 있던 배경이 문단에 생기지 않게 한다
        com.sun.star.beans.XPropertyState pst = UnoRuntime.queryInterface(com.sun.star.beans.XPropertyState.class, pp);
        if (pst != null && pst.getPropertyState("ParaBackColor") != com.sun.star.beans.PropertyState.DIRECT_VALUE) {
            pbf.fillColor = null;
        }
        if (pbf.fillColor == null) {
            pbf.fillColor = cursorBackColor(pp);
        }
        boolean anyLine = false;
        for (String t : pbf.type) if (!"NONE".equals(t)) anyLine = true;
        if (anyLine || pbf.fillColor != null) {
            p.borderFillId = doc.addBorderFill(pbf);
        }

        LineSpacing ls = (LineSpacing) AnyConverter.toObject(LineSpacing.class, pp.getPropertyValue("ParaLineSpacing"));
        switch (ls.Mode) {
        // 가져오기(ConvPara.setParagraphProperties) 의 역변환
        case LineSpacingMode.FIX:
            p.lineSpacingType = "FIXED";
            p.lineSpacing = 2 * (int) Math.ceil(ls.Height / 0.352778 - 1e-9);    // 가져오기: (short)(v/2*0.352778), 버림의 역
            break;
        case LineSpacingMode.MINIMUM:
            try (java.io.FileWriter dbg = new java.io.FileWriter("C:/Users/eband/AppData/Local/Temp/claude/6daf135a74c11a55/d2731b15-300e-4335-8638-6b2eaade6b18/scratchpad/ls.txt", true)) { // DBG
                dbg.write("MIN h=" + ls.Height + " single=" + singleParaCell + " ot=" + origLineSpacingType + " ov=" + origLineSpacing + "\n"); // DBG
            } catch (java.io.IOException e) { } // DBG
            if (singleParaCell && Math.abs(ls.Height - 100) <= 2) {
                // 가져오기는 문단 하나뿐인 셀의 줄 간격을 "최소 100" 으로 바꾼다 (ConvTable). 가져오기가 남긴 원래 값을 쓰고,
                // 없으면 한글 기본값 PERCENT 160 으로 추정한다 (기존: 늘 160 이라 100% 인 표가 한글에서 높아졌다)
                if (origLineSpacingType >= 0 && origLineSpacing > 0) {
                    p.lineSpacingType = origLineSpacingType == 1 ? "FIXED" : origLineSpacingType == 2 ? "BETWEEN_LINES"
                                      : origLineSpacingType == 3 ? "AT_LEAST" : "PERCENT";
                    p.lineSpacing = origLineSpacing;
                } else {
                    p.lineSpacingType = "PERCENT";
                    p.lineSpacing = 160;
                }
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

    /** 쪽 스타일로 지금 구역의 용지 크기/여백, 머리말/꼬리말을 정한다 */
    private void page(String pageStyle) {
        try {
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
