package rsw.http.ws;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import rsw.http.ClientConnection;
import rsw.http.HttpRequest;
import rsw.http.HttpResponse;

public class WebsocketHandler {
    ClientConnection con;
    DataInputStream dis;
    DataOutputStream dos;

    int version;

    public WebsocketHandler(ClientConnection con) throws IOException {
        this.con = con;
        this.dis = new DataInputStream(con.getInputStream());
        this.dos = new DataOutputStream(con.getOutputStream());
    }

    public ClientConnection getConnection() {
        return con;
    }

    public void sendHeader() throws IOException {
        HttpRequest req = con.getRequest();
        HttpResponse res = con.getResponse();
        assert isWebsocket(req);
        res.putAttribute("Sec-WebSocket-Accept", WSUtils.keyAccept(req.getAttributes().get("Sec-WebSocket-Key")));
        version = Integer.valueOf(req.getAttributes().get("Sec-WebSocket-Version"));
        res.setStatus("101 Switching Protocols");
        res.putAttribute("Connection", "Upgrade");
        res.putAttribute("Upgrade", "websocket");
        con.sendHeader();
    }

    public static boolean isWebsocket(HttpRequest req) throws IOException {
        if (!req.getMethod().toUpperCase().equals("GET"))
            return false;
        if (!req.getAttributes().get("Connection").equals("Upgrade"))
            return false;
        if (!req.getAttributes().get("Upgrade").equals("websocket"))
            return false;
        if (!req.getAttributes().containsKey("Sec-WebSocket-Key"))
            return false;
        return true;
    }

    public int getVersion() {
        return version;
    }

    public void send(WSFrame packet) throws IOException {
        dos.write((packet.fin ? 0x80 : 0) | packet.opcode);
        int lenSize = packet.data.length > 125 ? (packet.data.length > 65535 ? 2 : 1) : 0;
        dos.write(lenSize == 0 ? packet.data.length : (lenSize == 1 ? 126 : 127));
        if (lenSize > 0) {
            if (lenSize == 1) {
                dos.writeShort(packet.data.length);
            } else {
                dos.writeLong(packet.data.length);
            }
        }
        if (packet.isMask) {
            dos.write(packet.maskKey);
            WSUtils.mask(packet.data, packet.maskKey);
        }
        dos.write(packet.data);
        dos.flush();
    }

    public WSFrame receive() throws IOException {
        WSFrame packet = new WSFrame();
        int b = dis.readByte();
        packet.opcode = b & 0x0F;
        packet.fin = (b & 0x80) == 0x80;
        b = dis.readByte();
        int length = b & 0x7F;
        packet.isMask = (b & 0x80) == 0x80;
        if (length == 126) {
            packet.data = new byte[(int) dis.readShort() & 0xFFFF];
        } else if (length == 127) {
            packet.data = new byte[(int) dis.readLong()];
        } else {
            packet.data = new byte[length];
        }
        if (packet.isMask)
            packet.maskKey = new byte[4];
        dis.readFully(packet.maskKey);
        dis.readFully(packet.data);
        WSUtils.mask(packet.data, packet.maskKey);
        return packet;
    }

    public void close() throws IOException {
        con.close();
    }
}
