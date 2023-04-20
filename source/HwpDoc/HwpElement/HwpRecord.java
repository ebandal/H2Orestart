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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord.class.getName());

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	HwpTag tag;
	int level;
	int size;
	
	HwpRecord(int tagNum, int level, int size) {
		this(HwpTag.from(tagNum), level, size);
	}
	
	HwpRecord(HwpTag tag, int level, int size) {
		this.tag = tag;
		this.level = level;
		this.size = size;
	}
	
	public static void dump(byte[] buf, int off, int size) {
		int offset = off;
		while(offset < off+size) {
		    char[] hexChars = new char[16 * 2];
		    for (int j=0; j<16 && offset+j<buf.length; j++) {
		        int v = buf[offset+j] & 0xFF;
		        hexChars[j*2] = HEX_ARRAY[v >>> 4];
		        hexChars[j*2+1] = HEX_ARRAY[v & 0x0F];
		    }
		    offset += 16;
			log.finer(new String(hexChars));
		}
	}

	public static void dump(String str) {
		log.finer(str);
	}
	
	public static void dumpNode(Node node, int depth) {
	    NamedNodeMap attributes = node.getAttributes();
	    if (attributes != null) {
	        StringBuffer sb = new StringBuffer();
    	    for (int i=0; i<attributes.getLength(); i++) {
    	        Node attrNode = attributes.item(i);
    	        sb.append(attrNode.getNodeName() + "=" + attrNode.getNodeValue()+",");
    	    }
    	    log.info(IntStream.range(0,depth).mapToObj(i -> " ").collect(Collectors.joining()) + node.getNodeName()+"="+node.getNodeValue()+",[" + sb.toString() + "]");
	    }
	    
	    NodeList children = node.getChildNodes();
	    if (children != null) {
    	    for (int i=0; i<children.getLength(); i++) {
    	        dumpNode(children.item(i), depth+1);
    	    }
	    }
	}

	public static String toHexString(byte[] buf) {
		char[] hexChars = new char[buf.length*2];
		for (int i=0;i<buf.length;i++) {
	        int v = buf[i] & 0xFF;
	        hexChars[i*2] = HEX_ARRAY[v >>> 4];
	        hexChars[i*2+1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

}
