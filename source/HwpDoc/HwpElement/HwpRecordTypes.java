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
/* 본 제품은 한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다.
 * 개방형 워드프로세서 마크업 언어(OWPML) 문서 구조 KS X 6101:2018 문서를 참고하였습니다.
 * 작성자 : 반희수 ebandal@gmail.com  
 * 작성일 : 2022.10
 */
package HwpDoc.HwpElement;

public class HwpRecordTypes {

    // 밑줄
    public static enum LineStyle1 {
        SOLID               (0),    // 실선
        DASH                (1),    // 긴 점선
        DOT                 (2),    // 점선
        DASH_DOT            (3),    // -.-.-.-
        DASH_DOT_DOT        (4),    // -..-..-..-
        LONG_DASH           (5),    // Dash 보다 긴 선분의 반복
        CIRCLE              (6),    // Dot보다 큰 동그라미의 반복
        DOUBLE_SLIM         (7),    // 2중선
        SLIM_THICK          (8),    // 가는선+굵은선 2중선
        THICK_SLIM          (9),    // 굵은선+가는선 2중선
        SLIM_THICK_SLIM     (10),   // 가는선+굵은선+가는선 3중선
        WAVE                (11),   // 물결
        DOUBLE_WAVE         (12),   // 물결 2중선
        THICK_3D            (13),   // 두꺼운 3D
        THICK_3D_REVERS_LI  (14),   // 두꺼운 3D(광원 반대)
        SOLID_3D            (15),   // 3D 단선
        SOLID_3D_REVERS_LI  (16);   // 3D 단선(광원 반대)

        private int num;
        private LineStyle1(int num) { 
            this.num = num;
        }
        public static LineStyle1 from(int num) {
            for (LineStyle1 shape: values()) {
                if (shape.num == num)
                    return shape;
            }
            return SOLID;
        }
    }

    // 테두리선, 취소선, 단 구분선
    public static enum LineStyle2 {
        NONE                (0),
        SOLID               (1),    // 실선
        DASH                (2),    // 긴 점선
        DOT                 (3),    // 점선
        DASH_DOT            (4),    // -.-.-.-
        DASH_DOT_DOT        (5),    // -..-..-..-
        LONG_DASH           (6),    // Dash 보다 긴 선분의 반복
        CIRCLE              (7),    // Dot보다 큰 동그라미의 반복
        DOUBLE_SLIM         (8),    // 2중선
        SLIM_THICK          (9),    // 가는선+굵은선 2중선
        THICK_SLIM          (10),   // 굵은선+가는선 2중선
        SLIM_THICK_SLIM     (11);   // 가는선+굵은선+가는선 3중선

        private int num;
        private LineStyle2(int num) { 
            this.num = num;
        }
        public static LineStyle2 from(int num) {
            for (LineStyle2 shape: values()) {
                if (shape.num == num)
                    return shape;
            }
            return NONE;
        }
    }

    public static enum NumberShape1 {
        DIGIT                   (0),    // 1, 2, 3
        CIRCLE_DIGIT            (1),    // 동그라미 쳐진 1, 2, 3
        ROMAN_CAPITAL           (2),    // I, II, III
        ROMAN_SMALL             (3),    // i, ii, iii
        LATIN_CAPITAL           (4),    // A, B, C
        LATIN_SMALL             (5),    // a, b, c
        CIRCLED_LATIN_CAPITAL   (6),    // 동그라미 쳐진 A, B, C
        CIRCLED_LATIN_SMALL     (7),    // 동그라미 쳐진 a, b, c
        HANGLE_SYLLABLE         (8),    // 가, 나, 다
        CIRCLED_HANGUL_SYLLABLE (9),    // 동그라미 쳐진 가, 나, 다
        HANGUL_JAMO             (10),   // ㄱ, ㄴ, ㄷ
        CIRCLED_HANGUL_JAMO     (11),   // 동그라미 쳐진 ㄱ, ㄴ, ㄷ
        HANGUL_PHONETIC         (12),   // 일, 이 , 삼,
        IDEOGRAPH               (13),   // 一, 二, 三
        CIRCLED_IDEOGRAPH       (14);   // 동그라미 쳐진 一, 二, 三

