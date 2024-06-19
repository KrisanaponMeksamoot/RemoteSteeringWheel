package rsw;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.TreeSet;

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

    TreeSet<WsConThread> threads = new TreeSet<>();

    private Server server;

    private Main() throws IOException {
        instance = this;

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
            System.out.printf("%s %s\n", con.getRequest().getMethod(), path);
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

    public static void main(String[] args) {
        try {
            new Main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}