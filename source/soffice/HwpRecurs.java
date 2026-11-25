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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.sun.star.beans.XPropertySet;
import com.sun.star.text.ControlCharacter;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextRange;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.Exception.HwpParseException;
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
import HwpDoc.paragraph.Ctrl_Common.VRelTo;
import HwpDoc.paragraph.Ctrl_Common.VertAlign;
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
    public static void printParaRecurs(WriterContext wContext, WriterContext parentWriterContext, HwpParagraph para, HwpCallback callback, int step) throws HwpParseException {

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
                // 글자 없는 문단에는 표 다음 간격 보정을 하지 않는다 (v0.8.1 과 같은 간격. 한글과 비교해 확인)
                beforeParaBreak(wContext, para.paraShapeID, oldCharShapeID, false, false, step);
                wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.PARAGRAPH_BREAK, false);
            }
            if (callback!=null) {
                callback.onFirstAfterTable(false);
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
                                if (append == false && isEmptyPara(para)) {
                                    // 내용 없는 문단: 스타일을 지정하는 곳(첫 글자)이 없어 앞 문단의 스타일이 이어졌다.
                                    // 문단 끝 모양(beforeParaBreak)보다 먼저 이 문단의 스타일을 지정한다 (HWPX 저장/다시 읽기)
                                    setParaStyle(wContext, para.paraStyleID);
                                }
                                // 표 다음 간격 보정은 글자가 있는 문단에만 (append: 이 문단에 글자를 넣었는지).
                                // 표 바로 뒤 빈 문단에 더하면 한글보다 간격이 넓어져 쪽이 밀린다 (v0.8.1 과 같게)
                                beforeParaBreak(wContext, para.paraShapeID, (short)((Ctrl_Character)ctrl).charShapeId, false,
                                                callback==null?false:(callback.firstParaAfterTable && append), step);
                                wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.PARAGRAPH_BREAK, false);
                            }
                            if (callback!=null) {
                                callback.onFirstAfterTable(false);
                            }
                        } else {
                            oweParaBreak = false;
                            // 표 문단의 문단 끝은 넣지 않지만, 그 글자 모양을 표 뒤 문단(지금 커서 문단)에 둔다. 문서가 표로
                            // 끝나면 표 뒤 문단은 이대로 남는다 (기존: 앞 내용의 글자 속성이 남아 HWPX 로 저장/다시 읽을 때
                            // 끝 문단의 글자 크기가 바뀌었다). 뒤에 문단이 더 있으면 그 문단 끝에서 다시 설정된다
                            applyCharShapeAtCursor(wContext, ((Ctrl_Character)ctrl).charShapeId, step);
                            if (callback!=null) {
                                callback.onFirstAfterTable(true);
                            }
                        }
                        break;
                    // 묶음 빈칸/하이픈은 글자처럼 넣는다 (LibreOffice 의 HARD_SPACE/HARD_HYPHEN 도 같은 문자 U+00A0/U+2011).
                    // 제어 문자로 넣으면 그 위치의 글자 모양을 받지 못하고, 문단 첫 글자일 때는 뒤 글자에서 문단 스타일을
                    // 설정하면서 글자 모양이 지워진다
                    case HARD_HYPHEN:
                    case HARD_SPACE:
                        {
                            String ch = ((Ctrl_Character)ctrl).ctrlChar==Ctrl_Character.CtrlCharType.HARD_SPACE ? " " : "‑";
                            int charShapeId = ((Ctrl_Character)ctrl).charShapeId;
                            if (callback==null || callback.onText(ch, charShapeId, ((Ctrl_Character)ctrl).startIdx, append)==false) {
                                insertParaString(wContext, ch, para.lineSegs, para.paraStyleID, para.paraShapeID,
                                                 (short)charShapeId, append,
                                                 callback==null?false:callback.firstParaAfterTable, step);
                                oldParaShapeID = para.paraShapeID;
                                oldCharShapeID = (short) charShapeId;
                            }
                            append = true;
                        }
                        break;
                    default:
                        break;
                    }
                }
                break;
            case "dces":
                if (step==1) { // 1-depth에서만 처리
                    if (secdDone==false) {
                        ConvPage.setupPage(wContext, ((Ctrl_SectionDef)ctrl).page);
                        secdDone = true;
                    }
                }
                break;
            case "dloc":
                if (step==1) { // 1-depth에서만 처리
                    if (secdDone == false) {
                        // 동일 PARAGRAPH내 COLD가 SECD 보다 먼저 오는 경우에 SECD를 먼저 처리
                        Ctrl_SectionDef ctrlSecd = para.p.stream().filter(c -> (c instanceof Ctrl_SectionDef))
                                                        .map(c -> (Ctrl_SectionDef)c).findAny().orElse(null);
                        if (ctrlSecd!=null) {
                            ConvPage.setupPage(wContext, ctrlSecd.page);
                            secdDone = true;
                        }
                    }
                    if (secdDone) { 
                        // (25.07.20) COLD가 SECD와 PARAGRAPH내 같이 있는 경우에만 처리되도록 함.
                        ConvPage.setColumn(wContext, (Ctrl_ColumnDef)ctrl);
                    }
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
                        markInsertedPara(wContext);
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
                    } else if ((((Ctrl_Table)ctrl).vertRelTo == VRelTo.PAGE || ((Ctrl_Table)ctrl).vertRelTo == VRelTo.PAPER)  && 
                               (((Ctrl_Table)ctrl).vertAlign == VertAlign.BOTTOM )) { 
                        // issue #58. 테이블이 PAGE의 BOTTOM에 위치하는 경우, LibreOffice에서 처리할 수 없어 frame으로 만든다.
                        callback.changeTableFrame(TableFrame.MAKE);
                    } else {
                    	// table 다음 PARA_BREAK 1개를 생략한다.
                        oweParaBreak = true;
                    }
                    if (oweParaBreak && step == 1 && para == wContext.lastBodyPara) {
                        // 문서가 표로 끝난다: 표 뒤 문단(문서 끝 문단)이 그대로 남으므로 표 문단의 스타일을 둔다. 표가 문단
                        // 모양을 적용하기 전에 지정해야 그 모양이 지워지지 않는다 (기존: 문단 시작에서 기본 스타일로
                        // 돌린 채 남아 HWPX 로 저장/다시 읽을 때 끝 문단 스타일이 바뀌었다)
                        setParaStyle(wContext, para.paraStyleID);
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
            case "loc$":    // 개체연결선 (기존: 빠져 있어 묶음 밖의 연결선이 그려지지 않았음)
            case "noc$":    // 묶음 개체
            case "lle$":    // 타원
            case "lop$":    // 다각형
            case "ruc$":    // 곡선
            case "div$":    // 비디오
            case "tat$":    // 글맵시
                if (append == false) {
                    if (ctrl.runCharShapeId >= 0 && wContext.styleSetBeforeShape == null) {
                        // HWPX: 문단 스타일을 개체보다 먼저 지정한다. 첫 글자를 넣을 때 스타일을 지정하면 LibreOffice 가
                        // 문단 안의 글자 속성을 지워(개체 뒤 빈칸의 글자 모양 등) HWPX 로 저장/다시 읽을 때 바뀌었다
                        String styleName = ConvPara.getStyleName(para.paraStyleID);
                        if (styleName != null && !styleName.isEmpty()) {
                            try {
                                XPropertySet pp = UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);
                                pp.setPropertyValue("ParaStyleName", styleName);
                                wContext.styleSetBeforeShape = styleName;
                            } catch (Exception e) {
                                log.log(Level.SEVERE, e.toString(), e);
                            }
                        }
                    }
                    // 문단의 첫 내용이 개체이면 문단 끝 글자 모양을 먼저 적용한다. 개체를 넣은 뒤에는 문단이 비어 있지 않아
                    // 문단 끝(beforeParaBreak)에서 설정한 글자 속성이 남지 않고 앞 문단의 글자 속성이 이어진다
                    setEmptyParaCharShape(wContext, para, step);
                }
                {
                    int before = textBeforeCursor(wContext).length();
                    ConvGraphics.insertGraphic(wContext, (Ctrl_GeneralShape)ctrl, para.paraShapeID, step);
                    // 개체 뒤에 넣은 빈칸(ConvGraphics, LibreOffice 7.2 이상)에 개체가 든 run 의 글자 모양을 준다 (HWPX).
                    // 기존: 그때 커서의 글자 속성(스타일 값 등)을 받아, HWPX 로 저장/다시 읽을 때 개체 run 의 글자 모양이 바뀌었다
                    if (ctrl.runCharShapeId >= 0) {
                        String after = textBeforeCursor(wContext);
                        if (after.length() == before + 1 && after.endsWith(" ")) {
                            styleLastChar(wContext, ctrl.runCharShapeId, step);
                        } else if (after.length() == before) {
                            // 글자처럼 넣은 개체: 개체 자리 글자(문자열에는 안 나옴)에 개체 run 의 글자 모양을 준다.
                            // 기존: 문단 끝 글자 모양을 받아 HWPX 로 저장/다시 읽을 때 개체 run 의 글자 모양이 바뀌었다
                            styleAnchorChar(wContext, ctrl.runCharShapeId, step);
                        }
                    }
                }
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

    /**
     * firstParaAfterTable: 표 바로 다음 문단인지. 문단 모양 레코드는 문단들이 함께 쓰므로 매번 설정한다
     * (안 하면 앞 문단에서 남은 값으로 같은 모양의 빈 문단에도 표 다음 간격이 더해진다)
     */
    /** 글자도 개체도 없는 문단인가 (문단 끝/구역/단/머리말 등 정의만 있다) */
    private static boolean isEmptyPara(HwpParagraph para) {
        if (para.p == null) return true;
        for (Ctrl c : para.p) {
            if (c instanceof Ctrl_Character || c instanceof Ctrl_SectionDef || c instanceof Ctrl_ColumnDef
                    || c instanceof Ctrl_HeadFoot || c instanceof Ctrl_PageNumPos || c instanceof Ctrl_NewNumber) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** 커서 문단에 HWP 스타일을 지정한다 (LibreOffice 는 문단 안의 글자 속성을 지운다) */
    private static void setParaStyle(WriterContext wContext, int styleID) {
        String styleName = ConvPara.getStyleName(styleID);
        if (styleName == null || styleName.isEmpty()) {
            return;
        }
        try {
            XPropertySet pp = UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);
            pp.setPropertyValue("ParaStyleName", styleName);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    /** 커서가 있는 문단의 처음부터 커서까지의 글 */
    private static String textBeforeCursor(WriterContext wContext) {
        try {
            XParagraphCursor pc = UnoRuntime.queryInterface(XParagraphCursor.class,
                    wContext.mText.createTextCursorByRange(wContext.mTextCursor.getEnd()));
            if (pc == null) return "";
            pc.gotoStartOfParagraph(true);
            return pc.getString();
        } catch (com.sun.star.uno.RuntimeException e) {
            return "";
        }
    }

    /** 커서 바로 앞 글자에 글자 모양을 적용한다 */
    private static void styleLastChar(WriterContext wContext, int charShapeId, int step) {
        HwpRecord_CharShape charShape = wContext.getCharShape((short) charShapeId);
        if (charShape == null) {
            return;
        }
        try {
            XTextCursor c = wContext.mText.createTextCursorByRange(wContext.mTextCursor.getEnd());
            c.goLeft((short) 1, true);
            XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, c);
            HwpRecord_BorderFill borderFill = wContext.getBorderFill(charShape.borderFillIDRef);
            ConvPara.setCharacterProperties(props, charShape, borderFill, step);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    /** 표 사이에 넣은 빈 문단에 붙이는 표시 (문단 사용자 정의 속성). HWPX 저장(LoToHwpx)은 이 문단을 쓰지 않는다 */
    public static final String INSERTED_PARA_ATTR = "h2o-inserted";

    /**
     * 방금 끝낸 문단(커서 앞 문단)이 가져오기가 넣은 문단임을 표시한다. 원본에 없는 문단이라 HWPX 로 저장할 때 빼고,
     * 다시 읽으면 가져오기가 다시 넣는다 (기존: 저장할 때마다 표 사이에 빈 줄이 남아 한글에서 큰 표가 다음 쪽으로 밀렸다)
     */
    private static void markInsertedPara(WriterContext wContext) {
        try {
            XParagraphCursor c = UnoRuntime.queryInterface(XParagraphCursor.class,
                    wContext.mText.createTextCursorByRange(wContext.mTextCursor.getStart()));
            if (c == null || !c.gotoPreviousParagraph(false)) return;
            XPropertySet ps = UnoRuntime.queryInterface(XPropertySet.class, c);
            com.sun.star.container.XNameContainer attrs = UnoRuntime.queryInterface(com.sun.star.container.XNameContainer.class,
                    ps.getPropertyValue("ParaUserDefinedAttributes"));
            if (attrs == null) return;
            com.sun.star.xml.AttributeData d = new com.sun.star.xml.AttributeData();
            d.Type = "CDATA";
            d.Value = "1";
            if (attrs.hasByName(INSERTED_PARA_ATTR)) attrs.replaceByName(INSERTED_PARA_ATTR, d);
            else attrs.insertByName(INSERTED_PARA_ATTR, d);
            ps.setPropertyValue("ParaUserDefinedAttributes", attrs);
        } catch (Exception e) {
            log.log(Level.FINE, e.toString(), e);
        }
    }

    /**
     * 문단이 비어 있으면 원래 글꼴 정보를 문단 속성으로 남긴다. LibreOffice 는 빈 문단에 지정한 글자 속성만 문단의 글자 속성으로
     * 남기므로 기록도 같은 때에만 바꾼다 (기존: 마지막에 지정한 글자 모양으로 바뀌어 실제 문단 글자 속성과 맞지 않았다)
     */
    private static void setParaOrigFontIfEmpty(WriterContext wContext, XPropertySet props, HwpRecord_CharShape charShape) {
        if (textBeforeCursor(wContext).isEmpty()) {
            ConvPara.setParaOrigFontAttr(props, charShape);
        }
    }

    /** 커서 바로 앞이 글자처럼 놓인 개체의 자리 글자이면 그 글자에 글자 모양을 적용한다 */
    private static void styleAnchorChar(WriterContext wContext, int charShapeId, int step) {
        HwpRecord_CharShape charShape = wContext.getCharShape((short) charShapeId);
        if (charShape == null) {
            return;
        }
        try {
            XParagraphCursor c = UnoRuntime.queryInterface(XParagraphCursor.class,
                    wContext.mText.createTextCursorByRange(wContext.mTextCursor.getEnd()));
            if (c == null || c.isStartOfParagraph()) return;
            c.goLeft((short) 1, true);
            if (!c.getString().isEmpty()) return;      // 보통 글자: 개체 자리가 아니다
            XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, c);
            HwpRecord_BorderFill borderFill = wContext.getBorderFill(charShape.borderFillIDRef);
            ConvPara.setCharacterProperties(props, charShape, borderFill, step);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    /** 커서 문단 끝에 글자 모양을 적용한다 (빈 문단이면 문단의 글자 속성이 된다) */
    private static void applyCharShapeAtCursor(WriterContext wContext, int charShapeId, int step) {
        HwpRecord_CharShape charShape = wContext.getCharShape((short) charShapeId);
        XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
        if (charShape == null || paraCursor == null) {
            return;
        }
        paraCursor.gotoEnd(false);
        XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
        try {
            HwpRecord_BorderFill borderFill = wContext.getBorderFill(charShape.borderFillIDRef);
            ConvPara.setCharacterProperties(props, charShape, borderFill, step);
            setParaOrigFontIfEmpty(wContext, props, charShape);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    /** 빈 문단(커서 위치)에 문단 끝(PARAGRAPH_BREAK) 의 글자 모양을 적용한다 */
    private static void setEmptyParaCharShape(WriterContext wContext, HwpParagraph para, int step) {
        Ctrl_Character brk = null;
        for (Ctrl c : para.p) {
            if (c instanceof Ctrl_Character && ((Ctrl_Character)c).ctrlChar == Ctrl_Character.CtrlCharType.PARAGRAPH_BREAK) {
                brk = (Ctrl_Character) c;
            }
        }
        if (brk == null) {
            return;
        }
        HwpRecord_CharShape charShape = wContext.getCharShape((short) brk.charShapeId);
        if (charShape == null) {
            return;
        }
        XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
        if (paraCursor == null) {
            return;
        }
        paraCursor.gotoEnd(false);
        XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
        try {
            HwpRecord_BorderFill borderFill = wContext.getBorderFill(charShape.borderFillIDRef);
            ConvPara.setCharacterProperties(props, charShape, borderFill, step);
            setParaOrigFontIfEmpty(wContext, props, charShape);
        } catch (Exception e) {
            log.log(Level.SEVERE, e.toString(), e);
        }
    }

    public static void beforeParaBreak(WriterContext wContext,
                                        short paraShapeID, short charShapeID,
                                        boolean append, boolean firstParaAfterTable, int step) {
        HwpRecord_ParaShape paraShape = wContext.getParaShape(paraShapeID);
        if (paraShape != null) {
            paraShape.firstAfterTable = firstParaAfterTable;
        }
        HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID);

        beforeParaBreak(wContext, paraShape, charShape, append, false, step);
    }

    public static void beforeParaBreak(WriterContext wContext,  
                                        HwpRecord_ParaShape paraShape, HwpRecord_CharShape charShape, 
                                        boolean append, boolean ignoreNumbering, int step) {
        wContext.styleSetBeforeShape = null;    // 문단 끝: 다음 문단에 남기지 않는다

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
                    setParaOrigFontIfEmpty(wContext, paraProps, charShape);    // 문단 끝 글자 모양의 원래 글꼴 (HWPX 저장)
                }

            } catch (Exception e) {
                log.log(Level.SEVERE, e.toString(), e);
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

        if (ConvHypua2Unicode.hasHypua(content)) {
            // 옛한글 (Hanyang private use area code)코드를 첫가끝(Syllable-Initial-Peak-Final Encoding Approach)코드로 치환
        	content = ConvHypua2Unicode.convertText(content);
        }
        
        XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
        if (paraCursor != null) {
            paraCursor.gotoEnd(false);
            XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
            try {
                if (append==false) {	// Paragraph의 첫content이면, Property 설정한다. 
                    String already = wContext.styleSetBeforeShape;
                    wContext.styleSetBeforeShape = null;
                    if (paraStyleName!=null && !paraStyleName.isEmpty() && !paraStyleName.equals(already)) {
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
                    // 문단의 글자 속성(문단 끝 글자 모양)은 마지막에 넣은 글자 모양을 따른다 (셀의 마지막 문단 등 문단 끝이
                    // 없는 문단). 원래 글꼴 정보도 같이 둔다 (HWPX 저장)
                    setParaOrigFontIfEmpty(wContext, paraProps, charShape);
                }
                
            } catch (Exception e) {
                log.log(Level.SEVERE, e.toString(), e);
            }
        }

        log.finest("Text=" + content);
        if (charShape!=null && charShape.origFontName!=null && paraCursor!=null) {
            // 대체폰트(나눔고딕)는 글자 종류별 자간으로 한컴 글자 폭에 맞춘다.
            XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
            FontWidthCorrection.insertString(wContext.mText, wContext.mTextCursor, paraProps, content, charShape, paraShape);
        } else {
            wContext.mText.insertString(wContext.mTextCursor, content, false);
        }
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

        if (ConvHypua2Unicode.hasHypua(content)) {
            content = ConvHypua2Unicode.convertText(content);
        }

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
            log.log(Level.SEVERE, e.toString(), e);
        }

        log.finest("Text=" + content);
        if (charShape!=null && charShape.origFontName!=null) {
            FontWidthCorrection.insertString(wContext.mText, wContext.mTextCursor, xTextPropSet, content, charShape, paraShape);
        } else {
            wContext.mText.insertString(wContext.mTextCursor, content, false);
        }
    }
}
