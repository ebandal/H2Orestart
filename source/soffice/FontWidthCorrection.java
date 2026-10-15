/* Copyright (C) 2023 ebandal
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
package soffice;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.star.beans.XPropertySet;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;

import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_ParaShape;

/**
 * 한컴 폰트가 없어서 나눔고딕으로 대체한 경우, 글자 종류별 자간으로 한컴오피스의 글자 폭에 맞춘다.
 * 리브레오피스 문장 폭이 한컴오피스 문장 폭보다 커지지 않도록(줄이 더 늘어나지 않도록) 여유값만큼 좁게 맞춘다.
 */
public class FontWidthCorrection {
    private static final Logger log = Logger.getLogger(FontWidthCorrection.class.getName());

    public static final String SUBST_FONT_NAME = "나눔고딕";

    // 글자 종류
    static final int HANGUL = 0;    // 한글 음절, 자모
    static final int SPACE  = 1;    // 공백
    static final int DIGIT  = 2;    // 0~9
    static final int LOWER  = 3;    // a~z
    static final int UPPER  = 4;    // A~Z
    static final int PUNCT  = 5;    // .,:;()[]'"
    static final int HYPHEN = 6;    // -
    static final int OTHER  = 7;    // 그 외(한자, 기호 등)는 보정하지 않는다.

    // 나눔고딕 글자 폭(em). 글꼴 파일의 평균 advance. Bold도 같다.
    private static final double[] NANUM_WIDTHS = { 0.940, 0.280, 0.606, 0.520, 0.651, 0.323, 0.363 };

    // 한컴오피스에서 측정한 글자 폭(em, 7/10/11pt 평균). 순서는 글자 종류 순서.
    // 공백은 '글꼴에 어울리는 빈칸'을 쓰지 않을 때(반각) 값.
    private static final Map<String, double[]> HANCOM_WIDTHS = new HashMap<String, double[]>();
    // 측정하지 않은 폰트는 측정한 폰트들의 최소값을 쓴다. (좁게 맞추는 쪽)
    private static final double[] DEFAULT_WIDTHS = { 0.971, 0.484, 0.501, 0.465, 0.501, 0.249, 0.412 };
    static {
        putWidths(new double[] { 1.000, 0.495, 0.501, 0.513, 0.719, 0.304, 0.571 }, "한양신명조");
        putWidths(new double[] { 1.000, 0.495, 0.501, 0.465, 0.646, 0.260, 0.520 }, "한양중고딕");
        putWidths(new double[] { 1.000, 0.495, 0.501, 0.487, 0.635, 0.286, 0.501 }, "휴먼명조");
        putWidths(new double[] { 1.000, 0.495, 0.505, 0.465, 0.573, 0.279, 0.501 }, "휴먼고딕");
        putWidths(new double[] { 1.000, 0.495, 0.622, 0.546, 0.726, 0.378, 0.836 }, "HY신명조");
        putWidths(new double[] { 1.000, 0.495, 0.582, 0.532, 0.688, 0.408, 0.836 }, "HY중고딕");
        putWidths(new double[] { 1.000, 0.495, 0.582, 0.528, 0.565, 0.252, 0.582 }, "HY헤드라인M");
        putWidths(new double[] { 0.971, 0.484, 0.550, 0.537, 0.664, 0.322, 0.550 }, "함초롬바탕", "HCR Batang");
        putWidths(new double[] { 0.971, 0.484, 0.550, 0.515, 0.643, 0.299, 0.468 }, "함초롬돋움", "HCR Dotum");
        putWidths(new double[] { 1.000, 0.495, 0.597, 0.534, 0.698, 0.337, 0.622 }, "바탕", "Batang");
        putWidths(new double[] { 1.000, 0.495, 0.501, 0.501, 0.501, 0.506, 0.501 }, "바탕체", "BatangChe");
        putWidths(new double[] { 1.000, 0.495, 0.574, 0.516, 0.658, 0.350, 0.622 }, "굴림", "Gulim");
        putWidths(new double[] { 1.000, 0.495, 0.501, 0.501, 0.501, 0.506, 0.501 }, "굴림체", "GulimChe");
        putWidths(new double[] { 1.000, 0.495, 0.582, 0.510, 0.653, 0.365, 0.590 }, "돋움", "Dotum");
        putWidths(new double[] { 1.000, 0.495, 0.550, 0.503, 0.624, 0.249, 0.412 }, "맑은 고딕", "Malgun Gothic");
        putWidths(new double[] { 1.000, 0.495, 0.574, 0.516, 0.658, 0.350, 0.622 }, "새굴림", "New Gulim");
    }

