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
package HwpDoc.HwpElement;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;

public class HwpRecord_DocumentProperties extends HwpRecord {
	private HwpDocInfo parent;
	
	public short sectionSize;			// 구역 갯수
	public short pageStartNo; 			// 페이지 시작 번호
	public short footNoteStartNo;		// 각주 시작 번호
	public short endNoteStartNo;		// 미주 시작 번호
	public short figureStartNo;			// 그림 시작 번호
	public short tableStartNo;			// 표 시작 번호
	public short eqStartNo;				// 수식 시작 번호
	public int	 listID;				// 리스트 아이디 (문서 내 캐럿의 위치 정보)
	public int   paraID;				// 문단 아이디 (문서 내 캐럿의 위치 정보)
	public int   charUnitLocInPara;		// 문단 내에서의 글자 단위 위치
	

	HwpRecord_DocumentProperties(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public HwpRecord_DocumentProperties(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		this(tagNum, level, size);
		this.parent = docInfo;
		int offset = off;
		
		sectionSize		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		pageStartNo		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		footNoteStartNo	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		endNoteStartNo	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		figureStartNo	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		tableStartNo	= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		eqStartNo		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		listID			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		paraID			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		charUnitLocInPara= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		
		if (offset-off!=26) {
			throw new HwpParseException();
		}
	}

    public HwpRecord_DocumentProperties(HwpDocInfo docInfo, Node node, int version) throws HwpParseException {
        super(HwpTag.HWPTAG_DOCUMENT_PROPERTIES, 0, 0);
        this.parent = docInfo;
        
        NamedNodeMap attributes = node.getAttributes(); 
        // [endnote="1", equation="1", footnote="1", page="1", pic="1", tbl="1"]
        
        for (int i=0; i<attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            switch(attr.getNodeName()) {
            case "page":
                pageStartNo = Short.parseShort(attr.getNodeValue());
                break;
            case "footnote":
                footNoteStartNo = Short.parseShort(attr.getNodeValue());
                break;
            case "endnote":
                endNoteStartNo = Short.parseShort(attr.getNodeValue());
                break;
            case "pic":
                figureStartNo = Short.parseShort(attr.getNodeValue());
                break;
            case "tbl":
                tableStartNo = Short.parseShort(attr.getNodeValue());
                break;
            case "equation":
                eqStartNo = Short.parseShort(attr.getNodeValue());
                break;
            default:
                System.out.println("Cannot parse Hwpx. " + attr.getNodeName() + ":" + attr.getNodeValue());
                throw new HwpParseException();
            }
        }
    }

}
