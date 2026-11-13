/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecordTypes.LineStyle2;

/**
 * 두 모델 객체 그래프(HWP 파서 결과 vs HWPX 파서 결과)를 리플렉션으로 나란히 순회하며 다른 값을 수집한다.
 * - 리스트/배열은 같은 인덱스끼리 비교하고, 길이가 다르면 "#size" 차이를 기록한다
 * - Map 은 순서대로 값끼리 비교한다 (HWP/HWPX 의 키 체계가 다를 수 있음)
 * - 차이 키: "선언클래스.필드" (인덱스 제외) → 말뭉치 단위 집계에 사용
 */
public class ModelDiff {

    /** 비교하지 않는 필드 (파서 경로에 따라 원래 다른 값) */
    static final Set<String> IGNORE_FIELDS = new HashSet<String>(Arrays.asList(
            "size",          // 바이너리 레코드 크기
            "level",         // 바이너리 레코드 계층
            "hanType",       // HWP / HWPX 구분
            "recordList",    // 바이너리 원본 레코드 목록
            "fullfilled",
            "parent",        // 상위 객체 역참조 (HwpDocInfo, HwpParagraph)
            "parentHwp", "parentHwpx", "hwp", "hwpx", "docInfo"
    ));

    /** 비교하지 않는 차이 키 (형식마다 원래 표현이 다른 값) */
    static final Set<String> IGNORE_KEYS = new HashSet<String>(Arrays.asList(
            "HwpElement.HwpRecord_BinData.aPath",       // BIN0001.jpg  vs BinData/image1.jpg
            "HwpElement.HwpRecord_BinData.itemId",      // "1"          vs "image1"
            "HwpElement.HwpRecord_BinData.binDataID",
            "HwpElement.HwpRecord_BinData.compressed",
            "HwpElement.HwpRecord_BinData.state",
            "HwpElement.HwpRecord_ParaShape.attrBits",    // HWP 원본 비트 (HWPX 에는 없음)
            "HwpElement.HwpRecord_Numbering$ParaHeadInfo.attrBits"
    ));

    /**
     * ID 참조 필드 -> {참조하는 목록, ID 기준값}. 비교할 때 ID 값 대신 가리키는 객체의 내용을 비교한다.
     * 기준값은 변환기(WriterContext)의 접근 방식과 같다 (borderFill 은 1부터, 나머지는 0부터).
     */
    static final Map<String, String[]> REFS = new java.util.HashMap<String, String[]>();
    static {
        ref("paragraph.HwpParagraph.paraShapeID", "paraShapeList", 0);
        ref("paragraph.HwpParagraph.paraStyleID", "styleList", 0);
        ref("paragraph.ParaText.charShapeId", "charShapeList", 0);
        ref("paragraph.Ctrl_Character.charShapeId", "charShapeList", 0);
        ref("paragraph.CharShape.charShapeID", "charShapeList", 0);
        ref("HwpElement.HwpRecord_Style.paraShape", "paraShapeList", 0);
        ref("HwpElement.HwpRecord_Style.charShape", "charShapeList", 0);
        ref("HwpElement.HwpRecord_Style.nextStyle", "styleList", 0);
        ref("HwpElement.HwpRecord_Numbering$ParaHeadInfo.charShape", "charShapeList", 0);
        ref("HwpElement.HwpRecord_ParaShape.tabDef", "tabDefList", 0);
        ref("HwpElement.HwpRecord_ParaShape.borderFill", "borderFillList", 1);
        ref("HwpElement.HwpRecord_CharShape.borderFillIDRef", "borderFillList", 1);
        ref("paragraph.TblCell.borderFill", "borderFillList", 1);
        ref("paragraph.Ctrl_Table.borderFillID", "borderFillList", 1);
        ref("section.PageBorderFill.borderFill", "borderFillList", 1);
        ref("paragraph.Ctrl_ShapePic.binDataID", "binDataList", 0);
        ref("paragraph.Ctrl_ShapeOle.binDataID", "binDataList", 0);
    }

    private static void ref(String key, String list, int base) {
        REFS.put(key, new String[] { list, String.valueOf(base) });
    }

    /** ID 로 참조되는 목록은 위치끼리 비교하지 않는다 (참조를 따라가 내용으로 비교됨) */
    static final Set<String> REF_LISTS = new HashSet<String>(Arrays.asList(
            "HwpDocInfo.charShapeList", "HwpDocInfo.paraShapeList", "HwpDocInfo.borderFillList",
            "HwpDocInfo.styleList", "HwpDocInfo.tabDefList", "HwpDocInfo.faceNameList", "HwpDocInfo.binDataList",
            "HwpDocInfo.faceNameOffset"
    ));

