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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import HwpDoc.HwpElement.HwpRecord;
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
            throw new HwpParseException(HwpRecord.parseError("LineSeg[tag="+tagNum+",level="+level+"]", buf, off, size, offset));
        }
    }
    
    /**
     * HWPX hp:lineseg
     * &lt;hp:lineseg textpos="0" vertpos="0" vertsize="1000" textheight="1000" baseline="850" spacing="600"
     *             horzpos="0" horzsize="42520" flags="393216"/&gt;
     */
    public LineSeg (Node node, int version) {
        NamedNodeMap attrs = node.getAttributes();
        
        this.startPos           = intAttr(attrs, "textpos");
        this.lineVerticalPos    = intAttr(attrs, "vertpos");
        this.lineHeight         = intAttr(attrs, "vertsize");
        this.textHeight         = intAttr(attrs, "textheight");
        this.lineDistanceToBase = intAttr(attrs, "baseline");
        this.lineSpacing        = intAttr(attrs, "spacing");
        this.columnStartPos     = intAttr(attrs, "horzpos");
        this.segmentWidth       = intAttr(attrs, "horzsize");
        this.lineTag            = intAttr(attrs, "flags");
        this.isHeadingApplied   = (this.lineTag>>21&0x01)==0x01?true:false;
    }

    /** HWPTAG_PARA_LINE_SEG 레코드 하나에 들어 있는 모든 줄 정보 (줄 하나당 36 bytes) */
    public static List<LineSeg> parseAll(int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        List<LineSeg> list = new ArrayList<LineSeg>();
        for (int i=0; i+36<=size; i+=36) {
            list.add(new LineSeg(tagNum, level, 36, buf, off+i, version));
        }
        return list;
    }

    private static int intAttr(NamedNodeMap attrs, String name) {
        Node n = attrs.getNamedItem(name);
        if (n == null) return 0;
        try {
            return (int) Long.parseLong(n.getNodeValue().trim());   // flags 등 unsigned 32bit 값
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
