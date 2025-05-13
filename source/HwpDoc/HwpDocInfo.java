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
package HwpDoc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.HwpElement.HwpRecord;
import HwpDoc.HwpElement.HwpRecord_BinData;
import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_DocumentProperties;
import HwpDoc.HwpElement.HwpRecord_FaceName;
import HwpDoc.HwpElement.HwpRecord_IdMapping;
import HwpDoc.HwpElement.HwpRecord_Numbering;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.HwpElement.HwpRecord_Style;
import HwpDoc.HwpElement.HwpRecord_TabDef;
import HwpDoc.HwpElement.HwpTag;

public class HwpDocInfo {
	private static final Logger log = Logger.getLogger(HwpDocInfo.class.getName());
	public HanType         hanType;
	private HwpxFile       parentHwpx;
	private HwpFile        parentHwp;
	public List<HwpRecord> recordList;
	
	public LinkedHashMap<String, HwpRecord> binDataList;
	public List<HwpRecord> faceNameList;
	public List<HwpRecord> borderFillList;
	public List<HwpRecord> charShapeList;
	public List<HwpRecord> numberingList;
	public List<HwpRecord> bulletList;
	public List<HwpRecord> paraShapeList;
	public List<HwpRecord> styleList;
	public List<HwpRecord> tabDefList;
	public CompatDoc       compatibleDoc;
	
    public HwpDocInfo(HanType hanType) {
        recordList      = new ArrayList<HwpRecord>();
        binDataList     = new LinkedHashMap<String, HwpRecord>();
        faceNameList    = new ArrayList<HwpRecord>();
        borderFillList  = new ArrayList<HwpRecord>();
        charShapeList   = new ArrayList<HwpRecord>();
        numberingList   = new ArrayList<HwpRecord>();
        bulletList      = new ArrayList<HwpRecord>();
        paraShapeList   = new ArrayList<HwpRecord>();
        styleList       = new ArrayList<HwpRecord>();
        tabDefList      = new ArrayList<HwpRecord>();
        compatibleDoc   = CompatDoc.HWP;
        this.hanType    = hanType; 
    }

    public HwpDocInfo(HwpxFile parent) {
        this(HanType.HWPX);
		this.parentHwpx = parent;
	}
    
    public HwpDocInfo(HwpFile parent) {
        this(HanType.HWP);
        this.parentHwp = parent;
    }
	
	boolean parse(byte[] buf, int version) throws HwpParseException {
		int off = 0;
		while(off < buf.length) {
			int header = buf[off+3]<<24&0xFF000000 | buf[off+2]<<16&0xFF0000 | buf[off+1]<<8&0xFF00 | buf[off]&0xFF;
			int tagNum = header&0x3FF;				// 10 bits (0 - 9 bit)
			int level = (header&0xFFC00)>>>10;		// 10 bits (10-19 bit)
			int size =  (header&0xFFF00000)>>>20;	// 12 bits (20-31 bit)
			
			if (size==0xFFF) {
				size = buf[off+7]<<24&0xFF000000 | buf[off+6]<<16&0xFF0000 | buf[off+5]<<8&0xFF00 | buf[off+4]&0xFF;
				off += 8;
			} else {
				off += 4;
			}
			
			HwpRecord record = null;
			HwpTag tag = HwpTag.from(tagNum);
			log.fine(IntStream.rangeClosed(0, level).mapToObj(i -> String.valueOf(i)).collect(Collectors.joining())+"[TAG]="+tag.toString()+" ("+size+")");
			switch(tag) {
			case HWPTAG_DOCUMENT_PROPERTIES:
				record = new HwpRecord_DocumentProperties(this, tagNum, level, size, buf, off, version);
				recordList.add(record);
				break;
			case HWPTAG_ID_MAPPINGS:
				record = new HwpRecord_IdMapping(this, tagNum, level, size, buf, off, version);
				recordList.add(record);
				break;
			case HWPTAG_BIN_DATA:
			    HwpRecord_BinData binRecord = new HwpRecord_BinData(this, tagNum, level, size, buf, off, version);
				binDataList.put(binRecord.itemId, binRecord);
				break;
			case HWPTAG_FACE_NAME:
				record = new HwpRecord_FaceName(this, tagNum, level, size, buf, off, version);
				faceNameList.add(record);
				break;
			case HWPTAG_BORDER_FILL:
				record = new HwpRecord_BorderFill(this, tagNum, level, size, buf, off, version);
				borderFillList.add(record);
				break;
			case HWPTAG_CHAR_SHAPE:
				record = new HwpRecord_CharShape(this, tagNum, level, size, buf, off, version);
				charShapeList.add(record);
				break;
			case HWPTAG_TAB_DEF:
				record = new HwpRecord_TabDef(this, tagNum, level, size, buf, off, version);
				tabDefList.add(record);
				break;
			case HWPTAG_NUMBERING:
				record = new HwpRecord_Numbering(this, tagNum, level, size, buf, off, version);
				numberingList.add(record);
				break;
			case HWPTAG_BULLET:
				record = new HwpRecord_Bullet(this, tagNum, level, size, buf, off, version);
				bulletList.add(record);
				break;
			case HWPTAG_PARA_SHAPE:
				record = new HwpRecord_ParaShape(this, tagNum, level, size, buf, off, version);
				paraShapeList.add(record);
				break;
			case HWPTAG_STYLE:
				record = new HwpRecord_Style(this, tagNum, level, size, buf, off, version);
				styleList.add(record);
				break;
            case HWPTAG_COMPATIBLE_DOCUMENT:
                compatibleDoc = CompatDoc.from(buf[off+3]<<24&0xFF000000 | buf[off+2]<<16&0x00FF0000 | buf[off+1]<<8&0x0000FF00 | buf[off]&0x000000FF);
                break;
            case HWPTAG_LAYOUT_COMPATIBILITY:
                break;
			case HWPTAG_DOC_DATA:
			case HWPTAG_DISTRIBUTE_DOC_DATA:			    
			case HWPTAG_TRACKCHANGE:
			case HWPTAG_MEMO_SHAPE:
			case HWPTAG_FORBIDDEN_CHAR:
			case HWPTAG_TRACK_CHANGE:
			case HWPTAG_TRACK_CHANGE_AUTHOR:
				break;
			default:
			}
			off += size;
		}
		
		return true;
	}
	
