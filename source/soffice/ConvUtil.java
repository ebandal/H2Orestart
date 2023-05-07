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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.sun.star.awt.XBitmap;
import com.sun.star.beans.Property;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.beans.XPropertySetInfo;
import com.sun.star.container.XNameAccess;
import com.sun.star.io.IOException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.text.TextContentAnchorType;
import com.sun.star.text.XTextFrame;
import com.sun.star.text.XTextColumns;
import com.sun.star.text.XTextRange;
import com.sun.star.ucb.XFileIdentifierConverter;
import com.sun.star.ucb.XSimpleFileAccess;
import com.sun.star.uno.Any;
import com.sun.star.uno.Exception;
import com.sun.star.uno.XInterface;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.paragraph.CharShape;


public class ConvUtil {
	private static final Logger log = Logger.getLogger(ConvUtil.class.getName());

	public static short selectCharShapeID(List<CharShape> charShapes, int startPos) {
		short charShapeID = 0;
		
		if (charShapes.size()==1) {
			charShapeID = (short)charShapes.get(0).charShapeID;
		} else if (charShapes.size()>1) {
			for(CharShape shape: charShapes) {
				if (startPos >= shape.start) {
					charShapeID = (short)shape.charShapeID;
				}
			}
		}
		return charShapeID;
	}

	
    public static String convertToURL(WriterContext wContext, String sBase, String sSystemPath) {
        String sURL = null;
        
        try {
            XFileIdentifierConverter xFileConverter 
            		= UnoRuntime.queryInterface(XFileIdentifierConverter.class,
            				wContext.mMCF.createInstanceWithContext("com.sun.star.ucb.FileContentProvider", wContext.mContext));
            sURL = xFileConverter.getFileURLFromSystemPath(sBase, sSystemPath );
        } catch (com.sun.star.uno.Exception e) {
            e.printStackTrace();
        } finally {
            return sURL;
        }
    }
    
    public static String convertToSystemPath(WriterContext wContext, String fileURL) {
        String systemPath = null;
        
        try {
            XFileIdentifierConverter xFileConverter 
            		= UnoRuntime.queryInterface(XFileIdentifierConverter.class,
            				wContext.mMCF.createInstanceWithContext("com.sun.star.ucb.FileContentProvider", wContext.mContext));
            systemPath = xFileConverter.getSystemPathFromFileURL(fileURL);
        } catch (com.sun.star.uno.Exception e) {
            e.printStackTrace();
        } finally {
            return systemPath;
        }
    }

    public static boolean checkFile(WriterContext wContext, String aURL ){
        boolean bExists = false;
        try {
            XSimpleFileAccess xSFA = UnoRuntime.queryInterface(
							                    XSimpleFileAccess.class, 
							                    wContext.mMCF.createInstanceWithContext("com.sun.star.ucb.SimpleFileAccess", wContext.mContext));
            bExists = xSFA.exists(aURL) && !xSFA.isFolder(aURL);
        } catch (com.sun.star.ucb.CommandAbortedException ex){
            ex.printStackTrace();
        } catch (com.sun.star.uno.Exception ex){
            ex.printStackTrace();
        } catch (java.lang.Exception ex){
            ex.printStackTrace();
        }
        return bExists;
    }
    
    public static <T> String printRecursive(Class<?> c, Object obj, int step) {
    	StringBuffer sb = new StringBuffer("{");

    	try {
			while (c!=null) {
				List<Method> methods = Arrays.asList(c.getDeclaredMethods());
				
				Field[] members = c.getDeclaredFields();
				for (Field f: members) {
					// if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) continue;
					
					if (Modifier.isPrivate(f.getModifiers()) || Modifier.isProtected(f.getModifiers())) {
						if (f.getName().equals("m_value")) {
							Method getValueMethod = methods.stream().filter(m -> m.getName().equals("getValue")).findAny().get();
							if (getValueMethod!=null) {
								Integer m_value = (Integer)getValueMethod.invoke(obj, null);
								sb.append("m_value="+m_value);
							}
						}
					} else {
						Object childObj = f.get(obj);
						String fieldName = f.getName(); 
						Class<?> childC = childObj.getClass();
						
						if (fieldName.equals("UNOTYPEINFO")) continue;
	
						if (obj instanceof Any) {
					    	Method getObjectMethod = c.getDeclaredMethod("getObject", (Class<?>[])null);
							Object internalObj = getObjectMethod.invoke(obj, (Object[])null);
							if (internalObj==null) {
								sb.append(fieldName+"=null");
							} else {
								String value = printRecursive(childC, childObj, step+1); 
								sb.append(fieldName+"="+value+",");
							}
						} else if (childC.getName().startsWith("java.lang")) {
							sb.append(fieldName+"="+childObj+",");
						} else if (childC.getName().startsWith("[")) {
							sb.append(fieldName+"="+printArrayRecursive(childC, childObj, step+1)+",");
						} else if (obj.getClass() != childObj.getClass()) {
						 	sb.append(fieldName+"="+printRecursive(childC, childObj, step+1)+",");
						} else {
							// enum type ???
							// sb.append(fieldName+"="+childObj.toString()+",");
						}
					}
				}
				c = c.getSuperclass();
			}
		} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		sb.append("}");
    	return sb.toString();
    }

