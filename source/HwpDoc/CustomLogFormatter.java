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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class CustomLogFormatter extends Formatter {
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm");
	private Date dat = new Date();

	@Override
	public String format(LogRecord record) {
		StringBuffer buf = new StringBuffer();
		dat.setTime(record.getMillis());
		
		buf.append("[").append(dateFormat.format(dat)).append("] ")
			.append("(").append(shortenClass(record.getSourceClassName())+"."+shortenMethod(record.getSourceMethodName())).append(") ")
			.append(record.getLevel()).append(": ").append(formatMessage(record));
	    if (record.getThrown() != null) {
	        StringWriter sw = new StringWriter();
	        PrintWriter pw = new PrintWriter(sw);
	        pw.println();
	        record.getThrown().printStackTrace(pw);
	        pw.close();
	        buf.append(sw.toString());
	    }
		buf.append("\n");

		return buf.toString();
	}

	/** 소스 클래스명이 20자보다 짧거나 없는 경우에도 예외 없이 로그가 기록되도록 한다. */
	private static String shortenClass(String className) {
		if (className==null) {
			return "?";
		}
		return className.length()>20 ? className.substring(className.length()-20) : className;
	}

	/** 소스 메소드명이 4자보다 짧거나 없는 경우에도 예외 없이 로그가 기록되도록 한다. */
	private static String shortenMethod(String methodName) {
		if (methodName==null) {
			return "?";
		}
		return methodName.length()>4 ? methodName.substring(0,4) : methodName;
	}
}
