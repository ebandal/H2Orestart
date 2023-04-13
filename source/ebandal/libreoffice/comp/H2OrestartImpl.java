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
package ebandal.libreoffice.comp;

import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

import HwpDoc.CustomLogFormatter;
import HwpDoc.HwpDetectException;
import HwpDoc.HwpFile;
import HwpDoc.HwpSection;
import HwpDoc.Exception.CompoundDetectException;
import HwpDoc.Exception.CompoundParseException;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.Exception.OwpmlParseException;
import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_Numbering;
import HwpDoc.HwpElement.HwpRecord_Style;
import HwpDoc.paragraph.Ctrl_SectionDef;
import HwpDoc.paragraph.HwpParagraph;
import soffice.ConvEquation;
import soffice.ConvFootnote;
import soffice.ConvGraphics;
import soffice.ConvNumbering;
import soffice.ConvPage;
import soffice.ConvPara;
import soffice.ConvTable;
import soffice.ConvUtil;
import soffice.HwpRecurs;
import soffice.WriterContext;
import working.Hwp2Swriter;

import com.sun.star.lib.uno.helper.Factory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.sun.star.beans.PropertyValue;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.text.XTextDocument;
import com.sun.star.lib.uno.helper.WeakBase;


public final class H2OrestartImpl extends WeakBase implements ebandal.libreoffice.XH2Orestart,
															com.sun.star.lang.XInitialization,
															com.sun.star.document.XImporter,
															com.sun.star.document.XFilter,
															com.sun.star.document.XExtendedFilterDetection {
	private static final Logger log = Logger.getLogger(Hwp2Swriter.class.getName());

    private static final String m_implementationName = H2OrestartImpl.class.getName();
    /** Service name for the component */
    public static final String __serviceName = "ebandal.libreoffice.H2Orestart";
    private static final String[] m_serviceNames = { "ebandal.libreoffice.H2Orestart" };
    private static WriterContext writerContext;
    Logger rootLogger;

    public H2OrestartImpl( XComponentContext context ) {
        writerContext = new WriterContext();
        writerContext.mContext = context;
        writerContext.mMCF = writerContext.mContext.getServiceManager();
        initialLogger();
    };

    public static XSingleComponentFactory __getComponentFactory( String sImplementationName ) {
    	log.fine("__getComponentFactory called");
        XSingleComponentFactory xFactory = null;

        if ( sImplementationName.equals( m_implementationName ) )
            xFactory = Factory.createComponentFactory(H2OrestartImpl.class, m_serviceNames);
        return xFactory;
    }

    public static boolean __writeRegistryServiceInfo( XRegistryKey xRegistryKey ) {
    	log.fine("__writeRegistryServiceInfo called");
        return Factory.writeRegistryServiceInfo(m_implementationName,
                                                m_serviceNames,
                                                xRegistryKey);
    }

	
	@Override
	public void cancel() {
		log.fine("cancel called");
	}

	@Override
	public void setTargetDocument(XComponent arg0) throws IllegalArgumentException {
		log.fine("setTargetDocument called");
		
        writerContext.mMyDocument = UnoRuntime.queryInterface(XTextDocument.class, arg0);
        writerContext.mMSF = UnoRuntime.queryInterface(XMultiServiceFactory.class, writerContext.mMyDocument);
        writerContext.mText = writerContext.mMyDocument.getText();
        writerContext.mTextCursor = writerContext.mText.createTextCursor();
        WriterContext.version = ConvUtil.getVersion(writerContext);
	}

	@Override
	public boolean filter(PropertyValue[] lDescriptor) {
		log.fine("filter called");
		
		String filePath = null;

		for (int i=0; i<lDescriptor.length; i++) {
			switch(lDescriptor[i].Name) {
			case "URL":
				filePath = lDescriptor[i].Value.toString();
				break;
			case "FilterName":
			case "Referer":
			case "StatusIndicator":
			case "InteractionHandler":
			case "InputStream":
			case "Stream":
			case "FrameName":
			case "MacroExecutionMode":
			case "UpdateDocMode":
			case "DocumentBaseURL": // file:///D:/workspace/Ebook/hwpviewer/doc/%EB%B3%80%ED%99%98_%ED%95%9C%EA%B8%80%EB%AC%B8%EC%84%9C%ED%8C%8C%EC%9D%BC%ED%98%95%EC%8B%9D_%EB%B0%B0%ED%8F%AC%EC%9A%A9%EB%AC%B8%EC%84%9C_revision1.2.hwp\n"
			case "DocumentService": // com.sun.star.text.TextDocument
			case "Replaceable":
				log.fine("Name="+lDescriptor[i].Name+",Value="+lDescriptor[i].Value.toString());
			}
		}

		return impl_import(filePath); 
	}

	private boolean impl_import(String filePath) {
    	try {
			String systemPath = ConvUtil.convertToSystemPath(writerContext, filePath);
			writerContext.setFile(systemPath);
            writerContext.detect();
            writerContext.open();
		} catch (HwpDetectException | IOException | CompoundDetectException | NotImplementedException | CompoundParseException | DataFormatException | HwpParseException  e) {
			log.severe(e.getMessage());
			e.printStackTrace();
		} catch (OwpmlParseException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }

        // 화면 갱신 suspend
        // writerContext.mMyDocument.lockControllers();
        try {
            List<HwpSection> sections = writerContext.getSections();
    
            ConvPage.adjustFontIfNotExists(writerContext);    // 별 효과 없음.  차라리 미리 font 들을  OS에 설치하는 게 좋겠음.
            for (int i=0; i < writerContext.getDocInfo().bulletList.size(); i++) {
                // Bullet ID는 1부터 시작한다.
                ConvNumbering.makeCustomBulletStyle(writerContext, i+1, (HwpRecord_Bullet)writerContext.getDocInfo().bulletList.get(i));
            }
            for (int i=0; i < writerContext.getDocInfo().numberingList.size(); i++) {
                // Numbering ID는 1부터 시작한다.
                ConvNumbering.makeCustomNumberingStyle(writerContext, i+1, (HwpRecord_Numbering)writerContext.getDocInfo().numberingList.get(i));
            }
            
            for (HwpSection section: sections) {
                // 커스톰 PageStyle 생성
                Ctrl_SectionDef secd =  (Ctrl_SectionDef)section.paraList.stream()
                                                                .filter(p -> p.p!=null && p.p.size()>0)
                                                                .flatMap(p -> p.p.stream())
                                                                .filter(c -> (c instanceof Ctrl_SectionDef)).findAny().get();
                ConvPage.makeCustomPageStyle(writerContext, secd);
            }
            for (int i=0; i<writerContext.getDocInfo().styleList.size();i++) {
                ConvPara.makeCustomParagraphStyle(writerContext, i, (HwpRecord_Style)writerContext.getDocInfo().styleList.get(i));
            }
    
            long totalParas = sections.stream()
                    .peek(s -> System.out.println("Section Has " + s==null?0:s.paraList.size()==0?0:s.paraList.size() + " paras"))
                    .mapToLong(s -> s==null?0:s.paraList==null?0:s.paraList.size()).sum();
            long parasCnt = 0;
    
            int secIndex = 0;
            for (int i=0; i<sections.size(); i++) {
                // context.mMyDocument.lockControllers();
                HwpSection section = sections.get(i);
                ConvPage.setSectionIndex(secIndex++);
                
                for (HwpParagraph para: section.paraList) {
                    HwpRecurs.printParaRecurs(writerContext, para, null, 1);
                }
                // context.mMyDocument.unlockControllers();
            }
    
        	// 화면 갱신 resume
    		// writerContext.mMyDocument.unlockControllers();

    		log.fine("Cleaning temporary folder.");
			WriterContext.cleanTempFolder();
		} catch (IOException | HwpDetectException e) {
			e.printStackTrace();
		}
        
		return true;
	}

	private void initialLogger() {
	    //initialize logger
		Properties properties = new Properties();
		rootLogger = Logger.getLogger("");
		Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler: handlers) {
			if (handler instanceof ConsoleHandler || handler instanceof FileHandler) {
				rootLogger.removeHandler(handler);
			}
		}
		try {
			File jarFile = new File(H2OrestartImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			File jarDir = jarFile.getParentFile();
			if (jarDir != null && jarDir.isDirectory()) {
				try (FileInputStream fis = new FileInputStream(new File(jarDir, "logger.properties"))) {
					Files.createDirectories(Paths.get(System.getProperty("user.home"),".H2Orestart"));
					// "%h" the value of the "user.home" system property
					FileHandler fileHandler = new FileHandler("%h/.H2Orestart/import_%g.log", 4194304, 10, false);
			        // fileHandler.setLevel(Level.WARNING);
			        CustomLogFormatter sformatter = new CustomLogFormatter();
			        fileHandler.setFormatter(sformatter);
			        rootLogger.addHandler(fileHandler);
					ConsoleHandler cHandler = new ConsoleHandler();
					cHandler.setFormatter(sformatter);
			        // cHandler.setLevel(Level.WARNING);
			        rootLogger.addHandler(cHandler);
					properties.load(fis);
				}
			}
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void initialize(Object[] args) throws Exception {
		log.fine("initialize called");
		
		for (int i=0; i<args.length; i++) {
			if (args[i] instanceof PropertyValue[]) {
				PropertyValue[] pValues = (PropertyValue[])args[i];
				for (PropertyValue pValue: pValues) {
					log.fine("Name="+pValue.Name+",Value="+pValue.Value.toString());
					// Name=TemplateName,Value=
					// Name=UserData,Value=[Ljava.lang.String;@5af95039
					// Name=Name,Value=Hwp2002_Reader
					// Name=Enabled,Value=true
					// Name=Type,Value=Hwp2002_File
					// Name=UIComponent,Value=
					// Name=FileFormatVersion,Value=5
					// Name=FilterService,Value=ebandal.libreoffice.H2Orestart
					// Name=DocumentService,Value=com.sun.star.text.TextDocument
					// Name=ExportExtension,Value=Any[Type[void], null]
					// Name=Flags,Value=524353
					// Name=UINames,Value=[Lcom.sun.star.beans.PropertyValue;@543ea516
					// Name=UIName,Value=Hwp2002_Reader
					// Name=FilterName,Value=Hwp2002_Reader
					// Name=Referer,Value=private:user
					// Name=StatusIndicator,Value=Any[Type[com.sun.star.task.XStatusIndicator], com.sun.star.bridges.jni_uno.JNI_proxy@6362289 [oid=17f5d4f2670;mscx[0];2ec586b56b44bd5b9be3e6d5cc823, type=com.sun.star.task.XStatusIndicator]]
					// Name=InteractionHandler,Value=Any[Type[com.sun.star.task.XInteractionHandler], com.sun.star.bridges.jni_uno.JNI_proxy@5637b746 [oid=17f3994a280;mscx[0];2ec586b56b44bd5b9be3e6d5cc823, type=com.sun.star.task.XInteractionHandler]]
					// Name=InputStream,Value=Any[Type[com.sun.star.io.XInputStream], com.sun.star.bridges.jni_uno.JNI_proxy@17715b41 [oid=17f39971310;mscx[0];2ec586b56b44bd5b9be3e6d5cc823, type=com.sun.star.io.XInputStream]]
					// Name=Stream,Value=Any[Type[com.sun.star.io.XStream], com.sun.star.bridges.jni_uno.JNI_proxy@1db5da9e [oid=17f39971310;mscx[0];2ec586b56b44bd5b9be3e6d5cc823, type=com.sun.star.io.XStream]]
					// Name=MacroExecutionMode,Value=3
					// Name=UpdateDocMode,Value=2
					// Name=DocumentBaseURL,Value=file:///C:/workspace/doc/HWPML_revision1.2.hwp
					// Name=DocumentService,Value=com.sun.star.text.TextDocument
					// Name=Replaceable,Value=false
				}
			}
		}
		
		reset();
	}
	
	@Override
	public String detect(PropertyValue[][] args) {
		log.fine("detect called");
		reset();
		
	    StringBuffer typeName = new StringBuffer("Hwp2002_File");
    	
		for (int i=0; i<args.length; i++) {
			for (int j=0; j<args[i].length; j++) {
				switch(args[i][j].Name) {
				case "URL":
					{
						String systemPath = ConvUtil.convertToSystemPath(writerContext, args[i][j].Value.toString());
				    	try {
				    		// writerContext.hwp = new HwpFile(systemPath);
				    		writerContext.setFile(systemPath);
				    		writerContext.detect();
						} catch (HwpDetectException | IOException | CompoundDetectException | NotImplementedException | CompoundParseException | ParserConfigurationException | SAXException | DataFormatException  e) {
							e.printStackTrace();
							typeName.setLength(0);
						}
					}
					break;
				case "TypeName":
				case "Stream":
				case "InputStream":
					log.fine("Name="+args[i][j].Name + ", Value="+args[i][j].Value);
					break;
				}
			}
		}
		try {
			writerContext.close();
		} catch (IOException | HwpDetectException e) {
			log.severe(e.getMessage());
		}
		
	    return typeName.toString();
	}
	
	private void reset() {
		log.info("Resetting Page info.");
    	ConvPage.reset(writerContext);
		log.info("Resetting Numbering info.");
		ConvNumbering.reset(writerContext);
		log.info("Resetting Paragraph info.");
		ConvPara.reset(writerContext);
		log.info("Resetting Equasion info.");
		ConvEquation.reset(writerContext);
		log.info("Resetting Graphics info.");
		ConvGraphics.reset(writerContext);
		log.info("Resetting Table info.");
		ConvTable.reset(writerContext);
		log.info("Resetting Footnote info.");
		ConvFootnote.reset(writerContext);

		if (writerContext!=null) {
			log.fine("HwpFile still exists. Will be closed.");
			try {
				log.info("Cleaning temporary folder.");
				WriterContext.cleanTempFolder();
				writerContext.close();
			} catch (IOException | HwpDetectException e) {
				log.severe(e.getMessage());
			}
		} else {
			log.fine("HwpFile not exists.");
		}

	}
}
