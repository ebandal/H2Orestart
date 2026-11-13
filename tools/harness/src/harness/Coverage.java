/* Copyright (C) 2026 ebandal
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package harness;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 파서 커버리지 집계.
 * 원본 XML 의 각 요소/속성/텍스트가 파서에 의해 읽혔는지를 DomTracker 기록과 대조한다.
 *  - 요소: 부모가 처리(entered)된 경우에만 집계 (처리되지 않은 서브트리 내부는 underSkipped 로만 셈)
 *  - 속성/텍스트: 요소가 처리된 경우에만 "읽힘/안읽힘" 판정
 */
public class Coverage {

    static class ElemStat {
        long instances, used, flagOnly, nameOnly, untouched, underSkipped;
        Set<Integer> untouchedDocs = new HashSet<Integer>();   // nameOnly + untouched 가 발생한 문서
        Set<Integer> docs = new HashSet<Integer>();
        String sampleDoc;
    }

    static class AttrStat {
        long present, read, onSkipped;
        Set<Integer> docs = new HashSet<Integer>();
        Set<Integer> unreadDocs = new HashSet<Integer>();
        String sampleValue;
        String sampleDoc;
    }

    private final Map<String, ElemStat> elems = new TreeMap<String, ElemStat>();
    private final Map<String, AttrStat> attrs = new TreeMap<String, AttrStat>();
    private long attrPresent, attrReadTotal;

    public void collect(DomTracker t, int docIdx, String docName) {
        // 같은 엔트리를 여러 번 읽었으면 마지막 것만 사용
        Map<String, DomTracker.Loaded> byEntry = new LinkedHashMap<String, DomTracker.Loaded>();
        for (DomTracker.Loaded l : t.getLoaded()) {
            byEntry.put(l.entry, l);
        }
        for (DomTracker.Loaded l : byEntry.values()) {
            Element root = l.doc.getDocumentElement();
            if (root == null) continue;
            String kind = XmlInventory.entryKind(l.entry);
            walk(t, kind, root, "#doc", true, docIdx, docName);
        }
    }

