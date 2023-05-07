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
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ShapeVideo extends Ctrl_GeneralShape {
	private static final Logger log = Logger.getLogger(Ctrl_ShapeVideo.class.getName());
	private int size;
	
	public int			videoType;		// 동영상타입 (0:로컬동영상, 1:웹동영상)
	public short		vidoeBinID;
	public String 		webURL;			
	public String		thumnailBinID;

	public Ctrl_ShapeVideo(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId, size, buf, off, version);
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}

	public Ctrl_ShapeVideo(Ctrl_GeneralShape shape) {
		super(shape);
		
		this.size = shape.getSize();
	}

	public Ctrl_ShapeVideo(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId, node, version);
        
        NamedNodeMap attributes = node.getAttributes();
        
        switch(attributes.getNamedItem("type").getNodeValue()) {
        case "VT_LOCAL":
            videoType = 0;   break;
        case "VT_WEB":
            videoType = 1;   break;
        default:
            throw new NotImplementedException("Ctrl_ShapeVideo");
        }
        
        String numStr = attributes.getNamedItem("fileIDRef").getNodeValue();
        vidoeBinID = (short) Integer.parseInt(numStr);
        
        numStr = attributes.getNamedItem("imageIDRef").getNodeValue();
        thumnailBinID = numStr;
        
        if (videoType==1) {
            webURL = attributes.getNamedItem("tag").getNodeValue();
        }
    }

	public static int parseElement(Ctrl_ShapeVideo obj, int size, byte[] buf, int off, int version) throws HwpParseException, NotImplementedException {
        int offset = off;
        
        obj.videoType   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        if (obj.videoType==0) {
            obj.vidoeBinID  = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
        } else if (obj.videoType==1) {
            int urlLen      = (short) ((buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF)*2);
            offset += 2;
            obj.objDesc = new String(buf, offset, urlLen, StandardCharsets.UTF_16LE);
            offset += urlLen;
        }
        short binID 		= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.thumnailBinID 	= String.valueOf(binID-1);
        
        if (offset-off-size!=0) {
            log.fine("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            // size 계산 무시
            // throw new HwpParseException();
        }
        
        return offset-off;
    }
    
    public static int parseCtrl(Ctrl_ShapeVideo shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        int len = Ctrl_ObjElement.parseCtrl(shape, size, buf, offset, version);
        offset += len;
        
        return offset-off;
    }

    
    public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("CTRL("+ctrlId+")")
			.append("=공통속성:"+super.toString());
		return strb.toString();
	}

	@Override
	public int getSize() {
		return size;
	}
}
