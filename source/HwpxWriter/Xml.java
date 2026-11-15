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

/** XML 문자열 도우미 */
final class Xml {
    private Xml() {}

    static final String DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>";

    /** 한글 2022 가 header.xml, sectionN.xml, content.hpf 루트에 쓰는 이름공간 선언 */
    static final String NS =
            " xmlns:ha=\"http://www.hancom.co.kr/hwpml/2011/app\""
          + " xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\""
          + " xmlns:hp10=\"http://www.hancom.co.kr/hwpml/2016/paragraph\""
          + " xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\""
          + " xmlns:hc=\"http://www.hancom.co.kr/hwpml/2011/core\""
          + " xmlns:hh=\"http://www.hancom.co.kr/hwpml/2011/head\""
          + " xmlns:hhs=\"http://www.hancom.co.kr/hwpml/2011/history\""
          + " xmlns:hm=\"http://www.hancom.co.kr/hwpml/2011/master-page\""
          + " xmlns:hpf=\"http://www.hancom.co.kr/schema/2011/hpf\""
          + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
          + " xmlns:opf=\"http://www.idpf.org/2007/opf/\""
          + " xmlns:ooxmlchart=\"http://www.hancom.co.kr/hwpml/2016/ooxmlchart\""
          + " xmlns:hwpunitchar=\"http://www.hancom.co.kr/hwpml/2016/HwpUnitChar\""
          + " xmlns:epub=\"http://www.idpf.org/2007/ops\""
          + " xmlns:config=\"urn:oasis:names:tc:opendocument:xmlns:config:1.0\"";

    /** 속성값/텍스트 이스케이프. XML 1.0 에서 쓸 수 없는 제어 문자는 버린다 */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '&':  sb.append("&amp;");  break;
            case '<':  sb.append("&lt;");   break;
            case '>':  sb.append("&gt;");   break;
            case '"':  sb.append("&quot;"); break;
            default:
                if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                    break;      // 허용되지 않는 제어 문자
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** #RRGGBB */
    static String color(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
