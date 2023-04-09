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

import HwpDoc.Exception.NotImplementedException;
import HwpDoc.paragraph.Ctrl_HeadFoot.PageRange;

public class Ctrl_Note extends Ctrl {
	private static final Logger log = Logger.getLogger(Ctrl_Note.class.getName());

	private int size;
	
	public List<HwpParagraph> paras;

    public Ctrl_Note(String ctrlId) {
        super(ctrlId);
    }

	public Ctrl_Note(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId);
		
		int offset = off;
		// 각주/미주는 문단 리스트 외에 속성을 갖지 않는다. 하지만 쓰레기 값이나 불필요한 업데이트를 줄이기 위해 8 byte를 serialize한다.
		// 도데체 무슨 말인지??? 8byte를 포함한다는 말인가?
		offset += 8;
		
		log.fine("                                                  " + toString());

		this.size = offset-off;
		this.fullfilled = true;

	}
	
	public Ctrl_Note(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId);
        
        // id는 처리하지 않는다. List<Ctrl_Note>에 순차적으로 추가한다.
        // String id = attributes.getNamedItem("id").getNodeValue();

        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "subList":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        switch(grandChild.getNodeName()) {
                        case "p":
                            HwpParagraph cellP = new HwpParagraph(grandChild, version);
                            paras.add(cellP);
                            break;
                        default:
                            throw new NotImplementedException("Ctrl_Note");
                        }
                    }
                }
            }
        }
        this.fullfilled = true;
    }


    public String toString() {
		return "CTRL("+ctrlId+")";
	}
	
	public int getSize() {
		return size;
	}
}
