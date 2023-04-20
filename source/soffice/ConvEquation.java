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
package soffice;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.star.beans.XPropertySet;
import com.sun.star.document.XEmbeddedObjectSupplier2;
import com.sun.star.lang.XComponent;
import com.sun.star.text.ControlCharacter;
import com.sun.star.text.HoriOrientation;
import com.sun.star.text.RelOrientation;
import com.sun.star.text.TextContentAnchorType;
import com.sun.star.text.VertOrientation;
import com.sun.star.text.XText;
import com.sun.star.text.XTextContent;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextFrame;
import com.sun.star.uno.Exception;
import com.sun.star.uno.UnoRuntime;

import HwpDoc.paragraph.Ctrl_EqEdit;


public class ConvEquation {
	private static final Logger log = Logger.getLogger(ConvEquation.class.getName());
	private static int autoNum = 0;

	public static void reset(WriterContext wContext) {
		autoNum = 0;
	}
	
	public static void addFormula(WriterContext wContext, Ctrl_EqEdit eq, int step) {
		String formula = convertEquation(eq.eqn);
    	boolean hasCaption = eq.caption==null?false:eq.caption.size()==0?false:true;
    	XTextFrame xFrame = null;
    	XText xFrameText = null;
    	XTextCursor xFrameCursor = null;

    	try {
    		if (hasCaption) {
    			xFrame = ConvGraphics.makeOuterFrame(wContext, eq, false, step);
				// Frame 내부 Cursor 생성
				xFrameText = xFrame.getText();
				xFrameCursor = xFrameText.createTextCursor();
    		}

    		Object obj = wContext.mMSF.createInstance("com.sun.star.text.TextEmbeddedObject");
			XTextContent embedContent = UnoRuntime.queryInterface(XTextContent.class, obj);
			if (embedContent == null) {
				log.severe("Could not create a formula embedded object");
				return;
			}
			
			// set class ID for type of object being inserted
			XPropertySet props = UnoRuntime.queryInterface(XPropertySet.class, embedContent);
			props.setPropertyValue("CLSID", "078B7ABA-54FC-457F-8551-6147e776a997");  // a formula
			props.setPropertyValue("AnchorType", TextContentAnchorType.AS_CHARACTER);
            props.setPropertyValue("Height", Transform.translateHwp2Office(eq.height));
            props.setPropertyValue("Width", Transform.translateHwp2Office(eq.width));
			
    		if (hasCaption) {
    			props.setPropertyValue("VertOrient", VertOrientation.CENTER);				// Top, Bottom, Center, fromBottom
    			props.setPropertyValue("VertOrientRelation", RelOrientation.TEXT_LINE);   // Base line, Character, Row
    			props.setPropertyValue("HoriOrient", HoriOrientation.CENTER);	// 0:NONE=From left
    			props.setPropertyValue("HoriOrientRelation", RelOrientation.PRINT_AREA);   // 1:paragraph text area
    		}

	        if (hasCaption) {
		        xFrameText.insertTextContent(xFrameCursor, embedContent, false);
		        xFrameText.insertControlCharacter(xFrameCursor, ControlCharacter.PARAGRAPH_BREAK, false);
	        } else {
		        wContext.mText.insertTextContent(wContext.mTextCursor, embedContent, false);
	        }

			// access object's model
			XEmbeddedObjectSupplier2 embedObjSupplier = UnoRuntime.queryInterface(XEmbeddedObjectSupplier2.class, embedContent);
			XComponent embedObjModel = embedObjSupplier.getEmbeddedObject();
			XPropertySet formulaProps = UnoRuntime.queryInterface(XPropertySet.class, embedObjModel);
			formulaProps.setPropertyValue("Formula", formula);

			// 캡션 쓰기
  			if (hasCaption) {
  				ConvGraphics.addCaptionString(wContext, xFrameText, xFrameCursor, eq, step);
  			}
		} catch (Exception e) {
			e.printStackTrace();
		} catch (SkipDrawingException e) {
		    e.printStackTrace();
		}
	}
	
