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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpFile;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecord_BinData.Compressed;

public class Ctrl_ShapePic extends Ctrl_GeneralShape {
    private static final Logger log = Logger.getLogger(Ctrl_ShapePic.class.getName());
    private int size;
    
    public int 			borderColor;	// 테두리 색
    public int			borderThick;	// 테두리 두께
    public int			borderAttr;		// 테두리 속성
    public Point[]		borderPoints;	// 이미지의 테두리 사각형의 x,y 좌표(최초 그림 삽입 시 크기)
    public int			cropLeft;		// 자르기 한 후 사각형의 left
    public int			cropTop;		// 자르기 한 후 사각형의 top
    public int			cropRight;		// 자르기 한 후 사각형의 right
    public int			cropBottom;		// 자르기 한 후 사각형의 bottom
    public short[]		innerSpaces;	// 안쪽여백(왼쪽여백,오른쪽여백,위쪽여백,아래쪽여백) default:141(표) or 0(그림)
    public byte			bright;			// 그림 밝기
    public byte			contrast;		// 그림 명암
    public byte			effect;			// 그림 효과 (0:REAL_PIC,1:GRAY_SCALE,2:BLACK_WHTE,4:PATTERN8x8
    public String		binDataID;		// BinItem의 아이디 참조값
    // public ImagePath    imagePath;      // BinItemID값 대신 문자열을 사용하도록 함 (hwpx); Fill에서 Image 사용할 수 있도록 bindDataID 사용        
    
    public byte			borderAlpha;	// 테두리 투명도
    public int			instanceID;		// 문서 내 각 개체에 대한 고유 아이디(instance ID)
    public int			picEffectInfo;	// 그림효과정보(그림자,네온,부드러운,가장자리,반사)
    public List<PicEffect>	picEffect;		// 각 효과 정보
    
    public int			iniPicWidth;	// 그림 최초 생성시 기준 이미지 크기
    public int			iniPicHeight;	// 그림 최초 생성시 기준 이미지 크기
    public byte			picAlpha;		// 이미지 투명도
    
    public Ctrl_ShapePic(String ctrlId, int size, byte[] buf, int off, int version) {
        super(ctrlId, size, buf, off, version);
        this.size = offset-off;
        
        log.fine("                                                  " + toString());
    }
    
    public Ctrl_ShapePic(Ctrl_GeneralShape shape) {
        super(shape);
        
        this.size = shape.getSize();
    }
    
