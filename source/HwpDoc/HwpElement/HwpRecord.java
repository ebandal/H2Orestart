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

import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class HwpRecord {
	private static final Logger log = Logger.getLogger(HwpRecord.class.getName());

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	HwpTag tag;
	int level;
	int size;
	
	HwpRecord(int tagNum, int level, int size) {
		this(HwpTag.from(tagNum), level, size);
	}
	
	HwpRecord(HwpTag tag, int level, int size) {
		this.tag = tag;
		this.level = level;
		this.size = size;
	}
	
	public static void dump(byte[] buf, int off, int size) {
		int offset = off;
		while(offset < off+size) {
		    char[] hexChars = new char[16 * 2];
		    for (int j=0; j<16 && offset+j<buf.length; j++) {
		        int v = buf[offset+j] & 0xFF;
		        hexChars[j*2] = HEX_ARRAY[v >>> 4];
		        hexChars[j*2+1] = HEX_ARRAY[v & 0x0F];
		    }
		    offset += 16;
			log.finer(new String(hexChars));
		}
	}

	public static void dump(String str) {
		log.finer(str);
	}
	
	public static void dumpNode(Node node, int depth) {
	    NamedNodeMap attributes = node.getAttributes();
	    if (attributes != null) {
	        StringBuffer sb = new StringBuffer();
    	    for (int i=0; i<attributes.getLength(); i++) {
    	        Node attrNode = attributes.item(i);
    	        sb.append(attrNode.getNodeName() + "=" + attrNode.getNodeValue()+",");
    	    }
    	    log.info(IntStream.range(0,depth).mapToObj(i -> " ").collect(Collectors.joining()) + node.getNodeName()+"="+node.getNodeValue()+",[" + sb.toString() + "]");
	    }
	    
	    NodeList children = node.getChildNodes();
	    if (children != null) {
    	    for (int i=0; i<children.getLength(); i++) {
    	        dumpNode(children.item(i), depth+1);
    	    }
	    }
	}

	/** 예외 메시지에 담을 원본 바이트의 최대 개수 */
	private static final int RAW_DUMP_LIMIT = 128;

	/**
	 * 레코드를 파싱하지 못했을 때 원인을 추적할 수 있도록 태그, 크기, 소비한 바이트 수, 원본 바이트를
	 * 문자열로 만든다. 예외 메시지에 담아서 사용자의 로그 파일에 root cause 가 남도록 하기 위한 것이다.
	 */
	protected String parseError(byte[] buf, int off, int size, int offset) {
		return parseError(getClass().getSimpleName()+"[tag="+tag+",level="+level+"]", buf, off, size, offset);
	}

	public static String parseError(String where, byte[] buf, int off, int size, int offset) {
		int consumed = offset - off;
		int remained = size - consumed;
		return where + " size=" + size + ", off=" + off
				+ ", consumed=" + consumed + ", remained=" + remained
				+ "\nraw=\n" + toHexDump(buf, off, size, consumed);
	}

	/** 처리하지 못한 XML 속성의 이름과 값을, 해당 요소의 전체 속성과 함께 문자열로 만든다. */
	public static String attrError(String where, Node node, Node attr) {
		return where + " unsupported attribute "
				+ (attr==null ? "?" : attr.getNodeName()+"="+attr.getNodeValue())
				+ (node==null ? "" : " in " + describe(node));
	}

	/** 노드 이름과 모든 속성명, 속성값을 로그용 한 줄 문자열로 만든다. */
	public static String describe(Node node) {
		if (node==null) {
			return "null";
		}
		StringBuffer sb = new StringBuffer(node.getNodeName());
		sb.append("[");
		NamedNodeMap attrs = node.getAttributes();
		if (attrs!=null) {
			for (int i=0; i<attrs.getLength(); i++) {
				Node attr = attrs.item(i);
				if (i>0) {
					sb.append(", ");
				}
				sb.append(attr.getNodeName()).append("=").append(attr.getNodeValue());
			}
		}
		sb.append("]");
		return sb.toString();
	}

	/** off 위치부터 len 바이트를 16진수 문자열로 만든다. 너무 길면 잘라낸다. */
	public static String toHexString(byte[] buf, int off, int len) {
		if (buf==null) {
			return "";
		}
		int from = off<0 ? 0 : off;
		int to = off+len>buf.length ? buf.length : off+len;
		int count = to-from;
		if (count<=0) {
			return "";
		}
		int printed = count>RAW_DUMP_LIMIT ? RAW_DUMP_LIMIT : count;
		char[] hexChars = new char[printed*2];
		for (int i=0; i<printed; i++) {
			int v = buf[from+i] & 0xFF;
			hexChars[i*2] = HEX_ARRAY[v >>> 4];
			hexChars[i*2+1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars) + (count>printed ? "...(" + count + " bytes)" : "");
	}

	public static String toHexString(byte[] buf) {
		char[] hexChars = new char[buf.length*2];
		for (int i=0;i<buf.length;i++) {
	        int v = buf[i] & 0xFF;
	        hexChars[i*2] = HEX_ARRAY[v >>> 4];
	        hexChars[i*2+1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}

	/**
	 * 버퍼의 내용을 Hex Editor(16바이트 단위 가로폭, 오프셋, Hex 값, ASCII 표현) 형식의 문자열로 만든다.
	 */
	public static String toHexDump(byte[] buf, int off, int len) {
		return toHexDump(buf, off, len, -1);
	}

	public static String toHexDump(byte[] buf) {
		return buf == null ? "" : toHexDump(buf, 0, buf.length, -1);
	}

	/**
	 * 버퍼의 내용을 Hex Editor 형식으로 만들며, highlightOffset 위치에 에러 포인터 커서를 표시한다.
	 */
	public static String toHexDump(byte[] buf, int off, int len, int highlightOffset) {
		if (buf == null) {
			return "";
		}
		int from = off < 0 ? 0 : off;
		int to = off + len > buf.length ? buf.length : off + len;
		int count = to - from;
		if (count <= 0) {
			return "";
		}

		int printed = count > RAW_DUMP_LIMIT ? RAW_DUMP_LIMIT : count;
		StringBuilder sb = new StringBuilder();
		final int BYTES_PER_LINE = 16;

		for (int i = 0; i < printed; i += BYTES_PER_LINE) {
			int lineLen = (i + BYTES_PER_LINE <= printed) ? BYTES_PER_LINE : (printed - i);

			// 오프셋 (4자리 16진수)
			sb.append(String.format("%04X: ", i));

			// Hex 영역 (16바이트 기준 일정 폭 유지)
			for (int j = 0; j < BYTES_PER_LINE; j++) {
				if (j == 8) {
					sb.append(" ");
				}
				if (j < lineLen) {
					int v = buf[from + i + j] & 0xFF;
					sb.append(HEX_ARRAY[v >>> 4]);
					sb.append(HEX_ARRAY[v & 0x0F]);
					sb.append(" ");
				} else {
					sb.append("   ");
				}
			}

			sb.append(" |");
			// ASCII 영역
			for (int j = 0; j < lineLen; j++) {
				int v = buf[from + i + j] & 0xFF;
				if (v >= 0x20 && v <= 0x7E) {
					sb.append((char) v);
				} else {
					sb.append('.');
				}
			}
			sb.append("|");

			// highlightOffset이 현재 라인에 위치할 경우 바로 아래 줄에 포인터 표시
			if (highlightOffset >= i && highlightOffset < i + lineLen) {
				int posInLine = highlightOffset - i;
				int spaces = 6 + (posInLine * 3) + (posInLine >= 8 ? 1 : 0);
				sb.append("\n");
				for (int s = 0; s < spaces; s++) {
					sb.append(" ");
				}
				sb.append(String.format("^^-- [error at offset +%d (0x%04X)]", highlightOffset, highlightOffset));
			}

			if (i + BYTES_PER_LINE < printed || count > printed) {
				sb.append("\n");
			}
		}

		if (count > printed) {
			sb.append("...(").append(count).append(" bytes)");
		}

		return sb.toString();
	}

}