    /** 글자처럼 취급하는 개체에서 비교하지 않는 필드 */
    static final Set<String> INLINE_IGNORED = new HashSet<String>(Arrays.asList(
            "textWrap", "textFlow", "allowOverlap", "flowWithText", "vertRelTo", "horzRelTo", "vertAlign", "horzAlign"
    ));

    /** 한쪽 모델에서 ID 를 객체로 바꾼다. 찾지 못하면 null */
    public interface Resolver {
        Object resolve(String list, int base, Object rawId);
    }

    public static class Diff {
        public final String key;     // Class.field
        public final String path;    // 전체 경로
        public final String left;    // HWP
        public final String right;   // HWPX
        Diff(String key, String path, String left, String right) {
            this.key = key; this.path = path; this.left = left; this.right = right;
        }
    }

    private final List<Diff> diffs = new ArrayList<Diff>();
    private final IdentityHashMap<Object, Boolean> visiting = new IdentityHashMap<Object, Boolean>();
    private final int maxDiffs;
    private long compared;
    private final Resolver leftResolver, rightResolver;
    /** 이미 비교한 (왼쪽 객체, 오른쪽 객체) 쌍 - 같은 참조 대상을 반복해서 비교하지 않는다 */
    private final IdentityHashMap<Object, IdentityHashMap<Object, Boolean>> comparedPairs =
            new IdentityHashMap<Object, IdentityHashMap<Object, Boolean>>();

    public ModelDiff(int maxDiffs) {
        this(maxDiffs, null, null);
    }

    public ModelDiff(int maxDiffs, Resolver left, Resolver right) {
        this.maxDiffs = maxDiffs;
        this.leftResolver = left;
        this.rightResolver = right;
    }

    private boolean firstTime(Object a, Object b) {
        IdentityHashMap<Object, Boolean> m = comparedPairs.get(a);
        if (m == null) {
            m = new IdentityHashMap<Object, Boolean>();
            comparedPairs.put(a, m);
        }
        return m.put(b, Boolean.TRUE) == null;
    }

    public List<Diff> getDiffs() { return diffs; }
    public long getCompared() { return compared; }

    public void compare(String path, Object a, Object b) {
        compare(path, "", a, b, 0);
    }

    private void add(String key, String path, Object a, Object b) {
        if (diffs.size() < maxDiffs) {
            diffs.add(new Diff(key, path, show(a), show(b)));
        }
    }

    private static String show(Object v) {
        if (v == null) return "null";
        Class<?> c = v.getClass();
        if (ModelDumper.isScalar(c)) {
            String s = ModelDumper.scalar(v);
            return s.length() > 80 ? s.substring(0, 80) + "…" : s;
        }
        if (c == byte[].class) return "bytes[" + ((byte[]) v).length + "] " + ModelDumper.hash((byte[]) v);
        if (c.isArray()) return ModelDumper.simpleName(c.getComponentType()) + "[" + Array.getLength(v) + "]";
        if (v instanceof Collection) return "size=" + ((Collection<?>) v).size();
        if (v instanceof Map) return "size=" + ((Map<?, ?>) v).size();
        return "<" + ModelDumper.simpleName(c) + ">";
    }

