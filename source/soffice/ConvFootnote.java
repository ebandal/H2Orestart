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
            XFootnote xFootnote = UnoRuntime.queryInterface(XFootnote.class, wContext.mMSF.createInstance("com.sun.star.text.Footnote" ));
            // xFootnote.setLabel(label);
            XTextContent xContent = UnoRuntime.queryInterface (XTextContent.class, xFootnote );
            wContext.mText.insertTextContent (wContext.mTextCursor, xContent, false );
            
            XFootnotesSupplier xFootnoteSupplier = UnoRuntime.queryInterface(XFootnotesSupplier.class, wContext.mMyDocument);
            XIndexAccess xFootnotes = UnoRuntime.queryInterface (XIndexAccess.class, xFootnoteSupplier.getFootnotes() );
            XFootnote xNumbers = UnoRuntime.queryInterface (XFootnote.class, xFootnotes.getByIndex(getFootnoteIndex()) );
            XText xSimple = UnoRuntime.queryInterface (XText.class, xNumbers );
            XTextCursor xRange = UnoRuntime.queryInterface (XTextCursor.class, xSimple.createTextCursor() );
            
			WriterContext context2 = new WriterContext();
			context2.hwp 			= wContext.hwp;
			context2.mContext 		= wContext.mContext;
			context2.mDesktop 		= wContext.mDesktop;
			context2.mMCF			= wContext.mMCF;
			context2.mMSF			= wContext.mMSF;
			context2.mMyDocument 	= wContext.mMyDocument;
			context2.mText			= xSimple;
			context2.mTextCursor	= xRange;
            
			if (note.paras!=null) {
	      		for (int paraIndex=0; paraIndex < note.paras.size(); paraIndex++) {
	      			HwpParagraph para = note.paras.get(paraIndex);
	      			if (para.p==null || para.p.size()==0)          continue;
	
	      			boolean isLastPara = (paraIndex==note.paras.size()-1)?true:false;
	
	      			String styleName = ConvPara.getStyleName((int)para.paraStyleID);
	      			log.finer("StyleID="+para.paraStyleID+ ", StyleName="+styleName);
	      			if (styleName==null || styleName.isEmpty()) {
	      				log.fine("Style Name is empty");
	      			}
	
	      			short[] charShapeID = new short[1];
	      			Optional<Ctrl> ctrlOp = para.p.stream().filter(c -> (c instanceof ParaText)).findFirst();
	      			if (ctrlOp.isPresent()) {
	      			    charShapeID[0] = (short) ((ParaText)ctrlOp.get()).charShapeId;
	      			}
	      			HwpRecord_Style paraStyle = wContext.getParaStyle(para.paraStyleID);
	      			HwpRecord_ParaShape paraShape = wContext.getParaShape(para.paraShapeID);
	    			
	    			HwpCallback callback = new HwpCallback() {
	    				@Override
	    				public void onNewNumber(int paraStyleID, int paraShapeID) {
	    					reset(context2);
	    					String label = Integer.valueOf(getFootnoteIndex()+1).toString()+")";	// index 보다 1많은 값으로 표현.
	    					xFootnote.setLabel(label);
	    				};
	    				@Override
	    				public void onAutoNumber(int paraStyleID, int paraShapeID) {
	    					String label = Integer.valueOf(getFootnoteIndex()+1).toString()+")";	// index 보다 1많은 값으로 표현.
	    					xFootnote.setLabel(label);
	    				};
	    				@Override
	    				public boolean onTab(String info) {
	                        HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID[0]);
	    					HwpRecurs.insertParaString(context2, "\t", para.lineSegs,
	    												styleName, paraStyle, paraShape, charShape,	true, true, step);
	    					return true;
	    				};
	    				@Override
	    				public boolean onText(String content, int charShapeId, int charPos, boolean append) {
	    					charShapeID[0] = (short)charShapeId;
	                        HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID[0]);
	    	   				HwpRecurs.insertParaString(context2, content, para.lineSegs,
	    	   											styleName, paraStyle, paraShape, charShape, append, true, step);
	    	   	            // xSimple.insertString (xRange, content, false );
	    					return true;
	    				}
	    				@Override
	    				public boolean onParaBreak() {
	    					if (isLastPara==false) {
	                            HwpRecord_CharShape charShape = wContext.getCharShape(charShapeID[0]);
	    						HwpRecurs.insertParaString(context2, "\r", para.lineSegs,
	    												styleName, paraStyle, paraShape, charShape, true, true, step);
	    					}
	    					return true;
	    				}
	    			};
	    			HwpRecurs.printParaRecurs(context2, para, callback, 2);
	      		}
			}
        } catch (Exception e) {
            e.printStackTrace();
        }
        setFootnoteIndex(getFootnoteIndex()+1);
    }


}
