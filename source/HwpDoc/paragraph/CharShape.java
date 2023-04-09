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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.sun.star.logging.LogLevel;

import HwpDoc.Exception.HwpParseException;

public class CharShape {
    private static final Logger log = Logger.getLogger(CharShape.class.getName());

    public int  start;
    public int  charShapeID;

    public static List<CharShape> parse(int tagNum, int level, int size, byte[] buf, int off, int version) throws HwpParseException {
        int offset = off;
        
        List<CharShape> charShapeList = new ArrayList<CharShape>();
        
        while (size-(offset-off) >= 8) {
            CharShape   shape = new CharShape();
            shape.start         = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
            shape.charShapeID   = buf[offset+3]<<24&0xFF000000 | buf[offset+2]<<16&0x00FF0000 | buf[offset+1]<<8&0x0000FF00 | buf[offset]&0x000000FF;
            offset += 4;
    
            log.fine("                                                  "
                    +"String시작위치="+shape.start
                    +",문자모양ID="+shape.charShapeID
            );
            charShapeList.add(shape);
        }
        
        if (offset-off!=size) {
            log.fine("[TAG]=" + tagNum + ", size=" + size + ", but currentSize=" + (offset-off));
            throw new HwpParseException();
        }
        return charShapeList;
    }

    public static int fillCharShape(int tagNum, int level, int size, byte[] buf, int off, int version, LinkedList<Ctrl> paras) throws HwpParseException {
        
        List<CharShape> charShapeList = parse(tagNum, level, size, buf, off, version);

        if (paras != null) {
            
            Iterator<CharShape> iter = charShapeList.iterator();
            while(iter.hasNext()) {
                CharShape shape = iter.next();
                if (shape.start==0) {
                    paras.stream().filter(p -> (p instanceof ParaText))
                                  .forEach(p -> ((ParaText)p).charShapeId = shape.charShapeID);
                } else if (shape.start > 0) {
                    Optional<ParaText> paraTextOp = 
                            paras.stream().filter(p -> (p instanceof ParaText))
                                          .map(p -> (ParaText)p)
                                          .filter(t -> t.startIdx <= shape.start && shape.start < t.startIdx+t.text.length())
                                          .reduce((a, b) -> b);
                    if (paraTextOp.isPresent()) {
                        ParaText t = paraTextOp.get();
                        if (t.startIdx == shape.start) {
                            t.charShapeId = shape.charShapeID;
                        } else {    // paraText.startIdx < shape.start
                            // split
                            int lenToSplit = shape.start - t.startIdx;
                            String splitLeftText = t.text.substring(0, lenToSplit);
                            String splitRightText = t.text.substring(lenToSplit);
                            t.text = splitLeftText;
                            
                            ParaText newParaText = new ParaText("____", splitRightText, shape.start, shape.charShapeID);
                            int index = paras.indexOf(t);
                            paras.add(index+1, newParaText);
                        }
                    }
                    paras.stream().filter(p -> (p instanceof ParaText))
                                  .filter(p -> ((ParaText)p).startIdx > shape.start)
                                  .forEach(p -> ((ParaText)p).charShapeId = shape.charShapeID);
                }
            }
            return paras.size();
        } else {
            return 0;
        }
    }

}