    private void compare(String path, String key, Object a, Object b, int depth) {
        compared++;
        if (depth > 64) return;
        if (a == null && b == null) return;
        if (a == null || b == null) {
            add(key + "#null", path, a, b);
            return;
        }
        Class<?> ca = a.getClass(), cb = b.getClass();
        boolean sa = ModelDumper.isScalar(ca), sb = ModelDumper.isScalar(cb);
        if (sa || sb) {
            if (!(sa && sb) || !scalarEquals(a, b)) add(key, path, a, b);
            return;
        }
        if (ca == byte[].class || cb == byte[].class) {
            if (!(a instanceof byte[] && b instanceof byte[] && Arrays.equals((byte[]) a, (byte[]) b))) add(key, path, a, b);
            return;
        }
        if (ca.isArray() || cb.isArray()) {
            if (!(ca.isArray() && cb.isArray())) { add(key + "#type", path, a, b); return; }
            int la = Array.getLength(a), lb = Array.getLength(b);
            if (la != lb) add(key + "#size", path, a, b);
            for (int i = 0; i < Math.min(la, lb); i++) {
                compare(path + "[" + i + "]", key, Array.get(a, i), Array.get(b, i), depth + 1);
            }
            return;
        }
        if (a instanceof Collection || b instanceof Collection || a instanceof Map || b instanceof Map) {
            List<Object> la = asList(a), lb = asList(b);
            if (la == null || lb == null) { add(key + "#type", path, a, b); return; }
            if (la.size() != lb.size()) add(key + "#size", path, a, b);
            for (int i = 0; i < Math.min(la.size(), lb.size()); i++) {
                compare(path + "[" + i + "]", key, la.get(i), lb.get(i), depth + 1);
            }
            return;
        }
        if (ca != cb) {
            add(key + "#type", path, ModelDumper.simpleName(ca), ModelDumper.simpleName(cb));
            return;
        }
        if (!ca.getName().startsWith("HwpDoc.")) {
            if (!a.equals(b)) add(key, path, a, b);
            return;
        }
        if (visiting.containsKey(a)) return;     // 순환 참조
        visiting.put(a, Boolean.TRUE);
        // 글자처럼 취급하는 개체는 본문 배치 관련 값이 쓰이지 않는다 (명세 표 70). 한글은 HWPX 저장 시 값을 바꿔 쓴다
        boolean bothInline = a instanceof HwpDoc.paragraph.Ctrl_Common && b instanceof HwpDoc.paragraph.Ctrl_Common
                && ((HwpDoc.paragraph.Ctrl_Common) a).treatAsChar && ((HwpDoc.paragraph.Ctrl_Common) b).treatAsChar;
        try {
            for (Field f : ModelDumper.fieldsOf(ca)) {
                if (bothInline && INLINE_IGNORED.contains(f.getName())) continue;
                if (IGNORE_FIELDS.contains(f.getName())) continue;
                Object fa, fb;
                try {
                    fa = f.get(a);
                    fb = f.get(b);
                } catch (IllegalAccessException e) {
                    continue;
                }
                String fkey = ModelDumper.simpleName(f.getDeclaringClass()) + "." + f.getName();
                if (IGNORE_KEYS.contains(fkey)) continue;
                boolean normalize = leftResolver != null && rightResolver != null;
                if (normalize && REF_LISTS.contains(fkey)) continue;
                if (normalize && "paragraph.HwpParagraph.breakType".equals(fkey) && fa instanceof Number && fb instanceof Number) {
                    // 구역(0x01)/다단(0x02) 나누기는 HWPX 에서 secPr/colPr 컨트롤로 표현되므로 쪽(0x04)/단(0x08) 나누기만 비교
                    int ma = ((Number) fa).intValue() & 0x0C, mb = ((Number) fb).intValue() & 0x0C;
                    if (ma != mb) add(fkey, path + "." + f.getName(), fa, fb);
                    continue;
                }
                if (normalize && PARA_CTRL_LIST.equals(fkey) && fa instanceof List && fb instanceof List) {
                    compareParaCtrls(path + "." + f.getName(), (List<?>) fa, (List<?>) fb, depth + 1);
                    continue;
                }
                String[] ref = normalize ? REFS.get(fkey) : null;
                if (ref != null) {
                    int base = Integer.parseInt(ref[1]);
                    Object ra = leftResolver.resolve(ref[0], base, fa);
                    Object rb = rightResolver.resolve(ref[0], base, fb);
                    if (ra != null && rb != null) {
                        if (firstTime(ra, rb)) {
                            compare(path + "." + f.getName() + "->" + ref[0], fkey, ra, rb, depth + 1);
                        }
                        continue;
                    } else if (ra != null || rb != null) {
                        // HWP 의 테두리/배경 "없음"(0) 과 HWPX 의 빈 테두리/배경(보통 1번)은 같은 뜻
                        if (!isEmptyBorderFill(ra != null ? ra : rb)) {
                            add(fkey + "#ref", path + "." + f.getName(), fa, fb);
                        }
                        continue;
                    }
                    // 양쪽 다 찾지 못하면 (예: 없음을 뜻하는 0, -1) 값 그대로 비교
                }
                compare(path + "." + f.getName(), fkey, fa, fb, depth + 1);
            }
        } finally {
            visiting.remove(a);
        }
    }

    /** 선도 채우기도 없는 테두리/배경 */
    static boolean isEmptyBorderFill(Object o) {
        if (!(o instanceof HwpRecord_BorderFill)) return false;
        HwpRecord_BorderFill b = (HwpRecord_BorderFill) o;
        HwpRecord_BorderFill.Border[] sides = { b.left, b.right, b.top, b.bottom };
        for (HwpRecord_BorderFill.Border side : sides) {
            if (side != null && side.style != null && side.style != LineStyle2.NONE) return false;
        }
        if (b.slash != 0 || b.backSlash != 0) return false;
        return b.fill == null || b.fill.fillType == 0;
    }

