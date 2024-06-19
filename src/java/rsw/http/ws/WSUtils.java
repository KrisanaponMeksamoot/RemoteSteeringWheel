package rsw.http.ws;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class WSUtils {
    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final MessageDigest md;

    public static final int OP_PERSISTENT = 0;
    public static final int OP_TEXT = 1;
    public static final int OP_BINARY = 2;
    public static final int OP_UnControlled3 = 3;
    public static final int OP_UnControlled4 = 4;
    public static final int OP_UnControlled5 = 5;
    public static final int OP_UnControlled6 = 6;
    public static final int OP_UnControlled = 7;
    public static final int OP_CLOSE = 8;
    public static final int OP_PING = 9;
    public static final int OP_PONG = 10;
    public static final int OP_ControlB = 11;
    public static final int OP_ControlC = 12;
    public static final int OP_ControlD = 13;
    public static final int OP_ControlE = 14;
    public static final int OP_ControlF = 15;

    public static String keyAccept(String key) {
        return new String(Base64.getEncoder().encode(md.digest((key + GUID).getBytes())));
    }

    static {
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ncae) {
            throw new RuntimeException(ncae);
        }
    }

    public static void mask(byte[] data, byte[] key) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (key[i % key.length] ^ data[i]);
        }
    }
}
