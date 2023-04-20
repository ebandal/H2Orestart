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

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.logging.Logger;

import com.sun.star.awt.Point;
import com.sun.star.beans.PropertyValue;
import com.sun.star.drawing.TextVerticalAdjust;
import com.sun.star.frame.XStorable;
import com.sun.star.io.IOException;
import com.sun.star.style.ParagraphAdjust;
import com.sun.star.table.BorderLine;
import com.sun.star.table.BorderLine2;
import com.sun.star.table.BorderLineStyle;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.HwpElement.HwpRecordTypes;
import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecordTypes.LineType2;
import HwpDoc.paragraph.Ctrl_GeneralShape;
import HwpDoc.paragraph.Ctrl_ShapePic;

public class Transform {
    
	/*
	 * change value in HWP to value in OpenOffice
	 * A4의 가로 길이는 Hwp에서는 59529(1/7000 inch), OpenOffice에서는 210(mm)
	 */
	public static int translateHwp2Office(int hwpValue) {
		int officeValue = (int)(hwpValue*((double)21000/59529));
		return officeValue;
	}

	public static BorderLine toBorderLine(HwpRecord_BorderFill.Border border) {
		BorderLine line = new BorderLine();
		if (border==null) {
			line.Color= 0x000000;
			// line.LineStyle = BorderLineStyle.NONE;
			line.InnerLineWidth = 0;
			line.OuterLineWidth = 0;
			line.LineDistance = 0;
		} else {
			line.Color= border.color;
			// line.LineStyle= Transform.toBorderLineStyle(border.type);
			line.InnerLineWidth = 0;
			line.OuterLineWidth = border.type==LineType2.NONE?0:Transform.toLineWidth(border.width);
			line.LineDistance = 0;
		}
		return line;
	}

	public static BorderLine2 toBorderLine2(HwpRecord_BorderFill.Border border) {
		BorderLine2 line = new BorderLine2();
		if (border==null || border.type == LineType2.NONE) {
			line.Color= 0x000000;
			line.LineStyle = BorderLineStyle.NONE;
			line.InnerLineWidth = 0;
			line.OuterLineWidth = 0;
			line.LineDistance = 0;
			line.LineWidth = 0;
		} else {
			line.LineStyle= Transform.toBorderLineStyle(border.type);
			line.Color= border.color;
			
			switch(line.LineStyle) {
			case BorderLineStyle.DOUBLE:
			case BorderLineStyle.DOUBLE_THIN:
				line.LineWidth = Transform.toLineWidth(border.width);
				line.InnerLineWidth = (short)(line.LineWidth/3);
				line.OuterLineWidth = (short)(line.LineWidth/3);
				line.LineDistance = (short)(line.LineWidth);
				break;
			case BorderLineStyle.THICKTHIN_LARGEGAP:
			case BorderLineStyle.THICKTHIN_MEDIUMGAP:
			case BorderLineStyle.THICKTHIN_SMALLGAP:
			case BorderLineStyle.THINTHICK_LARGEGAP:
			case BorderLineStyle.THINTHICK_MEDIUMGAP:
			case BorderLineStyle.THINTHICK_SMALLGAP:
				line.LineWidth = Transform.toLineWidth(border.width);
				line.InnerLineWidth = (short)(line.LineWidth/3);
				line.OuterLineWidth = (short)(line.LineWidth/3);
				line.LineDistance = (short)(line.LineWidth);
				break;
			case BorderLineStyle.SOLID:
			case BorderLineStyle.DASHED:
			case BorderLineStyle.FINE_DASHED:
			case BorderLineStyle.DOTTED:
			case BorderLineStyle.DASH_DOT:
			case BorderLineStyle.DASH_DOT_DOT:
			case BorderLineStyle.EMBOSSED:
			case BorderLineStyle.ENGRAVED:
			case BorderLineStyle.INSET:
			case BorderLineStyle.OUTSET:
			default:
				line.LineWidth = Transform.toLineWidth(border.width);
				line.InnerLineWidth = 0;
				line.OuterLineWidth = (short)line.LineWidth;
				line.LineDistance = 0;
			}
		}
		return line;
	}

	public static BorderLine2 toBorderLineDefault(HwpRecord_BorderFill.Border border) {
		BorderLine2 line = new BorderLine2();
		if (border==null || border.type == LineType2.NONE) {
			line.Color= 0x000000;
			line.LineStyle = BorderLineStyle.NONE;
			line.InnerLineWidth = 0;
			line.OuterLineWidth = 0;
			line.LineDistance = 0;
			line.LineWidth = 0;
		} else {
			line.LineStyle= BorderLineStyle.DOUBLE;
			line.LineWidth = 90;
			line.Color= 0x000000;
			line.InnerLineWidth = 30;
			line.OuterLineWidth = 30;
			line.LineDistance = 30;
		}
		return line;
	}

