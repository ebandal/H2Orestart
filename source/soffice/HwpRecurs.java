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

import java.util.ListIterator;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.sun.star.beans.XPropertySet;
import com.sun.star.text.ControlCharacter;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextRange;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.HwpElement.HwpRecord_Style;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_AutoNumber;
import HwpDoc.paragraph.Ctrl_Character;
import HwpDoc.paragraph.Ctrl_ColumnDef;
import HwpDoc.paragraph.Ctrl_Common;
import HwpDoc.paragraph.Ctrl_EqEdit;
import HwpDoc.paragraph.Ctrl_GeneralShape;
import HwpDoc.paragraph.Ctrl_HeadFoot;
import HwpDoc.paragraph.Ctrl_NewNumber;
import HwpDoc.paragraph.Ctrl_Note;
import HwpDoc.paragraph.Ctrl_PageNumPos;
import HwpDoc.paragraph.Ctrl_SectionDef;
import HwpDoc.paragraph.Ctrl_Table;
import HwpDoc.paragraph.ParaText;
import HwpDoc.section.Page;
import soffice.HwpCallback.TableFrame;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.LineSeg;

public class HwpRecurs {
    private static final Logger log = Logger.getLogger(HwpRecurs.class.getName());
    private static short oldParaShapeID;
    private static short oldCharShapeID;
    private static final String PATTERN_STRING = "[\\u0000\\u000a\\u000d\\u0018-\\u001f]|[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017].{6}[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017]";

    // 컨트롤에 쓰기는 wContext로, 페이지에 쓰기는 parentWriterContext로 (각주,미주)
    public static void printParaRecurs(WriterContext wContext, WriterContext parentWriterContext, HwpParagraph para, HwpCallback callback, int step) {

        // PARA_BREAK 후 return 되기 전에  default로 만들 필요 있음. 그래서 가장 먼저 한다.
        if (step<=1 && oldParaShapeID!=para.paraShapeID) {
            ConvPara.setDefaultParaStyle(wContext);
        }

        if (para.breakType > 0) {
            if ((para.breakType&0x01)==0x01) { // 구역나누기
                ConvPage.makeNextPage(wContext);
            } else if ((para.breakType&0x02)==0x02) { // 다단나누기

            } else if ((para.breakType&0x04)==0x04) { // 쪽 나누기
                ConvPage.makeNextPage(wContext);
                // 쪽 변경시 (SECD없이도) 페이지 가로,세로가 바뀌는 경우 있음. 국방CBD방법론v2(2권).hwp
                // 쪽만 바뀌면서 HEADER가 중복으로 추가되어 표현됨. 디버깅 할 것. 머리글서식을 하나씩 추가해야 할것 같음. 
            } else if ((para.breakType&0x08)==0x08) { // 단 나누기
                ConvPage.makeNextColumn(wContext);
            }
        }

        if (para.p==null) {
            if (callback==null || callback.onParaBreak()==false) {
                beforeParaBreak(wContext, para.paraShapeID, oldCharShapeID, false, step);
                wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.PARAGRAPH_BREAK, false);
            }
            return;
        }

        // Start of [Overcome Table discrepancy]
        // HWP table과  LibreOffice table 표현 방법이 다름을 극복하기 위한 방법
        // 다른 공통개체(그림으로 표시하는 개체)가 현재 문단내에 존재하는지 갯수를 가져온다.  단, 문자로 취급하지 않는 개체만 카운트한다.
        long objCount = para.p.stream().filter(c -> ((c instanceof Ctrl_Common) && ((Ctrl_Common)c).treatAsChar==false)
        										 || ((c instanceof Ctrl_Table) && ((Ctrl_Common)c).treatAsChar==true)
                                               )
                                       .collect(Collectors.counting());
        // 글자가 포함되어 있는지 가져온다.
        String remainChars = para.p.stream().filter(c -> (c instanceof ParaText))
                                            .map(c -> (ParaText)c)
                                            .map(t -> t.text.replaceAll(PATTERN_STRING, "")).collect(Collectors.joining());
        boolean oweParaBreak = false;
        // End of [Overcome Table discrepancy]
        
        // Start of [Overcome COLD > SECD order]
        boolean secdDone = false;
        // End of [Overcome COLD > SECD order]
        
