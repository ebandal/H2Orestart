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
package soffice;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.sun.star.frame.XDesktop;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.text.XText;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.XComponentContext;

import HwpDoc.HwpDetectException;
import HwpDoc.HwpDocInfo;
import HwpDoc.HwpFile;
import HwpDoc.HwpSection;
import HwpDoc.HwpxFile;
import HwpDoc.Exception.CompoundDetectException;
import HwpDoc.Exception.CompoundParseException;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.Exception.OwpmlParseException;
import HwpDoc.HwpElement.HwpRecord_BinData;
import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_Numbering;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.HwpElement.HwpRecord_Style;
import HwpDoc.HwpElement.HwpRecord_TabDef;
import HwpDoc.HwpElement.HwpRecord_BinData.Type;

public class WriterContext {
	private static final Logger log = Logger.getLogger(WriterContext.class.getName());

	private static HanType hType;
	public static HwpFile  hwp	= null;
    public static HwpxFile hwpx = null;
	public static int version;
	
	public XDesktop mDesktop = null;
	public XComponentContext mContext = null;
	public XMultiComponentFactory mMCF = null;
	public XMultiServiceFactory mMSF = null;
	public XTextDocument mMyDocument = null;
	public XText mText = null;
	public XTextCursor mTextCursor = null;

	public WriterContext()  { }

	public void setFile(String inputFile) {
	    try {
            if (inputFile.endsWith(".hwp")) {
                hType = HanType.HWP;
                hwp = new HwpFile(inputFile);
            } else if (inputFile.endsWith(".hwpx")) {
                hType = HanType.HWPX;
                hwpx = new HwpxFile(inputFile);
            }
	    } catch (FileNotFoundException e) {
	        hType = HanType.NONE;
	    }
	}
	
	public List<HwpSection> getSections() throws HwpDetectException {
	    List<HwpSection> sections = null;
	    switch(hType) {
	    case HWP:
	        sections = hwp.getSections();
	        break;
	    case HWPX:
	        sections = hwpx.getSections();
	        break;
        case NONE:
            throw new HwpDetectException();
	    }
	    return sections;
	}
	
	public void detect() throws HwpDetectException, CompoundDetectException, NotImplementedException, IOException, CompoundParseException, ParserConfigurationException, SAXException, DataFormatException {
        switch(hType) {
        case HWP:
            hwp.detect();
            break;
        case HWPX:
            hwpx.detect();
            break;
        case NONE:
            throw new HwpDetectException();
        }
	}
	
	public void open() throws HwpDetectException, CompoundDetectException, IOException, DataFormatException, HwpParseException, NotImplementedException, CompoundParseException, ParserConfigurationException, SAXException, OwpmlParseException {
        switch(hType) {
        case HWP:
            hwp.open();
            break;
        case HWPX:
            hwpx.open();
            break;
        case NONE:
            throw new HwpDetectException();
        }
	}
	
	public void close() throws IOException, HwpDetectException {
	    if (hType!=null) {
            switch(hType) {
            case HWP:
                hwp.close();
                break;
            case HWPX:
                hwpx.close();
                break;
            case NONE:
                throw new HwpDetectException();
            }
	    }
	}
	
	public HwpDocInfo getDocInfo() {
	    HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        return docInfo;
	}
	
	public static HwpRecord_BorderFill getBorderFill(short id) {
	    HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

		return (id>0?(HwpRecord_BorderFill) docInfo.borderFillList.get(id-1):null);
	}
	
	public HwpRecord_ParaShape getParaShape(int id) {
	    HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        if (id >= 0 && id < docInfo.paraShapeList.size()) {
            return (HwpRecord_ParaShape) docInfo.paraShapeList.get(id);
        } else {
        	return null;
        }
	}
	
	public HwpRecord_Style getParaStyle(short id) {
	    HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

		return (HwpRecord_Style) docInfo.styleList.get(id);
	}
	
	public HwpRecord_CharShape getCharShape(int id) {
       HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        if (id >= 0 && id < docInfo.charShapeList.size()) {
        	return (HwpRecord_CharShape) docInfo.charShapeList.get(id);
        } else {
        	return null;
        }
	}

	public HwpRecord_Numbering getNumbering(short id) {
       HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

		return (HwpRecord_Numbering) docInfo.numberingList.get(id);
	}
	
	public HwpRecord_Bullet getBullet(short id) {
       HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

		return (HwpRecord_Bullet) docInfo.bulletList.get(id-1);
	}
	
	public String getBinFilename(String id) {
		String retString = "";
		HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

		HwpRecord_BinData binData = (HwpRecord_BinData)docInfo.binDataList.get(id);
		retString = binData.aPath;
		/*
		String compoundFileName = String.format("BIN%04X.%s", binData.binDataID, binData.format);
		try {
			retString = hwp.saveChildEntry(getWorkingFolder(), compoundFileName, binData.compressed);
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/
		return retString;
	}
	
	public byte[] getBinBytes(short id) {
		byte[] imageBytes = null;
		HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            HwpRecord_BinData binData = (HwpRecord_BinData)docInfo.binDataList.get(id);
            if (binData != null) {
                if (binData.type==Type.LINK) {
                    File file = new File(binData.aPath);
                    try {
                        imageBytes = Files.readAllBytes(file.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    if (hwp.getBinData().size() >= binData.binDataID) {
                        String compoundFileName = String.format("BIN%04X.%s", binData.binDataID, binData.format);
                        try {
                            imageBytes = hwp.getChildBytes(compoundFileName, binData.compressed);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

		return imageBytes;
	}

	public static String getBinFormat(short id) {
        HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
	    
		return ((HwpRecord_BinData)docInfo.binDataList.get(id-1)).format;
	}
	
	public static HwpRecord_TabDef getTabDef(short id) {
        HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
		return (HwpRecord_TabDef) docInfo.tabDefList.get(id);
	}

	public static void cleanTempFolder() throws IOException {
		if (hwp!=null) {
			hwp.removeSaveFolder(Paths.get(System.getProperty("user.home"),".H2Orestart"));
		}
	}

	public static Path getWorkingFolder() {
		Path path = null;
		try {
			path = Files.createDirectories(Paths.get(System.getProperty("user.home"),".H2Orestart"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return path;
	}

	public static Path makeTempFile(String fileName) {
		Path path = null;
		try {
			path = Files.createDirectories(Paths.get(System.getProperty("user.home"),".H2Orestart"));
			path = Paths.get(path.toString(), fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return path;
	}
	
	public static enum HanType {
	    NONE   (0x0),
	    HWP    (0x1),
	    HWPX   (0x2);

        private int num;
        private HanType(int num) { 
            this.num = num;
        }
	}
}
