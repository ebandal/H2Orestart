package HwpDoc.paragraph;

public class ParaText extends Ctrl {
    public String  text;
    public int     startIdx;
    public int     charShapeId;

    public ParaText(String ctrlId, String text, int startIdx) {
        super(ctrlId);
        this.text = text;
        this.startIdx = startIdx;
    }

    public ParaText(String ctrlId, String text, int startIdx, int charShapeId) {
        super(ctrlId);
        this.text = text;
        this.startIdx = startIdx;
        this.charShapeId = charShapeId;
    }

    @Override
    public int getSize() {
        return text==null?0:text.length();
    }

}