	public static BorderLine2 toBorderLine2(Ctrl_GeneralShape shape) {
		BorderLine2 line = new BorderLine2();
		if (shape==null) {
			line.Color= 0x000000;
			line.LineStyle = BorderLineStyle.NONE;
			line.InnerLineWidth = 0;
			line.OuterLineWidth = 0;
			line.LineDistance = 0;
			line.LineWidth = 0;
		} else {
			if (shape instanceof Ctrl_ShapePic) {
				line.Color= ((Ctrl_ShapePic)shape).borderColor;
				line.LineStyle= BorderLineStyle.SOLID;
				line.InnerLineWidth = 0;
				line.LineWidth = Transform.translateHwp2Office(((Ctrl_ShapePic)shape).borderThick);
			} else {
				line.Color= shape.lineColor;
				line.LineStyle= Transform.toBorderLineStyle(shape.lineType);
				line.InnerLineWidth = 0;
				line.OuterLineWidth = shape.lineType==LineType2.NONE?0:Transform.toLineWidth((byte)shape.lineThick);
				line.LineDistance = (short)(shape.lineType==LineType2.NONE?0:100);
				line.LineWidth = shape.lineType==LineType2.NONE?0:Transform.translateHwp2Office(shape.lineThick);
				if (line.LineStyle!=BorderLineStyle.NONE && line.LineWidth==0) {
					line.LineWidth = 1;
				}
			}
		}
		return line;
	}

	public static ParagraphAdjust toHorzAlign(short align ) {
		ParagraphAdjust adjust = com.sun.star.style.ParagraphAdjust.BLOCK;
		
		switch(align) {
		case 0:		// 0:양쪽정렬
			adjust = com.sun.star.style.ParagraphAdjust.STRETCH;
			break;
		case 1:		// 1:왼쪽정렬
			adjust = com.sun.star.style.ParagraphAdjust.LEFT;
			break;
		case 2:		// 2:오른쪽정렬
			adjust = com.sun.star.style.ParagraphAdjust.RIGHT;
			break;
		case 3:		// 3:가운데정렬
			adjust = com.sun.star.style.ParagraphAdjust.CENTER;
			break;
		case 4:		// 4:배분정렬
		case 5:		// 5:나눔 정렬
			adjust = com.sun.star.style.ParagraphAdjust.BLOCK;
			break;
		}
		return adjust;
	}

	public static TextVerticalAdjust toTextVertAlign(int align) {
		TextVerticalAdjust orient = TextVerticalAdjust.BLOCK;
		
		switch(align) {
		case 0:		// 0:Top
			orient = TextVerticalAdjust.TOP;
			break;
		case 1:		// 1:Center
			orient = TextVerticalAdjust.CENTER;
			break;
		case 2:		// 2:Bottom
			orient = TextVerticalAdjust.BOTTOM;
			break;
		}
		return orient;
	}

	public static short toVertAlign(int align) {
		short orient = com.sun.star.text.VertOrientation.NONE;
		
		switch(align) {
		case 0:		// 0:Top
			orient = com.sun.star.text.VertOrientation.TOP;
			break;
		case 1:		// 1:Center
			orient = com.sun.star.text.VertOrientation.CENTER;
			break;
		case 2:		// 2:Bottom
			orient = com.sun.star.text.VertOrientation.BOTTOM;
			break;
		}
		return orient;
	}
	
	public static short toLineWidth(short hwpThick) {
		short lineWidth = 0;
		switch(hwpThick) {
		case 0:		// 0.1mm
			lineWidth = 10; break;
		case 1:		// 0.12mm
			lineWidth = 12; break;
		case 2:		// 0.15mm
			lineWidth = 15; break;
		case 3:		// 0.2mm
			lineWidth = 20; break;
		case 4:		// 0.25mm
			lineWidth = 25; break;
		case 5:		// 0.3mm
			lineWidth = 30; break;
		case 6:		// 0.4mm
			lineWidth = 40; break;
		case 7:		// 0.5mm
			lineWidth = 50; break;
		case 8:		// 0.6mm
			lineWidth = 60; break;
		case 9:		// 0.7mm
			lineWidth = 70; break;
		case 10:	// 1.0mm
			lineWidth = 100; break;
		case 11:	// 1.5mm
			lineWidth = 150; break;
		case 12:	// 2.0mm
			lineWidth = 200; break;
		case 13:	// 3.0mm
			lineWidth = 300; break;
		case 14:	// 4.0mm
			lineWidth = 400; break;
		case 15:	// 5.0mm
			lineWidth = 500; break;
		}
		return lineWidth;
	}
	
