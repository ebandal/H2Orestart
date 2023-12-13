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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Page {
    private static final Logger log = Logger.getLogger(Page.class.getName());

	public boolean		landscape;			// 용지 방향 (0:좁게, 1:넓게)
	public int			width;				// 용지 가로 크기	(hwpunit)
	public int			height;				// 용지 세로 크기
	public byte			gutterType;			// 제책 방법 (LeftOnly,LeftRight,TopBottom)
	public int			marginLeft;			// 왼쪽여백
	public int			marginRight;		// 오늘쪽 여백
	public int			marginTop;			// 위 여백
	public int			marginBottom;		// 아래 여백
	public int			marginHeader;		// 머리말 여백
	public int			marginFooter;		// 꼬리말 여백
	public int			marginGutter;		// 제본 여백
	
	public Page() { }
	
	public Page(Node node) throws NotImplementedException {
        NamedNodeMap attributes = node.getAttributes();

        switch(attributes.getNamedItem("landscape").getNodeValue()) {
        case "NARROWLY":
            landscape = true;  break;
        case "WIDELY":
            landscape = false;   break;      // 세로인데, 왜 WIDELY로 되어 있지?
        default:
            if (log.isLoggable(Level.FINE)) {
            	throw new NotImplementedException("Page");
            }
        }
        
        String numStr = attributes.getNamedItem("width").getNodeValue();
        width = Integer.parseInt(numStr);
        
        numStr = attributes.getNamedItem("height").getNodeValue();
        height = Integer.parseInt(numStr);
        
        switch(attributes.getNamedItem("gutterType").getNodeValue()) {
        case "LEFT_ONELY":
            gutterType = 0; break;
        case "LEFT_RIGHT":
            gutterType = 1; break;
        case "TOP_BOTTOM":
            gutterType = 2; break;
        }
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hp:margin":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("left").getNodeValue();
                    marginLeft = Integer.parseInt(numStr);
                    
                    numStr = childAttrs.getNamedItem("right").getNodeValue();
                    marginRight = Integer.parseInt(numStr);
                    
                    numStr = childAttrs.getNamedItem("top").getNodeValue();
                    marginTop = Integer.parseInt(numStr);
                    
                    numStr = childAttrs.getNamedItem("bottom").getNodeValue();
                    marginBottom = Integer.parseInt(numStr);
                    
                    numStr = childAttrs.getNamedItem("header").getNodeValue();
                    marginHeader = Integer.parseUnsignedInt(numStr);
                    
                    numStr = childAttrs.getNamedItem("footer").getNodeValue();
                    marginFooter = Integer.parseUnsignedInt(numStr);
                    
                    numStr = childAttrs.getNamedItem("gutter").getNodeValue();
                    marginGutter = Integer.parseInt(numStr);
                }
                break;
            }
        }
    }

    public static Page parse(int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        Page page = new Page();

        // 디폴트 A4의 가로 길이는 210mm = 59529이다.  한글97과의 호환을 위해 59528을 사용한다.
        page.width          = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        // 디폴트 A4의 세로 길이는 2297mm = 84188이다.
        page.height         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        page.marginLeft     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        page.marginRight    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        page.marginTop      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        page.marginBottom   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        page.marginHeader   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        page.marginFooter   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        page.marginGutter   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          
        int attr            = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;          

        page.landscape      = (attr&0x01)==0x01?true:false;
        page.gutterType     = (byte) (attr>>1&0x03);
        
        log.fine("                                                  "
                +"용지=("+page.width+","+page.height+")"+","+(page.landscape?"가로":"세로")
                +",여백=("+page.marginLeft+","+page.marginRight+","+page.marginTop+","+page.marginBottom+")"
                +",머리글꼬리글=("+page.marginHeader+","+page.marginFooter+")"
            );

        if (offset-off-size!=0) {
            throw new HwpParseException();
        }
        
        return page;
    }

    public String toString() {
		String output = "용지=("+width+","+height+")"+","+(landscape?"가로":"세로")
		+",여백=("+marginLeft+","+marginRight+","+marginTop+","+marginBottom+")";

		return output;
	}
}
