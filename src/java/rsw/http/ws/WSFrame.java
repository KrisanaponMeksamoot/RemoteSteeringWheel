package rsw.http.ws;

public class WSFrame {
    public boolean fin;
    public int opcode;
    public byte[] data;
    public boolean isMask;
    public byte[] maskKey;

    public WSFrame() {
    }

    public WSFrame(boolean fin, int opcode, byte[] data) {
        this.fin = fin;
        this.opcode = opcode;
        this.data = data;
        this.isMask = false;
    }

    public WSFrame(boolean fin, int opcode, byte[] data, byte[] maskKey) {
        this(fin, opcode, data);
        this.maskKey = maskKey;
        this.isMask = true;
    }
}