	public static short toBorderLineStyle(HwpRecordTypes.LineType2 line) {
		short lineStyle = BorderLineStyle.NONE;
		if (line==null) return lineStyle;
		
		switch(line) {
		case NONE:
			lineStyle =  BorderLineStyle.NONE;
			break;
		case SOLID:
			lineStyle =  BorderLineStyle.SOLID;
			break;
		case DASH:
		case LONG_DASH:
			lineStyle =  BorderLineStyle.DASHED;
			break;
		case DOT:
			lineStyle =  BorderLineStyle.DOTTED;
			break;
		case DASH_DOT:
			lineStyle =  BorderLineStyle.DASH_DOT;
			break;
		case DASH_DOT_DOT:
			lineStyle =  BorderLineStyle.DASH_DOT_DOT;
			break;
		case DOUBLE_SLIM:
			lineStyle =  BorderLineStyle.THICKTHIN_MEDIUMGAP;
			break;
		case SLIM_THICK:
			lineStyle =  BorderLineStyle.THINTHICK_MEDIUMGAP;
			// lineStyle =  BorderLineStyle.DOUBLE;
			break;
		case THICK_SLIM:
			lineStyle =  BorderLineStyle.THICKTHIN_MEDIUMGAP;
			// lineStyle =  BorderLineStyle.DOUBLE;
			break;
		case SLIM_THICK_SLIM:
			lineStyle =  BorderLineStyle.THINTHICK_MEDIUMGAP;
			// lineStyle =  BorderLineStyle.DOUBLE;
			break;
		case CIRCLE:
		default:
			lineStyle =  BorderLineStyle.DASHED;
			break;
		}
		
		return lineStyle;
	}
	
	public static void save2Pdf(XTextDocument myDoc, String fileURI) throws IOException {
        if (fileURI==null || fileURI.isEmpty()) {
        	return;
        }
        fileURI = fileURI.replace('\\',  '/');
        // save as a PDF
        PropertyValue[] propertyValues = new PropertyValue[2];
        propertyValues[0] = new PropertyValue();
        propertyValues[0].Name = "Overwrite";
        propertyValues[0].Value = true;
        propertyValues[1] = new PropertyValue();
        propertyValues[1].Name = "FilterName";
        propertyValues[1].Value = "writer_pdf_Export";
        
        XStorable xStorable = (XStorable) UnoRuntime.queryInterface(XStorable.class, myDoc);
		xStorable.storeToURL("file:///" + fileURI, propertyValues);
	}
	
	public static void save2Odf(XTextDocument myDoc, String fileURI) throws IOException {
        if (fileURI==null || fileURI.isEmpty()) {
        	return;
        }
        fileURI = fileURI.replace('\\',  '/');
        // save as ODF
        PropertyValue[] propertyValues = new PropertyValue[2];
        propertyValues[0] = new PropertyValue();
        propertyValues[0].Name = "Overwrite";
        propertyValues[0].Value = true;
        propertyValues[1] = new PropertyValue();
        propertyValues[1].Name = "FilterName";
        propertyValues[1].Value = "writer8";

        XStorable xStorable = (XStorable) UnoRuntime.queryInterface(XStorable.class, myDoc);
       	xStorable.storeToURL("file:///" + fileURI, propertyValues);
	}

    public static Point2D rotateValue(int rotat, Point2D ptSrc) {
        Point2D ptDst = null;
        
        if (rotat%90 == 0) { // 4 quadrant
            if (rotat%180 == 0) {
                ptDst = (Point2D) ptSrc.clone();
            } else {
                ptDst = new Point2D.Double(ptSrc.getY(), ptSrc.getX());
            }
        } else {
            AffineTransform at = new AffineTransform();
            at.translate(0, 0);
            double angle = rotat%180==90? Math.PI/2 : Math.toRadians(rotat%180);
            at.rotate(angle);
            ptDst = at.transform(ptSrc, null);
        }
        return ptDst;
    }

    public static Point2D rotateValue(double angle, Point2D ptSrc) {
        Point2D ptDst = null;
        
        AffineTransform at = new AffineTransform();
        at.translate(0, 0);
        at.rotate(angle);
        ptDst = at.transform(ptSrc, null);
        return ptDst;
    }

}
