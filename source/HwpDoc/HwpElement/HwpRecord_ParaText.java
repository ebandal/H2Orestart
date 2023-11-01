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
package HwpDoc.HwpElement;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import HwpDoc.Exception.HwpParseException;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_AutoNumber;
import HwpDoc.paragraph.Ctrl_Character;
import HwpDoc.paragraph.Ctrl_Character.CtrlCharType;
import HwpDoc.paragraph.Ctrl_ColumnDef;
import HwpDoc.paragraph.Ctrl_EqEdit;
import HwpDoc.paragraph.Ctrl_GeneralShape;
import HwpDoc.paragraph.Ctrl_HeadFoot;
import HwpDoc.paragraph.Ctrl_NewNumber;
import HwpDoc.paragraph.Ctrl_Note;
import HwpDoc.paragraph.Ctrl_PageNumPos;
import HwpDoc.paragraph.Ctrl_SectionDef;
import HwpDoc.paragraph.Ctrl_Table;
import HwpDoc.paragraph.ParaText;

public class HwpRecord_ParaText extends HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord_ParaText.class.getName());
    private static final String PATTERN_STRING = "[\\u0000\\u000a\\u000d\\u0018-\\u001f]|[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017].{6}[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017]";
    private static final String PATTERN_8BYTES = "[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017].{6}[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017]";
    public static Pattern pattern = Pattern.compile(PATTERN_STRING);

	HwpRecord_ParaText(int tagNum, int level, int size) {
		super(tagNum, level, size);
	}
	
	public static List<Ctrl> parse(int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
		int offset = off;
		
		ArrayList<Ctrl> paras = new ArrayList<>();
		
		String text = new String(buf, offset, size, StandardCharsets.UTF_16LE);
		offset += size;

        Matcher m = pattern.matcher(text);
        
        log.finer("paraText Length="+ text.length());
        int prevIndex = 0;
        
        while(m.find()) {
            if (m.start()>prevIndex) {
                // write text
                int startIndex = prevIndex;
                
                String content = text.substring(startIndex, m.start());
                paras.add(new ParaText("____", content, startIndex));
            }
            
            if (m.start()+1==m.end()) {
                // 문자컨드롤
                byte controlByte = m.group().getBytes(StandardCharsets.UTF_16LE)[0];
                
                switch(controlByte) {
                case 0x0a:      // 10 한 줄 끝 (line break);
                    paras.add(new Ctrl_Character("   _", CtrlCharType.LINE_BREAK));
                    break;
                case 0x0d:      // 13 문단 끝 (para break)
                    paras.add(new Ctrl_Character("   _", CtrlCharType.PARAGRAPH_BREAK));
                    break;
                case 0x18:      // 24 하이픈
                    paras.add(new Ctrl_Character("   _", CtrlCharType.HARD_HYPHEN));
                    break;
                case 0x1e:      // 30 묶음 빈칸
                case 0x1f:      // 31 고정폭 빈칸
                    paras.add(new Ctrl_Character("   _", CtrlCharType.HARD_SPACE));
                    break;
                }
                
            } else if (m.start()+8==m.end()) {
                // 인라인 컨트롤, 확장컨트롤
                byte controlByte = m.group().getBytes(StandardCharsets.UTF_16LE)[0];
                String info = new String(m.group().getBytes(StandardCharsets.UTF_16LE), 2, 12, StandardCharsets.US_ASCII).replaceAll("[\\x00-\\x20]+$", "");

                switch(controlByte) {
                case 0x04:  // 필드 끝
                    break;
                case 0x08:  // title mark
                    break;
                case 0x09:  // 탭
                    paras.add(new ParaText("____", "\t", 0));
                    break;
                case 0x10:  // 머리말/꼬리말
                    paras.add(new Ctrl_HeadFoot(info));
                    break;
                case 0x12:  // 자동번호
                    paras.add(new Ctrl_AutoNumber(info));
                    break;
                case 0x15:  // 페이지 컨트롤(감추기, 새번호로 시작 등)
                    {
                        switch(info) {
                        case "dhgp":    // 감추기
                            break;
                        case "pngp":    // 쪽 번호 위치
                            paras.add(new Ctrl_PageNumPos(info));
                            break;
                        case "onwn":    // 새 번호 지정
                            paras.add(new Ctrl_NewNumber(info));
                            break;
                        }
                    }
                    break;
                case 0x02:  // 구역정의/단정의
                    {
                        switch(info) {
                        case "dces":
                            paras.add(new Ctrl_SectionDef(info));
                            break;
                        case "dloc":
                            paras.add(new Ctrl_ColumnDef(info));
                            break;
                        }
                    }
                    break;
                case 0x03:  // 필드 시작 (누름틀,하이퍼링크,블록책갈피,표계산식,문서 요약,사용자 정보,현재 날짜/시간,문서 날짜/시간,파일 경로,상호 참조,메일머지,메모,교정부호,개인정보
                case 0x0e:  // 예약
                case 0x0f:  // 숨은 설명
                    break;
                case 0x11:  // 각주/미주
                    paras.add(new Ctrl_Note(info));
                    break;
                case 0x16:  // 책갈피/찾아보기 표식
                case 0x17:  // 덧말/글자 겹침
                    break;
                case 0x0b:  // 그리기 개체/표
                    {
                        switch(info) {
                        case " osg":
                            paras.add(new Ctrl_GeneralShape(info));
                            break;
                        case " lbt":
                            paras.add(new Ctrl_Table(info));
                            break;
                        case "deqe":
                            paras.add(new Ctrl_EqEdit(info));
                            break;
                        case "mrof":
                            break;
                        }
                    }
                    break;
                default:
                    break;
                }
            }
            prevIndex = m.end();
        }
        
        if (prevIndex<text.length()) {
            // write final text
            int startIndex = prevIndex;
            String content = text.substring(startIndex);
            paras.add(new ParaText("____", content, startIndex));
        }
		        
		String readable = toReadableString(text);
		log.fine("                                                  "+readable);

		if (offset-off-size != 0) {
			log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
			dump(buf, off, size);
			throw new HwpParseException();
		}
		return paras;
	}
	
	public static String getText(int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        String text = new String(buf, offset, size, StandardCharsets.UTF_16LE);
        offset += size;

        String readable = toReadableString(text);
        log.fine("                                                  "+readable);

        if (offset-off-size != 0) {
            log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
            dump(buf, off, size);
            throw new HwpParseException();
        }
        return text;
    }
	
	private static String toReadableString(String text) {
		StringBuffer sb = new StringBuffer();
		char extendChar = 0;
		char inlineChar = 0;
		ByteBuffer bb = ByteBuffer.allocate(text.length()*2).order(ByteOrder.LITTLE_ENDIAN);
		
		for(int i=0;i<text.length();i++) {
			char c = text.charAt(i);
			if (extendChar==0 && inlineChar==0 && c>31) {
				sb.append(c);
			} else if (extendChar!=0) {
				if (c==extendChar) {
					sb.append(new String(bb.array(), StandardCharsets.US_ASCII).trim()+"[/EXT("+(int)c+")]");
					((Buffer)bb).clear();	// Java9 이후와 호환을 위해
					extendChar=0;
				} else {
					bb.putChar(c);
				}
			} else if (inlineChar!=0) {
				if (c==inlineChar) {
					switch(c) {
					case 9:
						sb.append(HwpRecord.toHexString(bb.array())+"[/INL("+(int)c+")]"); 
						break;
					default:
						sb.append(new String(bb.array(), StandardCharsets.US_ASCII).trim()+"[/INL("+(int)c+")]");
					}
					((Buffer)bb).clear();	// Java9 이후와 호환을 위해
					inlineChar=0;
				} else {
					bb.putChar(c);
				}
			} else if (c <= 31) {
				switch(c) {
				case 13:
					sb.append("[END]");
					break;
				case 0:
				case 10:
				case 24:
				case 25:
				case 26:
				case 27:
				case 28:
				case 29:
				case 30:
				case 31:
					sb.append("[CHAR("+(int)c+")]");
					break;
				case 1:
				case 2:
				case 3:
				case 11:
				case 12:
				case 14:
				case 15:
				case 16:
				case 17:
				case 18:
				case 21:
				case 22:
				case 23:
					sb.append("[EXT("+(int)c+")]");
					((Buffer)bb).clear();	// Java9 이후와 호환을 위해
					extendChar = c;
					break;
				case 4:
				case 5:
				case 6:
				case 7:
				case 8:
				case 9:
				case 19:
				case 20:
					sb.append("[INL("+(int)c+")]");
					((Buffer)bb).clear();	// Java9 이후와 호환을 위해
					inlineChar = c;
					break;
				default:
					bb.putChar(c);
				}
			}
		}
		return sb.toString();
	}

}