    public static <T> String printArrayRecursive(Class<T> c, Object obj, int step) {
    	StringBuffer sb = new StringBuffer("[");

    	try {
    		int len = Array.getLength(obj);
    		len = Math.min(len, 100);
    		for (int i=0; i<len; i++) {
    			String value = "";
        		Object childObj = Array.get(obj, i);
        		Class arrayClass = childObj.getClass();
        		
        		switch(arrayClass.getName()) {
        		case "java.lang.Byte":
        		case "java.lang.Integer":
        		case "java.lang.Long":
        		case "java.lang.String":
        		case "java.lang.Boolean":
        			value = childObj.toString();
        			break;
        		case "com.sun.star.beans.PropertyValue":
        		case "com.sun.star.style.TabStop":
        		case "com.sun.star.drawing.EnhancedCustomShapeParameterPair":
        		case "com.sun.star.drawing.EnhancedCustomShapeTextFrame":
        		case "com.sun.star.drawing.EnhancedCustomShapeSegment":
					value = printRecursive(arrayClass, childObj, step+1);
        			break;
    			default:
    				log.finest("unhandled class = " + arrayClass.getName());
					if (arrayClass.getName().startsWith("[]")) {
						value = printArrayRecursive(arrayClass, childObj, step+1);
					} else {
						value = "("+arrayClass.getName()+")"+printRecursive(arrayClass, childObj, step+1);
					}
    				break;
        		}
        		
        		sb.append("{"+value+"}");
    		}
		} catch (IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
		}
		sb.append("]");
		
    	return sb.toString();
    }

