package compare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import HwpDoc.HwpDetectException;
import HwpDoc.HwpDocInfo;
import HwpDoc.HwpFile;
import HwpDoc.HwpSection;
import HwpDoc.HwpxFile;
import HwpDoc.IContext;
import HwpDoc.Exception.CompoundDetectException;
import HwpDoc.Exception.CompoundParseException;
import HwpDoc.Exception.HwpParseException;
import HwpDoc.Exception.NotImplementedException;
import HwpDoc.Exception.OwpmlParseException;
import HwpDoc.HwpElement.HwpRecord_Bullet;
import HwpDoc.HwpElement.HwpRecord_Numbering;
import HwpDoc.HwpElement.HwpRecord_ParaShape;
import HwpDoc.paragraph.Ctrl_SectionDef;
import HwpDoc.paragraph.HwpParagraph;

public class HwpComparer implements IContext {
    private static final Logger log = Logger.getLogger(HwpComparer.class.getName());
    private static final String PATTERN_STRING = "[\\u0000\\u000a\\u000d\\u0018-\\u001f]|[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017].{6}[\\u0001\\u0002-\\u0009\\u000b-\\u000c\\u000e-\\u0017]";
    public static Pattern pattern = Pattern.compile(PATTERN_STRING);

    private static HwpFile hwp = null;
    private static HwpxFile hwpx = null;
    
    public List<ParaNode> loadHwp(String inputFile) throws HwpDetectException, CompoundDetectException, NotImplementedException, 
                                                         IOException, CompoundParseException, DataFormatException, 
                                                         HwpParseException, ParserConfigurationException, SAXException, OwpmlParseException {
        String detectingType = detectHancom(inputFile);
        
        if (detectingType==null) {
            throw new HwpDetectException();
        }
        
        List<HwpSection> sections = null;
        HwpDocInfo docInfo = null;
        switch(detectingType) {
        case "HWP":
            hwp = new HwpFile(inputFile);
            hwp.open();
            sections = hwp.getSections();
            docInfo = hwp.getDocInfo();
            break;
        case "HWPX":
            hwpx = new HwpxFile(inputFile);
            hwpx.open(this);
            sections = hwpx.getSections();
            docInfo = hwpx.getDocInfo();
            break;
        }
        
        for (int i=0; i < docInfo.bulletList.size(); i++) {
            // Bullet ID는 1부터 시작한다.
            CompNumbering.makeCustomBulletStyle(i+1, (HwpRecord_Bullet)docInfo.bulletList.get(i));
        }
        for (int i=0; i < docInfo.numberingList.size(); i++) {
            // Numbering ID는 1부터 시작한다.
            CompNumbering.makeCustomNumberingStyle(i+1, (HwpRecord_Numbering)docInfo.numberingList.get(i));
        }
        
        for (HwpSection section: sections) {
            // 커스톰 PageStyle 생성
            Ctrl_SectionDef secd =  (Ctrl_SectionDef)section.paraList.stream()
                                                            .filter(p -> p.p!=null && p.p.size()>0)
                                                            .flatMap(p -> p.p.stream())
                                                            .filter(c -> (c instanceof Ctrl_SectionDef)).findAny().get();
            CompPage.makeCustomPageStyle(secd);
        }

        // 리턴 자료구조
        List<ParaNode> paraList = new ArrayList<ParaNode>();
        
        int secIndex = 0;
        for (int i=0; i<sections.size(); i++) {
            HwpSection section = sections.get(i);
            CompPage.setSectionIndex(secIndex++);
            String numberingPrefix = "";
            
            for (HwpParagraph para: section.paraList) {
                HwpRecord_ParaShape paraShape = (HwpRecord_ParaShape) docInfo.paraShapeList.get(para.paraShapeID);
                
                boolean showNumberingPrefix = false;
                String numberingStyleName = "";
                HwpRecord_Numbering numberingStyle = null;
                switch(paraShape.headingType) {
                case NONE:
                    break;
                case OUTLINE:
                    numberingStyleName = CompNumbering.getOutlineStyleName();
                    Ctrl_SectionDef secd = CompPage.getCurrentPage();
                    if (secd.outlineNumberingID!=0 && docInfo.numberingList.size() > secd.outlineNumberingID-1) {
                        numberingStyle = (HwpRecord_Numbering)docInfo.numberingList.get(secd.outlineNumberingID-1);
                        numberingPrefix = CompNumbering.getNumberingHead(numberingStyleName, numberingStyle, paraShape.headingLevel);
                        showNumberingPrefix = true;
                    }
                    break;
                case NUMBER:
                    log.finest("번호문단ID="+paraShape.headingIdRef + ",문단수준="+paraShape.headingLevel);
                    if (paraShape.headingIdRef!=0) {
                        numberingStyleName = CompNumbering.numberingStyleNameMap.get((int)paraShape.headingIdRef);
                        if (numberingStyleName!=null) {
                            numberingStyle = (HwpRecord_Numbering)docInfo.numberingList.get((int)paraShape.headingIdRef-1);
                            numberingPrefix = CompNumbering.getNumberingHead(numberingStyleName, numberingStyle, paraShape.headingLevel);
                            showNumberingPrefix = true;
                        }
                    }
                    break;
                case BULLET:
                    log.finest("글머리표문단ID="+paraShape.headingIdRef + ",문단수준="+paraShape.headingLevel);
                    if (paraShape.headingIdRef!=0 && docInfo.bulletList.size() > paraShape.headingIdRef-1) {
                        numberingStyleName = CompNumbering.bulletStyleNameMap.get((int)paraShape.headingIdRef);
                        HwpRecord_Bullet bulletStyle = (HwpRecord_Bullet)docInfo.bulletList.get((int)paraShape.headingIdRef-1);
                        numberingPrefix = Character.toString((char)bulletStyle.bulletChar);
                        showNumberingPrefix = true;
                    }
                    break;
                }
                String paragraph = CompRecurs.getParaString(para);
                
                paraList.add(new ParaNode(numberingPrefix, showNumberingPrefix, paragraph));
            }
        }
        
        return paraList;
    }
    
