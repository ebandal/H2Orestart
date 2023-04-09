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
package HwpDoc.paragraph;

import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapeTextArt extends Ctrl_GeneralShape {
	private static final Logger log = Logger.getLogger(Ctrl_ShapeTextArt.class.getName());
	private int size;
	
	public String  text;       // 글맵시 내용
	public Point   pt0;        // 첫번째 좌표
	public Point   pt1;        // 두번째 좌표
	public Point   pt2;        // 세번째 좌표
	public Point   pt3;        // 네번째 좌표
	                           // 글맵시 모양 정보
	public String  fontName;       // 글꼴이름
	public String  fontStyle;      // 글꼴 스타일
	public String  fontType;       // 글꼴 형식
	public String  textShape;      // 글맵시 모양
	public short   lineSpacing;    // 줄 간격
	public short   spacing;        // 글자 간격
	public String  align;          // 정렬 방식
	
	public Point[] outline;    // 외곽선 정보
	
	public Ctrl_ShapeTextArt(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId, size, buf, off, version);
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}
	
	public Ctrl_ShapeTextArt(Ctrl_GeneralShape shape) {
		super(shape);
		
		this.size = shape.getSize();
	}

	public Ctrl_ShapeTextArt(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId, node, version);
        
        NamedNodeMap attributes = node.getAttributes();
        text = attributes.getNamedItem("text").getNodeValue();
        
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            NamedNodeMap childAttrs = child.getAttributes();
            switch(child.getNodeName()) {
            case "hp:pt0":
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                pt0.x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                pt0.y = Integer.parseInt(numStr);
                break;
            case "hp:pt1":
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                pt1.x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                pt1.y = Integer.parseInt(numStr);
                break;
            case "hp:pt2":
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                pt2.x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                pt2.y = Integer.parseInt(numStr);
                break;
            case "hp:pt3":
                numStr = childAttrs.getNamedItem("x").getNodeValue();
                pt3.x = Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("y").getNodeValue();
                pt3.y = Integer.parseInt(numStr);
                break;
            case "hp:textartPr":   // 글맵시 모양 정보
                fontName = childAttrs.getNamedItem("fontName").getNodeValue();
                fontStyle = childAttrs.getNamedItem("fontStyle").getNodeValue();
                fontType = childAttrs.getNamedItem("fontType").getNodeValue();
                textShape = childAttrs.getNamedItem("textShape").getNodeValue();
                fontStyle = childAttrs.getNamedItem("fontStyle").getNodeValue();
                numStr = childAttrs.getNamedItem("lineSpacing").getNodeValue();
                lineSpacing = (short) Integer.parseInt(numStr);
                numStr = childAttrs.getNamedItem("spacing").getNodeValue();
                spacing = (short) Integer.parseInt(numStr);
                align = childAttrs.getNamedItem("align").getNodeValue();
                /*
                 * [TODO] shadow 처리 미구현
                 */
                break;
            case "hp:outline":   // 외곽선 정보
                numStr = childAttrs.getNamedItem("cnt").getNodeValue();
                int cnt = Integer.parseInt(numStr);
                outline = new Point[cnt];
                
                NodeList childNodeList = child.getChildNodes();
                for (int j=0; j<childNodeList.getLength(); j++) {
                    Node grandChild = childNodeList.item(j);
                    switch(grandChild.getNodeName()) {
                    case "hp:pt":
                        outline[j] = new Point();
                        NamedNodeMap grandAttrs = grandChild.getAttributes();
                        numStr = grandAttrs.getNamedItem("x").getNodeValue();
                        outline[j].x = Integer.parseInt(numStr);
                        numStr = grandAttrs.getNamedItem("y").getNodeValue();
                        outline[j].y = Integer.parseInt(numStr);
                        break;
                    default:
                        throw new NotImplementedException("Ctrl_ShapeRect");
                    }
                }
                break;
            default:
                throw new NotImplementedException("Ctrl_ShapeRect");
            }
        }
    }
	
	public static int parseElement(Ctrl_ShapeTextArt obj, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;

        // [HWP ambiguous] following 120bytes are unknown.
        // Document doesn't mention about this at all.

        if (offset-off-size!=0) {
            log.fine("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
        }
        
        return size;
    }
    
    public static int parseCtrl(Ctrl_ShapeTextArt shape,  int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;
        int len = Ctrl_GeneralShape.parseCtrl(shape, size,  buf,  off,  version);
        offset += len;
        
        return offset-off;
    }

    public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("CTRL("+ctrlId+")")
			.append("=공통속성:"+super.toString());
		return strb.toString();
	}

	@Override
	public int getSize() {
		return size;
	}
}
