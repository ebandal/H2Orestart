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
 * 본 제품은 한글과컴퓨터의 ᄒᆞᆫ글 문서 파일(.hwp) 공개 문서를 참고하여 개발하였습니다.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.sun.star.beans.Property;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.beans.XPropertySetInfo;
import com.sun.star.container.XNameAccess;
import com.sun.star.io.IOException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.ucb.XFileIdentifierConverter;
import com.sun.star.ucb.XSimpleFileAccess;
import com.sun.star.uno.Any;
import com.sun.star.uno.Exception;
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
    	StringBuffer sb = new StringBuffer("{");

    	try {
    		int len = Array.getLength(obj);
    		for (int i=0; i<len; i++) {
    			String value = "";
        		Object childObj = Array.get(obj, i);
        		Class arrayClass = childObj.getClass();
        		
				if (arrayClass.getName().startsWith("java.lang.")) {
					value = childObj==null?"null":childObj.equals("")?"\"\"":childObj.toString();
				} else if (arrayClass.getName().startsWith("[")) {
					value = "("+arrayClass.getName()+")"+printArrayRecursive(arrayClass, childObj, step+1);
				} else {
					value = "("+arrayClass.getName()+")"+printRecursive(arrayClass, childObj, step+1);
				}
        		sb.append("["+value+"]");
    		}
		} catch (IllegalArgumentException | SecurityException e) {
			e.printStackTrace();
		}
		sb.append("}");
		
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
  				
  				if (c.getName().startsWith("java.lang.")) {
  					value = obj==null?"null":obj.equals("")?"\"\"":obj.toString();
				} else if (c.getName().startsWith("[")) {
					value = "("+property.Type.getTypeName()+")"+printArrayRecursive(c, obj, step+1);
				} else {
					value = "("+property.Type.getTypeName()+")"+printRecursive(c, obj, step+1);
				}
  				log.finest(property.Name+"="+value);
  			} catch (UnknownPropertyException | IllegalArgumentException e) {
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
