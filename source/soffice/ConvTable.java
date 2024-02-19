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
package soffice;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.sun.star.uno.*;
import com.sun.star.uno.Exception;

import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.HwpElement.HwpRecord_Style;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_AutoNumber;
import HwpDoc.paragraph.Ctrl_Table;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.ParaText;
import HwpDoc.paragraph.TblCell;
import soffice.HwpCallback.TableFrame;

import com.sun.star.lang.*;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.table.BorderLine2;
import com.sun.star.table.BorderLineStyle;
import com.sun.star.table.TableBorder;
import com.sun.star.table.XCell;
import com.sun.star.table.XTableRows;
import com.sun.star.awt.Size;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.drawing.TextVerticalAdjust;
import com.sun.star.drawing.XShape;
import com.sun.star.frame.*;
import com.sun.star.text.*;

public class ConvTable {
    private static final Logger log = Logger.getLogger(ConvTable.class.getName());

    public static XDesktop xDesktop;
    public static XMultiServiceFactory xMSF;
    public static XMultiComponentFactory xMCF;
    public static XComponent doc;
    private static int autoNum = 0;

    public static void reset(WriterContext wContext) {
        autoNum = 0;
    }

    public static void endParagraph(XTextCursor cursor) {
        append(cursor, ControlCharacter.PARAGRAPH_BREAK);
    }

    public static void appendPara(XTextCursor cursor, String text) {
        append(cursor, text);
        append(cursor, ControlCharacter.PARAGRAPH_BREAK);
    }

    public static void append(XTextCursor cursor, String text) {
        cursor.setString(text);
        cursor.gotoEnd(false);
    }

    public static void append(XTextCursor cursor, short ctrlChar) {
        XText xText = cursor.getText();
        xText.insertControlCharacter(cursor, ctrlChar, false);
    }

