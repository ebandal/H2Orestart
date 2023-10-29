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
package compare;

import java.util.logging.Logger;
import java.util.stream.Collectors;

import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.Ctrl_Character;
import HwpDoc.paragraph.Ctrl_GeneralShape;
import HwpDoc.paragraph.Ctrl_Table;
import HwpDoc.paragraph.ParaText;
import HwpDoc.paragraph.HwpParagraph;

public class CompRecurs {
	private static final Logger log = Logger.getLogger(CompRecurs.class.getName());
	private static final String PATTERN_STRING = "[\\u0000\\u000a\\u000d\\u0018-\\u001f]|[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017].{6}[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017]";
    private static final String PATTERN_8BYTES = "[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017].{6}[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017]";

    
    public static String getParaString(HwpParagraph para) {
        StringBuffer sb = new StringBuffer();

        if (para.p!=null) {
            for (Ctrl ctrl : para.p) {
                if (ctrl==null) continue;
                
                switch(ctrl.ctrlId) {
                case "____":
                    sb.append(((ParaText)ctrl).text);
                    break;
                case "   _":
                    {
                        switch(((Ctrl_Character)ctrl).ctrlChar) {
                        case LINE_BREAK:
                            sb.append("\n");
                            break;
                        case PARAGRAPH_BREAK:
                            sb.append("");
                           break;
                        case HARD_HYPHEN:
                            sb.append("-");
                            break;
                        case HARD_SPACE:
                            sb.append(" ");
                            break;
                        }
                    }
                    break;
                case "dces":
                    break;
                case "dloc":
                    break;
                case "daeh":    // 머리말
                case "toof":    // 꼬리말
                    break;
                case "  nf":    // 각주
                case "  ne":    // 미주
                    break;
                case " lbt":    // table
                    {
                        // 테이블 경우,  셀 내용을 수집
                        String tableContent = ((Ctrl_Table) ctrl).cells.stream()
                                                                .flatMap(cell -> cell.paras.stream())
                                                                .filter(cp -> cp.p!=null)
                                                                .flatMap(cp -> cp.p.stream())
                                                                .filter(c -> (c!=null) && (c instanceof ParaText))
                                                                .map(c -> (ParaText)c)
                                                                .map(t -> t.text==null?"":t.text.replaceAll(PATTERN_STRING, ""))
                                                                .collect(Collectors.joining("|"));
                        sb.append("["+tableContent+"]");
                    }
                    break;
                case "onta":    // 자동 번호
                    break;
                case "onwn":    // 새 번호 지정
                    break;
                case " osg":    // GeneralShapeObject
                case "cip$":    // 그림       ShapePic obj = new ShapePic(shape);
                case "cer$":    // 사각형
                case "cra$":    // 호
                case "elo$":    // OLE
                case "nil$":    // 선
                case "noc$":    // 묶음 개체
                case "lle$":    // 타원
                case "lop$":    // 다각형
                case "ruc$":    // 곡선
                case "div$":    // 비디오
                case "tat$":    // 글맵시
                    {
                        // 그림 개체의 경우  글상자, 캡션만 출력
                        if (((Ctrl_GeneralShape) ctrl).paras != null) {
                            String content = ((Ctrl_GeneralShape) ctrl).paras.stream()
                                                        .flatMap(p -> p.p.stream())
                                                        .filter(c -> c instanceof ParaText)
                                                        .map(c -> (ParaText)c)
                                                        .map(t -> t.text.replaceAll(PATTERN_8BYTES, "").replaceAll("[\\u000a\\u000d]", "\\\\n"))
                                                        .collect(Collectors.joining(""));
                            sb.append("["+content+"]");
                        }
        
                        if (((Ctrl_GeneralShape) ctrl).caption != null) {
                            String caption = ((Ctrl_GeneralShape) ctrl).caption.stream()
                                                        .flatMap(p -> p.p.stream())
                                                        .filter(c -> c instanceof ParaText)
                                                        .map(c -> (ParaText)c)
                                                        .map(cap -> cap.text.replaceAll(PATTERN_STRING, ""))
                                                        .collect(Collectors.joining(""));
                            sb.append(caption);
                        }
                    }
                    break;
                case "deqe":    // 한글97 수식
                    break;
                case "cot%":    // FIELD_TABLEOFCONTENT
                case "klc%":    // FIELD_CLICKHERE
                case "dhgp":    // 감추기
                case "pngp":    // 쪽 번호 위치
                case "knu%":    // FIELD_UNKNOWN
                case "etd%":    // FIELD_DATE
                case "tdd%":    // FIELD_DOCDATE
                case "tap%":    // FIELD_PATH
                case "kmb%":    // FIELD_BOOKMARK
                case "gmm%":    // FIELD_MAILMERGE
                case "frx%":    // FIELD_CROSSREF
                case "rms%":    // FIELD_SUMMARY
                case "rsu%":    // FIELD_USERINFO
                case "klh%":    // FIELD_HYPERLINK
                case "gis%":    // FIELD_REVISION_SIGN
                case "d*%%":    // FIELD_REVISION_DELETE
                case "a*%%":    // FIELD_REVISION_ATTACH
                case "C*%%":    // FIELD_REVISION_CLIPPING
                case "S*%%":    // FIELD_REVISION_SAWTOOTH
                case "T*%%":    // FIELD_REVISION_THINKING
                case "P*%%":    // FIELD_REVISION_PRAISE
                case "L*%%":    // FIELD_REVISION_LINE
                case "c*%%":    // FIELD_REVISION_SIMPLECHANGE
                case "h*%%":    // FIELD_REVISION_HYPERLINK
                case "A*%%":    // FIELD_REVISION_LINEATTACH
                case "i*%%":    // FIELD_REVISION_LINELINK
                case "t*%%":    // FIELD_REVISION_LINETRANSFER
                case "r*%%":    // FIELD_REVISION_RIGHTMOVE
                case "l*%%":    // FIELD_REVISION_LEFTMOVE
                case "n*%%":    // FIELD_REVISION_TRANSFER
                case "e*%%":    // FIELD_REVISION_SIMPLEINSERT
                case "lps%":    // FIELD_REVISION_SPLIT
                case "rm%%":    // FIELD_REVISION_CHANGE
                case "em%%":    // FIELD_MEMO
                case "rpc%":    // FIELD_PRIVATE_INFO_SECURITY
                default:
                }
            }
        }
        
        return sb.toString();
    }
    
}
