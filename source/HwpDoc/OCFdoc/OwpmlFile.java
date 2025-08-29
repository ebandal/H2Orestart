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
package HwpDoc.OCFdoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;



public class OwpmlFile {

    private HashMap<String, Offset> offsetMap = new HashMap<>();
    private File file;

    public OwpmlFile(String filename) throws FileNotFoundException {
        this(new File(filename));
    }
    
    public OwpmlFile(File file) throws FileNotFoundException {
        this.file = file;
    }
    
    public void open() {
        
        try (FileInputStream fileInputStream = new FileInputStream(this.file);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {
            
            ZipEntry zipEntry = null;
            long entryOffset = 0;
            
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                entryOffset += 30 + zipEntry.getName().length() + (zipEntry.getExtra()==null ? 0 : zipEntry.getExtra().length);

                long offsetStart = entryOffset;
                entryOffset += zipEntry.getCompressedSize();
                long offsetEnd = entryOffset;
                int zipMethod = zipEntry.getMethod();
                
                offsetMap.put(zipEntry.getName(), new Offset(offsetStart, offsetEnd, zipMethod));
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public InputStream getInputStream(String entryName) throws IOException, DataFormatException {
        Offset offset = offsetMap.get(entryName);
        if (offset == null) {
            throw new DataFormatException();
        }
        long entrySize = (int)(offset.end - offset.start);

        byte[] buf = new byte[(int)entrySize];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset.start);
            int readLen = raf.read(buf, 0, (int)entrySize);
            
            if (offset.zipMethod == ZipEntry.DEFLATED) {
                buf = unzip(buf, readLen);
            }
        }
        return new ByteArrayInputStream(buf);
    }
    
    public String findBinData(String shortName) {
        return null;
    }

    public byte[] getBytes(String entryName) throws IOException, DataFormatException {
        Offset offset = offsetMap.get(entryName);
        long entrySize = (int)(offset.end - offset.start);
        
        byte[] buf = new byte[(int)entrySize];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset.start);
            int readLen = raf.read(buf, 0, (int)entrySize);
    
            if (offset.zipMethod == ZipEntry.DEFLATED) {
                buf = unzip(buf, readLen);
            }
        }

        return buf;
    }
    
    public List<String> getSections() {
        List<String> sections = offsetMap.keySet().stream().filter(s -> s.contains("section"))
                                         .sorted((s1, s2) -> {
                                                int lengthCompare = Integer.compare(s1.length(), s2.length());
                                                if (lengthCompare != 0) {
                                                    return lengthCompare;
                                                }
                                                return s1.compareTo(s2);
                                            })
                                         .collect(Collectors.toList());
        return sections;
    }
    
    public String getBinData(String shortName) {
        Optional<String> binData = offsetMap.keySet().stream().filter(s -> s.startsWith("BinData"))
                                                          .filter(s -> s.contains(shortName + ".")).findAny();
        return binData.orElse("");
    }
    
    private byte[] unzip(byte[] input, int inLen) throws IOException, DataFormatException {
        Inflater decompressor = new Inflater(true);
        decompressor.setInput(input, 0, input.length);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(inLen);

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
    
    public void close() throws IOException {
    }
    
    public static class Offset {
        long start;
        long end;
        int zipMethod;
        
        public Offset(long start, long end, int zipMethod) {
            this.start = start;
            this.end = end;
            this.zipMethod = zipMethod;
        }
    }

}