    public void close() {
        try {
            if (hwp!=null)
                hwp.close();
            if (hwpx!=null)
                hwpx.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static String detectHancom(String inputFile) {
        String detectingType = null;
        
        HwpxFile hwpxTemp = null;
        try {
            hwpxTemp = new HwpxFile(inputFile);
            hwpxTemp.detect();
            detectingType = "HWPX";
            hwpxTemp.close();
        } catch (IOException | HwpDetectException e1) {
            try {
                hwpxTemp.close();
            } catch (IOException e) {
                log.severe(e.getMessage());
            }
            
            HwpFile hwpTemp = null;
            try {
                hwpTemp = new HwpFile(inputFile);
                hwpTemp.detect();
                detectingType = "HWP";
                hwpTemp.close();
            } catch (IOException | HwpDetectException e2) {
                log.info("file detected neither HWPX nor HWP");
                try {
                    hwpTemp.close();
                } catch (IOException e) {
                    log.severe(e.getMessage());
                }
            }
        }
        return detectingType;
    }
    
    private LinkedList<Pair<ParaNode, ParaNode>> getMatched(List<ParaNode> controlList, List<ParaNode> testList) {
        LinkedList<Pair<ParaNode, ParaNode>> matches = new LinkedList<>();
        
        for (int i=0; i< controlList.size(); i++) {
            ParaNode control = controlList.get(i);
            
            if (control.content.equals("")) {
                continue;
            }
            
            if (testList.contains(control)) {   // 동일값이 여러개 있다면 가장 처음에 있는것이 찾아진다.
                matches.add(new Pair<>(control, control));
            }
        }
        return matches;
    }
    
    public List<String> compare(List<ParaNode> controlList, List<ParaNode> testList) {
        List<String> ret = new ArrayList<String>();
        
        LinkedList<Pair<ParaNode, ParaNode>> matched = getMatched(controlList, testList);

        int lastIdxA = 0, lastIdxB = 0;
        for (Pair<ParaNode, ParaNode> pair: matched) {
            ParaNode control = pair.left;
            ParaNode test = pair.right;
            
            int indexA = controlList.indexOf(control);
            if (indexA < lastIdxA) {
                for (int i=lastIdxA+1; i<=controlList.lastIndexOf(control); i++) {
                    if (controlList.get(i).equals(control)) {
                        indexA = i;
                        break;
                    }
                }
            }
            // controlList 에서 unmatched List또는 Map을 구성한다.
            for (int i=lastIdxA+1; i< indexA; i++) {
                ParaNode node = controlList.get(i);
                if (node.content.equals("")==false) {
                    System.out.println("[+] "+ (control.showNumberingHead?control.numberingHead:"") + " " + node.content);
                }
            }
            
            int indexB = testList.indexOf(test);
            if (indexB < lastIdxB) {
                for (int i=lastIdxB+1; i<=testList.lastIndexOf(control); i++) {
                    if (testList.get(i).equals(test)) {
                        indexB = i;
                        break;
                    }
                }
            }
            // testList에서 unmatched List 또는 Map을 구성한다.
            for (int i=lastIdxB+1; i < indexB; i++) {
                ParaNode node = testList.get(i);
                if (node.content.equals("")==false) {
                    System.out.println("[-] "+ (control.showNumberingHead?control.numberingHead:"") + " " + node.content);
                }
            }
            
            System.out.println("[=] "+ (control.showNumberingHead?control.numberingHead:"") + " " + control.content);
            
            lastIdxA = indexA;
            lastIdxB = indexB;
        }
        
        return ret;
    }
    
    private class Pair<L, R>{
        L left;
        R right;
        
        private Pair(L left, R right){
            this.left = left;
            this.right = right;
        }
    }
    
    public static void main(String[] args) {
        
        Logger root = LogManager.getLogManager().getLogger("");
        root.setLevel(Level.OFF);
        for (Handler h : root.getHandlers()) {
            h.setLevel(Level.OFF);
        }
        
        HwpComparer comp = new HwpComparer();
        
        if (args.length==2 && args[0].equals("-print")) {               // Hwp 내용 출력
            String inputFile = args[1];
            try {
                List<ParaNode> nodes = comp.loadHwp(inputFile);
                for (ParaNode paragraph: nodes) {
                    if (paragraph.content.equals("")==false) {
                        System.out.println((paragraph.showNumberingHead?String.format("%-5s",paragraph.numberingHead):"     ") + paragraph.content);
                    }
                }
            } catch (HwpDetectException | CompoundDetectException | NotImplementedException | IOException | 
                     CompoundParseException | DataFormatException | HwpParseException | 
                     ParserConfigurationException | SAXException | OwpmlParseException e)  {
                e.printStackTrace();
            }
            return;
        } else if (args.length==3 && args[0].equals("-diff")) {         // Hwp 내용 비교
            String inputFile1 = args[1];
            String inputFile2 = args[2];
            
            try {
                List<ParaNode> compare1 = comp.loadHwp(inputFile1);
                List<ParaNode> compare2 = comp.loadHwp(inputFile2);
                List<String> compared = comp.compare(compare1, compare2);
                for (String line: compared) {
                    System.out.println(line);
                }
            } catch (HwpDetectException | CompoundDetectException | NotImplementedException | IOException |
                     CompoundParseException | DataFormatException | HwpParseException | 
                     ParserConfigurationException | SAXException | OwpmlParseException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Hwp File compare tool ver 0.1  created by heesu.ban@k2web.co.kr");
            System.out.println("Usage #1 (compare hwp files) : java -jar HwpComparer.jar -diff hwpfile1 hwpfile2");
            System.out.println("Usage #2 (print hwp content) : java -jar HwpComparer.jar -print hwpfile");
        }
        comp.close();
    }

	@Override
	public HwpxFile getHwpx() {
		return hwpx;
	}
}
