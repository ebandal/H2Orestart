/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Attr;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * DOM 을 동적 프록시로 감싸 파서가 실제로 접근한 요소/속성/텍스트를 기록한다.
 * 파서 코드는 수정하지 않는다 (HwpxFile.getDocument 오버라이드로 주입).
 */
public class DomTracker {

    /** 파싱된 엔트리 하나 */
    public static class Loaded {
        public final String entry;
        public final Document doc;
        Loaded(String entry, Document doc) {
            this.entry = entry;
            this.doc = doc;
        }
    }

    private final Map<Object, Object> proxies = new IdentityHashMap<Object, Object>();
    private final Map<Node, Set<String>> attrRead = new IdentityHashMap<Node, Set<String>>();
    private final Set<Node> nameRead = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private final Set<Node> entered = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private final Set<Node> textRead = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private final List<Loaded> loaded = new ArrayList<Loaded>();
    /** NamedNodeMap -> 소유 요소 */
    private final Map<Object, Node> mapOwner = new IdentityHashMap<Object, Node>();
    /** 파서가 DOM 에서 지운 속성 (요소 -> 이름 -> 값) */
    private final Map<Node, Map<String, String>> removed = new IdentityHashMap<Node, Map<String, String>>();
    private static final Map<Class<?>, Class<?>[]> IFACES = new HashMap<Class<?>, Class<?>[]>();

    public Document wrap(String entry, Document doc) {
        loaded.add(new Loaded(entry, doc));
        return (Document) wrapAny(doc);
    }

    public List<Loaded> getLoaded() { return loaded; }
    public boolean isNameRead(Node n) { return nameRead.contains(n); }
    public boolean isEntered(Node n) { return entered.contains(n); }
    public boolean isTextRead(Node n) { return textRead.contains(n); }
    public Map<String, String> removedAttrs(Node n) {
        Map<String, String> m = removed.get(n);
        return m == null ? Collections.<String, String>emptyMap() : m;
    }
    public boolean isAttrRead(Node n, String attr) {
        Set<String> s = attrRead.get(n);
        return s != null && s.contains(attr);
    }

    private Object wrapAny(Object o) {
        if (o == null || Proxy.isProxyClass(o.getClass())) return o;
        if (!(o instanceof Node || o instanceof NodeList || o instanceof NamedNodeMap)) return o;
        Object p = proxies.get(o);
        if (p == null) {
            p = Proxy.newProxyInstance(DomTracker.class.getClassLoader(), domInterfaces(o.getClass()), new Handler(o));
            proxies.put(o, p);
        }
        return p;
    }

    private static synchronized Class<?>[] domInterfaces(Class<?> c) {
        Class<?>[] r = IFACES.get(c);
        if (r == null) {
            Set<Class<?>> set = new LinkedHashSet<Class<?>>();
            collect(c, set);
            r = set.toArray(new Class<?>[0]);
            IFACES.put(c, r);
        }
        return r;
    }

    private static void collect(Class<?> c, Set<Class<?>> out) {
        if (c == null) return;
        for (Class<?> i : c.getInterfaces()) {
            if (i.getName().startsWith("org.w3c.dom.")) out.add(i);
            collect(i, out);
        }
        collect(c.getSuperclass(), out);
    }

    private void markAttr(Node owner, String name) {
        if (owner == null || name == null) return;
        Set<String> s = attrRead.get(owner);
        if (s == null) {
            s = new HashSet<String>();
            attrRead.put(owner, s);
        }
        s.add(name);
    }

    private void recordRemove(Object target, String m, Object[] args) {
        Node owner = null;
        String name = null;
        if (target instanceof NamedNodeMap && "removeNamedItem".equals(m)) {
            owner = mapOwner.get(target);
            name = (String) args[0];
        } else if (target instanceof Element && ("removeAttribute".equals(m))) {
            owner = (Element) target;
            name = (String) args[0];
        }
        if (owner == null || name == null || !(owner instanceof Element)) return;
        String value = ((Element) owner).getAttribute(name);
        Map<String, String> mm = removed.get(owner);
        if (mm == null) {
            mm = new java.util.LinkedHashMap<String, String>();
            removed.put(owner, mm);
        }
        mm.put(name, value);
    }

    private void record(Object target, String m, Object[] args) {
        if (target instanceof Element) {
            Element e = (Element) target;
            if ("getNodeName".equals(m) || "getTagName".equals(m) || "getLocalName".equals(m)) {
                nameRead.add(e);
            } else if ("getAttribute".equals(m) || "getAttributeNode".equals(m) || "hasAttribute".equals(m)) {
                entered.add(e);
                markAttr(e, (String) args[0]);
            } else if ("getTextContent".equals(m)) {
                entered.add(e);
                textRead.add(e);
            } else if (m.startsWith("get") || m.startsWith("has")) {
                // getAttributes, getChildNodes, getFirstChild, getElementsByTagName ...
                if (!"getParentNode".equals(m) && !"getOwnerDocument".equals(m) && !"getNodeType".equals(m)) {
                    entered.add(e);
                }
            }
        } else if (target instanceof Attr) {
            if ("getNodeValue".equals(m) || "getValue".equals(m) || "getTextContent".equals(m)) {
                Attr a = (Attr) target;
                markAttr(a.getOwnerElement(), a.getName());
            }
        } else if (target instanceof CharacterData) {
            if ("getNodeValue".equals(m) || "getData".equals(m) || "getTextContent".equals(m) || "getWholeText".equals(m)) {
                Node parent = ((Node) target).getParentNode();
                if (parent != null) textRead.add(parent);
            }
        }
    }

    private class Handler implements InvocationHandler {
        private final Object target;
        Handler(Object target) { this.target = target; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && Proxy.isProxyClass(args[i].getClass())) {
                        InvocationHandler h = Proxy.getInvocationHandler(args[i]);
                        if (h instanceof Handler) args[i] = ((Handler) h).target;
                    }
                }
            }
            record(target, method.getName(), args);
            recordRemove(target, method.getName(), args);
            Object r;
            try {
                r = method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (target instanceof Element && "getAttributes".equals(method.getName()) && r != null) {
                mapOwner.put(r, (Element) target);
            }
            return wrapAny(r);
        }
    }
}
