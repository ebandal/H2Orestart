package harness;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import HwpDoc.HwpDocInfo;
import HwpDoc.HwpSection;
import HwpDoc.HwpxFile;
import HwpDoc.IContext;
import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_Character;
import HwpDoc.paragraph.Ctrl_Table;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.ParaText;
import HwpDoc.paragraph.TblCell;

/**
 * 왕복 비교: 원본 HWPX 와, 그것을 LibreOffice 로 열어 다시 HWPX 로 저장한 결과를 비교한다.
 * 모델의 ID 가 아니라 값으로 비교한다 (내보내기는 모양 목록을 새로 번호 매긴다).
 *  - 문단을 펼친다: 본문 문단, 표 셀의 문단 (지금 내보내기는 표를 셀 문단으로 쓴다)
 *  - 문단 텍스트로 LCS 정렬해 짝을 짓고, 짝 문단의 문단 모양과 글자별 글자 모양을 비교한다
 * 사용법: RoundTripCompare <pairs.tsv (원본\t결과)> <출력 폴더>
 */
public class RoundTripCompare {

    /** HWPUNIT 허용 오차 */
    static final int TOL = 8;      // 가져오기의 /2 정수 절사와 1/100mm 변환을 두 번 거친다

    static class Para {
        String text;
        int depth;         // 0: 본문, 1 이상: 표 셀 깊이
        HwpRecord_ParaShape ps;
        String tabs = "";   // 탭 정의 (자동 탭, 위치/종류)
        List<HwpRecord_CharShape> chars = new ArrayList<HwpRecord_CharShape>();   // 글자별
    }

