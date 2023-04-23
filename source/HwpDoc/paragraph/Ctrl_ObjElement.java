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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;

public class Ctrl_ObjElement extends Ctrl_Common {
	private static final Logger log = Logger.getLogger(Ctrl_ObjElement.class.getName());
	private int size;

	public int		xGrpOffset;	// 개체가 속한 그룹 내에서의 X offset
	public int		yGrpOffset;	// 개체가 속한 그룹 내에서의 Y offset
	public short	nGrp;		// 몇번이나 그룹 되었는지
	public short	ver;		// 개체 요소의 local file version
	public int 		iniWidth;	// 개체 생성시 초기 폭
	public int		iniHeight;	// 개체 생성시 초기 높이
	public int		curWidth;	// 개체의 현재 폭
	public int		curHeight;	// 개체의 현재 높이
	public boolean  horzFlip;	// 속성(0:horz flip, 1:ver flip)
    public boolean  verFlip;    // 속성(0:horz flip, 1:ver flip)
	public short	rotat;		// 회전각
	public int		xCenter;	// 회전 중심의 x 자표
	public int		yCenter;	// 회전 중심의 y 자표
	public short	matCnt;		// scale matrix와 ratation matrix쌍의 갯수. 초기엔 1, group할때마다 하나씩 증가하고, ungroup할때마다 하나씩 감소한다.
	public double[]	matrix;		// transalation matrix
	public double[]	matrixSeq;	// scale matrix/rotation matrix sequence

	public Ctrl_ObjElement() {
		super();
	}

	public Ctrl_ObjElement(String ctrlId) {
        super(ctrlId);
    }

	public Ctrl_ObjElement(String ctrlId, int size, byte[] buf, int off, int version) {
		super(ctrlId, size, buf, off, version);
		this.size = offset-off;

		log.fine("                                                  " + toString());
	}
	
	public Ctrl_ObjElement(Ctrl_ObjElement element) {
		super((Ctrl_Common)element);
		
		this.xGrpOffset = element.xGrpOffset;
		this.yGrpOffset = element.yGrpOffset;
		this.nGrp 		= element.nGrp;
		this.ver 		= element.ver;
		this.iniWidth 	= element.iniWidth;
		this.iniHeight 	= element.iniHeight;
		this.curWidth 	= element.curWidth;
		this.curHeight 	= element.curHeight;
		this.horzFlip 	= element.horzFlip;
        this.verFlip    = element.verFlip;
		this.rotat 		= element.rotat;
		this.xCenter 	= element.xCenter;
		this.yCenter 	= element.yCenter;
		this.matCnt 	= element.matCnt;
		this.matrix 	= element.matrix;
		this.matrixSeq 	= element.matrixSeq;
	}
	
