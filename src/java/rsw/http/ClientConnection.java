package rsw.http;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ClientConnection {

    private Server server;
    private Socket s;
    private HttpRequest request;
    private HttpResponse response = new HttpResponse();

    protected ClientConnection(Server server, Socket s) {
        this.server = server;
        this.s = s;
    }

    public Socket getSocket() {
        return s;
    }

    public InputStream getInputStream() throws IOException {
        return s.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return s.getOutputStream();
    }

    public void readHeader() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(getInputStream()));
        Scanner headReader = new Scanner(reader.readLine());
        String method = headReader.next();
        String path = headReader.next();
        String version = headReader.next();
        String line;
        String host = "0.0.0.0";
        HashMap<String, String> attributes = new HashMap<>();
        while (!(line = reader.readLine()).isEmpty()) {
            int sep = line.indexOf(": ");
            String key = line.substring(0, sep);
            String value = line.substring(sep + 2);
            attributes.put(key, value);
            if (key.equals("Host"))
                host = value;
        }
        try {
            this.request = new HttpRequest(method, version, new URI(String.format("http://%s%s", host, path)),
                    attributes);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public HttpRequest getRequest() {
        return request;
    }

    public Server getServer() {
        return server;
    }

    public void sendHeader() throws IOException {
        PrintStream ps = new PrintStream(getOutputStream());
        ps.println(String.format("%s %s", response.getVersion(), response.getStatus()));
        for (Map.Entry<String, String> attr : response.getAttributes().entrySet()) {
            ps.println(String.format("%s: %s", attr.getKey(), attr.getValue()));
        }
        ps.println();
    }

    public HttpResponse getResponse() {
        return response;
    }

    public void close() throws IOException {
        s.close();
    }
}
