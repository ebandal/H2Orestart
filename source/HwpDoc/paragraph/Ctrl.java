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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Node;

import HwpDoc.Exception.NotImplementedException;

public abstract class Ctrl {
    private static final Logger log = Logger.getLogger(Ctrl.class.getName());

	public String ctrlId;
	public boolean fullfilled;     // 파싱이 완료되었는지를 나타냄

	public Ctrl() {
	}

	public Ctrl(String ctrlId) {
		this.ctrlId = ctrlId;
	}
	
    public abstract int getSize();
	
    public static Ctrl getCtrl(Node node, int version) throws NotImplementedException {
        Ctrl ctrl = null;
        switch(node.getNodeName()) {
        case "hp:colPr":
            ctrl = new Ctrl_ColumnDef("dloc", node, version);
            break;
        case "hp:header":
            ctrl = new Ctrl_HeadFoot("daeh", node, version);
            break;
        case "hp:footer":
            ctrl = new Ctrl_HeadFoot("toof", node, version);
            break;
        case "hp:footNote":
            ctrl = new Ctrl_Note("  nf", node, version);
            break;
        case "hp:endNote":
            ctrl = new Ctrl_Note("  ne", node, version);
            break;
        case "hp:autoNum":
            ctrl = new Ctrl_AutoNumber("onta", node, version);
            break;
        case "hp:newNum":
            ctrl = new Ctrl_NewNumber("onwn", node, version);
            break;
        case "hp:pageNum":
            ctrl = new Ctrl_PageNumPos("pngp", node, version);
        case "hp:fieldBegin":
        case "hp:fieldEnd":
        case "hp:bookmark":
        case "hp:pageHiding":
        case "hp:pageNumCtrl":
        case "hp:indexmark":
        case "hp:hiddenComment":
            break;
        case "#text":
            break;
        default:
        	if (log.isLoggable(Level.FINE)) {
        		throw new NotImplementedException("Ctrl");
        	}
        }
            
        return ctrl;
    }

}
