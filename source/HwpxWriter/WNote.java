/* Copyright (C) 2026 ebandal
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
package HwpxWriter;

import java.util.ArrayList;
import java.util.List;

/** 각주/미주 (hp:footNote, hp:endNote) */
public class WNote {
    public boolean endNote;
    /** 번호 (1 부터) */
    public int number;
    /** 번호 뒤 글자 (예: ")"). 첫 문단 앞에 자동 번호를 넣을 때 쓴다 */
    public String suffixChar = ")";
    /** 첫 문단 앞에 자동 번호(hp:autoNum)를 넣는가 */
    public boolean autoNumber;
    public final List<WParagraph> paragraphs = new ArrayList<WParagraph>();
}
