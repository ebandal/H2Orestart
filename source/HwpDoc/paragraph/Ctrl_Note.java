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
package HwpDoc.paragraph;

import java.util.List;
import java.util.logging.Level;
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
                        	if (log.isLoggable(Level.FINE)) {
                        		throw new NotImplementedException("Ctrl_Note");
                        	}
                        	break;
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
