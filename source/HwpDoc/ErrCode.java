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

public enum ErrCode {
	
	UNDEFINED 					(0),
	SIGANTURE_NOT_MATCH			(1),
	INVALID_MAJORVERSION		(2),
	INVALID_MINORVERSION		(3),
	INVALID_BYTEORDER			(4),
	INVALID_SECTORSHIFT			(5),
	INVALID_MINISECTORSHIFT		(6),
	INVALID_NUM_DIRECTORYSECTOR	(7),
	INVALID_MINI_STREAM_CUTOFF	(8),
	FILE_READ_ERROR             (9),
	INVALID_ZIP_DATA_FORMAT     (10),
	;
	
	
	private int errCode;
	
	ErrCode(int code) {
		this.errCode = code;
	}

	public void set(int errCode) {
		this.errCode = errCode;
	}

	public int get() {
		return errCode;
	}
}
