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
package HwpxWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * HWPX(OCF zip) 패키지를 쓴다.
 * 엔트리 순서와 압축 방식은 한글 2022 와 같다: mimetype, version.xml 은 압축하지 않고 맨 앞에 둔다.
 */
public final class HwpxPackage {
    private HwpxPackage() {}

    public static final String APP_NAME = "H2Orestart";

    public static void write(WDocument doc, OutputStream out) throws IOException {
        if (doc.sections.isEmpty()) {
            doc.sections.add(new WSection());
        }
        ZipWriter zip = new ZipWriter(out);
        stored(zip, "mimetype", "application/hwp+zip");
        stored(zip, "version.xml", versionXml());
        deflated(zip, "Contents/header.xml", HeaderXml.build(doc));
        // 그림은 이미 압축된 형식이 많아 한글처럼 압축하지 않고 넣는다 (BMP 등은 압축)
        for (WDocument.BinItem b : doc.binItems) {
            boolean compressed = !("png".equals(b.ext) || "jpg".equals(b.ext) || "gif".equals(b.ext));
            zip.add(b.path(), b.data, compressed);
        }
        for (int i = 0; i < doc.sections.size(); i++) {
            deflated(zip, "Contents/section" + i + ".xml", SectionXml.build(doc, doc.sections.get(i)));
        }
        deflated(zip, "Preview/PrvText.txt", previewText(doc));
        deflated(zip, "settings.xml", Xml.DECL
                + "<ha:HWPApplicationSetting xmlns:ha=\"http://www.hancom.co.kr/hwpml/2011/app\" xmlns:config=\"urn:oasis:names:tc:opendocument:xmlns:config:1.0\">"
                + "<ha:CaretPosition listIDRef=\"0\" paraIDRef=\"0\" pos=\"0\"/></ha:HWPApplicationSetting>");
        deflated(zip, "META-INF/container.rdf", containerRdf(doc));
        deflated(zip, "Contents/content.hpf", contentHpf(doc));
        deflated(zip, "META-INF/container.xml", Xml.DECL
                + "<ocf:container xmlns:ocf=\"urn:oasis:names:tc:opendocument:xmlns:container\" xmlns:hpf=\"http://www.hancom.co.kr/schema/2011/hpf\">"
                + "<ocf:rootfiles><ocf:rootfile full-path=\"Contents/content.hpf\" media-type=\"application/hwpml-package+xml\"/>"
                + "<ocf:rootfile full-path=\"Preview/PrvText.txt\" media-type=\"text/plain\"/>"
                + "<ocf:rootfile full-path=\"META-INF/container.rdf\" media-type=\"application/rdf+xml\"/></ocf:rootfiles></ocf:container>");
        deflated(zip, "META-INF/manifest.xml", Xml.DECL
                + "<odf:manifest xmlns:odf=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\"/>");
        zip.finish();
    }

    /** 형식 버전은 한글 2022 와 같게 (major 5, minor 1, micro 1, xmlVersion 1.5). 만든 프로그램은 H2Orestart 로 밝힌다 */
    private static String versionXml() {
        return Xml.DECL
                + "<hv:HCFVersion xmlns:hv=\"http://www.hancom.co.kr/hwpml/2011/version\" tagetApplication=\"WORDPROCESSOR\""
                + " major=\"5\" minor=\"1\" micro=\"1\" buildNumber=\"0\" os=\"1\" xmlVersion=\"1.5\""
                + " application=\"" + APP_NAME + "\" appVersion=\"1, 0, 0, 0\"/>";
    }

