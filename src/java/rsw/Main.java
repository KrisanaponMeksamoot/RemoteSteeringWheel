package rsw;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashSet;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import rsw.http.ClientConnection;
import rsw.http.HttpRequest;
import rsw.http.HttpResponse;
import rsw.http.Server;
import rsw.http.ws.WSFrame;
import rsw.http.ws.WSUtils;
import rsw.http.ws.WebsocketHandler;

public class Main {
    static Main instance;

    ServerSocket ss;

    HashSet<WsConThread> threads = new HashSet<>();

    private Server server;

    private Robot robot;

    private double mul = -2;

    private Main() throws IOException, AWTException {
        instance = this;

        robot = new Robot();

        File f_cert = File.createTempFile("krisgroup2024-subroot-test1-localhost", "pfx");

        try (InputStream is = ClassLoader.getSystemResourceAsStream("krisgroup2024-subroot-test1-localhost.pfx")) {
            FileOutputStream fos = new FileOutputStream(f_cert);
            is.transferTo(fos);
            fos.close();
        }

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLSv1.3");
            String algorithm = KeyManagerFactory.getDefaultAlgorithm();
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
            char[] password = "12345678".toCharArray();
            KeyStore ks = KeyStore.getInstance(f_cert, password);
            keyManagerFactory.init(ks, password);
            trustManagerFactory.init(ks);
            sslContext.init(keyManagerFactory.getKeyManagers(),
                    trustManagerFactory.getTrustManagers(), new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | UnrecoverableKeyException
                | KeyManagementException e) {
            throw new RuntimeException(e);
        }

        ss = sslContext.getServerSocketFactory().createServerSocket();

        server = new Server(ss);

        server.setHandler(this::defaultHandleRequest);

        server.start(8443);
    }

    private void defaultHandleRequest(ClientConnection con) {
        try {
            if (con.getSocket() instanceof SSLSocket)
                ((SSLSocket) con.getSocket()).startHandshake();
            con.readHeader();
            HttpRequest req = con.getRequest();
            HttpResponse res = con.getResponse();
            String path = req.getURI().getPath();
            System.out.printf("%s : %s %s\n", con.getSocket().getRemoteSocketAddress().toString(),
                    con.getRequest().getMethod(),
                    path);
            InputStream data;
            res.setStatus("200 OK");
            if (path.equals("/") || path.equals("/index.html")) {
                data = ClassLoader.getSystemResourceAsStream("dist/index.html");
                res.putAttribute("Content-Type", "text/html");
            } else if (path.equals("/script.js")) {
                data = ClassLoader.getSystemResourceAsStream("dist/script.js");
                res.putAttribute("Content-Type", "text/javascipt");
            } else if (path.equals("/ws") && WebsocketHandler.isWebsocket(req)) {
                new WsConThread(con).start();
                return;
            } else {
                data = new ByteArrayInputStream("404 Not Found".getBytes());
                res.putAttribute("Content-Type", "text/html");
                res.setStatus("404 NOT FOUND");
            }
            con.getResponse().putAttribute("Content-Length", Integer.toString(data.available()));
            con.sendHeader();
            data.transferTo(con.getOutputStream());
            con.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class WsConThread extends Thread {
        ClientConnection con;

        public WsConThread(ClientConnection con) {
            this.con = con;
            threads.add(this);
        }

        @Override
        public void run() {
            try {
                WebsocketHandler ws = new WebsocketHandler(con);
                ws.sendHeader();
                loop: while (true) {
                    WSFrame frame = ws.receive();
                    switch (frame.opcode) {
                        case WSUtils.OP_CLOSE:
                            break loop;
                        case WSUtils.OP_BINARY: {
                            processPacket(frame.data);
                        }
                            break;
                        case WSUtils.OP_PING: {
                            ws.send(new WSFrame(true, WSUtils.OP_PONG, frame.data));
                        }
                            break;
                    }
                }
                ws.send(new WSFrame(true, WSUtils.OP_CLOSE, new byte[] { 0, 0 }));
                ws.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            threads.remove(Thread.currentThread());
        }

        public ClientConnection getClientConnection() {
            return con;
        }

        public void close() throws IOException {
            con.close();
            threads.remove(Thread.currentThread());
        }
    }

    void processPacket(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int p_id = buf.getInt();
        switch (p_id) {
            case 1: { // reset
                robot.keyRelease(KeyEvent.VK_W);
                robot.keyRelease(KeyEvent.VK_S);
                System.out.println("Reset");
            }
                break;
            case 2: { // ypr
                double width = Toolkit.getDefaultToolkit().getScreenSize().getWidth();
                float roll = buf.getFloat(12);
                // System.out.printf("Roll: %.2f : %f\n", roll, (roll / Math.PI * mul + 1) / 2);
                robot.mouseMove((int) ((roll / Math.PI * mul + 1) / 2 * width),
                        (int) MouseInfo.getPointerInfo().getLocation().getY());
            }
                break;
            case 3: { // key
                int key = buf.getInt();
                byte flag = buf.get();
                if (key == 87 || key == 83) {
                    System.out.printf("Key %s %s\n", (key == 87) ? "W" : "S", flag == 1 ? "pressed" : "released");
                    int keycode = key == 87 ? KeyEvent.VK_W : KeyEvent.VK_S;
                    if (flag == 1) {
                        robot.keyPress(keycode);
                    } else {
                        robot.keyRelease(keycode);
                    }
                }
            }
                break;
            default:
                System.err.printf("Known packet: %d\n", p_id);
                break;
        }
    }

    public static void main(String[] args) {
        try {
            new Main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}