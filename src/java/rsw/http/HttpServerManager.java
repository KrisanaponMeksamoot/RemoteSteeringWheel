package rsw.http;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.function.Consumer;

import javax.net.ssl.SSLSocket;

import rsw.http.ws.WSFrame;
import rsw.http.ws.WSUtils;
import rsw.http.ws.WebsocketHandler;

public class HttpServerManager {
    Server server;

    LinkedList<ConThread> threads = new LinkedList<>();
    // LinkedList<ServerHandler> handlers = new LinkedList<>();
    Consumer<ClientConnection> handler;

    public HttpServerManager() throws IOException {
        this(HttpServerManager::defaultHandleRequest);
    }

    public HttpServerManager(Consumer<ClientConnection> handler) throws IOException {
        server = new Server();
        this.handler = handler;
        server.setHandler(this::handleNewThread);
    }

    public void start(int port) throws IOException {
        server.start(port);
    }

    private void handleNewThread(ClientConnection con) {
        ConThread t = new ConThread(con);
        threads.add(t);
        t.start();
    }

    private static void defaultHandleRequest(ClientConnection con) {
        try {
            if (con.getSocket() instanceof SSLSocket)
                ((SSLSocket) con.getSocket()).startHandshake();
            con.readHeader();
            HttpRequest req = con.getRequest();
            HttpResponse res = con.getResponse();
            String path = req.getURI().getPath();
            byte[] data;
            res.setStatus("200 OK");
            if (path.equals("/") || path.equals("/page.html")) {
                data = readAllBytes(ClassLoader.getSystemResourceAsStream("assets/page.html"));
                res.putAttribute("Content-Type", "text/html");
            } else if (path.equals("/ws") && WebsocketHandler.isWebsocket(req)) {
                WebsocketHandler ws = new WebsocketHandler(con);
                ws.sendHeader();
                ws.send(new WSFrame(true, WSUtils.OP_CLOSE, new byte[] { 0, 0 }));
                ws.close();
                return;
            } else {
                data = "404 Not Found".getBytes();
                res.putAttribute("Content-Type", "text/html");
                res.setStatus("404 NOT FOUND");
            }
            con.getResponse().putAttribute("Content-Length", Integer.toString(data.length));
            con.sendHeader();
            con.getOutputStream().write(data);
            con.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void interruptAll() {
        for (Thread t : threads) {
            t.interrupt();
        }
    }

    public void joinAll() throws InterruptedException {
        for (Thread t : threads) {
            t.join();
        }
    }

    public void closeAll() throws IOException {
        for (ConThread t : threads) {
            t.close();
        }
    }

    public void close() {
        for (ConThread t : threads) {
            try {
                if (!t.getClientConnection().getSocket().isClosed())
                    t.close();
            } catch (IOException e) {
            }
        }
    }

    public void stopServer() throws IOException {
        server.stop();
    }

    public static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1048576];
        int readLen;
        while ((readLen = is.read(buffer)) != -1)
            baos.write(buffer, 0, readLen);
        return baos.toByteArray();
    }

    class ConThread extends Thread {
        ClientConnection con;

        public ConThread(ClientConnection con) {
            this.con = con;
        }

        @Override
        public void run() {
            handler.accept(con);
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
}