	public static String replacePattern(String targetStr, String regex, String replaceWith) {
		Pattern pattern = Pattern.compile(regex);
		Matcher m = pattern.matcher(targetStr);
		StringBuffer sb = new StringBuffer();
		int startIndex = 0;
		int endIndex = -1;
		
		while(m.find()) {
			startIndex = m.start();
			if (startIndex > 0) sb.append(targetStr.substring(endIndex+1, startIndex));
			endIndex = m.end();
			String para1 = targetStr.substring(startIndex, endIndex);
			para1 = para1.replaceAll(regex, replaceWith);
			sb.append(para1);
		}
		if (endIndex < targetStr.length()) sb.append(targetStr.substring(endIndex+1));
		return sb.toString();
	}
	
	public static String convertEquation(String hwpString) {
		String retString = hwpString;
		
		retString = retString.replaceAll("(?<![a-zA-Z0-9])SQRT(?![a-zA-Z0-9])", "sqrt")
							.replaceAll("(?<![a-zA-Z0-9])PILE(?![a-zA-Z0-9])", "alignc")
							.replaceAll("(?<![a-zA-Z0-9])LPILE(?![a-zA-Z0-9])", "alignl")
							.replaceAll("(?<![a-zA-Z0-9])RPILE(?![a-zA-Z0-9])", "alignr")
							.replaceAll("(?<![a-zA-Z0-9])LSUB(?![a-zA-Z0-9])", "lsub")
							.replaceAll("(?<![a-zA-Z0-9])LSUP(?![a-zA-Z0-9])", "lsup")
							.replaceAll("(?<![a-zA-Z0-9])under(?![a-zA-Z0-9])",  "underline")
							.replaceAll("(?<![a-zA-Z0-9])(SMALLUNION|smallunion|UNION|CAP)(?![a-zA-Z0-9])", "union")
							.replaceAll("(?<![a-zA-Z0-9])(SMALLINTER|smallinter|INTER)(?![a-zA-Z0-9])", "Intersection")
							.replaceAll("(?<![a-zA-Z0-9])PROD(?![a-zA-Z0-9])", "prod")
							.replaceAll("(?<![a-zA-Z0-9])Alpha(?![a-zA-Z0-9])", "%ALPHA")
							.replaceAll("(?<![a-zA-Z0-9])Beta(?![a-zA-Z0-9])", " %BETA")
							.replaceAll("(?<![a-zA-Z0-9])Gamma(?![a-zA-Z0-9])", "%GAMMA")
							.replaceAll("(?<![a-zA-Z0-9])Delta(?![a-zA-Z0-9])", "%DELTA")
							.replaceAll("(?<![a-zA-Z0-9])Epsilon(?![a-zA-Z0-9])", "%EPSILON")
							.replaceAll("(?<![a-zA-Z0-9])Zeta(?![a-zA-Z0-9])", "%ZETA")
							.replaceAll("(?<![a-zA-Z0-9])Eta(?![a-zA-Z0-9])", "%ETA")
							.replaceAll("(?<![a-zA-Z0-9])Theta(?![a-zA-Z0-9])", "%THETA")
							.replaceAll("(?<![a-zA-Z0-9])Iota(?![a-zA-Z0-9])", "%IOTA")
							.replaceAll("(?<![a-zA-Z0-9])Kappa(?![a-zA-Z0-9])", "%KAPPA")
							.replaceAll("(?<![a-zA-Z0-9])Lambda(?![a-zA-Z0-9])", "%LAMBDA")
							.replaceAll("(?<![a-zA-Z0-9])Mu(?![a-zA-Z0-9])", "%MU")
							.replaceAll("(?<![a-zA-Z0-9])Nu(?![a-zA-Z0-9])", "%NU")
							.replaceAll("(?<![a-zA-Z0-9])Xi(?![a-zA-Z0-9])", "%XI")
							.replaceAll("(?<![a-zA-Z0-9])Omicron(?![a-zA-Z0-9])", "%OMICRON")
							.replaceAll("(?<![a-zA-Z0-9])Pi(?![a-zA-Z0-9])", "%PI")
							.replaceAll("(?<![a-zA-Z0-9])Rho(?![a-zA-Z0-9])", "%RHO")
							.replaceAll("(?<![a-zA-Z0-9])(Sigma|SIGMA)(?![a-zA-Z0-9])", "%SIGMA")
							.replaceAll("(?<![a-zA-Z0-9])Tau(?![a-zA-Z0-9])", "%TAU")
							.replaceAll("(?<![a-zA-Z0-9])Upsilon(?![a-zA-Z0-9])", "%UPSILON")
							.replaceAll("(?<![a-zA-Z0-9])Phi(?![a-zA-Z0-9])", "%PHI")
							.replaceAll("(?<![a-zA-Z0-9])Chi(?![a-zA-Z0-9])", "%CHI")
							.replaceAll("(?<![a-zA-Z0-9])Psi(?![a-zA-Z0-9])", "%PSI")
							.replaceAll("(?<![a-zA-Z0-9])Omega(?![a-zA-Z0-9])", "%OMEGA")
							.replaceAll("(?<![a-zA-Z0-9])alpha(?![a-zA-Z0-9])", "%alpha")
							.replaceAll("(?<![a-zA-Z0-9])beta(?![a-zA-Z0-9])", "%beta")
							.replaceAll("(?<![a-zA-Z0-9])gamma(?![a-zA-Z0-9])", "%gamma")
							.replaceAll("(?<![a-zA-Z0-9])delta(?![a-zA-Z0-9])", "%delta")
							.replaceAll("(?<![a-zA-Z0-9])epsilon(?![a-zA-Z0-9])", "%epsilon")
							.replaceAll("(?<![a-zA-Z0-9])zeta(?![a-zA-Z0-9])", "%zeta")
							.replaceAll("(?<![a-zA-Z0-9])eta(?![a-zA-Z0-9])", "%eta")
							.replaceAll("(?<![a-zA-Z0-9])theta(?![a-zA-Z0-9])", "%theta")
							.replaceAll("(?<![a-zA-Z0-9])iota(?![a-zA-Z0-9])", "%iota")
							.replaceAll("(?<![a-zA-Z0-9])kappa(?![a-zA-Z0-9])", "%kappa")
							.replaceAll("(?<![a-zA-Z0-9])lambda(?![a-zA-Z0-9])", "%lambda")
							.replaceAll("(?<![a-zA-Z0-9])mu(?![a-zA-Z0-9])", "%mu")
							.replaceAll("(?<![a-zA-Z0-9])nu(?![a-zA-Z0-9])", "%nu")
							.replaceAll("(?<![a-zA-Z0-9])xi(?![a-zA-Z0-9])", "%xi")
							.replaceAll("(?<![a-zA-Z0-9])omicron(?![a-zA-Z0-9])", "%omicron")
							.replaceAll("(?<![a-zA-Z0-9])pi(?![a-zA-Z0-9])", "%pi")
							.replaceAll("(?<![a-zA-Z0-9])rho(?![a-zA-Z0-9])", "%rho")
							.replaceAll("(?<![a-zA-Z0-9])sigma(?![a-zA-Z0-9])", "%sigma")
							.replaceAll("(?<![a-zA-Z0-9])tau(?![a-zA-Z0-9])", "%tau")
							.replaceAll("(?<![a-zA-Z0-9])upsilon(?![a-zA-Z0-9])", "%upsilon")
							.replaceAll("(?<![a-zA-Z0-9])phi(?![a-zA-Z0-9])", "%phi")
							.replaceAll("(?<![a-zA-Z0-9])chi(?![a-zA-Z0-9])", "%chi")
							.replaceAll("(?<![a-zA-Z0-9])psi(?![a-zA-Z0-9])", "%psi")
							.replaceAll("(?<![a-zA-Z0-9])omega(?![a-zA-Z0-9])", "%omega")
							.replaceAll("(?<![a-zA-Z0-9])ALEPH(?![a-zA-Z0-9])", "aleph")
							.replaceAll("(?<![a-zA-Z0-9])HBAR(?![a-zA-Z0-9])", "hbar")
							.replaceAll("(?<![a-zA-Z0-9])IMAG(?![a-zA-Z0-9])", "im")
							.replaceAll("(?<![a-zA-Z0-9])WP(?![a-zA-Z0-9])", "wp")
							.replaceAll("(?<![a-zA-Z0-9])vartheta(?![a-zA-Z0-9])", "%vartheta")
							.replaceAll("(?<![a-zA-Z0-9])varpi(?![a-zA-Z0-9])", "%varpi")
							.replaceAll("(?<![a-zA-Z0-9])varsigma(?![a-zA-Z0-9])", "%varsigma")
							.replaceAll("(?<![a-zA-Z0-9])varphi(?![a-zA-Z0-9])", "%varphi")
							.replaceAll("(?<![a-zA-Z0-9])varepsilon(?![a-zA-Z0-9])", "%varepsilon")
							.replaceAll("(?<![a-zA-Z0-9])OPLUS(?![a-zA-Z0-9])", "oplus")
							.replaceAll("(?<![a-zA-Z0-9])OMINUS(?![a-zA-Z0-9])", "ominus")
							.replaceAll("(?<![a-zA-Z0-9])OTIMES(?![a-zA-Z0-9])", "otimes")
							.replaceAll("(?<![a-zA-Z0-9])ODOT(?![a-zA-Z0-9])", "odot")
							.replaceAll("(?<![a-zA-Z0-9])(OSLASH|ODIV)(?![a-zA-Z0-9])", "odivide")
							.replaceAll("(?<![a-zA-Z0-9])(VEE|LOR)(?![a-zA-Z0-9])", "or")
							.replaceAll("(?<![a-zA-Z0-9])WEDGE(?![a-zA-Z0-9])", "and")
							.replaceAll("(?<![a-zA-Z0-9])SUBSET(?![a-zA-Z0-9])", "subset")
							.replaceAll("(?<![a-zA-Z0-9])(SUPSET|SUPERSET)(?![a-zA-Z0-9])", "supset")
							.replaceAll("(?<![a-zA-Z0-9])SUBSETEQ(?![a-zA-Z0-9])", "subseteq")
							.replaceAll("(?<![a-zA-Z0-9])SUPSETEQ(?![a-zA-Z0-9])", "supseteq")
							.replaceAll("(?<![a-zA-Z0-9])IN(?![a-zA-Z0-9])", "in")
							.replaceAll("(?<![a-zA-Z0-9])OWNS(?![a-zA-Z0-9])", "owns")
							.replaceAll("(?<![a-zA-Z0-9])LEQ(?![a-zA-Z0-9])", "<=")
							.replaceAll("(?<![a-zA-Z0-9])GEQ(?![a-zA-Z0-9])", ">=")
							.replaceAll("(?<![a-zA-Z0-9])PREC(?![a-zA-Z0-9])", "prec")
							.replaceAll("(?<![a-zA-Z0-9])SUCC(?![a-zA-Z0-9])", "succ")
							.replaceAll("(?<![a-zA-Z0-9])PLUSMINUS(?![a-zA-Z0-9])", "plusminus")
							.replaceAll("(?<![a-zA-Z0-9])MINUSPLUS(?![a-zA-Z0-9])", "minusplus")
							.replaceAll("(?<![a-zA-Z0-9])(DIVIDE|divide)(?![a-zA-Z0-9])", "div")
							.replaceAll("(?<![a-zA-Z0-9])CIRC(?![a-zA-Z0-9])", "circ")
							.replaceAll("(?<![a-zA-Z0-9])EMPTYSET(?![a-zA-Z0-9])", "emptyset")
							.replaceAll("(?<![a-zA-Z0-9])EXIST(?![a-zA-Z0-9])", "exists")
							.replaceAll("(?<![a-zA-Z0-9])(!=|not\\s*=|NOT\\s*=)(?![a-zA-Z0-9])", "neq")
							.replaceAll("(?<![a-zA-Z0-9])SIM(?![a-zA-Z0-9])", "sim")
							.replaceAll("(?<![a-zA-Z0-9])APPROX(?![a-zA-Z0-9])", "approx")
							.replaceAll("(?<![a-zA-Z0-9])SIMEQ(?![a-zA-Z0-9])", "simeq")
							.replaceAll("(?<![a-zA-Z0-9])(EQUIV|==)(?![a-zA-Z0-9])", "equiv")
							.replaceAll("(?<![a-zA-Z0-9])FORALL(?![a-zA-Z0-9])", "forall")
							.replaceAll("(?<![a-zA-Z0-9])PARTIAL(?![a-zA-Z0-9])", "partial")
							.replaceAll("(?<![a-zA-Z0-9])larrow(?![a-zA-Z0-9])", "leftarrow")
							.replaceAll("(?<![a-zA-Z0-9])rarrow(?![a-zA-Z0-9])", "rightarrow")
							.replaceAll("(?<![a-zA-Z0-9])LARROW(?![a-zA-Z0-9])", "dlarrow")
							.replaceAll("(?<![a-zA-Z0-9])RARROW(?![a-zA-Z0-9])", "drarrow")
							.replaceAll("(?<![a-zA-Z0-9])LRARROW(?![a-zA-Z0-9])", "dlrarrow")
							.replaceAll("(?<![a-zA-Z0-9])vert(?![a-zA-Z0-9])", "divides")
							.replaceAll("(?<![a-zA-Z0-9])VERT(?![a-zA-Z0-9])", "parallel")
							.replaceAll("(?<![a-zA-Z0-9])cdots(?![a-zA-Z0-9])", "dotsaxis")
							.replaceAll("(?<![a-zA-Z0-9])LDOTS(?![a-zA-Z0-9])", "dotslow")
							.replaceAll("(?<![a-zA-Z0-9])VDOTS(?![a-zA-Z0-9])", "dotsvert")
							.replaceAll("(?<![a-zA-Z0-9])DDOTS(?![a-zA-Z0-9])", "dotsdown")
							.replaceAll("(?<![a-zA-Z0-9])TRIANGLED(?![a-zA-Z0-9])", "nabla")
							.replaceAll("(?<![a-zA-Z0-9])SANGLE(?![a-zA-Z0-9])", "%angle")
							.replaceAll("(?<![a-zA-Z0-9])BOT(?![a-zA-Z0-9])", "ortho")
							.replaceAll("(?<![a-zA-Z0-9])hund(?![a-zA-Z0-9])", "%perthousand")
							.replaceAll("(?<![a-zA-Z0-9])TIMES(?![a-zA-Z0-9])", "times")
							.replaceAll("(?<![a-zA-Z0-9])(INT|OINT|DINT|TINT|ODINT|OTINT)(?![a-zA-Z0-9])", "int")
							.replaceAll("(?<![a-zA-Z0-9])(inf|INF)(?![a-zA-Z0-9])", "infinity")
							.replaceAll("(?<![a-zA-Z0-9])ANGSTROM(?![a-zA-Z0-9])", "{circle A}")
							.replaceAll("(?<![a-zA-Z0-9])IMATH(?![a-zA-Z0-9])", "{italic i}")
							.replaceAll("(?<![a-zA-Z0-9])JMATH(?![a-zA-Z0-9])", "{italic j}")
							.replaceAll("(?<![a-zA-Z0-9])(ELL|LITER)(?![a-zA-Z0-9])", "{italic l}")
							.replaceAll("(?<![a-zA-Z0-9])OHM(?![a-zA-Z0-9])", "%OMEGA")
							// 변환불가
							// .replaceAll("LADDER",  "ladder").replaceAll("SLADDER",  "sladder").replaceAll("LONGDIV",  "longdiv").replaceAll("dyad", "").replaceAll("arch",  "")
							// .replaceAll("CASES", "cases")
							// replaceAll("varupsilon", "").replaceAll("varphi", "")
							// .replaceAll("COPROD", "").replaceAll("UPLUS", "").replaceAll("SQSUPSETEQ", "").replaceAll("LLL", "").replaceAll(">>>", "")
							// .replaceAll("BULLET", "").replaceAll("DEG", "").replaceAll("AST", "").replaceAll("STAR", "").replaceAll("BIGCIRC", "")
							// .replaceAll("SQSUBSET", "").replaceAll("SQSUPSET", "").replaceAll("SQSUBSETEQ", "").replaceAll("SQCAP", "").replaceAll("SQCUP", "")
							// .replaceAll("DAGGER", "").replaceAll("DDAGGER", "").replaceAll("LNOT", "").replaceAll("PROPTO", "").replaceAll("XOR", "")
							// .replaceAll("THEREFORE", "").replaceAll("BECAUSE", "").replaceAll("IDENTICAL", "").replaceAll("DOTEQ", "").replaceAll("image", "").replaceAll("REIMAGE", "")
							// .replaceAll("udarrow", "").replaceAll("lrarrow", "").replaceAll("UDARROW", "").replaceAll("UPARROW", "").replaceAll("DOWNARROW", "")
							// .replaceAll("nwarrow", "").replaceAll("searrow", "").replaceAll("nearrow", "").replaceAll("CONG", "")
							// .replaceAll("swarrow", "").replaceAll("hookleft", "").replaceAll("hookright", "").replaceAll("mapsto", "")
							// .replaceAll("TRIANGLE", "").replaceAll("ANGLE", "").replaceAll("MSANGLE", "").replaceAll("prime", "")
							// .replaceAll("ASYMP", "").replaceAll("ISO", "").replaceAll("DIAMOND", "").replaceAll("DSUM", "")
							// .replaceAll("RTANGLE", "").replaceAll("VDASH", "").replaceAll("HLEFT", "").replaceAll("TOP", "").replaceAll("MODELS", "")
							// .replaceAll("LAPLACE", "").replaceAll("CENTIGRADE", "").replaceAll("FAHRENHEIT", "").replaceAll("LSLANT", "").replaceAll("RSLANT", "")
							// .replaceAll("att", "").replaceAll("thou", "").replaceAll("well", "").replaceAll("base", "").replaceAll("benzene", "")
							// 동일. 변환할 필요없음.
							// .replaceAll("<<", "").replaceAll(">>", "").replaceAll("notin", "").replaceAll("uparrow", "").replaceAll("downarrow", "")
							// .replaceAll("acute",  "").replaceAll("bar",  "").replaceAll("grave",  "").replaceAll("vec",  "").replaceAll("dot",  "").replaceAll("ddot", "")
							;

		if (retString.toLowerCase().contains("bigg")) {
			if (retString.matches(".*(BIGG|bigg)\\s*\\/.*")) {
				retString = replacePattern(retString, "(BIGG|bigg)\\s*/\\s*(.*)", "wideslash {$2}");
			} else if (retString.matches(".*(BIGG|bigg)\\s*\\\\\\s*(.*)")) {
				retString = replacePattern(retString, "(BIGG|bigg)\\s*\\\\\\s*(.*)", "widebslash {$2}");
			}
		}
		if (retString.toLowerCase().contains("over")) {
			if (retString.matches(".*([^\\s\\}]+)\\s*(OVER|over)\\s*([^\\{\\s]+).*")) {
				retString = replacePattern(retString, "([^\\s]+)\\s*(OVER|over)\\s*([^\\s]+)", "{$1 over $3} ");
			}
		}
		if (retString.toLowerCase().contains("matrix")) {
			String param2 = retString.replaceAll(".*(MATRIX|matrix)\\s*\\{((\\{.+\\}|.+)+)\\}.*", "$2");
			String newParam2 = param2.replaceAll("#", "##").replaceAll("&", "#");

			if (retString.toLowerCase().contains("bmatrix")) {
				retString = replacePattern(retString, "(BMATRIX|bmatrix)\\s*\\{((\\{.+\\}|.+)+)\\}", "left [ matrix{ "+newParam2+"} right ]");
			} else if (retString.toLowerCase().contains("dmatrix")) {
				retString = replacePattern(retString, "(DMATRIX|dmatrix)\\s*\\{((\\{.+\\}|.+)+)\\}", "left lline matrix{ "+newParam2+"} right rline");
			} else if (retString.toLowerCase().contains("pmatrix")) {
				retString = replacePattern(retString, "(PMATRIX|pmatrix)\\s*\\{((\\{.+\\}|.+)+)\\}", "left ( matrix{ "+newParam2+"} right )");
			} else {
				retString = replacePattern(retString, "(MATRIX|matrix)\\s*\\{((\\{.+\\}|.+)+)\\}", "matrix{ "+newParam2+"} ");
			}
		}
		
		if (retString.toLowerCase().contains("hat")) {
			String param1 = retString.replaceAll("hat\\s*(\\{?.*\\}?)\\s*.*", "$1");
			if (param1.length()>1) {
				retString = replacePattern(retString, "hat\\s*(\\{?.*\\}?)\\s*.*", "widehat {$1}");
			}
		}
		if (retString.toLowerCase().contains("check")) {
			String param1 = retString.replaceAll("check\\s*(\\{?.*\\}?)\\s*.*", "$1");
			if (param1.length()>1) {
				retString = replacePattern(retString, "check\\s*(\\{?.*\\}?)\\s*.*", "widecheck {$1}");
			}
		}
		if (retString.toLowerCase().contains("tilde")) {
			String param1 = retString.replaceAll("tilde\\s*(\\{?.*\\}?)\\s*.*", "$1");
			if (param1.length()>1) {
				retString = replacePattern(retString, "tilde\\s*(\\{?.*\\}?)\\s*.*", "widetilde {$1}");
			}
		}
		if (retString.toLowerCase().contains(" atop ")) {
			retString = replacePattern(retString, "(\\{?\\s*.+\\s*\\}?)\\s+(ATOP|atop)\\s+(\\{?\\s*.+\\s*\\}?)", "binom $1 $3");
		}
		if (retString.toLowerCase().contains("sum")) {
			if (retString.matches(".*(SUM|sum)\\s?\\_\\s*(\\{[^\\}]+\\}|[^\\s]+)\\s*\\^(\\{.+\\}|[^\\s]+)\\s*.*")) {
				retString = replacePattern(retString, "(SUM|sum)\\s?\\_\\s*(\\{[^\\}]+\\}|[^\\s]+)\\s*\\^(\\{.+\\}|[^\\s]+)", "sum from {$2} to {$3} ");
			}
		}
		if (retString.toLowerCase().contains("lim")) {
			if (retString.matches(".*(Lim|lim)\\s?\\_\\s*(\\{[^\\}]+\\}|[^\\s]+)\\s.*")) {
				retString = replacePattern(retString, "(Lim|lim)\\s?\\_\\s*(\\{[^\\}]+\\}|[^\\s]+)", "lim from {$2}");
			}
		}
		if (retString.toLowerCase().contains("color")) {
			if (retString.matches(".*(COLOR|Color|color)\\s*\\{\\s*(\\d+)\\s*\\,\\s*\\d+\\s*\\,\\s*\\d+\\s*\\}.*")) {
				retString = replacePattern(retString, "(COLOR|color)\\s*\\{\\s*(\\d+)\\s*\\,\\s*(\\d+)\\s*\\,\\s*(\\d+)\\s*\\}", "color rgb $2 $3 $4 ");
			}
		}

		return retString;
	}

}