    /** 문단의 컨트롤 목록 (ParaText + 컨트롤) */
    static final String PARA_CTRL_LIST = "paragraph.HwpParagraph.p";
    static final String CHAR_SHAPE_KEY = "paragraph.ParaText.charShapeId";

    /**
     * 문단 안의 컨트롤 목록을 비교한다.
     * - 한글은 HWPX 로 저장할 때 같은 글자 모양의 텍스트 조각을 합치므로 ParaText 는 위치로 비교하지 않는다.
     *   텍스트는 이어 붙인 문자열로, 글자 모양은 글자 위치마다 (참조 대상 내용으로) 비교한다.
     * - 그 밖의 컨트롤은 종류 순서를 LCS 로 맞춘 뒤 짝끼리 비교하고, 짝이 없으면 "#only" 로 기록한다.
     */
    private void compareParaCtrls(String path, List<?> a, List<?> b, int depth) {
        StringBuilder ta = new StringBuilder(), tb = new StringBuilder();
        List<Object> sa = new ArrayList<Object>(), sb = new ArrayList<Object>();   // 글자 위치별 글자 모양 ID
        List<Object> ca = new ArrayList<Object>(), cb = new ArrayList<Object>();   // 텍스트 이외의 컨트롤
        split(a, ta, sa, ca);
        split(b, tb, sb, cb);

        if (!ta.toString().equals(tb.toString())) {
            add("paragraph.ParaText.text#merged", path, ta.toString(), tb.toString());
        } else {
            String[] ref = REFS.get(CHAR_SHAPE_KEY);
            int base = Integer.parseInt(ref[1]);
            for (int i = 0; i < sa.size(); i++) {
                Object ra = leftResolver.resolve(ref[0], base, sa.get(i));
                Object rb = rightResolver.resolve(ref[0], base, sb.get(i));
                if (ra != null && rb != null) {
                    if (firstTime(ra, rb)) {
                        compare(path + "#char[" + i + "]->" + ref[0], CHAR_SHAPE_KEY, ra, rb, depth + 1);
                    }
                } else if (ra != null || rb != null) {
                    add(CHAR_SHAPE_KEY + "#ref", path + "#char[" + i + "]", sa.get(i), sb.get(i));
                }
            }
        }

        // 컨트롤 종류 순서로 LCS 정렬
        int n = ca.size(), m = cb.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = sig(ca.get(i)).equals(sig(cb.get(j))) ? lcs[i + 1][j + 1] + 1
                                                                 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && sig(ca.get(i)).equals(sig(cb.get(j)))) {
                compare(path + "[" + sig(ca.get(i)) + "#" + i + "]", PARA_CTRL_LIST, ca.get(i), cb.get(j), depth + 1);
                i++;
                j++;
            } else if (j >= m || (i < n && lcs[i + 1][j] >= lcs[i][j + 1])) {
                add(PARA_CTRL_LIST + "#only:" + sig(ca.get(i)), path + "[" + i + "]", sig(ca.get(i)), "(none)");
                i++;
            } else {
                add(PARA_CTRL_LIST + "#only:" + sig(cb.get(j)), path + "[" + j + "]", "(none)", sig(cb.get(j)));
                j++;
            }
        }
    }

    private static void split(List<?> list, StringBuilder text, List<Object> shapes, List<Object> ctrls) {
        for (Object o : list) {
            if (o instanceof HwpDoc.paragraph.ParaText) {
                HwpDoc.paragraph.ParaText t = (HwpDoc.paragraph.ParaText) o;
                String s = t.text == null ? "" : t.text;
                text.append(s);
                for (int k = 0; k < s.length(); k++) shapes.add(t.charShapeId);
            } else if (o != null) {
                ctrls.add(o);
            }
        }
    }

    private static String sig(Object o) {
        return o == null ? "null" : ModelDumper.simpleName(o.getClass());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        if (o instanceof Collection) return new ArrayList<Object>((Collection<Object>) o);
        if (o instanceof Map) return new ArrayList<Object>(((Map<Object, Object>) o).values());
        return null;
    }

    private static boolean scalarEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            if (a instanceof Float || a instanceof Double || b instanceof Float || b instanceof Double) {
                return Math.abs(((Number) a).doubleValue() - ((Number) b).doubleValue()) < 1e-6;
            }
            return ((Number) a).longValue() == ((Number) b).longValue();
        }
        if (a instanceof Enum && b instanceof Enum) return ((Enum<?>) a).name().equals(((Enum<?>) b).name());
        return a.equals(b);
    }
}
