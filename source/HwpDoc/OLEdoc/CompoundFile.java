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
package HwpDoc.OLEdoc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import HwpDoc.ErrCode;
import HwpDoc.Exception.CompoundDetectException;


public class CompoundFile {
	private static final Logger log = Logger.getLogger(CompoundFile.class.getName());

	private RandomAccessFile raf;
	private int minorVersion;
	private int majorVersion;
	private int sectorSize = 512;
	private int shortSectorSize;
	private int num_Directory;			// Support only in version 4
	private int num_SAT;
	private int first_SecID_Directory;
	private int miniStreamCutoffSize;
	private int first_SecID_SSAT;
	private int num_SSAT;
	private int first_SecID_MSAT;
	private int num_MSAT;
	private ArrayList<Sector> sectorList;
	private ArrayList<Integer> SAT_list;	// Master SAT
	private ArrayList<Integer> SSAT_SecID_list;
	private ArrayList<Integer> Directory_SecID_list;
	private ArrayList<Integer> SStream_SecID_list;
	private ArrayList<Integer> SStream_list;
	private ArrayList<DirectoryEntry> DirectoryEntry_list;
	
	
	final static byte[] COMPOUND_SIGANTURE = { (byte)0xD0, (byte)0xCF, (byte)0x11, (byte)0xE0, (byte)0xA1, (byte)0xB1, (byte)0x1A, (byte)0xE1 };
	
	public CompoundFile(String filename) throws FileNotFoundException {
		this(new File(filename));
	}
	
	public CompoundFile(File file) throws FileNotFoundException {
		raf = new RandomAccessFile(file, "r");
		sectorList = new ArrayList<Sector>();
		SAT_list = new ArrayList<Integer>();
		SSAT_SecID_list = new ArrayList<Integer>();
		Directory_SecID_list = new ArrayList<Integer>();
		SStream_SecID_list = new ArrayList<Integer>();
		SStream_list = new ArrayList<Integer>();
		DirectoryEntry_list = new ArrayList<DirectoryEntry>();
	}
	
	private void addSiblings(List<Integer> indexList, int currentIndex) {
		if (currentIndex==-1) 
			return;
		
		if (!indexList.contains(currentIndex)) {
			indexList.add(currentIndex);
		}
		int leftSibling = DirectoryEntry_list.size()>currentIndex?DirectoryEntry_list.get(currentIndex).leftSiblingID:-1;
		int rightSibling = DirectoryEntry_list.size()>currentIndex?DirectoryEntry_list.get(currentIndex).rightSiblingID:-1;
		
		if (rightSibling!=-1) {
			int elderIndex = indexList.indexOf(currentIndex);
			indexList.add(elderIndex+1, rightSibling);
			if (rightSibling < DirectoryEntry_list.size()) {
				addSiblings(indexList, rightSibling);
			}
		}
		if (leftSibling!=-1) {
			int elderIndex = indexList.indexOf(currentIndex);
			indexList.add(elderIndex, leftSibling);
			if (leftSibling < DirectoryEntry_list.size()) {
				addSiblings(indexList, leftSibling);
			}
		}
	}
	
	public DirectoryEntry getEntry(String entryName) {
		Optional<DirectoryEntry> op = DirectoryEntry_list.stream()
														.filter(e -> e.directoryEntryName.trim().equals(entryName))
														.findFirst();
		return op.isPresent()?op.get():null;
	}

	public List<DirectoryEntry> getChildEntries(DirectoryEntry baseEntry) {
		List<Integer> entryIdx = new LinkedList<Integer>();
		int index = 0;
		if (baseEntry == null) {
			index = DirectoryEntry_list.get(0).childID;
		} else {
			index = baseEntry.childID;
		}
		addSiblings(entryIdx, index);
		List<DirectoryEntry> entries =  entryIdx.stream()
												.filter(i -> (i<DirectoryEntry_list.size()))
												.map(i -> DirectoryEntry_list.get(i))
												.collect(Collectors.toList());
		return entries;
	}

	public List<DirectoryEntry> getChildEntries(String baseEntryName) {
		Optional<DirectoryEntry> op = DirectoryEntry_list.stream()
														.filter(e -> e.directoryEntryName.trim().equals(baseEntryName))
														.findFirst();
		if (op.isPresent()) {
			return getChildEntries(op.get());
		} else {
			return new ArrayList<DirectoryEntry>();
		}
	}
	
