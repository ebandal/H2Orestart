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
package HwpDoc.HwpElement;

import static org.junit.Assert.assertNotNull;

import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.HwpElement.HwpRecordTypes.LineType1;
import HwpDoc.HwpElement.HwpRecordTypes.LineType2;

public class HwpRecord_CharShape extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_CharShape.class.getName());
	private HwpDocInfo	parent;
	
	public String[]		fontName	= new String[Lang.MAX.num];	// 언어별 글꼴명(FaceID에서 유도)	// f#
	public short[]		ratio		= new short[Lang.MAX.num];	// 언어별 장평, 50%~200%		// r#
	public short[]		spacing		= new short[Lang.MAX.num];	// 언어별 자간, -50%~50%		// s#
	public short[]		relSize		= new short[Lang.MAX.num];	// 언어별 상대 크기, 10%~250%	// e#
	public short[]		charOffset	= new short[Lang.MAX.num];	// 언어별 글자 위치, -100%~100%	// o#
	public int			height;									// 기준 크기, 0pt~4096pt		// he
	
	// public int		attribute;								// 속성
	public boolean 		italic;									// 기울임 여부					// it
	public boolean 		bold;									// 진하게 여부					// bo
	public Underline	underline;								// 밑줄 종류					// ut
	public LineType2	underlineShape;							// 밑줄 모양					// us
	public int			underlineColor;							// 밑줄 색
	public Outline		outline;								// 외곽선종류					// 
	public Shadow		shadow;									// 그림자 종류					// 
	public boolean		emboss;									// 양각 여부					// em?
	public boolean 		engrave;								// 음각 여부					// en?
	public boolean		superScript;							// 위 첨자 여부					// su?
	public boolean		subScript;								// 아래 첨자 여부				// sb?
	public byte			strikeOut;								// 취소선 여부
	public Accent		symMark;								// 강조점 종류
	public boolean		useFontSpace;							// 글꼴에 어울리는 빈칸 사용 여부		// uf?
	public LineType2	strikeOutShape;							// 취소선 모양
	public boolean		useKerning;								// kerning여부				// uk?
	
	public byte			shadowOffsetX;							// 그림자 간격, -100%~100%
    public byte         shadowOffsetY;                          // 그림자 간격, -100%~100%
	public int			textColor;								// 글자 색						// 
	public int			shadeColor;								// 음영 색
	public int			shadowColor;							// 그림자 색
	public short		borderFillIDRef;						// 글자 테두리/배경 ID(CharShapeBorderFill ID) 참조 값
	public int			strikeOutColor;							// 취소선 색
	
	HwpRecord_CharShape(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public HwpRecord_CharShape(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		this(tagNum, level, size);
		this.parent = docInfo;

		int offset = off;
		for (int i=0; i < Lang.MAX.num; i++) {
			short fontID 	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
			fontName[i] 	= ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
			offset += 2;
		}
		for (int i=0; i < Lang.MAX.num; i++) {
			ratio[i] 	= (short) (buf[offset++] & 0x00FF);
		}
		for (int i=0; i < Lang.MAX.num; i++) {
			spacing[i] 	= (byte) (buf[offset++] & 0x00FF);
		}
		for (int i=0; i < Lang.MAX.num; i++) {
			relSize[i] 	= (short) (buf[offset++] & 0x00FF);
		}
		for (int i=0; i < Lang.MAX.num; i++) {
			charOffset[i] = (byte) (buf[offset++] & 0x00FF);
		}
		
		
		height			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		int attrBits	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;

		// Attributes
		italic			= (attrBits&0x01)==0x01?true:false;
		bold			= (attrBits&0x02)==0x02?true:false;
		underline   	= Underline.from((attrBits>>>2)&0x03);
		underlineShape 	= LineType2.from((attrBits>>>4)&0x0F);
		outline			= Outline.from((attrBits>>>8)&0x7);
		shadow			= Shadow.from((attrBits>>11)&0x03);
		emboss			= (attrBits&0x2000)==0x2000?true:false;
		engrave			= (attrBits&0x4000)==0x4000?true:false;
		superScript		= (attrBits&0x8000)==0x8000?true:false;
		subScript		= (attrBits&0xF000)==0xF000?true:false;
		strikeOut		= (byte) ((attrBits>>>18)&0x07);
		symMark			= Accent.from((attrBits>>>21)&0x0F);
		useFontSpace	= (attrBits&0x2000000)==0x2000000?true:false;
		strikeOutShape	= LineType2.from((attrBits>>>26)&0x0F);
		useKerning		= (attrBits&0x40000000)==0x40000000?true:false;
		// Attributes
		
		shadowOffsetX	= buf[offset++];
        shadowOffsetY   = buf[offset++];
        textColor       = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
        underlineColor  = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
        shadeColor      = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
        shadowColor     = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
		offset += 4;
		if (offset-off < size) {
		    borderFillIDRef	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
			offset += 2;
		}
		if (version > 5030 && offset-off < size) {
            strikeOutColor  = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
			offset += 4;
		}
		
		log.fine("                                                  "
				+"ID="+(parent.charShapeList.size())
				+",폰트명[0]="+fontName[0]
				+",장평="+ratio[0]+"%"
				+",자간="+spacing[0]+"%"
				+",크기="+relSize[0]+"%"
				+",위치="+charOffset[0]+"%"
				+",크기="+height+"pt"
				+",기울임="+(italic?"Y":"N")
				+",진하게="+(bold?"Y":"N")
				+",밑줄="+(underline==null?"???":underline.toString())
				+",외곽선="+(outline==null?"???":outline.toString())
				+",그림자="+(shadow==null?"???":shadow.toString())
				+",글자색="+String.format("%06X", textColor)
				+",음영색="+String.format("%06X", shadeColor)
				+",테두리ID="+borderFillIDRef
				+(borderFillIDRef>0?",테두리=("+(((HwpRecord_BorderFill) (parent.borderFillList.get(borderFillIDRef-1))).left.type):"")
				+(borderFillIDRef>0?","+(((HwpRecord_BorderFill) (parent.borderFillList.get(borderFillIDRef-1))).right.type):"")
				+(borderFillIDRef>0?","+(((HwpRecord_BorderFill) (parent.borderFillList.get(borderFillIDRef-1))).top.type):"")
				+(borderFillIDRef>0?","+(((HwpRecord_BorderFill) (parent.borderFillList.get(borderFillIDRef-1))).bottom.type):"")+")"
		 	);
		
		if (offset-off-size != 0 && offset-off-size+1 != 0) {
			log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
			dump(buf, off, size);
			// throw new HwpParseException();
		}
	}

	public HwpRecord_CharShape(HwpDocInfo docInfo, Node node, int version) {
        super(HwpTag.HWPTAG_CHAR_SHAPE, 0, 0);
        this.parent = docInfo;
        
        dumpNode(node, 1);

        NamedNodeMap attributes = node.getAttributes();
        
        // id값은 처리하지 않는다. List<HwpRecord_CharShape>에 순차적으로 추가한다.
        // String id = attributes.getNamedItem("height").getNodeValue();
        
        height = 1000;
        String numStr = attributes.getNamedItem("height").getNodeValue();
        height = Integer.parseInt(numStr);
        
        textColor = 0x000000; 
        numStr = attributes.getNamedItem("textColor").getNodeValue();
        if (!numStr.equals("none")) {
            numStr = numStr.replaceAll("#", "");
            textColor = (int) Long.parseLong(numStr, 16);      // RGBColor (0xRRGGBB) 값으로 저장
        }

        shadeColor = 0xFFFFFFFF;
        if (attributes.getNamedItem("shadeColor") != null) {
            numStr = attributes.getNamedItem("shadeColor").getNodeValue();
            if (!numStr.equals("none")) {
                numStr = numStr.replaceAll("#", "");
                shadeColor = (int) Long.parseLong(numStr, 16);      // RGBColor (0xRRGGBB) 값으로 저장
            }
        }
        
        useFontSpace = false;
        switch(attributes.getNamedItem("useFontSpace").getNodeValue()) {
        case "0":
            useFontSpace = false;   break;
        case "1":
            useFontSpace = true;    break;
        }

        useKerning = false;
        switch(attributes.getNamedItem("useKerning").getNodeValue()) {
        case "0":
            useKerning = false;   break;
        case "1":
            useKerning = true;    break;
        }
        
        symMark = Accent.NONE;
        switch(attributes.getNamedItem("symMark").getNodeValue()) {
        case "NONE":
            symMark = Accent.NONE;      break;
        case "DOT_ABOVE":
            symMark = Accent.DOT;       break;
        case "RING_ABOVE":
            symMark = Accent.RING;     break;
        case "TILDE":
            symMark = Accent.TILDE;     break;
        case "CARON":
        case "SIDE":
        case "COLON":
        case "GRAVE_ACCENT":
        case "ACUTE_ACCENT":
        case "CIRCUMFLEX":
        case "MACRON":
        case "HOOK_ABOVE":
        case "DOT_BELOW":
        default:
            symMark = Accent.NONE;
        }
        
        numStr = attributes.getNamedItem("borderFillIDRef").getNodeValue();
        borderFillIDRef = (short)Integer.parseInt(numStr);
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hh:fontRef":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("hangul").getNodeValue();
                    short fontID = (short)Integer.parseInt(numStr);
                    fontName[Lang.HANGUL.num]     = ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
                    numStr = childAttrs.getNamedItem("latin").getNodeValue();
                    fontID = (short)Integer.parseInt(numStr);
                    fontName[Lang.LATIN.num]     = ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
                    numStr = childAttrs.getNamedItem("hanja").getNodeValue();
                    fontID = (short)Integer.parseInt(numStr);
                    fontName[Lang.HANJA.num]     = ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
                    numStr = childAttrs.getNamedItem("japanese").getNodeValue();
                    fontID = (short)Integer.parseInt(numStr);
                    fontName[Lang.JAPANESE.num]     = ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
                    numStr = childAttrs.getNamedItem("other").getNodeValue();
                    fontID = (short)Integer.parseInt(numStr);
                    fontName[Lang.OTHER.num]     = ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
                    numStr = childAttrs.getNamedItem("symbol").getNodeValue();
                    fontID = (short)Integer.parseInt(numStr);
                    fontName[Lang.SYMBOL.num]     = ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
                    numStr = childAttrs.getNamedItem("user").getNodeValue();
                    fontID = (short)Integer.parseInt(numStr);
                    fontName[Lang.USER.num]     = ((HwpRecord_FaceName)parent.faceNameList.get(fontID)).faceName;
                }
                break;
            case "hh:ratio":
                {
                    ratio[Lang.HANGUL.num] = 100;   ratio[Lang.LATIN.num] = 100;
                    ratio[Lang.HANJA.num] = 100;    ratio[Lang.JAPANESE.num] = 100;
                    ratio[Lang.OTHER.num] = 100;    ratio[Lang.SYMBOL.num] = 100;   ratio[Lang.USER.num] = 100;
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("hangul").getNodeValue();
                    ratio[Lang.HANGUL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("latin").getNodeValue();
                    ratio[Lang.LATIN.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("hanja").getNodeValue();
                    ratio[Lang.HANJA.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("japanese").getNodeValue();
                    ratio[Lang.JAPANESE.num]    = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("other").getNodeValue();
                    ratio[Lang.OTHER.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("symbol").getNodeValue();
                    ratio[Lang.SYMBOL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("user").getNodeValue();
                    ratio[Lang.USER.num]        = (short) Integer.parseInt(numStr);
                }
                break;
            case "hh:spacing":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("hangul").getNodeValue();
                    spacing[Lang.HANGUL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("latin").getNodeValue();
                    spacing[Lang.LATIN.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("hanja").getNodeValue();
                    spacing[Lang.HANJA.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("japanese").getNodeValue();
                    spacing[Lang.JAPANESE.num]    = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("other").getNodeValue();
                    spacing[Lang.OTHER.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("symbol").getNodeValue();
                    spacing[Lang.SYMBOL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("user").getNodeValue();
                    spacing[Lang.USER.num]        = (short) Integer.parseInt(numStr);
                }
                break;
            case "hh:relSz":
                {
                    relSize[Lang.HANGUL.num] = 100;   relSize[Lang.LATIN.num] = 100;
                    relSize[Lang.HANJA.num] = 100;    relSize[Lang.JAPANESE.num] = 100;
                    relSize[Lang.OTHER.num] = 100;    relSize[Lang.SYMBOL.num] = 100;   relSize[Lang.USER.num] = 100;
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("hangul").getNodeValue();
                    relSize[Lang.HANGUL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("latin").getNodeValue();
                    relSize[Lang.LATIN.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("hanja").getNodeValue();
                    relSize[Lang.HANJA.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("japanese").getNodeValue();
                    relSize[Lang.JAPANESE.num]    = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("other").getNodeValue();
                    relSize[Lang.OTHER.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("symbol").getNodeValue();
                    relSize[Lang.SYMBOL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("user").getNodeValue();
                    relSize[Lang.USER.num]        = (short) Integer.parseInt(numStr);
                }
                break;
            case "hh:offset":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("hangul").getNodeValue();
                    charOffset[Lang.HANGUL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("latin").getNodeValue();
                    charOffset[Lang.LATIN.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("hanja").getNodeValue();
                    charOffset[Lang.HANJA.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("japanese").getNodeValue();
                    charOffset[Lang.JAPANESE.num]    = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("other").getNodeValue();
                    charOffset[Lang.OTHER.num]       = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("symbol").getNodeValue();
                    charOffset[Lang.SYMBOL.num]      = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("user").getNodeValue();
                    charOffset[Lang.USER.num]        = (short) Integer.parseInt(numStr);
                }
                break;
            case "hh:underline":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    underline = Underline.valueOf(childAttrs.getNamedItem("type").getNodeValue());
                    
                    underlineShape = LineType2.valueOf(childAttrs.getNamedItem("shape").getNodeValue());
                    
                    numStr = childAttrs.getNamedItem("color").getNodeValue().replaceAll("#", "");
                    underlineColor = (short)Integer.parseInt(numStr, 16);
                }
                break;
            case "hh:strikeout":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    strikeOutShape = LineType2.valueOf(childAttrs.getNamedItem("shape").getNodeValue());
                    
                    // numStr = childAttrs.getNamedItem("color").getNodeValue().replaceAll("#", "");
                    // strikeoutColor = (short)Integer.parseInt(numStr, 16);
                }
                break;
            case "hh:outline":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    outline = Outline.valueOf(childAttrs.getNamedItem("type").getNodeValue());
                }
                break;
            case "hh:shadow":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    switch(childAttrs.getNamedItem("type").getNodeValue()) {
                    case "NONE":
                        shadow = Shadow.NONE;       break;
                    case "DROP":
                        shadow = Shadow.DISCRETE;   break;
                    case "CONTINUOUS":
                        shadow = Shadow.CONTINUOUS; break;
                    }
                    numStr = childAttrs.getNamedItem("color").getNodeValue().replaceAll("#", "");
                    shadowColor = (int)Integer.parseInt(numStr, 16);
                    
                    numStr = childAttrs.getNamedItem("offsetX").getNodeValue();
                    shadowOffsetX = (byte)Integer.parseInt(numStr);

                    numStr = childAttrs.getNamedItem("offsetY").getNodeValue();
                    shadowOffsetY = (byte)Integer.parseInt(numStr);
                    
                    // numStr = childAttrs.getNamedItem("alpha").getNodeValue();
                    // shadowOffsetY = (byte)Integer.parseInt(numStr);
                }
                break;
            case "hh:italic":
                italic = true;
                break;
            case "hh:bold":
                bold = true;
                break;
            case "hh:emboss":
                emboss = true;
                break; 
            case "hh:engrave":
                engrave = true;
                break;
            case "hh:supscript":
                superScript = true;
                break;
            case "hh:subscript":
                subScript = true;
                break;
            }
        }
    }

    public static enum Lang {
		HANGUL		(0x0),
		LATIN		(0x1),
		HANJA		(0x2),
		JAPANESE	(0x3),
		OTHER		(0x4),
		SYMBOL		(0x5),
		USER		(0x6),
		MAX			(0x7);
		
		private int num;
	    private Lang(int num) { 
	    	this.num = num;
	    }
	    public static Lang from(int num) {
	    	for (Lang langNum: values()) {
	    		if (langNum.num == num)
	    			return langNum;
	    	}
	    	return HANGUL;
	    }
	}

	public static enum Underline {
		NONE		(0x0),	// 없음
		BOTTOM		(0x1),	// 글자 아래
		CENTER		(0x2),	// 글자 중간
		TOP			(0x3);	// 글자 위
		
		private int num;
	    private Underline(int num) { 
	    	this.num = num;
	    }
	    public static Underline from(int num) {
	    	for (Underline underline: values()) {
	    		if (underline.num == num)
	    			return underline;
	    	}
	    	return NONE;
	    }
	}

	public static enum Outline {
		NONE		(0x0),	// 없음
		SOLID		(0x1),	// 실선
		DOTTED		(0x2),	// 점선
		BOLD		(0x3),	// 굵은 실선(두꺼운 선)
		DASHED		(0x4),	// 파선(긴 점선)
		DASH_DOT	(0x5),	// 일점쇄선(-.-.-.-)
		DASH_2DOT	(0x6);	// 이점쇄선(-..-..-)
		
		private int num;
	    private Outline(int num) { 
	    	this.num = num;
	    }
	    public static Outline from(int num) {
	    	for (Outline outline: values()) {
	    		if (outline.num == num)
	    			return outline;
	    	}
	    	return NONE;
	    }
	}
	
	public static enum Shadow {
		NONE		(0x0),	// 없음
		DISCRETE	(0x1),	// 비연속 -> 나중에 DROP으로 바꾸자.
		CONTINUOUS	(0x2);	// 연속
		
		private int num;
	    private Shadow(int num) { 
	    	this.num = num;
	    }
	    public static Shadow from(int num) {
	    	for (Shadow shadow: values()) {
	    		if (shadow.num == num)
	    			return shadow;
	    	}
	    	return NONE;
	    }
	}

	public static enum Accent {
		NONE		(0x0),	// 없음
		DOT			(0x1),	// 검정 동그라미 강조점
		RING		(0x2),	// 속 빈 동그라미 강조점
		CARON		(0x3),	// V
		TILDE		(0x4),	// ~
		ARAEA		(0x5),	// ㆍ
		TWOARAEA	(0x6);	// :
		
		private int num;
	    private Accent(int num) { 
	    	this.num = num;
	    }
	    public static Accent from(int num) {
	    	for (Accent accent: values()) {
	    		if (accent.num == num)
	    			return accent;
	    	}
	    	return NONE;
	    }
	}
	


}
