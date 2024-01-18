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
package HwpDoc.paragraph;

public class Ctrl_Character extends Ctrl {
    public CtrlCharType    ctrlChar;
    public int charShapeId;

    public Ctrl_Character(String ctrlId, CtrlCharType ctrlChar) {
        super(ctrlId);
        this.ctrlChar = ctrlChar;
    }
    
    @Override
    public int getSize() {
        return 1;
    }
    
    public enum CtrlCharType {
        LINE_BREAK      (0x1),
        PARAGRAPH_BREAK (0x2),
        HARD_HYPHEN     (0x3),
        HARD_SPACE      (0x4);
     
        private int type;
        
        private CtrlCharType(int type) { 
            this.type = type;
        }

    }
}