        boolean append = false;

        for (int ctrlIndex=0; ctrlIndex<para.p.size(); ctrlIndex++) {
            Ctrl ctrl = para.p.get(ctrlIndex);
            
            if (ctrl==null) {
                continue;
            }

            switch(ctrl.ctrlId) {
            case "____":
                {
                    int startIndex = ((ParaText)ctrl).startIdx;
                    // List<CharShape> charShapeList = para.charShapes.stream().filter(s -> s.start>=startIndex).collect(Collectors.toList());
                    // CharShape[] charShapes = charShapeList.toArray(new CharShape[charShapeList.size()]);
                    int charShapeId = ((ParaText)ctrl).charShapeId;
                    if (callback==null || callback.onText(((ParaText)ctrl).text, charShapeId, startIndex, append)==false) {
                        insertParaString(wContext, 
                                         ((ParaText)ctrl).text, 
                                         para.lineSegs, 
                                         para.paraStyleID, 
                                         para.paraShapeID, 
                                         (short)charShapeId, 
                                         append, 
                                         callback==null?false:callback.firstParaAfterTable,
                                         step);
                        oldParaShapeID = para.paraShapeID;
                        oldCharShapeID = (short) charShapeId;
                    }
                    append = true;
                }
                break;
            case "   _":
                {
                    switch(((Ctrl_Character)ctrl).ctrlChar) {
                    case LINE_BREAK:
                        wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.LINE_BREAK, false);
                        break;
                    case PARAGRAPH_BREAK:
                         if (callback==null || oweParaBreak==false) {
                            if (callback==null || callback.onParaBreak()==false) {
                                beforeParaBreak(wContext, para.paraShapeID, (short)((Ctrl_Character)ctrl).charShapeId, false, step);
                                wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.PARAGRAPH_BREAK, false);
                            }
                            if (callback!=null) {
                                callback.onFirstAfterTable(false);
                            }
                        } else {
                            oweParaBreak = false;
                            if (callback!=null) {
                                callback.onFirstAfterTable(true);
                            }
                        }
                        break;
                    case HARD_HYPHEN:
                        wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.HARD_HYPHEN, false);
                        break;
                    case HARD_SPACE:
                        wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.HARD_SPACE, false);
                        break;
                    }
                }
                break;
            case "dces":
                if (step==1) { // 1depth에서만 처리
                    if (secdDone==false) {
                        ConvPage.setupPage(wContext, ((Ctrl_SectionDef)ctrl).page);
                        secdDone = true;
                    }
                }
                break;
            case "dloc":
                if (step==1) { // 1depth에서만 처리
                    if (secdDone == false) {
                        Ctrl_SectionDef ctrlSecd = para.p.stream().filter(c -> (c instanceof Ctrl_SectionDef))
                                                        .map(c -> (Ctrl_SectionDef)c).findAny().orElse(null);
                        if (ctrlSecd!=null) {
                            ConvPage.setupPage(wContext, ctrlSecd.page);
                            secdDone = true;
                        }
                    }
                    ConvPage.setColumn(wContext, (Ctrl_ColumnDef)ctrl);
                }
                break;
            case "daeh":    // 머리말
            case "toof":    // 꼬리말
                ConvPage.setHeaderFooter(wContext, (Ctrl_HeadFoot)ctrl);
                break;
            case "  nf":    // 각주
            case "  ne":    // 미주
                // 미주,각주는 상위 WriterContext로 출력
                ConvFootnote.insertFootnote(parentWriterContext, (Ctrl_Note) ctrl, step+1);
                break;
            case " lbt":    // table
                {
                    if (callback!=null && callback.firstParaAfterTable==true && ((Ctrl_Table)ctrl).treatAsChar==true) {
                        beforeParaBreak(wContext, null, null, false, true, step);
                        wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.PARAGRAPH_BREAK, false);
                    }
                    // 이전에 table 이고, 이번에도 table이면 공백을 추가한다.
                    else if (ctrlIndex > 0) {
                        Ctrl previous = para.p.get(ctrlIndex-1);
                        if (previous instanceof Ctrl_Table && ((Ctrl_Table)previous).treatAsChar==true) {
                            wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.HARD_SPACE, false);
                            
                        }
                    } 
                    // 테이블을 그릴때는 문단에 한개의 테이블만 있는지(table + split속성), 다른 개체나 문장과 같이 있는지(table in textframe)에 따라 다르게 그린다.
                    boolean hasSibling = objCount>1?true:remainChars.length()>1?true:false; // 문단내에 1개 테이블외 다른것이 포함되어 있는지 나타냄
                    TableFrame oldFrame = callback==null?TableFrame.NONE:callback.tableFrame;
                    
                    if (hasSibling==true) {
                        if (callback==null) {
                            callback = new HwpCallback();
                        }
                        if (callback!=null && callback.onTableWithFrame()!=TableFrame.MADE) {
                            // (table이 여러 페이지에 걸쳐서 있는지 체크하기 위해) row 높이를 모두 더해서 페이지보다 큰지 체크한다.
                            Ctrl_Table table = (Ctrl_Table)ctrl;
                            int rowHeightSum = 0;
                            int pageHeight = 0;
                            if (table.rowSize != null) {
                                for (int row=0, cellIndex=0; row<table.rowSize.length; row++) {
                                    cellIndex += table.rowSize[row];
                                    rowHeightSum += Transform.translateHwp2Office(table.cells.get(cellIndex-1).height);
                                }
                                Page currPage = ConvPage.getCurrentPage().page;
                                pageHeight = Transform.translateHwp2Office(currPage.height)
                                            - Transform.translateHwp2Office(currPage.marginTop)
                                            - Transform.translateHwp2Office(currPage.marginBottom)
                                            - Transform.translateHwp2Office(currPage.marginHeader)
                                            - Transform.translateHwp2Office(currPage.marginFooter);
                                log.finest("curr Page=[top:" + currPage.marginTop + ",height:"+ currPage.height + ",bottom:" + currPage.marginBottom + "]");
                                log.finest("Table acutual height=" + rowHeightSum + ", Page height=" + pageHeight);
                            }
                            if (rowHeightSum==0 || rowHeightSum<pageHeight) {
                                log.finest("make OuterFrame to show table");
                                callback.changeTableFrame(TableFrame.MAKE);
                            } else {
                                oweParaBreak = true;
                                if (table.treatAsChar==false) {
                                    // Frame이 없이 table을 하나 그릴것이기에, 나머지 같이 그려야할 obj카운트에서 제외한다.
                                    objCount -= 1;
                                }
                            }
                        }
                    } else {
                    	// table 다음 PARA_BREAK 1개를 생략한다.
                        oweParaBreak = true;
                    }
                    ConvTable.insertTable(wContext, (Ctrl_Table) ctrl, para.paraShapeID, callback, step+1);
                    if (callback!=null) {
                        callback.tableFrame = oldFrame;	// 원래 TableFrame 속성으로 복원
                    }
                }
                break;
            case "onta":    // 자동 번호
                Ctrl_AutoNumber autoNumber = (Ctrl_AutoNumber) ctrl;
                if (callback!=null) {
                    callback.onAutoNumber(autoNumber, para.paraStyleID, para.paraShapeID);
                }
                break;
            case "onwn":    // 새 번호 지정
                Ctrl_NewNumber newNumber = (Ctrl_NewNumber) ctrl;
                break;
            case "dhgp":    // 감추기
            	break;
            case "pngp":    // 쪽 번호 위치
            	Ctrl_PageNumPos numPoz = (Ctrl_PageNumPos) ctrl;
            	ConvPage.putPageNum(wContext, numPoz);
            	break;
            case " osg":    // GeneralShapeObject
            case "cip$":    // 그림
            case "cer$":    // 사각형
            case "cra$":    // 호
            case "elo$":    // OLE
            case "nil$":    // 선
            case "noc$":    // 묶음 개체
            case "lle$":    // 타원
            case "lop$":    // 다각형
            case "ruc$":    // 곡선
            case "div$":    // 비디오
            case "tat$":    // 글맵시
                ConvGraphics.insertGraphic(wContext, (Ctrl_GeneralShape)ctrl, para.paraShapeID, step);
                break;
            case "deqe":    // 한글97 수식
                ConvEquation.addFormula(wContext, (Ctrl_EqEdit)ctrl, step);
                break;
            case "cot%":    // FIELD_TABLEOFCONTENT
            case "klc%":    // FIELD_CLICKHERE
            case "knu%":    // FIELD_UNKNOWN
            case "etd%":    // FIELD_DATE
            case "tdd%":    // FIELD_DOCDATE
            case "tap%":    // FIELD_PATH
            case "kmb%":    // FIELD_BOOKMARK
            case "gmm%":    // FIELD_MAILMERGE
            case "frx%":    // FIELD_CROSSREF
            case "rms%":    // FIELD_SUMMARY
            case "rsu%":    // FIELD_USERINFO
            case "klh%":    // FIELD_HYPERLINK
            case "gis%":    // FIELD_REVISION_SIGN
            case "d*%%":    // FIELD_REVISION_DELETE
            case "a*%%":    // FIELD_REVISION_ATTACH
            case "C*%%":    // FIELD_REVISION_CLIPPING
            case "S*%%":    // FIELD_REVISION_SAWTOOTH
            case "T*%%":    // FIELD_REVISION_THINKING
            case "P*%%":    // FIELD_REVISION_PRAISE
            case "L*%%":    // FIELD_REVISION_LINE
            case "c*%%":    // FIELD_REVISION_SIMPLECHANGE
            case "h*%%":    // FIELD_REVISION_HYPERLINK
            case "A*%%":    // FIELD_REVISION_LINEATTACH
            case "i*%%":    // FIELD_REVISION_LINELINK
            case "t*%%":    // FIELD_REVISION_LINETRANSFER
            case "r*%%":    // FIELD_REVISION_RIGHTMOVE
            case "l*%%":    // FIELD_REVISION_LEFTMOVE
            case "n*%%":    // FIELD_REVISION_TRANSFER
            case "e*%%":    // FIELD_REVISION_SIMPLEINSERT
            case "lps%":    // FIELD_REVISION_SPLIT
            case "rm%%":    // FIELD_REVISION_CHANGE
            case "em%%":    // FIELD_MEMO
            case "rpc%":    // FIELD_PRIVATE_INFO_SECURITY
            default:
                break;
            }
        }
    }
    
	static void removeLastParaBreak(XTextCursor textCursor) {
        // LibreOffice에서는 입력전부터 ParaBreak가 생성되어 있다. 따라서 미리 추가된 1개의 ParaBreak를 삭제한다.
        XParagraphCursor xParaCursor = UnoRuntime.queryInterface(XParagraphCursor.class, textCursor);
        if (xParaCursor != null) {
            xParaCursor.gotoEnd(false);
            xParaCursor.goLeft((short)1, true);
            String temp = xParaCursor.getString();
            // table의 경우는  (셀값\r\n)+ 로 반복되며, 개체의 경우는 ""이다. 개행문자로만 된 것만 삭제한다.
            if (temp.equals("\r\n")) {
                xParaCursor.setString("");
            }
            xParaCursor.gotoEnd(false);
        }
    }

    public static void beforeParaBreak(WriterContext wContext,  
                                        short paraShapeID, short charShapeID, 
                                        boolean append, int step) {
        HwpRecord_ParaShape paraShape = wContext.getParaShape(paraShapeID);
        HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID);

        beforeParaBreak(wContext, paraShape, charShape, append, false, step);
    }

    public static void beforeParaBreak(WriterContext wContext,  
                                        HwpRecord_ParaShape paraShape, HwpRecord_CharShape charShape, 
                                        boolean append, boolean ignoreNumbering, int step) {

        XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
        if (paraCursor!=null) {
            paraCursor.gotoEnd(false);
            XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
            try {
                if (append==false) {    // Paragraph의 첫content이면, Property 설정한다. 
                    if (ignoreNumbering==false) {
                        if (paraShape!=null) {
                            ConvPara.setNumberingProperties(paraProps, paraShape);
                        }
                    }
                }

                if (paraShape!=null) {
                    ConvPara.setParagraphProperties(paraProps, paraShape, wContext.getDocInfo().compatibleDoc, charShape.lineSpaceAlpha);
                } else {
                    ConvPara.setMinimumParagraphProperties(paraProps);
                }

                if (charShape!=null) {
                    HwpRecord_BorderFill borderFill = wContext.getBorderFill(charShape.borderFillIDRef);
                    ConvPara.setCharacterProperties(paraProps, charShape, borderFill, step);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void insertParaString(WriterContext wContext, String content, LineSeg lineSeg, 
                                        short styleID, short paraShapeID, short charShapeID, 
                                        boolean append, boolean firstParaAfterTable, int step) {
        HwpRecord_Style paraStyle = wContext.getParaStyle(styleID);
        HwpRecord_ParaShape paraShape = wContext.getParaShape(paraShapeID);
        paraShape.firstAfterTable = firstParaAfterTable;
        HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID);
        String paraStyleName = ConvPara.getStyleName((int)styleID);
        
        insertParaString(wContext, content, lineSeg, paraStyleName, paraStyle, paraShape, charShape, append, false, step);
    }

    public static void insertParaString(WriterContext wContext, String content, LineSeg lineSeg, 
                                        String paraStyleName, HwpRecord_Style paraStyle, 
                                        HwpRecord_ParaShape paraShape, HwpRecord_CharShape charShape, 
                                        boolean append, boolean ignoreNumbering, int step) {

        XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
        if (paraCursor != null) {
            paraCursor.gotoEnd(false);
            XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
            try {
                if (append==false) {	// Paragraph의 첫content이면, Property 설정한다. 
                    if (paraStyleName!=null && !paraStyleName.isEmpty()) {
                        paraProps.setPropertyValue("ParaStyleName", paraStyleName);
                    }

                    if (ignoreNumbering==false) {
                        if (paraShape!=null) {
                            ConvPara.setNumberingProperties(paraProps, paraShape);
                        } else if (paraStyle!=null) {
                            // para.paraStyle.paraShape 와 para.paraShape 일치할때.
                            HwpRecord_ParaShape numberingShape = wContext.getParaShape(paraStyle.paraShape);
                            ConvPara.setNumberingProperties(paraProps, numberingShape);
                        }
                    }
                }

                if (paraShape!=null) {
                    ConvPara.setParagraphProperties(paraProps, paraShape, wContext.getDocInfo().compatibleDoc, charShape.lineSpaceAlpha);
                }

                if (charShape!=null) {
                    HwpRecord_BorderFill borderFill = wContext.getBorderFill(charShape.borderFillIDRef);
                    ConvPara.setCharacterProperties(paraProps, charShape, borderFill, step);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        log.finest("Text=" + content);
        wContext.mText.insertString(wContext.mTextCursor, content, false);
    }

    public static void insertDrawingString(WriterContext wContext, String content,  
                                           short styleID, short paraShapeID, short charShapeID, 
                                           boolean append, int step) {
        HwpRecord_Style paraStyle = wContext.getParaStyle(styleID);
        HwpRecord_ParaShape paraShape = wContext.getParaShape(paraShapeID);
        HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID);
        String paraStyleName = ConvPara.getStyleName((int)styleID);

        insertDrawingString(wContext, content, paraStyleName, paraStyle, paraShape, charShape, append, false, step);
    }

    public static void insertDrawingString(WriterContext wContext, String content,  
                                           String paraStyleName, HwpRecord_Style paraStyle, 
                                           HwpRecord_ParaShape paraShape, HwpRecord_CharShape charShape, 
                                           boolean append, boolean ignoreNumbering, int step) {

        wContext.mTextCursor.gotoEnd(false);
        XTextRange xTextRange = (XTextRange)UnoRuntime.queryInterface(XTextRange.class, wContext.mTextCursor);
        XPropertySet xTextPropSet = (XPropertySet)UnoRuntime.queryInterface(XPropertySet.class, xTextRange);
        
        try {
            if (paraShape!=null) {
                ConvPara.setDrawingParagraphProperties(xTextPropSet, paraShape, wContext.getDocInfo().compatibleDoc, ConvPara.PARA_SPACING);
            }

            if (charShape!=null) {
                ConvPara.setDrawingCharacterProperties(xTextPropSet, charShape, step);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        log.finest("Text=" + content);
        wContext.mText.insertString(wContext.mTextCursor, content, false);
    }
}