	public byte[] getComponent(String entryName) throws CompoundDetectException {
	    DirectoryEntry entry = getEntry(entryName);
	    if (entry!=null) {
	        return read(entry);
	    } else {
	        throw new CompoundDetectException();
	    }
	}
	
	public byte[] read(DirectoryEntry entry) {
		byte[] buf = new byte[(int)entry.streamSize];
		int buff_offset = 0;

		int len = 0;
		List<Integer> streamContainerSectors = null;
		if (entry.streamSize<miniStreamCutoffSize) { // short Stream
			byte[] b = new byte[64];
			int remainSize = (int)entry.streamSize;
			streamContainerSectors = DirectoryEntry_list.get(0).secNums;
			for (int secNum: entry.secNums) {
				// readStream
				int stream_Index = secNum/(sectorSize/64);
				int stream_offset = secNum % (sectorSize/64);
				int satID = streamContainerSectors.get(stream_Index);
				try {
					raf.seek((satID+1)*sectorSize + stream_offset*64);
					int readLen = raf.read(b, 0, remainSize>=64?64:remainSize);
					if (readLen<0) continue;
					remainSize -= readLen;
					
					// writeToBuffer
					System.arraycopy(b, 0, buf, buff_offset, readLen);
					buff_offset += readLen;
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		} else {									 // normal Stream
			byte[] b = new byte[sectorSize];
			int remainSize = (int)entry.streamSize;
			streamContainerSectors = entry.secNums;
			for (int secNum: entry.secNums) {
				if (secNum==0xFFFFFFFE) continue;
				// readStream
				try {
					raf.seek((secNum+1)*sectorSize);
					int readLen = raf.read(b, 0, remainSize>=sectorSize?sectorSize:remainSize);
					remainSize -= readLen;
					
					// writeToBuf
					System.arraycopy(b, 0, buf, buff_offset, readLen);
					buff_offset += readLen;
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		
		return buf;
	}

	
	public void open() throws CompoundDetectException, IOException {
		
		byte[] buf = new byte[sectorSize];	// from Signature to Number of DIFAT sectors
		if (raf.read(buf, 0, sectorSize) != sectorSize) {
			throw new CompoundDetectException(ErrCode.FILE_READ_ERROR);
		}
		parse_Header(buf);
		if (majorVersion == 0x0004) {
			raf.seek(4096);
			sectorSize = 4096;
			buf = new byte[sectorSize];
		}
		
		// collect MSAT SecID 
		int secID = first_SecID_MSAT;
		if (secID != 0xFFFFFFFE && secID != 0xFFFFFFFF) {  // [20211103] 0xFFFFFFFF 조건 추가. (국방CBD방법론v1(1권) 읽지 못하는 이슈 수정)
			read_MSAT_sector(secID);	// MSAT sector에서  SSAT SecID들을 구한다.
		}
		
		if (log.isLoggable(Level.FINEST)) {
			log.finest("[______SAT Sector]={"+ SAT_list.stream().map(i -> i.toString()).collect(Collectors.joining(",")) + "}");
		}
		
		// Directory
		secID = first_SecID_Directory;
		Directory_SecID_list.add(secID);
		while(secID != 0xFFFFFFFE) {
			int satIndex = secID/(sectorSize/4);

			// collect Directory SecID
			int satID = SAT_list.get(secID/(sectorSize/4));
			List<Integer> secID_list = get_SecIDs_from_SAT(satID, satIndex, secID);
			secID_list.stream().filter(id -> !Directory_SecID_list.contains(id)).forEach(id -> Directory_SecID_list.add(id));
			secID = secID_list.get(secID_list.size()-1);
		}
			
		// collect Directory Entries
		for (int secID_Directory: Directory_SecID_list) {
			if (secID_Directory != 0xFFFFFFFE) {
				read_Directory_sector(secID_Directory);
			}
		}
		if (log.isLoggable(Level.FINEST)) {
			log.finest("[Directory Sector]={" + Directory_SecID_list.stream().map(i -> i.toString()).collect(Collectors.joining(",")) + "}");
		}
		
		// collect SSAT SecID 
		int lastSecID = first_SecID_SSAT;
		SSAT_SecID_list.add(lastSecID);
		while(lastSecID != 0xFFFFFFFE) {
			int satIndex = lastSecID/(sectorSize/4);
			int satID = SAT_list.get(satIndex);
			List<Integer> secID_list = get_SecIDs_from_SAT(satID, satIndex, lastSecID);	// SAT를 읽어서 SSAT SecID 들을 구한다.
			secID_list.stream().filter(id -> !SSAT_SecID_list.contains(id)).forEach(id -> SSAT_SecID_list.add(id));
			lastSecID = secID_list.get(secID_list.size()-1);
		}
		if (log.isLoggable(Level.FINEST)) {
			log.finest("[Short SAT Sector]={" +SSAT_SecID_list.stream().map(i->i.toString()).collect(Collectors.joining(",")) + "}");
		}
		
		int SecID_SStream = 0;
		
		// collect SecID of Stream for each Directory entries 
		for (DirectoryEntry entry: DirectoryEntry_list) {
			if (entry.objectType == 0x05) { // Root Storage
				// Short Stream container Stream SecID 
				lastSecID = entry.startingSectorID;
				entry.secNums = new ArrayList<Integer>();
				entry.secNums.add(lastSecID);
				while(lastSecID != 0xFFFFFFFE) {
					int containerIndex = lastSecID/(sectorSize/4);
					int satID = SAT_list.get(containerIndex);
					List<Integer> secID_list = get_SecIDs_from_SAT(satID, containerIndex, lastSecID);	// SAT를 읽어서 stream container SecID 들을 구한다.
					secID_list.stream().filter(id -> !entry.secNums.contains(id)).forEach(id -> entry.secNums.add(id));
					lastSecID = secID_list.get(secID_list.size()-1);
				}
				
			} else if (entry.objectType == 0x02) {	// Stream
				entry.secNums = new ArrayList<Integer>();
				entry.secNums.add(entry.startingSectorID);
				if (entry.streamSize<miniStreamCutoffSize) {
					// ShortStream
					int lastSSecID = entry.startingSectorID;
					while(lastSSecID != 0xFFFFFFFE) {
						int ssatIndex = lastSSecID/(sectorSize/4);
						int ssatID = SSAT_SecID_list.get(ssatIndex);
						List<Integer> secID_list = get_SecIDs_from_SAT(ssatID, ssatIndex, lastSSecID);
						secID_list.stream().filter(id -> !entry.secNums.contains(id)).forEach(id -> entry.secNums.add(id));
						lastSSecID = entry.secNums.get(entry.secNums.size()-1);
					}
				} else {
					// Stream
					int last_SecID = entry.startingSectorID;
					while(last_SecID != 0xFFFFFFFE) {
						int satIndex = last_SecID/(sectorSize/4);
						int satID = SAT_list.get(satIndex);
						List<Integer> secID_list = get_SecIDs_from_SAT(satID, satIndex, last_SecID);
						secID_list.stream().filter(id -> !entry.secNums.contains(id)).forEach(id -> entry.secNums.add(id));
						last_SecID = entry.secNums.get(entry.secNums.size()-1);
					}
				}
			} else {
				continue;
			}
		}

		if (log.isLoggable(Level.FINEST)) {
			log.finest("_I __________Name_______ ___Type LS RS Chd Sec Size__  Chain__________");
			for (int idx=0; idx<DirectoryEntry_list.size();idx++) {
				DirectoryEntry e = DirectoryEntry_list.get(idx);
				log.finest(String.format("%2d %21s %7s %2d %2d %3d %3d %6d %s{%s}",
												idx, 
												e.directoryEntryName.trim(),
												(e.objectType==0x5?"root":e.objectType==0x2?"stream":e.objectType==0x1?"storage":"none"),
												e.leftSiblingID,
												e.rightSiblingID,
												e.childID,
												e.startingSectorID,
												e.streamSize,
												e.streamSize<miniStreamCutoffSize?"s":"N",
												e.secNums==null?"null":e.secNums.stream().map(i -> i.toString()).collect(Collectors.joining(",")))
									);
			}
		}
		
		log.fine("open() closing");
	}


	private List<Integer> get_SecIDs_from_SAT(int secID, int satIndex, int secID_SSAT) throws IOException {
		byte[] buf = new byte[sectorSize];
		raf.seek((secID+1) * sectorSize);
		raf.read(buf, 0, sectorSize);
		
		int currSecID = secID_SSAT;
		int iBuf = currSecID%(sectorSize/4) * 4; 
		List<Integer> secIdList = new ArrayList<Integer>();
		
		while((sectorSize/4)*(satIndex) <= currSecID && currSecID < (sectorSize/4)*(satIndex+1)) {
			currSecID = buf[iBuf+3]<<24&0xFF000000 | buf[iBuf+2]<<16&0xFF0000 | buf[iBuf+1]<<8&0xFF00 | buf[iBuf]&0xFF;
			if (currSecID == 0xFFFFFFFE) {
				secIdList.add(currSecID);
				break;
			}
			secIdList.add(currSecID);
			iBuf = currSecID%(sectorSize/4) * 4;
		}
		return secIdList;
	}

	
	public void parseSectors(int secID, byte[] buf) throws CompoundDetectException, IOException {
		int sectorType = buf[3]<<24&0xFF000000 | buf[2]<<16&0xFF0000 | buf[1]<<8&0xFF00 | buf[0]&0xFF;	// for DIFAT, FAT, MiniFAT,

		if (SAT_list.contains(secID)) {
			parse_SAT_sector(secID, buf);
		}
		
		if (secID < first_SecID_Directory) {
			if (secID == first_SecID_SSAT) {
				parse_SSAT_sector(buf);
			} else {
			}
		}
	}

	private void parse_SAT_sector(int secID, byte[] buf) {

		Map<String, List<Integer>> multiMap = new HashMap<String, List<Integer>>();
		
		
		for(int i=secID*(sectorSize/4), iBuf=0; iBuf < sectorSize-4; i++,iBuf+=4) {
			int nextSecID = buf[iBuf+3]<<24&0xFF000000 | buf[iBuf+2]<<16&0xFF0000 | buf[iBuf+1]<<8&0xFF00 | buf[iBuf]&0xFF;
			
			Sector sectorInChain = new Sector();
			sectorInChain.sectorNum = i;
			
			switch(nextSecID) {
			case 0xFFFFFFFC:	// DIFAT
				sectorInChain.type = SectorType.MSAT;
				break;
			case 0xFFFFFFFD:	// FAT
				sectorInChain.type = SectorType.SAT;
				break;
			case 0xFFFFFFFA:	// maximum regular sector number
			case 0xFFFFFFFE:	// ENDOFCHAIN
				sectorInChain.type = SectorType.ENDOFCHAIN;
				// Directory
				// FAT
				// MiniFAT
				// DIFAT
				// Stream (User-Defined Data)
				// Range Lock
				break;
			case 0xFFFFFFFF:	// unallocated
				sectorInChain.type = SectorType.FREE;
				break;
			case 0xFFFFFFFB:	// Reserved for future use.
				sectorInChain.type = SectorType.CONTINUE;
				break;
			default:
				sectorInChain.type = SectorType.CONTINUE;
				sectorInChain.nextNum = nextSecID;
			}
			if (nextSecID < 0xFFFFFFFF)
				sectorList.add(sectorInChain);
		}
	}
	
	private SectorType lookupSectorType(byte[] buf) {
		for(int i=0, index=0; index < sectorSize-4; i++,index+=4) {
			int sector = buf[index+3]<<24&0xFF000000 | buf[index+2]<<16&0xFF0000 | buf[index+1]<<8&0xFF00 | buf[index]&0xFF;
			if (sector == 0xFFFFFFFC)
				return SectorType.MSAT;
			else if (sector == 0xFFFFFFFD)
				return SectorType.SAT;
		}
		return SectorType.CONTINUE;
	}
	
	private void read_Directory_sector(int secID) throws IOException {
		byte[] buf = new byte[sectorSize];
		raf.seek((secID+1) * sectorSize);
		if (raf.read(buf, 0, sectorSize) == sectorSize) {
			parse_Directory_sector(buf);
		}
	}

	private void parse_Directory_sector(byte[] buf) throws UnsupportedEncodingException {
		for(int i=0, index=0; index <= sectorSize-128; i++,index+=128) {
			int entryNameLen 				= buf[index+65]<<8&0xFF00 | buf[index+64]&0xFF;
			String directoryEntryName = new String(buf, index, entryNameLen, StandardCharsets.UTF_16LE);

			int objectType 			= buf[index+66]&0xFF;
			int colorFlag 			= buf[index+67]&0xFF;
			int leftSiblingID		= buf[index+71]<<24&0xFF000000 | buf[index+70]<<16&0xFF0000 | buf[index+69]<<8&0xFF00 | buf[index+68]&0xFF;
			int rightSiblingID		= buf[index+75]<<24&0xFF000000 | buf[index+74]<<16&0xFF0000 | buf[index+73]<<8&0xFF00 | buf[index+72]&0xFF;
			int childID				= buf[index+79]<<24&0xFF000000 | buf[index+78]<<16&0xFF0000 | buf[index+77]<<8&0xFF00 | buf[index+76]&0xFF;

			long clsID1				= buf[index+87]<<24&0xFF000000 | buf[index+86]<<16&0xFF0000 | buf[index+85]<<8&0xFF00 | buf[index+84]&0xFF;
			clsID1  		   		= clsID1<<32 | buf[index+83]<<24&0xFF000000 | buf[index+82]<<16&0xFF0000 | buf[index+81]<<8&0xFF00 | buf[index+80]&0xFF;
			long clsID2  			= buf[index+95]<<24&0xFF000000 | buf[index+94]<<16&0xFF0000 | buf[index+93]<<8&0xFF00 | buf[index+92]&0xFF;
			clsID2  		   		= clsID2<<32 | buf[index+91]<<24&0xFF000000 | buf[index+90]<<16&0xFF0000 | buf[index+89]<<8&0xFF00 | buf[index+88]&0xFF;
			
			int stateBit			= buf[index+99]<<24&0xFF000000 | buf[index+98]<<16&0xFF0000 | buf[index+97]<<8&0xFF00 | buf[index+96]&0xFF;
			long creationTime		= buf[index+107]<<24&0xFF000000 | buf[index+106]<<16&0xFF0000 | buf[index+105]<<8&0xFF00 | buf[index+104]&0xFF;
			creationTime 			= creationTime<<32 | buf[index+103]<<24&0xFF000000 | buf[index+102]<<16&0xFF0000 | buf[index+101]<<8&0xFF00 | buf[index+100]&0xFF;
			
			long modifiedTime		= buf[index+115]<<24&0xFF000000 | buf[index+114]<<16&0xFF0000 | buf[index+113]<<8&0xFF00 | buf[index+112]&0xFF;
			modifiedTime 			= modifiedTime<<32 | buf[index+111]<<24&0xFF000000 | buf[index+110]<<16&0xFF0000 | buf[index+109]<<8&0xFF00 | buf[index+108]&0xFF;
			int startingSectorID	= buf[index+119]<<24&0xFF000000 | buf[index+118]<<16&0xFF0000 | buf[index+117]<<8&0xFF00 | buf[index+116]&0xFF;
			long streamSize			= buf[index+127]<<24&0xFF000000 | buf[index+126]<<16&0xFF0000 | buf[index+125]<<8&0xFF00 | buf[index+124]&0xFF;
			streamSize 	   			= streamSize<<32 | buf[index+123]<<24&0xFF000000 | buf[index+122]<<16&0xFF0000 | buf[index+121]<<8&0xFF00 | buf[index+120]&0xFF;

			DirectoryEntry de = new DirectoryEntry(directoryEntryName, objectType, colorFlag, leftSiblingID, rightSiblingID, childID, clsID1, clsID2, 
											stateBit, creationTime, modifiedTime, startingSectorID, streamSize);
			DirectoryEntry_list.add(de);
		}
	}

	private void read_SSAT_sector(int secID) throws IOException {
		byte[] buf = new byte[sectorSize];
		raf.seek((secID+1) * sectorSize);
		if (raf.read(buf, 0, sectorSize) == sectorSize) {
			parse_SSAT_sector(buf);
		}
	}
	
	private void parse_SSAT_sector(byte[] buf) {
		int offset = 0;
		while(offset<sectorSize-4) {
			int nextSectorID = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
			offset += 4;
			if (nextSectorID != 0xFFFFFFFF)
				SStream_list.add(nextSectorID);
		}
	}
	
	private void read_MSAT_sector(int secID) throws IOException {
		byte[] buf = new byte[sectorSize];
		raf.seek((secID+1) * sectorSize);
		if (raf.read(buf, 0, sectorSize) == sectorSize) {
			parse_MSAT_sector(buf);
		}
	}

	private void parse_MSAT_sector(byte[] buf) throws IOException {
		int offset = 0;
		while(offset<sectorSize-4) {
			int sector = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
			offset += 4;
			if (sector != 0xFFFFFFFF)
				SAT_list.add(sector);
		}
		
		// 다음번 MasterSector ID 읽기
		int nextSecID_MSAT = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		if (nextSecID_MSAT != 0xFFFFFFFE && nextSecID_MSAT != 0xFFFFFFFF) {  // [20211103] 0xFFFFFFFF 조건 추가. (국방CBD방법론v1(1권) 읽지 못하는 이슈 수정)
			read_MSAT_sector(nextSecID_MSAT);
		}
	}

	
	public void parse_Header(byte[] buf) throws CompoundDetectException, IOException {
		int offset = 0;
		byte[] bufSig = new byte[8];
		System.arraycopy(buf, 0, bufSig, 0, 8);
		if (Arrays.equals(bufSig, COMPOUND_SIGANTURE) == false) {
			throw new CompoundDetectException(ErrCode.SIGANTURE_NOT_MATCH);
		}
		offset += 8;  // Header signature
		offset += 16; // Header CLSID
		minorVersion 					= buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 2;
		if (minorVersion != 0x003E) {
			throw new CompoundDetectException(ErrCode.INVALID_MINORVERSION);
		}
		majorVersion 					= buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 2;
		if (majorVersion != 0x0003 && majorVersion != 0x0004) {
			throw new CompoundDetectException(ErrCode.INVALID_MAJORVERSION);
		}
		int byteOrder 					= buf[offset+1]<<8&0xFF00 | buf[offset]&0x00FF;
		offset += 2;
		if (byteOrder != 0xFFFE) {
			throw new CompoundDetectException(ErrCode.INVALID_BYTEORDER);
		}
		int sectorShift 				= buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 2;
		sectorSize = (int)Math.pow(2.0, (double)sectorShift);
		if (majorVersion == 0x0003 && sectorShift != 0x0009) {
			throw new CompoundDetectException(ErrCode.INVALID_SECTORSHIFT);
		} else if (majorVersion == 0x0004 && sectorShift != 0x000C) {
			throw new CompoundDetectException(ErrCode.INVALID_SECTORSHIFT);
		}
		sectorShift 			= buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 2;
		shortSectorSize = (int)Math.pow(2.0, (double)sectorShift);
		if (sectorShift != 0x0006) {
			throw new CompoundDetectException(ErrCode.INVALID_MINISECTORSHIFT);
		}
		offset += 6; // reserved
		num_Directory		 			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		if (majorVersion == 0x0003 && num_Directory != 0x0000) {
			throw new CompoundDetectException(ErrCode.INVALID_NUM_DIRECTORYSECTOR);
		}
		num_SAT		  					= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		first_SecID_Directory			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		offset += 4; // Transaction Signature Number
		miniStreamCutoffSize			= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		if (miniStreamCutoffSize != 0x00001000) {
			throw new CompoundDetectException(ErrCode.INVALID_MINI_STREAM_CUTOFF);
		}
		first_SecID_SSAT				= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		num_SSAT						= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		first_SecID_MSAT				= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		num_MSAT						= buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
		offset += 4;
		
		while(offset<512) {
			int sector = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0xFF0000 | buf[offset+1]<<8&0xFF00 | buf[offset]&0xFF;
			offset += 4;
			if (sector != 0xFFFFFFFF) {
				SAT_list.add(sector);
			}
		}
	}
	
	public byte[] read(int len) throws IOException {
		byte[] buf = new byte[len];
		raf.read(buf, 0, len);
		return buf;
	}
	
	public void close() throws IOException {
		raf.close();
	}

}