    /** key -> [count, docs, example] */
    static final Map<String, Object[]> agg = new TreeMap<String, Object[]>();

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        File outDir = new File(args[1]);
        new File(outDir, "docs").mkdirs();
        PrintWriter summary = writer(new File(outDir, "summary.tsv"));
        summary.println("doc\torigParas\toutParas\tmatched\tmissing\textra\tdiffs\tstatus");
        int n = 0;
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length < 2) continue;
            n++;
            String name = new File(p[0]).getName();
            try {
                List<Para> a = flatten(p[0]);
                List<Para> b = flatten(p[1]);
                PrintWriter doc = writer(new File(outDir, "docs/" + String.format("%03d_", n) + safe(name) + ".txt"));
                doc.println("# orig: " + p[0]);
                doc.println("# out : " + p[1]);
                int[] r = compare(name, a, b, doc);
                doc.close();
                summary.println(name + "\t" + a.size() + "\t" + b.size() + "\t" + r[0] + "\t" + r[1] + "\t" + r[2] + "\t" + r[3] + "\tOK");
                System.out.println("[" + n + "] " + name + " matched=" + r[0] + "/" + a.size() + " diffs=" + r[3]);
            } catch (Throwable t) {
                summary.println(name + "\t\t\t\t\t\t\tERROR " + t);
                System.out.println("[" + n + "] " + name + " ERROR " + t);
            }
        }
        summary.close();
        PrintWriter d = writer(new File(outDir, "diffs.tsv"));
        d.println("count\tdocs\tkey\texample");
        List<Map.Entry<String, Object[]>> es = new ArrayList<Map.Entry<String, Object[]>>(agg.entrySet());
        es.sort((x, y) -> Integer.compare((Integer) y.getValue()[0], (Integer) x.getValue()[0]));
        for (Map.Entry<String, Object[]> e : es) {
            d.println(e.getValue()[0] + "\t" + ((java.util.Set<?>) e.getValue()[1]).size() + "\t" + e.getKey() + "\t" + e.getValue()[2]);
        }
        d.close();
        System.out.println("see " + new File(outDir, "diffs.tsv"));
    }

    static String safe(String s) {
        return s.replaceAll("[^0-9A-Za-z가-힣._-]", "_");
    }

    static PrintWriter writer(File f) throws Exception {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
    }

    static List<Para> flatten(String path) throws Exception {
        final HwpxFile hwpx = new HwpxFile(path);
        List<Para> out = new ArrayList<Para>();
        try {
            hwpx.open(new IContext() {
                @Override public HwpxFile getHwpx() { return hwpx; }
            });
            HwpDocInfo info = hwpx.getDocInfo();
            for (HwpSection s : hwpx.getSections()) {
                for (HwpParagraph p : s.paraList) {
                    addPara(info, p, out, 0);
                }
            }
        } finally {
            hwpx.close();
        }
        return out;
    }

    static void addPara(HwpDocInfo info, HwpParagraph p, List<Para> out, int depth) {
        Para para = new Para();
        para.depth = depth;
        para.ps = p.paraShapeID >= 0 && p.paraShapeID < info.paraShapeList.size()
                ? (HwpRecord_ParaShape) info.paraShapeList.get(p.paraShapeID) : null;
        if (para.ps != null && para.ps.tabDef >= 0 && para.ps.tabDef < info.tabDefList.size()) {
            HwpDoc.HwpElement.HwpRecord_TabDef td = (HwpDoc.HwpElement.HwpRecord_TabDef) info.tabDefList.get(para.ps.tabDef);
            StringBuilder ts = new StringBuilder();
            ts.append((td.attr & 1) != 0 ? "autoL " : "").append((td.attr & 2) != 0 ? "autoR " : "");
            if (td.tabs != null) {
                for (HwpDoc.HwpElement.HwpRecord_TabDef.Tab t : td.tabs) ts.append(t.pos).append(t.type).append(' ');
            }
            para.tabs = ts.toString().trim();
        }
        StringBuilder sb = new StringBuilder();
        List<Ctrl_Table> tables = new ArrayList<Ctrl_Table>();
        List<HwpDoc.paragraph.Ctrl_Common> shapes = new ArrayList<HwpDoc.paragraph.Ctrl_Common>();
        List<Ctrl> others = new ArrayList<Ctrl>();      // 머리말/꼬리말, 쪽 번호 위치
        if (p.p != null) {
            for (Ctrl c : p.p) {
                if (c instanceof ParaText) {
                    ParaText t = (ParaText) c;
                    HwpRecord_CharShape cs = charShape(info, t.charShapeId);
                    for (int i = 0; i < t.text.length(); i++) {
                        sb.append(t.text.charAt(i));
                        para.chars.add(cs);
                    }
                } else if (c instanceof Ctrl_Character && ((Ctrl_Character) c).ctrlChar != Ctrl_Character.CtrlCharType.PARAGRAPH_BREAK) {
                    // 줄바꿈, 묶음/고정폭 빈칸, 하이픈은 LibreOffice 를 거쳐 문자로 돌아온다
                    switch (((Ctrl_Character) c).ctrlChar) {
                    case LINE_BREAK:  sb.append('\n');     break;
                    case HARD_SPACE:  sb.append(' '); break;
                    default:          sb.append('‑'); break;
                    }
                    para.chars.add(charShape(info, ((Ctrl_Character) c).charShapeId));
                } else if (c instanceof HwpDoc.paragraph.Ctrl_AutoNumber) {
                    // 자동 번호(쪽 번호 등)는 글자 하나로
                    sb.append('#');
                    para.chars.add(null);
                } else if (c instanceof HwpDoc.paragraph.Ctrl_HeadFoot || c instanceof HwpDoc.paragraph.Ctrl_PageNumPos
                        || c instanceof HwpDoc.paragraph.Ctrl_SectionDef) {
                    others.add(c);
                } else if (c instanceof Ctrl_Table) {
                    tables.add((Ctrl_Table) c);
                } else if (c instanceof HwpDoc.paragraph.Ctrl_ShapePic || c instanceof HwpDoc.paragraph.Ctrl_ShapeLine
                        || (c instanceof HwpDoc.paragraph.Ctrl_GeneralShape && "cer$".equals(c.ctrlId))) {
                    shapes.add((HwpDoc.paragraph.Ctrl_Common) c);
                }
            }
        }
        para.text = sb.toString();
        out.add(para);
        // 머리말/꼬리말: 종류와 적용 쪽을 가상 문단으로, 내용 문단은 이어서. 쪽 번호 위치도 가상 문단으로
        for (Ctrl c : others) {
            Para sig = new Para();
            sig.depth = depth;
            if (c instanceof HwpDoc.paragraph.Ctrl_HeadFoot) {
                HwpDoc.paragraph.Ctrl_HeadFoot hf = (HwpDoc.paragraph.Ctrl_HeadFoot) c;
                sig.text = (hf.isHeader ? "§HEADER " : "§FOOTER ") + hf.whichPage;
                out.add(sig);
                if (hf.paras != null) {
                    for (HwpParagraph hp : hf.paras) {
                        addPara(info, hp, out, depth + 1);
                    }
                }
            } else if (c instanceof HwpDoc.paragraph.Ctrl_SectionDef) {
                // 구역 시작: 용지 크기/방향/여백
                HwpDoc.section.Page pg = ((HwpDoc.paragraph.Ctrl_SectionDef) c).page;
                sig.text = pg == null ? "§SECTION" : "§SECTION " + pg.width + "x" + pg.height + (pg.landscape ? " landscape" : "")
                        + " L" + pg.marginLeft + " R" + pg.marginRight + " T" + pg.marginTop + " B" + pg.marginBottom
                        + " H" + pg.marginHeader + " F" + pg.marginFooter;
                out.add(sig);
            } else {
                HwpDoc.paragraph.Ctrl_PageNumPos pn = (HwpDoc.paragraph.Ctrl_PageNumPos) c;
                sig.text = "§PAGENUM " + pn.pos + " " + pn.numShape + " [" + pn.constantDash + "]";
                out.add(sig);
            }
        }
        // 그림/글상자: 종류, 크기, 위치, 배치를 가상 문단으로. 글상자 문단은 이어서
        for (HwpDoc.paragraph.Ctrl_Common sh : shapes) {
            Para sig = new Para();
            boolean pic = sh instanceof HwpDoc.paragraph.Ctrl_ShapePic;
            boolean line = sh instanceof HwpDoc.paragraph.Ctrl_ShapeLine;
            boolean box = !pic && !line && sh.paras != null && !sh.paras.isEmpty();
            StringBuilder ss = new StringBuilder(pic ? "§PIC " : line ? "§LINE " : box ? "§BOX " : "§RECT ");
            ss.append(sh.width).append('x').append(sh.height);
            if (sh.treatAsChar) {
                ss.append(" char");
            } else {
                ss.append(' ').append(sh.textWrap).append(' ').append(sh.vertRelTo).append('/').append(sh.vertAlign)
                  .append('/').append(sh.vertOffset).append(' ').append(sh.horzRelTo).append('/').append(sh.horzAlign)
                  .append('/').append(sh.horzOffset);
            }
            sig.text = ss.toString();
            sig.depth = depth;
            out.add(sig);
            if (box) {
                for (HwpParagraph cp : sh.paras) {
                    addPara(info, cp, out, depth + 1);
                }
            }
        }
        // 내보내기(LoToHwpx) 처럼 표 셀 문단을 이어서
        for (Ctrl_Table t : tables) {
            if (t.cells == null) continue;
            // 표 구조를 가상 문단으로 (다르면 짝 없는 문단으로 드러난다)
            Para sig = new Para();
            StringBuilder ts = new StringBuilder("§TABLE " + t.nRows + "x" + t.nCols + " ");
            for (TblCell cell : t.cells) {
                ts.append(cell.rowAddr).append(',').append(cell.colAddr).append(',')
                  .append(cell.rowSpan).append(',').append(cell.colSpan).append(';');
            }
            sig.text = ts.toString();
            out.add(sig);
            for (TblCell cell : t.cells) {
                if (cell.paras == null) continue;
                for (HwpParagraph cp : cell.paras) {
                    addPara(info, cp, out, depth + 1);
                }
            }
        }
    }

    static HwpRecord_CharShape charShape(HwpDocInfo info, int id) {
        return id >= 0 && id < info.charShapeList.size() ? (HwpRecord_CharShape) info.charShapeList.get(id) : null;
    }

    /** @return {matched, missing, extra, diffs} */
    static int[] compare(String doc, List<Para> a, List<Para> b, PrintWriter out) {
        int n = a.size(), m = b.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = norm(a.get(i).text).equals(norm(b.get(j).text)) ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0, j = 0, matched = 0, missing = 0, extra = 0, diffs = 0;
        while (i < n || j < m) {
            if (i < n && j < m && norm(a.get(i).text).equals(norm(b.get(j).text))) {
                diffs += comparePara(doc, i, a.get(i), b.get(j), out);
                matched++;
                i++;
                j++;
            } else if (j < m && (i >= n || lcs[i][j + 1] >= lcs[i + 1][j])) {
                extra++;
                add(doc, "para#extra", "", b.get(j).text, out, "out[" + j + "]");
                j++;
            } else {
                missing++;
                add(doc, "para#missing", a.get(i).text, "", out, "orig[" + i + "]");
                i++;
            }
        }
        return new int[] { matched, missing, extra, diffs };
    }

    static String norm(String s) {
        return s == null ? "" : s;
    }

    static int comparePara(String doc, int idx, Para a, Para b, PrintWriter out) {
        int d = 0;
        String w = "orig[" + idx + "]" + (a.depth > 0 ? "{cell" + a.depth + "}" : "") + " " + abbreviate(a.text);
        if (a.ps != null && b.ps != null) {
            d += cmp(doc, "ParaShape.align", String.valueOf(a.ps.align), String.valueOf(b.ps.align), out, w);
            d += cmpInt(doc, "ParaShape.marginLeft", a.ps.marginLeft, b.ps.marginLeft, out, w);
            d += cmpInt(doc, "ParaShape.marginRight", a.ps.marginRight, b.ps.marginRight, out, w);
            d += cmpInt(doc, "ParaShape.indent", a.ps.indent, b.ps.indent, out, w);
            d += cmpInt(doc, "ParaShape.marginPrev", a.ps.marginPrev, b.ps.marginPrev, out, w);
            d += cmpInt(doc, "ParaShape.marginNext", a.ps.marginNext, b.ps.marginNext, out, w);
            d += cmp(doc, "ParaShape.lineSpacingType", String.valueOf(a.ps.lineSpacingType), String.valueOf(b.ps.lineSpacingType), out, w);
            d += cmp(doc, "ParaShape.tabs", a.tabs, b.tabs, out, w);
            if (a.ps.lineSpacingType == b.ps.lineSpacingType) {
                d += cmpInt(doc, "ParaShape.lineSpacing", a.ps.lineSpacing, b.ps.lineSpacing, out, w);
            }
        }
        // 글자 모양: 같은 차이가 문단 안에서 반복되면 한 번만 센다
        Map<String, String[]> seen = new LinkedHashMap<String, String[]>();
        for (int k = 0; k < a.chars.size() && k < b.chars.size(); k++) {
            HwpRecord_CharShape x = a.chars.get(k), y = b.chars.get(k);
            if (x == null || y == null) continue;
            charDiff(seen, "CharShape.height", String.valueOf(x.height), String.valueOf(y.height));
            charDiff(seen, "CharShape.bold", String.valueOf(x.bold), String.valueOf(y.bold));
            charDiff(seen, "CharShape.italic", String.valueOf(x.italic), String.valueOf(y.italic));
            charDiff(seen, "CharShape.underline", String.valueOf(x.underline != null && !"NONE".equals(x.underline.name())),
                    String.valueOf(y.underline != null && !"NONE".equals(y.underline.name())));
            charDiff(seen, "CharShape.strikeOut", String.valueOf(x.strikeOut != 0), String.valueOf(y.strikeOut != 0));
            charDiff(seen, "CharShape.textColor", String.format("%06X", x.textColor & 0xFFFFFF), String.format("%06X", y.textColor & 0xFFFFFF));
            charDiff(seen, "CharShape.fontName[hangul]", x.fontName[0], y.fontName[0]);
            charDiff(seen, "CharShape.fontName[latin]", x.fontName[1], y.fontName[1]);
            charDiff(seen, "CharShape.ratio[hangul]", String.valueOf(x.ratio[0]), String.valueOf(y.ratio[0]));
            charDiff(seen, "CharShape.spacing[hangul]", String.valueOf(x.spacing[0]), String.valueOf(y.spacing[0]));
            charDiff(seen, "CharShape.relSize[latin]", String.valueOf(x.relSize[1]), String.valueOf(y.relSize[1]));
            charDiff(seen, "CharShape.script", (x.superScript ? "sup" : "") + (x.subScript ? "sub" : ""), (y.superScript ? "sup" : "") + (y.subScript ? "sub" : ""));
            charDiff(seen, "CharShape.shadeColor", String.format("%08X", x.shadeColor), String.format("%08X", y.shadeColor));
            charDiff(seen, "CharShape.underlineShape", String.valueOf(x.underline != null && !"NONE".equals(x.underline.name()) ? x.underlineShape : null),
                    String.valueOf(y.underline != null && !"NONE".equals(y.underline.name()) ? y.underlineShape : null));
        }
        for (Map.Entry<String, String[]> e : seen.entrySet()) {
            d += cmp(doc, e.getKey(), e.getValue()[0], e.getValue()[1], out, w);
        }
        return d;
    }

    static void charDiff(Map<String, String[]> seen, String key, String a, String b) {
        if (a == null ? b == null : a.equals(b)) return;
        String k = key + "|" + a + "|" + b;
        if (!seen.containsKey(k)) seen.put(k, new String[] { a, b });
    }

    static int cmpInt(String doc, String key, int a, int b, PrintWriter out, String where) {
        if (Math.abs(a - b) <= TOL) return 0;
        add(doc, key, String.valueOf(a), String.valueOf(b), out, where);
        return 1;
    }

    static int cmp(String doc, String key, String a, String b, PrintWriter out, String where) {
        if (a == null ? b == null : a.equals(b)) return 0;
        String k = key.contains("|") ? key.substring(0, key.indexOf('|')) : key;
        add(doc, k, a, b, out, where);
        return 1;
    }

    static void add(String doc, String key, String a, String b, PrintWriter out, String where) {
        out.println(key + "\t" + where + "\t" + abbreviate(a) + "\t" + abbreviate(b));
        Object[] v = agg.get(key);
        if (v == null) {
            v = new Object[] { 0, new java.util.HashSet<String>(), doc + " " + where + " : " + abbreviate(a) + " -> " + abbreviate(b) };
            agg.put(key, v);
        }
        v[0] = (Integer) v[0] + 1;
        @SuppressWarnings("unchecked")
        java.util.Set<String> docs = (java.util.Set<String>) v[1];
        docs.add(doc);
    }

    static String abbreviate(String s) {
        if (s == null) return "null";
        s = s.replace("\t", "\\t").replace("\n", "\\n");
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
