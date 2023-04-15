/* MIT License
 *  
 * Copyright (c) 2022 ebandal
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * 본 제품은 한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다.
 * 개방형 워드프로세서 마크업 언어(OWPML) 문서 구조 KS X 6101:2018 문서를 참고하였습니다.
 * 작성자 : 반희수 ebandal@gmail.com  
 * 작성일 : 2022.10
 */
package soffice;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.DataFormatException;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.sun.star.awt.Point;
import com.sun.star.awt.Size;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.drawing.CircleKind;
import com.sun.star.drawing.FillStyle;
import com.sun.star.drawing.HomogenMatrix3;
import com.sun.star.drawing.LineEndType;
import com.sun.star.drawing.PolyPolygonBezierCoords;
import com.sun.star.drawing.PolygonFlags;
import com.sun.star.drawing.TextHorizontalAdjust;
import com.sun.star.drawing.TextVerticalAdjust;
import com.sun.star.drawing.XShape;
import com.sun.star.graphic.XGraphic;
import com.sun.star.graphic.XGraphicProvider;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lib.uno.adapter.ByteArrayToXInputStreamAdapter;
import com.sun.star.style.ParagraphAdjust;
import com.sun.star.table.BorderLine2;
import com.sun.star.table.BorderLineStyle;
import com.sun.star.text.ControlCharacter;
import com.sun.star.text.HoriOrientation;
import com.sun.star.text.RelOrientation;
import com.sun.star.text.SizeType;
import com.sun.star.text.TextContentAnchorType;
import com.sun.star.text.VertOrientation;
import com.sun.star.text.WrapTextMode;
import com.sun.star.text.WritingMode2;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextFrame;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.HwpElement.HwpRecord_BorderFill.Fill;
import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.HwpElement.HwpRecord_BinData.Type;
import HwpDoc.paragraph.CapParagraph;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_Common;
import HwpDoc.paragraph.Ctrl_Container;
import HwpDoc.paragraph.Ctrl_GeneralShape;
import HwpDoc.paragraph.Ctrl_ShapeArc;
import HwpDoc.paragraph.Ctrl_ShapeCurve;
import HwpDoc.paragraph.Ctrl_ShapeEllipse;
import HwpDoc.paragraph.Ctrl_ShapeLine;
import HwpDoc.paragraph.Ctrl_ShapePic;
import HwpDoc.paragraph.Ctrl_ShapePolygon;
import HwpDoc.paragraph.Ctrl_ShapeRect;
import HwpDoc.paragraph.Ctrl_ShapeVideo;
import HwpDoc.paragraph.Ctrl_Table;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.ParaText;
import HwpDoc.section.Page;
import soffice.HwpCallback.TableFrame;

public class ConvGraphics {
	private static final Logger log = Logger.getLogger(ConvGraphics.class.getName());
	private static int autoNum = 0;
	
	public static void reset(WriterContext wContext) {
		autoNum = 0;
	}
	
    public static void insertGraphic(WriterContext wContext, Ctrl_GeneralShape obj, short paraShapeID, int step) {
		HwpRecord_ParaShape paraShape = wContext.getParaShape((short)paraShapeID);
  		XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
  		XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
  		ConvPara.setParagraphProperties(paraProps, paraShape, wContext.getDocInfo().compatibleDoc, ConvPara.PARA_SPACING);

    	switch(obj.ctrlId) {
	    case "cip$":
	    	insertPICTURE(wContext, (Ctrl_ShapePic)obj, step, -1, -1);
	    	break;
		case "div$":
	    	insertVIDEO(wContext, (Ctrl_ShapeVideo)obj, step, -1, -1);
			break;
	    case "cer$":
	        if (obj.paras==null || obj.paras.size()<1) {
	            insertRECTANGLE(wContext, (Ctrl_ShapeRect)obj, step, -1, -1);
	        } else {
	            insertTextFrame(wContext, (Ctrl_ShapeRect)obj, step, -1, -1);
	        }
	    	break;
		case "nil$":	// 선
		case "loc$":
    		insertLINE(wContext, (Ctrl_ShapeLine)obj, step, -1, -1);
	    	break;
		case "lle$":	// 타원
    		insertELLIPSE(wContext, (Ctrl_ShapeEllipse)obj, step, -1, -1);
	    	break;
		case "lop$":	// 다각형
        	insertPOLYGON(wContext, (Ctrl_ShapePolygon)obj, step, -1, -1);
	    	break;
		case "ruc$":	// 곡선
        	insertCURVE(wContext, (Ctrl_ShapeCurve)obj, step);
	    	break;
	    case "cra$":
        	insertARC(wContext, (Ctrl_ShapeArc)obj, step, -1, -1);
	    	break;
		case "noc$":	// 묶음 개체
    		insertMulti(wContext, (Ctrl_Container)obj, step);
			break;
		case "elo$":	// OLE
		case "tat$":	// 글맵시
        	insertDummyTextFrame(wContext, (Ctrl_GeneralShape)obj, step);
	    	break;
    	default:
    		break;
    	}
    }
    
