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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import soffice.WriterContext.HanType;

public class HwpRecord_BinData extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_BinData.class.getName());

	public Type	  		type;
	public Compressed	compressed;
	public State  		state;
	
	public String aPath;		// Type이 "LINK"일때, 연결 파일의 절대 경로
	// public String rPath;		// Type이 "LINK"일때, 연결 파일의 상대 경로
	public short  binDataID;	// Type이 "EMBEDDING"이거나 "STORAGE"일때, BINDATASTORAGE에 저장된 바이너리 데이터의 아이디
	public String format;		// Type이 "EMBEDDING"일때, extension("."제외)

	public String itemId;       // hwpx에서는 itemId가 String
    
	
	HwpRecord_BinData(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public HwpRecord_BinData(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		this(tagNum, level, size);

		if (docInfo.hanType == HanType.HWP) {
    		if (docInfo.getParentHwp().getBinData()==null) {
    		    docInfo.getParentHwp().setBinData(docInfo.getParentHwp().getOleFile().getChildEntries("BinData"));
    		}
		}
		
		int offset = off;
		short typeBits = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
		offset += 2;
		type = Type.from(typeBits&0x0F);
		compressed = Compressed.from(typeBits&0x30);
		state = State.from(typeBits&0x300);

		int pathLen1 = 0, pathLen2 = 0;
		if (type==Type.LINK) {
			pathLen1 = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2;
			offset += 2;
			if (pathLen1 > 0) {
				aPath = new String(buf, offset, pathLen1, StandardCharsets.UTF_16LE);
				offset += pathLen1;
				log.finest("                                                  " + aPath + "(AbsoluteLink)");
			}
			pathLen2 = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2;
			offset += 2;
			if (pathLen2 > 0) {
				// rPath = new String(buf, offset, pathLen2, StandardCharsets.UTF_16LE);
				offset += pathLen2;
				// log.fine("                                                  " + rPath + "(RelativeLink)");
			}
		}
		if (type==Type.EMBEDDING || type==Type.STORAGE) {
			binDataID = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
			offset += 2;
			if (docInfo.getParentHwp().getBinData().size() >= binDataID) {
			    aPath = docInfo.getParentHwp().getBinData().get(binDataID-1).getDirectoryEntryName().trim();
				log.fine("                                                  "
						+"ID="+binDataID+"("+aPath+")");
                // String.format("BIN%04X.%s", binDataID, format);
			}
			itemId = String.valueOf(binDataID);
		}
		if (type==Type.EMBEDDING || type==Type.STORAGE) {
			int extLen = (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2;
			offset += 2;
			if (extLen > 0) {
				format = new String(buf, offset, extLen, StandardCharsets.UTF_16LE);
				offset += extLen;
			}
		}
		
		if (offset-off-size!=0) {
			throw new HwpParseException();
		}
	}
	
	public HwpRecord_BinData(Node node, int version) throws NotImplementedException {
        super(HwpTag.HWPTAG_BIN_DATA, 0, 0);

        NamedNodeMap attributes = node.getAttributes();
        
        itemId = attributes.getNamedItem("id").getNodeValue();
        
        Node tempNode = attributes.getNamedItem("isEmbeded");
        if (tempNode!=null) {
            switch(tempNode.getNodeValue()) {
            case "0":
                type = type.LINK;
                aPath = attributes.getNamedItem("sub-path").getNodeValue();
                break;
            case "1":
                type = Type.EMBEDDING;
                aPath = attributes.getNamedItem("href").getNodeValue();
                break;
            }
        } else {
            aPath = attributes.getNamedItem("href").getNodeValue();
        }
        
        format = attributes.getNamedItem("media-type").getNodeValue();
        format = format.replaceAll("image/(jpg)", "$1");
    }
	
	public static enum Type {
		LINK		(0),
		EMBEDDING	(1),
		STORAGE		(2);

		private int type;
		
	    private Type(int type) { 
	    	this.type = type;
	    }

	    public static Type from(int type) {
	    	for (Type typeNum: values()) {
	    		if (typeNum.type == type)
	    			return typeNum;
	    	}
	    	return null;
	    }
	}

	public static enum Compressed {
		FOLLOW_STORAGE	(0x00),
		COMPRESS		(0x10),
		NO_COMPRESS		(0x20);
		
		private int comp;
		
	    private Compressed(int comp) { 
	    	this.comp = comp;
	    }

	    public static Compressed from(int comp) {
	    	for (Compressed compNum: values()) {
	    		if (compNum.comp == comp)
	    			return compNum;
	    	}
	    	return null;
	    }
	}

	public static enum State {
		NEVER_ACCESSED		(0x000),
		FOUND_FILE_BY_ACCESS(0x100),
		ACCESS_FAILED		(0x200),
		LINK_ACCESS_IGNORED (0x400);
		
		private int state;
		
	    private State(int state) { 
	    	this.state = state;
	    }

	    public static State from(int state) {
	    	for (State stateNum: values()) {
	    		if (stateNum.state == state)
	    			return stateNum;
	    	}
	    	return null;
	    }
	}

}
