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
            "HwpElement.HwpRecord_BinData.state"
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
        try {
            for (Field f : ModelDumper.fieldsOf(ca)) {
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
                        add(fkey + "#ref", path + "." + f.getName(), fa, fb);
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