    private static void insertPICTURE(WriterContext wContext, Ctrl_ShapePic pic, int step, int shapeWidth, int shapeHeight) {
    	boolean hasCaption = pic.caption==null?false:pic.caption.size()==0?false:true;
    	
    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;
    	try {
    		if (hasCaption) {
    			xFrame = makeOuterFrame(wContext, pic, false, step);
				// Frame 내부 Cursor 생성
				xFrameText = xFrame.getText();
				xFrameCursor = xFrameText.createTextCursor();
    		}
    		
    		int sizeWidth = shapeWidth<=0?pic.width:shapeWidth;
    		int sizeHeight = shapeHeight<=0?pic.height:shapeHeight;
    		
	  		// 그림그리기
	        Object textGraphicObject = wContext.mMSF.createInstance("com.sun.star.text.TextGraphicObject");
	        XTextContent xTextContent = (XTextContent) UnoRuntime.queryInterface(XTextContent.class, textGraphicObject);
	        XPropertySet xPropSet = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, textGraphicObject);
	        
	        // image ByteArray로 그림 그리기
    		Object graphicProviderObject = wContext.mMCF.createInstanceWithContext("com.sun.star.graphic.GraphicProvider", wContext.mContext);
    		XGraphicProvider xGraphicProvider = UnoRuntime.queryInterface(XGraphicProvider.class, graphicProviderObject);

            byte[] imageAsByteArray = null;
            String imageType = "";

    		switch(pic.imagePath.type) {
    		case LINK:
    		    imageAsByteArray =  Files.readAllBytes(new File(pic.imagePath.path).toPath());
                imageType = pic.imagePath.path.substring(pic.imagePath.path.lastIndexOf(".") + 1);
    		    break;
    		case COMPOUND:
    		    imageAsByteArray = wContext.hwp.getChildBytes(pic.imagePath.path, pic.imagePath.compressed);
    		    imageType = pic.imagePath.path.substring(pic.imagePath.path.lastIndexOf(".") + 1);
    		    break;
    		case OWPML:
    		    String entry = wContext.hwpx.findBinData(pic.imagePath.path);
                imageType = entry.substring(entry.lastIndexOf(".") + 1);
                imageAsByteArray = wContext.hwpx.getBinDataByEntry(entry);
    		}

    		if (imageAsByteArray==null || imageAsByteArray.length==0) {
    			log.severe("Something Wrong!!!. skip drawing");
    			return;
    		}
    		
    		PropertyValue[] v = new PropertyValue[2];
    		v[0] = new PropertyValue();
    		v[0].Name = "InputStream";
    		v[0].Value = new ByteArrayToXInputStreamAdapter(imageAsByteArray);
    		v[1] = new PropertyValue();
    		v[1].Name = "MimeType";
    		switch(imageType.toLowerCase()) {
    		case "png":
        		v[1].Value = "image/png";
    			break;
    		case "bmp":
        		v[1].Value = "image/bmp";
    			break;
    		case "wmf":
        		v[1].Value = "image/x-wmf";
    			break;
    		case "jpg":
        		v[1].Value = "image/jpeg";
    			break;
    		case "gif":
        		v[1].Value = "image/gif";
    			break;
    		case "tif":
        		v[1].Value = "image/tiff";
    			break;
    		case "svg":
        		v[1].Value = "image/svg+xml";
    			break;
    		}

    		XGraphic graphic = xGraphicProvider.queryGraphic(v);
    		if (graphic == null) {
    			log.severe("Error loading the image");
    		} else {
    			xPropSet.setPropertyValue("Graphic", graphic);
    		}
	        // image ByteArray로 그림 그리기
	        
	        if (hasCaption) {
	        	try {
			        xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
		        xPropSet.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
		        xPropSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
		        xPropSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
		        xPropSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
	        } else {
                double xScale = pic.matrixSeq==null?1.0:pic.matrixSeq[0];
                double yScale = pic.matrixSeq==null?1.0:pic.matrixSeq[4];
	        	setPosition(xPropSet, pic, (int)(pic.nGrp>0?pic.xGrpOffset*xScale:0), (int)(pic.nGrp>0?pic.yGrpOffset*yScale:0));
	        }
            setWrapStyle(xPropSet, pic);
	        
			// 위치를 잡은 후에  크기를 조정한다.
	        xPropSet.setPropertyValue("Width", Transform.translateHwp2Office(sizeWidth));
	        xPropSet.setPropertyValue("Height", Transform.translateHwp2Office(sizeHeight));

	        // setLineStyle 대신 border 속성값을 직접 넣는다.
	        if (pic instanceof Ctrl_ShapePic) {
				BorderLine2 pictureBorder = Transform.toBorderLine2(pic);
				xPropSet.setPropertyValue("TopBorder", pictureBorder);
				xPropSet.setPropertyValue("BottomBorder", pictureBorder);
				xPropSet.setPropertyValue("LeftBorder", pictureBorder);
				xPropSet.setPropertyValue("RightBorder", pictureBorder);
	        }
	        
	        if (hasCaption) {
	            xFrameText.insertTextContent(xFrameCursor, xTextContent, true);
	            xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
	        } else {
		        wContext.mText.insertTextContent(wContext.mTextCursor, xTextContent, true);
		        if (wContext.version >= 72) {
		            TextContentAnchorType anchorType = (TextContentAnchorType)xPropSet.getPropertyValue("AnchorType");
		            if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
		                wContext.mText.insertString(wContext.mTextCursor, " ", false);
		            }
		        }
	        }
	        if (pic.nGrp==0) {
	            ++autoNum;
	        }
	        
	  		// 캡션 쓰기
  			if (hasCaption) {
  				addCaptionString(wContext, xFrameText, xFrameCursor, pic, step);
  			}
			
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
    	} catch (SkipDrawingException e) {
    	    e.printStackTrace();
    	} catch (IOException e) {
            e.printStackTrace();
        } catch (DataFormatException e) {
			e.printStackTrace();
		}

    }
    
    private static void insertVIDEO(WriterContext wContext, Ctrl_ShapeVideo vid, int step, int shapeWidth, int shapeHeight) {
        boolean hasCaption = vid.caption==null?false:vid.caption.size()==0?false:true;
        
        XTextFrame xFrame = null;
        XText xFrameText = null;
        XTextCursor xFrameCursor = null;
        try {
            if (hasCaption) {
                xFrame = makeOuterFrame(wContext, vid, false, step);
                // Frame 내부 Cursor 생성
                xFrameText = xFrame.getText();
                xFrameCursor = xFrameText.createTextCursor();
            }
            
            int sizeWidth = shapeWidth<=0?vid.width:shapeWidth;
            int sizeHeight = shapeHeight<=0?vid.height:shapeHeight;
            
            // 그림그리기
            Object textGraphicObject = wContext.mMSF.createInstance("com.sun.star.text.TextGraphicObject");
            XTextContent xTextContent = (XTextContent) UnoRuntime.queryInterface(XTextContent.class, textGraphicObject);
            XPropertySet xPropSet = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, textGraphicObject);
            
            // image ByteArray로 그림 그리기
            Object graphicProviderObject = wContext.mMCF.createInstanceWithContext("com.sun.star.graphic.GraphicProvider", wContext.mContext);
            XGraphicProvider xGraphicProvider = UnoRuntime.queryInterface(XGraphicProvider.class, graphicProviderObject);

            byte[] imageAsByteArray = wContext.getBinBytes(vid.thumnailBinID);
            
            if (imageAsByteArray==null || imageAsByteArray.length==0) {
                log.severe("Something Wrong!!!. skip drawing");
                return;
            }
            
            PropertyValue[] v = new PropertyValue[2];
            v[0] = new PropertyValue();
            v[0].Name = "InputStream";
            v[0].Value = new ByteArrayToXInputStreamAdapter(imageAsByteArray);
            v[1] = new PropertyValue();
            v[1].Name = "MimeType";
            switch(WriterContext.getBinFormat(vid.thumnailBinID).toLowerCase()) {
            case "png":
                v[1].Value = "image/png";
                break;
            case "bmp":
                v[1].Value = "image/bmp";
                break;
            case "wmf":
                v[1].Value = "image/x-wmf";
                break;
            case "jpg":
                v[1].Value = "image/jpeg";
                break;
            case "gif":
                v[1].Value = "image/gif";
                break;
            case "tif":
                v[1].Value = "image/tiff";
                break;
            case "svg":
                v[1].Value = "image/svg+xml";
                break;
            }

            XGraphic graphic = xGraphicProvider.queryGraphic(v);
            if (graphic == null) {
                log.severe("Error loading the image");
            } else {
                xPropSet.setPropertyValue("Graphic", graphic);
            }
            // image ByteArray로 그림 그리기
            
            if (hasCaption) {
                try {
                    xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
                } catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
                    log.severe("AnchorType has Exception");
                }
                xPropSet.setPropertyValue("VertOrient", VertOrientation.CENTER);                // Top, Bottom, Center, fromBottom
                xPropSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
                xPropSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);    // 0:NONE=From left
                xPropSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
            } else {
                double xScale = vid.matrixSeq==null?1.0:vid.matrixSeq[0];
                double yScale = vid.matrixSeq==null?1.0:vid.matrixSeq[4];
                setPosition(xPropSet, vid, (int)(vid.nGrp>0?vid.xGrpOffset*xScale:0), (int)(vid.nGrp>0?vid.yGrpOffset*yScale:0));
            }
            setWrapStyle(xPropSet, vid);
            
            // 위치를 잡은 후에  크기를 조정한다.
            xPropSet.setPropertyValue("Width", Transform.translateHwp2Office(sizeWidth));
            xPropSet.setPropertyValue("Height", Transform.translateHwp2Office(sizeHeight));

            if (hasCaption) {
                xFrameText.insertTextContent(xFrameCursor, xTextContent, true);
                xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
            } else {
                wContext.mText.insertTextContent(wContext.mTextCursor, xTextContent, true);
                if (wContext.version >= 72) {
                    TextContentAnchorType anchorType = (TextContentAnchorType)xPropSet.getPropertyValue("AnchorType");
                    if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                        wContext.mText.insertString(wContext.mTextCursor, " ", false);
                    }
                }
            }
            if (vid.nGrp==0) {
                ++autoNum;
            }
            
            // 캡션 쓰기
            if (hasCaption) {
                addCaptionString(wContext, xFrameText, xFrameCursor, vid, step);
            }
            
        } catch (com.sun.star.uno.Exception e) {
            e.printStackTrace();
        } catch (SkipDrawingException e) {
            e.printStackTrace();
        }

    }

    private static void insertMulti(WriterContext wContext, Ctrl_Container container, int step) {
    	boolean hasCaption = container.caption==null?false:container.caption.size()==0?false:true;

    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;
    	XPropertySet paraProps = null;
    	try {
			xFrame = makeOuterFrame(wContext, container, false, step);
			// Frame 내부 Cursor 생성
			xFrameText = xFrame.getText();
			xFrameCursor = xFrameText.createTextCursor();
			
			WriterContext frameContext = new WriterContext();
			frameContext.hwp 			= wContext.hwp;
			frameContext.mContext 		= wContext.mContext;
			frameContext.mDesktop 		= wContext.mDesktop;
			frameContext.mMCF			= wContext.mMCF;
			frameContext.mMSF			= wContext.mMSF;
			frameContext.mMyDocument 	= wContext.mMyDocument;
			frameContext.mText			= xFrameText;
			frameContext.mTextCursor	= xFrameCursor;
			
			for (Ctrl_GeneralShape shape: container.list) {
			    
                double xScale = 1.0, yScale = 1.0;
			    double radian = 0.0;
                int sizeWidth = 0, sizeHeight = 0;
                for (int i=0; i<shape.matCnt; i++) {
                    xScale *= shape.matrixSeq[i*12+0];
                    yScale *= shape.matrixSeq[i*12+4];
                    radian += Math.atan2(shape.matrixSeq[i*12+9], shape.matrixSeq[i*12+6]);
                }
                if (radian != 0.0 && shape.rotat == 0) {
                    shape.rotat = (short) Math.toDegrees(radian);
                }

			    switch(shape.getClass().getSimpleName()) {
			    case "Ctrl_ShapeArc":
			    case "Ctrl_ShapeEllipse":
			    case "Ctrl_ShapeRect":
			    case "Ctrl_ShapePolygon":
			    case "Ctrl_ShapePic":
                    sizeWidth = shape.curWidth;
                    sizeHeight = shape.curHeight;
                    // 2레벨 container(nGrp>=2) 에서는  무조건 scale 연산을 하도록.
                    if (shape.nGrp>=1 || (shape.curWidth != shape.iniWidth || shape.curHeight != shape.iniHeight)) {
                        sizeWidth = (int)(shape.iniWidth*xScale /*/container.matrixSeq[0]*/);
                        sizeHeight = (int)(shape.iniHeight*yScale /*/container.matrixSeq[4]*/);
                    }
			        break;
			    case "Ctrl_ShapeLine":
			        sizeWidth = (((Ctrl_ShapeLine)shape).endX-((Ctrl_ShapeLine)shape).startX);
			        sizeHeight = (((Ctrl_ShapeLine)shape).endY-((Ctrl_ShapeLine)shape).startY);
                    sizeWidth = (int)(shape.iniWidth*xScale /*/container.matrixSeq[0]*/);
                    sizeHeight = (int)(shape.iniHeight*yScale /*/container.matrixSeq[4]*/);
			        break;
			    case "Ctrl_Container":
		        default:
			    }
                
				if (shape instanceof Ctrl_ShapeArc) {
					Ctrl_ShapeArc arc = (Ctrl_ShapeArc) shape;
					insertARC(frameContext, arc, step+1, sizeWidth, sizeHeight);
				} else if (shape instanceof Ctrl_ShapeEllipse) {
                    Ctrl_ShapeEllipse ell = (Ctrl_ShapeEllipse) shape;
                    insertELLIPSE(frameContext, ell, step+1, sizeWidth, sizeHeight);
				} else if (shape instanceof Ctrl_ShapeRect) {
                    Ctrl_ShapeRect rect = (Ctrl_ShapeRect) shape;
                    if (shape.paras==null || shape.paras.size()<1) {
                        insertRECTANGLE(frameContext, rect, step+1, sizeWidth, sizeHeight);
                    } else {
                        insertTextFrame(frameContext, rect, step+1, sizeWidth, sizeHeight);
                    }
				} else if (shape instanceof Ctrl_ShapePolygon) {
					// Polygon 내부에 테이블이 있는 경우 LibreOffice에서는 틀(Frame)으로 변환한다. LibreOffice에서 테이블을 넣을수 있는 개체는 Frame뿐인듯 하다.
                    boolean hasTable = shape.paras==null?false:
                                        shape.paras.stream()
                                                   .anyMatch(para -> { 
                                                             if (para.p==null || para.p.size()==0) return false;
                                                             return para.p.stream().anyMatch(ctrl -> ctrl instanceof Ctrl_Table); 
                                                         });
					if (hasTable) {
						insertTextFrame(frameContext, shape, step+1, sizeWidth, sizeHeight);
					} else {
	                    Ctrl_ShapePolygon pol = (Ctrl_ShapePolygon) shape;
	                    insertPOLYGON(frameContext, pol, step+1, sizeWidth, sizeHeight);
					}
				} else if (shape instanceof Ctrl_ShapePic) {
				    Ctrl_ShapePic pic = (Ctrl_ShapePic) shape;
				    insertPICTURE(frameContext, pic, step+1, sizeWidth, sizeHeight);
				} else if (shape instanceof Ctrl_ShapeLine) {
                    Ctrl_ShapeLine lin = (Ctrl_ShapeLine) shape;
                    insertLINE(frameContext, lin, step+1, sizeWidth, sizeHeight);
				} else if (shape instanceof Ctrl_Container) {
				    Ctrl_Container con = (Ctrl_Container) shape;
				    insertMulti(frameContext, con, step+1);
				}
			}
			if (container.nGrp==0) {
			    ++autoNum;
			}

	  		// 캡션 쓰기
  			if (hasCaption) {
		        xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
  				addCaptionString(wContext, xFrameText, xFrameCursor, container, step);
  			}
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
    	} catch (SkipDrawingException e) {
    	    e.printStackTrace();
    	}
    }

    
    private static void insertDummyTextFrame(WriterContext wContext, Ctrl_GeneralShape shape, int step) {

    	try {
	    	Object oFrame = wContext.mMSF.createInstance("com.sun.star.text.TextFrame");
	        XTextFrame xFrame = (XTextFrame) UnoRuntime.queryInterface(XTextFrame.class, oFrame);
	
			if (xFrame == null) {
				log.severe("Could not create a text frame");
				return;
			}
			
			XShape tfShape = UnoRuntime.queryInterface(XShape.class, xFrame);
			tfShape.setSize(new Size(Transform.translateHwp2Office(shape.width), Transform.translateHwp2Office(shape.height)));
			
			XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);

			setPosition(frameProps, shape, 0, 0);
    		setWrapStyle(frameProps, shape);

			// dummy에서는  점선, 가는 회색 테두리로  그리고, 내부에는 "Not Supported Object" 회색 글씨를 넣도록 한다.
			BorderLine2 border = new BorderLine2();
			border.Color= 0x808080; // GREY
			border.LineStyle = BorderLineStyle.DOTTED;
			border.InnerLineWidth = 0;
			border.OuterLineWidth = 0;
			border.LineDistance = 0;
			border.LineWidth = 35;	// 10=0.3pt, 35=1pt
			frameProps.setPropertyValue("TopBorder", border);
			frameProps.setPropertyValue("BottomBorder", border);
			frameProps.setPropertyValue("LeftBorder", border);
			frameProps.setPropertyValue("RightBorder", border);

			XText xText = wContext.mTextCursor.getText();
			xText.insertTextContent(wContext.mTextCursor, xFrame, false);
            if (wContext.version >= 72) {
                TextContentAnchorType anchorType = (TextContentAnchorType)frameProps.getPropertyValue("AnchorType");
                if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                    xText.insertString(wContext.mTextCursor, " ", false);
                }
            }
			frameProps.setPropertyValue("FrameIsAutomaticHeight", false);	// TextFrame을 그린 후에 automaticHeight를 조정해야..
			frameProps.setPropertyValue("TextVerticalAdjust", TextVerticalAdjust.CENTER);

			wContext.mTextCursor.gotoEnd(false);
			
			XText xFrameText = xFrame.getText();
			XTextCursor xFrameCursor = xFrameText.createTextCursor();
	  		XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, xFrameCursor);
	  		XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
  			paraProps.setPropertyValue("CharColor", 0x808080);		// 회색글씨
	  		paraProps.setPropertyValue("ParaAdjust", ParagraphAdjust.CENTER);
			
			xFrameText.insertString(xFrameCursor, "Not Supported Object", false);
			if (shape.nGrp==0) {
			    ++autoNum;
			}
	
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
    	} catch (SkipDrawingException e) {
    	    e.printStackTrace();
    	}

    }

    private static void insertTextFrame(WriterContext wOuterContext, Ctrl_GeneralShape shape, int step, int shapeWidth, int shapeHeight) {
    	try {
	    	Object oFrame = wOuterContext.mMSF.createInstance("com.sun.star.text.TextFrame");
	        XTextFrame xInternalFrame = (XTextFrame) UnoRuntime.queryInterface(XTextFrame.class, oFrame);
			if (xInternalFrame == null) {
				log.severe("Could not create a text frame");
				return;
			}
			
			int sizeWidth = 0, sizeHeight = 0;
			if (shapeWidth<=0 && shapeHeight<=0) {
    			sizeWidth = shape.curWidth;
    			sizeHeight = shape.curHeight;
                if (shape.rotat != 0) {
                    Point2D ptSrc = new Point2D.Double(sizeWidth, sizeHeight);
                    Point2D ptDst = Transform.rotateValue(shape.rotat, ptSrc);
                    sizeWidth = (int)ptDst.getX(); sizeHeight = (int)ptDst.getY();
                }
			} else {
			    sizeWidth = shapeWidth;
			    sizeHeight = shapeHeight;
			}
			
			XShape tfShape = UnoRuntime.queryInterface(XShape.class, xInternalFrame);
			tfShape.setSize(new Size(Transform.translateHwp2Office(sizeWidth), Transform.translateHwp2Office(sizeHeight)));
			
			// anchor the text frame
			XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xInternalFrame);
			frameProps.setPropertyValue("FrameIsAutomaticHeight", false);

            double xScale = shape.matrixSeq==null?1.0:shape.matrixSeq[0];
            double yScale = shape.matrixSeq==null?1.0:shape.matrixSeq[4];
			setPosition(frameProps, shape, (int)(shape.nGrp>0?shape.xGrpOffset*xScale:0), (int)(shape.nGrp>0?shape.yGrpOffset*yScale:0));
    		setWrapStyle(frameProps, shape);

    		// frameProps.setPropertyValue("ZOrder", shape.zOrder);
			frameProps.setPropertyValue("LeftMargin", 0);
			frameProps.setPropertyValue("RightMargin", 0);
			frameProps.setPropertyValue("TopMargin", 0);
			frameProps.setPropertyValue("BottomMargin", 0);

			BorderLine2 border = Transform.toBorderLine2(shape);
			frameProps.setPropertyValue("TopBorder", border);
			frameProps.setPropertyValue("BottomBorder", border);
			frameProps.setPropertyValue("LeftBorder", border);
			frameProps.setPropertyValue("RightBorder", border);
			frameProps.setPropertyValue("LeftBorderDistance", Transform.translateHwp2Office(shape.leftSpace)<=100?0:Transform.translateHwp2Office(shape.leftSpace)-100);
			frameProps.setPropertyValue("RightBorderDistance", Transform.translateHwp2Office(shape.rightSpace)<=100?0:Transform.translateHwp2Office(shape.rightSpace)-100);
			frameProps.setPropertyValue("TopBorderDistance", Transform.translateHwp2Office(shape.upSpace)<=100?0:Transform.translateHwp2Office(shape.upSpace)-100);
			frameProps.setPropertyValue("BottomBorderDistance", Transform.translateHwp2Office(shape.downSpace)<=100?0:Transform.translateHwp2Office(shape.downSpace)-100);
			
			// fill color
			if (shape.fill!=null) {
			    setFillStyle(frameProps, shape.fill);
			} else {
			    // Fill 이 없는 경우는  Transparency 100%로 설정한다.
			    frameProps.setPropertyValue("FillStyle", FillStyle.NONE);
			    frameProps.setPropertyValue("FillTransparence", 100);
			}

			// insert text frame into document (order is important here)
			XText xText = wOuterContext.mTextCursor.getText();
			xText.insertTextContent(wOuterContext.mTextCursor, xInternalFrame, false);
            if (wOuterContext.version >= 72) {
                TextContentAnchorType anchorType = (TextContentAnchorType)frameProps.getPropertyValue("AnchorType");
                if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                    xText.insertString(wOuterContext.mTextCursor, " ", false);
                }
            }

			frameProps.setPropertyValue("FrameIsAutomaticHeight", false);	// TextFrame을 그린 후에 automaticHeight를 조정해야..
																						// embedded rect는 autoHeight=true로 했었는데,  임시로 false로 해보자.
			frameProps.setPropertyValue("TextVerticalAdjust", Transform.toTextVertAlign(shape.textVerAlign.ordinal()));
			wOuterContext.mTextCursor.gotoEnd(false);

			if (shape.paras!=null) {
				WriterContext innerContext = new WriterContext();
				innerContext.hwp 			= wOuterContext.hwp;
				innerContext.mContext 		= wOuterContext.mContext;
				innerContext.mDesktop 		= wOuterContext.mDesktop;
				innerContext.mMCF			= wOuterContext.mMCF;
				innerContext.mMSF			= wOuterContext.mMSF;
				innerContext.mMyDocument 	= wOuterContext.mMyDocument;
				innerContext.mText			= xInternalFrame.getText();
				innerContext.mTextCursor	= innerContext.mText.createTextCursor();
				
	            // 외부Frame과 Ctrl의 크기를 비교한다.  Ctrl의 크기가 크다면, 일부 보여지지 않아야 하므로, ZOrder를 낮게 수정한다.
				int maxCtrlWidth = 0, maxCtrlHeight = 0;
				try {
                    maxCtrlWidth = shape.paras.stream().filter(para -> para.p!=null && para.p.size()>0)
                                        .flatMap(para -> para.p.stream())
                                        .filter(ctrl -> ctrl instanceof Ctrl_Common)
                                        .mapToInt(ctrl -> Integer.valueOf(((Ctrl_Common)ctrl).width))
                                        .max().getAsInt();
                    maxCtrlHeight = shape.paras.stream().filter(para -> para.p!=null && para.p.size()>0)
                                        .flatMap(para -> para.p.stream())
                                        .filter(ctrl -> ctrl instanceof Ctrl_Common)
                                        .mapToInt(ctrl -> Integer.valueOf(((Ctrl_Common)ctrl).height))
                                        .max().getAsInt();
                } catch (NoSuchElementException e) {
                    log.fine("Cannot get OptionalInt either maxCtrlWidth or maxCtrlHeight. " + e.getLocalizedMessage());
                }

				for (HwpParagraph para: shape.paras) {
				    // 테이블은 Frame 크기를 넘지 못하므로, 테이블 크기만큼 내부 Frame을 다시 만들어야 한다.
				    // 다만, 큰 테이블이라도  보이는건  외부 Frame 만큼 보이도록 한다.
					HwpCallback callback = new HwpCallback(TableFrame.MAKE);
					if (sizeWidth < maxCtrlWidth || sizeHeight < maxCtrlHeight) {
					    callback = new HwpCallback(TableFrame.MAKE_PART);
					}
					HwpRecurs.printParaRecurs(innerContext, para, callback, step+1);
				}
				HwpRecurs.removeLastParaBreak(innerContext.mTextCursor);
    			if (shape.nGrp==0) {
    			    ++autoNum;
    			}
			}			
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
    	} catch (SkipDrawingException e) {
    	    e.printStackTrace();
    	}

    }

    private static void insertRECTANGLE(WriterContext wOuterContext, Ctrl_GeneralShape shape, int step, int shapeWidth, int shapeHeight) {
        try {
            Object xObj = wOuterContext.mMSF.createInstance("com.sun.star.drawing.RectangleShape");

            XShape xShape = UnoRuntime.queryInterface(XShape.class, xObj);
            XTextContent xTextContentShape = (XTextContent)UnoRuntime.queryInterface(XTextContent.class, xObj);
            
            
            if (shape.nGrp==0) {
                int sizeWidth = 0, sizeHeight = 0;
                if (shapeWidth<=0 && shapeHeight<=0) {
                    sizeWidth = shape.curWidth;
                    sizeHeight = shape.curHeight;
                    if (shape.rotat != 0) {
                        Point2D ptSrc = new Point2D.Double(sizeWidth, sizeHeight);
                        Point2D ptDst = Transform.rotateValue(shape.rotat, ptSrc);
                        sizeWidth = (int)ptDst.getX(); sizeHeight = (int)ptDst.getY();
                    }
                } else {
                    sizeWidth = shapeWidth;
                    sizeHeight = shapeHeight;
                }
                xShape.setSize(new Size(Transform.translateHwp2Office(sizeWidth), Transform.translateHwp2Office(sizeHeight)));
            } else {
                xShape.setSize(new Size(shape.iniWidth, shape.iniHeight));
            }
            
            // anchor the text frame
            XPropertySet xPropsSet = UnoRuntime.queryInterface(XPropertySet.class, xShape);

            BorderLine2 border = Transform.toBorderLine2(shape);
            xPropsSet.setPropertyValue("TopBorder", border);
            xPropsSet.setPropertyValue("BottomBorder", border);
            xPropsSet.setPropertyValue("LeftBorder", border);
            xPropsSet.setPropertyValue("RightBorder", border);
            xPropsSet.setPropertyValue("LeftBorderDistance", Transform.translateHwp2Office(shape.leftSpace)<=100?0:Transform.translateHwp2Office(shape.leftSpace)-100);
            xPropsSet.setPropertyValue("RightBorderDistance", Transform.translateHwp2Office(shape.rightSpace)<=100?0:Transform.translateHwp2Office(shape.rightSpace)-100);
            xPropsSet.setPropertyValue("TopBorderDistance", Transform.translateHwp2Office(shape.upSpace)<=100?0:Transform.translateHwp2Office(shape.upSpace)-100);
            xPropsSet.setPropertyValue("BottomBorderDistance", Transform.translateHwp2Office(shape.downSpace)<=100?0:Transform.translateHwp2Office(shape.downSpace)-100);
            
            // insert text frame into document (order is important here)
            XText xText = wOuterContext.mTextCursor.getText();
            if (shape.nGrp>0) {
                xPropsSet.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
            } else {
                xPropsSet.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
                setPosition(xPropsSet, shape, 0, 0);
            }

            xText.insertTextContent(wOuterContext.mTextCursor, xTextContentShape, false);

            if (shape.nGrp>0) {
                transform(xPropsSet, shape);
            }
            
            setWrapStyle(xPropsSet, shape);
            
            // ZOrder 설정해도 변경되지 않는다. 대신 gso Ctrl에서 꺼내올때 zOrder 순서로 가져와서 그린다.
            // xPropsSet.setPropertyValue("ZOrder", Integer.valueOf(shape.zOrder));
            xPropsSet.setPropertyValue("LeftMargin", 0);
            xPropsSet.setPropertyValue("RightMargin", 0);
            xPropsSet.setPropertyValue("TopMargin", 0);
            xPropsSet.setPropertyValue("BottomMargin", 0);

            // fill color
            if (shape.fill!=null) {
                setFillStyle(xPropsSet, shape.fill);
            } else {
                // Fill 이 없는 경우는  Transparency 100%로 설정한다.
                xPropsSet.setPropertyValue("FillStyle", FillStyle.NONE);
                xPropsSet.setPropertyValue("FillTransparence", 100);
            }
            
            if (wOuterContext.version >= 72) {
                TextContentAnchorType anchorType = (TextContentAnchorType)xPropsSet.getPropertyValue("AnchorType");
                if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                    xText.insertString(wOuterContext.mTextCursor, " ", false);
                }
            }

            wOuterContext.mTextCursor.gotoEnd(false);

            if (shape.paras!=null) {
                xPropsSet.setPropertyValue("TextVerticalAdjust", Transform.toTextVertAlign(shape.textVerAlign.ordinal()));
                xPropsSet.setPropertyValue("TextHorizontalAdjust", TextHorizontalAdjust.CENTER);

                WriterContext innerContext = new WriterContext();
                innerContext.hwp            = wOuterContext.hwp;
                innerContext.mContext       = wOuterContext.mContext;
                innerContext.mDesktop       = wOuterContext.mDesktop;
                innerContext.mMCF           = wOuterContext.mMCF;
                innerContext.mMSF           = wOuterContext.mMSF;
                innerContext.mMyDocument    = wOuterContext.mMyDocument;
                innerContext.mText          = (XText)UnoRuntime.queryInterface(XText.class, xShape);
                innerContext.mTextCursor    = innerContext.mText.createTextCursor();
                
                // 외부Frame과 Ctrl의 크기를 비교한다.  Ctrl의 크기가 크다면, 일부 보여지지 않아야 하므로, ZOrder를 낮게 수정한다.
                int maxCtrlWidth = 0, maxCtrlHeight = 0;
                try {
                    maxCtrlWidth = shape.paras.stream().filter(para -> para.p!=null && para.p.size()>0)
                                        .flatMap(para -> para.p.stream())
                                        .filter(ctrl -> ctrl instanceof Ctrl_Common)
                                        .mapToInt(ctrl -> Integer.valueOf(((Ctrl_Common)ctrl).width))
                                        .max().getAsInt();
                    maxCtrlHeight = shape.paras.stream().filter(para -> para.p!=null && para.p.size()>0)
                                        .flatMap(para -> para.p.stream())
                                        .filter(ctrl -> ctrl instanceof Ctrl_Common)
                                        .mapToInt(ctrl -> Integer.valueOf(((Ctrl_Common)ctrl).height))
                                        .max().getAsInt();
                } catch (NoSuchElementException e) {
                    log.fine("Cannot get OptionalInt either maxCtrlWidth or maxCtrlHeight. " + e.getLocalizedMessage());
                }
                for (HwpParagraph para: shape.paras) {
                    // 테이블은 Frame 크기를 넘지 못하므로, 테이블 크기만큼 내부 Frame을 다시 만들어야 한다.
                    // 다만, 큰 테이블이라도  보이는건  외부 Frame 만큼 보이도록 한다.
                    HwpCallback callback = new HwpCallback(TableFrame.MAKE);
                    if (shape.curWidth < maxCtrlWidth || shape.curHeight < maxCtrlHeight) {
                        callback = new HwpCallback(TableFrame.MAKE_PART);
                    }
                    for (Ctrl c: para.p) {
                        if (c instanceof ParaText) {
                            ParaText paraText = (ParaText)c;
                            paraText.text = paraText.text.replaceAll("\r|\n", "");
                        }
                    }
                    HwpRecurs.printParaRecurs(innerContext, para, callback, step+1);
                }
                // REMOVE last PARA_BREAK. 하지만 shape에서는 동작하지 않음.
                HwpRecurs.removeLastParaBreak(innerContext.mTextCursor);
                if (shape.nGrp==0) {
                    ++autoNum;
                }
            }           
        } catch (com.sun.star.uno.Exception e) {
            e.printStackTrace();
        } catch (SkipDrawingException e) {
            e.printStackTrace();
        }
    }

    private static void insertLINE(WriterContext wContext, Ctrl_ShapeLine shape, int step, int shapeWidth, int shapeHeight)  {
    	boolean hasCaption = shape.caption==null?false:shape.caption.size()==0?false:true;
    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;

    	try {
    		if (hasCaption) {
    			xFrame = makeOuterFrame(wContext, shape, false, step);
				// Frame 내부 Cursor 생성
				xFrameText = xFrame.getText();
				xFrameCursor = xFrameText.createTextCursor();
    		}

	        Object xObj = wContext.mMSF.createInstance("com.sun.star.drawing.LineShape");
	        XTextContent xTextContentShape = (XTextContent)UnoRuntime.queryInterface(XTextContent.class, xObj);
	        XShape xShape = (XShape)UnoRuntime.queryInterface(XShape.class, xObj);

	        if (shape.nGrp==0) {
	            int sizeWidth = 0, sizeHeight = 0;
            
	            if (shapeWidth<=0 && shapeHeight<=0) {
	                double xScale = 1.0, yScale = 1.0;
	                for (int i=0; i<shape.matCnt; i++) {
	                    xScale *= shape.matrixSeq[i*12+0];
	                    yScale *= shape.matrixSeq[i*12+4];
	                    log.finest("[LINE] matCnt="+i+",matCnt="+shape.matCnt+",xSclae="+xScale+",yScale="+yScale);
	                }

	                double xSize = (shape.endX-shape.startX)*xScale;
	                double ySize = (shape.endY-shape.startY)*yScale;

	                if (shape.rotat != 0) {
	                    Point2D ptSrc = new Point2D.Double(xSize, ySize);
	                    Point2D ptDst = Transform.rotateValue(shape.rotat, ptSrc);
	                    xSize = ptDst.getX(); ySize = ptDst.getY();
	                }
	                sizeWidth = (int) xSize;
	                sizeHeight = (int) ySize;
	            } else {
	                sizeWidth = shapeWidth;
	                sizeHeight = shapeHeight;
	            }
            
	            Point aPos = new Point(0, 0);
	            Size aSize = new Size(Transform.translateHwp2Office(sizeWidth), Transform.translateHwp2Office(sizeHeight));
	            xShape.setPosition(aPos);
	            xShape.setSize(aSize);
	        } else {
	            xShape.setSize(new Size(shape.iniWidth, shape.iniHeight));
	        }
    		XPropertySet xPropsSet = (XPropertySet)UnoRuntime.queryInterface(XPropertySet.class, xShape);

    		if (hasCaption) {
    			try {
    			    xPropsSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
    			xPropsSet.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
		        xPropsSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
		        xPropsSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
		        xPropsSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
    		} else {
	            if (shape.nGrp==0) {
                    xPropsSet.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
    				setPosition(xPropsSet, shape, 0, 0);
				} else {
                    xPropsSet.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
				}
    		}
    		setWrapStyle(xPropsSet, shape);
            setLineStyle(xPropsSet, shape);
    		
	        if (hasCaption) {
	        	XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);
		        // 투명하게 해야  도형이 보인다.
	    		frameProps.setPropertyValue("FillTransparence", 100);

		        xFrameText.insertTextContent(xFrameCursor, xTextContentShape, true);
		        xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
	        } else {
		        wContext.mText.insertTextContent(wContext.mTextCursor, xTextContentShape, true);
	            
		        if (shape.nGrp>0) {
	            	transform(xPropsSet, shape);
	            	// workaround-LineShape-transform START
    	        	// transform 으로 크기,이동,회전이 모두 변환되어야 할것 같은데, scale은 변하지 않음. 따라서 transform 이후에 Size를 추가로 설정한다.
    	        	if (xShape.getSize().Width/10 == shape.iniWidth/10 && xShape.getSize().Height/10 == shape.iniHeight/10) {
    	            	if ((shape.rotat >= 45 && shape.rotat < 135) || shape.rotat >= 225 && shape.rotat < 315) {
    	            	    xShape.setSize(new Size(Transform.translateHwp2Office(shapeWidth==-1 && shapeHeight==-1 ? shape.curHeight : shapeHeight), 
    	            	                            Transform.translateHwp2Office(shapeWidth==-1 && shapeHeight==-1 ? shape.curWidth : shapeWidth)));
    	            	} else {
    	            	    xShape.setSize(new Size(Transform.translateHwp2Office(shapeWidth==-1 && shapeHeight==-1 ? shape.curWidth : shapeWidth), 
    	            	                            Transform.translateHwp2Office(shapeWidth==-1 && shapeHeight==-1 ? shape.curHeight : shapeHeight)));
    	            	}
    	        	}
    	        	// workaround-LineShape-transform END
	        	}

	            if (step ==2 && wContext.version >= 72) {
	                TextContentAnchorType anchorType = (TextContentAnchorType)xPropsSet.getPropertyValue("AnchorType");
	                if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
	                    wContext.mText.insertString(wContext.mTextCursor, " ", false);
	                }
	            }
	        }

    		setArrowStyle(xPropsSet, (shape.lineAttr>>10)&0x3F, (shape.lineAttr>>22)&0x0F, ((shape.lineAttr>>30)&0x1)==1?true:false, true);
    		setArrowStyle(xPropsSet, (shape.lineAttr>>16)&0x3F, (shape.lineAttr>>26)&0x0F, ((shape.lineAttr>>31)&0x1)==1?true:false, false);
             if (shape.nGrp==0) {
                ++autoNum;
            }
            
	  		// 캡션 쓰기
  			if (hasCaption) {
  				addCaptionString(wContext, xFrameText, xFrameCursor, shape, step);
  			}
	        
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
		} catch (SkipDrawingException e) {
		    e.printStackTrace();
		}
    }
    
    private static void insertELLIPSE(WriterContext wContext, Ctrl_ShapeEllipse ell, int step, int shapeWidth, int shapeHeight)  {
    	boolean hasCaption = ell.caption==null?false:ell.caption.size()==0?false:true;
    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;

    	try {
    		if (hasCaption) {
    			xFrame = makeOuterFrame(wContext, ell, false, step);
				// Frame 내부 Cursor 생성
				xFrameText = xFrame.getText();
				xFrameCursor = xFrameText.createTextCursor();
    		}
    		
	        Object xObj = wContext.mMSF.createInstance("com.sun.star.drawing.EllipseShape");
	        XTextContent xTextContentShape = (XTextContent)UnoRuntime.queryInterface(XTextContent.class, xObj);
	        XShape xShape = (XShape)UnoRuntime.queryInterface(XShape.class, xObj);
	        
	        int sizeWidth = shapeWidth<=0 ? ell.curWidth : shapeWidth;
	        int sizeHeight = shapeHeight<=0 ? ell.curHeight : shapeHeight;
	        
	        // 그릴 위치
	        Point aPos = new Point(0, 0);
	        Size aSize = new Size(Transform.translateHwp2Office(sizeWidth), Transform.translateHwp2Office(sizeHeight));
	        xShape.setPosition(aPos);
	        xShape.setSize(aSize);
    		XPropertySet xPropSet = (XPropertySet)UnoRuntime.queryInterface(XPropertySet.class, xShape);

    		if (hasCaption) {
    			try {
    				xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
		        xPropSet.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
		        xPropSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
		        xPropSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
		        xPropSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        // 투명하게 해야  도형이 보인다.
		        // xPropSet.setPropertyValue("BackTransparent", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        xPropSet.setPropertyValue("FillStyle", FillStyle.NONE);
		        xPropSet.setPropertyValue("FillTransparence", 100);
    		} else {
    			setPosition(xPropSet, ell, ell.nGrp>0?ell.xGrpOffset:0, ell.nGrp>0?ell.yGrpOffset:0);
    		}
    		setWrapStyle(xPropSet, ell);
    	    setLineStyle(xPropSet, ell);
    	    
       		xPropSet.setPropertyValue("CircleKind", CircleKind.FULL);
    		setFillStyle(xPropSet, ell.fill);
    		
	        if (hasCaption) {
	        	XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);
		        // 투명하게 해야  도형이 보인다.
	    		frameProps.setPropertyValue("FillTransparence", 100);

		        xFrameText.insertTextContent(xFrameCursor, xTextContentShape, false);
		        xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
	        } else {
		        wContext.mText.insertTextContent(wContext.mTextCursor, xTextContentShape, false);
	            // workaround-LibreOffice7.2 START
		        if (step == 2 && wContext.version >= 72) {
		            TextContentAnchorType anchorType = (TextContentAnchorType)xPropSet.getPropertyValue("AnchorType");
		            if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
		                wContext.mText.insertString(wContext.mTextCursor, " ", false);
		            }
		        }
                // workaround-LibreOffice7.2 END
	        }
	        if (ell.nGrp==0) {
	            ++autoNum;
	        }

	  		// 캡션 쓰기
  			if (hasCaption) {
  				addCaptionString(wContext, xFrameText, xFrameCursor, ell, step);
  			}
	        
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
		} catch (SkipDrawingException e) {
		    e.printStackTrace();
		}
    }

    private static void insertPOLYGON(WriterContext wContext, Ctrl_ShapePolygon pol, int step, int shapeWidth, int shapeHeight) {
    	// check below URL first before make the code to draw shapes.
    	// https://wiki.openoffice.org/wiki/Documentation/DevGuide/Drawings/Shape_Types
        
    	boolean hasParas = pol.paras==null?false:pol.paras.size()==0?false:true;
    	boolean hasCaption = pol.caption==null?false:pol.caption.size()==0?false:true;
    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;
    	XPropertySet paraProps = null;

    	try {
    		if (hasParas || hasCaption) {
    			xFrame = makeOuterFrame(wContext, pol, hasParas, step);
				// Frame 내부 Cursor 생성
				xFrameText = xFrame.getText();
				xFrameCursor = xFrameText.createTextCursor();
    		}

	        Object xObj = wContext.mMSF.createInstance("com.sun.star.drawing.PolyPolygonShape");
	        XTextContent xTextContentShape = (XTextContent)UnoRuntime.queryInterface(XTextContent.class, xObj);
	        XShape xShape = (XShape)UnoRuntime.queryInterface(XShape.class, xObj);
	        
	        int sizeWidth = shapeWidth<=0?pol.curWidth:shapeWidth;
	        int sizeHeight = shapeHeight<=0?pol.curHeight:shapeHeight;
	        
	        // 그릴 위치
	        Point aPos = new Point(0, 0);
	        Size aSize = new Size(Transform.translateHwp2Office(sizeWidth), Transform.translateHwp2Office(sizeHeight));
	        xShape.setPosition(aPos);
	        xShape.setSize(aSize);
    		XPropertySet xPropSet = (XPropertySet)UnoRuntime.queryInterface(XPropertySet.class, xShape);

    		if (hasParas || hasCaption) {
    			try {
    				xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
		        xPropSet.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
		        xPropSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
		        xPropSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
		        xPropSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        // 투명하게 해야  도형이 보인다.
		        // xPropSet.setPropertyValue("BackTransparent", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        xPropSet.setPropertyValue("FillStyle", FillStyle.NONE);
		        xPropSet.setPropertyValue("FillTransparence", 100);
    		} else {
    			setPosition(xPropSet, pol, pol.nGrp>0?pol.xGrpOffset:0, pol.nGrp>0?pol.yGrpOffset:0);
    		}
    		setWrapStyle(xPropSet, pol);
    	    setLineStyle(xPropSet, pol);

    		PolyPolygonBezierCoords aCoords = new PolyPolygonBezierCoords();
    		int nPointCount = pol.nPoints;
    		aCoords.Coordinates = new Point[1][];
    		aCoords.Flags = new PolygonFlags[1][];
   			Point[] pPolyPoints = new Point[nPointCount];
   			PolygonFlags[]pPolyFlags = new PolygonFlags[nPointCount];
   			for (int n = 0; n < nPointCount; n++) {
				pPolyPoints[n] = new Point();
    			pPolyPoints[n].X = Transform.translateHwp2Office(pol.points[n].x);
    			pPolyPoints[n].Y = Transform.translateHwp2Office(pol.points[n].y);
    			pPolyFlags [n] = PolygonFlags.NORMAL;
    		}
   			aCoords.Coordinates[0] = pPolyPoints;
   			aCoords.Flags[0] = pPolyFlags;    			
    		xPropSet.setPropertyValue("PolyPolygonBezier", aCoords);
    		
    		setFillStyle(xPropSet, pol.fill);
    		
	        if (hasParas || hasCaption) {
	        	XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);
		        // 투명하게 해야  도형이 보인다.
	    		frameProps.setPropertyValue("FillTransparence", 100);
	        }
	        
	        // [21.11.24] "글상자 속성" 가진 개체 내에 문단 쓰기. 
  			if (hasParas) {
				WriterContext context2 = new WriterContext();
				context2.hwp 			= wContext.hwp;
				context2.mContext 		= wContext.mContext;
				context2.mDesktop 		= wContext.mDesktop;
				context2.mMCF			= wContext.mMCF;
				context2.mMSF			= wContext.mMSF;
				context2.mMyDocument 	= wContext.mMyDocument;
				context2.mText			= xFrameText;
				context2.mTextCursor	= xFrameCursor;
				
				for (HwpParagraph para: pol.paras) {
					HwpCallback callback = new HwpCallback(TableFrame.MADE);
					HwpRecurs.printParaRecurs(context2, para, callback, step+1);
				}
                // REMOVE last PARA_BREAK
                HwpRecurs.removeLastParaBreak(context2.mTextCursor);
  			}
            if (pol.nGrp==0) {
                ++autoNum;
            }
            
	  		// 캡션 쓰기. 글속성으로 처리하니 캡션을 없을듯 하나, 코드는 남겨놓음
  			if (hasCaption) {
		        xFrameText.insertTextContent(xFrameCursor, xTextContentShape, false);
		        xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
  				addCaptionString(wContext, xFrameText, xFrameCursor, pol, step);
  			}
  			
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
        } catch (SkipDrawingException e) {
            e.printStackTrace();
		}
    }
    
    private static void insertCURVE(WriterContext wContext, Ctrl_ShapeCurve cur, int step) {
		String shapeString = "com.sun.star.drawing.OpenBezierShape";
		if ((cur.fillType>0)) {
			shapeString = "com.sun.star.drawing.ClosedBezierShape";
		}

    	boolean hasCaption = cur.caption==null?false:cur.caption.size()==0?false:true;
    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;

    	try {
    		if (hasCaption) {
    			xFrame = makeOuterFrame(wContext, cur, false, step);
				// Frame 내부 Cursor 생성
				xFrameText = xFrame.getText();
				xFrameCursor = xFrameText.createTextCursor();
    		}

	        Object xObj = wContext.mMSF.createInstance(shapeString);
	        XTextContent xTextContentShape = (XTextContent)UnoRuntime.queryInterface(XTextContent.class, xObj);
	        XShape xShape = (XShape)UnoRuntime.queryInterface(XShape.class, xObj);
	        // 그릴 위치
	        Point aPos = new Point(0, 0);
	        Size aSize = new Size(Transform.translateHwp2Office(cur.curWidth), Transform.translateHwp2Office(cur.curHeight));
	        xShape.setPosition(aPos);
	        xShape.setSize(aSize);
    		XPropertySet xPropSet = (XPropertySet)UnoRuntime.queryInterface(XPropertySet.class, xShape);

    		if (hasCaption) {
    			try {
    				xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
		        xPropSet.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
		        xPropSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
		        xPropSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
		        xPropSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        // 투명하게 해야  도형이 보인다.
		        // xPropSet.setPropertyValue("BackTransparent", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        xPropSet.setPropertyValue("FillStyle", FillStyle.NONE);
		        xPropSet.setPropertyValue("FillTransparence", 100);
    		} else {
    			setPosition(xPropSet, cur, cur.nGrp>0?cur.xGrpOffset:0, cur.nGrp>0?cur.yGrpOffset:0);
    		}
    		setWrapStyle(xPropSet, cur);
    	    setLineStyle(xPropSet, cur);
    		
    		PolyPolygonBezierCoords aCoords = new PolyPolygonBezierCoords();
    		// 시작점*2 + 끝점*2 + 중간점*3.  [N C] [C N C] [C N C] [C N C] [C N]. 컨트롤 Point는 pair로 와야 하며, pair 전후에  Normal Point가 있어야 한다.
    		int nPointCount = 4+(cur.nPoints-2)*3;

    		aCoords.Coordinates = new Point[1][];
    		aCoords.Flags = new PolygonFlags[1][];
   			Point[] pPolyPoints = new Point[nPointCount];
   			PolygonFlags[] pPolyFlags = new PolygonFlags[nPointCount];
   			
   			// 아래 CONTROL Point 계산은  출처가 있는 것이 아니고, 직접 작성한 계산방법이다.
   			// 더 좋은 계산법이 있다면  아래의 코드를 교체해도 무방하다.

   			double nDiv = 6.8;   // 이 값을 7전후로 맞춰야 HWP와 유사한 곡선 비율이 나온다.
   			for (int i=0,n=0; i<cur.nPoints; i++, n+=3) {
   				if (i==0) {
					pPolyPoints[n] = new Point();
	    			pPolyPoints[n].X = Transform.translateHwp2Office(cur.points[i].x);
	    			pPolyPoints[n].Y = Transform.translateHwp2Office(cur.points[i].y);
    				pPolyFlags [n] = PolygonFlags.NORMAL;
					pPolyPoints[n+1] = new Point();
	    			pPolyPoints[n+1].X = Transform.translateHwp2Office(cur.points[i].x);
	    			pPolyPoints[n+1].Y = Transform.translateHwp2Office(cur.points[i].y);
    				pPolyFlags [n+1] = PolygonFlags.CONTROL;
   				} else if (i==cur.nPoints-1) {
					pPolyPoints[n-1] = new Point();
	    			pPolyPoints[n-1].X = Transform.translateHwp2Office(cur.points[i].x);
	    			pPolyPoints[n-1].Y = Transform.translateHwp2Office(cur.points[i].y);
    				pPolyFlags [n-1] = PolygonFlags.CONTROL;
					pPolyPoints[n] = new Point();
	    			pPolyPoints[n].X = Transform.translateHwp2Office(cur.points[i].x);
	    			pPolyPoints[n].Y = Transform.translateHwp2Office(cur.points[i].y);
    				pPolyFlags [n] = PolygonFlags.NORMAL;
   				} else {
   					// CONTROL Point before NORMAL Point
   		    		double atan1 = Math.atan2(cur.points[i].y-cur.points[i-1].y, cur.points[i-1].x-cur.points[i].x);
   		    		int angle1 = (int)(atan1*180/Math.PI);														// NORMAL Point에 들어온 각도
   		    		double atan2 = Math.atan2(cur.points[i].y-cur.points[i+1].y, cur.points[i+1].x-cur.points[i].x);
   		    		int angle2 = (int)(atan2*180/Math.PI);														// NORMAL Point에서 나가는 각도
   		    		double angle3 = angle1 - (angle1-angle2)/2;													// 들어온 각도와 나간 각도의 중간각
   		    		double distance1 = Math.sqrt(Math.pow(cur.points[i].y-cur.points[i-1].y, 2)+Math.pow(cur.points[i-1].x-cur.points[i].x, 2));
   					double distance2 = Math.sqrt(Math.pow(cur.points[i].y-cur.points[i+1].y, 2)+Math.pow(cur.points[i+1].x-cur.points[i].x, 2));
   					double distance = Math.max(distance1, distance2);	// 양쪽 CONTROL Point 벌어짐은 긴 쪽을 기준으로 같게 한다.
   					// nDiv = 5+2/(1+Math.exp(1-0.0001*distance));		// nDiv를 가변적으로 조정하는 시그모이드 함수
   		    		double signX = Math.signum(Math.cos(Math.toRadians(angle1>angle3?angle3+90:angle3-90)));	// 접선의 각도로 Point X좌표값을 더할지 뺄지 결정하는 부호 
   		    		double signY = -1* Math.signum(Math.sin(Math.toRadians(angle1>angle3?angle3+90:angle3-90)));// 접선의 각도로 Point Y좌표값을 더할지 뺄지 결정하는 부호
   		    		double deltaY = distance/nDiv * Math.abs(Math.cos(Math.toRadians(angle3))) * signY;				// 접점에서 이격된  Y축 거리 
   		    		double deltaX = distance/nDiv * Math.abs(Math.sin(Math.toRadians(angle3))) * signX;				// 접점에서 이격된  X축 거리 
   		    		int controlX = cur.points[i].x + (int)deltaX;												// CONTROL Point의 x좌표
   		    		int controlY = cur.points[i].y + (int)deltaY;												// CONTROL Point의 y좌표
					pPolyPoints[n-1] = new Point();
	    			pPolyPoints[n-1].X = Transform.translateHwp2Office(controlX);
	    			pPolyPoints[n-1].Y = Transform.translateHwp2Office(controlY);
    				pPolyFlags [n-1] = PolygonFlags.CONTROL;
    				// NORMAL Point
					pPolyPoints[n] = new Point();
	    			pPolyPoints[n].X = Transform.translateHwp2Office(cur.points[i].x);
	    			pPolyPoints[n].Y = Transform.translateHwp2Office(cur.points[i].y);
    				pPolyFlags [n] = PolygonFlags.NORMAL;
					// CONTROL Point after NORMAL Point
   		    		signX = Math.signum(Math.cos(Math.toRadians(angle2>angle3?angle3+90:angle3-90)));
   		    		signY = -1* Math.signum(Math.sin(Math.toRadians(angle2>angle3?angle3+90:angle3-90)));
   		    		deltaY = distance/nDiv * Math.abs(Math.cos(Math.toRadians(angle3))) * signY;
   		    		deltaX = distance/nDiv * Math.abs(Math.sin(Math.toRadians(angle3))) * signX;
   		    		controlX = cur.points[i].x + (int)deltaX;
   		    		controlY = cur.points[i].y + (int)deltaY;
					pPolyPoints[n+1] = new Point();
	    			pPolyPoints[n+1].X = Transform.translateHwp2Office(controlX);
	    			pPolyPoints[n+1].Y = Transform.translateHwp2Office(controlY);
    				pPolyFlags [n+1] = PolygonFlags.CONTROL;
   				}
    		}
   			aCoords.Coordinates[0] = pPolyPoints;
   			aCoords.Flags[0] = pPolyFlags;
    		xPropSet.setPropertyValue("PolyPolygonBezier", aCoords);

    		setFillStyle(xPropSet, cur.fill);
            if (cur.nGrp==0) {
                ++autoNum;
            }
	        if (hasCaption) {
	        	XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);
		        // 투명하게 해야  도형이 보인다.
	    		frameProps.setPropertyValue("FillTransparence", 100);

		        xFrameText.insertTextContent(xFrameCursor, xTextContentShape, false);
		        xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
	        } else {
	            wContext.mText.insertTextContent(wContext.mTextCursor, xTextContentShape, false);
                // workaround-LibreOffice7.2 START
	            if (step==2 && wContext.version >= 72) {
	                TextContentAnchorType anchorType = (TextContentAnchorType)xPropSet.getPropertyValue("AnchorType");
	                if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
	                    wContext.mText.insertString(wContext.mTextCursor, " ", false);
	                }
	            }
                // workaround-LibreOffice7.2 END
	        }

	  		// 캡션 쓰기
  			if (hasCaption) {
  				addCaptionString(wContext, xFrameText, xFrameCursor, cur, step);
  			}
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
		} catch (SkipDrawingException e) {
		    e.printStackTrace();
		}
    }
    
    private static void insertARC(WriterContext wOuterContext, Ctrl_ShapeArc arc, int step, int shapeWidth, int shapeHeight) {
    	
    	boolean hasCaption = arc.caption==null?false:arc.caption.size()==0?false:true;
    	XTextFrame xInternalFrame = null;
    	XText xInternalFrameText = null;
    	XTextCursor xInternalFrameCursor = null;

    	try {
    		if (hasCaption) {
    			xInternalFrame = makeOuterFrame(wOuterContext, arc, false, step);
				// Frame 내부 Cursor 생성
				xInternalFrameText = xInternalFrame.getText();
				xInternalFrameCursor = xInternalFrameText.createTextCursor();
    		}

	        Object xObj = wOuterContext.mMSF.createInstance("com.sun.star.drawing.EllipseShape");
	        XShape xShape = (XShape)UnoRuntime.queryInterface(XShape.class, xObj);
	        XTextContent xTextContentShape = (XTextContent)UnoRuntime.queryInterface(XTextContent.class, xObj);
    		XPropertySet xPropSet = (XPropertySet)UnoRuntime.queryInterface(XPropertySet.class, xShape);
    		
    		int sizeWidth = shapeWidth<=0 ? arc.curWidth : shapeWidth;
    		int sizeHeight = shapeHeight<=0 ? arc.curHeight : shapeHeight;
    		
    		double atan1 = Math.atan2(arc.centerY-arc.axixY1, arc.axixX1-arc.centerX);
    		int angle1 = (int)(atan1*180/Math.PI);
    		angle1 = angle1>=0?angle1:360+angle1;
    		double atan2 = Math.atan2(arc.centerY-arc.axixY2, arc.axixX2-arc.centerX);
    		int angle2 = (int)(atan2*180/Math.PI);
    		angle2 = angle2>=0?angle2:360+angle2;

    		// 그리는 좌표(HoriOrientPosition,VertOrientPosition) 변경을 위한 부분 - 시작
    		int xOffset = 0;
    		int yOffset = 0;
    		double radius = Math.sqrt(Math.pow((arc.centerY-arc.axixY1)*arc.matrixSeq[4], 2)+Math.pow((arc.axixX1-arc.centerX)*arc.matrixSeq[0], 2));
    		int startQuadrant = angle1>=270?4:angle1>=180?3:angle1>=90?2:1;
    		int endQuadrant = angle2>=270?4:angle2>=180?3:angle2>=90?2:1;
    		if (startQuadrant > endQuadrant) {
    			endQuadrant += 4;	// 시각각도가 끝각도보다 크다면, 사분면 차지여부를 알기 위해서 한바뀌 돌려야 함.
    		}
    		List<Integer> xyPlot = IntStream.range(startQuadrant, endQuadrant).mapToObj(i -> Integer.valueOf(i)).collect(Collectors.toList());
    		if (xyPlot.contains(2)) {	  						// 2사분면 위치함. x.y 조정 필요 없음
    		} else {
    			if (xyPlot.contains(1)) {						// 1사분면 위치함. x 위치 조정
    				xOffset = Transform.translateHwp2Office((int)-radius);
    			}
    			if (xyPlot.contains(3)) {						// 3사분면 위치함. y 위치 조정
    				yOffset = Transform.translateHwp2Office((int)-radius);
    			}
    			if (xyPlot.size()==1 && xyPlot.contains(4)) {	// 4사분면 위치함. x,y 모두 위치 조정
    				xOffset = Transform.translateHwp2Office((int)-radius);
    				yOffset = Transform.translateHwp2Office((int)-radius);
    			}
    		}
    		// 그리는 좌표(HoriOrientPosition,VertOrientPosition) 변경을 위한 부분 - 끝
    		
	        // 그릴 위치
	        Point aPos = new Point(0, 0);
	        // ARC의 경우, HWP의 ARC보다 작은 크기로 나타난다. 사이즈를 재계산할 필요가 있다.
	        // 삼각함수를 이용하여 가로,세로 길이를 구하고,  전체 원지름 대비 비율로 사이즈를 재계산할 수 있겠다. 
	        // 그런 후에 xShape.setSize(aSize) 를 호출하면 될것으로 보인다.  당장은 2배로 부풀려서 그리도록 한다.
	        Size aSize = new Size(Transform.translateHwp2Office(sizeWidth)*2, Transform.translateHwp2Office(sizeHeight)*2);
	        xShape.setPosition(aPos);
	        xShape.setSize(aSize);

    		if (hasCaption) {
    			try {
    				xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
		        xPropSet.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
		        xPropSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
		        xPropSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
		        xPropSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        // 투명하게 해야  도형이 보인다.
		        // xPropSet.setPropertyValue("BackTransparent", RelOrientation.PRINT_AREA);   // 1:paragraph text area
		        xPropSet.setPropertyValue("FillStyle", FillStyle.NONE);
		        xPropSet.setPropertyValue("FillTransparence", 100);
    		} else {
    			setPosition(xPropSet, arc, xOffset, yOffset);
    			// 호는 추가적으로 그리는 위치를 shift 해야 한다.
                xPropSet.setPropertyValue("VertOrientPosition", Transform.translateHwp2Office(((int)(arc.yGrpOffset*arc.matrixSeq[4]))) + yOffset);
                xPropSet.setPropertyValue("HoriOrientPosition", Transform.translateHwp2Office(((int)(arc.xGrpOffset*arc.matrixSeq[0]))) + xOffset);
    		}
    		
    		setWrapStyle(xPropSet, arc);
    		setLineStyle(xPropSet, arc);
    		setFillStyle(xPropSet, arc.fill);
    		xPropSet.setPropertyValue("CircleStartAngle", angle1*100 );
    		xPropSet.setPropertyValue("CircleEndAngle", angle2*100 );

    		switch(arc.type) {
    		case NORMAL:
        		xPropSet.setPropertyValue("CircleKind", CircleKind.ARC);
        		break;
    		case PIE:
        		xPropSet.setPropertyValue("CircleKind", CircleKind.SECTION);
        		break;
    		case CHORD:
        		xPropSet.setPropertyValue("CircleKind", CircleKind.CUT);
        		break;
    		}
    		
	        if (hasCaption) {
	        	XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xInternalFrame);
		        // 투명하게 해야  도형이 보인다.
	    		frameProps.setPropertyValue("FillTransparence", 100);

		        xInternalFrameText.insertTextContent(xInternalFrameCursor, xTextContentShape, false);
		        xInternalFrameText.insertControlCharacter(xInternalFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
	        } else {
		        wOuterContext.mText.insertTextContent(wOuterContext.mTextCursor, xTextContentShape, false);
                if (step==2 && wOuterContext.version >= 72) {
                    TextContentAnchorType anchorType = (TextContentAnchorType)xPropSet.getPropertyValue("AnchorType");
                    if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                        wOuterContext.mText.insertString(wOuterContext.mTextCursor, " ", false);
                    }
                }
	        }
	        if (arc.nGrp==0) {
	            ++autoNum;
	        }
	        
	  		// 캡션 쓰기
  			if (hasCaption) {
  				addCaptionString(wOuterContext, xInternalFrameText, xInternalFrameCursor, arc, step);
  			}
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
		} catch (SkipDrawingException e) {
		    e.printStackTrace();
		}
    }
    
    static XTextFrame makeOuterFrame(WriterContext wContext, Ctrl_GeneralShape shape, boolean fixedSize, int step) throws SkipDrawingException, Exception {
    	XTextFrame xFrame = null;
    	
    	Object oFrame = wContext.mMSF.createInstance("com.sun.star.text.TextFrame");
        xFrame = (XTextFrame) UnoRuntime.queryInterface(XTextFrame.class, oFrame);
		if (xFrame == null) {
			log.severe("Could not create a text frame");
			return xFrame;
		}
		
		XShape tfShape = UnoRuntime.queryInterface(XShape.class, xFrame);
		tfShape.setSize(new Size(Transform.translateHwp2Office(shape.width), Transform.translateHwp2Office(shape.height)));
		XPropertySet frameProps = UnoRuntime.queryInterface(XPropertySet.class, xFrame);

		setPosition(frameProps, shape, 0, 0);
		setWrapStyle(frameProps, shape);

		BorderLine2 frameBorder = new BorderLine2();
		frameBorder.Color= 0x000000;
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
		// TextDirection.
		if (shape.maxTxtWidth!=shape.curWidth && shape.maxTxtWidth==shape.curHeight) {
			frameProps.setPropertyValue("WritingMode", WritingMode2.TB_RL);
		}

		XText xText = wContext.mTextCursor.getText();
		xText.insertTextContent(wContext.mTextCursor, xFrame, false);
        if (step==2 && wContext.version >= 72) {
            TextContentAnchorType anchorType = (TextContentAnchorType)frameProps.getPropertyValue("AnchorType");
            if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
                xText.insertString(wContext.mTextCursor, " ", false);
            }
        }

        // Transparency 100%로 설정한다.
        frameProps.setPropertyValue("FillStyle", FillStyle.NONE);
        frameProps.setPropertyValue("FillTransparence", 100);

		// TextFrame을 그린 후에 automaticHeight를 조정해야..
		if (fixedSize) {
			frameProps.setPropertyValue("FrameIsAutomaticHeight", false);
			frameProps.setPropertyValue("WidthType", SizeType.FIX);
			frameProps.setPropertyValue("TextVerticalAdjust", TextVerticalAdjust.BLOCK);
		} else {
			frameProps.setPropertyValue("FrameIsAutomaticHeight", true);
			frameProps.setPropertyValue("WidthType", SizeType.FIX);
			frameProps.setPropertyValue("TextVerticalAdjust", TextVerticalAdjust.CENTER);
		}
		wContext.mTextCursor.gotoEnd(false);
		
		return xFrame;
    }
    
    static void addCaptionString(WriterContext wContext, XText xFrameText, XTextCursor xFrameCursor, Ctrl_GeneralShape shape, int step) {
		XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, xFrameCursor);
		XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
    	
		List<String> capStr = new ArrayList<String>();
		short[] charShapeID = new short[1];
		Optional<Ctrl> ctrlOp = shape.caption.stream().filter(c -> c.p!=null).flatMap(c -> c.p.stream()).findFirst();
		if (ctrlOp.isPresent()) {
		    charShapeID[0] = (short) ((ParaText)ctrlOp.get()).charShapeId;
		}
		HwpCallback callback = new HwpCallback() {
			@Override
			public void onAutoNumber(int paraStyleID, int paraShapeID) {
				capStr.add(Integer.toString(autoNum));
			};
			@Override
			public boolean onTab(String info) {
				capStr.add("\t");
				return true;
			};
			@Override	
			public boolean onText(String content, int charShapeId, int charPos, boolean append) {
				capStr.add(content);
				charShapeID[0] = (short)charShapeId;
				return true; 
			}
			@Override
			public boolean onParaBreak() {
				capStr.add("\r");
				return true;
			}
		};
		HwpRecurs.printParaRecurs(wContext, shape.caption.get(0), callback, 2);
		if (capStr.size()>0 && capStr.get(capStr.size()-1).equals("\r")) {	// 마지막이 PARA_BREAK라면  출력하지 않음.
			capStr.remove(capStr.size()-1);
		}
		
		HwpRecord_ParaShape captionParaShape = wContext.getParaShape(shape.caption.get(0).paraShapeID);
		String styleName = ConvPara.getStyleName((int)shape.caption.get(0).paraStyleID);
		// short charShapeID = ConvUtil.selectCharShapeID(shape.caption.get(0).charShapes, 0);
		HwpRecord_CharShape captionCharShape = wContext.getCharShape(charShapeID[0]);

		try {
			paraProps.setPropertyValue("ParaStyleName", styleName);
			ConvPara.setParagraphProperties(paraProps, captionParaShape, wContext.hwp.docInfo.compatibleDoc, ConvPara.PARA_SPACING);
			ConvPara.setCharacterProperties(paraProps, captionCharShape, step);
			paraProps.setPropertyValue("ParaTopMargin", Transform.translateHwp2Office(shape.captionSpacing));
			for (String cap: capStr) {
				xFrameText.insertString(xFrameCursor, cap, false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    private static void setPosition(XPropertySet xProps, Ctrl_GeneralShape shape, int xGrpOffset, int yGrpOffset) throws SkipDrawingException {
    	int posX = 0;
    	int posY = 0;
    	int xOffsetToAdd = Transform.translateHwp2Office(xGrpOffset);
    	int yOffsetToAdd = Transform.translateHwp2Office(yGrpOffset);
    	Page page = ConvPage.getCurrentPage().page;
    	
		try {
			if (shape.treatAsChar==true) {
				try {
					xProps.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
				xProps.setPropertyValue("VertOrient", VertOrientation.CHAR_CENTER);				// Top, Bottom, Center, fromBottom
				xProps.setPropertyValue("VertOrientRelation", RelOrientation.PRINT_AREA);   	// Base line, Character, Row
				
    			xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
			} else {
	  			if (shape.vertRelTo==null) {
					xProps.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
					xProps.setPropertyValue("VertOrientRelation", RelOrientation.PRINT_AREA);
					xProps.setPropertyValue("VertOrient", VertOrientation.NONE);
					xProps.setPropertyValue("VertOrientPosition", yOffsetToAdd);
	  			} else {
					switch(shape.vertRelTo) {
					case PAPER:		// Anchor to Page
						// 그림에서 AnchorType을 AT_PAGE로 줄때  crash 발생
						if (!(shape instanceof Ctrl_ShapePic)) {
							try {
								xProps.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
							} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
								log.severe("AnchorType has Exception");
							}
						}
			        	switch(shape.vertAlign) {
			        	case TOP:
			        		xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_FRAME);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.vertOffset==0) {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.TOP);		// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.NONE);	// 0:NONE=From top
			        			// page상단으로부터  frame상단까지의 offset
			        			posY = Transform.translateHwp2Office(shape.vertOffset);	 
			        			if (posY<0 ||
			        				posY+Transform.translateHwp2Office(shape.height)>Transform.translateHwp2Office(page.height)) {
			        			    throw new SkipDrawingException();
			        			}
			        			xProps.setPropertyValue("VertOrientPosition", posY + yOffsetToAdd);
			        		}
			        		break;
			        	case CENTER:
			        		xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_FRAME);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.vertOffset==0) {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.CENTER);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.NONE);	// 0:NONE=From top
			        			// 중간지점에서  frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
			        			posY = (Transform.translateHwp2Office(page.height)-Transform.translateHwp2Office(shape.height))/2 + Transform.translateHwp2Office(shape.vertOffset);
			        			if (posY<0 ||
			        				posY+Transform.translateHwp2Office(shape.height)>Transform.translateHwp2Office(page.height)) {
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("VertOrientPosition", posY + yOffsetToAdd);
			        		}
			        		break;
			        	case BOTTOM:
			        		xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_FRAME);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.vertOffset==0) {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.BOTTOM);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.NONE);	// 0:NONE=From top	
			        			// page하단에서 frame 하단까지의 offset을  -> page상단부터의 frame상단까지 offset으로 계산
			        			posY = Transform.translateHwp2Office(page.height)-Transform.translateHwp2Office(shape.height)-Transform.translateHwp2Office(shape.vertOffset);
			        			if (posY<0 ||
			        				posY+Transform.translateHwp2Office(shape.height)>Transform.translateHwp2Office(page.height)) {
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("VertOrientPosition", posY + yOffsetToAdd);
			        		}
			        		break;
			        	}
						break;
					case PAGE:
						// 그림에서 AnchorType을 AT_PAGE로 줄때  crash 발생
						if (!(shape instanceof Ctrl_ShapePic)) {
							try {
								xProps.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
							} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
								log.severe("AnchorType has Exception");
							}
						}
			        	switch(shape.vertAlign) {
			        	case TOP:
			        		xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.vertOffset==0) {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.TOP);		// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.NONE);	// 0:NONE=From top
			        			// page상단으로부터  frame상단까지의 offset
			        			posY = Transform.translateHwp2Office(shape.vertOffset);
			        			if (posY+Transform.translateHwp2Office(page.marginTop)<0 ||
			        				posY+Transform.translateHwp2Office(shape.height)>Transform.translateHwp2Office(page.height)) {
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("VertOrientPosition", posY + yOffsetToAdd);
			        		}
			        		break;
			        	case CENTER:
			        		xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.vertOffset==0) {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.CENTER);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.NONE);	// 0:NONE=From top	
			        			// 중간지점에서  frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
			        			int pageHeight = Transform.translateHwp2Office(page.height)-Transform.translateHwp2Office(page.marginTop)-Transform.translateHwp2Office(page.marginBottom);
			        			posY = (pageHeight-Transform.translateHwp2Office(shape.height))/2;
			        			posY += Transform.translateHwp2Office(shape.vertOffset);
			        			if (posY<0 ||
			        				posY+Transform.translateHwp2Office(shape.height)>Transform.translateHwp2Office(page.height)) {
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("VertOrientPosition", posY + yOffsetToAdd);
			        		}
			        		break;
			        	case BOTTOM:
			        		xProps.setPropertyValue("VertOrientRelation", RelOrientation.PAGE_PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.vertOffset==0) {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.BOTTOM);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.NONE);	// 0:NONE=From top	
			        			// 쪽 하단에서 frame 하단까지의 offset을  -> 쪽 상단부터의 frame상단까지 offset으로 계산
			        			int pageHeight = Transform.translateHwp2Office(page.height)-Transform.translateHwp2Office(page.marginTop)-Transform.translateHwp2Office(page.marginBottom);
			        			posY = pageHeight-Transform.translateHwp2Office(shape.height)-Transform.translateHwp2Office(shape.vertOffset);
			        			if (posY<0 ||
			        				posY+Transform.translateHwp2Office(shape.height)>Transform.translateHwp2Office(page.height)) {
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("VertOrientPosition", posY + yOffsetToAdd);
			        		}
			        		break;
			        	}
						break;
					case PARA:
						try {
							xProps.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
						} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
							log.severe("AnchorType has Exception");
						}
			        	switch(shape.vertAlign) {
			        	case TOP:
			        		xProps.setPropertyValue("VertOrientRelation", RelOrientation.PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.vertOffset==0) {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.TOP);		// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("VertOrient", VertOrientation.NONE);	// 0:NONE=From top	
			        			// para상단으로부터  frame상단까지의 offset
			        			posY = Transform.translateHwp2Office(shape.vertOffset);
			        			if (/*posY+Transform.translateHwp2Office(page.marginTop)<0 ||*/
			        				posY+Transform.translateHwp2Office(shape.height)>Transform.translateHwp2Office(page.height)) {
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("VertOrientPosition", posY + yOffsetToAdd);
			        		}
			        		break;
			        	}
						break;
						
					}
	  			}
				
	  			if (shape.horzRelTo==null) {
	  				xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);
	  				xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);
	  				xProps.setPropertyValue("HoriOrientPosition", xOffsetToAdd);
	  			} else {
					switch(shape.horzRelTo) {
					case PAPER:
			        	switch(shape.horzAlign) {
			        	case LEFT:	// LEFT
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_FRAME);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From top	
			        			// page상단으로부터  frame상단까지의 offset
			        			posX = Transform.translateHwp2Office(shape.horzOffset);
			        			int leftMargin = Transform.translateHwp2Office(page.marginLeft);
			        			int shapeWidth = Transform.translateHwp2Office(shape.width);
			        			int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width);
			        			// 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	case CENTER:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_FRAME);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From top	
			        			// 중간지점에서  frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
			        			posX = (Transform.translateHwp2Office(page.width)-Transform.translateHwp2Office(shape.width))/2 + Transform.translateHwp2Office(shape.horzOffset);
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	case RIGHT:	// RIGHT
			        	case OUTSIDE:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_FRAME);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.RIGHT);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From top	
			        			// page하단에서 frame 하단까지의 offset을  -> page상단부터의 frame상단까지 offset으로 계산
			        			posX = Transform.translateHwp2Office(page.width)-Transform.translateHwp2Office(shape.width)-Transform.translateHwp2Office(shape.horzOffset);
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	}
						break;
					case PAGE:
			        	switch(shape.horzAlign) {
			        	case LEFT:	// LEFT
			        	case INSIDE:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From left	
			        			// page상단으로부터  frame상단까지의 offset
			        			posX = Transform.translateHwp2Office(shape.horzOffset);
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	case CENTER:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From left	
			        			// 중간지점에서  frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
			        			int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width)-Transform.translateHwp2Office(page.marginLeft)-Transform.translateHwp2Office(page.marginRight);
			        			posX = (pageWidth-Transform.translateHwp2Office(shape.width))/2;
			        			posX += Transform.translateHwp2Office(shape.horzOffset);
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	case RIGHT:	// RIGHT
			        	case OUTSIDE:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PAGE_PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.RIGHT);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From left
			        			// page하단에서 frame 하단까지의 offset을  -> page상단부터의 frame상단까지 offset으로 계산
			        			int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width)-Transform.translateHwp2Office(page.marginLeft)-Transform.translateHwp2Office(page.marginRight);
			        			posX = pageWidth-Transform.translateHwp2Office(shape.width)-Transform.translateHwp2Office(shape.horzOffset);
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	}
						break;
					case COLUMN:
					case PARA:
			        	switch(shape.horzAlign) {
			        	case LEFT:	// LEFT
			        	case INSIDE:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.LEFT);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From left	
			        			// page상단으로부터  frame상단까지의 offset
			        			posX = Transform.translateHwp2Office(shape.horzOffset);	 
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd+leftMargin<0 && (posX+xOffsetToAdd+leftMargin+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	case CENTER:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From left	
			        			// 중간지점에서  frame 중심까지의 offset -> page상단부터의 frame상단까지 offset으로 계산
			        			int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width)-Transform.translateHwp2Office(page.marginLeft)-Transform.translateHwp2Office(page.marginRight);
			        			posX = (pageWidth-Transform.translateHwp2Office(shape.width))/2;
			        			posX += Transform.translateHwp2Office(shape.horzOffset);
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	case RIGHT:	// RIGHT
			        	case OUTSIDE:
			        		xProps.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 7:EntirePage, 8:PageTextArea
			        		if (shape.horzOffset==0) {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.RIGHT);	// 1:Top, 2:Bottom, 2:Center, 0:NONE(From top)	
			        		} else {
			        			xProps.setPropertyValue("HoriOrient", HoriOrientation.NONE);	// 0:NONE=From left	
			        			// page하단에서 frame 하단까지의 offset을  -> page상단부터의 frame상단까지 offset으로 계산
			        			int pageWidth = Transform.translateHwp2Office(page.landscape?page.height:page.width)-Transform.translateHwp2Office(page.marginLeft)-Transform.translateHwp2Office(page.marginRight);
			        			posX = pageWidth-Transform.translateHwp2Office(shape.width)-Transform.translateHwp2Office(shape.horzOffset);
                                int leftMargin = Transform.translateHwp2Office(page.marginLeft);
                                int shapeWidth = Transform.translateHwp2Office(shape.width);
                                // 보이는 부분이 50% 넘으면 보이도록 수정
                                if ((posX+xOffsetToAdd<0 && (posX+xOffsetToAdd+shapeWidth/2)<0) || 
                                    (posX+xOffsetToAdd+shapeWidth>pageWidth && (posX+xOffsetToAdd+shapeWidth/2)>pageWidth)) {
                                    log.fine("posX=" + posX + ", shapeWidth=" + shapeWidth + ", pageLeftMargin=" + leftMargin + ", pageWidth=" + pageWidth);
                                    throw new SkipDrawingException();
                                }
			        			xProps.setPropertyValue("HoriOrientPosition", posX + xOffsetToAdd);
			        		}
			        		break;
			        	}
						break;
					}
	  			}
			}
		} catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException | WrappedTargetException e) {
			e.printStackTrace();
		}
    }
    
    private static void setWrapStyle(XPropertySet xPropSet, Ctrl_GeneralShape shape) {
		
		// wrapStyle;		// 0:어울림, 1:자리차지, 2:글 뒤로, 3:글 앞으로
		// wrapText;		// 0:양쪽, 1:왼쪽, 2:오른쪽, 3:큰쪽
		try {
		    if (shape.nGrp>0) {
                xPropSet.setPropertyValue("Opaque", true);
                xPropSet.setPropertyValue("AllowOverlap", true);    // THROUGH에서는 효과 없음.
                try {
                    xPropSet.setPropertyValue("SurroundContour", false);// contour는  THROUGH에서는 효과 없음
                } catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
                    log.severe("SurroundContour has exception");
                }
                try {
                    xPropSet.setPropertyValue("ContourOutside", false); // Frames 에서는 불가
                } catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
                    log.severe("ContourOutside has exception");
                }
                try {
                    xPropSet.setPropertyValue("IsAutomaticContour", false);
                } catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
                    log.severe("IsAutomaticContour has exception");
                }
                xPropSet.setPropertyValue("TextWrap", WrapTextMode.NONE);
		    } else {
    			switch(shape.wrapStyle) {
    			case 0x0:			// 어울림
    				xPropSet.setPropertyValue("Opaque", true);
    				xPropSet.setPropertyValue("AllowOverlap", true);	// THROUGH에서는 효과 없음.
    				
    				WrapTextMode wrapText = WrapTextMode.NONE;
    				boolean isAutomaticContour = false;
    				switch(shape.wrapText) {
    				case 0x0:	// 양쪽
    					wrapText = WrapTextMode.PARALLEL;
    					break;
    				case 0x1:	// 왼쪽
    					wrapText = WrapTextMode.LEFT;
    					break;
    				case 0x2:	// 오른쪽
    					wrapText = WrapTextMode.RIGHT;
    					break;
    				case 0x3:	// 큰쪽
    					wrapText = WrapTextMode.DYNAMIC;
    					isAutomaticContour = true;
    					break;
    				}
    				if (shape.treatAsChar==false) {
    					try {
    						xPropSet.setPropertyValue("SurroundContour", false);// contour는  THROUGH에서는 효과 없음
    					} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    						log.severe("SurroundContour has exception");
    					}
    					try {	
    						xPropSet.setPropertyValue("ContourOutside", false);	// Frames 에서는 불가
    					} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    						log.severe("ContourOutside has exception");
    					}
    					try {
    						xPropSet.setPropertyValue("IsAutomaticContour", isAutomaticContour);
    					} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    						log.severe("IsAutomaticContour has exception");
    					}
    				}
    				xPropSet.setPropertyValue("TextWrap", wrapText);
    				break;
    			case 0x1:		// 자리차지
    				xPropSet.setPropertyValue("Opaque", true);
    				if (shape.treatAsChar==false) {
    					xPropSet.setPropertyValue("AllowOverlap", true);	// THROUGH에서는 효과 없음.
    					try {
    						xPropSet.setPropertyValue("SurroundContour", false);// contour는  THROUGH에서는 효과 없음
    					} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    						log.severe("SurroundContour has exception");
    					}
    					try {
    						xPropSet.setPropertyValue("ContourOutside", false);	// Frames 에서는 불가
    					} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    						log.severe("ContourOutside has exception");
    					}
    					try {
    						xPropSet.setPropertyValue("IsAutomaticContour", false);
    					} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    						log.severe("IsAutomaticContour has exception");
    					}
    				}
    				xPropSet.setPropertyValue("TextWrap", WrapTextMode.NONE);
    				break;
    			case 0x2:		// 글 뒤로
    				xPropSet.setPropertyValue("Opaque", false);
    				if (shape.treatAsChar==false) {
    					xPropSet.setPropertyValue("AllowOverlap", true);	// THROUGH에서는 효과 없음.
    					if (!(shape instanceof Ctrl_ShapeRect) && !(shape instanceof Ctrl_Container)) {
    						try {
    							xPropSet.setPropertyValue("SurroundContour", false);// contour는  THROUGH에서는 효과 없음
    						} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    							log.severe("SurroundContour has exception");
    						}
    						try {
    							xPropSet.setPropertyValue("ContourOutside", false);	// Frames 에서는 불가
    						} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    							log.severe("ContourOutside has exception");
    						}
    						try {
    							xPropSet.setPropertyValue("IsAutomaticContour", false);
    						} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    							log.severe("IsAutomaticContour has exception");
    						}
    					}
    				}
    				xPropSet.setPropertyValue("TextWrap", WrapTextMode.THROUGH);
    				break;
    			case 0x3:		// 글 앞으로
    				xPropSet.setPropertyValue("Opaque", true);
    				if (shape.treatAsChar==false) {
    					xPropSet.setPropertyValue("AllowOverlap", true);	// THROUGH에서는 효과 없음.
    					if (!(shape instanceof Ctrl_ShapeRect) && !(shape instanceof Ctrl_Container)) {
    						try {
    							xPropSet.setPropertyValue("SurroundContour", false);// contour는  THROUGH에서는 효과 없음
    						} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    							log.severe("SurroundContour has exception");
    						}
    						try {
    							xPropSet.setPropertyValue("ContourOutside", false);	// Frames 에서는 불가
    						} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    							log.severe("ContourOutside has exception");
    						}
    						try {
    							xPropSet.setPropertyValue("IsAutomaticContour", false);
    						} catch (UnknownPropertyException|PropertyVetoException|IllegalArgumentException|WrappedTargetException e) {
    							log.severe("IsAutomaticContour has exception");
    						}
    					}
    				}
    				xPropSet.setPropertyValue("TextWrap", WrapTextMode.THROUGH);
    				break;
    			}
    			// 한컴에 있는 ZOrder(317,318)를 set하고 나서 odf를 열었을때 ZOrder(1,0)가 반전되는 현상
    			// 이 현상 때문에  그림위에 있는 TextFrame이 보이지 않는다. ZOrder 없이 화면에 뿌리는 순서대로 유지하도록 한다.
    			// xPropSet.setPropertyValue("ZOrder", shape.zOrder);
		    }
			
		} catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException | WrappedTargetException e) {
			e.printStackTrace();
		}
    }
    
    private static void setLineStyle(XPropertySet xPropSet, Ctrl_GeneralShape shape) {
    	if (shape.lineType==null) return;
    	
        try {
			switch(shape.lineType) {
			case NONE:
				xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.NONE);
	    		break;
			case SOLID:
	    		xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.SOLID);
	    		break;
			case DASH:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Long Dash");
			    break;
			case DOT:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dot");
                break;
			case DASH_DOT:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dash Dot");
                break;
			case DASH_DOT_DOT:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dash Dot Dot");
                break;
			case LONG_DASH:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Long Dash");
                break;
			case CIRCLE:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dot");
                break;
			case DOUBLE_SLIM:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dot");
                break;
			case SLIM_THICK:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dot");
                break;
			case THICK_SLIM:
                xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dot");
                break;
			case SLIM_THICK_SLIM:
	    		xPropSet.setPropertyValue("LineStyle", com.sun.star.drawing.LineStyle.DASH);
                xPropSet.setPropertyValue("LineDashName", "Dot");
                break;
			}
			// xPropSet.setPropertyValue("LineDash", lineDash);
			xPropSet.setPropertyValue("LineColor", shape.lineColor);

			int convertedLineWidth = Transform.translateHwp2Office(shape.lineThick);
            log.finest("Line width="+convertedLineWidth+ " from "+shape.lineThick+" in HWP.");
            // TextFrame
            // Line     566(2mm)
            // Curve    33(0.12mm)
            // Polygon
            xPropSet.setPropertyValue("LineWidth", convertedLineWidth);
			
			if ((shape.outline&0x1)==0x1) {
				xPropSet.setPropertyValue("LineEndType", LineEndType.NONE);
				xPropSet.setPropertyValue("LineColor", shape.lineColor);
			}
			
		} catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException | WrappedTargetException e) {
			e.printStackTrace();
		}
    }
    
	private static void setArrowStyle(XPropertySet xPropSet, int arrowStyle, int arrowWidth, boolean fill, boolean start) {
		
		String arrowStyleName = null;
		// UI에서 보이는 StyleName을 그대로 쓰면 안된다. IllegalArgumentException 발생. 검증된 영문기준 ArrowStyleName만 쓰도록 한다.
		switch(arrowStyle) {
		case 0:		// 모양없음
			arrowStyleName = null;
			break;
		case 1:		// 화살모양
			if (WriterContext.version>=70) {
				arrowStyleName = arrowWidth<3?"Arrow short":arrowWidth<6?"Arrow":"Arrow large";
			} else {
				arrowStyleName = "Arrow";
			}
			break;
		case 2:		// 라인모양
			if (WriterContext.version>=70) {
				arrowStyleName = arrowWidth<3?"Line short":"Line";
			} else {
				arrowStyleName = "Arrow";
			}
			break;
		case 3: 	// 오목한 화살모양
			if (WriterContext.version>=70) {
				arrowStyleName = arrowWidth<3?"Concave short":"Concave";
			} else {
				arrowStyleName = "Arrow concave";
			}
			break;
		case 4:		// 속이 찬 다이아몬드 모양
			arrowStyleName = fill?"Diamond":"Diamond unfilled";
			break;
		case 5:		// 속이 찬 원 모양
			arrowStyleName = fill?"Circle":"Circle unfilled";
			break;
		case 6:		// 속이 찬 사각모양
			arrowStyleName = fill?"Square":"Square unfilled";
			break;
		case 7:		// 속이 빈 다이아몬드 모양
			arrowStyleName = fill?"Diamond":"Diamond unfilled";
			break;
		case 8:		// 속이 빈 원 모양
			arrowStyleName = fill?"Circle":"Circle unfilled";
			break;
		case 9:		// 속이 빈 사각모양
			arrowStyleName = fill?"Square":"Square unfilled";
			break;
		}
		
		long arrowWidthNum = 0;
		switch(arrowWidth) {
		case 0:		// 작은-작은
		case 3:		// 중간-작은
		case 6:		// 큰-작은
			arrowWidthNum = 203;
			break;
		case 1:		// 작은-중간
		case 4:		// 중간-중간
		case 7:		// 큰-중간
			arrowWidthNum = 353;
			break;
		case 2:		// 작은-큰
		case 5:		// 중간-큰
		case 8:		// 큰-큰
			arrowWidthNum = 499;
		}
		
		try {
			/*
			// 크기를 설정하면 화살표가 나타나지 않는다. 어떤값을 주던 크기가 0으로 설정된다. 막아 놓는다.
			if (arrowWidthNum>0) {
				// xPropSet.setPropertyValue(start?"LineStartWidth":"LineEndWidth", arrowWidthNum);
			}
			*/
			if (arrowStyleName!=null && !arrowStyleName.isEmpty()) {
				xPropSet.setPropertyValue(start?"LineStartName":"LineEndName", arrowStyleName);
			}
			xPropSet.setPropertyValue(start?"LineStartCenter":"LineEndCenter", false);
		} catch (Exception e) {
			log.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
    private static void setFillStyle(XPropertySet xPropSet, Fill fill) {
		try {
			xPropSet.setPropertyValue("FillColor", fill.faceColor);
       		if (fill.isColorFill()) {
       			com.sun.star.drawing.Hatch hatch = new com.sun.star.drawing.Hatch();
       			switch(fill.hatchStyle) {
       			case NONE:
           			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.SOLID);
           			break;
       			case VERTICAL:	// - - - 
           			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.HATCH);
       				hatch.Style = com.sun.star.drawing.HatchStyle.SINGLE;
       				hatch.Color = fill.hatchColor;
       				hatch.Distance = 100;
       				hatch.Angle = 0;
           			xPropSet.setPropertyValue("FillHatch", hatch);
           			xPropSet.setPropertyValue("FillBackground", true);
           			break;
       			case HORIZONTAL: // |||||
           			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.HATCH);
       				hatch.Style = com.sun.star.drawing.HatchStyle.SINGLE;
       				hatch.Color = fill.hatchColor;
       				hatch.Distance = 100;
       				hatch.Angle = 900; 
           			xPropSet.setPropertyValue("FillHatch", hatch);
           			xPropSet.setPropertyValue("FillBackground", true);
           			break;
       			case BACK_SLASH: // \\\\\
           			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.HATCH);
       				hatch.Style = com.sun.star.drawing.HatchStyle.SINGLE;
       				hatch.Color = fill.hatchColor;
       				hatch.Distance = 100;
       				hatch.Angle = 1350;
           			xPropSet.setPropertyValue("FillHatch", hatch);
           			xPropSet.setPropertyValue("FillBackground", true);
           			break;
       			case SLASH:		// /////
           			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.HATCH);
       				hatch.Style = com.sun.star.drawing.HatchStyle.SINGLE;
       				hatch.Color = fill.hatchColor;
       				hatch.Distance = 100;
       				hatch.Angle = 450;
           			xPropSet.setPropertyValue("FillHatch", hatch);
           			xPropSet.setPropertyValue("FillBackground", true);
           			break;
       			case CROSS:		// +++++
           			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.HATCH);
       				hatch.Style = com.sun.star.drawing.HatchStyle.DOUBLE;
       				hatch.Color = fill.hatchColor;
       				hatch.Distance = 100;
       				hatch.Angle = 0;
           			xPropSet.setPropertyValue("FillHatch", hatch);
           			xPropSet.setPropertyValue("FillBackground", true);
           			break;
       			case CROSS_DIAGONAL:			// xxxxx
           			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.HATCH);
       				hatch.Style = com.sun.star.drawing.HatchStyle.DOUBLE;
       				hatch.Color = fill.hatchColor;
       				hatch.Distance = 100;
       				hatch.Angle = 450;
           			xPropSet.setPropertyValue("FillHatch", hatch);
           			xPropSet.setPropertyValue("FillBackground", true);
           			break;
       			}
       		} else if (fill.isGradFill()) {
       			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.GRADIENT);
       			com.sun.star.awt.Gradient gradient = new com.sun.star.awt.Gradient();
   				gradient.StartColor = fill.colors[0];
   				gradient.EndColor = fill.colors[1];
   				gradient.Angle = (short) (fill.angle * 10);  // 1/10 degree로 맞춘다.
   				gradient.XOffset = (short)Transform.translateHwp2Office(fill.centerX);
   				gradient.YOffset = (short)Transform.translateHwp2Office(fill.centerY);
   				gradient.StepCount = (short)fill.step;
   				
       			switch(fill.gradType) {
       			case LINEAR:	// 줄무니형
       				gradient.Style = com.sun.star.awt.GradientStyle.LINEAR;
       				break;
       			case RADIAL:	// 원형
       				gradient.Style = com.sun.star.awt.GradientStyle.RADIAL;
       				break;
       			case CONICAL:		// 원뿔형
       				gradient.Style = com.sun.star.awt.GradientStyle.AXIAL;
       				break;
       			case SQUARE:		// 사각형
       				gradient.Style = com.sun.star.awt.GradientStyle.RECT;
       				break;
       			}
       			xPropSet.setPropertyValue("FillGradient", gradient);
       		} else if (fill.isImageFill()) {
       			// xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.BITMAP);
       			// com.sun.star.awt.XBitmap bitmap = new com.sun.star.awt.XBitmap();
       			// xPropSet.setPropertyValue("FillBitmap", bitmap);
       		} else {
       			xPropSet.setPropertyValue("FillStyle", com.sun.star.drawing.FillStyle.NONE);
       		}
		} catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException | WrappedTargetException e) {
			e.printStackTrace();
		} // Open Bezier 이므로 Fill 하지 않는다.
    }
    
    // insert Link Image
    private static void insertPIC(WriterContext wContext, Ctrl_GeneralShape shape, String imageExtractPath, int step) {
    	boolean hasCaption = shape.caption==null?false:shape.caption.size()==0?false:true;
    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;
    	XPropertySet paraProps = null;
    	try {
    		if (hasCaption) {
    			xFrame = makeOuterFrame(wContext, shape, false, step);
				// Frame 내부 Cursor 생성
				xFrameText = xFrame.getText();
				xFrameCursor = xFrameText.createTextCursor();
    		}
	  		// 그림그리기
	        Object oGraphic = wContext.mMSF.createInstance("com.sun.star.text.TextGraphicObject");
	        XTextContent xTextContent = (XTextContent) UnoRuntime.queryInterface(XTextContent.class, oGraphic);
	        XPropertySet xPropSet = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, oGraphic);
	        String m_sGraphicFileURL = ConvUtil.convertToURL(wContext, "", imageExtractPath);
	        if (!ConvUtil.checkFile(wContext, m_sGraphicFileURL)) {
	        	log.fine("Impossible to locate the file " + m_sGraphicFileURL);
	        } else {
	        	xPropSet.setPropertyValue("GraphicURL", m_sGraphicFileURL);
	        }
	        if (hasCaption) {
	        	try {
	        		xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
				} catch (UnknownPropertyException | PropertyVetoException | IllegalArgumentException | WrappedTargetException e) {
					log.severe("AnchorType has Exception");
				}
		        xPropSet.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
		        xPropSet.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
		        xPropSet.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
		        xPropSet.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
	        } else {
	        	setPosition(xPropSet, shape, shape.nGrp>0?shape.xGrpOffset:0, shape.nGrp>0?shape.yGrpOffset:0);
	        }
    		setWrapStyle(xPropSet, shape);
			// 위치를 잡은 후에  크기를 조정한다.
	        xPropSet.setPropertyValue("Width", Transform.translateHwp2Office(shape.width));
	        xPropSet.setPropertyValue("Height", Transform.translateHwp2Office(shape.height));
	        // setLineStyle 대신 border 속성값을 직접 넣는다.
	        if (shape instanceof Ctrl_ShapePic) {
				BorderLine2 pictureBorder = Transform.toBorderLine2(shape);
				xPropSet.setPropertyValue("TopBorder", pictureBorder);
				xPropSet.setPropertyValue("BottomBorder", pictureBorder);
				xPropSet.setPropertyValue("LeftBorder", pictureBorder);
				xPropSet.setPropertyValue("RightBorder", pictureBorder);
	        }
	        if (hasCaption) {
	    		XParagraphCursor paraCursor = UnoRuntime.queryInterface(XParagraphCursor.class, xFrameCursor);
	    		paraProps = UnoRuntime.queryInterface(XPropertySet.class, paraCursor);
		        xFrameText.insertTextContent(xFrameCursor, xTextContent, true);
		        xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
	        } else {
		        wContext.mText.insertTextContent(wContext.mTextCursor, xTextContent, true);
		        if (wContext.version >= 72) {
		            TextContentAnchorType anchorType = (TextContentAnchorType)xPropSet.getPropertyValue("AnchorType");
		            if (anchorType == TextContentAnchorType.AT_PARAGRAPH) {
		                wContext.mText.insertString(wContext.mTextCursor, " ", false);
		            }
		        }
	        }
	  		// 캡션 쓰기
  			if (hasCaption) {
  				addCaptionString(wContext, xFrameText, xFrameCursor, shape, step);
  			}
    	} catch (com.sun.star.uno.Exception e) {
    		e.printStackTrace();
    	} catch (SkipDrawingException e) {
    	    e.printStackTrace();
    	}
    }
    
    public static void transform(XPropertySet xPropsSet, Ctrl_GeneralShape shape) throws UnknownPropertyException, WrappedTargetException, IllegalArgumentException, PropertyVetoException {
        // 화면에 그리기 전에는 Transformation 속성값을 가져오지 못한다. 즉, 화면에 그리고 나서 rotation 할 것.
        HomogenMatrix3 aHomogenMatrix3 = (HomogenMatrix3)xPropsSet.getPropertyValue("Transformation");
        AffineTransform prevMatrix = new AffineTransform(aHomogenMatrix3.Line1.Column1, 
                                                        aHomogenMatrix3.Line2.Column1,
                                                        aHomogenMatrix3.Line1.Column2, 
                                                        aHomogenMatrix3.Line2.Column2,
                                                        aHomogenMatrix3.Line1.Column3, 
                                                        aHomogenMatrix3.Line2.Column3 );

        for (int i=shape.matCnt-1; i>=0; i--) {
            // 1. scale matrix
            AffineTransform scaleMatrix = new AffineTransform(shape.matrixSeq[i*12+0], shape.matrixSeq[i*12+3], 
                                                              shape.matrixSeq[i*12+1], /*shape.matrixSeq[i*12+4]==0.0 ? 1 : */shape.matrixSeq[i*12+4], 
                                                              shape.matrixSeq[i*12+2], shape.matrixSeq[i*12+5]);
            scaleMatrix.concatenate(prevMatrix);
            // 2. rotation matrix
            AffineTransform rotatMatrix = new AffineTransform(shape.matrixSeq[i*12+6], shape.matrixSeq[i*12+9], 
                                                              shape.matrixSeq[i*12+7], shape.matrixSeq[i*12+10], 
                                                              shape.matrixSeq[i*12+8], shape.matrixSeq[i*12+11]);
            rotatMatrix.concatenate(scaleMatrix);
            prevMatrix = rotatMatrix;
        }
        // 3. translation matrix
        AffineTransform translateMatrix = new AffineTransform(shape.matrix[0], shape.matrix[3], 
                                                              shape.matrix[1], shape.matrix[4],
                                                              shape.matrix[2], shape.matrix[5]);
        translateMatrix.concatenate(prevMatrix);

        // 4. Hwp Unit -> LO Unit
        AffineTransform hwp2LoScale = new AffineTransform((double)21000/59529, 0, 0, (double)21000/59529, 0, 0);
        hwp2LoScale.concatenate(translateMatrix);
        
        double transformMatrix[] = new double[6];
        hwp2LoScale.getMatrix(transformMatrix);
        // convert the flatMatrix to our HomogenMatrix3 structure
        aHomogenMatrix3.Line1.Column1 = transformMatrix[0];
        aHomogenMatrix3.Line2.Column1 = transformMatrix[1];
        aHomogenMatrix3.Line1.Column2 = transformMatrix[2];
        aHomogenMatrix3.Line2.Column2 = transformMatrix[3];
        aHomogenMatrix3.Line1.Column3 = transformMatrix[4];
        aHomogenMatrix3.Line2.Column3 = transformMatrix[5];
        xPropsSet.setPropertyValue("Transformation", aHomogenMatrix3);
    }
}
