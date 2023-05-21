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
package HwpDoc.HwpElement;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import HwpDoc.HwpDocInfo;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.HwpElement.HwpRecordTypes.LineStyle2;

public class HwpRecord_TabDef extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_TabDef.class.getName());
	private HwpDocInfo	parent;
	
	public int 	 		attr;
	public int 			count;
	public List<Tab>	tabs;
	
	HwpRecord_TabDef(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public HwpRecord_TabDef(HwpDocInfo docInfo, int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		this(tagNum, level, size);
		this.parent = docInfo;

		int offset = off;
		attr = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;
		count = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
		offset += 4;          

		if (size-(offset-off)!=count*8) {
			throw new HwpParseException();
		}
		tabs = new ArrayList<Tab>();

		for (int i=0; i<count; i++) {
			// LibreOffice에서는 ParaTabStops 로 정의하며, 아래와 같은 형식을 가진다. 필요한 경우 활용하자. 
	  		// com.sun.star.style.TabStop[] tss = new com.sun.star.style.TabStop[1];
	  		// tss[0] = new com.sun.star.style.TabStop();
			// tss[0].Position = 1251;
			// tss[0].Alignment = TabAlign.DEFAULT;
			// tss[0].DecimalChar = 46; // .
			// tss[0].FillChar = 32; // Space
			// paraProps.setPropertyValue("ParaTabStops", tss);
			
			Tab tab = new Tab();
			tab.pos		= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
			offset += 4;
			tab.type 	= Tab.Type.from(buf[offset++]);
			tab.leader	= LineStyle2.from(buf[offset++]);
			// reserved 2 bytes for align
			offset += 2;
			tabs.add(tab);
		}
		
		log.fine("                                                  "
				+"ID="+(parent.tabDefList.size())
				+",갯수="+count
				+",속성="+String.format("0x%X", attr)
				+(count>0?",탭[0]="+tabs.get(0).pos+":"+tabs.get(0).type.toString()+":"+tabs.get(0).leader:"")
				+(count>1?",탭[1]="+tabs.get(1).pos+":"+tabs.get(1).type.toString()+":"+tabs.get(1).leader:"")
				+(count>2?",탭[2]="+tabs.get(2).pos+":"+tabs.get(2).type.toString()+":"+tabs.get(2).leader:"")
				+",왼쪽끝자동="+(attr&0x1)+",오른쪽끝자동="+(attr&0x2)
		 	);

		if (offset-off-size!=0) {
			throw new HwpParseException();
		}
	}
	
	public HwpRecord_TabDef(HwpDocInfo docInfo, Node node, int version) {
        super(HwpTag.HWPTAG_TAB_DEF, 0, 0);
        this.parent = docInfo;
        
        dumpNode(node, 1);
        
        NamedNodeMap attributes = node.getAttributes();
        
        // id는 처리하지 않는다. List<HwpRecord_TabDef>에 순차적으로 추가한다. 
        // String id = attributes.getNamedItem("id").getNodeValue();
        
        switch(attributes.getNamedItem("autoTabLeft").getNodeValue()) {
        case "0":
            attr &= 0xFFFFFFFE;
            break;
        case "1":
            attr |= 0x00000001;
            break;
        }

        switch(attributes.getNamedItem("autoTabRight").getNodeValue()) {
        case "0":
            attr &= 0xFFFFFFFD;
            break;
        case "1":
            attr |= 0x00000002;
            break;
        }
        
        String numStr = null;
        tabs = new ArrayList<Tab>();
        
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            
            switch(child.getNodeName()) {
            case "hh:tabItem":
                {
                    Tab tabItem = new Tab();
                    NamedNodeMap childAttrs = child.getAttributes();
                    
                    numStr = childAttrs.getNamedItem("pos").getNodeValue();
                    tabItem.pos = Integer.parseInt(numStr);
                    tabItem.type = Tab.Type.valueOf(childAttrs.getNamedItem("type").getNodeValue());
                    tabItem.leader = LineStyle2.valueOf(childAttrs.getNamedItem("leader").getNodeValue());

                    tabs.add(tabItem);
                }
            }
        }
    }

    public static class Tab {
		public int 			pos;
		public Type			type;
		public LineStyle2	leader;
		
		public static enum Type {
			LEFT		(0),
			RIGHT		(1),
			CENTER		(2),
			DECIMAL		(3);
	
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
		
	}


}
