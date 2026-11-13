/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * HwpDoc 모델 객체 그래프를 리플렉션으로 순회하여 결정적인(deterministic) 텍스트로 덤프한다.
 * - HwpDoc.* 패키지 객체는 필드 단위로 재귀 순회
 * - 이미 출력한 객체는 "@ref#n" 으로 표시 (순환 참조/역참조 방지)
 * - byte[] 는 길이와 해시만 출력
 */
public class ModelDumper {
    private static final int MAX_DEPTH = 64;
    private static final String[] SKIP_TYPES = { "HwpDoc.HwpxFile", "HwpDoc.HwpFile", "HwpDoc.OCFdoc.OwpmlFile" };

    private final PrintWriter out;
    private final IdentityHashMap<Object, Integer> seen = new IdentityHashMap<Object, Integer>();
    private int nextId = 1;
    private long nodeCount = 0;

    public ModelDumper(PrintWriter out) {
        this.out = out;
    }

    public long getNodeCount() {
        return nodeCount;
    }

    public void dump(String name, Object obj) {
        dumpValue(name, obj, 0);
    }

    private void line(int depth, String text) {
        StringBuilder sb = new StringBuilder(depth * 2 + text.length());
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(text);
        out.println(sb.toString());
        nodeCount++;
    }

    private void dumpValue(String name, Object v, int depth) {
        if (depth > MAX_DEPTH) {
            line(depth, name + " = <max-depth>");
            return;
        }
        if (v == null) {
            line(depth, name + " = null");
            return;
        }
        Class<?> c = v.getClass();
        if (isScalar(c)) {
            line(depth, name + " = " + scalar(v));
            return;
        }
        if (c == byte[].class) {
            byte[] b = (byte[]) v;
            line(depth, name + " = bytes[" + b.length + "] " + hash(b));
            return;
        }
        if (c.isArray()) {
            int len = Array.getLength(v);
            if (c.getComponentType().isPrimitive()) {
                StringBuilder sb = new StringBuilder();
                sb.append(name).append(" = [");
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(scalar(Array.get(v, i)));
                }
                sb.append("]");
                line(depth, sb.toString());
            } else {
                line(depth, name + " : " + simpleName(c) + "[" + len + "]");
                for (int i = 0; i < len; i++) {
                    dumpValue("[" + i + "]", Array.get(v, i), depth + 1);
                }
            }
            return;
        }
        if (v instanceof Collection) {
            Collection<?> col = (Collection<?>) v;
            line(depth, name + " : " + simpleName(c) + "(" + col.size() + ")");
            int i = 0;
            for (Object o : col) {
                dumpValue("[" + (i++) + "]", o, depth + 1);
            }
            return;
        }
        if (v instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) v;
            line(depth, name + " : " + simpleName(c) + "{" + map.size() + "}");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                dumpValue("{" + scalar(e.getKey()) + "}", e.getValue(), depth + 1);
            }
            return;
        }
        if (!c.getName().startsWith("HwpDoc.")) {
            // 모델 외부 타입은 toString 으로만 표현
            line(depth, name + " = <" + simpleName(c) + "> " + escape(String.valueOf(v)));
            return;
        }
        for (String skip : SKIP_TYPES) {
            if (c.getName().equals(skip)) {
                line(depth, name + " = <" + simpleName(c) + " skipped>");
                return;
            }
        }
        Integer ref = seen.get(v);
        if (ref != null) {
            line(depth, name + " = @ref#" + ref);
            return;
        }
        int id = nextId++;
        seen.put(v, id);
        line(depth, name + " : " + simpleName(c) + " #" + id);
        for (Field f : fieldsOf(c)) {
            Object fv;
            try {
                fv = f.get(v);
            } catch (IllegalAccessException e) {
                line(depth + 1, f.getName() + " = <inaccessible>");
                continue;
            }
            dumpValue(f.getName(), fv, depth + 1);
        }
    }

    static List<Field> fieldsOf(Class<?> c) {
        // 상위 클래스 필드부터 선언 순서대로
        List<Class<?>> chain = new ArrayList<Class<?>>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            chain.add(0, k);
        }
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> k : chain) {
            for (Field f : k.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || f.isSynthetic()) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                } catch (RuntimeException e) {
                    continue;
                }
                fields.add(f);
            }
        }
        return fields;
    }

    static boolean isScalar(Class<?> c) {
        return c.isPrimitive() || c == String.class || Number.class.isAssignableFrom(c)
                || c == Boolean.class || c == Character.class || c.isEnum()
                || (c.getSuperclass() != null && c.getSuperclass().isEnum());
    }

    static String scalar(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return "\"" + escape((String) v) + "\"";
        if (v instanceof Character) return "'" + escape(String.valueOf(v)) + "'";
        if (v instanceof Float || v instanceof Double) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        if (v instanceof Enum) return ((Enum<?>) v).name();
        return String.valueOf(v);
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') sb.append("\\\\");
            else if (ch == '"') sb.append("\\\"");
            else if (ch < 0x20 || ch == 0x7f) sb.append(String.format("\\u%04x", (int) ch));
            else sb.append(ch);
        }
        return sb.toString();
    }

    static String simpleName(Class<?> c) {
        String n = c.getName();
        return n.startsWith("HwpDoc.") ? n.substring(7) : c.getSimpleName();
    }

    static String hash(byte[] b) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(b);
            StringBuilder sb = new StringBuilder("sha1:");
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", d[i] & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "sha1:?";
        }
    }
}
