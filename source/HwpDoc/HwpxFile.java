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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
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
    private static final byte[] DISTRIBUTE_PASSWORD = {0x22,0x59,0x61,0x6E,0x67,0x20,0x57,0x61,0x6E,0x67,0x53,0x75,0x6E,0x76,0x21,0x21,0x22};

    public	String filename;
    public	OwpmlFile owplmFile;
    public	HwpFileHeader fileHeader;
    public	int	version;
    public	HwpDocInfo 	docInfo;
    public  HwpxManifest manifest;
    public	List<HwpSection> sections;

    // Let's have member that are needed for showing in LibreOffice
    public	List<DirectoryEntry> directoryBinData;
    public	List<HwpParagraph>  paraList;
    
    
    public HwpxFile(String filename) throws FileNotFoundException {
        this.filename = filename;
        owplmFile = new OwpmlFile(this.filename);
        fileHeader = new HwpFileHeader();
        docInfo = new HwpDocInfo(this);
        manifest = new HwpxManifest();
        sections = new ArrayList<HwpSection>();
    }

    public HwpxFile(File file) throws FileNotFoundException {
        owplmFile = new OwpmlFile(file);
        this.filename = file.toString();
        fileHeader = new HwpFileHeader();
        docInfo = new HwpDocInfo(this);
        manifest = new HwpxManifest();
        sections = new ArrayList<HwpSection>();
    }

    public OwpmlFile getOwpmlFile() {
        return owplmFile;
    }

    public List<HwpSection> getSections() {
        return sections;
    }

    public boolean detect() throws HwpDetectException, IOException, HwpParseException {
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

    public boolean getFileHeader() throws HwpDetectException, IOException, ParserConfigurationException, SAXException, DataFormatException, HwpParseException {
        return fileHeader.parse(getDocument("version.xml"));
    }

    public HwpDocInfo getDocInfo() {
        return docInfo;
    }

    public boolean getDocInfo(int version) throws IOException, DataFormatException, ParserConfigurationException, SAXException, HwpParseException, NotImplementedException {
        try {
            manifest.parse(getDocument("META-INF/manifest.xml"));
        } catch (DataFormatException e) {
            // META-INF/manifest.xml 없는 경우 있음 (smtech.go.kr 문서)
            log.warning("no META-INF/manifest.xml");
        }
        if (docInfo.readContentHpf(getDocument("Contents/content.hpf"), version)) {
            return docInfo.read(getDocument("Contents/header.xml"), version);
        } else {
            return false;
        }
    }

    public boolean readSection(String name, int version, IContext context) throws IOException, DataFormatException, 
                                                                ParserConfigurationException, SAXException, NotImplementedException, HwpParseException {
        Document document = getDocument(name);

        HwpSection hwpSection = new HwpSection(this);
        hwpSection.read(document, version, context);

        sections.add(hwpSection);
        return true;
    }
    
    
    public byte[] sha256(byte[] input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(input);
    }

    public byte[] pbkdf2HmacSha1(byte[] password, byte[] salt, int iterations, int keySize) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(password, "HmacSHA1"));
        byte[] result = new byte[keySize];
        int hLen = mac.getMacLength();
        int l = (keySize + hLen - 1) / hLen;
        for (int i = 1; i <= l; i++) {
            byte[] T = xorSum(mac, salt, iterations, i);
            int len = Math.min(hLen, keySize - (i - 1) * hLen);
            System.arraycopy(T, 0, result, (i - 1) * hLen, len);
        }
        return result;
    }

    private byte[] xorSum(Mac mac, byte[] salt, int iterations, int blockIndex) {
        byte[] U = new byte[salt.length + 4];
        System.arraycopy(salt, 0, U, 0, salt.length);
        U[salt.length] = (byte) (blockIndex >> 24);
        U[salt.length + 1] = (byte) (blockIndex >> 16);
        U[salt.length + 2] = (byte) (blockIndex >> 8);
        U[salt.length + 3] = (byte) blockIndex;

        U = mac.doFinal(U);
        byte[] T = U.clone();
        for (int i = 1; i < iterations; i++) {
            U = mac.doFinal(U);
            for (int j = 0; j < T.length; j++) {
                T[j] ^= U[j];
            }
        }
        return T;
    }

    public byte[] aesDecrypt(byte[] cipherText, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
        return cipher.doFinal(cipherText);
    }

    public byte[] deflateDecompress(byte[] data, int originalSize) throws Exception {
        Inflater inflater = new Inflater(true);
        inflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput()) break;
                }
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return Arrays.copyOf(data, originalSize);
        } finally {
            inflater.end();
        }
    }

    public boolean verifyChecksum(byte[] plaintext, byte[] expected) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha256.digest(Arrays.copyOf(plaintext, Math.min(1024, plaintext.length)));
        return Arrays.equals(digest, expected);
    }

    public byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    public InputStream decryptEntryStream(InputStream is, Map<String, String> entryManifestMap) throws HwpParseException, IOException {

        byte[] salt = Base64.getDecoder().decode(entryManifestMap.get("salt"));
        byte[] iv = Base64.getDecoder().decode(entryManifestMap.get("iv"));
        byte[] expectedChecksum = Base64.getDecoder().decode(entryManifestMap.get("checksum"));
        int iterations = Integer.parseInt(entryManifestMap.get("iterCount"));
        int keySize = Integer.parseInt(entryManifestMap.get("keySize"));
        int originalSize = Integer.parseInt(entryManifestMap.get("fileSize"));

        byte[] cipherText = toByteArray(is);

        try {
            byte[] startKey = sha256(DISTRIBUTE_PASSWORD);
            byte[] derivedKey = pbkdf2HmacSha1(startKey, salt, iterations, keySize);

            byte[] decrypted = aesDecrypt(cipherText, derivedKey, iv);
            byte[] plainText = deflateDecompress(decrypted, originalSize);

            if (verifyChecksum(plainText, expectedChecksum)) {
                return new ByteArrayInputStream(plainText);
            }
        } catch (Exception e) {
            log.severe("Decryption failed for entry");
            throw new HwpParseException(e.getMessage());
        }

        throw new HwpParseException("Decryption failed");
    }

    public byte[] decryptEntryBinary(byte[] cipherText, Map<String, String> entryManifestMap) throws HwpParseException, IOException {

        byte[] salt = Base64.getDecoder().decode(entryManifestMap.get("salt"));
        byte[] iv = Base64.getDecoder().decode(entryManifestMap.get("iv"));
        byte[] expectedChecksum = Base64.getDecoder().decode(entryManifestMap.get("checksum"));
        int iterations = Integer.parseInt(entryManifestMap.get("iterCount"));
        int keySize = Integer.parseInt(entryManifestMap.get("keySize"));
        int originalSize = Integer.parseInt(entryManifestMap.get("fileSize"));

        try {
            byte[] startKey = sha256(DISTRIBUTE_PASSWORD);
            byte[] derivedKey = pbkdf2HmacSha1(startKey, salt, iterations, keySize);

            byte[] decrypted = aesDecrypt(cipherText, derivedKey, iv);
            byte[] plainText = deflateDecompress(decrypted, originalSize);

            if (verifyChecksum(plainText, expectedChecksum)) {
                return plainText;
            }
        } catch (Exception e) {
            log.severe("Decryption failed for entry");
            throw new HwpParseException(e.getMessage());
        }

        throw new HwpParseException("Decryption failed");
    }

    public Document getDocument(String entryName) throws IOException, ParserConfigurationException, SAXException, DataFormatException, HwpParseException {

        InputStream is = owplmFile.getInputStream(entryName);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        if (fileHeader.bDistributable == true && manifest.entryMap !=null && manifest.entryMap.get(entryName)!=null) {
            Map<String, String> entryManifestMap = manifest.entryMap.get(entryName);
            InputStream decryptedIs = decryptEntryStream(is, entryManifestMap);
            return builder.parse(decryptedIs);
        } else {
            return builder.parse(is);
        }
    }

    public void close() throws IOException {
        owplmFile.close();
    }

    public String findBinData(String shortName) {
        return owplmFile.getBinData(shortName);
    }

    public byte[] getBinDataByIDRef(String shortName) throws IOException, DataFormatException, HwpParseException {
        String entry = owplmFile.getBinData(shortName);
        
        if (fileHeader.bDistributable == true && manifest.entryMap !=null && manifest.entryMap.get(entry)!=null) {
            Map<String, String> entryManifestMap = manifest.entryMap.get(entry);
            return decryptEntryBinary(owplmFile.getBytes(entry), entryManifestMap);
        } else {
            return owplmFile.getBytes(entry);
        }
    }

    public byte[] getBinDataByEntry(String entry) throws IOException, DataFormatException, HwpParseException {
        
        if (fileHeader.bDistributable == true && manifest.entryMap !=null && manifest.entryMap.get(entry)!=null) {
            Map<String, String> entryManifestMap = manifest.entryMap.get(entry);
            return decryptEntryBinary(owplmFile.getBytes(entry), entryManifestMap);
        } else {
            return owplmFile.getBytes(entry);
        }
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
