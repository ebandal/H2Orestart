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
 * 작성자 : 반희수 ebandal@gmail.com  
 * 작성일 : 2022.10
 */
package HwpDoc.OCFdoc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import HwpDoc.Exception.CompoundDetectException;
import HwpDoc.OLEdoc.DirectoryEntry;


public class OwpmlFile {

    private HashMap<String, Offset> offsetMap = new HashMap<>();
    private RandomAccessFile raf;
    private File file;

    public OwpmlFile(String filename) throws FileNotFoundException {
        this(new File(filename));
    }
    
    public OwpmlFile(File file) throws FileNotFoundException {
        this.file = file;
        raf = new RandomAccessFile(file, "r");
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
        long entrySize = (int)(offset.end - offset.start);
        
        byte[] buf = new byte[(int)entrySize];
        raf.seek(offset.start);
        int readLen = raf.read(buf, 0, (int)entrySize);
        
        if (offset.zipMethod == ZipEntry.DEFLATED) {
            buf = unzip(buf, readLen);
        }
        
        return new ByteArrayInputStream(buf);
    }
    
    public String findBinData(String shortName) {
        // TODO Auto-generated method stub
        return null;
    }

    public byte[] getBytes(String entryName) throws IOException {
        Offset offset = offsetMap.get(entryName);
        long entrySize = (int)(offset.end - offset.start);
        
        byte[] buf = new byte[(int)entrySize];
        raf.seek(offset.start);
        int readLen = raf.read(buf, 0, (int)entrySize);

        return buf;
    }
    
    public List<String> getSections() {
        List<String> sections = offsetMap.keySet().stream().filter(s -> s.contains("section")).sorted().collect(Collectors.toList());

        return sections;
    }
    
    public String getBinData(String shortName) {
        Optional<String> binData = offsetMap.keySet().stream().filter(s -> s.startsWith("BinData"))
                                                          .filter(s -> s.contains(shortName + ".")).findAny();
        return binData.orElse("");
    }
    
    public void inflate2() throws DataFormatException {
        
        try {
            // 압축해제할 폴더 생성
            File targetFolder = Paths.get("RAF").toFile();
            if (targetFolder.exists()==false) {
                Files.createDirectories(targetFolder.toPath());
            }
            
            Set<String> entries = offsetMap.keySet();
            for (String entryName: entries) {
    
                List<String> entryList = Arrays.asList(entryName.split("\\" + File.separator));
                
                Path curPath = Paths.get("RAF");
                for (String entry: entryList) {
                    curPath = Paths.get(curPath.toFile().getPath(), entry);
                }
                File subFolder = curPath.getParent().toFile();
                if (subFolder.exists()==false) {
                    Files.createDirectories(subFolder.toPath());
                }
                
                try (FileOutputStream fileOutputStream = new FileOutputStream(curPath.toFile().getAbsoluteFile())) {

                    Offset offset = offsetMap.get(entryName);
                    long entrySize = (int)(offset.end - offset.start);
                    long totalLen = 0;
                    
                    byte[] buf = new byte[(int)entrySize];
                    raf.seek(offset.start);
                    int readLen = raf.read(buf, 0, (int)entrySize);
                    
                    switch(offset.zipMethod) {
                    case ZipEntry.STORED:
                        break;
                    case ZipEntry.DEFLATED:
                        buf = unzip(buf, readLen);
                        readLen = buf.length;
                        break;
                    default:
                    }
                    fileOutputStream.write(buf, 0, readLen);
                    fileOutputStream.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        raf.close();
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
