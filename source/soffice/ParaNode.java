package soffice;

public class ParaNode {
    String numberingHead;
    boolean showNumberingHead;
    String content;
    
    public ParaNode(String head, boolean show, String content) {
        this.numberingHead = head;
        this.showNumberingHead = show;
        this.content = content;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((content == null) ? 0 : content.hashCode());
        result = prime * result + ((numberingHead == null) ? 0 : numberingHead.hashCode());
        result = prime * result + (showNumberingHead ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParaNode other = (ParaNode) obj;
        if (content == null) {
            if (other.content != null)
                return false;
        } else if (!content.equals(other.content))
            return false;
        if (numberingHead == null) {
            if (other.numberingHead != null)
                return false;
        } else if (!numberingHead.equals(other.numberingHead))
            return false;
        if (showNumberingHead != other.showNumberingHead)
            return false;
        return true;
    }


}
