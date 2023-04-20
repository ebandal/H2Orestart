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

import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecordTypes.NumberShape2;

public class Ctrl_AutoNumber extends Ctrl {
	private static final Logger log = Logger.getLogger(Ctrl_AutoNumber.class.getName());
	private int size;
	
	public NumType		numType;
	public NumberShape2	numShape;
	public boolean		superscript;
	
    public Ctrl_AutoNumber(String ctrlId) {
        super(ctrlId);
    }
	
	public Ctrl_AutoNumber(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId);

		int offset = off;

		int attr 	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		numType 	= NumType.from(attr&0xF);
		numShape	= NumberShape2.from(attr>>4&0xFF);
		superscript = (attr>>12&0x1)==0x1?true:false;
		
		log.fine("                                                  " + toString());
		this.size = offset-off;
		this.fullfilled = true;
	}
	
	public Ctrl_AutoNumber(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId);
        
        NamedNodeMap attributes = node.getAttributes();
        numType = NumType.valueOf(attributes.getNamedItem("numType").getNodeValue());

        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "autoNumFormat":
                numShape = NumberShape2.valueOf(child.getNodeValue());
                break;
            default:
                throw new NotImplementedException("Ctrl_AutoNumber");
            }
        }
        this.fullfilled = true;
    }

    public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("CTRL("+ctrlId+")")
			.append("=공통속성:"+super.toString());
		return strb.toString();
	}

	@Override
	public int getSize() {
		return this.size;
	}

	public static enum NumType {
		PAGE		(0x0),	// 쪽번호
		FOOTNOTE	(0x1),	// 각주번호
		ENDNOTE		(0x2),	// 미주번호
		FIGURE		(0x3),	// 그림번호
		TABLE		(0x4),	// 표번호
		EQUATION	(0x5);	// 수식번호
		
		private int num;
	    private NumType(int num) { 
	    	this.num = num;
	    }
	    public static NumType from(int num) {
	    	for (NumType type: values()) {
	    		if (type.num == num)
	    			return type;
	    	}
	    	return null;
	    }
	}
}
