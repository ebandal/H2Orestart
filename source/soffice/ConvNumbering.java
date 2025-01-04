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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.sun.star.awt.FontDescriptor;
import com.sun.star.awt.FontSlant;
import com.sun.star.awt.Size;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexReplace;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNameContainer;
import com.sun.star.style.NumberingType;
import com.sun.star.style.XStyle;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.text.HoriOrientation;
import com.sun.star.text.LabelFollow;
import com.sun.star.text.PositionAndSpaceMode;
import com.sun.star.text.VertOrientation;
import com.sun.star.text.XParagraphCursor;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_Numbering;
import HwpDoc.HwpElement.HwpRecord_Numbering.Numbering;
import HwpDoc.paragraph.Ctrl_SectionDef;

public class ConvNumbering {
    private static final Logger log = Logger.getLogger(ConvNumbering.class.getName());

    public static Map<Integer, String> numberingStyleNameMap = new HashMap<Integer, String>();
    public static Map<Integer, String> bulletStyleNameMap = new HashMap<Integer, String>();
    private static final String NUMBERING_STYLE_PREFIX = "HWP numbering ";
    private static final String BULLET_STYLE_PREFIX = "HWP bullet ";

    // UNOAPI를 사용하지 않는 경우, 현재 numbering 값을 가져오기 위해 사용
    private static Map<String, Integer[]> numberingNumbersMap = new HashMap<String, Integer[]>();
    private static Map<String, Integer> prevNumberingLevelMap = new HashMap<String, Integer>();
    private static Map<String, Integer[]> bulletNumbersMap = new HashMap<String, Integer[]>();

    public static void reset(WriterContext wContext) {
        deleteCustomStyles(wContext);
    }

