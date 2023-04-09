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

import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecord_IdMapping.Index;

public class HwpRecord_ParaShape extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_ParaShape.class.getName());
	private HwpDocInfo	parent;

	public HorizontalAlign align;								// 정렬방식 (0:양쪽정렬, 1:왼쪽정렬, 2:오른쪽정렬, 3:가운데정렬, 4:배분정렬, 5:나눔 정렬
	public byte			breakLatinWord;							// 줄 나눔 기준 영어 단위 (0:단어, 1:하이픈, 2:글자)
	public byte			breakNonLatinWord;						// 줄 나눔 기준 한글 단위 (0:어절, 1:글자)
	public boolean		snapToGrid;								// 편집 용지의 줄 격자 사용 여부
	public byte			condense;								// 공백 최소값 (0%~75%)
	public boolean 		widowOrphan;							// 외톨이줄 보호 여부
	public boolean 		keepWithNext;							// 다음 문단과 함께 여부
	public boolean 		pageBreakBefore;						// 문단 앞에서 항상 쪽 나눔 여부
	public VerticalAlign   vertAlign;							// 세로정렬 (0:글꼴기준, 1:위쪽, 2:가운데, 3:아래)
	public boolean		fontLineHeight;							// 글꼴에 어울리는 줄 높이 여부
	public HeadingType	headingType;							// 문단 머리 모양 종류 (0:없음, 1:개요, 2:번호, 3:글머리표(bullet))
	
	public byte			headingLevel;							// 문단 수준 (1수준~7수준)
	public boolean		connect;								// 문단 테두리 연결 여부
	public boolean		ignoreMargin;							// 문단 여백 무시 여부
	public boolean 		paraTailShape;							// 문단 꼬리 모양
	
	public int			indent;									// 들여쓰기/내어쓰기.
	public int			marginLeft;								// 왼쪽 여백		HWPUINT형이 아닌  INT32형이다. 7000값을 35pt, 12.3mm로 계산한다.
	public int			marginRight;							// 오른쪽 여백		HWPUINT형이 아닌  INT32형이다. 7000값을 35pt, 12.3mm로 계산한다.
	public int			marginPrev;								// 문단 간격 위		HWPUINT형이 아닌  INT32형이다. 7000값을 35pt, 12.3mm로 계산한다.
	public int			marginNext;								// 문단 간격 아래	HWPUINT형이 아닌  INT32형이다. 7000값을 35pt, 12.3mm로 계산한다.
	public int			lineSpacing;							// 줄 간격. 한글2007 이하버전(5.0.2.5 버전 미만)에서 사용.
																// 		percent일때:0%~500%, fixed일때:hpwunit또는 글자수,betweenline일때:hwpunit또는글자수  
	
	public short		tabDef;									// 탭 정의 아이디(TabDef ID) 참조 값
	public short		headingIdRef;    						// 번호 문단 ID(Numbering ID) 또는 글머리표 문단 모양 ID(Bullet ID)참조 값
	public short		borderFill;								// 테두리/배경 모양 ID(BorderFill ID) 참조 값
	public short		offsetLeft;								// 문단 테두리 왼쪽 간격
	public short		offsetRight;							// 문단 테두리 오른쪽 간격
	public short		offsetTop;								// 문단 테두리 위쪽 간격
	public short		offsetBottom;							// 문단 테두리 아래쪽 간격
																// 속성2 (5.0.1.7 버전 이상)
	public byte			lineWrap;								// 		한줄로 입력
	public boolean		autoSpaceEAsianEng;						// 		한글과 영어 간격을 자동 조절
	public boolean		autoSpaceEAsianNum;						// 		한글과 숫자 간격을 자동 조절
																// 속성3 (5.0.2.5 버전 이상)
	public int			lineSpacingType;						// 		줄간격 종류(0:Percent,1:Fixed,2:BetweenLines,4:AtLeast)
	
	HwpRecord_ParaShape(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public HwpRecord_ParaShape(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		super(tagNum, level, size);
		this.parent = docInfo;
		
		int offset = off;
		int typeBits 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		align				= HorizontalAlign.from(typeBits>>>2 & 0x07);
		breakLatinWord	 	= (byte) (typeBits>>>5 & 0x03);
		breakNonLatinWord 	= (byte) (typeBits>>>7 & 0x01);
		snapToGrid			= (typeBits&0x80)==0x80?true:false;
		condense			= (byte) (typeBits>>>9 & 0x7F);
		widowOrphan		 	= (typeBits&0x10000)==0x10000?true:false;
		keepWithNext		= (typeBits&0x20000)==0x20000?true:false;
		pageBreakBefore		= (typeBits&0x40000)==0x40000?true:false;
		vertAlign			= VerticalAlign.from(typeBits>>>20 & 0x03);
		fontLineHeight		= (typeBits&0x100000)==0x100000?true:false;
		headingType			= HeadingType.from((typeBits>>>23 & 0x03));
		headingLevel		= (byte) (typeBits>>>25 & 0x07);
		connect				= (typeBits&0x800000)==0x800000?true:false;
		ignoreMargin		= (typeBits&0x1000000)==0x1000000?true:false;
		paraTailShape		= (typeBits&0x2000000)==0x2000000?true:false;
		
		marginLeft 			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		marginRight			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		indent 				= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		marginPrev 			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		marginNext	 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		if (version<5025) {
		    lineSpacingType = (byte) (typeBits & 0x03);
			lineSpacing 	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
		} else {
			offset += 4;
		}
		tabDef				= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		headingIdRef		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		borderFill			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		offsetLeft			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		offsetRight			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		offsetTop			= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		offsetBottom		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		
		if (version>=5017) {
			int attrBits		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
			lineWrap			= (byte) (attrBits & 0x03);
			autoSpaceEAsianEng	=(typeBits&0x10)==0x10?true:false;
			autoSpaceEAsianNum	=(typeBits&0x20)==0x20?true:false;
		} else {
			offset += 4;
		}
		
		if (version>=5025) {
			int attrBits		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
			lineSpacingType 	= (byte) (attrBits&0x0F);
			lineSpacing			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
		} else {
			offset += 8;
		}

		log.fine("                                                  "
				+"ID="+(parent.paraShapeList.size())
				+",정렬="+align+",줄나눔="+breakNonLatinWord
				+",문단margin=("+marginLeft+","+marginRight+","+marginPrev+","+marginNext+")"
				+",문단offset=("+offsetLeft+","+offsetRight+","+offsetTop+","+offsetBottom+")"
				+",줄간격="+lineSpacingType+":"+lineSpacing
				+","+headingType+"(ID="+headingIdRef+",Level="+headingLevel+")"
				+",탭ID="+tabDef
				+",테두리ID="+borderFill
				);
		
		if (offset-off-size != 0 && offset-off!=54) {
			log.fine("[TAG]=" + tag.toString() + ", size=" + size + ", but currentSize=" + (offset-off));
			dump(buf, off, size);
			throw new HwpParseException();
		}
	}

	
	public HwpRecord_ParaShape(HwpDocInfo docInfo, Node node, int version) throws NotImplementedException {
        super(HwpTag.HWPTAG_PARA_SHAPE, 0, 0);
        this.parent = docInfo;
        
        dumpNode(node, 1);
        
        NamedNodeMap attributes = node.getAttributes();
        
        // id값은 처리하지 않는다. List<HwpRecord_ParaShape>에 순차적으로 추가한다.
        // String id = attributes.getNamedItem("id").getNodeValue();
        
        String numStr = attributes.getNamedItem("tabPrIDRef").getNodeValue();
        tabDef = (short) Integer.parseInt(numStr);
        
        numStr = attributes.getNamedItem("condense").getNodeValue();
        condense = (byte) Integer.parseInt(numStr);
        
        switch(attributes.getNamedItem("fontLineHeight").getNodeValue()) {
        case "0":
            fontLineHeight = false; break;
        case "1":
            fontLineHeight = true;  break;
        }
        
        switch(attributes.getNamedItem("snapToGrid").getNodeValue()) {
        case "0":
            snapToGrid = false; break;
        case "1":
            snapToGrid = true;  break;
        }

        /*
        switch(attributes.getNamedItem("suppressLineNumbers").getNodeValue()) {
        case "0":
            suppressLineNumbers = false; break;
        case "1":
            suppressLineNumbers = true;  break;
        }
        */
        /*
        switch(attributes.getNamedItem("checked").getNodeValue()) {
        case "0":
            checked = false; break;
        case "1":
            checked = true;  break;
        }
        */
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            recursive_ParaShape(child);
        }
        
    }
	
	private void recursive_ParaShape(Node child) throws NotImplementedException {
        String numStr;
        
        switch(child.getNodeName()) {
        case "hh:align":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                align = HorizontalAlign.valueOf(childAttrs.getNamedItem("horizontal").getNodeValue());
                vertAlign = VerticalAlign.valueOf(childAttrs.getNamedItem("vertical").getNodeValue());
            }
            break;
        case "hh:heading":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                headingType = HeadingType.valueOf(childAttrs.getNamedItem("type").getNodeValue());
                
                numStr = childAttrs.getNamedItem("idRef").getNodeValue();
                headingIdRef = (short)Integer.parseInt(numStr);
    
                numStr = childAttrs.getNamedItem("level").getNodeValue();
                headingLevel = (byte)Integer.parseInt(numStr);
            }
            break;
        case "hh:breakSetting":
            {
                // keepLines=0,lineWrap=BREAK,pageBreakBefore=0]
                NamedNodeMap childAttrs = child.getAttributes();
                switch(childAttrs.getNamedItem("breakLatinWord").getNodeValue()) {
                case "KEEP_WORD":
                    breakLatinWord = 0; break;     // 0:단어,1:하이픈,2:글자
                default:
                    throw new NotImplementedException("HwpRecord_ParaShape");
                }
    
                switch(childAttrs.getNamedItem("breakNonLatinWord").getNodeValue()) {
                case "KEEP_WORD":
                    breakNonLatinWord = 0; break;     // 0:어절,1:글자
                case "BREAK_WORD":
                    breakNonLatinWord = 1; break; 
                default:
                    throw new NotImplementedException("HwpRecord_ParaShape");
                }
    
                switch(childAttrs.getNamedItem("widowOrphan").getNodeValue()) {
                case "0":
                    widowOrphan = false;    break;
                case "1":
                    widowOrphan = true;    break;
                }
                
                switch(childAttrs.getNamedItem("keepWithNext").getNodeValue()) {
                case "0":
                    keepWithNext = false;    break;
                case "1":
                    keepWithNext = true;    break;
                }
    
                /*
                switch(childAttrs.getNamedItem("keepLines").getNodeValue()) {
                case "0":
                    keepLines = false;    break;
                case "1":
                    keepLines = true;    break;
                }
                */
                switch(childAttrs.getNamedItem("pageBreakBefore").getNodeValue()) {
                case "0":
                    pageBreakBefore = false;    break;
                case "1":
                    pageBreakBefore = true;    break;
                }
    
                switch(childAttrs.getNamedItem("lineWrap").getNodeValue()) {
                case "BREAK":
                    lineWrap = 0;    break;
                default:
                    throw new NotImplementedException("HwpRecord_ParaShape");
                }
            }
            break;
        case "hh:lineSpacing":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                switch(childAttrs.getNamedItem("type").getNodeValue()) {
                case "PERCENT":
                    lineSpacingType = 0; break;
                case "FIXED":
                    lineSpacingType = 1; break;
                case "BETWEENLINES":
                    lineSpacingType = 2; break;
                case "ATLEAST":
                    lineSpacingType = 4; break;
                default:
                    throw new NotImplementedException("HwpRecord_ParaShape");
                }
                
                numStr = childAttrs.getNamedItem("value").getNodeValue();
                lineSpacing = Integer.parseInt(numStr);
            }
            break;
        case "hh:border":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                numStr = childAttrs.getNamedItem("borderFillIDRef").getNodeValue();
                borderFill = (short) Integer.parseInt(numStr);

                numStr = childAttrs.getNamedItem("offsetLeft").getNodeValue();
                offsetLeft = (short) Integer.parseInt(numStr);

                numStr = childAttrs.getNamedItem("offsetRight").getNodeValue();
                offsetRight = (short) Integer.parseInt(numStr);

                numStr = childAttrs.getNamedItem("offsetTop").getNodeValue();
                offsetTop = (short) Integer.parseInt(numStr);

                numStr = childAttrs.getNamedItem("offsetBottom").getNodeValue();
                offsetBottom = (short) Integer.parseInt(numStr);

                switch(childAttrs.getNamedItem("connect").getNodeValue()) {
                case "0":
                    connect = false; break;
                case "1":
                    connect = true;  break;
                }
                
                switch(childAttrs.getNamedItem("ignoreMargin").getNodeValue()) {
                case "0":
                    ignoreMargin = false; break;
                case "1":
                    ignoreMargin = true;  break;
                }
            }
            break;
        case "hh:autoSpacing":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                switch(childAttrs.getNamedItem("eAsianEng").getNodeValue()) {
                case "0":
                    autoSpaceEAsianEng = false; break;
                case "1":
                    autoSpaceEAsianEng = true;  break;
                }
                
                switch(childAttrs.getNamedItem("eAsianNum").getNodeValue()) {
                case "0":
                    autoSpaceEAsianNum = false; break;
                case "1":
                    autoSpaceEAsianNum = true;  break;
                }
            }
            break;
        case "hc:intent":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                numStr = childAttrs.getNamedItem("value").getNodeValue();
                indent = Integer.parseInt(numStr);
            }
            break;
        case "hc:left":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                numStr = childAttrs.getNamedItem("value").getNodeValue();
                marginLeft = Integer.parseInt(numStr);
            }
            break;
        case "hc:right":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                numStr = childAttrs.getNamedItem("value").getNodeValue();
                marginRight = Integer.parseInt(numStr);
            }
            break;
        case "hc:prev":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                numStr = childAttrs.getNamedItem("value").getNodeValue();
                marginPrev = Integer.parseInt(numStr);
            }
            break;
        case "hc:next":
            {
                NamedNodeMap childAttrs = child.getAttributes();
                numStr = childAttrs.getNamedItem("value").getNodeValue();
                marginNext = Integer.parseInt(numStr);
            }
            break;
        case "hp:switch":
        case "hp:case":
        case "hp:default":
        case "hh:margin":
            {
                NodeList childNodeList = child.getChildNodes();
                for (int i=0; i<childNodeList.getLength(); i++) {
                    Node grandChild = childNodeList.item(i);
                    recursive_ParaShape(grandChild);
                }
            }
            break;
        }
	}

    public static enum HeadingType {
		NONE		(0),	// 없음
		OUTLINE		(1),	// 개요
		NUMBER		(2),	// 번호
		BULLET		(3);	// 글머리표

		private int num;
		
	    private HeadingType(int num) { 
	    	this.num = num;
	    }

	    public static HeadingType from(int num) {
	    	for (HeadingType type: values()) {
	    		if (type.num == num)
	    			return type;
	    	}
	    	return null;
	    }
	}
    
    public static enum HorizontalAlign {
        JUSTIFY          (0),   // 양쪽 정렬
        LEFT             (1),   // 왼쪽 정렬
        RIGHT            (2),   // 오른쪽 정렬
        CENTER           (3),   // 가운데 정렬
        DISTRIBUTE       (4),   // 배분 정렬
        DISTRIBUTE_SPACE (5);   // 나눔 정렬
        
        private int num;
        
        private HorizontalAlign(int num) { 
            this.num = num;
        }

        public static HorizontalAlign from(int num) {
            for (HorizontalAlign type: values()) {
                if (type.num == num)
                    return type;
            }
            return JUSTIFY;
        }
    }
    
    public static enum VerticalAlign {
        BASELINE    (0),    // 글꼴 기준
        TOP         (1),    // 위쪽
        CENTER      (2),    // 가운데
        BOTTOM      (3);    // 아래

        private int num;
        
        private VerticalAlign(int num) { 
            this.num = num;
        }

        public static VerticalAlign from(int num) {
            for (VerticalAlign type: values()) {
                if (type.num == num)
                    return type;
            }
            return BASELINE;
        }
    }
    
    
}
