package HwpDoc.paragraph;

public class Ctrl_Character extends Ctrl {
    public CtrlCharType    ctrlChar;

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
