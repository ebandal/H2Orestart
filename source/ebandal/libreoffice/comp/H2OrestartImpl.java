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
import soffice.HwpCallback;
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
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.InvalidAlgorithmParameterException;
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

    /** 변환 실패 시 soffice를 비정상 종료시킬지 결정하는 환경변수. */
    private static final String EXIT_ON_ERROR_ENV = "H2ORESTART_EXIT_ON_ERROR";
    /** 변환 실패로 soffice를 종료시킬 때 사용하는 종료코드. */
    private static final int EXIT_CODE_CONVERSION_FAILED = 1;

    public H2OrestartImpl( XComponentContext context ) {
        writerContext = new WriterContext();
        writerContext.mContext = context;
        writerContext.mMCF = writerContext.mContext.getServiceManager();
        writerContext.userHomeDir = getAppCachePath();
        if (rootLogger==null) {
            cleanTmpFolder();
            initialLogger();
            logEnvironment();
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

        log.info("Converting document. name=" + file.getName() + ", size=" + file.length()
                 + ", detectedType=" + detectedFileExt + ", LibreOffice=" + WriterContext.version);

        boolean imported = impl_import(file);
        if (imported==false) {
            abortIfRequested();
        }
        return imported;
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
                log.log(Level.SEVERE, e.toString(), e);
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
        WriterContext.setActiveContext(writerContext);
        try {
            writerContext.open(file, detectedFileExt);
        } catch (HwpDetectException | IOException | CompoundDetectException | NotImplementedException | CompoundParseException | DataFormatException | HwpParseException  e) {
            logFailure("Failed to open document.", e);
            return false;
        } catch (OwpmlParseException | ParserConfigurationException | SAXException e) {
            logFailure("Failed to open document.", e);
            return false;
        }

        // 화면 갱신 suspend
        // writerContext.mMyDocument.lockControllers();
        try {
            List<HwpSection> sections = writerContext.getSections();
            if (sections==null || sections.isEmpty()) {
                log.severe("No section has been parsed. Document is not converted.");
                return false;
            }

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
                
            	HwpCallback callback = new HwpCallback();

                for (HwpParagraph para: section.paraList) {
                    HwpRecurs.printParaRecurs(writerContext, writerContext, para, callback, 1);
                }
                // context.mMyDocument.unlockControllers();
            }

            // 모든 개체 로드가 끝난 후 Z-order 일괄 조정 수행
            writerContext.adjustZOrders();

            // 화면 갱신 resume
            // writerContext.mMyDocument.unlockControllers();
        } catch (HwpDetectException | HwpParseException e) {
            WriterContext.clearActiveContext();
            logFailure("Failed to convert document.", e);
            return false;
        } catch (RuntimeException e) {
            WriterContext.clearActiveContext();
            logFailure("Failed to convert document.", e);
            return false;
        }
        WriterContext.clearActiveContext();

        XCloseable xCloseable = (XCloseable) UnoRuntime.queryInterface(XCloseable.class, writerContext.mMyDocument);
        xCloseable.addCloseListener(this);

        return true;
    }

    /**
     * 문서를 변환하지 못했을 때 soffice 프로세스를 비정상 종료시킨다.
     * GUI 로 사용 중인 LibreOffice 가 통째로 종료되는 것을 막기 위해,
     * H2ORESTART_EXIT_ON_ERROR 환경변수가 설정된 경우에만 동작한다.
     * 헤드리스 일괄 변환(docker, CI 등)에서 변환 실패를 종료코드로 감지하기 위한 용도이다.
     */
    private void abortIfRequested() {
        String env = System.getenv(EXIT_ON_ERROR_ENV);
        if (env==null || !(env.equals("1") || env.equalsIgnoreCase("true"))) {
            log.info("Document is not converted. " + EXIT_ON_ERROR_ENV + " is not set, so soffice keeps running.");
            return;
        }

        log.severe("Document is not converted. Terminating soffice with exit code " + EXIT_CODE_CONVERSION_FAILED + ".");
        // halt()는 로그를 flush하지 않으므로 직접 flush한다.
        if (rootLogger!=null) {
            for (Handler handler: rootLogger.getHandlers()) {
                handler.flush();
            }
        }
        Runtime.getRuntime().halt(EXIT_CODE_CONVERSION_FAILED);
    }

    /**
     * 변환에 실패한 예외를 스택트레이스와 함께 로그 파일에 남긴다.
     * 원인 예외가 감싸여 있는 경우 최초 원인(root cause)도 함께 기록한다.
     */
    private static void logFailure(String what, Throwable t) {
        Throwable root = t;
        while (root.getCause()!=null && root.getCause()!=root) {
            root = root.getCause();
        }
        if (root!=t) {
            log.severe(what + " root cause: " + root.toString());
        }
        log.log(Level.SEVERE, what + " " + t.toString(), t);
    }

    /** 버그 신고에 필요한 실행 환경 정보를 로그 첫머리에 남긴다. */
    private void logEnvironment() {
        log.info("H2Orestart=" + getExtensionVersion()
                 + ", OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                 + " (" + System.getProperty("os.arch") + ")"
                 + ", Java=" + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"
                 + ", " + EXIT_ON_ERROR_ENV + "=" + System.getenv(EXIT_ON_ERROR_ENV));
    }

    /** 설치된 확장의 description.xml 에서 확장 버전을 읽는다. 읽지 못하면 unknown 을 돌려준다. */
    private String getExtensionVersion() {
        try {
            CodeSource codeSource = H2OrestartImpl.class.getProtectionDomain().getCodeSource();
            if (codeSource==null) {
                return "unknown";
            }
            Path jarPath = Paths.get(codeSource.getLocation().toURI());
            Path descPath = jarPath.getParent().resolve("description.xml");
            for (String line: Files.readAllLines(descPath, StandardCharsets.UTF_8)) {
                int tagPos = line.indexOf("<version");
                if (tagPos < 0) {
                    continue;
                }
                int valuePos = line.indexOf("value=", tagPos);
                if (valuePos < 0) {
                    continue;
                }
                int begin = line.indexOf('"', valuePos);
                int end = line.indexOf('"', begin+1);
                if (begin >= 0 && end > begin) {
                    return line.substring(begin+1, end);
                }
            }
        } catch (java.lang.Exception e) {
            // 버전을 읽지 못한다고 변환을 막을 수는 없다.
            log.fine("Cannot read extension version. " + e.toString());
        }
        return "unknown";
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
            log.log(Level.SEVERE, e.toString(), e);
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
                                log.log(Level.SEVERE, e.toString(), e);
                            }
                        });
            } catch (IOException e) {
                log.log(Level.SEVERE, e.toString(), e);
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
            log.log(Level.SEVERE, e.toString(), e);
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