    // 한컴오피스보다 넓어지지 않도록 글자마다 빼는 여유값(em).
    // 0.01은 테스트 문서에서는 충분했으나 실제 문서(14pt 이상, 숫자 등)에서 넓어지는 경우가 남아 0.02로 정함.
    static final double MARGIN = 0.02;

    private static void putWidths(double[] widths, String... names) {
        for (String name : names) {
            HANCOM_WIDTHS.put(name, widths);
        }
    }

    static boolean hasHancomWidths(String fontName) {
        return fontName != null && HANCOM_WIDTHS.containsKey(fontName);
    }

    static int charClass(char c) {
        if ((c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F)) {
            return HANGUL;
        } else if (c == ' ') {
            return SPACE;
        } else if (c >= '0' && c <= '9') {
            return DIGIT;
        } else if (c >= 'a' && c <= 'z') {
            return LOWER;
        } else if (c >= 'A' && c <= 'Z') {
            return UPPER;
        } else if (".,:;()[]'\"".indexOf(c) >= 0) {
            return PUNCT;
        } else if (c == '-') {
            return HYPHEN;
        }
        return OTHER;
    }

    /**
     * 글자 종류별 자간.
     * 한컴 글자 폭 = 장평 * 폭 * (1 + 자간%)   (자간은 글자 폭에 비례. 반각 글자는 절반만 적용된다)
     * 공백은 최소 공백(condense)만큼 줄어들 수 있으므로 줄어든 폭에 맞춘다.
     * 리브레오피스 자간 = 한컴 글자 폭 - 나눔고딕 글자 폭 - 여유값
     * @return CharKerning (1/100 mm)
     */
    static short getCharKerning(HwpRecord_CharShape charShape, HwpRecord_ParaShape paraShape, int charClass) {
        if (charClass == OTHER) {
            return ConvPara.getCharKerning(charShape);
        }
        int lang = (charClass == HANGUL) ? 0 : 1;   // 0:한글, 1:영문
        double[] hancom = HANCOM_WIDTHS.get(charShape.origFontName[lang]);
        if (hancom == null) {
            hancom = DEFAULT_WIDTHS;
        }
        double ratio = charShape.ratio[lang] / 100.0;
        double spacing = charShape.spacing[lang] / 100.0;
        double hancomWidth = hancom[charClass];
        if (charClass == SPACE) {
            if (charShape.useFontSpace) {
                // 글꼴에 어울리는 빈칸: 원래 폰트의 공백 폭을 알 수 없으므로 나눔고딕 공백 그대로 쓴다.
                hancomWidth = NANUM_WIDTHS[SPACE];
            }
            if (paraShape != null) {
                hancomWidth *= (1.0 - paraShape.condense / 100.0);
            }
        }
        double kerningEm = ratio * hancomWidth * (1.0 + spacing) - ratio * NANUM_WIDTHS[charClass] - MARGIN;
        double fontSize = charShape.height / 100.0 * (charShape.relSize[lang] / 100.0);   // pt
        // 1pt = 0.35278mm = 35.278 (1/100 mm). 넓어지지 않도록 내림
        return (short) Math.floor(kerningEm * fontSize * 35.278);
    }

    /**
     * 같은 자간을 쓰는 글자끼리 나누어, 자간을 지정하며 문자열을 넣는다.
     */
    static void insertString(XText xText, XTextCursor xCursor, XPropertySet cursorProps, String content,
                             HwpRecord_CharShape charShape, HwpRecord_ParaShape paraShape) {
        short[] kerningByClass = new short[OTHER + 1];
        for (int i = 0; i <= OTHER; i++) {
            kerningByClass[i] = getCharKerning(charShape, paraShape, i);
        }
        int start = 0;
        while (start < content.length()) {
            short kerning = kerningByClass[charClass(content.charAt(start))];
            int end = start + 1;
            while (end < content.length() && kerningByClass[charClass(content.charAt(end))] == kerning) {
                end++;
            }
            try {
                cursorProps.setPropertyValue("CharKerning", kerning);
            } catch (Exception e) {
                log.log(Level.SEVERE, e.toString(), e);
            }
            xText.insertString(xCursor, content.substring(start, end), false);
            start = end;
        }
    }
}