        private int num;
        private NumberShape1(int num) { 
            this.num = num;
        }
        public static NumberShape1 from(int num) {
            for (NumberShape1 shape: values()) {
                if (shape.num == num)
                    return shape;
            }
            return DIGIT;
        }
    }
    public static enum NumberShape2 {
        DIGIT                   (0),    // 1, 2, 3
        CIRCLE_DIGIT            (1),    // 동그라미 쳐진 1, 2, 3
        ROMAN_CAPITAL           (2),    // I, II, III
        ROMAN_SMALL             (3),    // i, ii, iii
        LATIN_CAPITAL           (4),    // A, B, C
        LATIN_SMALL             (5),    // a, b, c
        CIRCLED_LATIN_CAPITAL   (6),    // 동그라미 쳐진 A, B, C
        CIRCLED_LATIN_SMALL     (7),    // 동그라미 쳐진 a, b, c
        HANGLE_SYLLABLE         (8),    // 가, 나, 다
        CIRCLED_HANGUL_SYLLABLE (9),    // 동그라미 쳐진 가, 나, 다
        HANGUL_JAMO             (10),   // ㄱ, ㄴ, ㄷ
        CIRCLED_HANGUL_JAMO     (11),   // 동그라미 쳐진 ㄱ, ㄴ, ㄷ
        HANGUL_PHONETIC         (12),   // 일, 이 , 삼,
        IDEOGRAPH               (13),   // 一, 二, 三
        CIRCLED_IDEOGRAPH       (14),   // 동그라미 쳐진 一, 二, 三
        DECAGON_CIRCLE          (15),   // 갑, 을, 병, 정, 무, 기, 경, 신, 임, 계
        DECAGON_CRICLE_HANGJA   (16),   // 甲, 乙, 丙, 丁, 戊, 己, 庚, 辛, 壬, 癸
        SYMBOL                  (0x80), // 4가지 문자가 차례로 반복
        USER_CHAR               (0x81); // 사용자 지정 문자 반복

        private int num;
        private NumberShape2(int num) { 
            this.num = num;
        }
        public static NumberShape2 from(int num) {
            for (NumberShape2 shape: values()) {
                if (shape.num == num)
                    return shape;
            }
            return DIGIT;
        }
    }

    public static enum LineArrowStyle {
        NORMAL          (0),    // 모양없음
        ARROW           (1),    // 화살모양
        SPEAR           (2),    // 라인모양
        CONCAVE_ARROW   (3),    // 오목한 화살모양
        DIAMOND         (4),    // 속이 찬 다이아몬드 모양
        CIRCLE          (5),    // 속이 찬 원 모양
        BOX             (6),    // 속이 찬 사각모양
        EMPTY_DIAMOND   (7),    // 속이 빈 다이아몬드 모양
        EMPTY_CIRCLE    (8),    // 속이 빈 원 모양
        EMPTY_BOX       (9);    // 속이 빈 사각모양
        
        private int num;
        private LineArrowStyle(int num) { 
            this.num = num;
        }
        public static LineArrowStyle from(int num, boolean fill) {
            switch(num) {
            case 0:	
                return NORMAL;
            case 1: 
                return ARROW;
            case 2: 
                return SPEAR;
            case 3: 
                return CONCAVE_ARROW;
            case 4: 
            case 7:
                return fill?DIAMOND:EMPTY_DIAMOND;
            case 5:
            case 8:
                return fill?CIRCLE:EMPTY_CIRCLE;
            case 6:
            case 9:
                return fill?BOX:EMPTY_BOX;
            default:
                return NORMAL;
            }
        }
    }

    public static enum LineArrowSize {
        SMALL_SMALL         (0),    // 작은-작은
        SMALL_MEDIUM        (1),    // 작은-중간
        SMALL_LARGE         (2),    // 작은-큰
        MEDIUM_SMALL        (3),    // 중간-작은
        MEDIUM_MEDIUM       (4),    // 중간-중간
        MEDIUM_LARGE        (5),    // 중간-큰
        LARGE_SMALL         (6),    // 큰-작은
        LARGE_MEDIUM        (7),    // 큰-중간
        LARGE_LARGE         (8);    // 큰-큰

        private int num;
        private LineArrowSize(int num) { 
            this.num = num;
        }
        public static LineArrowSize from(int num) {
            for (LineArrowSize shape: values()) {
                if (shape.num == num)
                    return shape;
            }
            return MEDIUM_MEDIUM;
        }
    }

}