	boolean readContentHpf(Document document, int version) throws HwpParseException, NotImplementedException {
        Element element = document.getDocumentElement();
        
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            
            HwpRecord_BinData record = null;
            switch(node.getNodeName()) {
            case "opf:metadata":
                break;
            case "opf:manifest":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "opf:item":
                            record = new HwpRecord_BinData(childNode, version);
                            binDataList.put(record.itemId, record);
                            break;
                        // case "#text":
                        default:
                            break;
                        }
                    }
                }
                break;
            }
        }
        
        return true;
	}
	
	boolean read(Document document, int version) throws HwpParseException, NotImplementedException {
	    int off = 0;
        
        Element element = document.getDocumentElement();
        
        // Node : [[hh:beginNum: null], [hh:refList: null], [hh:compatibleDocument: null], [hh:docOption: null], [hh:trackchageConfig: null]]
        
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            
            HwpRecord record = null;
            switch(node.getNodeName()) {
            case "hh:beginNum":
                record = new HwpRecord_DocumentProperties(this, node, version);
                recordList.add(record);
                break;
            case "hh:refList":
                readRefList(node, version);
                break;
            case "hh:compatibleDocument":
                break;
            case "hh:docOption":
                break;
            case "hh:trackchageConfig":
                break;
            case "hh:forbiddenWordList":
                break;
            }
            
        }
        
        return true;
    }
    
    private boolean readRefList(Node rootNode, int version) throws HwpParseException, NotImplementedException {
        NodeList nodeList = rootNode.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            
            HwpRecord record = null;
            switch(node.getNodeName()) {
            case "hh:fontfaces":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "hh:fontface":
                            NamedNodeMap attributes = childNode.getAttributes();
                            String lang = attributes.getNamedItem("lang").getNodeValue();   // [fontCnt="6", lang="HANGUL"]
                            
                            NodeList childNodeList = childNode.getChildNodes();
                            for (int k=0; k<childNodeList.getLength(); k++) {
                                Node grandChild = childNodeList.item(k);
                                switch(grandChild.getNodeName()) {
                                case "hh:font":
                                    record = new HwpRecord_FaceName(this, grandChild, version);
                                    faceNameList.add(record);
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                break;
            case "hh:borderFills":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "hh:borderFill":
                            record = new HwpRecord_BorderFill(this, childNode, version);
                            borderFillList.add(record);
                            break;
                        }
                    }
                }
                break;
            case "hh:charProperties":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "hh:charPr":
                            record = new HwpRecord_CharShape(this, childNode, version);
                            charShapeList.add(record);
                            break;
                        }
                    }
                }
                break;
            case "hh:tabProperties":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "hh:tabPr":
                            record = new HwpRecord_TabDef(this, childNode, version);
                            tabDefList.add(record);
                            break;
                        }
                    }
                }
                break;
            case "hh:numberings":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "hh:numbering":
                            record = new HwpRecord_Numbering(this, childNode, version);
                            numberingList.add(record);
                            break;
                        }
                    }
                }
                break;
            case "hh:bullets":
            	{
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                    	Node childNode = children.item(j);
		                record = new HwpRecord_Bullet(this, childNode, version);
		                bulletList.add(record);
                    }
            	}
                break;
            case "hh:paraProperties":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "hh:paraPr":
                            record = new HwpRecord_ParaShape(this, childNode, version);
                            paraShapeList.add(record);
                            break;
                        }
                    }
                }
                break;
            case "hh:styles":
                {
                    NodeList children = node.getChildNodes();
                    for (int j=0; j<children.getLength(); j++) {
                        Node childNode = children.item(j);
                        switch(childNode.getNodeName()) {
                        case "hh:style":
                            record = new HwpRecord_Style(this, childNode, version);
                            styleList.add(record);
                            break;
                        }
                    }
                }
                break;
            case "hh:memoProperties":
                break;
            case "hh:trackChanges":
                break;
            case "hh:trackChangeAuthros":
                break;
            }
        }
        
        return true;
    }
    
    public void setFontNameLineSpaceAlpha(String fontName, double fontLineSpaceAlpha) {
    	charShapeList.stream().filter(l -> l instanceof HwpRecord_CharShape)
    				.filter(l -> {
    						return Arrays.stream(((HwpRecord_CharShape)l).fontName).anyMatch(c -> c.equals(fontName));
    					})
    				.forEach(c -> {
    						((HwpRecord_CharShape)c).lineSpaceAlpha = fontLineSpaceAlpha;	
    					});
    }

    public HwpFile getParentHwp() {
        return parentHwp;
    }

	public static enum CompatDoc {
	    HWP         (0x0),  // 한글문서(현재버전)
        OLD_HWP     (0x1),  // 한글2007호환
        MS_WORD     (0x2),  // MS 워드 호환
        ;
        
        private int num;
        private CompatDoc(int num) { 
            this.num = num;
        }
        public static CompatDoc from(int num) {
            for (CompatDoc type: values()) {
                if (type.num == num)
                    return type;
            }
            return null;
        }
	}

}
