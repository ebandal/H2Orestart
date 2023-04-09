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
	
	public HwpRecord_ParaShape getParaShape(short id) {
	    HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        return (HwpRecord_ParaShape) docInfo.paraShapeList.get(id);
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
	
	public HwpRecord_CharShape getCharShape(short id) {
       HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        if (id < docInfo.charShapeList.size()) {
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
	
	public String getBinFilename(short id) {
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

		HwpRecord_BinData binData = (HwpRecord_BinData)docInfo.binDataList.get(String.valueOf(id));
		if (binData.type==Type.LINK) {
			retString = binData.aPath;
		} else {
			String compoundFileName = String.format("BIN%04X.%s", binData.binDataID, binData.format);
			try {
				retString = hwp.saveChildEntry(getWorkingFolder(), compoundFileName, binData.compressed);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return retString;
	}
	
	public byte[] getBinBytes(short id) {
		byte[] imageBytes = null;
		HwpDocInfo docInfo = null;
        switch(hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            HwpRecord_BinData binData = (HwpRecord_BinData)docInfo.binDataList.get(String.valueOf(id));
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
