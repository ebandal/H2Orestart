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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
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

import HwpDoc.HanType;
import HwpDoc.HwpDetectException;
import HwpDoc.HwpDocInfo;
import HwpDoc.HwpFile;
import HwpDoc.HwpSection;
import HwpDoc.HwpxFile;
import HwpDoc.IContext;
import HwpDoc.Exception.CompoundDetectException;
import HwpDoc.Exception.CompoundParseException;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.Exception.OwpmlParseException;
import HwpDoc.HwpElement.HwpRecord;
import HwpDoc.HwpElement.HwpRecord_BinData;
import HwpDoc.HwpElement.HwpRecord_BorderFill;
import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_FaceName;
import HwpDoc.HwpElement.HwpRecord_Numbering;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.HwpElement.HwpRecord_Style;
import HwpDoc.HwpElement.HwpRecord_TabDef;
import HwpDoc.HwpElement.HwpRecord_BinData.Type;

public class WriterContext implements IContext {
    private static final Logger log = Logger.getLogger(WriterContext.class.getName());

    private static HanType hType;
    public static HwpFile hwp = null;
    public static HwpxFile hwpx = null;
    public static int version;
    public static Set<String> fontNameSet = new HashSet<String>();

    public XDesktop mDesktop = null;
    public XComponentContext mContext = null;
    public XMultiComponentFactory mMCF = null;
    public XMultiServiceFactory mMSF = null;
    public XTextDocument mMyDocument = null;
    public XText mText = null;
    public XTextCursor mTextCursor = null;
    public Path userHomeDir = null;

    public WriterContext() {
    }