    private static void deleteCustomStyles(WriterContext wContext) {
        if (wContext.mMyDocument!=null) {
            try {
                XStyleFamiliesSupplier xSupplier = (XStyleFamiliesSupplier)UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
                XNameAccess xFamilies = (XNameAccess) UnoRuntime.queryInterface (XNameAccess.class, xSupplier.getStyleFamilies());

                XNameContainer xNumeringFamily = (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class, xFamilies.getByName("NumberingStyles"));
                for (Integer custIndex: numberingStyleNameMap.keySet()) {
                    if (xNumeringFamily.hasByName(numberingStyleNameMap.get(custIndex))) {
                        xNumeringFamily.removeByName(numberingStyleNameMap.get(custIndex));
                    }
                }
                for (Integer custIndex: bulletStyleNameMap.keySet()) {
                    if (xNumeringFamily.hasByName(bulletStyleNameMap.get(custIndex))) {
                        xNumeringFamily.removeByName(bulletStyleNameMap.get(custIndex));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        numberingStyleNameMap.clear();
        bulletStyleNameMap.clear();
    }

    private static void setNumberingProp(PropertyValue[] aProps, int i, HwpRecord_Numbering numbering) {
        short numberingType = -1;
        short adjust = -1;
        short parentNumbering = -1;
        String prefix = "";
        String suffix = "";
        String charStyleName = "";
        String listFormat = "";
        short startsWith = -1;
        short positionAndSpaceMode = PositionAndSpaceMode.LABEL_ALIGNMENT;
        short labelFollowedBy = LabelFollow.SPACE;
        long listtabStopPosition = -1;
        long firstLineIndent = -1;
        long indentAt = -1;
        int charShapeId;
        
        // Hwp에서 수준(level)은 7개+확장3개,  LibreOffice에서 level은  10개이다.
        if (i<7) {
            Numbering numb = numbering.numbering[i];
            if (numb!=null) {
                adjust = numb.align==0x0?HoriOrientation.LEFT:numb.align==0x1?HoriOrientation.CENTER:numb.align==0x2?HoriOrientation.RIGHT:HoriOrientation.NONE;
                // boolean      useInstWidth;           // 문단머리정보 - 번호 너비를 실제 인스턴스 문자열의 너비에 따를지 여부
                // boolean      autoIndent;             // 문단머리정보 - 자동 내어 쓰기 여부
                // byte         textOffsetType;         // 문단머리정보 - 수준별 본문과의 거리 종류
                // short        widthAdjust;            // 문단머리정보 - 너비 보정값
                // short        textOffset;             // 문단머리정보 - 본문과의 거리
                // int          charShape;              // 문단머리정보 - 글자 모양 아이디 참조
                // int          start;
                // String       numFormat;              // 번호 형식
                parentNumbering = getParentNumbering(numb.numFormat, i);
                prefix = getPrefix(numb.numFormat);
                suffix = getSuffix(numb.numFormat);
                listFormat = getNumberFormat(numb.numFormat);
                numberingType = getNumberingType(numb.numFormat, i);
                charShapeId = numb.charShape+1;
                charStyleName = ConvPara.getCharStyleName(charShapeId);
                if (numb.textOffsetType==0x1) {         // 절대값 거리
                    indentAt = Transform.translateHwp2Office(numb.textOffset);
                    listtabStopPosition = indentAt/2;
                    firstLineIndent = -listtabStopPosition;
                } else {                                    // 상대값 거리 (글자의 몇%)
                    indentAt = 1400;
                    listtabStopPosition = indentAt/2;
                    firstLineIndent = -listtabStopPosition;
                }
                startsWith = (short)numbering.numbering[i].startNumber;
            }
        } else {
            startsWith = (short)numbering.extLevelStart[i-7];
            parentNumbering = getParentNumbering(numbering.extLevelFormat[i-7], i);
            prefix = getPrefix(numbering.extLevelFormat[i-7]);
            suffix = getSuffix(numbering.extLevelFormat[i-7]);
            numberingType = getNumberingType(numbering.extLevelFormat[i-7], i);
        }
        
        for (int j=0; j<aProps.length; j++) {
            switch(aProps[j].Name) {
            case "NumberingType":
                if (numberingType!=-1) {
                    aProps[j].Value = numberingType;
                }
                break;
            case "Adjust":
                if (adjust!=-1) {
                    aProps[j].Value = adjust;
                }
                break;
            case "ParentNumbering":
                if (parentNumbering!=-1) {
                    aProps[j].Value = parentNumbering;
                }
                break;
            case "Prefix":         // as of LibreOffice 7.2, use ListFormat instead
                aProps[j].Value = prefix;
                break;
            case "Suffix":         // as of LibreOffice 7.2, use ListFormat instead
                aProps[j].Value = suffix;
                break;
            case "CharStyleName":
                if (charStyleName!=null && charStyleName.equals("")==false) {
                    aProps[j].Value = charStyleName;
                }
                break;
            case "StartWith":
                if (startsWith!=-1) {
                    aProps[j].Value = startsWith;
                }
                break;
            case "PositionAndSpaceMode":
                aProps[j].Value = positionAndSpaceMode;
                break;
            case "LabelFollowedBy":
                aProps[j].Value = labelFollowedBy;
                break;
            case "ListtabStopPosition":
                if (listtabStopPosition!=-1) {
                    aProps[j].Value = listtabStopPosition;
                }
                break;
            case "FirstLineIndent":
                if (firstLineIndent!=-1) {
                    aProps[j].Value = firstLineIndent;
                }
                break;
            case "IndentAt":
                if (indentAt!=-1) {
                    aProps[j].Value = indentAt;
                }
            case "ListFormat":     // since LibreOffice 7.2
                aProps[j].Value = listFormat;
                break;
            }
        }
    }

    public static void makeCustomNumberingStyle(WriterContext wContext, int id, HwpRecord_Numbering numbering) {
        try {
            XStyle xListStyle = UnoRuntime.queryInterface(XStyle.class, wContext.mMSF.createInstance("com.sun.star.style.NumberingStyle"));
            XStyleFamiliesSupplier xSupplier = (XStyleFamiliesSupplier)UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
            XNameAccess xFamilies = (XNameAccess) UnoRuntime.queryInterface (XNameAccess.class, xSupplier.getStyleFamilies());
            XNameContainer xFamily = (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class, xFamilies.getByName("NumberingStyles"));

            String hwpStyleName = NUMBERING_STYLE_PREFIX + id;
            if (xFamily.hasByName(hwpStyleName)==false) {
                xFamily.insertByName (hwpStyleName, xListStyle);
            }
            numberingStyleNameMap.put(id, hwpStyleName);

            XPropertySet xStyleProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xFamily.getByName(hwpStyleName));
            XIndexReplace xReplace = (XIndexReplace) UnoRuntime.queryInterface(XIndexReplace.class, xStyleProps.getPropertyValue("NumberingRules"));

            for (int i=0; i < xReplace.getCount(); i++) {
                if (numbering.numbering[i]!=null && 
                    numbering.numbering[i].numFormat!=null &&
                    numbering.numbering[i].numFormat.equals("")==false) {
                    PropertyValue[] aProps = (PropertyValue []) xReplace.getByIndex(i);
                    setNumberingProp(aProps, i, numbering);
                    xReplace.replaceByIndex(i, aProps);
                }
            }
            // NumberingRules 속성을 설정해야  Style이 변경된다. 
            XPropertySet xCursorProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);
            xCursorProps.setPropertyValue("NumberingRules", xReplace);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getNumberingHead(String hwpStyleName, HwpRecord_Numbering numbering, int headingLevel) {
        
        Integer[] curNumbers = numberingNumbersMap.get(hwpStyleName);
        Integer prevLevel = prevNumberingLevelMap.get(hwpStyleName);
        if (prevLevel == null) {
            prevLevel = 0;
        }
        curNumbers[headingLevel] += 1;

        StringBuffer sb = new StringBuffer();

        // Hwp에서 수준(level)은 7개+확장3개,  LibreOffice에서 level은  10개이다.
        if (headingLevel<7) {
            Numbering numb = numbering.numbering[headingLevel];
            
            // ""
            // "^2."
            // "^2.^3."
            // "^2.^3.^4"

            if (numb.numFormat!=null && numb.numFormat.equals("")==false) {
                Pattern pattern = Pattern.compile("\\^\\d+");
                Matcher m = pattern.matcher(numb.numFormat);

                int prevEnd = 0;
                
                while(m.find()) {
                    int s = m.start();
                    int e = m.end();

                    int num = Integer.parseInt(numb.numFormat.substring(s+1, e));
                    if (num <= headingLevel+1) {
                        sb.append(numb.numFormat.subSequence(prevEnd, s));
                        sb.append(curNumbers[num-1]);
                    }
                    prevEnd = e;
                }
                sb.append(numb.numFormat.substring(prevEnd));
            }
        } else {
            //
        }

        // 현재 headingLevel값은 1증가
        // curNumbers[headingLevel] += 1;

        // 하위 headingLevel은 1로 초기화
        for (int i=headingLevel+1; i<curNumbers.length; i++) {
            curNumbers[i] = 0;
        }
        prevNumberingLevelMap.put(hwpStyleName, headingLevel);
        
        return sb.toString();
    }

    public static void makeCustomNumberingStyle(int id, HwpRecord_Numbering numbering) {
        String hwpStyleName = NUMBERING_STYLE_PREFIX + id;
        numberingStyleNameMap.put(id, hwpStyleName);

        Integer[] curNumbers = new Integer[10];
        for (int i=0; i< curNumbers.length; i++) {
            curNumbers[i] = 0;
        }
        numberingNumbersMap.put(hwpStyleName, curNumbers);
    }

    public static void makeCustomBulletStyle(WriterContext wContext, int id, HwpRecord_Bullet bullet) {
        try {
            XStyle xListStyle = UnoRuntime.queryInterface(XStyle.class, wContext.mMSF.createInstance("com.sun.star.style.NumberingStyle"));
            XStyleFamiliesSupplier xSupplier = (XStyleFamiliesSupplier)UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
            XNameAccess xFamilies = (XNameAccess) UnoRuntime.queryInterface (XNameAccess.class, xSupplier.getStyleFamilies());
            XNameContainer xFamily = (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class, xFamilies.getByName("NumberingStyles"));

            String hwpStyleName = BULLET_STYLE_PREFIX + id;
            if (xFamily.hasByName(hwpStyleName)==false) {
                xFamily.insertByName (hwpStyleName, xListStyle);
            }
            bulletStyleNameMap.put(id, hwpStyleName);

            XPropertySet xStyleProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xFamily.getByName(hwpStyleName));
            XIndexReplace xReplace = (XIndexReplace) UnoRuntime.queryInterface(XIndexReplace.class, xStyleProps.getPropertyValue("NumberingRules"));
            for (int i=0; i < xReplace.getCount(); i++) {
                short numberingType = -1;
                short adjust = -1;
                short parentNumbering = -1;
                String prefix = "";
                String suffix = "";
                String charStyleName = "";
                short startsWith = -1;
                short positionAndSpaceMode = PositionAndSpaceMode.LABEL_ALIGNMENT;
                short labelFollowedBy = LabelFollow.SPACE;          // SPACE로 지정해야.
                long listtabStopPosition = -1;
                long firstLineIndent = -1;
                long indentAt = -1;

                // Hwp에서 Bullet 수준(level)은 1개,  LibreOffice에서 level은  10개이다.
                // Level1 만 설정하고, 나머지는 NONE으로 설정한다.
                if (i==0) {
                    if (bullet.bulletImage==0) {    // 글머리표
                        numberingType = NumberingType.CHAR_SPECIAL;
                    } else {	// 이미지 글머리
                        numberingType = NumberingType.BITMAP;
                    }
                    if (bullet.headerInfo!=null) {
                        if (bullet.headerInfo.textOffsetType==0x1) { // 절대값 거리
                            indentAt = 0; // Transform.translateHwp2Office(bullet.headerInfo.textOffset);
                            listtabStopPosition = indentAt/2;
                            firstLineIndent = -listtabStopPosition;
                        } else {                                     // 상대값 거리 (글자의 몇%)
                            indentAt = 0;
                            listtabStopPosition = indentAt/2;
                            firstLineIndent = -listtabStopPosition;
                        }
                        startsWith = (short)bullet.headerInfo.startNumber;
                        parentNumbering = getParentNumbering(bullet.headerInfo.numFormat, 0);
                        prefix = getPrefix(bullet.headerInfo.numFormat);
                        suffix = getSuffix(bullet.headerInfo.numFormat);
                    }
                    adjust = HoriOrientation.RIGHT;
                } else {
                    indentAt = 0;
                    listtabStopPosition = indentAt/2;
                    firstLineIndent = -listtabStopPosition;
                    numberingType = NumberingType.NUMBER_NONE;
                    adjust = HoriOrientation.RIGHT;
                }

                PropertyValue[] aProps = (PropertyValue []) xReplace.getByIndex(i);
                int extendSize = (i>0?0:bullet.bulletImage==0?3:3);
                PropertyValue[] newProps = new PropertyValue[aProps.length+extendSize];
                System.arraycopy(aProps, 0, newProps, 0, aProps.length);

                for (int j=0; j<aProps.length; j++) {
                    switch(newProps[j].Name) {
                    case "NumberingType":
                        if (numberingType!=-1) {
                            newProps[j].Value = numberingType;
                        }
                        break;
                    case "Adjust":
                        if (adjust!=-1) {
                            newProps[j].Value = adjust;
                        }
                        break;
                    case "ParentNumbering":
                        if (parentNumbering!=-1) {
                            newProps[j].Value = parentNumbering;
                        }
                        break;
                    case "Prefix":
                        newProps[j].Value = prefix;
                        break;
                    case "Suffix":
                        newProps[j].Value = suffix;
                        break;
                    case "CharStyleName":
                        if (charStyleName.equals("")==false) {
                            newProps[j].Value = charStyleName;
                        }
                        break;
                    case "StartWith":
                        if (startsWith!=-1) {
                            newProps[j].Value = startsWith;
                        }
                        break;
                    case "PositionAndSpaceMode":
                        newProps[j].Value = positionAndSpaceMode;
                        break;
                    case "LabelFollowedBy":
                        newProps[j].Value = labelFollowedBy;
                        break;
                    case "ListtabStopPosition":
                        if (listtabStopPosition!=-1) {
                            newProps[j].Value = listtabStopPosition;
                        }
                        break;
                    case "FirstLineIndent":
                        if (firstLineIndent!=-1) {
                            newProps[j].Value = firstLineIndent;
                        }
                        break;
                    case "IndentAt":
                        if (indentAt!=-1) {
                            newProps[j].Value = indentAt;
                        }
                        break;
                    }
                }
                Path path = null;
                
                if (i==0) {
                    if (bullet.bulletImage==0) {	// 글머리표
                        newProps[newProps.length-3] = new PropertyValue();
                        newProps[newProps.length-3].Name = "BulletFontName";
                        newProps[newProps.length-3].Value = "regular";
                        newProps[newProps.length-2] = new PropertyValue();
                        newProps[newProps.length-2].Name = "BulletChar";
                        newProps[newProps.length-2].Value = Character.toString(bullet.bulletChar);
                        newProps[newProps.length-1] = new PropertyValue();
                        newProps[newProps.length-1].Name = "BulletFont";
                        newProps[newProps.length-1].Value = new FontDescriptor("OpenSymbol", (short)0, (short)0,"Regular",	// Name,Height,Width,StyleName,
                                                                               (short)0, (short)10, (short)0, 0.0f, 0.0f,	// Family,CharSet,Pitch,CharacterWidth,Weight,
                                                                               FontSlant.NONE, (short)0, (short)0, 		// Slant,Underline,Strikeout,Orientation,
                                                                               0.0f, false, false, (short)0);				// Kerning,WordLineMode,Type
                    } else {
                        // GraphicBitmap 전달하는 것이 동작하지 않는다. 해결될때까지 GraphicURL 전달하는 방식으로 유지한다.
                        byte[] imageAsByteArray = wContext.getBinBytes(bullet.binItemRefID);
                        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageAsByteArray)) {
                            BufferedImage originalImage = ImageIO.read(bis);
                            int imgWidth = originalImage.getWidth();
                            int imgHeight = originalImage.getHeight();
                            
                            String imageExtractPath = wContext.getBinFilename(bullet.binItemRefID);
                            Path homeDir = wContext.userHomeDir;
                            path = Files.createTempFile(homeDir, "H2O_IMG_", "_" + imageExtractPath);
                            URL url = path.toFile().toURI().toURL();
                            String urlString = url.toExternalForm();
                            ImageIO.write(originalImage, "png", path.toFile());

                            newProps[newProps.length-3] = new PropertyValue();
                            newProps[newProps.length-3].Name = "GraphicSize";
                            newProps[newProps.length-3].Value = new Size(imgWidth*10, imgHeight*10);
                            newProps[newProps.length-2] = new PropertyValue();
                            newProps[newProps.length-2].Name = "GraphicURL";	// "GraphicBitmap"
                            newProps[newProps.length-2].Value = urlString;		// myBitmap;
                            newProps[newProps.length-1] = new PropertyValue();
                            newProps[newProps.length-1].Name = "VertOrient";
                            newProps[newProps.length-1].Value = VertOrientation.LINE_CENTER;
                        } catch (IOException e) {
                        }
                    }
                }
                xReplace.replaceByIndex(i, newProps);
                if (path!=null) {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                    }
                }
            }
            // NumberingRules 속성을 설정해야  Style이 변경된다. 
            XPropertySet xCursorProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);
            xCursorProps.setPropertyValue("NumberingRules", xReplace);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getOutlineStyleName() {
        Ctrl_SectionDef secd = ConvPage.getCurrentPage();
        return numberingStyleNameMap.get(secd.outlineNumberingID);
    }

    public static void setNumberingStyle(WriterContext wContext, int id) {
        try {
            String hwpStyleName = numberingStyleNameMap.get(id);

            // Numbering level 조정
            XParagraphCursor xParaCursor = (XParagraphCursor) UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
            XPropertySet xParaProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xParaCursor);
            xParaProps.setPropertyValue("NumberingStyleName", hwpStyleName);
            xParaProps.setPropertyValue("NumberingLevel", (short) 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setBulletStyle(WriterContext wContext, int id) {
        try {
            String hwpStyleName = bulletStyleNameMap.get(id);
            // Numbering level 조정
            XParagraphCursor xParaCursor = (XParagraphCursor) UnoRuntime.queryInterface(XParagraphCursor.class, wContext.mTextCursor);
            XPropertySet xParaProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xParaCursor);
            xParaProps.setPropertyValue("NumberingStyleName", hwpStyleName);
            xParaProps.setPropertyValue("NumberingLevel", (short) 0);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    
    public static short getParentNumbering(String format, int i) {
        if (format==null || format.equals("")) return 0;

        Pattern pattern = Pattern.compile("\\^\\d");
        Matcher m = pattern.matcher(format);

        short cnt = 0;
        while (m.find()) {
            log.finest("Parent Numbering. START="+m.start()+",END="+m.end());
            cnt++;
        }
        return cnt;
    }

    public static String getPrefix(String format) {
        String prefix = "";

        if (format==null||format.equals("")) return prefix;

        int lastIndex = format.lastIndexOf("^");
        if (lastIndex > 0) {
            int nextIndex = format.lastIndexOf("^", lastIndex-1);
            if (nextIndex == -1) {
                prefix = format.substring(lastIndex-1, lastIndex);
            }
        }
        return prefix;
    }

    public static String getSuffix(String format) {
        String suffix = "";

        if (format==null||format.equals("")) return suffix;

        int lastIndex = format.lastIndexOf("^");
        if (lastIndex > -1) {
            if (lastIndex+2 < format.length()) {
                suffix = format.substring(lastIndex+2, lastIndex+3);
            }
        } else if (format.length()==1) {
            suffix = format;
        }
        return suffix;
    }
    
    public static String getNumberFormat(String format) {
        // ""
        // "^2."
        // "^2.^3."
        // "^2.^3.^4"

        String formatStr = "";
        if (format==null||format.equals("")) return formatStr;

        Pattern pattern = Pattern.compile("\\^\\d+");
        Matcher m = pattern.matcher(format);

        StringBuffer sb = new StringBuffer();
        int prevEnd = 0;

        while(m.find()) {
            int s = m.start();
            int e = m.end();

            sb.append(format.subSequence(prevEnd, s));
            int num = Integer.parseInt(format.substring(s+1, e));
            sb.append("%" + num + "%");
            prevEnd = e;
        }
        sb.append(format.substring(prevEnd));

        return sb.toString();
    }

    public static short getNumberingType(String format, int i) {
        // 1, 2, 3, 4   ARABIC
        // a, b, c, e,  CHARS_LOWER_LETTER
        // A, B, C, D,  CHARS_UPPER_LETTER
        //              CIRCLE_NUMBER
        // ㉠,㉡,㉢       HANGUL_CIRCLED_JAMO_KO
        // ㉮,㉯,㉰       HANGUL_CIRCLED_SYLLABLE_KO
        // ㄱ,ㄴ,ㄷ       HANGUL_JAMO_KO
        // 가,나,다       HANGUL_SYLLABLE_KO
        // 일,이,삼       NUMBER_HANGUL_KO
        // 一,二,三       NUMBER_LOWER_ZH
        // i, ii, iii,  ROMAN_LOWER
        // I, II, III,  ROMAN_UPPER

        if (format==null||format.equals("")) {
            return NumberingType.NUMBER_NONE;
        }

        int lastIndex = format.lastIndexOf("^");
        if (lastIndex > -1) {
            if (lastIndex+1 < format.length()) {
                char num = format.charAt(lastIndex+1);
                if (num >= 0x30 && num <= 0x39) {
                    return NumberingType.ARABIC;
                } else if (num >= 0x41 && num <= 0x5A) {
                    return NumberingType.CHARS_UPPER_LETTER;
                } else if (num >= 0x61 && num <= 0x7A) {
                    return NumberingType.CHARS_LOWER_LETTER;
                } else if (num >= 0x2160 && num <= 0x2169) {
                    return NumberingType.ROMAN_UPPER;
                } else if (num >= 0x2170 && num <= 0x2179) {
                    return NumberingType.ROMAN_LOWER;
                } else if (num >= 0x2460 && num <= 0x2473) {
                    return NumberingType.CIRCLE_NUMBER;
                } else if (num >= 0x3131 && num <= 0x314E) {
                    return NumberingType.HANGUL_JAMO_KO;
                } else if (num >= 0x3260 && num <= 0x326D) {
                    return NumberingType.HANGUL_CIRCLED_JAMO_KO;
                } else if (num >= 0x326E && num <= 0x327B) {
                    return NumberingType.HANGUL_CIRCLED_SYLLABLE_KO;
                } else if (num >= 0xAC00 && num <= 0xC773) {
                    return NumberingType.HANGUL_SYLLABLE_KO;
                } else if (num >= 0xC774 && num <= 0xC0BC) {
                    return NumberingType.NUMBER_HANGUL_KO;
                } else if (num >= 0x4E00 && num <= 0x9FCB) {
                    return NumberingType.NUMBER_LOWER_ZH;
                }
            }
        }
        return NumberingType.NUMBER_NONE;
    }


}
