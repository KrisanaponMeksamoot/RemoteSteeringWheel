package rsw.http;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class Server {
    ServerSocket ss;
    Thread thread = new Thread(this::run);
    Consumer<ClientConnection> handler = this::defaultHandle;

    public Server(ServerSocket ss) throws IOException {
        this.ss = ss;
    }

    public Server() throws IOException {
        this(new ServerSocket());
    }

    public void start(int port) throws IOException {
        ss.bind(new InetSocketAddress(port));
        thread.start();
    }

    public void stop() throws IOException {
        ss.close();
    }

    public void waitUntilDie() throws InterruptedException {
        thread.join();
    }

    private void run() {
        while (true) {
            try {
                Socket s = ss.accept();
                ClientConnection con = new ClientConnection(this, s);
                handler.accept(con);
            } catch (IOException e) {
                e.printStackTrace();
                if (ss.isClosed())
                    break;
            }
        }
    }

    public void setHandler(Consumer<ClientConnection> handler) {
        if (handler == null)
            this.handler = this::defaultHandle;
        else
            this.handler = handler;
    }

    private void defaultHandle(ClientConnection con) {
        try {
            con.readHeader();
            byte[] data = "<html><head></head><body><h1>Server is started and working!</h1><p>Please set the handler to modify the page.</p></body></html>"
                    .getBytes();
            HttpResponse res = con.getResponse();
            res.setStatus("200 OK");
            res.putAttribute("Content-Type", "text/html");
            res.putAttribute("Content-Length", Integer.toString(data.length));
            con.sendHeader();
            con.getOutputStream().write(data);
            con.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}