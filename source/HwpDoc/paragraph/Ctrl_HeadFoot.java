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

import java.util.List;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecordTypes.LineType2;

public class Ctrl_HeadFoot extends Ctrl {
	private static final Logger log = Logger.getLogger(Ctrl_HeadFoot.class.getName());

	private int 		size;
	public 	boolean		isHeader;
	public 	int 		attr;
	public PageRange	whichPage;
	public int	   		serialInSec;
	
	public int textWidth;
	public int textHeight;
	public byte refLevelText;
	public byte refLevelNum;
	
	public List<HwpParagraph> paras;
	
	public Ctrl_HeadFoot(String ctrlId) {
	    super(ctrlId);
	}
	
	public Ctrl_HeadFoot(String ctrlId, int size, byte[] buf, int off, int version, boolean isHeader) {
		super(ctrlId);
		
		int offset = off;
		this.isHeader = isHeader;
		
		// 속성(표 141참조) (머리말이 적용 0:양쪽, 1:짝수쪽만, 2:홀수쪽만)
		attr			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		whichPage		= PageRange.from(attr);
		
		serialInSec		=  buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
				
		log.fine("                                                  " + toString());

		this.size = offset-off;
	}
	
    public static int parseListHeaderAppend(Ctrl_HeadFoot obj, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
		int offset = off;
		offset += 2;
		
		// 텍스트 영역의 폭  (1/7200inch로 계산하는가?)
		obj.textWidth		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		// 텍스트 영역의 높이  (1/7200inch로 계산하는가?)
		obj.textHeight		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		// 각 비트가 해당 레벨의 텍스트에 대한 참조를 했는지 여부
		obj.refLevelText	= buf[offset++];
		// 각 비트가 해당 레벨의 번호에 대한 참조를 했는지 여부
		obj.refLevelNum		= buf[offset++];
		
		if (size-(offset-off)>0) {
			log.fine("                                                  [CtrlId]=" + obj.ctrlId + "," + size + " bytes를 해석하지 못함.");
			offset += (size-(offset-off));
		}
        obj.fullfilled = true;
		
		return offset-off;
	}

    public Ctrl_HeadFoot(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId);
        
        if (ctrlId.equals("daeh")) {
            isHeader = true;
        } else {
            isHeader = false;
        }

        NamedNodeMap attributes = node.getAttributes();
        
        // id는 처리하지 않는다. List<Ctrl_HeadFoot>에 순차적으로 추가한다. 
        // String id = attributes.getNamedItem("id").getNodeValue();

        whichPage = PageRange.valueOf(attributes.getNamedItem("applyPageType").getNodeValue());
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hp:subList":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    // [hasNumRef="0", hasTextRef="0", id="", lineWrap="BREAK", linkListIDRef="0", 
                    // linkListNextIDRef="0", textDirection="HORIZONTAL", textHeight="7087", 
                    // textWidth="45354", vertAlign="TOP"]
                    for (int j=0; j<childAttrs.getLength(); j++) {
                    	Node childNodeAttr = childAttrs.item(j);
                    	switch(childNodeAttr.getNodeName()) {
                    	case "hasNumRef":
                    		break;
                    	case "hasTextRef":
                    		break;
                    	case "id":
                    		break;
                    	case "lineWrap":
                    		break;
                    	case "linkListIDRef":
                    		break;
                    	case "linkListNextIDRef":
                    		break;
                    	case "textDirection":
                    		break;
                    	case "textHeight":
                    		break;
                    	case "textWidth":
                    		break;
                    	case "vertAlign":
                    		break;
                        default:
                        	log.warning(childNodeAttr.getNodeName() + ":" + childNodeAttr.getNodeValue());
                            throw new NotImplementedException("Ctrl_HeadFoot");
                    	}
                    }
                    
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        // [columnBreak="0", id="2147483648", merged="0", pageBreak="0", paraPrIDRef="47", styleIDRef="1"]
                        switch(grandChild.getNodeName()) {
                        case "columnBreak":
                            break;
                        case "id":
                            break;
                        case "merged":
                            break;
                        case "pageBreak":
                            break;
                        case "paraPrIDRef":
                            break;
                        case "styleIDRef":
                            break;
                        case "hp:p":
                        	break;
                        default:
                        	log.warning(grandChild.getNodeName() + ":" + grandChild.getNodeValue());
                            throw new NotImplementedException("Ctrl_HeadFoot");
                        }
                    }
                }
                break;
            default:
            	log.warning(child.getNodeName() + ":" + child.getNodeValue());
                throw new NotImplementedException("Ctrl_HeadFoot");
            }
        }
        this.fullfilled = true;
    }


	public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("CTRL("+ctrlId+")")
			.append("=속성:"+whichPage.toString())
			.append(",텍스트폭:"+textWidth)
			.append(",텍스트높이:"+textHeight)
			.append(",레벨의텍스트참조여부:"+refLevelText)
			.append(",레벨의번호참조여부:"+refLevelNum);
		return strb.toString();
	}
	
	public int getSize() {
		return size;
	}
	
	public static enum PageRange {
		BOTH		(0),	// 양쪽 
		EVEN		(1),	// 짝수쪽
		ODD			(2);	// 홀수쪽

		private int range;
		
	    private PageRange(int range) { 
	    	this.range = range;
	    }

	    public static PageRange from(int range) {
	    	for (PageRange typeNum: values()) {
	    		if (typeNum.range == range)
	    			return typeNum;
	    	}
	    	return null;
	    }
	}
}
