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
package HwpDoc;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.Exception.OwpmlParseException;
import HwpDoc.HwpElement.HwpTag;
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

	public OwpmlFile getOwpmlFile() {
		return owplmFile;
	}

	public List<HwpSection> getSections() {
	    return sections;
	}
	
	public boolean detect() throws HwpDetectException, IOException, ParserConfigurationException, SAXException, DataFormatException {
		// read CompoundFile structure
	    owplmFile.open();
		try {
			if (getFileHeader() == false) {
			    owplmFile.close();
			    // throw new CompoundParseException();
			}
		} catch (HwpDetectException e) {
		    owplmFile.close();
			throw new HwpDetectException(e.getReason());
		}
		log.fine("Header parsed");
		return true;
	}

	public void open() throws HwpDetectException, IOException, DataFormatException,  
	                            ParserConfigurationException, SAXException, OwpmlParseException, HwpParseException, NotImplementedException {
		if (fileHeader.version==null) {
			detect();
		}
		version = Integer.parseInt(fileHeader.version);
		
		if (getDocInfo(version)==false) 
			throw new OwpmlParseException();
		log.fine("DocInfo parsed");

		// Contents/SectionX.xml 을 읽는다.
		for (String section: owplmFile.getSections()) {
		    readSection(section, version);
		}
	}
	
	private byte[] unzip(byte[] input) throws IOException, DataFormatException {
        Inflater decompressor = new Inflater(true);
        decompressor.setInput(input, 0, input.length);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);

        // Decompress the data
        byte[] buf = new byte[8096];
        while (!decompressor.finished()) {
            int count = decompressor.inflate(buf);
            if (count > 0) {
                bos.write(buf, 0, count);
            } else {
                throw new IOException("can't decompress data");
            }
        }
        bos.close();
        return bos.toByteArray();
	}
	
	private byte[] decrypt(byte[] buf) throws HwpParseException {
		int offset 	= 0;
		int header 	= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		int tagNum 	= header&0x3FF;				// 10 bits (0 - 9 bit)
		int level 	= (header&0xFFC00)>>>10;	// 10 bits (10-19 bit)
		int size 	= (header&0xFFF00000)>>>20;	// 12 bits (20-31 bit)
		offset += 4;
		
		HwpTag tag = HwpTag.from(tagNum);
		if (tag!=HwpTag.HWPTAG_DISTRIBUTE_DOC_DATA) {
			throw new HwpParseException();
		}
		if (size != 256) {
			throw new HwpParseException();
		}

		byte[] docData = new byte[256];
		System.arraycopy(buf, offset, docData, 0, 256);
		offset += 256;

		int seed	= docData[3]<<24&0xFF000000 | docData[2]<<16&0xFF0000 | docData[1]<<8&0xFF00 | docData[0]&0xFF;
		int hashoffset = (seed & 0x0f)+4;

		// 한글문서파일형식_배포용문서_revision1.2.hwp
		// MS Visual C의 랜덤함수 srand(), rand()를 사용
		// 1. srand() 초기화 (Seed 사용)
		// 2. rand()함수의 결과 값을 이용하여 배열을 채운다. 단, rand()함수가 호출되는 순번에 따라 그 사용 방식이 달라진다.홀수번째 : 배열에 채워지는 값짝수번째 : 배열에 채워지는 횟수
		// 3. 홀수번째 rand() & 0xFF의 값을 A라 하고, 짝수번째 (rand() & 0x0F + 1)의 결과를 B라 할 때배열에 A값을 B번 횟수만큼 삽입한다.예를 들어 A가 ‘a’이고, B가 3일 경우에 배열에 ‘a’를 3번 삽입한다.
		// 4. 배열크기가 256이 될 때까지 3항을 반복한다.
		Rand.srand(seed);
		for (int i=0; i < 256; ) {
			byte a = (byte) (Rand.rand() & 0x000000FF);
			int cnt = (Rand.rand() & 0x0000000F) + 1;
			for (int j=0; j<cnt && i<256; j++,i++) {
				docData[i] ^= a;
			}
		}

		// byte[] hash = new byte[80];
		// System.arraycopy(docData, hashoffset, hash, 0, 80);
		// 2 bytes - 복사방지설정, 인쇄방지설정  

		byte[] output = null;
		try {
			// byte[] aesKey = new byte[16];
			// System.arraycopy(docData, hashoffset, aesKey, 0, 16);
			// SecretKeySpec skey = new SecretKeySpec(aesKey, "AES");
			SecretKeySpec skey = new SecretKeySpec(docData, hashoffset, 16, "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, skey);
			output = cipher.doFinal(buf, offset, buf.length-offset);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
		}
		
		return output;
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
	
    public boolean readSection(String name, int version) throws IOException, DataFormatException, ParserConfigurationException, SAXException, NotImplementedException {

        Document document = getDocument(name);
        
        HwpSection hwpSection = new HwpSection(this);
        hwpSection.read(document, version);
            
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