  	public static void printProperties(XPropertySet xPropertySet) {
  		XPropertySetInfo setInfo = xPropertySet.getPropertySetInfo();
  		Property[] properties = setInfo.getProperties();
  		
  		for (Property property: properties) {
  			int step = 0;
  			String value = "";
  			
  			try {
                Object obj = setInfo.getPropertyByName(property.Name);
  				Class<?> c = obj.getClass();
  				
  				if (property.Name.equals("FillBitmapURL")) {
  					log.finest("FillBitmapURL");
  				}
  				
  				switch(property.Type.getTypeName()) {
  				case "boolean":
  				case "short":
  				case "long":
  				case "string":
  				case "byte":
  				case "float":
	  				{
	  					Object propValue = xPropertySet.getPropertyValue(property.Name);
	  					if (propValue instanceof Any) {
	  						Any any = (Any)propValue;
	  						com.sun.star.uno.Type type = any.getType();
	  						Object ob = any.getObject();
	  						if (type.getTypeName().equals("com.sun.star.awt.XBitmap")) {
	  							XBitmap xBitmap = (XBitmap)UnoRuntime.queryInterface(XBitmap.class, ob);
	  		  					value = Base64.getEncoder().encodeToString(xBitmap.getDIB());
	  						} else {
		  						value = ob==null?"":ob.toString();
	  						}
	  					} else {
	  						value = propValue.toString();
	  					}
	  				}
  					break;
  				case "com.sun.star.lang.Locale":
	  				{
	  					com.sun.star.lang.Locale locale = (com.sun.star.lang.Locale)xPropertySet.getPropertyValue(property.Name);
	  					value = locale.Country + "," + locale.Language;
	  				}
	  				break;
  				case "com.sun.star.awt.Point":
	  				{
	  					com.sun.star.awt.Point point = (com.sun.star.awt.Point)xPropertySet.getPropertyValue(property.Name);
	  					value = point.X + "," + point.Y;
	  				}
	  				break;
  				case "com.sun.star.awt.XBitmap":
	  				{
	  					Object ob = xPropertySet.getPropertyValue(property.Name);
	  					com.sun.star.awt.XBitmap xBitmap = (XBitmap) UnoRuntime.queryInterface(com.sun.star.awt.XBitmap.class, ob);
	  					value = Base64.getEncoder().encodeToString(xBitmap.getDIB());
	  				}
	  				break;
  				case "com.sun.star.awt.Rectangle":
	  				{
	  					com.sun.star.awt.Rectangle rect = (com.sun.star.awt.Rectangle)xPropertySet.getPropertyValue(property.Name);
	  					value = rect.X + "," + rect.Y + "," + rect.Width + "," + rect.Height;
	  				}
	  				break;
  				case "com.sun.star.text.TextContentAnchorType":
  				case "com.sun.star.drawing.BitmapMode":
  				case "com.sun.star.drawing.RectanglePoint":
  				case "com.sun.star.drawing.FillStyle":
  				case "com.sun.star.drawing.LineStyle":
  				case "com.sun.star.awt.FontSlant":
  				case "com.sun.star.text.WrapTextMode":
  				case "com.sun.star.text.WritingMode":
  				case "com.sun.star.drawing.LineCap":
  				case "com.sun.star.drawing.LineJoint":
  				case "com.sun.star.drawing.TextAnimationDirection":
  				case "com.sun.star.drawing.TextAnimationKind":
  				case "com.sun.star.drawing.TextFitToSizeType":
  				case "com.sun.star.drawing.TextHorizontalAdjust":
  				case "com.sun.star.drawing.TextVerticalAdjust":

	  				{
	  					com.sun.star.uno.Enum unoEnum = (com.sun.star.uno.Enum)xPropertySet.getPropertyValue(property.Name);
	  					value = String.valueOf(unoEnum.getValue());
	  				}
	  				break;
  				case "com.sun.star.awt.Gradient":
	  				{
	  					com.sun.star.awt.Gradient gra = (com.sun.star.awt.Gradient)xPropertySet.getPropertyValue(property.Name);
						value = gra.StartColor + "," + gra.EndColor + "," + gra.Angle + "," + gra.Border + "," + gra.XOffset
									+ "," + gra.YOffset + "," + gra.StartIntensity + "," + gra.EndIntensity + "," + gra.StepCount
									+ "," + gra.Style.toString();
	  				}
	  				break;
  				case "com.sun.star.drawing.Hatch":
	  				{
	  					com.sun.star.drawing.Hatch hatch = (com.sun.star.drawing.Hatch)xPropertySet.getPropertyValue(property.Name);
	  					value = hatch.Color + "," + hatch.Distance + "," + hatch.Angle + "," + hatch.Style.toString();
	  				}
	  				break;
  				case "com.sun.star.text.GraphicCrop":
	  				{
	  					com.sun.star.text.GraphicCrop crop = (com.sun.star.text.GraphicCrop)xPropertySet.getPropertyValue(property.Name);
	  					value = crop.Top + "," + crop.Bottom + "," + crop.Left + "," + crop.Right;
	  				}
	  				break;
  				case "com.sun.star.text.XTextColumns":
	  				{
	  					Object ob = xPropertySet.getPropertyValue(property.Name);
	  					XTextColumns xTC = (XTextColumns) UnoRuntime.queryInterface(XTextColumns.class, ob);
	  					value = xTC==null?"":String.valueOf(xTC.getColumnCount());
	  				}
	  				break;
  				case "com.sun.star.text.XTextRange":
	  				{
	  					Object ob = xPropertySet.getPropertyValue(property.Name);
	  					XTextRange xTR = (XTextRange) UnoRuntime.queryInterface(XTextRange.class, ob);
	  					value = xTR.getString();
	  				}
	  				break;
  				case "com.sun.star.drawing.LineDash":
	  				{
	  					com.sun.star.drawing.LineDash dash = (com.sun.star.drawing.LineDash)xPropertySet.getPropertyValue(property.Name);
	  					value = dash.Dots + "," + dash.DotLen + "," + dash.Dashes + "," + dash.DashLen + "," + dash.Distance;
	  				}
	  				break;
  				case "com.sun.star.drawing.PolyPolygonBezierCoords":
	  				{
	  					com.sun.star.drawing.PolyPolygonBezierCoords poly = (com.sun.star.drawing.PolyPolygonBezierCoords)xPropertySet.getPropertyValue(property.Name);
	  					value = "" + poly.Coordinates.length + "[]";
	  				}
	  				break;
  				case "com.sun.star.style.LineSpacing":
	  				{
	  					com.sun.star.style.LineSpacing space = (com.sun.star.style.LineSpacing)xPropertySet.getPropertyValue(property.Name);
	  					value = space.Mode + "," + space.Height;
	  				}
	  				break;
  				case "com.sun.star.text.XTextFrame":
	  				{
	  					Object ob = xPropertySet.getPropertyValue(property.Name);
	  					XInterface xInterface = (XInterface)UnoRuntime.queryInterface(property.Type.getClass(), ob);
	  					value = xInterface==null?"":xInterface.toString();
	  				}
	  				break;
  				case "com.sun.star.container.XIndexReplace":
  				case "com.sun.star.container.XNameContainer":
  				case "com.sun.star.drawing.HomogenMatrix3":
	  				log.finest("unhandled type = " + property.Type.getTypeName());
  					break;
				default:
					if (property.Type.getTypeName().startsWith("[]")) {
	  					Object propValue = xPropertySet.getPropertyValue(property.Name);
						value = printArrayRecursive(c, propValue, step+1);
					} else {
		  				log.finest("unhandled type = " + property.Type.getTypeName());
	  					Object propValue = xPropertySet.getPropertyValue(property.Name);
						value = "("+property.Type.getTypeName()+")"+printRecursive(c, propValue, step+1);
					}
	  				break;
  				}
  				
  				log.finest(property.Name+"="+value);
  			} catch (UnknownPropertyException | IllegalArgumentException | WrappedTargetException e) {
  				e.printStackTrace();
  			}
  		}
  	}
  	
