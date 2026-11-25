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

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import HwpDoc.IContext;
import HwpDoc.HwpElement.HwpRecordTypes.NumberShape2;


public class Ctrl_PageNumPos extends Ctrl {
	private static final Logger log = Logger.getLogger(Ctrl_PageNumPos.class.getName());
	private int size;
	
	public NumPos 		pos;
	public NumberShape2	numShape;
	public String  		userDef;
	public String		prefix;
	public String		postfix;
	public String		constantDash;
	
    public Ctrl_PageNumPos(String ctrlId) {
        super(ctrlId);
    }

    public Ctrl_PageNumPos(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId);
		int offset 		= off;

		int attr 		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		numShape		= NumberShape2.from(attr&0xFF);
		pos 			= NumPos.from((attr>>8)&0xF);
		
		offset += 4;		// UINT32 속성 (기존 2: 이후 문자들이 한 칸씩 밀려 읽혔음)
		userDef 		= new String(buf, offset, 2, StandardCharsets.UTF_16LE);
		offset += 2;
		prefix 			= new String(buf, offset, 2, StandardCharsets.UTF_16LE);
		offset += 2;
		postfix 		= new String(buf, offset, 2, StandardCharsets.UTF_16LE);
		offset += 2;
		constantDash 	= new String(buf, offset, 2, StandardCharsets.UTF_16LE);
		offset += 2;
		
		// 값이 없으면 NUL 문자. HWPX 와 같이 빈 문자열로 둔다
		userDef = noNul(userDef);
		prefix = noNul(prefix);
		postfix = noNul(postfix);
		constantDash = noNul(constantDash);
		log.fine("                                                  " + toString());
		this.size = offset-off;
	}
	
	public Ctrl_PageNumPos(String ctrlId, Node node, int version, IContext context) {
	    super(ctrlId);
	    
        NamedNodeMap attributes = node.getAttributes();
        
        pos = NumPos.fromXml(attributes.getNamedItem("pos").getNodeValue());
        numShape = NumberShape2.fromXml(attributes.getNamedItem("formatType").getNodeValue());
        // 줄표 넣기 (예: "-" 이면 "- 1 -")
        Node sideChar = attributes.getNamedItem("sideChar");
        constantDash = sideChar==null ? "" : sideChar.getNodeValue();
        userDef = "";       // HWPX 에는 해당 속성이 없다
        prefix = "";
        postfix = "";
    }

    private static String noNul(String s) {
        return s==null ? "" : s.replace(String.valueOf((char) 0), "");
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
	
	public static enum NumPos {
		NONE			(0x0),	// 쪽 번호 없음
		LEFT_TOP		(0x1),	// 왼쪽 위
		CENTER_TOP		(0x2),	// 가운데 위
		RIGHT_TOP		(0x3),	// 오른쪽 위
		LEFT_BOTTOM		(0x4),	// 왼쪽 아래
		BOTTOM_CENTER	(0x5),	// 가운데 아래  [20230518]
		RIGHT_BOTTOM	(0x6),	// 오른쪽 아래
		OUTER_TOP		(0x7),	// 바깥쪽 위
		OUTER_BOTTOM	(0x8),	// 바깥쪽 아래
		INNER_TOP		(0x9),	// 안쪽 위
		INNER_BOTTOM	(0xA);	// 안쪽 아래
		
		private int num;
	    private NumPos(int num) { 
	    	this.num = num;
	    }
	    /** HWPX(OWPML) 의 pos 값 (TOP_LEFT, BOTTOM_RIGHT, OUTSIDE_BOTTOM ...) 을 변환한다. 알 수 없는 값은 NONE */
	    public static NumPos fromXml(String name) {
	        if (name == null) return NONE;
	        switch (name) {
	        case "TOP_LEFT":        return LEFT_TOP;
	        case "TOP_CENTER":      return CENTER_TOP;
	        case "TOP_RIGHT":       return RIGHT_TOP;
	        case "BOTTOM_LEFT":     return LEFT_BOTTOM;
	        case "BOTTOM_CENTER":   return BOTTOM_CENTER;
	        case "BOTTOM_RIGHT":    return RIGHT_BOTTOM;
	        case "OUTSIDE_TOP":     return OUTER_TOP;
	        case "OUTSIDE_BOTTOM":  return OUTER_BOTTOM;
	        case "INSIDE_TOP":      return INNER_TOP;
	        case "INSIDE_BOTTOM":   return INNER_BOTTOM;
	        default:
	            for (NumPos type: values()) {
	                if (type.name().equals(name)) return type;
	            }
	            return NONE;
	        }
	    }

	    public static NumPos from(int num) {
	    	for (NumPos type: values()) {
	    		if (type.num == num)
	    			return type;
	    	}
	    	return null;
	    }
	}

}
