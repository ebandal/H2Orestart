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
package ebandal.libreoffice.comp;

import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;
import com.sun.star.util.CloseVetoException;
import com.sun.star.util.XCloseable;

import HwpDoc.CustomLogFormatter;
import HwpDoc.HwpDetectException;
import HwpDoc.HwpSection;
import HwpDoc.Exception.CompoundDetectException;
import HwpDoc.Exception.CompoundParseException;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.Exception.OwpmlParseException;
import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_CharShape;
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

import com.sun.star.lib.uno.helper.Factory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.sun.star.beans.PropertyValue;
import com.sun.star.io.XInputStream;
import com.sun.star.lang.EventObject;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiServiceFactory;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.text.XTextDocument;
import com.sun.star.lib.uno.helper.WeakBase;
import com.sun.star.lib.uno.adapter.XInputStreamToInputStreamAdapter;


public final class H2OrestartImpl extends WeakBase implements ebandal.libreoffice.XH2Orestart,
                                                            com.sun.star.lang.XInitialization,
                                                            com.sun.star.document.XImporter,
                                                            com.sun.star.document.XFilter,
                                                            com.sun.star.document.XExtendedFilterDetection,
                                                            com.sun.star.util.XCloseListener {
    private static final Logger log = Logger.getLogger(H2OrestartImpl.class.getName());

    private static final String m_implementationName = H2OrestartImpl.class.getName();
    /** Service name for the component */
    public static final String __serviceName = "ebandal.libreoffice.H2Orestart";
    private static final String[] m_serviceNames = { "ebandal.libreoffice.H2Orestart" };
    private static WriterContext writerContext;
    private static String detectedFileExt;
    private static Logger rootLogger;
    private static String tmpFilePath;

    public H2OrestartImpl( XComponentContext context ) {
        writerContext = new WriterContext();
        writerContext.mContext = context;
        writerContext.mMCF = writerContext.mContext.getServiceManager();
        writerContext.userHomeDir = getAppCachePath();
        if (rootLogger==null) {
            cleanTmpFolder();
            initialLogger();
        }
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
        File file = null;
        String filePath = null;
        Object inputStream = null;

        for (int i=0; i<lDescriptor.length; i++) {
            switch(lDescriptor[i].Name) {
            case "URL":
                filePath = lDescriptor[i].Value.toString();
                break;
            case "InputStream":
                inputStream = lDescriptor[i].Value;
                break;
            case "FilterName":
            case "Referer":
            case "StatusIndicator":
            case "InteractionHandler":
            case "Stream":
            case "FrameName":
            case "MacroExecutionMode":
            case "UpdateDocMode":
            case "DocumentBaseURL":
            case "DocumentService":
            case "Replaceable":
                log.fine("Name="+lDescriptor[i].Name+",Value="+lDescriptor[i].Value.toString());
            }
        }

        if (filePath!=null && filePath.startsWith("file:///")) {
            String systemPath = ConvUtil.convertToSystemPath(writerContext, filePath);
            file = new File(systemPath);
        } else {
            if (tmpFilePath==null) {
                tmpFilePath = copyToTmpFile(inputStream);
            }
            file = new File(tmpFilePath);
        }

        return impl_import(file);
    }

    @Override
    public void initialize(Object[] args) throws Exception {
        log.fine("initialize called");

        for (int i=0; i<args.length; i++) {
            if (args[i] instanceof PropertyValue[]) {
                PropertyValue[] pValues = (PropertyValue[])args[i];
                for (PropertyValue pValue: pValues) {
                    log.finest("Name="+pValue.Name+",Value="+pValue.Value.toString());
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
        String url = null;
        Object inputStream = null;
        
        for (int i=0; i<args.length; i++) {
            for (int j=0; j<args[i].length; j++) {
                switch(args[i][j].Name) {
                case "URL":
                    url = args[i][j].Value.toString();
                    break;
                case "InputStream":
                    inputStream = args[i][j].Value;
                    break;
                default:
                    log.finest("Name="+args[i][j].Name + ", Value="+args[i][j].Value);
                    break;
                }
            }
        }

        // https://... , "file:///... , "ftp://... , smb://... 
        log.info("URL starts with : " + url.replaceAll("^([^:]*://.{10}).*", "$1"));

        if (url!=null && url.startsWith("file:///")) {
            log.info("reading file directly");
            String systemPath = ConvUtil.convertToSystemPath(writerContext, url);
            detectedFileExt = WriterContext.detectHancom(new File(systemPath));
        } else if (inputStream!=null) {
            log.info("copying InputStream to temp File");
            tmpFilePath = copyToTmpFile(inputStream);
            detectedFileExt = WriterContext.detectHancom(new File(tmpFilePath));
        }

        if (detectedFileExt==null) {
            log.info("File is not Hancomm document.");
            typeName.setLength(0);
        } else {
            log.info("File is Hancomm document.");
        }

        try {
            writerContext.close();
        } catch (IOException | HwpDetectException e) {
            log.severe(e.getMessage());
        }

        return typeName.toString();
    }

    @Override
    public void disposing(EventObject arg0) {
        if (tmpFilePath!=null) {
            log.info("Disposing tmp file");
            try {
                Files.deleteIfExists(new File(tmpFilePath).toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            tmpFilePath=null;
        }
    }

    @Override
    public void notifyClosing(EventObject arg0) {
    }

    @Override
    public void queryClosing(EventObject arg0, boolean arg1) throws CloseVetoException {
    }

    private boolean impl_import(File file) {
        try {
            writerContext.open(file, detectedFileExt);
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
            for (int i=0; i < writerContext.getDocInfo().charShapeList.size(); i++) {
                // Bullet ID는 1부터 시작한다.
                ConvPara.makeCustomCharacterStyle(writerContext, i+1, (HwpRecord_CharShape)writerContext.getDocInfo().charShapeList.get(i));
            }
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
                Ctrl_SectionDef secd = (Ctrl_SectionDef)section.paraList.stream()
                                                               .filter(p -> p.p!=null && p.p.size()>0)
                                                               .flatMap(p -> p.p.stream())
                                                               .filter(c -> (c instanceof Ctrl_SectionDef)).findAny().get();
                ConvPage.makeCustomPageStyle(writerContext, secd);
            }
            for (int i=0; i<writerContext.getDocInfo().styleList.size();i++) {
                ConvPara.makeCustomParagraphStyle(writerContext, i, (HwpRecord_Style)writerContext.getDocInfo().styleList.get(i));
            }

            int secIndex = 0;
            for (int i=0; i<sections.size(); i++) {
                // context.mMyDocument.lockControllers();
                HwpSection section = sections.get(i);
                ConvPage.setSectionIndex(secIndex++);

                for (HwpParagraph para: section.paraList) {
                    HwpRecurs.printParaRecurs(writerContext, writerContext, para, null, 1);
                }
                // context.mMyDocument.unlockControllers();
            }

            // 화면 갱신 resume
            // writerContext.mMyDocument.unlockControllers();
        } catch (HwpDetectException e) {
            e.printStackTrace();
        }

        XCloseable xCloseable = (XCloseable) UnoRuntime.queryInterface(XCloseable.class, writerContext.mMyDocument);
        xCloseable.addCloseListener(this);

        return true;
    }

    private void initialLogger() {
        //initialize logger
        rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler: handlers) {
            if (handler instanceof ConsoleHandler || handler instanceof FileHandler) {
                rootLogger.removeHandler(handler);
            }
        }
        try {
            Path baseDir = getAppCachePath();
            Set<String> attrViews = baseDir.getFileSystem().supportedFileAttributeViews();
            if (attrViews.contains("posix")) {
                if (baseDir.toFile().exists()) {
                    Files.setPosixFilePermissions(baseDir, PosixFilePermissions.fromString("rwx------"));
                } else {
                    Files.createDirectories(baseDir,
                                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                }
            } else {
                Files.createDirectories(baseDir);
            }
            writerContext.userHomeDir = baseDir;
            // "%h" the value of the "user.home" system property
            FileHandler fileHandler = new FileHandler(baseDir.toAbsolutePath() + "/import_%g.log", 4194304, 10, false);
            fileHandler.setLevel(Level.INFO);
            CustomLogFormatter sformatter = new CustomLogFormatter();
            fileHandler.setFormatter(sformatter);
            rootLogger.addHandler(fileHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cleanTmpFolder() {
        Path tmpFolder = getAppCachePath();
        if (tmpFolder.toFile().exists()) {
            try (Stream<Path> paths = Files.find(tmpFolder, Integer.MAX_VALUE, 
                                                (path, attr) -> {
                                                    Instant delInstant = ZonedDateTime.now().minusDays(5).toInstant();
                                                    FileTime fileTime = FileTime.from(delInstant);
                                                    int comp = attr.creationTime().compareTo(fileTime);
                                                    if (path.toFile().isFile() && comp==-1) {
                                                        return true;
                                                    } else {
                                                        return false;
                                                    }
                                                })) {
                paths.forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void reset() {
        log.fine("Resetting Page info.");
        ConvPage.reset(writerContext);
        log.fine("Resetting Numbering info.");
        ConvNumbering.reset(writerContext);
        log.fine("Resetting Paragraph info.");
        ConvPara.reset(writerContext);
        log.fine("Resetting Equasion info.");
        ConvEquation.reset(writerContext);
        log.fine("Resetting Graphics info.");
        ConvGraphics.reset(writerContext);
        log.fine("Resetting Table info.");
        ConvTable.reset(writerContext);
        log.fine("Resetting Footnote info.");
        ConvFootnote.reset(writerContext);

        if (writerContext!=null) {
            log.fine("HwpFile still exists. Will be closed.");
            try {
                writerContext.close();
            } catch (IOException | HwpDetectException e) {
                log.severe(e.getMessage());
            }
        } else {
            log.fine("HwpFile not exists.");
        }
    }
    
    private String copyToTmpFile(Object inputStream) {
        String ret = null;
        byte[] buf = new byte[4096];
        XInputStream xinput = UnoRuntime.queryInterface(XInputStream.class, inputStream);
        try {
            Path baseDir = getAppCachePath();
            Set<String> attrViews = baseDir.getFileSystem().supportedFileAttributeViews();
            File tmpFile = null;
            if (attrViews.contains("posix")) {
                tmpFile = Files.createTempFile(baseDir, "H2O_TMP_", null,
                                               PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                               .toFile();
            } else {
                tmpFile = Files.createTempFile(baseDir, "H2O_TMP_", null)
                               .toFile();
            }
            ret = tmpFile.toString();
            try (FileOutputStream fos = new FileOutputStream(tmpFile);
                 XInputStreamToInputStreamAdapter adapter = new XInputStreamToInputStreamAdapter(xinput)) {
                while(true) {
                    int readLen = adapter.read(buf, 0, buf.length);
                    fos.write(buf, 0, readLen);
                    if (readLen != buf.length) {
                        break;
                    }
                }
            }
            xinput.closeInput();
        } catch (IOException | com.sun.star.io.IOException e) {
            e.printStackTrace();
        }

        return ret;
    }

    private Path getAppCachePath() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("linux")) {
            // Linux: Use XDG Base Directory Specification
            // https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html
            String cacheHomeDir = System.getenv("XDG_CACHE_HOME");
            if (cacheHomeDir == null || cacheHomeDir.isEmpty())
                return Paths.get(System.getProperty("user.home"), ".cache", "H2Orestart");
            else
                return Paths.get(cacheHomeDir, "H2Orestart");
        } else if (osName.contains("mac")) {
            // MacOS: Use ~/Library/Caches/H2Orestart
            // https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html
            return Paths.get(System.getProperty("user.home"), "Library", "Caches", "H2Orestart");
        } else {
            return Paths.get(System.getProperty("user.home"),".H2Orestart");
        }
    }
}