	public Ctrl_ObjElement(String ctrlId, Node node, int version) throws NotImplementedException {
	    super(ctrlId, node, version);
	    
        NamedNodeMap attributes = node.getAttributes();
        
        // attributes.getNamedItem("href").getNodeValue();  // 변경 추적 대상 파일의 경로
        // attributes.getNamedItem("InstId").getNodeValue();
        
        String numStr = attributes.getNamedItem("groupLevel").getNodeValue();
        nGrp = (short) Integer.parseInt(numStr);

        matrix = new double[(nGrp+1)*6];
        matrixSeq = new double[(nGrp+1)*6*2];

        int matrixIdx = 0;
        NodeList nodeList = node.getChildNodes();
        for (int i=nodeList.getLength()-1; i>=0; i--) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hp:offset":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    xGrpOffset = (int) Long.parseLong(numStr);
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    yGrpOffset = (int) Long.parseLong(numStr);
                }
                node.removeChild(child);
                break;
            case "hp:orgSz":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("width").getNodeValue();
                    iniWidth = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("height").getNodeValue();
                    iniHeight = Integer.parseInt(numStr);
                }
                node.removeChild(child);
                break;
            case "hp:curSz":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("width").getNodeValue();
                    curWidth = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("height").getNodeValue();
                    curHeight = Integer.parseUnsignedInt(numStr);
                }
                node.removeChild(child);
                break;
            case "hp:flip":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    switch(childAttrs.getNamedItem("horizontal").getNodeValue()) {
                    case "0":
                        horzFlip = false;   break;
                    case "1":
                        horzFlip = true;    break;
                    }
                    
                    switch(childAttrs.getNamedItem("vertical").getNodeValue()) {
                    case "0":
                        verFlip = false;   break;
                    case "1":
                        verFlip = true;    break;
                    }
                }
                node.removeChild(child);
                break;
            case "hp:rotationInfo":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("angle").getNodeValue();
                    rotat = (short) Integer.parseInt(numStr);

                    numStr = childAttrs.getNamedItem("centerX").getNodeValue();
                    xCenter = Integer.parseInt(numStr);

                    numStr = childAttrs.getNamedItem("centerY").getNodeValue();
                    yCenter = Integer.parseInt(numStr);
                    // childAttrs.getNamedItem("rotateimage").getNodeValue()) {
                }
                node.removeChild(child);
                break;
            case "hp:renderingInfo":
                {
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        
                        switch(grandChild.getNodeName()) {
                        case "hc:transMatrix":
                            setMatrix(grandChild, matrix, matrixIdx*6);
                            break;
                        case "hc:scaMatrix":
                            setMatrix(grandChild, matrixSeq, matrixIdx*6*2);
                            break;
                        case "hc:rotMatrix":
                            setMatrix(grandChild, matrixSeq, matrixIdx*6*2+6);
                            break;
                        default:
                            throw new NotImplementedException("Ctrl_ObjElement");
                        }
                    }
                    matrixIdx++;
                    matCnt++;
                }
                node.removeChild(child);
                break;
            case "hp:lineShape":
                
                break;
            case "hc:fillBrush":
                
                break;
            case "hp:shadow":
                
                break;
            case "hp:drawText":
                
                break;
            case "hc:pt0":
                
                break;
            case "hc:pt1":
                
                break;
            case "hc:pt2":
                
                break;
            case "hc:pt3":
                
                break;
            case "hp:sz":
                
                break;
            case "hp:pos":
                
                break;
            case "hp:outMargin":
                
                break;
            case "hp:imgRect":
                
                break;
            case "hp:imgClip":
                
                break;
            case "hp:inMargin":
                
                break;
            case "hp:imgDim":
                
                break;
            case "hc:img":
                
                break;
            case "hp:effects":
                
                break;
            case "hp:shapeComment":
                
                break;
            default:
            	log.warning(child.getNodeName() + "=" + child.getNodeValue());
                // throw new NotImplementedException("Ctrl_ObjElement");
            }
        }
    }

	private void setMatrix(Node node, double[] matrix, int offset) {
        NamedNodeMap attributes = node.getAttributes();
        String numStr = attributes.getNamedItem("e1").getNodeValue();
        matrix[offset+0] = Double.parseDouble(numStr);
        numStr = attributes.getNamedItem("e2").getNodeValue();
        matrix[offset+1] = Double.parseDouble(numStr);
        numStr = attributes.getNamedItem("e3").getNodeValue();
        matrix[offset+2] = Double.parseDouble(numStr);
        numStr = attributes.getNamedItem("e4").getNodeValue();
        matrix[offset+3] = Double.parseDouble(numStr);
        numStr = attributes.getNamedItem("e5").getNodeValue();
        matrix[offset+4] = Double.parseDouble(numStr);
        numStr = attributes.getNamedItem("e6").getNodeValue();
        matrix[offset+5] = Double.parseDouble(numStr);
	}
	
	public static int parseCtrl(Ctrl_ObjElement obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        int len = Ctrl_Common.parseCtrl(obj, size, buf, offset, version);
        offset += len;

        obj.xGrpOffset      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.yGrpOffset      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.nGrp            = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.ver             = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.iniWidth        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.iniHeight       = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.curWidth        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.curHeight       = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.horzFlip        = (buf[offset]&0x01)==0x01?true:false;
        obj.verFlip         = (buf[offset]&0x02)==0x02?true:false;
        offset += 4;
        obj.rotat           = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        obj.xCenter         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.yCenter         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.matCnt          = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        
        obj.matrix = new double[6];
        for (int i=0;i<6;i++) {
            obj.matrix[i] = ByteBuffer.wrap(buf, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
            offset += 8;
        }

        int matrixSize = obj.matCnt*6*2;
        if (matrixSize>0) {
            obj.matrixSeq = new double[matrixSize];
            for (int i=0;i<matrixSize;i++) {
                obj.matrixSeq[i] = ByteBuffer.wrap(buf, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                offset += 8;
            }
        }
        
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
