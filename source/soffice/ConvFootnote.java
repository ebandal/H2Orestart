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

import java.util.Optional;
import java.util.logging.Logger;

import com.sun.star.container.XIndexAccess;
import com.sun.star.text.XFootnote;
import com.sun.star.text.XFootnotesSupplier;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.HwpElement.HwpRecord_Style;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_AutoNumber;
import HwpDoc.paragraph.Ctrl_Note;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.ParaText;

public class ConvFootnote {
    private static final Logger log = Logger.getLogger(ConvFootnote.class.getName());

    private static int footnoteIndex = 0;

    public static int getFootnoteIndex() {
        return footnoteIndex;
    }

    public static void setFootnoteIndex(int index) {
        footnoteIndex = index;
    }

    public static void reset(WriterContext wContext) {
        footnoteIndex = 0;
    }

    protected static void insertFootnote(WriterContext wContext, Ctrl_Note note, int step) {
        try {
            XFootnote xFootnote = UnoRuntime.queryInterface(XFootnote.class, wContext.mMSF.createInstance("com.sun.star.text.Footnote"));
            XTextContent xContent = UnoRuntime.queryInterface(XTextContent.class, xFootnote);
            wContext.mText.insertTextContent(wContext.mTextCursor, xContent, false);

            XFootnotesSupplier xFootnoteSupplier = UnoRuntime.queryInterface(XFootnotesSupplier.class, wContext.mMyDocument);
            XIndexAccess xFootnotes = UnoRuntime.queryInterface(XIndexAccess.class, xFootnoteSupplier.getFootnotes());
            XFootnote xNumbers = UnoRuntime.queryInterface(XFootnote.class, xFootnotes.getByIndex(getFootnoteIndex()));
            XText xSimple = UnoRuntime.queryInterface(XText.class, xNumbers);
            
            XTextCursor xRange = UnoRuntime.queryInterface(XTextCursor.class, xSimple.createTextCursor());

            WriterContext context2 = new WriterContext();
            context2.mContext = wContext.mContext;
            context2.mDesktop = wContext.mDesktop;
            context2.mMCF = wContext.mMCF;
            context2.mMSF = wContext.mMSF;
            context2.mMyDocument = wContext.mMyDocument;
            context2.userHomeDir = wContext.userHomeDir;
            context2.mText = xSimple;
            context2.mTextCursor = xRange;

            if (note.paras != null) {
                for (int paraIndex = 0; paraIndex < note.paras.size(); paraIndex++) {
                    HwpParagraph para = note.paras.get(paraIndex);
                    if (para.p == null || para.p.size() == 0)
                        continue;

                    boolean isLastPara = (paraIndex == note.paras.size() - 1) ? true : false;

                    String styleName = ConvPara.getStyleName((int) para.paraStyleID);
                    log.finer("StyleID=" + para.paraStyleID + ", StyleName=" + styleName);
                    if (styleName == null || styleName.isEmpty()) {
                        log.fine("Style Name is empty");
                    }

                    short[] charShapeID = new short[1];
                    Optional<Ctrl> ctrlOp = para.p.stream().filter(c -> (c instanceof ParaText)).findFirst();
                    if (ctrlOp.isPresent()) {
                        charShapeID[0] = (short) ((ParaText) ctrlOp.get()).charShapeId;
                    }
                    HwpRecord_Style paraStyle = wContext.getParaStyle(para.paraStyleID);
                    HwpRecord_ParaShape paraShape = wContext.getParaShape(para.paraShapeID);

                    HwpCallback callback = new HwpCallback() {
                        @Override
                        public void onNewNumber(int paraStyleID, int paraShapeID) {
                            reset(context2);
                            String label = Integer.valueOf(getFootnoteIndex() + 1).toString() + ")"; // index 보다 1많은 값으로
                                                                                                     // 표현.
                            xFootnote.setLabel(label);
                        };

                        @Override
                        public void onAutoNumber(Ctrl_AutoNumber autoNumber, int paraStyleID, int paraShapeID) {
                            String label = Integer.valueOf(getFootnoteIndex() + 1).toString() + ")"; // index 보다 1많은 값으로
                                                                                                     // 표현.
                            xFootnote.setLabel(label);
                        };

                        @Override
                        public boolean onTab(String info) {
                            HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID[0]);
                            HwpRecurs.insertParaString(context2, "\t", para.lineSegs, styleName, paraStyle, paraShape,
                                    charShape, true, true, step);
                            return true;
                        };

                        @Override
                        public boolean onText(String content, int charShapeId, int charPos, boolean append) {
                            charShapeID[0] = (short) charShapeId;
                            HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID[0]);
                            HwpRecurs.insertParaString(context2, content, para.lineSegs, styleName, paraStyle,
                                    paraShape, charShape, append, true, step);
                            // xSimple.insertString (xRange, content, false );
                            return true;
                        }

                        @Override
                        public boolean onParaBreak() {
                            if (isLastPara == false) {
                                HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID[0]);
                                HwpRecurs.insertParaString(context2, "\r", para.lineSegs, styleName, paraStyle,
                                        paraShape, charShape, true, true, step);
                            }
                            return true;
                        }
                    };
                    HwpRecurs.printParaRecurs(context2, wContext, para, callback, 2);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        setFootnoteIndex(getFootnoteIndex() + 1);
    }

}
