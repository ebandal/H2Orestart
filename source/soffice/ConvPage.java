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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.sun.star.awt.FontDescriptor;
import com.sun.star.awt.SimpleFontMetric;
import com.sun.star.awt.XDevice;
import com.sun.star.awt.XFont;
import com.sun.star.awt.XToolkit;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XNameAccess;
import com.sun.star.container.XNameContainer;
import com.sun.star.graphic.XGraphic;
import com.sun.star.graphic.XGraphicProvider;
import com.sun.star.io.IOException;
import com.sun.star.lang.DisposedException;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lib.uno.adapter.ByteArrayToXInputStreamAdapter;
import com.sun.star.style.BreakType;
import com.sun.star.style.GraphicLocation;
import com.sun.star.style.NumberingType;
import com.sun.star.style.PageStyleLayout;
import com.sun.star.style.ParagraphAdjust;
import com.sun.star.style.VerticalAlignment;
import com.sun.star.style.XStyle;
import com.sun.star.style.XStyleFamiliesSupplier;
import com.sun.star.text.ControlCharacter;
import com.sun.star.text.PageNumberType;
import com.sun.star.text.TextColumn;
import com.sun.star.text.XText;
import com.sun.star.text.XTextColumns;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextField;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.HwpElement.HwpRecord_CharShape;
import HwpDoc.HwpElement.HwpRecord_FaceName;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.paragraph.Ctrl_AutoNumber;
import HwpDoc.paragraph.Ctrl_ColumnDef;
import HwpDoc.paragraph.Ctrl_HeadFoot;
import HwpDoc.paragraph.Ctrl_PageNumPos;
import HwpDoc.paragraph.Ctrl_SectionDef;
import HwpDoc.paragraph.Ctrl_ShapePic;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.section.Page;

public class ConvPage {
    private static final Logger log = Logger.getLogger(ConvPage.class.getName());

    private static Map<Integer, String> pageStyleNameMap = new HashMap<Integer, String>();
    private static Map<Integer, Ctrl_SectionDef> pageMap = new HashMap<Integer, Ctrl_SectionDef>();
    private static List<Integer> headerDone = new ArrayList<>();    // Header 중복을 
    private static List<Integer> footerDone = new ArrayList<>();
    private static int customIndex = 0;
    private static int secdIndex = 0;
    private static final String PAGE_STYLE_PREFIX = "HWP ";

    public static int getSectionIndex() {
        return secdIndex;
    }

    public static void setSectionIndex(int index) {
        secdIndex = index;
    }

    public static Ctrl_SectionDef getCurrentPage() {
        return pageMap.get(secdIndex);
    }

    public static void reset(WriterContext wContext) {
        deleteAllCustomPageStyle(wContext);
        headerDone.clear();
        footerDone.clear();
        secdIndex = 0;
        customIndex = 0;
    }

    private static void deleteAllCustomPageStyle(WriterContext wContext) {
        if (wContext.mMyDocument != null) {
            try {
                XStyleFamiliesSupplier xSupplier = (XStyleFamiliesSupplier) UnoRuntime
                        .queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
                XNameAccess xFamilies = (XNameAccess) UnoRuntime.queryInterface(XNameAccess.class,
                        xSupplier.getStyleFamilies());
                XNameContainer xFamily = (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class,
                        xFamilies.getByName("PageStyles"));

                for (Integer custIndex : pageStyleNameMap.keySet()) {
                    try {
                        if (xFamily.hasByName(pageStyleNameMap.get(custIndex))) {
                            xFamily.removeByName(pageStyleNameMap.get(custIndex));
                        }
                    } catch (DisposedException e) {
                        log.severe(e.getMessage());
                    }
                }
            } catch (NoSuchElementException | WrappedTargetException e) {
                log.severe(e.getMessage());
            }
        }
        pageStyleNameMap.clear();
        pageMap.clear();
    }