    public List<HwpSection> getSections() throws HwpDetectException {
        List<HwpSection> sections = null;
        switch (hType) {
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

    public static String detectHancom(File file) {
        String detectingType = null;

        try {
            HwpxFile hwpxTemp = new HwpxFile(file);
            hwpxTemp.detect();
            detectingType = "HWPX";
            hwpxTemp.close();
            log.info("file detected as HWPX");
        } catch (IOException | HwpDetectException e1) {
            log.info("file detected not HWPX");

            try {
                HwpFile hwpTemp = new HwpFile(file);
                hwpTemp.detect();
                detectingType = "HWP";
                hwpTemp.close();
                log.info("file detected as HWP");
            } catch (IOException | HwpDetectException e2) {
                log.info("file detected neither HWPX nor HWP");
            }
        }

        return detectingType;
    }

    public void detect() throws HwpDetectException, CompoundDetectException, NotImplementedException, IOException,
                                CompoundParseException, ParserConfigurationException, SAXException, DataFormatException {
        switch (hType) {
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

    public void open(String inputFile, String hanTypeStr) throws HwpDetectException, CompoundDetectException,
                                                                IOException, DataFormatException, HwpParseException, 
                                                                NotImplementedException, CompoundParseException, ParserConfigurationException,
                                                                SAXException, OwpmlParseException {
        switch (hanTypeStr) {
        case "HWP":
            hType = HanType.HWP;
            hwp = new HwpFile(inputFile);
            hwp.open();
            break;
        case "HWPX":
            hType = HanType.HWPX;
            hwpx = new HwpxFile(inputFile);
            hwpx.open(this);
            break;
        default:
            throw new HwpDetectException();
        }
    }

    public void open(File inputFile, String hanTypeStr) throws HwpDetectException, CompoundDetectException,
                                                                 IOException, DataFormatException, HwpParseException, 
                                                                 NotImplementedException, CompoundParseException, ParserConfigurationException,
                                                                 SAXException, OwpmlParseException {
        switch (hanTypeStr) {
        case "HWP":
            hType = HanType.HWP;
            hwp = new HwpFile(inputFile);
            hwp.open();
            break;
        case "HWPX":
            hType = HanType.HWPX;
            hwpx = new HwpxFile(inputFile);
            hwpx.open(this);
            break;
        default:
            throw new HwpDetectException();
        }
    }

    public void close() throws IOException, HwpDetectException {
        if (hType != null) {
            switch (hType) {
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
        fontNameSet.clear();
    }

    public HwpDocInfo getDocInfo() {
        HwpDocInfo docInfo = null;
        switch (hType) {
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
        switch (hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

        return (id > 0 ? (HwpRecord_BorderFill) docInfo.borderFillList.get(id - 1) : null);
    }

    public HwpRecord_ParaShape getParaShape(int id) {
        HwpDocInfo docInfo = null;
        switch (hType) {
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
        switch (hType) {
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
        switch (hType) {
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
        switch (hType) {
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
        switch (hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }

        return (HwpRecord_Bullet) docInfo.bulletList.get(id - 1);
    }

    public String getBinFilename(String id) {
        HwpRecord_BinData binData = null;
        String retString = "";
        HwpDocInfo docInfo = null;
        switch (hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            ArrayList<String> keyList = new ArrayList<String>(docInfo.binDataList.keySet());
            String key = keyList.get(Integer.parseInt(id));
            binData = (HwpRecord_BinData) docInfo.binDataList.get(key);
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            binData = (HwpRecord_BinData) docInfo.binDataList.get(id);
            break;
        }

        retString = binData.aPath;
        /*
         * String compoundFileName = String.format("BIN%04X.%s", binData.binDataID,
         * binData.format); try { retString = hwp.saveChildEntry(getWorkingFolder(),
         * compoundFileName, binData.compressed); } catch (IOException e) {
         * e.printStackTrace(); }
         */
        return retString;
    }

    public byte[] getBinBytes(String id) {
        byte[] imageBytes = null;
        HwpDocInfo docInfo = null;
        switch (hType) {
        case HWP: {
            docInfo = hwp.getDocInfo();
            ArrayList<String> keyList = new ArrayList<String>(docInfo.binDataList.keySet());
            String key = keyList.get(Integer.parseInt(id));
            HwpRecord_BinData binData = (HwpRecord_BinData) docInfo.binDataList.get(key);
            if (binData != null) {
                if (binData.type == Type.LINK) {
                    File file = new File(binData.aPath);
                    try {
                        imageBytes = Files.readAllBytes(file.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
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
        case HWPX: {
            docInfo = hwpx.getDocInfo();
            HwpRecord_BinData binData = (HwpRecord_BinData) docInfo.binDataList.get(id);
            if (binData != null) {
                try {
                    String binShortName = binData.aPath.replaceAll("BinData/(.*)\\..*", "$1");
                    imageBytes = hwpx.getBinDataByIDRef(binShortName);
                } catch (IOException | DataFormatException e) {
                    e.printStackTrace();
                }
            }
        }
            break;
        }

        return imageBytes;
    }

    public String getBinFormat(String id) {
        HwpRecord_BinData binData = null;
        HwpDocInfo docInfo = null;
        switch (hType) {
        case HWP: {
            docInfo = hwp.getDocInfo();
            ArrayList<String> keyList = new ArrayList<String>(docInfo.binDataList.keySet());
            String key = keyList.get(Integer.parseInt(id));
            binData = (HwpRecord_BinData) docInfo.binDataList.get(key);
        }
            break;
        case HWPX: {
            docInfo = hwpx.getDocInfo();
            binData = (HwpRecord_BinData) docInfo.binDataList.get(id);
        }
            break;
        }

        return binData==null ? null : binData.format;
    }

    public static HwpRecord_TabDef getTabDef(short id) {
        HwpDocInfo docInfo = null;
        switch (hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        return (HwpRecord_TabDef) docInfo.tabDefList.get(id);
    }
    
    public static List<HwpRecord_FaceName> getFontNames() {
        HwpDocInfo docInfo = null;
        switch (hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        return docInfo.faceNameList.stream().map(r -> (HwpRecord_FaceName)r).collect(Collectors.toList());
    }

    public static void setFontNameLineSpaceAlpha(String faceName, double fontLineSpaceAlpha) {
        HwpDocInfo docInfo = null;
        switch (hType) {
        case HWP:
            docInfo = hwp.getDocInfo();
            break;
        case HWPX:
            docInfo = hwpx.getDocInfo();
            break;
        }
        docInfo.setFontNameLineSpaceAlpha(faceName, fontLineSpaceAlpha);
    }
    
    @Override
    public HwpxFile getHwpx() {
    	return hwpx;
    }

}