    public Ctrl_ShapePic(String ctrlId, Node node, int version) throws NotImplementedException {
        super(ctrlId, node, version);
        
        NamedNodeMap attributes = node.getAttributes();
        // attributes.getNamedItem("reverse").getNodeValue();
        String numStr;
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            switch(child.getNodeName()) {
            case "hp:imgRect":      // 이미지 좌표 정보
                {
                    NodeList childNodeList = child.getChildNodes();
                    borderPoints = new Point[4];
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        NamedNodeMap childAttrs = grandChild.getAttributes();
                        switch(grandChild.getNodeName()) {
                        case "hc:pt0":  // 첫번째 좌표
                            borderPoints[0] = new Point();
                            numStr = childAttrs.getNamedItem("x").getNodeValue();
                            borderPoints[0].x = Integer.parseInt(numStr);
                            numStr = childAttrs.getNamedItem("y").getNodeValue();
                            borderPoints[0].y = Integer.parseInt(numStr);
                            break;
                        case "hc:pt1":  // 두번째 좌표
                            borderPoints[1] = new Point();
                            numStr = childAttrs.getNamedItem("x").getNodeValue();
                            borderPoints[1].x = Integer.parseInt(numStr);
                            numStr = childAttrs.getNamedItem("y").getNodeValue();
                            borderPoints[1].y = Integer.parseInt(numStr);
                            break;
                        case "hc:pt2":  // 세번째 좌표
                            borderPoints[2] = new Point();
                            numStr = childAttrs.getNamedItem("x").getNodeValue();
                            borderPoints[2].x = Integer.parseInt(numStr);
                            numStr = childAttrs.getNamedItem("y").getNodeValue();
                            borderPoints[2].y = Integer.parseInt(numStr);
                            break;
                        case "hc:pt3":  // 네번째 좌표
                            borderPoints[3] = new Point();
                            numStr = childAttrs.getNamedItem("x").getNodeValue();
                            borderPoints[3].x = Integer.parseInt(numStr);
                            numStr = childAttrs.getNamedItem("y").getNodeValue();
                            borderPoints[3].y = Integer.parseInt(numStr);
                            break;
                        }
                    }
                }
                break;
            case "hp:imgClip":      // 이미지 자르기 정보
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("left").getNodeValue();   // 왼쪽에서 이미지를 자른 크기
                    cropLeft = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("right").getNodeValue();  // 오른쪽에서 이미지를 자른 크기
                    cropRight = Integer.parseInt(numStr);
                    numStr =  childAttrs.getNamedItem("top").getNodeValue();   // 위쪽에서 이미지를 자른 크기
                    cropTop = Integer.parseInt(numStr);
                    numStr =  childAttrs.getNamedItem("bottom").getNodeValue();// 아래쪽에서 이미지를 자른 크기
                    cropBottom = Integer.parseInt(numStr);
                    
                    // 한컴 5.0.3.4 오류 체크 및 값 강제설정
                    int minX = Math.min(Math.min(borderPoints[0].x, borderPoints[1].x), Math.min(borderPoints[2].x, borderPoints[3].x));
                    int maxX = Math.max(Math.max(borderPoints[0].x, borderPoints[1].x), Math.max(borderPoints[2].x, borderPoints[3].x));
                    int minY = Math.min(Math.min(borderPoints[0].y, borderPoints[1].y), Math.min(borderPoints[2].y, borderPoints[3].y));
                    int maxY = Math.max(Math.max(borderPoints[0].y, borderPoints[1].y), Math.max(borderPoints[2].y, borderPoints[3].y));
                    
                    if ((cropLeft < minX || cropLeft > maxX) || (cropRight < minX || cropRight > maxX) || 
                        (cropTop < minY || cropTop > maxY) || (cropBottom < minY || cropBottom > maxY)) {
                        cropLeft = minX;
                        cropRight = maxX;
                        cropTop = minY;
                        cropBottom = maxY;
                    }
                }
                break;
            case "hp:effects":      // 이미지 효과 정보
                {
                    if (picEffect==null) {
                        picEffect = new ArrayList<>();
                    }
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        NamedNodeMap childAttrs = grandChild.getAttributes();
                        switch(grandChild.getNodeName()) {
                        case "hp:shadow":  // 그림자 효과
                            {
                                PicEffectType effectType = PicEffectType.SHADOW;
                                Shadow effect = new Shadow(effectType, grandChild, version);
                                picEffect.add(effect);
                            }
                            break;
                        case "hp:glow":  // 네온 효과
                            {
                                PicEffectType effectType = PicEffectType.GLOW;
                                Neon effect = new Neon(effectType, grandChild, version);
                                picEffect.add(effect);
                            }
                            break;
                        case "hp:softEdge":  // 부드러운 가장자리 효과
                            {
                                PicEffectType effectType = PicEffectType.SOFT_EDGE;
                                SoftEdge effect = new SoftEdge(effectType, grandChild, version);
                                picEffect.add(effect);
                            }
                            break;
                        case "hp:reflection":  // 반사 효과
                            {
                                PicEffectType effectType = PicEffectType.REFLECT;
                                Reflect effect = new Reflect(effectType, grandChild, version);
                                picEffect.add(effect);
                            }
                            break;
                        default:
                            if (log.isLoggable(Level.FINE)) {
                                throw new NotImplementedException("Ctrl_ShapePic");
                            }
                            break;
                        }
                    }
                }
                break;
            case "hp:inMargin":     // 안쪽 여백 정보
                {
                    /*
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("left").getNodeValue();
                    numStr = childAttrs.getNamedItem("right").getNodeValue();
                    numStr = childAttrs.getNamedItem("top").getNodeValue();
                    numStr = childAttrs.getNamedItem("bottom").getNodeValue();
                    */
                }
                break;
            case "hc:img":          // 그림 정보    51 page
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("bright").getNodeValue();   // 그림의 밝기
                    bright = (byte) Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("contrast").getNodeValue();  // 그림의 명암
                    contrast = (byte) Integer.parseInt(numStr);
                    switch(childAttrs.getNamedItem("effect").getNodeValue()) {   // 그림의 추가 효과
                    case "REAL_PIC":
                        effect = 0;    break;
                    case "GRAY_SCALE":
                        effect = 1;    break;
                    case "BLACK_WHITE":
                        effect = 2;    break;
                    default:
                        if (log.isLoggable(Level.FINE)) {
                            throw new NotImplementedException("Ctrl_ShapePic");
                        }
                        break;
                    }
                    numStr =  childAttrs.getNamedItem("binaryItemIDRef").getNodeValue();// BinDataItem 요소의 아이디 참조값
                    binDataID = numStr;
                }
                break;
            case "hp:offset":
                break;
            case "hp:orgSz":
                break;
            case "hp:curSz":
                break;
            case "hp:flip":
                break;
            case "hp:rotationInfo":
                break;
            case "hp:renderingInfo":
                break;
            case "hp:imgDim":
                {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("dimwidth").getNodeValue();   // 원본 그림의 너비
                    iniPicWidth = Integer.parseInt(numStr);
                    numStr = childAttrs.getNamedItem("dimheight").getNodeValue();  // 원본 그림의 높이
                    iniPicHeight = Integer.parseInt(numStr);
                }
                break;
            case "hp:sz":
                break;
            case "hp:pos":
                break;
            case "hp:outMargin":
                break;
            case "hp:shapeComment":
                break;
            case "hp:parameterset":
	            {
                    NamedNodeMap childAttrs = child.getAttributes();
                    numStr = childAttrs.getNamedItem("cnt").getNodeValue();
                    numStr = childAttrs.getNamedItem("name").getNodeValue();
                    NodeList childNodeList = child.getChildNodes();
                    for (int j=0; j<childNodeList.getLength(); j++) {
                        Node grandChild = childNodeList.item(j);
                        switch(grandChild.getNodeName()) {
                        case "hp:listParam":	break;
                        default:
                        	if (log.isLoggable(Level.FINE)) {
                        		throw new NotImplementedException("Ctrl_ShapePic");
                        	}
                        }
                    }
	            }
	            break;
            default:
                if (log.isLoggable(Level.FINE)) {
                    throw new NotImplementedException("Ctrl_ShapePic");
                }
                break;
            }
        }
    }

	public static int parseElement(Ctrl_ShapePic obj, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        obj.borderColor     = buf[offset+3]<<24&0xFF000000 | buf[offset]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset+2]&0x000000FF;
        offset += 4;
        obj.borderThick     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.borderAttr      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.borderPoints = new Point[4];
        for (int i=0;i<4;i++) {
            obj.borderPoints[i] = new Point();
            obj.borderPoints[i].x = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            obj.borderPoints[i].y = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
        }
        obj.cropLeft        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.cropTop         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.cropRight       = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.cropBottom      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
        offset += 4;
        obj.innerSpaces = new short[4];
        for (int i=0;i<4;i++) {
            obj.innerSpaces[i]= (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
            offset += 2;
        }
        
        obj.bright          = buf[offset++];
        obj.contrast        = buf[offset++];
        obj.effect          = buf[offset++];

        short binItemID       = (short) (buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF);
        offset += 2;
        
        obj.binDataID = String.valueOf(binItemID-1);
        obj.borderAlpha	    = buf[offset++];
        
        if (offset-off < size) {
            obj.instanceID      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
        }
        if (offset-off < size) {
            obj.picEffectInfo   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;

            if (obj.picEffectInfo>0 && offset-off<size) {
                obj.picEffect = new ArrayList<PicEffect>();
                if ((obj.picEffectInfo&0x1) == 0x1) {
                    PicEffect effect = new Shadow(obj.picEffectInfo, buf, offset, 56);
                    offset += effect.getSize();
                    obj.picEffect.add(effect);
                }
                if ((obj.picEffectInfo&0x2) == 0x2) {
                    PicEffect effect = new Neon(obj.picEffectInfo, buf, offset, 28);
                    offset += effect.getSize();
                    obj.picEffect.add(effect);
                }
                if ((obj.picEffectInfo&0x4) == 0x4) {
                    PicEffect effect = new SoftEdge(obj.picEffectInfo, buf, offset, 4);
                    offset += effect.getSize();
                    obj.picEffect.add(effect);
                }
                if ((obj.picEffectInfo&0x8) == 0x8) {
                    PicEffect effect = new Reflect(obj.picEffectInfo, buf, offset, 56);
                    offset += effect.getSize();
                    obj.picEffect.add(effect);
                }
            }
        }

        if (offset-off < size) {
            // 추가이미지 속성 (그림 최조 생성시 기준 이미지 크기)
            obj.iniPicWidth     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            // 추가이미지 속성 (그림 최조 생성시 기준 이미지 크기)
            obj.iniPicHeight    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            if (size-(offset-off)>=1) {
                // 추가이미지 속성 (이미지 투명도)
                obj.picAlpha        = buf[offset++];
            }
        }
        
        log.fine("                                                  "
                +"(X,Y)=("+obj.xGrpOffset+","+obj.yGrpOffset+")"
                +",Width="+obj.curWidth+",Height="+obj.curHeight
                );
        
        if (offset-off-size!=0) {
            log.severe("[CtrlId]=" + obj.ctrlId + ", size=" + size + ", but currentSize=" + (offset-off));
            // 그림 효과를 제대로 읽을 수 없으니  size 맞지 않으니 Exception이 계속 발생할 수 밖에 없다. Exception 발생하지 않도록 처리한다.
            // throw new HwpParseException();
        }
        
        return size;
    }
	
    public static int parseCtrl(Ctrl_ShapePic shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        int len = Ctrl_ObjElement.parseCtrl((Ctrl_ObjElement)shape, size-82, buf, offset, version);
        offset += len;
        
        return offset-off;
    }
    
    public static int parseListHeaderAppend(Ctrl_ShapePic shape, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        // LIST_HEADER 정의는 6byte이지만, 실제 33byte 넘어온다. 6byte 뒤의 27byte는 알 수 없다.
        offset += size;
        
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
    
    public static class PicEffect {
        protected int   size;
        
        PicEffectType   type;
        
        public PicEffect(PicEffectType type) {
            this.type = type;
        }
        
        public PicEffect(int typeNum) {
            this(PicEffectType.from(typeNum));
        }
        
        public int getSize() {
            return this.size;
        }
    }
    
    public static class Shadow extends PicEffect {
        public int      style;          // 그림자 스타일
        public int      transparency;   // 그림자 투명도
        public int      blur;           // 그림자 흐릿하게
        public int      direction;      // 방향
        public int      distance;       // 거리
        public float    angleX;         // 기울기 각도 x
        public float    angleY;         // 기울기 각도 y
        public float    magnifyX;       // 확배비율 x
        public float    mganifyY;       // 확대비율 y
        public int      rotation;       // 그림과 함께 그림자 회전
        public PicColor color;          
        
        public Shadow(int typeNum, byte[] buf, int off, int size) throws HwpParseException {
            super(typeNum);
            
            int offset = off;
            style           = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            transparency    = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            blur            = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            direction       = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            distance        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            angleX          = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            angleY          = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            magnifyX        = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            mganifyY        = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            rotation        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            color           = new PicColor(buf, offset, size-(offset-off)); 
            offset += color.getSize();
            
            if (offset-off-size != 0 && offset-off-size+1 != 0) {
                throw new HwpParseException();
            }
            this.size = offset-off;
        }
        
        public Shadow(PicEffectType type, Node node, int version) {
            super(type);
            
            NamedNodeMap attrs = node.getAttributes();
            String numStr =  attrs.getNamedItem("style").getNodeValue(); // 그림자스타일
            style = Integer.parseInt(numStr);
            numStr =  attrs.getNamedItem("alpha").getNodeValue(); // 시작 투명도
            transparency = Integer.parseInt(numStr);
            numStr =  attrs.getNamedItem("radius").getNodeValue(); // 흐릿함 정도
            blur = Integer.parseInt(numStr);
            numStr =  attrs.getNamedItem("direction").getNodeValue(); // 방향 각도
            direction = Integer.parseInt(numStr);
            numStr =  attrs.getNamedItem("distance").getNodeValue(); // 대상과 그림자 사이의 거리
            distance = Integer.parseInt(numStr);
            // attrs.getNamedItem("alignStyle").getNodeValue(); // 그림자 정렬
            switch(attrs.getNamedItem("rotationStyle").getNodeValue()) { // 도형과 함께 그림자 회전 여부
            case "0":
                rotation = 0;   break;
            case "1":
                rotation = 1;   break;
            }
            
            NodeList nodeList = node.getChildNodes();
            for (int i=0; i<nodeList.getLength(); i++) {
                Node child = nodeList.item(i);
                NamedNodeMap childAttrs = child.getAttributes();
                switch(child.getNodeName()) {
                case "hp:skew":  // 기울기
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    angleX = Float.parseFloat(numStr);  // 기울기 각도 x
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    angleY = Float.parseFloat(numStr);  // 기울기 각도 x
                    break;
                case "hp:scale":    // 확대 비율
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    magnifyX = Float.parseFloat(numStr);  // x축 확대 비율
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    mganifyY = Float.parseFloat(numStr);  // y축 확대 비율
                    break;
                case "hp:effectsColor": // 그림자 색상
                    color = new PicColor(child);
                    break;
                }
            }
        }
    }
    
    public static class Neon extends PicEffect {
        public  float       transparency;       // 네온 투명도
        public  float       radius;             // 네온 반경
        public  PicColor    color;          // 네온 색상
        
        public Neon(int typeNum, byte[] buf, int off, int size) {
            super(typeNum);
            
            int offset = off;
            transparency    = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            radius          = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            color           = new PicColor(buf, offset, size-(offset-off)); 
            offset += color.getSize();

            this.size = offset-off;
        }

        public Neon(PicEffectType type, Node node, int version) {
            super(type);
            
            NamedNodeMap attrs = node.getAttributes();
            String numStr =  attrs.getNamedItem("alpha").getNodeValue(); // 투명도
            transparency = Float.parseFloat(numStr);
            numStr =  attrs.getNamedItem("radius").getNodeValue(); // 네온 크기
            radius = Float.parseFloat(numStr);
            
            NodeList nodeList = node.getChildNodes();
            for (int i=0; i<nodeList.getLength(); i++) {
                Node child = nodeList.item(i);
                switch(child.getNodeName()) {
                case "hp:effectsColor":  // 네온 색상
                    color = new PicColor(child);
                    break;
                }
            }
        }
    }
    
    public static class SoftEdge extends PicEffect {
        public  float       radius;         // 부드러운 가장자리 반경
        
        public SoftEdge(int typeNum, byte[] buf, int off, int size) {
            super(typeNum);
            
            int offset = off;
            radius  = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            
            this.size = offset-off;
        }
        
        public SoftEdge(PicEffectType type, Node node, int version) {
            super(type);
            
            NamedNodeMap attrs = node.getAttributes();
            String numStr =  attrs.getNamedItem("radius").getNodeValue(); // 부드러운 가장자리 크기
            radius = Float.parseFloat(numStr);
        }
    }
    
    public static class Reflect extends PicEffect {
        public  int     style;          // 반사 스타일
        public  float   radius;         // 반경
        public  float   direction;      // 방향
        public  float   distance;       // 거리
        public  float   angleX;         // 기울기 각도 x
        public  float   angleY;         // 기울기 각도 y
        public  float   magnifyX;       // 확대 비율 x
        public  float   magnifyY;       // 확대 비율 y
        public  int     rotateStyle;    // 회전 스타일
        public  float   startTrans;     // 시작 투명도
        public  float   startPos;       // 시작 위치
        public  float   endTrans;       // 끝 투명도
        public  float   endPos;         // 끝 위치
        public  float   offsetDirection;// 오프셋 방향
        
        public Reflect(int typeNum, byte[] buf, int off, int size) {
            super(typeNum);
            
            int offset = off;
            style           = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            radius          = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            direction       = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            distance        = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            angleX          = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            angleY          = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            magnifyX        = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            magnifyY        = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            rotateStyle     = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            startTrans      = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            startPos        = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            endTrans        = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            endPos          = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            offsetDirection = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            offset += 4;
            
            this.size = offset-off;
        }
        
        public Reflect(PicEffectType type, Node node, int version) throws NotImplementedException {
            super(type);
            
            NamedNodeMap attrs = node.getAttributes();
            switch(attrs.getNamedItem("alignStyle").getNodeValue()) { // 반사된 그림 위치
            case "0":
                break;
            default:
            	if (log.isLoggable(Level.FINE)) {
            		throw new NotImplementedException("Reflect");
            	}
            	break;
            }
            
            String numStr =  attrs.getNamedItem("radius").getNodeValue(); // 흐릿함 정도
            radius = Float.parseFloat(numStr);
            numStr =  attrs.getNamedItem("direction").getNodeValue(); // 반사된 그림 방향 각도
            direction = Integer.parseInt(numStr);
            numStr =  attrs.getNamedItem("distance").getNodeValue(); // 대상과 그림자 사이의 거리
            distance = Integer.parseInt(numStr);
            switch(attrs.getNamedItem("rotationStyle").getNodeValue()) { // 도형과 함께 회전할 것인지 여부
            case "0":
                rotateStyle = 0;    break;
            case "1":
                rotateStyle = 1;    break;
            }
            numStr =  attrs.getNamedItem("fadeDirection").getNodeValue(); // 오프셋 방향
            offsetDirection = Integer.parseInt(numStr);
            
            NodeList nodeList = node.getChildNodes();
            for (int i=0; i<nodeList.getLength(); i++) {
                Node child = nodeList.item(i);
                NamedNodeMap childAttrs = child.getAttributes();
                switch(child.getNodeName()) {
                case "hp:skew":  // 기울기
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    angleX = Float.parseFloat(numStr);  // 기울기 각도 x
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    angleY = Float.parseFloat(numStr);  // 기울기 각도 x
                    break;
                case "hp:scale":    // 확대 비율
                    numStr = childAttrs.getNamedItem("x").getNodeValue();
                    magnifyX = Float.parseFloat(numStr);  // x축 확대 비율
                    numStr = childAttrs.getNamedItem("y").getNodeValue();
                    magnifyY = Float.parseFloat(numStr);  // y축 확대 비율
                    break;
                case "hp:alpha": // 투명도
                    numStr = childAttrs.getNamedItem("start").getNodeValue();
                    startTrans = Float.parseFloat(numStr);  // 시작 위치 투명도
                    numStr = childAttrs.getNamedItem("end").getNodeValue();
                    endTrans = Float.parseFloat(numStr);  // 끝 위치 투명도
                    break;
                case "hp:pos":  // 위치
                    numStr = childAttrs.getNamedItem("start").getNodeValue();
                    startPos = Float.parseFloat(numStr);  // 시작 위치
                    numStr = childAttrs.getNamedItem("end").getNodeValue();
                    endPos = Float.parseFloat(numStr);  // 끝 위치
                    break;
                default:
                	if (log.isLoggable(Level.FINE)) {
                		throw new NotImplementedException("Reflect");
                	}
                	break;
                }
            }
        }
    }
    
    /* 
     * 상용SW품질이 목표가 아니므로, 상세 내용을 LibreOffice에서 표현하고자 하지 않으므로 간략화 한다.
     */
    public static class PicColor {
        private int     size;       
        
        public  int     type;       // 색상타입
        public  int     rgb;        // 0x00rrggbb
        // public   int     cmyk;       // 0xccmmyykk
        // public   int     schemaType; // 스키마타입
        // public   int     systemType; // 시스템타입
        // public   int     presetType; // 프리셋타입
        // public   int     scr;        // ScR
        // public   int     scg;        // ScG
        // public   int     h;          // H
        // public   int     s;          // S
        // public   int     l;          // L
        // public   int     nEffect;    // 색상효과 수
        // public   int[]   effectType; // 색상효과 종류
        // public   float[] effectValue;// 색상효과 값
        
        public PicColor(byte[] buf, int off, int size) {
            int offset = off;
            type        = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            rgb         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            // cmyk         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // schemaType   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // systemType   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // presetType   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // scr      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // scg      = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // h            = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // s            = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // l            = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // nEffect  = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            // offset += 4;
            // if (nEffect>0) {
            //  effectType = new int[nEffect];
            //  effectValue = new float[nEffect];
            //  
            //  for (int i=0;i<nEffect;i++) {
            //      effectType[i] = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            //      offset += 4;
            //      effectValue[i] = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            //      offset += 4;
            //  }
            // }
            this.size=size;
        }
        
        public PicColor(Node node) {
            NamedNodeMap attrs = node.getAttributes();
            switch(attrs.getNamedItem("type").getNodeValue()) {  // 색상 표현방법
            default:
                type = 0;
            }
            
            /*
            attrs.getNamedItem("schemaIndex").getNodeValue();     // Scheme Index
            attrs.getNamedItem("systemIndex").getNodeValue();     // System Index
            attrs.getNamedItem("presetIndex").getNodeValue();     // Preset Index
            NodeList nodeList = node.getChildNodes();
            for (int i=0; i<nodeList.getLength(); i++) {
                Node child = nodeList.item(i);
                switch(child.getNodeName()) {
                case "hp:rgb":
                case "hp:pcmyk":
                case "hp:scheme":
                case "hp:system":
                case "hp:effect":
                }
            }
            */
        }
        
        public int getSize() {
            return size;
        }
    }
    
    public static enum PicEffectType {
        // 한글문서파일구조 5.0 에서  정의하지 않고 있다. 다만 임시 구현을 위해 임의의 값으로 정의했다.
        NONE        (0x0),
        SHADOW      (0x1),
        GLOW        (0x2),
        SOFT_EDGE   (0x4),
        REFLECT     (0x8);
        
        private int effect;
        
        private PicEffectType(int effect) { 
            this.effect = effect;
        }
        
        public static PicEffectType from(int effect) {
            for (PicEffectType typeNum: values()) {
                if (typeNum.effect == effect)
                    return typeNum;
            }
            return null;
        }
    }
    
    public static enum ImagePathType {
        COMPOUND    (0x0),
        OWPML       (0x1),
        LINK        (0x2);
        
        private int type;
        
        private ImagePathType(int type) { 
            this.type = type;
        }
        
        public static ImagePathType from(int type) {
            for (ImagePathType typeNum: values()) {
                if (typeNum.type == type)
                    return typeNum;
            }
            return null;
        }
    }
    
    public static class ImagePath {
        public ImagePathType type;
        public Compressed compressed;
        public String path;
    }
}
