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
package HwpDoc.paragraph;

import java.util.List;

public class CommonObj {
	public String 	ctrlId;

	public int 		objAttr;		// 개체 공통 속성 (표 70참조)
	public int		yOffset;		// 세로 오프셋 값
	public int		xOffset;		// 가로 오프셋 값
	public int		objWidth;		// width 오브젝트의 폭
	public int		objHeight;		// height 오브젝트의 높이
	public int 		zOrder;		
	public short[] 	objSpaces;
	public int		objInstanceID;	// 문서 내 각 개체에 대한 고유 아이디(instance ID)
	public int		blockPageBreak;	// 쪽나눔 방지 on(1)/off(0)
	public String	objDesc;		// 개체 설명문
	
	public List<HwpParagraph> paras;	// LIST_HEADER 뒤에 따라오는 PARA_HEADER (복수개)
	public int 		captionAttr;	// 캡션 속성
	public int 		captionWidth;	// 캡션 폭
	public int 		captionSpacing;	// 캡션과 틀 사이 간격
	public int		captionMaxW;	// 텍스트의 최대 길이 (=개체의 폭)

}
