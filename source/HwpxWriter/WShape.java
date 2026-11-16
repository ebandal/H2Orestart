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

import java.util.ArrayList;
import java.util.List;

/**
 * 그리기 개체: 그림(hp:pic) 또는 글상자(글이 든 사각형, hp:rect + hp:drawText).
 * 크기/위치 단위는 HWPUNIT.
 */
public class WShape {
    /** 그림, 글상자, 선, 사각형(글 없음), 타원, 호, 다각형, 곡선 */
    public enum Kind { PICTURE, TEXTBOX, LINE, RECT, ELLIPSE, ARC, POLYGON, CURVE }

    public final Kind kind;
    public int width;
    public int height;

    /** 글자처럼 취급 */
    public boolean treatAsChar;
    /** PAPER, PAGE, PARA */
    public String vertRelTo = "PARA";
    /** PAPER, PAGE, COLUMN, PARA */
    public String horzRelTo = "PARA";
    /** TOP, CENTER, BOTTOM */
    public String vertAlign = "TOP";
    /** LEFT, CENTER, RIGHT */
    public String horzAlign = "LEFT";
    public int vertOffset;
    public int horzOffset;
    /** 위치가 정렬(위/가운데/왼쪽...)이 아니라 기준에서의 거리로 정해졌는가 (내보내기에서만 쓴다) */
    public boolean vertByOffset;
    public boolean horzByOffset;
    /** SQUARE, TOP_AND_BOTTOM, BEHIND_TEXT, IN_FRONT_OF_TEXT */
    public String textWrap = "TOP_AND_BOTTOM";
    /** BOTH_SIDES, LEFT_ONLY, RIGHT_ONLY, LARGEST_ONLY */
    public String textFlow = "BOTH_SIDES";
    public int zOrder;

    // 그림
    /** content.hpf 의 항목 id (예: image1) */
    public String binItemId;
    /** 원본 그림 크기 (HWPUNIT) */
    public int imgWidth;
    public int imgHeight;

    // 글상자
    /** 테두리 선: NONE 이면 선 없음 */
    public String lineStyle = "NONE";
    public int lineColor;
    public int lineWidth;
    /** 채우기 색. null 이면 채우지 않음 */
    public Integer fillColor;
    /** 글과 테두리 사이 (왼쪽, 오른쪽, 위, 아래) */
    public final int[] textMargin = { 283, 283, 283, 283 };
    /** 글 세로 정렬: TOP, CENTER, BOTTOM */
    public String textVertAlign = "TOP";
    public final List<WParagraph> paragraphs = new ArrayList<WParagraph>();

    // 다각형, 곡선
    /** 꼭짓점(다각형) 또는 곡선이 지나는 점. 개체 왼쪽 위 기준 (HWPUNIT) */
    public final List<int[]> points = new ArrayList<int[]>();

    // 호
    /** NORMAL(호), PIE(부채꼴), CHORD(활) */
    public String arcType = "NORMAL";
    /** 중심, 제1축(시작), 제2축(끝) 좌표 */
    public final int[] arcCenter = new int[2];
    public final int[] arcAxis1 = new int[2];
    public final int[] arcAxis2 = new int[2];

    public WShape(Kind kind) {
        this.kind = kind;
    }
}
