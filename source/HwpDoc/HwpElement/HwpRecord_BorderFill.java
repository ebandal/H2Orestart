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

import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecordTypes.LineType2;

public class HwpRecord_BorderFill extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_BorderFill.class.getName());
	private HwpDocInfo		parent;
	final  float[]			LINE_THICK	= { 0.1f, 0.12f, 0.15f, 0.2f, 0.25f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f };
	
	public boolean			threeD;					// 3D효과의 유무
	public boolean 			shadow;					// 그림자 효과의 유무
	public byte				slash;					// Slash 대각선 모양(시계 방향으로 각각의 대각선 유무를 나타냄, 왼쪽부터 차례대로 "0","2","3","6","7")
	public byte				backSlash;				// BackSlash 대각선 모양(반시계 방향으로 각각의 대각선 유무를 나타냄)
	public byte				crookedSlash;			// Slash 대각선 꺾은선 (slash,backslash의 가운데 대각선이 꺽어진 대각선임)
	public byte				crookedBackSlash;		// BaskSlash 대각선 꺽선
	public boolean			counterSlash;			// Slash 대각선 모양 180도 회전 여부
	public boolean 			counterBackSlash;		// Backslash 대각선 모양 180도 회전 여부
	public boolean			breakCellSeparateLine;	// 중심선 유무
	
	public Border			left;
	public Border			right;
	public Border			top;
	public Border			bottom;
	public Border			diagonal;
	public Fill				fill;
	
	HwpRecord_BorderFill(int tagNum, int level, int size) {
		super(tagNum, level, size);
		left 		= new Border();
		right 		= new Border();
		top 		= new Border();
		bottom 		= new Border();
		diagonal 	= new Border();
	}
	
	public HwpRecord_BorderFill(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
		this(tagNum, level, size);
		this.parent = docInfo;

		int offset = off;
		short typeBits 			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		threeD	 				= (typeBits&0x01)==0x01?true:false;
		shadow		 			= (typeBits&0x02)==0x02?true:false;
		slash 					= (byte) ((typeBits>>>2)&0x07);
		backSlash 				= (byte) ((typeBits>>>5)&0x07);
		crookedSlash 			= (byte) ((typeBits>>>8)&0x03);
		crookedBackSlash 		= (byte) ((typeBits>>>10)&0x01);
		counterSlash			= (typeBits&0x800)==0x800?true:false;
		counterBackSlash		= (typeBits&0x1000)==0x1000?true:false;
		breakCellSeparateLine	= (typeBits&0x2000)==0x2000?true:false;
	
		// Hwp 문서 파일 구조 5.0 에는 4방향정보로 4byte, 4byte, 16byte 읽어내는 것으로 명시했으나, 
		// 실제 hwp 문서를 파싱하면서 판단하기에  1byte,1byte,4byte 4회 반복이 맞는 것 같다고 보임. 
		left.type		= LineType2.from(buf[offset++]);
		left.width 		= buf[offset++];
        left.color      = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;    // 0x00rrggbb
		offset += 4;
		right.type		= LineType2.from(buf[offset++]);
		right.width 	= buf[offset++];
        right.color     = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
		top.type		= LineType2.from(buf[offset++]);
		top.width 		= buf[offset++];
        top.color       = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
		bottom.type		= LineType2.from(buf[offset++]);
		bottom.width 	= buf[offset++];
        bottom.color    = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
		diagonal.type		= LineType2.from(buf[offset++]);
		diagonal.width 	= buf[offset++];
        diagonal.color  = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
		
		fill = new Fill(buf, offset, size-(offset-off));
		offset += fill.getSize();
		
		log.fine("                                                  "
				+"ID="+(parent.borderFillList.size()+1)
				+",3D="+(threeD?"Y":"N")
				+",그림자="+(shadow?"Y":"N")
				+",S="+slash
				+",BS="+backSlash
				+",S꺾="+crookedSlash
				+",BS꺾="+crookedBackSlash
				+",S회전="+(counterSlash?"Y":"N")
				+",BS회전="+(counterBackSlash?"Y":"N")
				+",중심선="+(breakCellSeparateLine?"Y":"N")
				+",4테선종류=("+left.type.toString()+","+right.type.toString()+","+top.type.toString()+","+bottom.type.toString()+")"
				+",4테선굵기=("+(LINE_THICK[left.width])+","+(LINE_THICK[right.width])+","+(LINE_THICK[top.width])	+","+(LINE_THICK[bottom.width])+")"
				+",4테선색=("+String.format("%06X", left.color)+","+String.format("%06X", right.color)+","+String.format("%06X", top.color)+","+String.format("%06X", bottom.color)+")"
				+",대각선="+diagonal.type.toString()
				+",대각선굵기="+(LINE_THICK[diagonal.width])
				+",대각선색깔="+String.format("%06X", diagonal.color)
				+",단색채우기="+(fill.isColorFill()?"Y"+String.format("(%06X)", fill.faceColor):"N")
				+",Grad채우기="+(fill.isGradFill()?"Y("+fill.gradType.toString()+")":"N")
				+",Img채우기="+(fill.isImageFill()?"Y("+fill.mode.toString()+")":"N"));
		
		// 왜 1byte 차이가 나는지 알수 없으나, 실제 문서에서 발생하므로 허용하기로 함. 
		if (offset-off-size!=0) {
			log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
			dump(buf, off, size);
			// throw new HwpParseException();
		}

	}
	
	public HwpRecord_BorderFill(HwpDocInfo docInfo, Node node, int version) throws NotImplementedException {
        super(HwpTag.HWPTAG_BORDER_FILL, 0, 0);
        this.parent = docInfo;

        NamedNodeMap attributes = node.getAttributes();
        
        // id는 처리하지 않는다. List<HwpRecord_BorderFill>에 순차적으로 추가한다. 
        // String id = attributes.getNamedItem("id").getNodeValue();
        
        switch(attributes.getNamedItem("threeD").getNodeValue()) {
        case "0":
            threeD = false;
            break;
        case "1":
            threeD = true;
            break;
        }
        
        switch(attributes.getNamedItem("shadow").getNodeValue()) {
        case "0":
            shadow = false;
            break;
        case "1":
            shadow = true;
            break;
        }
        
        // centerLine 처리는 어떻게 할지 모름.
        // attributes.getNamedItem("centerLine").getNodeValue();
        
        switch(attributes.getNamedItem("breakCellSeparateLine").getNodeValue()) {
        case "0":
            breakCellSeparateLine = false;
            break;
        case "1":
            breakCellSeparateLine = true;
            break;
        }
        
        // fillBrush가 없는 경우를 위해 default로 fill 생성
        fill = new Fill();
        fill.fillType = 0;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hh:slash":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    switch(childAttrs.getNamedItem("type").getNodeValue()) {
                    case "NONE":
                        slash = 0;      break;
                    case "CENTER":
                        slash = 0b010; break; 
                    case "CENTER_BELOW":
                        slash = 0b011; break;
                    case "CENTER_ABOVE":
                        slash = 0b110; break;
                    case "ALL":
                        slash = 0b111; break;
                    }
                    
                    switch(childAttrs.getNamedItem("Crooked").getNodeValue()) {
                    case "0":
                        crookedSlash = 0;   break;
                    case "1":
                        crookedSlash = 1;   break;
                    }
                    
                    switch(childAttrs.getNamedItem("isCounter").getNodeValue()) {
                    case "0":
                        counterSlash = false;   break;
                    case "1":
                        counterSlash = true;    break;
                    }
                }
                break;
            case "hh:backSlash":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    switch(childAttrs.getNamedItem("type").getNodeValue()) {
                    case "NONE":
                        slash = 0;      break;
                    case "CENTER":
                        slash = 0b010; break; 
                    case "CENTER_BELOW":
                        slash = 0b011; break;
                    case "CENTER_ABOVE":
                        slash = 0b110; break;
                    case "ALL":
                        slash = 0b111; break;
                    }

                    switch(childAttrs.getNamedItem("Crooked").getNodeValue()) {
                    case "0":
                        crookedBackSlash = 0;   break;
                    case "1":
                        crookedBackSlash = 1;   break;
                    }
                    
                    switch(childAttrs.getNamedItem("isCounter").getNodeValue()) {
                    case "0":
                        counterBackSlash = false;   break;
                    case "1":
                        counterBackSlash = true;    break;
                    }
                }
                break;
            case "hh:leftBorder":
                left = getBorder(child); break;
            case "hh:rightBorder":
                right = getBorder(child); break;
            case "hh:topBorder":
                top = getBorder(child); break;
            case "hh:bottomBorder":
                bottom = getBorder(child); break;
            case "hh:diagonal":
                diagonal = getBorder(child); break;
            case "hc:fillBrush":
                fill = readFillBrush(child); break;
            default:
                throw new NotImplementedException("HwpRecord_BorderFill");
            }
        }
    }
	
	/*
	 * https://e-ks.kr/streamdocs/view/sd;streamdocsId=72059197557727331  39 page
	 */
	private static Border getBorder(Node child) {
	    Border border = new Border();
        NamedNodeMap childAttrs = child.getAttributes();
        // [color="none", type="NONE", width="0.1 mm"]
        
        border.type = LineType2.valueOf(childAttrs.getNamedItem("type").getNodeValue());
        String colorStr = childAttrs.getNamedItem("color").getNodeValue().replaceAll("^#([0-9A-F]+)$", "$1");
        if (!colorStr.equals("none")) {
        	border.color = Integer.parseInt(colorStr, 16);      // RGBColor (0xRRGGBB) 값으로 저장
        }
        switch(childAttrs.getNamedItem("width").getNodeValue()) {
        case "0.1":
        case "0.1 mm":
            border.width = 0; break;
        case "0.12":
        case "0.12 mm":
            border.width = 1; break;
        case "0.15":
        case "0.15 mm":
            border.width = 2; break;
        case "0.2":
        case "0.2 mm":
            border.width = 3; break;
        case "0.25":
        case "0.25 mm":
            border.width = 4; break;
        case "0.3":
        case "0.3 mm":
            border.width = 5; break;
        case "0.4":
        case "0.4 mm":
            border.width = 6; break;
        case "0.5":
        case "0.5 mm":
            border.width = 7; break;
        case "0.6":
        case "0.6 mm":
            border.width = 8; break;
        case "0.7":
        case "0.7 mm":
            border.width = 9; break;
        case "1.0":
        case "1.0 mm":
            border.width = 10; break;
        case "1.5":
        case "1.5 mm":
            border.width = 11; break;
        case "2.0":
        case "2.0 mm":
            border.width = 12; break;
        case "3.0":
        case "3.0 mm":
            border.width = 13; break;
        case "4.0":
        case "4.0 mm":
            border.width = 14; break;
        case "5.0":
        case "5.0 mm":
            border.width = 15; break;
        }
        return border;
    }
	
	public static Fill readFillBrush(Node child) throws NotImplementedException {
	    Fill fill = new Fill();
        NodeList grandChildren = child.getChildNodes();
        for (int j=0; j<grandChildren.getLength(); j++) {
            Node grandChild = grandChildren.item(j);

            switch(grandChild.getNodeName()) {
            case "hc:winBrush":
                readWinBrush(grandChild, fill);
                fill.fillType = fill.fillType | 0x01;
                break;
            case "hc:gradation":
                readGradation(grandChild, fill); 
                fill.fillType = fill.fillType | 0x04;
                break;
            case "hc:imgBrush":
                readImgBrush(grandChild, fill);
                fill.fillType = fill.fillType | 0x02;
                break;
            default:
                throw new NotImplementedException("readFillBrush");
            }
        }
        return fill;
	}

	private static void readWinBrush(Node child, Fill fill) {
        NamedNodeMap childAttrs = child.getAttributes();
        fill.faceColor = 0xFFFFFFFF;
        String colorStr = childAttrs.getNamedItem("faceColor").getNodeValue();
        if (!colorStr.equals("none")) {
            colorStr = colorStr.replaceAll("#", "");
            fill.faceColor = (int) Long.parseLong(colorStr, 16);      // RGBColor (0xRRGGBB) 값으로 저장
        }

        fill.hatchColor = 0x000000;
        colorStr = childAttrs.getNamedItem("hatchColor").getNodeValue();
        if (!colorStr.equals("none")) {
            colorStr = colorStr.replaceAll("#", "");
            fill.hatchColor = (int) Long.parseLong(colorStr, 16);      // RGBColor (0xRRGGBB) 값으로 저장
        }
        
        if (childAttrs.getNamedItem("hatchStyle")==null) {
            fill.hatchStyle = ColorFillPattern.NONE;
        } else {
            fill.hatchStyle = ColorFillPattern.valueOf(childAttrs.getNamedItem("hatchStyle").getNodeValue());
            /*
            switch(childAttrs.getNamedItem("hatchStyle").getNodeValue()) {
            case "HORIZONTAL":
                fill.hatchStyle = ColorFillPattern.HORIZONTAL;  break;
            case "VERTICAL":
                fill.hatchStyle = ColorFillPattern.VERTICAL;    break;
            case "BACK_SLASH":
                fill.hatchStyle = ColorFillPattern.BACK_SLASH;  break;
            case "SLASH":
                fill.hatchStyle = ColorFillPattern.SLASH;       break;
            case "CROSS":
                fill.hatchStyle = ColorFillPattern.CROSS;       break;
            case "CROSS_DIAGONAL":
                fill.hatchStyle = ColorFillPattern.CROSS_DIAGONAL;  break;
            }
            */
        }
            
        colorStr = childAttrs.getNamedItem("alpha").getNodeValue();
        fill.alpha = (byte)Float.parseFloat(colorStr);
	}
	
	private static void readGradation(Node child, Fill fill) {
	    NamedNodeMap childAttrs = child.getAttributes();
	    fill.gradType = GradFillType.valueOf(childAttrs.getNamedItem("type").getNodeValue());
	    /*
        switch(childAttrs.getNamedItem("type").getNodeValue()) {
        case "LINEAR":
            fill.gradType = GradFillType.LINEAR;    break;
        case "RADIAL":
            fill.gradType = GradFillType.RADIAL;    break;
        case "CONICAL":
            fill.gradType = GradFillType.CONICAL;   break;
        case "SQUARE":
            fill.gradType = GradFillType.SQUARE;    break;
        }
        */
	    
        String numStr = childAttrs.getNamedItem("angle").getNodeValue();
        fill.angle = Integer.parseInt(numStr);
        
        numStr = childAttrs.getNamedItem("centerX").getNodeValue();
        fill.centerX = Integer.parseInt(numStr);
        
        numStr = childAttrs.getNamedItem("centerY").getNodeValue();
        fill.centerY = Integer.parseInt(numStr);

        numStr = childAttrs.getNamedItem("step").getNodeValue();
        fill.step = Integer.parseInt(numStr);

        numStr = childAttrs.getNamedItem("colorNum").getNodeValue();
        fill.colorNum = Integer.parseInt(numStr);

        numStr = childAttrs.getNamedItem("stepCenter").getNodeValue();
        fill.stepCenter = (byte)Integer.parseInt(numStr);

        numStr = childAttrs.getNamedItem("stepCenter").getNodeValue();
        fill.alpha = (byte)Float.parseFloat(numStr);
        
        NodeList grandChildren = child.getChildNodes();
        fill.colors = new int[grandChildren.getLength()];
        
        for (int j=0; j<grandChildren.getLength(); j++) {
            Node grandChild = grandChildren.item(j);
            switch(grandChild.getNodeName()) {
            case "Color":
                String colorStr = grandChild.getNodeValue().replaceAll("\\D", "");
                fill.colors[j] = Integer.parseInt(colorStr, 16);      // RGBColor (0xRRGGBB) 값으로 저장
                break;
            }
        }
    }

    private static void readImgBrush(Node child, Fill fill) {
        NamedNodeMap childAttrs = child.getAttributes();
        fill.mode = ImageFillType.valueOf(childAttrs.getNamedItem("mode").getNodeValue());
        
        String numStr;

        NodeList grandChildren = child.getChildNodes();
        for (int j=0; j<grandChildren.getLength(); j++) {
            Node grandChild = grandChildren.item(j);
            switch(grandChild.getNodeName()) {
            case "img":
                {
                    NamedNodeMap gradChildAttrs = child.getAttributes();
                    numStr = gradChildAttrs.getNamedItem("bright").getNodeValue();
                    fill.bright = (byte)Integer.parseInt(numStr);
                    
                    numStr = gradChildAttrs.getNamedItem("contrast").getNodeValue();
                    fill.contrast = (byte)Integer.parseInt(numStr);
                    switch(gradChildAttrs.getNamedItem("effect").getNodeValue()) {
                    case "REAL_PIC":
                        fill.effect = 0;    break;
                    case "GRAY_SCALE":
                        fill.effect = 1;    break;
                    case "BLACK_WHITE":
                        fill.effect = 2;    break;
                    }

                    numStr = gradChildAttrs.getNamedItem("binaryItemIDRef").getNodeValue();
                    fill.binItem = (short)Integer.parseInt(numStr);

                    numStr = gradChildAttrs.getNamedItem("alpha").getNodeValue();
                    fill.binItem = (byte)Float.parseFloat(numStr);
                }
                break;
            }
        }
    }
	
	
    public static class Fill {
		private		int 	size;

		public int			fillType;
		public byte[]		extraFill;		// 추가 채우기 속성 - 그라데이션일 경우, 번짐 정도의 중심(0-100): 1byte
		
		public int 			faceColor;		// 배경색
		public int			hatchColor;		// 무늬색
		public ColorFillPattern	hatchStyle;		// 무늬 종류

		public GradFillType	gradType;		// 그라데이션 유형
		public int			angle;			// 그라데이션의 기울임
		public int			centerX;		// 그라데이션의 가로 중심
		public int			centerY;		// 그라데이션의 세로 중심
		public int			step;			// 그라데이션의 번짐 정도 (0~100)
		public int			colorNum;		// 그라데이션의 색 수(num). (워디안/한글2002/SE에서는 항상2이다.)
		public int[]		colors;		    // 그라데이션 색상 (* 색 수) 시작색깔, 끝색깔
		// public int			endColor;		// 끝색깔

		public ImageFillType	mode;			// 채우기유형
		public byte			bright;			// 밝기
		public byte			contrast;		// 명암
		public byte			effect;			// 그림효과. 0:REAL-PIC, 1:GRAY_SCALE, 2:BLACK_WHITE, 4:PATTERN8x8
		public short		binItem;		// BinItem의 아이디 참조값
		public byte			stepCenter;		// 그라데이션 번짐정도의 중심 (0..100)

		public byte			alpha;			// 투명도 0~256 (0~100%)
		
		public Fill() {}
		
		public Fill(byte[] buf, int off, int size) throws HwpParseException {
			int offset = off;
			
			fillType 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;

			if ((fillType&0x01)==0x01) {
                faceColor       = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
				offset += 4;
                hatchColor      = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
				offset += 4;
				int nPattern 	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
				offset += 4;
				hatchStyle		= ColorFillPattern.from(nPattern);
			}
			if ((fillType&0x04)==0x04) {
				byte typeNum 	= buf[offset++];
				gradType		= GradFillType.from(typeNum);
				
				angle	 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
				offset += 4;
				centerX 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
				offset += 4;
				centerY 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
				offset += 4;
				step	 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
				offset += 4;
				colorNum 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
				offset += 4;
				if (colorNum > 0) {
				    colors = new int[colorNum];
	                if (colorNum > 2) {
	                    // 색상이 바뀌는 곳의 위치. 4bytes * 색 수
	                    offset += (4 * (colorNum-2));
	                }
				}
				for (int i=0; i<colorNum; i++) {
                    colors[i]    = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
				    offset += 4;
				}
			}
			if ((fillType&0x02)==0x02) {
				byte typeNum 	= buf[offset++];
				mode			= ImageFillType.from(typeNum);
				
				bright 			= buf[offset++];
				contrast 		= buf[offset++];
				effect		 	= buf[offset++];
				binItem		 	= (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
				offset += 2;
			}
			
			int	moreSize	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
			if (moreSize>0) {
				stepCenter	= buf[offset++];
				offset += (moreSize-1);
			}
			if (fillType>0) {
				alpha			= buf[offset++];
			}
			// 이미지fill 일 경우, alpha값이 한번 더 있다.
			if ((fillType&0x02)==0x02) {	
				alpha			= buf[offset++];
			}					
			
			
			// 문서상에는 없으나, "추가 채우기 속성 길이"가 큰 값일 경우  무시하도록 한다.
//			if (extraSize>0 && offset-off-size==extraSize) {
//				extraFill = new byte[extraSize];
//				System.arraycopy(buf, offset, extraFill, 0, extraSize);
//				offset += extraSize;
//			}
			
			if (offset-off-size!=0) {
				log.fine("[Fill] size=" + size + ", but currentSize=" + (offset-off));
				dump(buf, off, size);
				// throw new HwpParseException();
			}
			this.size = offset-off;
		}
		
		public int getSize() {
			return this.size;
		}
		
		public boolean isColorFill() {
			return (fillType&0x01)==0x01;
		}

		public boolean isGradFill() {
			return (fillType&0x04)==0x04;
		}
		
		public boolean isImageFill() {
			return (fillType&0x02)==0x02;
		}
	}

	public static class Border {
		public LineType2	type;	// 선 종류
		public byte			width;	// 굵기  (0.1/0.12/0.15/0.2/0.25/0.3/0.4/0.5/0.6/0.7/1.0/1.5/2.0/3.0/4.0/5.0 mm)
		public int			color;	// 색상  0xRRGGBB 값으로 저장하자.  

	}
	
	public static enum ImageFillType {
		TILE				(0),	// 바둑판식으로 - 모두 
		TILE_HORZ_TOP		(1),	// 바둑판식으로 - 가로/위
		TILE_HORZ_BOTTOM	(2),	// 바둑판식으로 - 가로/아래
		TILE_VERT_LEFT		(3),	// 바둑판식으로 - 세로/왼쪽
		TILE_VERT_RIGHT		(4),	// 바둑판식으로 - 세로/오른쪽
		TOTAL				(5),	// 크기에 맞추어
		CENTER				(6),	// 가운데로
		CENTER_TOP			(7),	// 가운데 위로
		CENTER_BOTTOM		(8),	// 가운데 아래로
		LEFT_CENTER			(9),	// 왼쪽 가운데로
		LEFT_TOP			(10), 	// 왼쪽 위로
		LEFT_BOTTOM			(11),	// 왼쪽 아래로
		RIGHT_CENTER		(12),	// 오른쪽 가운데로
		RIGHT_TOP			(13), 	// 오른쪽 위로
		RIGHT_BOTTOM		(14),	// 오른쪽 아래로
		ZOOM				(15),	// 확대
		NONE				(16);	// NONE

		private int fill;
		
	    private ImageFillType(int fill) { 
	    	this.fill = fill;
	    }

	    public static ImageFillType from(int fill) {
	    	for (ImageFillType typeNum: values()) {
	    		if (typeNum.fill == fill)
	    			return typeNum;
	    	}
	    	return null;
	    }
	}
	
	
	public static enum GradFillType {
		LINEAR      (1),	// 줄무니형 
		RADIAL      (2),	// 원형
		CONICAL     (3),	// 원뿔형
		SQUARE      (4);	// 사각형

		private int fill;
		
	    private GradFillType(int fill) { 
	    	this.fill = fill;
	    }

	    public static GradFillType from(int gradation) {
	    	for (GradFillType typeNum: values()) {
	    		if (typeNum.fill == gradation)
	    			return typeNum;
	    	}
	    	return null;
	    }
	}

	public static enum ColorFillPattern {
		NONE           (-1),
		VERTICAL       (0),	// - - - 
		HORIZONTAL	   (1),	// |||||
		BACK_SLASH	   (2),	// \\\\\
		SLASH		   (3),	// /////
		CROSS		   (4),	// +++++
		CROSS_DIAGONAL (5);	// xxxxx

		private int fill;
		
	    private ColorFillPattern(int fill) { 
	    	this.fill = fill;
	    }

	    public static ColorFillPattern from(int fill) {
	    	for (ColorFillPattern typeNum: values()) {
	    		if (typeNum.fill == fill)
	    			return typeNum;
	    	}
	    	return null;
	    }		
	}
}
