package harness;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

import HwpDoc.HwpSection;
import HwpDoc.HwpxFile;
import HwpDoc.IContext;
import HwpDoc.paragraph.Ctrl;
import HwpDoc.paragraph.HwpParagraph;
import HwpDoc.paragraph.ParaText;
import HwpxWriter.HwpxPackage;
import HwpxWriter.WCharPr;
import HwpxWriter.WDocument;
import HwpxWriter.WParaPr;
import HwpxWriter.WParagraph;
import HwpxWriter.WSection;

/**
 * HWPX 저장 0단계 확인: 최소 문서를 쓰고, 우리 HWPX 파서로 다시 읽어 텍스트를 출력한다.
 * 사용법: HwpxSmoke <출력.hwpx>
 */
public class HwpxSmoke {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        WDocument doc = WDocument.createDefault();
        doc.title = "H2Orestart HWPX 저장 시험";

        WCharPr bold = new WCharPr();
        bold.fontRef = doc.charPrList.get(0).fontRef.clone();
        bold.bold = true;
        bold.height = 1400;
        int boldId = doc.addCharPr(bold);

        WParaPr center = new WParaPr();
        center.align = "CENTER";
        int centerId = doc.addParaPr(center);

        WSection sec = new WSection();
        WParagraph title = WParagraph.of("HWPX 저장 시험 문서", boldId);
        title.paraPrId = centerId;
        sec.paragraphs.add(title);
        sec.paragraphs.add(WParagraph.of("가나다라 마바사 ABC abc 123", 0));
        WParagraph mixed = new WParagraph();
        mixed.runs.add(new WParagraph.WRun(0, "보통 글자 "));
        mixed.runs.add(new WParagraph.WRun(boldId, "굵은 글자"));
        mixed.runs.add(new WParagraph.WRun(0, " 다시 보통"));
        sec.paragraphs.add(mixed);
        sec.paragraphs.add(WParagraph.of("탭\t다음\t글자", 0));
        sec.paragraphs.add(WParagraph.of("줄바꿈 앞\n줄바꿈 뒤", 0));
        sec.paragraphs.add(WParagraph.of("특수 문자: & < > \" '", 0));
        sec.paragraphs.add(new WParagraph());
        sec.paragraphs.add(WParagraph.of("마지막 문단", 0));
        doc.sections.add(sec);

        OutputStream os = new FileOutputStream(out);
        try {
            HwpxPackage.write(doc, os);
        } finally {
            os.close();
        }
        System.out.println("written " + out + " (" + out.length() + " bytes)");

        // 우리 파서로 다시 읽기
        final HwpxFile hwpx = new HwpxFile(out);
        hwpx.open(new IContext() {
            @Override public HwpxFile getHwpx() { return hwpx; }
        });
        List<HwpSection> sections = hwpx.getSections();
        System.out.println("sections=" + sections.size() + " charPr=" + hwpx.getDocInfo().charShapeList.size()
                + " paraPr=" + hwpx.getDocInfo().paraShapeList.size());
        for (HwpSection s : sections) {
            for (HwpParagraph p : s.paraList) {
                StringBuilder sb = new StringBuilder();
                if (p.p != null) {
                    for (Ctrl c : p.p) {
                        if (c instanceof ParaText) sb.append(((ParaText) c).text);
                    }
                }
                System.out.println("  [" + p.paraShapeID + "] " + sb.toString().replace("\t", "<TAB>").replace("\n", "<LF>"));
            }
        }
        hwpx.close();
    }
}