    private static String containerRdf(WDocument doc) {
        StringBuilder sb = new StringBuilder();
        String pkg = "http://www.hancom.co.kr/hwpml/2016/meta/pkg#";
        sb.append(Xml.DECL).append("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">");
        sb.append("<rdf:Description rdf:about=\"\"><ns0:hasPart xmlns:ns0=\"").append(pkg).append("\" rdf:resource=\"Contents/header.xml\"/></rdf:Description>");
        sb.append("<rdf:Description rdf:about=\"Contents/header.xml\"><rdf:type rdf:resource=\"").append(pkg).append("HeaderFile\"/></rdf:Description>");
        for (int i = 0; i < doc.sections.size(); i++) {
            sb.append("<rdf:Description rdf:about=\"\"><ns0:hasPart xmlns:ns0=\"").append(pkg).append("\" rdf:resource=\"Contents/section").append(i).append(".xml\"/></rdf:Description>");
            sb.append("<rdf:Description rdf:about=\"Contents/section").append(i).append(".xml\"><rdf:type rdf:resource=\"").append(pkg).append("SectionFile\"/></rdf:Description>");
        }
        sb.append("<rdf:Description rdf:about=\"\"><rdf:type rdf:resource=\"").append(pkg).append("Document\"/></rdf:Description>");
        sb.append("</rdf:RDF>");
        return sb.toString();
    }

    private static String contentHpf(WDocument doc) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String now = iso.format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append(Xml.DECL).append("<opf:package").append(Xml.NS).append(" version=\"\" unique-identifier=\"\" id=\"\">");
        sb.append("<opf:metadata><opf:title>").append(Xml.esc(doc.title)).append("</opf:title><opf:language>ko</opf:language>")
          .append("<opf:meta name=\"creator\" content=\"text\">").append(Xml.esc(doc.creator)).append("</opf:meta>")
          .append("<opf:meta name=\"subject\" content=\"text\"/><opf:meta name=\"description\" content=\"text\"/>")
          .append("<opf:meta name=\"lastsaveby\" content=\"text\">").append(Xml.esc(doc.creator)).append("</opf:meta>")
          .append("<opf:meta name=\"CreatedDate\" content=\"text\">").append(now).append("</opf:meta>")
          .append("<opf:meta name=\"ModifiedDate\" content=\"text\">").append(now).append("</opf:meta>")
          .append("<opf:meta name=\"date\" content=\"text\"/><opf:meta name=\"keyword\" content=\"text\"/></opf:metadata>");
        sb.append("<opf:manifest><opf:item id=\"header\" href=\"Contents/header.xml\" media-type=\"application/xml\"/>");
        for (int i = 0; i < doc.sections.size(); i++) {
            sb.append("<opf:item id=\"section").append(i).append("\" href=\"Contents/section").append(i).append(".xml\" media-type=\"application/xml\"/>");
        }
        for (WDocument.BinItem b : doc.binItems) {
            sb.append("<opf:item id=\"").append(b.id).append("\" href=\"").append(b.path())
              .append("\" media-type=\"").append(b.mediaType()).append("\" isEmbeded=\"1\"/>");
        }
        sb.append("<opf:item id=\"settings\" href=\"settings.xml\" media-type=\"application/xml\"/></opf:manifest>");
        sb.append("<opf:spine><opf:itemref idref=\"header\" linear=\"yes\"/>");
        for (int i = 0; i < doc.sections.size(); i++) {
            sb.append("<opf:itemref idref=\"section").append(i).append("\" linear=\"yes\"/>");
        }
        sb.append("</opf:spine></opf:package>");
        return sb.toString();
    }

    /** 미리보기 텍스트: 문단을 줄 단위로 (앞부분만) */
    private static String previewText(WDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (WSection s : doc.sections) {
            for (WParagraph p : s.paragraphs) {
                sb.append(p.text()).append("\r\n");
                if (sb.length() > 1024) return sb.substring(0, 1024);
            }
        }
        return sb.toString();
    }

    private static void stored(ZipWriter zip, String name, String content) throws IOException {
        zip.add(name, content.getBytes(StandardCharsets.UTF_8), false);
    }

    private static void deflated(ZipWriter zip, String name, String content) throws IOException {
        zip.add(name, content.getBytes(StandardCharsets.UTF_8), true);
    }
}