    private void walk(DomTracker t, String kind, Element e, String parentName, boolean parentEntered, int docIdx, String docName) {
        String name = e.getNodeName();
        String key = kind + "\t" + parentName + ">" + name;
        ElemStat es = elems.get(key);
        if (es == null) {
            es = new ElemStat();
            es.sampleDoc = docName;
            elems.put(key, es);
        }
        boolean entered = t.isEntered(e);
        es.instances++;
        es.docs.add(docIdx);
        if (!parentEntered) {
            es.underSkipped++;
        } else if (entered) {
            es.used++;
        } else if (t.isNameRead(e)) {
            // 속성/자식이 없는 빈 요소(예: hh:bold)는 이름 확인만으로 처리된 것으로 본다
            if (e.getAttributes().getLength() == 0 && !e.hasChildNodes()) {
                es.flagOnly++;
            } else {
                es.nameOnly++;
                es.untouchedDocs.add(docIdx);
            }
        } else {
            es.untouched++;
            es.untouchedDocs.add(docIdx);
        }

        // 속성
        NamedNodeMap am = e.getAttributes();
        for (int i = 0; i < am.getLength(); i++) {
            Attr a = (Attr) am.item(i);
            String an = a.getName();
            if (an.startsWith("xmlns")) continue;
            String akey = kind + "\t" + name + "@" + an;
            AttrStat as = attrs.get(akey);
            if (as == null) {
                as = new AttrStat();
                as.sampleValue = a.getValue();
                as.sampleDoc = docName;
                attrs.put(akey, as);
            }
            as.docs.add(docIdx);
            if (!entered) {
                as.onSkipped++;
                continue;
            }
            as.present++;
            attrPresent++;
            if (t.isAttrRead(e, an)) {
                as.read++;
                attrReadTotal++;
            } else {
                as.unreadDocs.add(docIdx);
                if (as.sampleValue == null || as.unreadDocs.size() == 1) as.sampleValue = a.getValue();
            }
        }

        // 텍스트 (직접 자식 텍스트가 공백이 아닐 때)
        boolean hasText = false;
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if ((c.getNodeType() == Node.TEXT_NODE || c.getNodeType() == Node.CDATA_SECTION_NODE)
                    && c.getNodeValue().trim().length() > 0) {
                hasText = true;
                break;
            }
        }
        if (hasText) {
            String tkey = kind + "\t" + name + "#text";
            AttrStat ts = attrs.get(tkey);
            if (ts == null) {
                ts = new AttrStat();
                ts.sampleDoc = docName;
                attrs.put(tkey, ts);
            }
            ts.docs.add(docIdx);
            if (!entered && !t.isTextRead(e)) {
                ts.onSkipped++;
            } else {
                ts.present++;
                if (t.isTextRead(e)) ts.read++;
                else {
                    ts.unreadDocs.add(docIdx);
                    if (ts.sampleValue == null) ts.sampleValue = e.getTextContent();
                }
            }
        }

        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                walk(t, kind, (Element) c, name, entered, docIdx, docName);
            }
        }
    }

    public void writeElements(PrintWriter pw) {
        List<Map.Entry<String, ElemStat>> list = new ArrayList<Map.Entry<String, ElemStat>>(elems.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, ElemStat>>() {
            @Override public int compare(Map.Entry<String, ElemStat> a, Map.Entry<String, ElemStat> b) {
                int c = Integer.compare(b.getValue().untouchedDocs.size(), a.getValue().untouchedDocs.size());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        pw.println("entry\tparent>element\tdocs\tinstances\tused\tflagOnly\tnameOnly\tuntouched\tskippedDocs\tunderSkipped\tsampleDoc");
        for (Map.Entry<String, ElemStat> e : list) {
            ElemStat s = e.getValue();
            pw.println(e.getKey() + "\t" + s.docs.size() + "\t" + s.instances + "\t" + s.used + "\t" + s.flagOnly + "\t" + s.nameOnly + "\t"
                    + s.untouched + "\t" + s.untouchedDocs.size() + "\t" + s.underSkipped + "\t" + s.sampleDoc);
        }
    }

    public void writeAttributes(PrintWriter pw) {
        List<Map.Entry<String, AttrStat>> list = new ArrayList<Map.Entry<String, AttrStat>>(attrs.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, AttrStat>>() {
            @Override public int compare(Map.Entry<String, AttrStat> a, Map.Entry<String, AttrStat> b) {
                int c = Integer.compare(b.getValue().unreadDocs.size(), a.getValue().unreadDocs.size());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        pw.println("entry\telement@attr\tdocs\tpresent\tread\tunread\tunreadDocs\tonSkippedElement\tsampleValue\tsampleDoc");
        for (Map.Entry<String, AttrStat> e : list) {
            AttrStat s = e.getValue();
            String v = s.sampleValue == null ? "" : CorpusScan.oneLine(s.sampleValue);
            if (v.length() > 40) v = v.substring(0, 40) + "…";
            pw.println(e.getKey() + "\t" + s.docs.size() + "\t" + s.present + "\t" + s.read + "\t" + (s.present - s.read) + "\t"
                    + s.unreadDocs.size() + "\t" + s.onSkipped + "\t" + v + "\t" + s.sampleDoc);
        }
    }

    public String summary() {
        long used = 0, flagOnly = 0, nameOnly = 0, untouched = 0;
        int untouchedKinds = 0, unreadAttrKinds = 0;
        for (ElemStat s : elems.values()) {
            used += s.used;
            flagOnly += s.flagOnly;
            nameOnly += s.nameOnly;
            untouched += s.untouched;
            if (s.untouched + s.nameOnly > 0) untouchedKinds++;
        }
        for (AttrStat s : attrs.values()) {
            if (s.present > s.read) unreadAttrKinds++;
        }
        return String.format("elements(frontier): used=%d flagOnly=%d skipped(nameOnly)=%d untouched=%d (%d kinds skipped)%n"
                + "attributes on used elements: read %d / %d (%.1f%%), %d kinds with unread",
                used, flagOnly, nameOnly, untouched, untouchedKinds,
                attrReadTotal, attrPresent, attrPresent == 0 ? 0.0 : 100.0 * attrReadTotal / attrPresent, unreadAttrKinds);
    }
}
