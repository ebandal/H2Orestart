/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * HWPX 패키지 안의 XML 들에서 (엔트리 종류, 부모>요소), (요소@속성) 사용 통계를 모은다.
 * 한글이 실제로 출력하는 요소/속성 목록 = 저장 기능이 만들어내야 할 목록의 기준.
 */
public class XmlInventory {
    private static final int MAX_SAMPLES = 8;
    private static final int MAX_DISTINCT = 1000;

    static class Stat {
        long count;
        Set<Integer> docs = new HashSet<Integer>();
        Set<String> samples = new LinkedHashSet<String>();
        Set<String> distinct = new HashSet<String>();
        boolean distinctOverflow;
        String firstDoc;
    }

    private final Map<String, Stat> elements = new TreeMap<String, Stat>();
    private final Map<String, Stat> attributes = new TreeMap<String, Stat>();
    private final Map<String, Stat> entries = new TreeMap<String, Stat>();
    private final XMLInputFactory factory;

    public XmlInventory() {
        factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    }

    /** @return 파싱 실패한 엔트리 메시지 목록 */
    public List<String> scan(ZipFile zip, int docIndex, String docName) {
        List<String> errors = new ArrayList<String>();
        List<? extends ZipEntry> list = Collections.list(zip.entries());
        Enumeration<? extends ZipEntry> en = Collections.enumeration(list);
        int order = 0;
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            String kind = entryKind(ze.getName());
            String method = ze.getMethod() == ZipEntry.STORED ? "STORED" : "DEFLATED";
            add(entries, kind + "\t" + method, String.valueOf(order++), docIndex, docName);
            if (!(kind.endsWith(".xml") || kind.endsWith(".hpf") || kind.endsWith(".rdf"))) {
                continue;
            }
            InputStream is = null;
            try {
                is = zip.getInputStream(ze);
                scanXml(kind, is, docIndex, docName);
            } catch (IOException | XMLStreamException e) {
                errors.add(ze.getName() + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
            } finally {
                if (is != null) {
                    try { is.close(); } catch (IOException e) { /* ignore */ }
                }
            }
        }
        return errors;
    }

    private void scanXml(String kind, InputStream is, int docIndex, String docName) throws XMLStreamException {
        XMLStreamReader r = factory.createXMLStreamReader(is, "UTF-8");
        List<String> stack = new ArrayList<String>();
        stack.add("#doc");
        try {
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String q = qname(r.getPrefix(), r.getLocalName());
                    String parent = stack.get(stack.size() - 1);
                    add(elements, kind + "\t" + parent + ">" + q, null, docIndex, docName);
                    for (int i = 0; i < r.getAttributeCount(); i++) {
                        String aq = qname(r.getAttributePrefix(i), r.getAttributeLocalName(i));
                        add(attributes, kind + "\t" + q + "@" + aq, r.getAttributeValue(i), docIndex, docName);
                    }
                    stack.add(q);
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    stack.remove(stack.size() - 1);
                }
            }
        } finally {
            r.close();
        }
    }

    private static String qname(String prefix, String local) {
        return (prefix == null || prefix.isEmpty()) ? local : prefix + ":" + local;
    }

    static String entryKind(String name) {
        // Contents/section12.xml -> Contents/section#.xml, BinData/image3.png -> BinData/*.png
        if (name.startsWith("BinData/")) {
            int dot = name.lastIndexOf('.');
            return "BinData/*" + (dot >= 0 ? name.substring(dot).toLowerCase() : "");
        }
        return name.replaceAll("[0-9]+(\\.[A-Za-z]+)$", "#$1");
    }

    private static void add(Map<String, Stat> map, String key, String value, int docIndex, String docName) {
        Stat s = map.get(key);
        if (s == null) {
            s = new Stat();
            s.firstDoc = docName;
            map.put(key, s);
        }
        s.count++;
        s.docs.add(docIndex);
        if (value != null) {
            if (s.samples.size() < MAX_SAMPLES) {
                s.samples.add(value.length() > 40 ? value.substring(0, 40) + "…" : value);
            }
            if (!s.distinctOverflow) {
                s.distinct.add(value);
                if (s.distinct.size() > MAX_DISTINCT) {
                    s.distinctOverflow = true;
                    s.distinct.clear();
                }
            }
        }
    }

    public void writeElements(PrintWriter pw) {
        pw.println("entry\tparent>element\tdocs\tcount\tfirstDoc");
        for (Map.Entry<String, Stat> e : elements.entrySet()) {
            Stat s = e.getValue();
            pw.println(e.getKey() + "\t" + s.docs.size() + "\t" + s.count + "\t" + s.firstDoc);
        }
    }

    public void writeAttributes(PrintWriter pw) {
        pw.println("entry\telement@attr\tdocs\tcount\tdistinct\tsamples\tfirstDoc");
        for (Map.Entry<String, Stat> e : attributes.entrySet()) {
            Stat s = e.getValue();
            String distinct = s.distinctOverflow ? ">" + MAX_DISTINCT : String.valueOf(s.distinct.size());
            pw.println(e.getKey() + "\t" + s.docs.size() + "\t" + s.count + "\t" + distinct + "\t"
                    + String.join(" | ", s.samples).replace('\t', ' ').replace('\n', ' ') + "\t" + s.firstDoc);
        }
    }

    public void writeEntries(PrintWriter pw) {
        pw.println("entry\tmethod\tdocs\tcount\tzipOrderSamples");
        for (Map.Entry<String, Stat> e : entries.entrySet()) {
            Stat s = e.getValue();
            pw.println(e.getKey() + "\t" + s.docs.size() + "\t" + s.count + "\t" + String.join(",", s.samples));
        }
    }

    public int elementKinds() { return elements.size(); }
    public int attributeKinds() { return attributes.size(); }
}
