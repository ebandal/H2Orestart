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

import HwpDoc.Exception.HwpParseException;

public class LineSeg {
    private static final Logger log = Logger.getLogger(LineSeg.class.getName());

    public int startPos;
    public int lineVerticalPos;
    public int lineHeight;
    public int textHeight;
    public int lineDistanceToBase;
    public int lineSpacing;
    public int columnStartPos;
    public int segmentWidth;
    public int lineTag;
    public boolean isHeadingApplied;
    
    public LineSeg (int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        this.startPos        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.lineVerticalPos = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.lineHeight      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.textHeight      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.lineDistanceToBase  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.lineSpacing     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.columnStartPos  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.segmentWidth    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.lineTag         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        this.isHeadingApplied= (this.lineTag>>21&0x01)==0x01?true:false;
        
        if (offset-off-size != 0 && offset-off!=36) {
            log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
            throw new HwpParseException();
        }
    }
    
    public LineSeg (Node node, int version) {
        NamedNodeMap attrs = node.getAttributes();
        
        String numStr = attrs.getNamedItem("baseline").getNodeValue();
        numStr = attrs.getNamedItem("flags").getNodeValue();
        numStr = attrs.getNamedItem("horzpos").getNodeValue();
        numStr = attrs.getNamedItem("horzsize").getNodeValue();
        numStr = attrs.getNamedItem("spacing").getNodeValue();
        numStr = attrs.getNamedItem("textheight").getNodeValue();
        numStr = attrs.getNamedItem("textpos").getNodeValue();
        numStr = attrs.getNamedItem("vertpos").getNodeValue();
        numStr = attrs.getNamedItem("vertsize").getNodeValue();
    }
}
