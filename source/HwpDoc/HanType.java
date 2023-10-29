package HwpDoc;

public enum HanType {
    NONE   (0x0),
    HWP    (0x1),
    HWPX   (0x2);

    private int num;
    private HanType(int num) { 
        this.num = num;
    }
}
