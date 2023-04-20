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
package HwpDoc.section;

import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class PageBorderFill {
    private static final Logger log = Logger.getLogger(PageBorderFill.class.getName());

    public boolean			textBorder;			// 쪽 테두리 위치기준(true:종이기준, false:본문기준)
	public boolean			headerInside;		// 머리말 포함
	public boolean			footerInside;		// 꼬리말 포함
	public byte				fillArea;			// 채울영역 Paper, Page, Border
	public short			offsetLeft;			// 1417(5mm)
	public short			offsetRight;		// 1417(5mm)
	public short			offsetTop;			// 1417(5mm)
	public short			offsetBottom;		// 1417(5mm)
	public short			borderFill;			// BorderFill ID속성값
	
	public PageBorderFill() { }
	
	public PageBorderFill(Node node) throws NotImplementedException {
        NamedNodeMap attributes = node.getAttributes();
        
        switch(attributes.getNamedItem("type").getNodeValue()) {
        case "BOTH":
            break;
        case "EVEN":
            break;
        case "ODD":
            break;
        }

        String numStr = attributes.getNamedItem("borderFillIDRef").getNodeValue();
        borderFill = (short) Integer.parseInt(numStr);

        switch(attributes.getNamedItem("textBorder").getNodeValue()) {
        case "PAPER":
            textBorder = false; break;
        default:
            textBorder = true;
            throw new NotImplementedException("pageBorderFill");
        }

        switch(attributes.getNamedItem("headerInside").getNodeValue()) {
        case "0":
            headerInside = false; break;
        case "1":
            headerInside = true;  break;
        }

        switch(attributes.getNamedItem("footerInside").getNodeValue()) {
        case "0":
            footerInside = false; break;
        case "1":
            footerInside = true;  break;
        }
        
        switch(attributes.getNamedItem("fillArea").getNodeValue()) {
        case "PAPER":
            fillArea = 0;   break;
        case "PAGE":
            fillArea = 1;   break;
        case "BORDER":
            fillArea = 2;   break;
        }
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "offset":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("left").getNodeValue();
                    offsetLeft = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("right").getNodeValue();
                    offsetRight = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("top").getNodeValue();
                    offsetTop = (short) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("bottom").getNodeValue();
                    offsetBottom = (short) Integer.parseInt(numStr);
                }
                break;
            }
        }
    }

	public static PageBorderFill parse(int level, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;

        PageBorderFill borderFill = new PageBorderFill();
        
        int attr                = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        borderFill.textBorder   = (attr&0x01)==0x01?true:false;
        borderFill.headerInside = (attr&0x02)==0x02?true:false;
        borderFill.footerInside = (attr&0x04)==0x04?true:false;
        borderFill.fillArea     = (byte) (attr>>3&0x03);
        
        borderFill.offsetLeft   = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        borderFill.offsetRight  = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        borderFill.offsetTop    = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        borderFill.offsetBottom = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;
        borderFill.borderFill   = (short) (buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF);
        offset += 2;

        log.fine("                                                  "
                +"배경ID="+borderFill
                +",테두리간격=("+borderFill.offsetLeft+":"+borderFill.offsetRight+":"+borderFill.offsetTop+":"+borderFill.offsetBottom+")"
                +",쪽테두리위치="+(borderFill.textBorder?"종이":"본문")
                +",머리말포함="+(borderFill.headerInside?"Y":"N")
                +",꼬리말포함="+(borderFill.footerInside?"Y":"N")
                +",채울영역="+(borderFill.fillArea==0?"Paper":borderFill.fillArea==1?"Page":borderFill.fillArea==2?"Border":"???")
            );

        if (offset-off-size!=0) {
            throw new HwpParseException();
        }
        
        return borderFill;
    }

}
