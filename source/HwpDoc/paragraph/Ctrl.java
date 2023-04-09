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

import org.w3c.dom.Node;

import HwpDoc.Exception.NotImplementedException;

public abstract class Ctrl {
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
        default:
            throw new NotImplementedException("Ctrl");
        }
            
        return ctrl;
    }

}
