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
package HwpDoc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.Exception.OwpmlParseException;
import HwpDoc.OCFdoc.OwpmlFile;
import HwpDoc.OLEdoc.DirectoryEntry;
import HwpDoc.paragraph.HwpParagraph;

public class HwpxFile {
    private static final Logger log = Logger.getLogger(HwpxFile.class.getName());
    
    public	String filename;
    public	OwpmlFile owplmFile;
    public	HwpFileHeader fileHeader;
    public	int	version;
    public	HwpDocInfo 	docInfo;
    public	List<HwpSection> sections;
    
    // Let's have member that are needed for showing in LibreOffice
    public	List<DirectoryEntry> directoryBinData;
    public	List<HwpParagraph>  paraList;
    
    
    public HwpxFile(String filename) throws FileNotFoundException {
        this.filename = filename;
        owplmFile = new OwpmlFile(this.filename);
        fileHeader = new HwpFileHeader();
        docInfo = new HwpDocInfo(this);
        sections = new ArrayList<HwpSection>();
    }

    public HwpxFile(File file) throws FileNotFoundException {
        owplmFile = new OwpmlFile(file);
        this.filename = file.toString();
        fileHeader = new HwpFileHeader();
        docInfo = new HwpDocInfo(this);
        sections = new ArrayList<HwpSection>();
    }

    public OwpmlFile getOwpmlFile() {
        return owplmFile;
    }
    
    public List<HwpSection> getSections() {
        return sections;
    }
    
    public boolean detect() throws HwpDetectException, IOException {
        // read CompoundFile structure
        try {
            owplmFile.open();
            if (getFileHeader() == false) {
                owplmFile.close();
                // throw new CompoundParseException();
            }
        } catch (ParserConfigurationException | SAXException | DataFormatException e) {
            owplmFile.close();
            throw new HwpDetectException(ErrCode.INVALID_ZIP_DATA_FORMAT);
        } catch (HwpDetectException e) {
            owplmFile.close();
            throw new HwpDetectException(e.getReason());
        }
        log.fine("Header parsed");
        return true;
    }
    
    public void open(IContext context) throws HwpDetectException, IOException, DataFormatException,  
                                ParserConfigurationException, SAXException, OwpmlParseException, 
                                HwpParseException, NotImplementedException {
        if (fileHeader.version==null) {
            detect();
        }
        version = Integer.parseInt(fileHeader.version);
        
        if (getDocInfo(version)==false) 
            throw new OwpmlParseException();
        log.fine("DocInfo parsed");
        
        // Contents/SectionX.xml 을 읽는다.
        for (String section: owplmFile.getSections()) {
            readSection(section, version, context);
        }
    }
    
    public boolean getFileHeader() throws HwpDetectException, IOException, ParserConfigurationException, SAXException, DataFormatException {
        return fileHeader.parse(getDocument("version.xml"));
    }
    
    public HwpDocInfo getDocInfo() {
        return docInfo;
    }
    
    public boolean getDocInfo(int version) throws IOException, DataFormatException, ParserConfigurationException, SAXException, HwpParseException, NotImplementedException {
        if (docInfo.readContentHpf(getDocument("Contents/content.hpf"), version)) {
            return docInfo.read(getDocument("Contents/header.xml"), version);
        } else {
            return false;
        }
    }
    
    public boolean readSection(String name, int version, IContext context) throws IOException, DataFormatException, 
                                                                ParserConfigurationException, SAXException, NotImplementedException {
        
        Document document = getDocument(name);
        
        HwpSection hwpSection = new HwpSection(this);
        hwpSection.read(document, version, context);
            
        sections.add(hwpSection);
        return true;
    }
    
    public Document getDocument(String entryName) throws IOException, ParserConfigurationException, SAXException, DataFormatException {
        
        InputStream is = owplmFile.getInputStream(entryName);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(is);
    }
    
    public void close() throws IOException {
        owplmFile.close();
    }
    
    public String findBinData(String shortName) {
        return owplmFile.getBinData(shortName);
    }
    
    public byte[] getBinDataByIDRef(String shortName) throws IOException, DataFormatException {
        String entry = owplmFile.getBinData(shortName);
        return owplmFile.getBytes(entry);
    }
    
    public byte[] getBinDataByEntry(String entry) throws IOException, DataFormatException {
        return owplmFile.getBytes(entry);
    }
    
    public List<HwpParagraph> getParaList() {
        return paraList;
    }
    
    public void addParaList(HwpParagraph para) {
        if (this.paraList == null) 
            this.paraList = new ArrayList<HwpParagraph>();
        this.paraList.add(para);
    }
    
    public static class Rand {
        static int random_seed;
        
        public static void srand(int seed) {
            random_seed = seed;
        }
        
        public static int rand() {
            random_seed = (random_seed * 214013 + 2531011) & 0xFFFFFFFF;
            return ((random_seed >> 16) & 0x7FFF);
        }
    }
} 