    public static void insertTable(WriterContext wContext, Ctrl_Table table, short paraShapeID, HwpCallback callback,
            int step) {
        // 테이블 그리기 전, 문단모양 설정한다. 문단에 Frame을 넣기때문에 문단 margin에 맞게 여백이 들어가야 한다.
        HwpRecord_ParaShape paraShape = wContext.getParaShape((short) paraShapeID);
        XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
        XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
        ConvPara.setParagraphProperties(paraProps, paraShape, wContext.getDocInfo().compatibleDoc,
                ConvPara.PARA_SPACING);

        XTextFrame xFrame = null;
        XText xFrameText = null;
        XTextCursor xFrameCursor = null;

        try {
            TblCell[][] cellArray = new TblCell[table.nRows][table.nCols];
            for (int index = 0; index < table.cells.size(); index++) {
                TblCell cell = table.cells.get(index);
                cellArray[cell.rowAddr][cell.colAddr] = cell;
            }
            // HWP테이블에는 존재하지 않는 Cell이 포함되어 있는 경우가 있다.
            // 이 경우 TableColumnSeparator의 Position 계산이 어려워질뿐 아니라 CellMerge도 어려워진다. all null
            // 칼럼을 제거하고, colSpan을 조정한다.
            cellArray = removeAllNullColumns(cellArray);
            int maxColSize = Arrays.stream(cellArray).map(row -> row.length)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue()).get().getKey();

            Object tableObj = wContext.mMSF.createInstance("com.sun.star.text.TextTable");
            XTextTable xTextTable = UnoRuntime.queryInterface(XTextTable.class, tableObj);

            if (xTextTable == null) {
                log.severe("Could not create a text table.");
                return;
            }
            XPropertySet tableProps = UnoRuntime.queryInterface(XPropertySet.class, xTextTable);
            xTextTable.initialize(cellArray.length, maxColSize);

            if (callback != null && callback.onTableWithFrame() == TableFrame.MAKE) {
                xFrame = makeOuterFrame(wContext, table);
                xFrameText = xFrame.getText();
                xFrameCursor = xFrameText.createTextCursor();
                xFrameText.insertTextContent(xFrameCursor, xTextTable, false);
                if (wContext.version >= 72) {
                    XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);
                    TextContentAnchorType anchorType = (TextContentAnchorType) frameProps
                            .getPropertyValue("AnchorType");
                    if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                        xFrameText.insertString(xFrameCursor, " ", true);
                    }
                }
                addCaptionString(wContext, xFrameText, xFrameCursor, table, step + 1);
                step += 1;
            } else {
                wContext.mText.insertTextContent(wContext.mTextCursor, xTextTable, false);
                if (wContext.version >= 72) {
                    TextContentAnchorType anchorType = (TextContentAnchorType) tableProps
                            .getPropertyValue("AnchorType");

                    // 한컴에서 AS_CHAR 가 아니면 PARA_BREAK가 추가되어 있을것임, libreoffice에서는 ANCHOR를 붙일수 없으니 마지막
                    // PARA_BREAK를 제거하자.
                    // 여기서는 없애지 못하므로 (아래의 코드로도 PARA_BREAK 지우지 못한다. Table 이후 나오는 PARA_BREAK 한개를 생략한다.
                    // (oweParaBreak)
                    if (table.treatAsChar == false) {
                        HwpRecurs.removeLastParaBreak(wContext.mTextCursor);
                    }
                }
                // 캡션이 표시될 문단의 오른쪽 spacing을 임의로 조정
                HwpDoc.section.Page page = ConvPage.getCurrentPage().page;
                int rightSpace = page.width - page.marginLeft - page.marginRight - paraShape.marginLeft - table.width;
                addCaptionString(wContext, wContext.mText, wContext.mTextCursor, table, 0, rightSpace, step);
                if (callback != null && callback.onTableWithFrame() == TableFrame.MADE) {

                } else {
                    setParaPosition(tableProps, table, paraShape);
                }
                if (table.treatAsChar) {
                    tableProps.setPropertyValue("Split", false);
                } else {
                    if ((table.attr & 0x03) == 0x02) {
                        tableProps.setPropertyValue("Split", true);
                    } else {
                        tableProps.setPropertyValue("Split", false);
                    }
                }
            }

            TableBorder tBorder = new TableBorder();
            tBorder.LeftLine = Transform.toBorderLine((HwpRecord_BorderFill.Border) null);
            tBorder.IsLeftLineValid = true;
            tBorder.RightLine = Transform.toBorderLine((HwpRecord_BorderFill.Border) null);
            tBorder.IsRightLineValid = true;
            tBorder.TopLine = Transform.toBorderLine((HwpRecord_BorderFill.Border) null);
            tBorder.IsTopLineValid = true;
            tBorder.BottomLine = Transform.toBorderLine((HwpRecord_BorderFill.Border) null);
            tBorder.IsBottomLineValid = true;
            tBorder.VerticalLine = Transform.toBorderLine((HwpRecord_BorderFill.Border) null);
            tBorder.IsVerticalLineValid = true;
            tBorder.HorizontalLine = Transform.toBorderLine((HwpRecord_BorderFill.Border) null);
            tBorder.IsHorizontalLineValid = true;
            tableProps.setPropertyValue("TableBorder", tBorder);

            int width = Transform.translateHwp2Office(table.width);
            short sTableColumnRelativeSum = (Short) tableProps.getPropertyValue("TableColumnRelativeSum");
            double dRatio = (double) sTableColumnRelativeSum / (double) width;

            // adjust cell width
            // prepare column width values, so that adjust cell width.
            int[] colWidth = calculateColumnWidth(cellArray, table.width);
            for (int n = 0; n < colWidth.length; n++) {
                colWidth[n] = Transform.translateHwp2Office(colWidth[n]);
            }

            TableColumnSeparator[] xSeparators = UnoRuntime.queryInterface(TableColumnSeparator[].class,
                    tableProps.getPropertyValue("TableColumnSeparators"));
            double dPosition = 0;
            for (int col = 0; col < colWidth.length; col++) {
                dPosition += (colWidth[col] * dRatio > 1.0 ? colWidth[col] * dRatio : 1); // [20221106] position gap이 최소
                                                                                          // 1이상이 되도록 한다. 장기요양 서식.
                xSeparators[col].Position = (short) Math.ceil(dPosition);
            }
            tableProps.setPropertyValue("TableColumnSeparators", xSeparators);
            // table row 높이 조정
            XTableRows xTableRows = xTextTable.getRows();
            if (xTableRows != null) {
                int[] rowHeight = calculateRowHeight(cellArray, table.height);
                if (hasNullRow(rowHeight, table.height) == false) {
                    for (int row = 0; row < xTableRows.getCount(); row++) {
                        Object aRowObj = xTableRows.getByIndex(row);
                        XPropertySet tableRowProps = UnoRuntime.queryInterface(com.sun.star.beans.XPropertySet.class,
                                aRowObj);
                        tableRowProps.setPropertyValue("IsAutoHeight", false);
                        tableRowProps.setPropertyValue("Height", Transform.translateHwp2Office(rowHeight[row]));
                    }
                }
            }

            // merge cell and fill text.
            for (int row = 0; row < cellArray.length; row++) {

                for (int col = cellArray[row].length - 1; col >= 0; col--) {
                    TblCell cell = cellArray[row][col];

                    String cellAddr = mkCellNameBeforeMerge(cellArray, col, row, row - 1);

                    XCell xCell = xTextTable.getCellByName(cellAddr);
                    if (xCell != null) {
                        // 인접한 Cell Border때문에 border가 지워지지 않는 현상있음. 모든 Cell에 대해 border가 없는 상태로 먼저 만든다.
                        XPropertySet cellProps = UnoRuntime.queryInterface(XPropertySet.class, xCell);
                        HwpRecord_BorderFill.Border nullBorder = null;
                        cellProps.setPropertyValue("LeftBorder", Transform.toBorderLine(nullBorder));
                        cellProps.setPropertyValue("RightBorder", Transform.toBorderLine(nullBorder));
                        cellProps.setPropertyValue("TopBorder", Transform.toBorderLine(nullBorder));
                        cellProps.setPropertyValue("BottomBorder", Transform.toBorderLine(nullBorder));
                        // 테이블 경계선 근처에서 텍스트 잘리지 않도록 함
                        cellProps.setPropertyValue("LeftBorderDistance", 0);
                        cellProps.setPropertyValue("RightBorderDistance", 0);
                        cellProps.setPropertyValue("TopBorderDistance", 0);
                        cellProps.setPropertyValue("BottomBorderDistance", 0);
                        cellProps.setPropertyValue("BorderDistance", 0);
                    }
                    if (cell == null)
                        continue;

                    if (cell.colSpan > 1 || cell.rowSpan > 1) {
                        try {
                            // 한가지 방식으로 통일하면 좋겠으나 extension에서 동작하지 않아 각각 방식을 달리한다.
                            log.finest("CELL ADDR=" + cellAddr + " out of (" + cell.colAddr + ", " + cell.rowAddr
                                    + ") spans (" + cell.colSpan + "," + cell.rowSpan + ")");

                            XTextTableCursor xCellCursor = xTextTable.createCursorByCellName(cellAddr);
                            // column merge는 goRight() 후 mergeRange(). gotoCellByName()은 extension에서 동작 안함.
                            if (cell.colSpan > 1) {
                                boolean ret = xCellCursor.goRight((short) (cell.colSpan - 1), true);
                                log.finest("GoRight(" + (cell.colSpan - 1) + ") return=" + ret);
                                ret = xCellCursor.mergeRange();
                                log.finest("Merge=" + ret);
                            }
                            // row merge는 gotoCellByName() 후 mergeRange(). goRight()은 extension에서 동작 안함.
                            if (cell.rowSpan > 1) {
                                String cellAddr2 = mkCellNameBeforeMerge(cellArray, cell.colAddr + cell.colSpan - 1,
                                        cell.rowAddr + cell.rowSpan - 1, row - 1);
                                boolean ret = xCellCursor.gotoCellByName(cellAddr2, true);
                                log.finest("GotoCell(" + cellAddr2 + ") return=" + ret);
                                ret = xCellCursor.mergeRange();
                                log.finest("Merge=" + ret);
                            }
                        } catch (com.sun.star.uno.RuntimeException e1) {
                            e1.printStackTrace();
                        }
                    }

                    try {
                        // 셀을 병합한 후에 Border를 그린다.
                        if (xCell != null) {
                            XPropertySet cellProps = UnoRuntime.queryInterface(XPropertySet.class, xCell);
                            HwpRecord_BorderFill cellBorderFill = WriterContext.getBorderFill(cell.borderFill);
                            if (cellBorderFill != null) {
                                cellProps.setPropertyValue("LeftBorder", Transform.toBorderLine(cellBorderFill.left));
                                cellProps.setPropertyValue("RightBorder", Transform.toBorderLine(cellBorderFill.right));
                                cellProps.setPropertyValue("TopBorder", Transform.toBorderLine(cellBorderFill.top));
                                cellProps.setPropertyValue("BottomBorder", Transform.toBorderLine(cellBorderFill.bottom));
                            }
                            cellProps.setPropertyValue("LeftBorderDistance", Transform.translateHwp2Office(table.inLSpace));
                            cellProps.setPropertyValue("RightBorderDistance", Transform.translateHwp2Office(table.inRSpace));
                            cellProps.setPropertyValue("TopBorderDistance", Transform.translateHwp2Office(table.inUSpace));
                            cellProps.setPropertyValue("BottomBorderDistance", Transform.translateHwp2Office(table.inDSpace));
                            cellProps.setPropertyValue("VertOrient", Transform.toVertAlign(cell.verAlign.ordinal()));

                            if (cellBorderFill != null) {
                                if (cellBorderFill.fill.isColorFill()) {
                                    if (callback != null && callback.onTableWithFrame() == TableFrame.MAKE_PART) {
                                        cellProps.setPropertyValue("BackTransparent", true); // ZOrder 변경이 안되어 이런식으로 만듬.
                                    } else {
                                        cellProps.setPropertyValue("BackTransparent", false);
                                        cellProps.setPropertyValue("BackColor", cellBorderFill.fill.faceColor);
                                    }
                                } else if (cellBorderFill.fill.isGradFill()) {
                                    // CellProperties에는 Gradient 그릴 수 있는 속성이 없다. 중간색으로 칠한다.
                                    if (cellBorderFill.fill.colors.length==2) {
                                        short r, g, b;
                                        r = (short) (((cellBorderFill.fill.colors[0]>>16&0x00FF) + (cellBorderFill.fill.colors[1]>>16&0x00FF))/2);  
                                        g = (short) (((cellBorderFill.fill.colors[0]>>8&0x00FF) + (cellBorderFill.fill.colors[1]>>8&0x00FF))/2);  
                                        b = (short) (((cellBorderFill.fill.colors[0]&0x00FF) + (cellBorderFill.fill.colors[1]&0x00FF))/2);  
                                        int midColor = (r<<16)|(g<<8)|b;
                                        cellProps.setPropertyValue("BackColor", midColor);
                                    }
                                } else if (cellBorderFill.fill.isImageFill()) {
                                    ConvGraphics.fillGraphic(wContext, cellProps, cellBorderFill.fill);
                                } else {
                                    cellProps.setPropertyValue("BackTransparent", false);
                                }
                            }
                        }
                    } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                            | WrappedTargetException e) {
                        e.printStackTrace();
                    }

                    setCellPara(cellAddr, cell, xTextTable, table, wContext, callback, step);
                }
            }
        } catch (IllegalArgumentException | Exception e3) {
            e3.printStackTrace();
        }
    }

    private static XTextFrame makeOuterFrame(WriterContext wContext, Ctrl_Table table) throws Exception {
        XTextFrame xFrame = null;

        Object oFrame = wContext.mMSF.createInstance("com.sun.star.text.TextFrame");
        xFrame = (XTextFrame) UnoRuntime.queryInterface(XTextFrame.class, oFrame);
        if (xFrame == null) {
            log.severe("Could not create a text frame.");
            return xFrame;
        }

        XShape tfShape = UnoRuntime.queryInterface(XShape.class, xFrame);
        tfShape.setSize(
                new Size(Transform.translateHwp2Office(table.width), Transform.translateHwp2Office(table.height)));
        XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);

        setFramePosition(frameProps, table);
        setFrameWrapStyle(frameProps, table);

        BorderLine2 frameBorder = new BorderLine2();
        frameBorder.Color = 0x000000;
        frameBorder.LineStyle = BorderLineStyle.NONE;
        frameBorder.InnerLineWidth = 0;
        frameBorder.OuterLineWidth = 0;
        frameBorder.LineDistance = 0;
        frameBorder.LineWidth = 0;
        frameProps.setPropertyValue("TopBorder", frameBorder);
        frameProps.setPropertyValue("BottomBorder", frameBorder);
        frameProps.setPropertyValue("LeftBorder", frameBorder);
        frameProps.setPropertyValue("RightBorder", frameBorder);
        // margin 0으로
        frameProps.setPropertyValue("LeftMargin", 0);
        frameProps.setPropertyValue("RightMargin", 0);
        frameProps.setPropertyValue("TopMargin", 0);
        frameProps.setPropertyValue("BottomMargin", 0);
        // 안쪽여백을 0으로...
        frameProps.setPropertyValue("BorderDistance", 0);

        XText xText = wContext.mTextCursor.getText();
        xText.insertTextContent(wContext.mTextCursor, xFrame, false);
        if (wContext.version >= 72) {
            TextContentAnchorType anchorType = (TextContentAnchorType) frameProps.getPropertyValue("AnchorType");
            if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                xText.insertString(wContext.mTextCursor, " ", false);
            }
        }

        // TextFrame을 그린 후에 automaticHeight를 조정해야..
        frameProps.setPropertyValue("FrameIsAutomaticHeight", true);
        // frameProps.setPropertyValue("WidthType", SizeType.VARIABLE);
        frameProps.setPropertyValue("TextVerticalAdjust", TextVerticalAdjust.CENTER);
        wContext.mTextCursor.gotoEnd(false);

        return xFrame;
    }

    private static void setParaPosition(XPropertySet xProps, Ctrl_Table shape, HwpRecord_ParaShape paraShape) {
        int posX = 0;
        int posY = 0;

        HwpDoc.section.Page page = ConvPage.getCurrentPage().page;
        if (shape.treatAsChar == true) {
            posX = Transform.translateHwp2Office(shape.horzOffset) - Transform.translateHwp2Office(page.marginLeft);
            posX = Math.max(posX, 0);
            try {
                xProps.setPropertyValue("LeftMargin", posX);
                xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                switch (paraShape.align) {
                case LEFT: // 왼쪽 정렬
                    xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT);
                    break;
                case RIGHT: // 오른쪽 정렬
                    xProps.setPropertyValue("HoriOrient", HoriOrientation.RIGHT);
                    break;
                case CENTER: // 가운데 정렬
                    xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER);
                    break;
                case JUSTIFY: // 양쪽 정렬
                case DISTRIBUTE: // 배분 정렬
                case DISTRIBUTE_SPACE: // 나눔 정렬
                    xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);
                    break;
                }
            } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                    | WrappedTargetException e) {
                e.printStackTrace();
            }
        } else {
            try {
                switch (shape.vertRelTo) {
                case PARA:
                    switch (shape.vertAlign) {
                    case TOP:
                        posY = Transform.translateHwp2Office(shape.vertOffset);
                        if (posY > 0) {
                            xProps.setPropertyValue("TopMargin", posY);
                        }
                        break;
                    }
                    break;
                }
            } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                    | WrappedTargetException e) {
                e.printStackTrace();
            }

            try {
                switch (shape.horzRelTo) {
                case PAPER:
                    switch (shape.horzAlign) {
                    case LEFT: // LEFT
                    case INSIDE:
                        posX = Transform.translateHwp2Office(shape.horzOffset)
                                - Transform.translateHwp2Office(page.marginLeft);
                        posX = Math.max(posX, 0);
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    case CENTER:
                        posX = Transform.translateHwp2Office(page.width) / 2
                                + Transform.translateHwp2Office(shape.horzOffset);
                        posX = posX - Transform.translateHwp2Office(page.marginLeft);
                        posX = posX - Transform.translateHwp2Office(shape.width) / 2;
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    case RIGHT: // RIGHT
                    case OUTSIDE:
                        posX = Transform.translateHwp2Office(page.width)
                                - Transform.translateHwp2Office(page.marginLeft)
                                - Transform.translateHwp2Office(shape.horzOffset);
                        posX = posX - Transform.translateHwp2Office(shape.width);
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    }
                    break;
                case PAGE:
                    switch (shape.horzAlign) {
                    case LEFT: // LEFT
                    case INSIDE:
                        posX = Transform.translateHwp2Office(shape.horzOffset);
                        posX = Math.max(posX, 0);
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    case CENTER:
                        posX = (Transform.translateHwp2Office(page.width)
                                - Transform.translateHwp2Office(page.marginLeft)
                                - Transform.translateHwp2Office(page.marginRight)) / 2;
                        posX = posX + Transform.translateHwp2Office(shape.horzOffset)
                                - Transform.translateHwp2Office(shape.width) / 2;
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    case RIGHT: // RIGHT
                    case OUTSIDE:
                        posX = Transform.translateHwp2Office(page.width)
                                - Transform.translateHwp2Office(page.marginLeft)
                                - Transform.translateHwp2Office(page.marginRight);
                        posX = posX - Transform.translateHwp2Office(shape.horzOffset)
                                - Transform.translateHwp2Office(shape.width);
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    }
                    break;
                case COLUMN:
                case PARA:
                    switch (shape.horzAlign) {
                    case LEFT: // LEFT. 왼쪽맞춤 일때 width,rightMargin이 필요(width+rightMargin이 전체 Para Width). 왼쪽에서부터
                               // 일때 width,leftMargin이 필요(width+leftMargin이 전체 width일 필요 없음).
                    case INSIDE:
                        posX = Transform.translateHwp2Office(shape.horzOffset)
                                + Transform.translateHwp2Office(paraShape.marginLeft / 2);
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    case CENTER: // 가운데맞춤일때 width,leftMargin이 필요 (width+leftMagin+leftMargin이 전체 width.
                                 // leftMargin=rightMargin 으로 봄)
                        posX = (Transform.translateHwp2Office(page.width)
                                - Transform.translateHwp2Office(page.marginLeft)
                                - Transform.translateHwp2Office(page.marginRight)) / 2;
                        posX = posX + Transform.translateHwp2Office(shape.horzOffset)
                                - Transform.translateHwp2Office(shape.width) / 2;
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    case RIGHT: // RIGHT. 오른쪽맞춤 일때, width, leftMargin이 필요(width+leftMargin이 전체 Para Width 이어야 함)
                    case OUTSIDE:
                        posX = Transform.translateHwp2Office(page.width)
                                - Transform.translateHwp2Office(page.marginLeft)
                                - Transform.translateHwp2Office(page.marginRight);
                        posX = posX - Transform.translateHwp2Office(shape.horzOffset)
                                - Transform.translateHwp2Office(shape.width);
                        xProps.setPropertyValue("LeftMargin", posX);
                        xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT_AND_WIDTH);
                        xProps.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
                        break;
                    }
                    break;
                }
            } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                    | WrappedTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private static void setParaWrapStyle(XPropertySet xPropSet, Ctrl_Table shape) {

        // wrapStyle; // 0:어울림, 1:자리차지, 2:글 뒤로, 3:글 앞으로
        // wrapText; // 0:양쪽, 1:왼쪽, 2:오른쪽, 3:큰쪽
        try {
            switch (shape.textWrap) {
            case SQUARE: // 어울림
                WrapTextMode wrapText = WrapTextMode.NONE;
                switch (shape.textFlow) {
                case 0x0: // 양쪽
                    wrapText = WrapTextMode.PARALLEL;
                    break;
                case 0x1: // 왼쪽
                    wrapText = WrapTextMode.LEFT;
                    break;
                case 0x2: // 오른쪽
                    wrapText = WrapTextMode.RIGHT;
                    break;
                case 0x3: // 큰쪽
                    wrapText = WrapTextMode.DYNAMIC;
                    break;
                }
                xPropSet.setPropertyValue("TextWrap", wrapText);
                break;
            case TOP_AND_BOTTOM: // 자리차지
                xPropSet.setPropertyValue("TextWrap", WrapTextMode.NONE);
                break;
            case BEHIND_TEXT: // 글 뒤로
                xPropSet.setPropertyValue("TextWrap", WrapTextMode.THROUGH);
                break;
            case IN_FRONT_OF_TEXT: // 글 앞으로
                xPropSet.setPropertyValue("TextWrap", WrapTextMode.THROUGH);
                break;
            }
        } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                | WrappedTargetException e) {
            e.printStackTrace();
        }
    }

    private static void setFramePosition(XPropertySet xProps, Ctrl_Table table) {
        int posX = 0;
        int posY = 0;

        try {
            if (table.treatAsChar == true) {
                xProps.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
                xProps.setPropertyValue("VertOrient", VertOrientation.CENTER); // Top, Bottom, Center, fromBottom
                // xProps.setPropertyValue("VertOrientPosition", posY);
                xProps.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE); // Base line, Character, Row

                xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER); // 0:NONE=From left
                xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA); // 1:paragraph text area
            } else {
                HwpDoc.section.Page page = ConvPage.getCurrentPage().page;

                switch (table.vertRelTo) {
                case PAPER: // Anchor to Page
                    xProps.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
                    switch (table.vertAlign) {
                    case TOP:
                        xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_FRAME); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.vertOffset == 0) {
                            xProps.setPropertyValue("VertOrient", VertOrientation.TOP); // 1:Top, 2:Bottom, 2:Center,
                                                                                        // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("VertOrient", VertOrientation.NONE); // 0:NONE=From top
                            // page상단으로부터 frame상단까지의 offset
                            posY = Transform.translateHwp2Office(table.vertOffset);
                            xProps.setPropertyValue("VertOrientPosition", posY);
                        }
                        break;
                    case CENTER:
                        xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_FRAME); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.vertOffset == 0) {
                            xProps.setPropertyValue("VertOrient", VertOrientation.CENTER); // 1:Top, 2:Bottom, 2:Center,
                                                                                           // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("VertOrient", VertOrientation.NONE); // 0:NONE=From top
                            // 중간지점에서 frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
                            posY = (Transform.translateHwp2Office(page.height)
                                    - Transform.translateHwp2Office(table.height)) / 2
                                    + Transform.translateHwp2Office(table.vertOffset);
                            xProps.setPropertyValue("VertOrientPosition", posY);
                        }
                        break;
                    case BOTTOM:
                        xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_FRAME); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.vertOffset == 0) {
                            xProps.setPropertyValue("VertOrient", VertOrientation.BOTTOM); // 1:Top, 2:Bottom, 2:Center,
                                                                                           // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("VertOrient", VertOrientation.NONE); // 0:NONE=From top
                            // page하단에서 frame 하단까지의 offset을 -> page상단부터의 frame상단까지 offset으로 계산
                            posY = Transform.translateHwp2Office(page.height)
                                    - Transform.translateHwp2Office(table.height)
                                    - Transform.translateHwp2Office(table.vertOffset);
                            xProps.setPropertyValue("VertOrientPosition", posY);
                        }
                        break;
                    }
                    break;
                case PAGE:
                    // 그림에서 AnchorType을 AT_PAGE로 줄때 crash 발생
                    xProps.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
                    switch (table.vertAlign) {
                    case TOP:
                        xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_PRINT_AREA); // 7:EntirePage,
                                                                                                       // 8:PageTextArea
                        if (table.vertOffset == 0) {
                            xProps.setPropertyValue("VertOrient", VertOrientation.TOP); // 1:Top, 2:Bottom, 2:Center,
                                                                                        // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("VertOrient", VertOrientation.NONE); // 0:NONE=From top
                            // page상단으로부터 frame상단까지의 offset
                            posY = Transform.translateHwp2Office(table.vertOffset);
                            xProps.setPropertyValue("VertOrientPosition", posY);
                        }
                        break;
                    case CENTER:
                        xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_PRINT_AREA); // 7:EntirePage,
                                                                                                       // 8:PageTextArea
                        if (table.vertOffset == 0) {
                            xProps.setPropertyValue("VertOrient", VertOrientation.CENTER); // 1:Top, 2:Bottom, 2:Center,
                                                                                           // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("VertOrient", VertOrientation.NONE); // 0:NONE=From top
                            // 중간지점에서 frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
                            int pageHeight = Transform.translateHwp2Office(page.height)
                                    - Transform.translateHwp2Office(page.marginTop)
                                    - Transform.translateHwp2Office(page.marginBottom);
                            posY = (pageHeight - Transform.translateHwp2Office(table.height)) / 2;
                            posY += Transform.translateHwp2Office(table.vertOffset);
                            xProps.setPropertyValue("VertOrientPosition", posY);
                        }
                        break;
                    case BOTTOM:
                        xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_PRINT_AREA); // 7:EntirePage,
                                                                                                       // 8:PageTextArea
                        if (table.vertOffset == 0) {
                            xProps.setPropertyValue("VertOrient", VertOrientation.BOTTOM); // 1:Top, 2:Bottom, 2:Center,
                                                                                           // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("VertOrient", VertOrientation.NONE); // 0:NONE=From top
                            // 쪽 하단에서 frame 하단까지의 offset을 -> 쪽 상단부터의 frame상단까지 offset으로 계산
                            int pageHeight = Transform.translateHwp2Office(page.height)
                                    - Transform.translateHwp2Office(page.marginTop)
                                    - Transform.translateHwp2Office(page.marginBottom);
                            posY = pageHeight - Transform.translateHwp2Office(table.height)
                                    - Transform.translateHwp2Office(table.vertOffset);
                            xProps.setPropertyValue("VertOrientPosition", posY);
                        }
                        break;
                    }
                    break;
                case PARA:
                    xProps.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
                    switch (table.vertAlign) {
                    case TOP:
                        xProps.setPropertyValue("VertOrientRelation", RelOrientation.PRINT_AREA); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.vertOffset == 0) {
                            xProps.setPropertyValue("VertOrient", VertOrientation.TOP); // 1:Top, 2:Bottom, 2:Center,
                                                                                        // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("VertOrient", VertOrientation.NONE); // 0:NONE=From top
                            // para상단으로부터 frame상단까지의 offset
                            posY = Transform.translateHwp2Office(table.vertOffset);
                            xProps.setPropertyValue("VertOrientPosition", posY);
                        }
                        break;
                    }
                    break;

                }

                switch (table.horzRelTo) {
                case PAPER:
                    switch (table.horzAlign) {
                    case LEFT: // LEFT
                    case INSIDE:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_FRAME); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT); // 1:Top, 2:Bottom, 2:Center,
                                                                                         // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From top
                            // page상단으로부터 frame상단까지의 offset
                            posX = Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    case CENTER:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_FRAME); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER); // 1:Top, 2:Bottom, 2:Center,
                                                                                           // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From top
                            // 중간지점에서 frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
                            posX = (Transform.translateHwp2Office(page.width)
                                    - Transform.translateHwp2Office(table.width)) / 2
                                    + Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    case RIGHT: // RIGHT
                    case OUTSIDE:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_FRAME); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.RIGHT); // 1:Top, 2:Bottom, 2:Center,
                                                                                          // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From top
                            // page하단에서 frame 하단까지의 offset을 -> page상단부터의 frame상단까지 offset으로 계산
                            posX = Transform.translateHwp2Office(page.width)
                                    - Transform.translateHwp2Office(table.width)
                                    - Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    }
                    break;
                case PAGE:
                    switch (table.horzAlign) {
                    case LEFT: // LEFT
                    case INSIDE:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_PRINT_AREA); // 7:EntirePage,
                                                                                                       // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT); // 1:Top, 2:Bottom, 2:Center,
                                                                                         // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From left
                            // page상단으로부터 frame상단까지의 offset
                            posX = Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    case CENTER:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_PRINT_AREA); // 7:EntirePage,
                                                                                                       // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER); // 1:Top, 2:Bottom, 2:Center,
                                                                                           // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From left
                            // 중간지점에서 frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
                            int pageWidth = Transform.translateHwp2Office(page.width)
                                    - Transform.translateHwp2Office(page.marginLeft)
                                    - Transform.translateHwp2Office(page.marginRight);
                            posX = (pageWidth - Transform.translateHwp2Office(table.width)) / 2;
                            posX += Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    case RIGHT: // RIGHT
                    case OUTSIDE:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_PRINT_AREA); // 7:EntirePage,
                                                                                                       // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.RIGHT); // 1:Top, 2:Bottom, 2:Center,
                                                                                          // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From left
                            // page하단에서 frame 하단까지의 offset을 -> page상단부터의 frame상단까지 offset으로 계산
                            int pageWidth = Transform.translateHwp2Office(page.width)
                                    - Transform.translateHwp2Office(page.marginLeft)
                                    - Transform.translateHwp2Office(page.marginRight);
                            posX = pageWidth - Transform.translateHwp2Office(table.width)
                                    - Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    }
                    break;
                case COLUMN:
                case PARA:
                    switch (table.horzAlign) {
                    case LEFT: // LEFT
                    case INSIDE:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA); // 1:paragraph text
                                                                                                  // area
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT); // 1:Top, 2:Bottom, 2:Center,
                                                                                         // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From left
                            // page상단으로부터 frame상단까지의 offset
                            posX = Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    case CENTER:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER); // 1:Top, 2:Bottom, 2:Center,
                                                                                           // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From left
                            // 중간지점에서 frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
                            int pageWidth = Transform.translateHwp2Office(page.width)
                                    - Transform.translateHwp2Office(page.marginLeft)
                                    - Transform.translateHwp2Office(page.marginRight);
                            posX = (pageWidth - Transform.translateHwp2Office(table.width)) / 2;
                            posX += Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    case RIGHT: // RIGHT
                    case OUTSIDE:
                        xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA); // 7:EntirePage,
                                                                                                  // 8:PageTextArea
                        if (table.horzOffset == 0) {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.RIGHT); // 1:Top, 2:Bottom, 2:Center,
                                                                                          // 0:NONE(From top)
                        } else {
                            xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE); // 0:NONE=From left
                            // page하단에서 frame 하단까지의 offset을 -> page상단부터의 frame상단까지 offset으로 계산
                            int pageWidth = Transform.translateHwp2Office(page.width)
                                    - Transform.translateHwp2Office(page.marginLeft)
                                    - Transform.translateHwp2Office(page.marginRight);
                            posX = pageWidth - Transform.translateHwp2Office(table.width)
                                    - Transform.translateHwp2Office(table.horzOffset);
                            xProps.setPropertyValue("HoriOrientPosition", posX);
                        }
                        break;
                    }
                    break;
                }
            }
        } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                | WrappedTargetException e) {
            e.printStackTrace();
        }
    }

    private static void setFrameWrapStyle(XPropertySet xPropSet, Ctrl_Table shape) {

        // wrapStyle; // 0:어울림, 1:자리차지, 2:글 뒤로, 3:글 앞으로
        // wrapText; // 0:양쪽, 1:왼쪽, 2:오른쪽, 3:큰쪽
        try {
            switch (shape.textWrap) {
            case SQUARE: // 어울림
                xPropSet.setPropertyValue("Opaque", true);
                xPropSet.setPropertyValue("AllowOverlap", true); // THROUGH에서는 효과 없음.

                WrapTextMode wrapText = WrapTextMode.NONE;
                switch (shape.textFlow) {
                case 0x0: // 양쪽
                    wrapText = WrapTextMode.PARALLEL;
                    break;
                case 0x1: // 왼쪽
                    wrapText = WrapTextMode.LEFT;
                    break;
                case 0x2: // 오른쪽
                    wrapText = WrapTextMode.RIGHT;
                    break;
                case 0x3: // 큰쪽
                    wrapText = WrapTextMode.DYNAMIC;
                    break;
                }
                if (shape.treatAsChar == false) {
                    xPropSet.setPropertyValue("SurroundContour", false);// contour는 THROUGH에서는 효과 없음
                }
                xPropSet.setPropertyValue("TextWrap", wrapText);
                break;
            case TOP_AND_BOTTOM: // 자리차지
                xPropSet.setPropertyValue("Opaque", true);
                if (shape.treatAsChar == false) {
                    xPropSet.setPropertyValue("AllowOverlap", true); // THROUGH에서는 효과 없음.
                }
                xPropSet.setPropertyValue("TextWrap", WrapTextMode.NONE);
                break;
            case BEHIND_TEXT: // 글 뒤로
                xPropSet.setPropertyValue("Opaque", false);
                if (shape.treatAsChar == false) {
                    xPropSet.setPropertyValue("AllowOverlap", true); // THROUGH에서는 효과 없음.
                    xPropSet.setPropertyValue("IsAutomaticContour", false);
                }
                xPropSet.setPropertyValue("TextWrap", WrapTextMode.THROUGH);
                break;
            case IN_FRONT_OF_TEXT: // 글 앞으로
                xPropSet.setPropertyValue("Opaque", true);
                if (shape.treatAsChar == false) {
                    xPropSet.setPropertyValue("AllowOverlap", true); // THROUGH에서는 효과 없음.
                }
                xPropSet.setPropertyValue("TextWrap", WrapTextMode.THROUGH);
                break;
            }
            // xPropSet.setPropertyValue("ZOrder", shape.zOrder);
        } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                | WrappedTargetException e) {
            e.printStackTrace();
        }
    }

    static void addCaptionString(WriterContext wContext, XText xFrameText, XTextCursor xFrameCursor, Ctrl_Table table,
            int step) {
        addCaptionString(wContext, xFrameText, xFrameCursor, table, 0, 0, step);
    }

    static void addCaptionString(WriterContext wContext, XText xFrameText, XTextCursor xFrameCursor, Ctrl_Table table,
            int leftSpacing, int rightSpacing, int step) {
        if (table.caption == null || table.caption.size() == 0)
            return;

        XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, xFrameCursor);
        XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);

        List<String> capStr = new ArrayList<String>();
        short[] charShapeID = new short[1];
        Optional<Ctrl> ctrlOp = table.caption.stream().filter(c -> c.p != null)
        		                             .flatMap(c -> c.p.stream())
        		                             .filter(c -> c instanceof ParaText).findFirst();
        if (ctrlOp.isPresent()) {
            charShapeID[0] = (short) ((ParaText) ctrlOp.get()).charShapeId;
        }

        HwpCallback callback = new HwpCallback() {
            @Override
            public void onAutoNumber(Ctrl_AutoNumber autoNumber, int paraStyleID, int paraShapeID) {
                capStr.add(Integer.toString(++autoNum));
            };

            @Override
            public boolean onTab(String info) {
                capStr.add("\t");
                return true;
            };

            @Override
            public boolean onText(String content, int charShapeId, int charPos, boolean append) {
                capStr.add(content);
                charShapeID[0] = (short) charShapeId;
                return true;
            }

            @Override
            public boolean onParaBreak() {
                capStr.add("\r");
                return true;
            }
        };
        HwpRecurs.printParaRecurs(wContext, wContext, table.caption.get(0), callback, step);
        if (capStr.size() > 0) {
            if (step > 2) { // 본문위에 table은 step=2, Frame내에 들어간 table은 step=3
                if (capStr.get(capStr.size() - 1).equals("\r")) { // 마지막이 PARA_BREAK라면 출력하지 않음.
                    capStr.remove(capStr.size() - 1);
                }
            }

            String styleName = ConvPara.getStyleName((int) table.caption.get(0).paraStyleID);
            HwpRecord_ParaShape captionParaShape = wContext.getParaShape(table.caption.get(0).paraShapeID);
            HwpRecord_CharShape captionCharShape = wContext.getCharShape(charShapeID[0]);

            try {
                paraProps.setPropertyValue("ParaStyleName", styleName);
                ConvPara.setParagraphProperties(paraProps, captionParaShape, wContext.getDocInfo().compatibleDoc, ConvPara.PARA_SPACING);
                ConvPara.setCharacterProperties(paraProps, captionCharShape, step);
                paraProps.setPropertyValue("ParaTopMargin", Transform.translateHwp2Office(table.captionSpacing));
                paraProps.setPropertyValue("ParaBottomMargin", Transform.translateHwp2Office(table.captionSpacing));
                paraProps.setPropertyValue("ParaLeftMargin", Transform.translateHwp2Office(leftSpacing));
                paraProps.setPropertyValue("ParaRightMargin", Transform.translateHwp2Office(rightSpacing));
                for (String cap : capStr) {
                    xFrameText.insertString(xFrameCursor, cap, false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String mkCellNameBeforeMerge(TblCell[][] cellArray, int x, int y, int mergedY) {
        // cell에 대한 알파벳을 가져오려면,
        // null을 포함하고 (아직 머지하기 전의 알파벳을 구해야 하므로), nullColCount
        // 이전 row에서 rowpan으로 인한 colspan 분량을 빼고, otherRowsColSpan
        // 현재 row에서 자리 차지하는 알파벳 갯수 증가. curRowsCount

        int nullColCount = 0;
        for (int col = 0; col < x; col++) {
            if (cellArray[y][col] == null) {
                nullColCount++;
            }
        }

        int otherRowsColSpan = Arrays.stream(cellArray).flatMap(row -> Arrays.stream(row))
                .filter(cell -> cell != null && cell.rowAddr <= mergedY && cell.colAddr <= x
                        && cell.rowAddr + (cell.rowSpan - 1) >= y)
                .map(cell -> Integer.valueOf(cell.colSpan)).reduce(0, Integer::sum);

        int curRowsCount = (int) Arrays.stream(cellArray).flatMap(row -> Arrays.stream(row))
                .filter(cell -> cell != null)
                .filter(cell -> (cell.rowAddr <= mergedY && cell.rowAddr + (cell.rowSpan - 1) >= y && cell.colAddr < x)
                        || (cell.rowAddr == y && cell.colAddr < x))
                .count();

        int xx = (nullColCount + curRowsCount - otherRowsColSpan) / 26;
        int xxx = (nullColCount + curRowsCount - otherRowsColSpan) % 26;
        return (xx > 0 ? "" + ((char) ('a' + (xxx))) : "" + ((char) ('A' + xxx))) + (y + 1);
    }

    private static void setCellPara(String cellName, TblCell cell, XTextTable xtable, Ctrl_Table table,
            WriterContext wContext, HwpCallback cb, int step) {
        XCell xCell = xtable.getCellByName(cellName);
        if (xCell == null) {
            log.fine("Can't get CellName:" + cellName);
            return;
        }
        XText cellText = UnoRuntime.queryInterface(XText.class, xCell);
        if (cellText == null) {
            log.fine("Can't get CellText");
            return;
        }
        XTextCursor cellCursor = cellText.createTextCursor();
        if (cellCursor == null)
            return;

        WriterContext childContext = new WriterContext();
        childContext.mContext = wContext.mContext;
        childContext.mDesktop = wContext.mDesktop;
        childContext.mMCF = wContext.mMCF;
        childContext.mMSF = wContext.mMSF;
        childContext.mMyDocument = wContext.mMyDocument;
        childContext.mText = cellText;
        childContext.mTextCursor = cellCursor;

        // parabreak 앞으로 이동
        cellCursor.gotoStart(false);

        for (int paraIndex = 0; paraIndex < cell.paras.size(); paraIndex++) {
            boolean isLastPara = (paraIndex == cell.paras.size() - 1) ? true : false;
            HwpParagraph para = cell.paras.get(paraIndex);

            String styleName = ConvPara.getStyleName((int) para.paraStyleID);
            log.finer("StyleID=" + para.paraStyleID + ", StyleName=" + styleName);
            if (styleName == null || styleName.isEmpty()) {
                log.fine("Style Name is empty");
            }

            short[] charShapeID = new short[1];
            if (para.p != null) {
                Optional<Ctrl> ctrlOp = para.p.stream().filter(c -> (c instanceof ParaText)).findFirst();
                if (ctrlOp.isPresent()) {
                    charShapeID[0] = (short) ((ParaText) ctrlOp.get()).charShapeId;
                }
            }
            HwpRecord_Style paraStyle = wContext.getParaStyle(para.paraStyleID);
            HwpRecord_ParaShape paraShape = wContext.getParaShape(para.paraShapeID);
            HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID[0]);

            if (para.p == null || para.p.size() == 0) {
                if (isLastPara == false) {
                    HwpRecurs.insertParaString(childContext, "\r", para.lineSegs, styleName, paraStyle, paraShape,
                            charShape, true, true, step);
                }
                continue;
            }

            HwpCallback callback = new HwpCallback() {
                @Override
                public void onAutoNumber(Ctrl_AutoNumber autoNumber, int paraStyleID, int paraShapeID) {
                };

                @Override
                public boolean onTab(String info) {
                    HwpRecurs.insertParaString(childContext, "\t", para.lineSegs, styleName, paraStyle, paraShape,
                            charShape, true, true, step);
                    return true;
                };

                @Override
                public boolean onText(String content, int charShapeId, int charPos, boolean append) {
                    String styleNameTemp = ConvPara.getStyleName((int) para.paraStyleID);
                    HwpRecord_Style paraStyleTemp = wContext.getParaStyle(para.paraStyleID);
                    HwpRecord_ParaShape paraShapeTemp = wContext.getParaShape(para.paraShapeID);
                    if (cell.paras.size()==1) {
                    	// Cell내 문단이 1개만 있는 경우, 선 간격을 최소로 한다. LibreOffice와 Hwp 간격 차이를 해소하기 위함.
                    	paraShapeTemp.lineSpacing = 100;
                    	paraShapeTemp.lineSpacingType = 0x3;
                    }
                    HwpRecord_CharShape charShapeTemp = wContext.getCharShape((short) charShapeId);
                    HwpRecurs.insertParaString(childContext, content, para.lineSegs, styleNameTemp, paraStyleTemp,
                            paraShapeTemp, charShapeTemp, append, true, step);
                    return true;
                }

                @Override
                public boolean onParaBreak() {
                    if (isLastPara == false) {
                        HwpRecurs.insertParaString(childContext, "\r", para.lineSegs, styleName, paraStyle, paraShape,
                                charShape, true, true, step);
                    }
                    return true;
                }
            };
            HwpRecurs.printParaRecurs(childContext, wContext, para, callback, 2);
        }
    }

    private static TblCell[][] removeAllNullColumns(TblCell[][] cellArray) {
        TblCell[][] newCellArray = null;
        List<Integer> allNullColList = new ArrayList<Integer>();

        int rowSize = cellArray.length;
        int maxColSize = Arrays.stream(cellArray).map(row -> row.length)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .max(Map.Entry.comparingByValue()).get().getKey();

        for (int col = 0; col < maxColSize; col++) {
            int nCol = col;
            boolean exists = IntStream.range(0, rowSize).mapToObj(r -> cellArray[r][nCol]).anyMatch(c -> c != null);
            if (exists == false) {
                allNullColList.add(col);
            }
        }

        if (allNullColList.size() > 0) {
            // Collections.reverse(allNullColList);
            newCellArray = new TblCell[cellArray.length][maxColSize - allNullColList.size()];
            for (int oldCol = 0, newCol = 0; oldCol < maxColSize; oldCol++) {
                if (allNullColList.contains(oldCol) == false) {
                    // all null 칼럼이 아니면,
                    for (int row = 0; row < newCellArray.length; row++) {
                        newCellArray[row][newCol] = cellArray[row][oldCol];
                        if (newCellArray[row][newCol] != null) {
                            newCellArray[row][newCol].rowAddr = (short) row;
                            newCellArray[row][newCol].colAddr = (short) newCol;
                        }
                    }
                    newCol++;
                } else {
                    // all null 칼럼이라면,
                    for (int row = 0; row < newCellArray.length; row++) {
                        log.finest("Removing null column. originalCol=" + oldCol + ", row=" + row);
                        if (newCol > 0) {
                            int nRow = row, nNewCol = newCol;
                            Optional<Integer> lastColOp = Arrays.stream(newCellArray[row]).filter(c -> c != null)
                                    .filter(c -> c.colAddr < nNewCol).map(c -> Integer.valueOf(c.colAddr))
                                    .reduce(Integer::max);
                            if (lastColOp.isPresent()) {
                                int lastCol = lastColOp.get();
                                if (newCellArray[row][lastCol].colSpan > 1) {
                                    newCellArray[row][lastCol].colSpan -= 1;
                                }
                            }
                        }
                    }
                }
            }

        } else {
            newCellArray = cellArray;
        }
        return newCellArray;
    }

    private static int calculateEquasion(List<TblCell> list, int[] colWidth, int col, int span, boolean isColumn) {
        int ret = 0;

        if (span == 1) {
            for (TblCell cell : list) {
                if (isColumn) {
                    if (cell.width > 0 && cell.colSpan == 1 && cell.colAddr == col) {
                        ret = cell.width;
                        break;
                    }
                } else {
                    if (cell.height > 0 && cell.rowSpan == 1 && cell.rowAddr == col) {
                        ret = cell.height;
                        break;
                    }
                }
            }
        } else {
            // 예) span==5 일때
            // [-4] [-3] [-2] [-1] [ 0] [ ] [ ] [ ] [ ]
            // [ ] [-3] [-2] [-1] [ 0] [ 1] [ ] [ ] [ ]
            // [ ] [ ] [-2] [-1] [ 0] [ 1] [ 2] [ ] [ ]
            // [ ] [ ] [ ] [-1] [ 0] [ 1] [ 2] [ 3] [ ]
            // [ ] [ ] [ ] [ ] [ 0] [ 1] [ 2] [ 3] [ 4]
            // int span_width[] = new int[5];
            // for (TblCell cell: list) {
            // if (cell.width>0 && cell.colSpan==5 && cell.colAddr==col-4) {
            // span_width[0] = cell.width;
            // } else if (cell.width>0 && cell.colSpan==5 && cell.colAddr==col-3) {
            // span_width[1] = cell.width;
            // } else if (cell.width>0 && cell.colSpan==5 && cell.colAddr==col-2) {
            // span_width[2] = cell.width;
            // } else if (cell.width>0 && cell.colSpan==5 && cell.colAddr==col-1) {
            // span_width[3] = cell.width;
            // } else if (cell.width>0 && cell.colSpan==5 && cell.colAddr==col) {
            // span_width[4] = cell.width;
            // }
            // }
            // if (span_width[0]>0 && colWidth[col-4]>0 && colWidth[col-3]>0 &&
            // colWidth[col-2]>0 && colWidth[col-1]>0) {
            // ret = span_width[0] - colWidth[col-4] - colWidth[col-3] - colWidth[col-2] -
            // colWidth[col-1];
            // } else if (span_width[1]>0 && colWidth[col-3]>0 && colWidth[col-2]>0 &&
            // colWidth[col-1]>0 && colWidth[col+1]>0) {
            // ret = span_width[1] - colWidth[col-3] - colWidth[col-2] - colWidth[col-1] -
            // colWidth[col+1];
            // } else if (span_width[2]>0 && colWidth[col-2]>0 && colWidth[col-1]>0 &&
            // colWidth[col+1]>0 && colWidth[col+2]>0) {
            // ret = span_width[2] - colWidth[col-2] - colWidth[col-1] - colWidth[col+1] -
            // colWidth[col+2];
            // } else if (span_width[3]>0 && colWidth[col-1]>0 && colWidth[col+1]>0 &&
            // colWidth[col+2]>0 && colWidth[col+3]>0) {
            // ret = span_width[3] - colWidth[col-1] - colWidth[col+1] - colWidth[col+2] -
            // colWidth[col+3];
            // } else if (span_width[4]>0 && colWidth[col+1]>0 && colWidth[col+2]>0 &&
            // colWidth[col+3]>0 && colWidth[col+4]>0) {
            // ret = span_width[4] - colWidth[col+1] - colWidth[col+2] - colWidth[col+3] -
            // colWidth[col+4];
            // }

            int span_width[] = new int[span];
            for (TblCell cell : list) {
                for (int idx = 0; idx < span; idx++) {
                    if (isColumn) {
                        if (cell.width > 0 && cell.colSpan == span && cell.colAddr == col - (span - 1) + idx) {
                            span_width[idx] = cell.width;
                        }
                    } else {
                        if (cell.height > 0 && cell.rowSpan == span && cell.rowAddr == col - (span - 1) + idx) {
                            span_width[idx] = cell.height;
                        }
                    }
                }
            }
            for (int idx = 0; idx < span; idx++) {
                boolean proceed = true;
                if (span_width[idx] <= 0)
                    continue;

                for (int colStart = col - (span - 1) + idx; colStart <= col + idx; colStart++) {
                    if (colStart == col)
                        continue;
                    if (colWidth[colStart] <= 0) {
                        proceed = false;
                        break;
                    }
                }

                if (proceed) {
                    ret = span_width[idx];
                    for (int colStart = col - (span - 1) + idx; colStart <= col + idx; colStart++) {
                        if (colStart == col)
                            continue;
                        ret -= colWidth[colStart];
                    }
                    break;
                }
            }
        }

        return ret;
    }

    private static int[] calculateColumnWidth(TblCell[][] cellArray, int totalWidth) {
        int nWidth = cellArray[0].length;
        int[] colWidth = new int[nWidth];

        // print
        for (int row = 0; row < cellArray.length; row++) {
            log.finest("CELLS [" + row + "]=" + Arrays.stream(cellArray[row])
                    .map(cell -> cell == null ? "" : String.valueOf(cell.width)).collect(Collectors.joining(",")));
        }

        // span loop
        for (int span = 1; span < nWidth; span++) {
            if (Arrays.stream(colWidth).filter(w -> w == 0).count() == 0) {
                log.finest("Span Loop:"
                        + Arrays.stream(colWidth).mapToObj(w -> String.valueOf(w)).collect(Collectors.joining(",")));
                break;
            }
            // column loop
            for (int col = 0; col < nWidth; col++) {
                if (colWidth[col] != 0)
                    continue;
                int nSpan = span;
                int nCol = col;
                List<TblCell> colWidthList = Arrays.stream(cellArray).flatMap(Arrays::stream)
                        .filter(cell -> cell != null && cell.colSpan == nSpan && cell.colAddr <= nCol
                                && cell.colAddr + nSpan >= nCol)
                        .collect(Collectors.toList());
                colWidth[col] = calculateEquasion(colWidthList, colWidth, col, span, true);
            }
        }

        // span loop
        for (int span = 2; span < nWidth; span++) {
            if (Arrays.stream(colWidth).filter(w -> w == 0).count() == 0) {
                log.finest("Span Loop:"
                        + Arrays.stream(colWidth).mapToObj(w -> String.valueOf(w)).collect(Collectors.joining(",")));
                break;
            }
            // column loop
            for (int col = 0; col < nWidth; col++) {
                if (colWidth[col] != 0)
                    continue;
                int nSpan = span;
                int nCol = col;
                List<TblCell> colWidthList = Arrays.stream(cellArray).flatMap(Arrays::stream)
                        .filter(cell -> cell != null && cell.colSpan == nSpan && cell.colAddr <= nCol
                                && cell.colAddr + nSpan >= nCol)
                        .collect(Collectors.toList());
                colWidth[col] = calculateEquasion(colWidthList, colWidth, col, span, true);
            }
        }

        log.finest("caculated:"
                + Arrays.stream(colWidth).mapToObj(w -> String.valueOf(w)).collect(Collectors.joining(",")));
        long emptyCols = Arrays.stream(colWidth).filter(w -> w <= 0).count();
        long widthSum = Arrays.stream(colWidth).filter(w -> w > 0).sum();
        if (emptyCols > 0) {
            int delta = (int) ((totalWidth - widthSum) / emptyCols);
            for (int col = 0; col < nWidth; col++) {
                if (colWidth[col] <= 0) {
                    colWidth[col] = delta;
                }
            }
        }

        int[] reducedColWidth = IntStream.range(0, nWidth - 1).map(i -> colWidth[i]).toArray();
        log.finest("reduced:"
                + Arrays.stream(reducedColWidth).mapToObj(w -> String.valueOf(w)).collect(Collectors.joining(",")));

        return reducedColWidth;
    }

    private static int[] calculateRowHeight(TblCell[][] cellArray, int totalHeight) {
        int nRow = cellArray.length;
        int[] rowHeight = new int[nRow];

        if (nRow == 1) {
        	rowHeight[0] = Arrays.stream(cellArray[0]).filter(c -> c!=null)
        			             .mapToInt(c -> c.height).min().orElse(0); 
        }
        // print
        for (int row = 0; row < cellArray.length; row++) {
            log.finest("CELLS [" + row + "]=" + Arrays.stream(cellArray[row])
                    .map(cell -> cell == null ? "" : String.valueOf(cell.height)).collect(Collectors.joining(",")));
        }

        // span loop
        for (int span = 1; span < nRow; span++) {
            if (Arrays.stream(rowHeight).filter(w -> w == 0).count() == 0) {
                log.finest("Span Loop:"
                        + Arrays.stream(rowHeight).mapToObj(w -> String.valueOf(w)).collect(Collectors.joining(",")));
                break;
            }
            // row loop
            for (int row = 0; row < nRow; row++) {
                if (rowHeight[row] != 0)
                    continue;
                int nSpan = span;
                int nR = row;
                List<TblCell> rowHeightList = Arrays.stream(cellArray).flatMap(Arrays::stream)
                        .filter(cell -> cell != null && cell.rowSpan == nSpan && cell.rowAddr <= nR
                                && cell.rowAddr + nSpan >= nR)
                        .collect(Collectors.toList());
                rowHeight[row] = calculateEquasion(rowHeightList, rowHeight, row, span, false);
            }
        }

        // span loop
        for (int span = 2; span < nRow; span++) {
            if (Arrays.stream(rowHeight).filter(w -> w == 0).count() == 0) {
                log.finest("Span Loop:"
                        + Arrays.stream(rowHeight).mapToObj(w -> String.valueOf(w)).collect(Collectors.joining(",")));
                break;
            }
            // row loop
            for (int row = 0; row < nRow; row++) {
                if (rowHeight[row] != 0)
                    continue;
                int nSpan = span;
                int nR = row;
                List<TblCell> rowHeightList = Arrays.stream(cellArray).flatMap(Arrays::stream)
                        .filter(cell -> cell != null && cell.rowSpan == nSpan && cell.rowAddr <= nR
                                && cell.rowAddr + nSpan >= nR)
                        .collect(Collectors.toList());
                rowHeight[row] = calculateEquasion(rowHeightList, rowHeight, row, span, false);
            }
        }

        log.finest("caculated:"
                + Arrays.stream(rowHeight).mapToObj(w -> String.valueOf(w)).collect(Collectors.joining(",")));
        return rowHeight;
    }

    private static boolean hasNullRow(int[] rowHeight, int totalHeight) {
        long sum = Arrays.stream(rowHeight).sum();
        if (sum != totalHeight)
            return true;

        long cnt = Arrays.stream(rowHeight).filter(w -> w == 0).count();
        if (cnt > 0)
            return true;
        else
            return false;
    }
}