  	public static class DebuggingElapse {
  		private static Map<String, LocalTime> localTimeMap = new HashMap<String, LocalTime>();
  		private static Map<String, Long> resultMap = new HashMap<String, Long>();
  		
  		private static boolean enabled = true;

  		static void setStart() {
  			setStart("temp");
  		}
  		static void setFinish() {
  			setFinish("temp");
  		}
  		static long getElapseTime() {
  			return getElapseTime("temp");
  		}
  		
  		public static void setStart(String timerName) {
  			localTimeMap.put(timerName, LocalTime.now());
  		}
  		
  		public static void setFinish(String timerName) {
  			if (enabled) {
	  			LocalTime start = localTimeMap.get(timerName);
  				localTimeMap.remove(timerName);
	  			if (start!=null) {
	  				LocalTime finish = LocalTime.now();
	  				resultMap.put(timerName, Duration.between(start, finish).toMillis());
	  			}
  			} else {
  				resultMap.put(timerName, 0L);
  			}
  		}
  		
  		public static long getElapseTime(String timerName) {
  			if (enabled) {
  				Long elapsed = resultMap.get(timerName);
  				resultMap.remove(timerName);
  				return elapsed==null?0:elapsed;
  			} else { 
  				return 0;
  			}
  		}
  	}
  	
    /* get Version of LibreOffice
     * 
     */
  	public static int getVersion(WriterContext wContext) {
  		int version = 0;
  		
  		try {
		    Object configProviderObject = wContext.mMCF.createInstanceWithContext("com.sun.star.configuration.ConfigurationProvider", wContext.mContext);
		    XMultiServiceFactory xConfigServiceFactory = (XMultiServiceFactory) UnoRuntime.queryInterface(XMultiServiceFactory.class, configProviderObject);
		    String readConfAccess = "com.sun.star.configuration.ConfigurationAccess";
		    PropertyValue[] properties = new PropertyValue[1];
		    properties[0] = new PropertyValue();
		    properties[0].Name = "nodepath";
		    properties[0].Value = "/org.openoffice.Setup/Product";
		    Object configReadAccessObject = xConfigServiceFactory.createInstanceWithArguments(readConfAccess, properties);
		    XNameAccess xConfigNameAccess = (XNameAccess) UnoRuntime.queryInterface(XNameAccess.class, configReadAccessObject);
			String[] names = xConfigNameAccess.getElementNames();
			for (String name: names) {
				String value = xConfigNameAccess.getByName(name).toString();
				if (name.equals("ooSetupVersion")) {
					version = Integer.valueOf(value.replaceAll("(\\d)\\.(\\d)(\\.\\d)*", "$1$2"));	// 6.4.1.2 -> 64
				}
				// Name=ooName,Value=LibreOffice
				// Name=ooVendor,Value=The Document Foundation
				// Name=ooSetupVersion,Value=6.4
				// Name=ooSetupExtension,Value=.7.2
				// Name=ooSetupLastVersion,Value=7.1
				// Name=LastTimeDonateShown,Value=1622522523
				// Name=ooSetupVersionAboutBox,Value=6.4.7.2
				// Name=LastTimeGetInvolvedShown,Value=1622522523
				// Name=ooSetupVersionAboutBoxSuffix,Value=
			}
  		} catch (Exception e) {
  			e.printStackTrace();
  			log.severe(e.getMessage());
  		}
		return version;
  	}
}