    public static void adjustFontIfNotExists(WriterContext wContext) {
    	
        try {
            Object o = wContext.mMCF.createInstanceWithContext("com.sun.star.awt.Toolkit", wContext.mContext);
            XToolkit xToolkit = UnoRuntime.queryInterface(XToolkit.class, o);
            XDevice device = xToolkit.createScreenCompatibleDevice(0, 0);
            FontDescriptor[] fds = device.getFontDescriptors();
            
            // 장평 조정 대상 폰트가 시스템에서 지원하는 폰트인지 알기 위해 저장
            for (int i = 0; i < fds.length; i++) {
                WriterContext.fontNameSet.add(fds[i].Name);
            }

            // LibreOffice는 폰트마다 줄간격이 달라서 폰트별 줄간격 가중치를 미리 포함해 놓는다.
            // 폰트(크기100)의 Ascent와 Descent 합이 117 이하이면 가중치 0.87.  예) 굴림,돋움,궁서,바탕,새굴림,HY*,휴먼*
            // 폰트(크기100)의 Ascent와 Descent 합이 130 이하이면 가중치 0.857. 예) 휴먼편지체
            // 폰트(크기100)의 Ascent와 Descent 합이 133 이하이면 가중치 0.764. 예) 함초롬*,한컴*
            // 폰트(크기100)의 Ascent와 Descent 합이 133 이상이면 가중치 0.752. 예) 맑은 고딕
        	List<HwpRecord_FaceName> fontnameList = WriterContext.getFontNames();
        	for (HwpRecord_FaceName fontname: fontnameList) {
        		FontDescriptor fd = new FontDescriptor();
        		fd.Name = fontname.faceName;
        		fd.Height = 100;
                XFont xFont = device.getFont(fd);
                SimpleFontMetric fontMetric = xFont.getFontMetric();
                log.fine("Font[" + fd.Name + "] ascent="+fontMetric.Ascent+", descent="+fontMetric.Descent+", leading="+fontMetric.Leading);
                
                double fontAscDes = fontMetric.Ascent+fontMetric.Descent;
                double fontLineSpaceAlpha = 0.870; // 기본 0.870
                if (fontAscDes >= 117 && fontAscDes < 130) {
                	fontLineSpaceAlpha = 0.857;
                } else if (fontAscDes >= 130 && fontAscDes < 133) {
                	fontLineSpaceAlpha = 0.764;
                } else if (fontAscDes >= 133) {
                	fontLineSpaceAlpha = 0.752;
                }
                WriterContext.setFontNameLineSpaceAlaph(fontname.faceName, fontLineSpaceAlpha);
        	}
        	
            for (int i = 0; i < wContext.getDocInfo().charShapeList.size(); i++) {
                HwpRecord_CharShape font = (HwpRecord_CharShape) wContext.getDocInfo().charShapeList.get(i);

                if (font.fontName[0]!=null && WriterContext.fontNameSet.contains(font.fontName[0])==false) {
                    String replaceFontName = null;

                    // 한컴 전용폰트이므로, 대체폰트로 교체.
                    switch (font.fontName[0]) {
                    case "한컴 윤고딕 720":
                        replaceFontName = "나눔스퀘어 Light";
                        break;
                    case "한컴 윤고딕 740":
                        replaceFontName = "나눔스퀘어";
                        break;
                    case "한컴 윤체 M":
                    case "한컴 윤고딕 230":
                    case "한컴 윤고딕 240":
                    case "한컴 윤고딕 760":
                        replaceFontName = "나눔스퀘어 Bold";
                        break;
                    case "한컴 윤체 B":
                    case "한컴 윤고딕 250":
                    case "HY견고딕":
                        replaceFontName = "나눔스퀘어 ExtraBold";
                        break;
                    case "가는안상수체":
                    case "중간안상수체":
                    case "굵은안상수체":
                    case "안상수2006가는":
                    case "안상수2006굵은":
                    case "안상수2006중간":
                        replaceFontName = "나눔바른펜";
                        break;
                    case "HY궁서":
                        replaceFontName = "궁서";
                        break;
                    case "HY그래픽":
                    case "휴먼고딕":
                        replaceFontName = "나눔고딕코딩";
                        break;
                    case "휴먼옛체":
                        replaceFontName = "나눔명조 옛한글";
                        break;
                    case "HY견명조":
                        replaceFontName = "나눔명조 ExtraBold";
                        break;
                    default:
                        replaceFontName = "나눔고딕";
                    }
                    for (int j = 0; j < font.fontName.length; j++) {
                        if (WriterContext.fontNameSet.contains(replaceFontName)) {
                            log.fine("Font[" + font.fontName[j] + "] does not exist. so replacing with basic Font["
                                    + replaceFontName + "]");
                            font.fontName[j] = replaceFontName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // Hwp에 맞는 Page Style을 미리 등록해놓자.
    public static String makeCustomPageStyle(WriterContext wContext, Ctrl_SectionDef secd) {
        String styleName = null;
        try {
            XStyle xPageStyle = UnoRuntime.queryInterface(XStyle.class,
                    wContext.mMSF.createInstance("com.sun.star.style.PageStyle"));
            XStyleFamiliesSupplier xSupplier = (XStyleFamiliesSupplier) UnoRuntime
                    .queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
            XNameAccess xFamilies = (XNameAccess) UnoRuntime.queryInterface(XNameAccess.class,
                    xSupplier.getStyleFamilies());
            XNameContainer xFamily = (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class,
                    xFamilies.getByName("PageStyles"));
            styleName = PAGE_STYLE_PREFIX + customIndex;
            if (xFamily.hasByName(styleName) == false) {
                xFamily.insertByName(styleName, xPageStyle);
            }
            
            XPropertySet xStyleProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xPageStyle);
           
            // DEBUG
            XPropertySet propSet = UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);
            propSet.setPropertyValue("PageDescName", styleName);
            // DEBUG
            
            // Size,Width,Height,Hidden,TextVerticalAdjust,
            // LeftMargin,RightMargin,TopMargin,BottomMargin,
            
            if (secd.page.landscape == false) {
                xStyleProps.setPropertyValue("Width", Integer.valueOf(Transform.translateHwp2Office(secd.page.width)));
                xStyleProps.setPropertyValue("Height",
                        Integer.valueOf(Transform.translateHwp2Office(secd.page.height)));
            } else {
                xStyleProps.setPropertyValue("Width", Integer.valueOf(Transform.translateHwp2Office(secd.page.height)));
                xStyleProps.setPropertyValue("Height", Integer.valueOf(Transform.translateHwp2Office(secd.page.width)));
            }
            xStyleProps.setPropertyValue("LeftMargin",
                    Integer.valueOf(Transform.translateHwp2Office(secd.page.marginLeft)));
            xStyleProps.setPropertyValue("RightMargin",
                    Integer.valueOf(Transform.translateHwp2Office(secd.page.marginRight)));

            // LeftBorder,RightBorder,TopBorder,BottomBorder,BorderDistance,LeftBorderDistance,RightBorderDistance,TopBorderDistance,BottomBorderDistance,
            // TextColumns,
            // UserDefinedAttributes,
            // StandardPageMode,
            // FirstIsShared,
            // IsLandscape,NumberingType,PageStyleLayout,PrinterPaperTray,
            // RegisterParagraphStyle,
            // FollowStyle,IsPhysical,DisplayName,
            // WritingMode,
            // ShadowFormat,ShadowTransparence,
            // RubyBelow,GridRubyHeight,
            // GridMode,GridLines,GridPrint,GridDisplay,GridBaseWidth,GridSnapToChars,GridBaseHeight,GridColor,
            // FillColor,FillStyle,FillColor2,FillBackground,
            // FillBitmapMode,FillBitmapName,FillBitmap,FillBitmapURL,FillBitmapSizeX,FillBitmapLogicalSize,FillBitmapOffsetX,FillBitmapOffsetY,FillBitmapPositionOffsetX,
            // FillBitmapPositionOffsetY,FillBitmapRectanglePoint,FillBitmapSizeY,FillBitmapTile,FillBitmapStretch,
            // FillGradientStepCount,FillGradient,FillGradientName,FillHatch,FillHatchName,
            // FillTransparence,FillTransparenceGradient,FillTransparenceGradientName,

            // FootnoteHeight,FootnoteLineAdjust,FootnoteLineColor,FootnoteLineTextDistance,FootnoteLineWeight,FootnoteLineStyle,FootnoteLineRelativeWidth,FootnoteLineDistance,

            // HeaderIsOn,HeaderText,HeaderTextFirst,HeaderTextLeft,HeaderTextRight,HeaderIsShared,HeaderIsDynamicHeight,HeaderHeight,
            // HeaderDynamicSpacing,HeaderBodyDistance,
            // HeaderLeftMargin,HeaderRightMargin,
            // HeaderLeftBorder,HeaderRightBorder,HeaderTopBorder,HeaderBottomBorder,HeaderBorderDistance,
            // HeaderLeftBorderDistance,HeaderRightBorderDistance,HeaderTopBorderDistance,HeaderBottomBorderDistance,
            // HeaderFillBackground,HeaderFillColor,HeaderFillColor2,HeaderFillHatchName,HeaderFillStyle,
            // HeaderFillBitmap,HeaderFillBitmapName,HeaderFillBitmapSizeY,HeaderFillBitmapPositionOffsetY,HeaderFillBitmapLogicalSize,HeaderFillBitmapStretch,HeaderFillBitmapPositionOffsetX,
            // HeaderFillTransparence,HeaderFillTransparenceGradient,HeaderFillTransparenceGradientName,
            // HeaderFillGradientStepCount,HeaderFillGradient,HeaderFillGradientName,
            // HeaderBackColor,HeaderBackGraphicURL,HeaderBackGraphic,HeaderBackGraphicFilter,HeaderBackTransparent,HeaderBackGraphicLocation,
            // HeaderShadowFormat,
            // HeaderFillBitmapTile,HeaderFillBitmapMode,HeaderFillBitmapOffsetX,HeaderFillBitmapOffsetY,HeaderFillBitmapRectanglePoint,HeaderFillBitmapSizeX,
            // HeaderFillHatch,

            if (secd.hideHeader == false) {
                if (secd.page.marginHeader >= 0) {
                    xStyleProps.setPropertyValue("TopMargin",
                            Integer.valueOf(Transform.translateHwp2Office(secd.page.marginTop)));
                    xStyleProps.setPropertyValue("HeaderIsOn", true); // 한컴(marginHeader) =
                                                                      // LO(HeaderHeight+HeaderBodyDistance)
                    xStyleProps.setPropertyValue("HeaderHeight",
                            Integer.valueOf(Transform.translateHwp2Office(secd.page.marginHeader)));
                    xStyleProps.setPropertyValue("HeaderBodyDistance", 0);
                } else {
                    xStyleProps.setPropertyValue("TopMargin", Integer
                            .valueOf(Transform.translateHwp2Office(secd.page.marginTop + secd.page.marginHeader)));
                }
            } else {
                xStyleProps.setPropertyValue("TopMargin",
                        Integer.valueOf(Transform.translateHwp2Office(secd.page.marginTop)));
                xStyleProps.setPropertyValue("HeaderIsOn", false);
            }

            // FooterIsOn,FooterText,FooterTextFirst,FooterTextLeft,FooterTextRight,FooterIsShared,FooterIsDynamicHeight,FooterHeight,
            // FooterDynamicSpacing,FooterBodyDistance,
            // FooterLeftMargin,FooterRightMargin,
            // FooterLeftBorder,FooterRightBorder,FooterTopBorder,FooterBottomBorder,FooterBorderDistance,
            // FooterLeftBorderDistance,FooterRightBorderDistance,FooterTopBorderDistance,FooterBottomBorderDistance,

            // FooterFillBackground,FooterBackGraphic,FooterBackGraphicLocation,FooterBackColor,FooterBackGraphicURL,FooterBackTransparent,FooterBackGraphicFilter,
            // FooterFillHatch,FooterFillHatchName,FooterFillStyle,
            // FooterFillColor,FooterFillColor2,
            // FooterFillBitmap,FooterFillBitmapName,FooterFillBitmapSizeY,FooterFillBitmapOffsetY,FooterFillBitmapPositionOffsetY,FooterFillBitmapSizeX,FooterFillBitmapStretch,
            // FooterFillBitmapTile,FooterFillBitmapPositionOffsetX,FooterFillBitmapMode,FooterShadowFormat,FooterFillBitmapOffsetX,FooterFillBitmapLogicalSize,FooterFillBitmapRectanglePoint,
            // FooterFillTransparence,FooterFillTransparenceGradient,FooterFillTransparenceGradientName,
            // FooterFillGradient,FooterFillGradientName,FooterFillGradientStepCount,
            if (secd.hideFooter == false) {
                if (secd.page.marginFooter >= 0) {
                    xStyleProps.setPropertyValue("BottomMargin",
                            Integer.valueOf(Transform.translateHwp2Office(secd.page.marginBottom)));
                    xStyleProps.setPropertyValue("FooterIsOn", true); // 한컴(marginHeader) =
                                                                      // LO(HeaderHeight+HeaderBodyDistance)
                    xStyleProps.setPropertyValue("FooterHeight",
                            Integer.valueOf(Transform.translateHwp2Office(secd.page.marginFooter)));
                    xStyleProps.setPropertyValue("FooterBodyDistance", 0);
                } else {
                    xStyleProps.setPropertyValue("BottomMargin", Integer
                            .valueOf(Transform.translateHwp2Office(secd.page.marginBottom + secd.page.marginFooter)));
                }
            } else {
                xStyleProps.setPropertyValue("BottomMargin",
                        Integer.valueOf(Transform.translateHwp2Office(secd.page.marginBottom)));
                xStyleProps.setPropertyValue("FooterIsOn", false);
            }

            // BackColor,BackTransparent,
            if (secd.paras!=null) {
            	Optional<Ctrl_ShapePic> picOp = secd.paras.stream()
			            							.filter(para -> para.p!=null)
				            						.flatMap(para -> para.p.stream())
				            						.filter(ctrl -> (ctrl!=null) && (ctrl instanceof Ctrl_ShapePic))
				            						.map(ctrl -> (Ctrl_ShapePic)ctrl)
				            						.findFirst();
            	if (picOp.isPresent()) {
                	// DEBUG
                    // DEBUG

	            	setBackGraphic(wContext, xStyleProps, picOp.get());
                    xStyleProps.setPropertyValue("BackTransparent", false);
                    xStyleProps.setPropertyValue("BackGraphicLocation", GraphicLocation.AREA);
                    xStyleProps.setPropertyValue("BackgroundFullSize", false);
                    
                    xStyleProps.setPropertyValue("HeaderBackTransparent", false);
                    xStyleProps.setPropertyValue("HeaderBackGraphicLocation", GraphicLocation.TILED);
                    
                    xStyleProps.setPropertyValue("FooterBackTransparent", true);
                    xStyleProps.setPropertyValue("FooterBackGraphicLocation", GraphicLocation.RIGHT_BOTTOM);
	            }
            }
            // BackGraphicURL,BackGraphicFilter,BackGraphicLocation,

        } catch (Exception e) {
            e.printStackTrace();
        }

        pageStyleNameMap.put(customIndex, styleName);
        pageMap.put(customIndex, secd);
        customIndex++;
        return styleName;
    }

    private static void setBackGraphic(WriterContext wContext, XPropertySet xStyleProps, Ctrl_ShapePic pic) throws Exception {
        // image ByteArray로 그림 그리기
        Object graphicProviderObject = wContext.mMCF.createInstanceWithContext("com.sun.star.graphic.GraphicProvider", wContext.mContext);
        XGraphicProvider xGraphicProvider = UnoRuntime.queryInterface(XGraphicProvider.class, graphicProviderObject);

        byte[] imageAsByteArray = null;
        String imageType = "";

        imageAsByteArray = wContext.getBinBytes(pic.binDataID);
        imageType = wContext.getBinFormat(pic.binDataID);

        if (imageAsByteArray == null || imageAsByteArray.length == 0) {
            log.severe("Something Wrong!!!. skip drawing");
            return;
        }

        PropertyValue[] v = new PropertyValue[2];
        v[0] = new PropertyValue();
        v[0].Name = "InputStream";
        v[0].Value = new ByteArrayToXInputStreamAdapter(imageAsByteArray);
        v[1] = new PropertyValue();
        v[1].Name = "MimeType";
        switch (imageType.toLowerCase()) {
        case "png":
            v[1].Value = "image/png";
            break;
        case "bmp":
            v[1].Value = "image/bmp";
            break;
        case "wmf":
            v[1].Value = "image/x-wmf";
            break;
        case "jpg":
            v[1].Value = "image/jpeg";
            break;
        case "gif":
            v[1].Value = "image/gif";
            break;
        case "tif":
            v[1].Value = "image/tiff";
            break;
        case "svg":
            v[1].Value = "image/svg+xml";
            break;
        }

        XGraphic graphic = xGraphicProvider.queryGraphic(v);

        if (graphic == null) {
            log.severe("Error loading the image");
        } else {
        	/*
            if (pic.cropLeft>0 || pic.cropRight>0 || pic.cropTop>0 || pic.cropBottom>0) {
                try {
                    PropertyValue[] pv = new PropertyValue[2];
                    Path homeDir = wContext.userHomeDir;
                    Path path = Files.createTempFile(homeDir, "H2O_IMG_", "_" + pic.binDataID + ".png");
                    URL url = path.toFile().toURI().toURL();
                    String urlString = url.toExternalForm();
                    pv[0] = new PropertyValue();
                    pv[0].Name = "URL";
                    pv[0].Value = urlString;
                    pv[1] = new PropertyValue();
                    pv[1].Name = "MimeType";
                    pv[1].Value = "image/png";
                    xGraphicProvider.storeGraphic(graphic, pv);
                    xStyleProps.setPropertyValue("BackGraphic", graphic);
                    
                    BufferedImage originalImage = ImageIO.read(path.toFile());
                    Files.delete(path);
                    
                    int orgWidth = pic.iniPicWidth==0 ? pic.iniWidth : pic.iniPicWidth;
                    int imgWidth = originalImage.getWidth();
                    int imgHeight = originalImage.getHeight();
                    float hwp2pixelRatio = (float)imgWidth / orgWidth;
                    int cropLeftPixel = (int)(pic.cropLeft*hwp2pixelRatio);
                    int cropTopPixel = (int)(pic.cropTop*hwp2pixelRatio);
                    int cropWidthPixel = (int)((pic.cropRight-pic.cropLeft)*hwp2pixelRatio);
                    int cropHeightPixel = (int)((pic.cropBottom-pic.cropTop)*hwp2pixelRatio);
                    int subLeft = cropLeftPixel>imgWidth ? 0 : cropLeftPixel;
                    int subTop = cropTopPixel>imgHeight ? 0 : cropTopPixel;
                    int subWidth = Math.min(cropWidthPixel, imgWidth-subLeft);
                    int subHeight = Math.min(cropHeightPixel, imgHeight-subTop);
                    BufferedImage subImgage = originalImage.getSubimage(subLeft,
                                                                        subTop,
                                                                        subWidth,
                                                                        subHeight);
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(subImgage, "png", baos);
                        imageAsByteArray = baos.toByteArray();
                        pv[0] = new PropertyValue();
                        pv[0].Name = "InputStream";
                        pv[0].Value = new ByteArrayToXInputStreamAdapter(imageAsByteArray);
                        pv[1] = new PropertyValue();
                        pv[1].Name = "MimeType";
                        pv[1].Value = "image/png";
                        XGraphic graphic2 = xGraphicProvider.queryGraphic(pv);
                        xStyleProps.setPropertyValue("BackGraphic", graphic2);

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            */
            xStyleProps.setPropertyValue("BackGraphic", graphic);
            Object obj = xStyleProps.getPropertyValue("BackGraphic");
            XGraphic xgraphicOut = (XGraphic) UnoRuntime.queryInterface(XGraphic.class, obj);
            
            xStyleProps.setPropertyValue("HeaderBackGraphic", xgraphicOut);
            xStyleProps.setPropertyValue("FooterBackGraphic", graphic);
        }
	}

	public static String makeCustomPageStyle(Ctrl_SectionDef secd) {
        String styleName = PAGE_STYLE_PREFIX + customIndex;
        pageStyleNameMap.put(customIndex, styleName);
        pageMap.put(customIndex, secd);
        customIndex++;
        return styleName;
    }

    public static void setColumn(WriterContext wContext, Ctrl_ColumnDef cold) {
        try {
            XPropertySet xCursorProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class,
                    wContext.mTextCursor);
            String currentPageStyleName = xCursorProps.getPropertyValue("PageStyleName").toString();
            if (currentPageStyleName.equals("")) {
                currentPageStyleName = "HWP " + ConvPage.getSectionIndex();
            }
            XStyleFamiliesSupplier xSupplier = (XStyleFamiliesSupplier) UnoRuntime
                    .queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
            XNameAccess xFamilies = (XNameAccess) UnoRuntime.queryInterface(XNameAccess.class,
                    xSupplier.getStyleFamilies());
            XNameContainer xFamily = (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class,
                    xFamilies.getByName("PageStyles"));
            String[] elementNames = xFamily.getElementNames();
            XStyle xCurrentPageStyle = UnoRuntime.queryInterface(XStyle.class, xFamily.getByName(currentPageStyleName));
            XPropertySet xCurrentPageStyleProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class,
                    xCurrentPageStyle);
            XTextColumns xColumns = UnoRuntime.queryInterface(XTextColumns.class,
                    wContext.mMSF.createInstance("com.sun.star.text.TextColumns"));

            xColumns.setColumnCount(cold.colCount);
            if (cold.colSzWidths != null && cold.colSzWidths.length > 0) {
                TextColumn[] aSequence = xColumns.getColumns();
                for (int i = 0; i < cold.colSzWidths.length; i++) {
                    if (i == 0) {
                        aSequence[i].LeftMargin = 0;
                        aSequence[i].Width = (cold.colSzWidths[i] - cold.colSzGaps[i]) / 2; // 대략계산
                        aSequence[i].RightMargin = cold.colSzGaps[i] / 4; // 대략계산
                    } else if (i == cold.colSzWidths.length - 1) {
                        aSequence[i].LeftMargin = cold.colSzGaps[i - 1] / 4; // 대략계산
                        aSequence[i].Width = (cold.colSzWidths[i] - cold.colSzGaps[i - 1]) / 2; // 대략계산
                        aSequence[i].RightMargin = 0;
                    } else {
                        aSequence[i].LeftMargin = cold.colSzGaps[i - 1] / 4; // 대략계산
                        aSequence[i].Width = (cold.colSzWidths[i] - cold.colSzGaps[i]) / 2; // 대략계산
                        aSequence[i].RightMargin = cold.colSzGaps[i] / 4; // 대략계산
                    }
                }
                xColumns.setColumns(aSequence);
            }
            XPropertySet xTextColumnProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xColumns);
            boolean isAutomatic = (boolean) xTextColumnProps.getPropertyValue("IsAutomatic");
            if (isAutomatic) {
                xTextColumnProps.setPropertyValue("AutomaticDistance", Transform.translateHwp2Office(cold.sameGap)); // IsAutomatic
                                                                                                                     // ==
                                                                                                                     // true
                                                                                                                     // 일때만
                                                                                                                     // valid
            }
            xTextColumnProps.setPropertyValue("SeparatorLineColor", cold.colLineColor); // aRGB
            xTextColumnProps.setPropertyValue("SeparatorLineIsOn", true);
            xTextColumnProps.setPropertyValue("SeparatorLineRelativeHeight", 100);

            // cold.separatorType
            // xTextColumnProps.setPropertyValue("SeparatorLineStyle", 0);
            xTextColumnProps.setPropertyValue("SeparatorLineVerticalAlignment", VerticalAlignment.TOP);
            xTextColumnProps.setPropertyValue("SeparatorLineWidth", Transform.toLineWidth(cold.colLineWidth) / 2);
            xCurrentPageStyleProps.setPropertyValue("TextColumns", xColumns);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setHeaderFooter(WriterContext wContext, Ctrl_HeadFoot hf) {
        String styleName = null;

        try {
            XStyleFamiliesSupplier xSupplier = (XStyleFamiliesSupplier) UnoRuntime
                    .queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
            XNameAccess xFamilies = (XNameAccess) UnoRuntime.queryInterface(XNameAccess.class,
                    xSupplier.getStyleFamilies());
            XNameContainer xFamily = (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class,
                    xFamilies.getByName("PageStyles"));

            styleName = PAGE_STYLE_PREFIX + ConvPage.getSectionIndex();
            XPropertySet xStyleProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class,
                    xFamily.getByName(styleName));

            HwpDoc.section.Page page = getCurrentPage().page;
            WriterContext context2 = new WriterContext();
            context2.mContext = wContext.mContext;
            context2.mDesktop = wContext.mDesktop;
            context2.mMCF = wContext.mMCF;
            context2.mMSF = wContext.mMSF;
            context2.mMyDocument = wContext.mMyDocument;
            context2.userHomeDir = wContext.userHomeDir;

            switch (hf.whichPage) {
            case ODD:
                xStyleProps.setPropertyValue("PageStyleLayout", PageStyleLayout.MIRRORED); // 짝수쪽, 홀수쪽 번갈아 header가 나오도록
                                                                                           // 하려면 MIRROED
                xStyleProps.setPropertyValue("FirstIsShared", true); // 첫번째 페이지에도 header가 나오도록 하려면 FirstIsShaed=true
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderIsOn" : "FooterIsOn"), true);
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderIsShared" : "FooterIsShared"), false);
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderHeight" : "FooterHeight"), Integer.valueOf(
                        Transform.translateHwp2Office(hf.isHeader == true ? page.marginHeader : page.marginFooter)));
                xStyleProps.setPropertyValue("HeaderIsDynamicHeight", false);
                // xStyleProps.setPropertyValue("FooterBodyDistance", 0);
                XText headerTextRight = UnoRuntime.queryInterface(XText.class,
                        xStyleProps.getPropertyValue((hf.isHeader == true ? "HeaderTextRight" : "FooterTextRight")));
                XTextCursor headerCursorRight = headerTextRight.createTextCursor();
                headerCursorRight.gotoEnd(false);
                headerCursorRight.gotoStart(true);
                headerCursorRight.setString("");
                headerCursorRight.gotoEnd(false);

                context2.mText = headerTextRight;
                context2.mTextCursor = headerCursorRight;

                HwpCallback callbackOdd = new HwpCallback() {
                    @Override
                    public void onAutoNumber(Ctrl_AutoNumber autoNumber, int paraStyleID, int paraShapeID) {
                        String paraStyleName = ConvPara.getStyleName(paraStyleID);
                        HwpRecord_ParaShape paraShape = wContext.getParaShape((short) paraShapeID);
                        try {
                            XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, headerCursorRight);
                            paraProps.setPropertyValue("ParaStyleName", paraStyleName);
                            ConvPara.setParagraphProperties(paraProps, paraShape, wContext.getDocInfo().compatibleDoc, ConvPara.PARA_SPACING);
                            // ConvPara.setCharacterProperties(paraProps, charShape);
                            XTextField numField;
                            XPropertySet numFieldProp;
                            switch (autoNumber.numType) {
                            case PAGE:
                            default:
                                numField = UnoRuntime.queryInterface(XTextField.class, wContext.mMSF.createInstance("com.sun.star.text.textfield.PageNumber"));
                                numFieldProp = UnoRuntime.queryInterface(XPropertySet.class, numField);
                                numFieldProp.setPropertyValue("NumberingType", NumberingType.ARABIC);
                                numFieldProp.setPropertyValue("SubType", PageNumberType.CURRENT);
                                break;
                            case TOTAL_PAGE:
                                numField = UnoRuntime.queryInterface(XTextField.class, wContext.mMSF.createInstance("com.sun.star.text.textfield.PageCount"));
                                numFieldProp = UnoRuntime.queryInterface(XPropertySet.class, numField);
                                numFieldProp.setPropertyValue("NumberingType", NumberingType.ARABIC);
                                break;
                            }
                            headerTextRight.insertTextContent(headerCursorRight, numField, false);
                            headerCursorRight.gotoEnd(false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    };

                    @Override
                    public boolean onTab(String info) {
                        return false;
                    };

                    @Override
                    public boolean onText(String content, int charShapeId, int charPos, boolean append) {
                        return false;
                    }

                    @Override
                    public boolean onParaBreak() {
                        return false;
                    }
                };
                for (HwpParagraph para : hf.paras) {
                    HwpRecurs.printParaRecurs(context2, wContext, para, callbackOdd, 2);
                }
                // REMOVE last PARA_BREAK
                HwpRecurs.removeLastParaBreak(context2.mTextCursor);
                break;
            case EVEN:
                xStyleProps.setPropertyValue("PageStyleLayout", PageStyleLayout.MIRRORED); // 짝수쪽, 홀수쪽 번갈아 header가 나오도록
                                                                                           // 하려면 MIRROED
                xStyleProps.setPropertyValue("FirstIsShared", true); // 첫번째 페이지에도 header가 나오도록 하려면 FirstIsShaed=true
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderIsOn" : "FooterIsOn"), true);
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderIsShared" : "FooterIsShared"), false);
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderHeight" : "FooterHeight"), Integer.valueOf(
                        Transform.translateHwp2Office(hf.isHeader == true ? page.marginHeader : page.marginFooter)));
                xStyleProps.setPropertyValue("HeaderIsDynamicHeight", false);
                XText headerTextLeft = UnoRuntime.queryInterface(XText.class,
                        xStyleProps.getPropertyValue((hf.isHeader == true ? "HeaderTextLeft" : "FooterTextLeft")));
                XTextCursor headerCursorLeft = headerTextLeft.createTextCursor();
                headerCursorLeft.gotoEnd(false);
                headerCursorLeft.gotoStart(true);
                headerCursorLeft.setString("");
                headerCursorLeft.gotoEnd(false);

                context2.mText = headerTextLeft;
                context2.mTextCursor = headerCursorLeft;

                HwpCallback callbackEven = new HwpCallback() {
                    @Override
                    public void onAutoNumber(Ctrl_AutoNumber autoNumber, int paraStyleID, int paraShapeID) {
                        String paraStyleName = ConvPara.getStyleName(paraStyleID);
                        HwpRecord_ParaShape paraShape = wContext.getParaShape((short) paraShapeID);
                        try {
                            XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, headerCursorLeft);
                            paraProps.setPropertyValue("ParaStyleName", paraStyleName);
                            ConvPara.setParagraphProperties(paraProps, paraShape, wContext.getDocInfo().compatibleDoc, ConvPara.PARA_SPACING);
                            // ConvPara.setCharacterProperties(paraProps, charShape);
                            XTextField numField;
                            XPropertySet numFieldProp;
                            switch (autoNumber.numType) {
                            case PAGE:
                            default:
                                numField = UnoRuntime.queryInterface(XTextField.class, wContext.mMSF.createInstance("com.sun.star.text.textfield.PageNumber"));
                                numFieldProp = UnoRuntime.queryInterface(XPropertySet.class, numField);
                                numFieldProp.setPropertyValue("NumberingType", NumberingType.ARABIC);
                                numFieldProp.setPropertyValue("SubType", PageNumberType.CURRENT);
                                break;
                            case TOTAL_PAGE:
                                numField = UnoRuntime.queryInterface(XTextField.class, wContext.mMSF.createInstance("com.sun.star.text.textfield.PageCount"));
                                numFieldProp = UnoRuntime.queryInterface(XPropertySet.class, numField);
                                numFieldProp.setPropertyValue("NumberingType", NumberingType.ARABIC);
                                break;
                            }
                            headerTextLeft.insertTextContent(headerCursorLeft, numField, false);
                            headerCursorLeft.gotoEnd(false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    };

                    @Override
                    public boolean onTab(String info) {
                        return false;
                    };

                    @Override
                    public boolean onText(String content, int charShapeId, int charPos, boolean append) {
                        return false;
                    }

                    @Override
                    public boolean onParaBreak() {
                        return false;
                    }
                };
                for (HwpParagraph para : hf.paras) {
                    HwpRecurs.printParaRecurs(context2, wContext, para, callbackEven, 2);
                }
                // REMOVE last PARA_BREAK
                HwpRecurs.removeLastParaBreak(context2.mTextCursor);
                break;
            case BOTH:
                PageStyleLayout layout = (PageStyleLayout) xStyleProps.getPropertyValue("PageStyleLayout");
                if (layout != PageStyleLayout.MIRRORED) {
                    xStyleProps.setPropertyValue("PageStyleLayout", PageStyleLayout.ALL); // 짝수쪽, 홀수쪽 번갈아 header가 나오도록
                                                                                          // 하려면 MIRRORED
                }
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderIsOn" : "FooterIsOn"), true);
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderIsShared" : "FooterIsShared"), true);
                xStyleProps.setPropertyValue((hf.isHeader == true ? "HeaderHeight" : "FooterHeight"), Integer.valueOf(
                        Transform.translateHwp2Office(hf.isHeader == true ? page.marginHeader : page.marginFooter)));
                xStyleProps.setPropertyValue("HeaderIsDynamicHeight", false);
                XText headerTextBoth = UnoRuntime.queryInterface(XText.class,
                        xStyleProps.getPropertyValue((hf.isHeader == true ? "HeaderText" : "FooterText")));
                XTextCursor headerCursorBoth = headerTextBoth.createTextCursor();
                if ((hf.isHeader==true && headerDone.contains(secdIndex)==false) || 
                    (hf.isHeader==false && footerDone.contains(secdIndex)==false)) {    // Header 중복 생성 방지
                    headerCursorBoth.gotoEnd(false);
                    headerCursorBoth.gotoStart(true);
                    headerCursorBoth.setString("");
                    headerCursorBoth.gotoEnd(false);
                    
                    context2.mText = headerTextBoth;
                    context2.mTextCursor = headerCursorBoth;
    
                    HwpCallback callbackBoth = new HwpCallback() {
                        @Override
                        public void onAutoNumber(Ctrl_AutoNumber autoNumber, int paraStyleID, int paraShapeID) {
                            String paraStyleName = ConvPara.getStyleName(paraStyleID);
                            HwpRecord_ParaShape paraShape = wContext.getParaShape((short) paraShapeID);
                            try {
                                XPropertySet paraProps = UnoRuntime.queryInterface(XPropertySet.class, headerCursorBoth);
                                paraProps.setPropertyValue("ParaStyleName", paraStyleName);
                                ConvPara.setParagraphProperties(paraProps, paraShape, wContext.getDocInfo().compatibleDoc, ConvPara.PARA_SPACING);
                                // ConvPara.setCharacterProperties(paraProps, charShape);
                                XTextField numField;
                                XPropertySet numFieldProp;
                                switch (autoNumber.numType) {
                                case PAGE:
                                default:
                                    numField = UnoRuntime.queryInterface(XTextField.class, wContext.mMSF.createInstance("com.sun.star.text.textfield.PageNumber"));
                                    numFieldProp = UnoRuntime.queryInterface(XPropertySet.class, numField);
                                    numFieldProp.setPropertyValue("NumberingType", NumberingType.ARABIC);
                                    numFieldProp.setPropertyValue("SubType", PageNumberType.CURRENT);
                                    break;
                                case TOTAL_PAGE:
                                    numField = UnoRuntime.queryInterface(XTextField.class, wContext.mMSF.createInstance("com.sun.star.text.textfield.PageCount"));
                                    numFieldProp = UnoRuntime.queryInterface(XPropertySet.class, numField);
                                    numFieldProp.setPropertyValue("NumberingType", NumberingType.ARABIC);
                                    break;
                                }
                                headerTextBoth.insertTextContent(headerCursorBoth, numField, false);
                                headerCursorBoth.gotoEnd(false);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        };
    
                        @Override
                        public boolean onTab(String info) {
                            return false;
                        };
    
                        @Override
                        public boolean onText(String content, int charShapeId, int charPos, boolean append) {
                            return false;
                        }
    
                        @Override
                        public boolean onParaBreak() {
                            return false;
                        }
                    };
                    if (hf.paras != null) {
                        for (HwpParagraph para : hf.paras) {
                            HwpRecurs.printParaRecurs(context2, wContext, para, callbackBoth, 2);
                        }
                        // REMOVE last PARA_BREAK
                        HwpRecurs.removeLastParaBreak(context2.mTextCursor);
                    }
                    if (hf.isHeader==true) {
                        headerDone.add(secdIndex);
                    }
                    if (hf.isHeader==false) {
                        footerDone.add(secdIndex);
                    }
                }
                break;
            }

            // PN = doc.createInstance("com.sun.star.text.textfield.PageNumber")
            // PC = doc.createInstance("com.sun.star.text.textfield.PageCount")
            // PN.NumberingType=4
            // PN.SubType="CURRENT"
            // PC.NumberingType=4

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void makeNextPage(WriterContext wContext) {
        if (secdIndex > 0) {
            wContext.mText.insertControlCharacter(wContext.mTextCursor, ControlCharacter.PARAGRAPH_BREAK, false);
        }

        XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);
        try {
            props.setPropertyValue("BreakType", BreakType.PAGE_BEFORE);
        } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                | WrappedTargetException e) {
            e.printStackTrace();
        }
    }

    public static void makeNextColumn(WriterContext wContext) {
        XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);
        try {
            props.setPropertyValue("BreakType", BreakType.COLUMN_BEFORE);
        } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                | WrappedTargetException e) {
            e.printStackTrace();
        }
    }

    public static void setupPage(WriterContext wContext, Page page) {
        String customPageStyleName = pageStyleNameMap.get(secdIndex);
        transSection(customPageStyleName, wContext.mText, wContext.mTextCursor, (secdIndex > 0));
    }

    public static void setupPageTemporary(WriterContext wContext, Page page) {
        XPropertySet propSet = UnoRuntime.queryInterface(XPropertySet.class, wContext.mTextCursor);

        try {
            if (page.landscape) {
                propSet.setPropertyValue("PageDescName", "Landscape");
            } else {
                propSet.setPropertyValue("PageDescName", "Standard");
            }
            propSet.setPropertyValue("BreakType", BreakType.PAGE_BEFORE);
            propSet.setPropertyValue("PageNumberOffset", (short) 1);

            // Page style 변경.
            // https://wiki.openoffice.org/wiki/Documentation/DevGuide/Text/Page_Layout
            int leftMargin = Transform.translateHwp2Office(page.marginLeft);
            int rightMargin = Transform.translateHwp2Office(page.marginRight);
            int topMargin = Transform.translateHwp2Office(page.marginTop)
                    + Transform.translateHwp2Office(page.marginHeader);
            int bottomMargin = Transform.translateHwp2Office(page.marginBottom)
                    + Transform.translateHwp2Office(page.marginFooter);
            setPageMargin(wContext.mMyDocument, wContext.mTextCursor, leftMargin, rightMargin, topMargin, bottomMargin);
        } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                | WrappedTargetException e) {
            e.printStackTrace();
        }
    }

    /*
     * margin value should be 100 times millimeter. ex) 1200 = 12mm margin
     */
    public static void setPageMargin(XTextDocument myDoc, XTextCursor xTCursor, int left, int right, int top,
            int buttom) {
        try {
            XStyleFamiliesSupplier xSupplier = UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, myDoc);
            XPropertySet xTextCursorProps = UnoRuntime.queryInterface(XPropertySet.class, xTCursor);
            String pageStyleName = xTextCursorProps.getPropertyValue("PageStyleName").toString();
            XNameAccess xFamilies = UnoRuntime.queryInterface(XNameAccess.class, xSupplier.getStyleFamilies());
            XNameContainer xFamily = UnoRuntime.queryInterface(XNameContainer.class, xFamilies.getByName("PageStyles"));
            XStyle xStyle = UnoRuntime.queryInterface(XStyle.class, xFamily.getByName(pageStyleName));
            XPropertySet xStyleProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xStyle);
            xStyleProps.setPropertyValue("LeftMargin", Short.valueOf((short) left));
            xStyleProps.setPropertyValue("RightMargin", Short.valueOf((short) right));
            xStyleProps.setPropertyValue("TopMargin", Short.valueOf((short) top));
            xStyleProps.setPropertyValue("BottomMargin", Short.valueOf((short) buttom));
        } catch (UnknownPropertyException e1) {
            e1.printStackTrace();
        } catch (WrappedTargetException e1) {
            e1.printStackTrace();
        } catch (NoSuchElementException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (PropertyVetoException e) {
            e.printStackTrace();
        }
    }

    public static void transSection(String customPageStyleName, XText xText, XTextCursor cursor, boolean nextPage) {
        XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, cursor);
        try {
            props.setPropertyValue("PageDescName", customPageStyleName);
            // props.setPropertyValue("PageNumberOffset", (short)1 );
        } catch (IllegalArgumentException | UnknownPropertyException | PropertyVetoException
                | WrappedTargetException e) {
            e.printStackTrace();
        }
    }

	public static void putPageNum(WriterContext wContext, Ctrl_PageNumPos numPoz) {
		try {
			// get current style for page style family
	        XStyleFamiliesSupplier xSupplier = UnoRuntime.queryInterface(XStyleFamiliesSupplier.class, wContext.mMyDocument);
	        XNameAccess xFamilies
	    		= (XNameAccess) UnoRuntime.queryInterface(XNameAccess.class, xSupplier.getStyleFamilies());
	        XNameContainer xFamily
	    		= (XNameContainer) UnoRuntime.queryInterface(XNameContainer.class, xFamilies.getByName("PageStyles"));
	        String pageStyleName = pageStyleNameMap.get(secdIndex);
	        XStyle xStyle = UnoRuntime.queryInterface(XStyle.class, xFamily.getByName(pageStyleName));
	        XPropertySet xStyleProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, xStyle);

	        XText footerText = null;

	        switch (numPoz.pos) {
	        case LEFT_TOP:
	        case CENTER_TOP:
	        case RIGHT_TOP:
	        case OUTER_TOP:
	        case INNER_TOP:
		        boolean headerOn = (boolean) xStyleProps.getPropertyValue("HeaderIsOn");
		        if (headerOn == false) {
		        	xStyleProps.setPropertyValue("HeaderIsOn", Boolean.TRUE);
		        }
		        footerText = UnoRuntime.queryInterface(XText.class, xStyleProps.getPropertyValue("HeaderText"));
	        	break;
	        case LEFT_BOTTOM:
	        case BOTTOM_CENTER:
	        case RIGHT_BOTTOM:
	        case OUTER_BOTTOM:
	        case INNER_BOTTOM:
		        boolean footerOn = (boolean) xStyleProps.getPropertyValue("FooterIsOn");
		        if (footerOn == false) {
		        	xStyleProps.setPropertyValue("FooterIsOn", Boolean.TRUE);
		        }
		        footerText = UnoRuntime.queryInterface(XText.class, xStyleProps.getPropertyValue("FooterText"));
	        	break;
	        }

	        XTextCursor footerCursor = footerText.createTextCursor();
	
	        /* set footer text properties via its cursor: font, font size, paragraph orientation    */
	        XPropertySet xFootProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, footerCursor);
	        xFootProps.setPropertyValue("CharFontName", "맑은고딕");
	        xFootProps.setPropertyValue("CharHeight", 10.0f);
	        switch (numPoz.pos) {
	        case NONE:
	        case LEFT_TOP:
	        case LEFT_BOTTOM:
		        xFootProps.setPropertyValue("ParaAdjust", ParagraphAdjust.LEFT);
		        break;
	        case CENTER_TOP:
	        case BOTTOM_CENTER:
		        xFootProps.setPropertyValue("ParaAdjust", ParagraphAdjust.CENTER);
		        break;
	        case RIGHT_TOP:
	        case RIGHT_BOTTOM:
		        xFootProps.setPropertyValue("ParaAdjust", ParagraphAdjust.RIGHT);
		        break;
	        case OUTER_TOP:
	        case OUTER_BOTTOM:
	        case INNER_TOP:
	        case INNER_BOTTOM:
	        	/* 처리하지 못하므로 가운데 */
		        xFootProps.setPropertyValue("ParaAdjust", ParagraphAdjust.CENTER);
		        break;
	        }
	
	        Object oPageNum = wContext.mMSF.createInstance("com.sun.star.text.TextField.PageNumber");
	        XTextField numField = UnoRuntime.queryInterface(XTextField.class, oPageNum);
	        XPropertySet xNumProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class, numField);
	        switch(numPoz.numShape) {
	        case DIGIT:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.ARABIC);
		        break;
	        case CIRCLE_DIGIT:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.CIRCLE_NUMBER);
		        break;
	        case ROMAN_CAPITAL:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.ROMAN_UPPER);
		        break;
	        case ROMAN_SMALL:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.ROMAN_LOWER);
		        break;
	        case LATIN_CAPITAL:
	        case CIRCLED_LATIN_CAPITAL:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.CHARS_UPPER_LETTER);
		        break;
	        case LATIN_SMALL:
	        case CIRCLED_LATIN_SMALL:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.CHARS_LOWER_LETTER);
		        break;
	        case HANGLE_SYLLABLE:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.HANGUL_SYLLABLE_KO);
		        break;
	        case CIRCLED_HANGUL_SYLLABLE:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.HANGUL_CIRCLED_SYLLABLE_KO);
		        break;
	        case HANGUL_JAMO:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.HANGUL_JAMO_KO);
		        break;
	        case CIRCLED_HANGUL_JAMO:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.HANGUL_CIRCLED_JAMO_KO);
		        break;
	        case HANGUL_PHONETIC:
	        case IDEOGRAPH:
	        case CIRCLED_IDEOGRAPH:
	        case DECAGON_CIRCLE:
	        case DECAGON_CRICLE_HANGJA:
	        case SYMBOL:
	        case USER_CHAR:
        	default:
		        xNumProps.setPropertyValue("NumberingType", NumberingType.ARABIC);
		        break;
	        }
	        xNumProps.setPropertyValue("SubType", PageNumberType.CURRENT);
	
			// add text fields to the footer
	        XText xText = footerCursor.getText();
	        xText.insertTextContent(footerCursor, numField, false);
		} catch (Exception | IllegalArgumentException e) {
			e.printStackTrace();
		}

	}  // end of setPageNumbers()
	
}
