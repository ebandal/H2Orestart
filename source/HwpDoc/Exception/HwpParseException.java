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
 * 작성자 : 반희수 ebandal@gmail.com  
 * 작성일 : 2022.10
 */
package HwpDoc.Exception;

import HwpDoc.ErrCode;

public class HwpParseException extends Exception {
	private static final long serialVersionUID = -6388448371538804607L;
	private ErrCode errCode;

	public HwpParseException() {
        super();
    }

	public HwpParseException(ErrCode errCode) {
        super(errCode.toString());
        this.errCode = errCode;
	}

	public HwpParseException(ErrCode errCode, String messsage) {
        super(messsage);
        this.errCode = errCode;
	}
	
	public HwpParseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public HwpParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public HwpParseException(String message) {
        super(message);
    }

    public HwpParseException(Throwable cause) {
        super(cause);
    }
    
    public ErrCode getReason() {
    	return errCode==null?ErrCode.UNDEFINED:errCode;
    }

}
