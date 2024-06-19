package rsw.http;

import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    private String version = "http/1.1";
    private String status;
    private Map<String, String> attributes;

    public HttpResponse() {
        this.attributes = new HashMap<>();
    }

    protected HttpResponse(String version, String status, Map<String, String> attributes) {
        this.version = version;
        this.status = status;
        this.attributes = attributes;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public void putAttributes(Map<String, String> attributes) {
        this.attributes.putAll(attributes);
    }

    public void putAttribute(String key, String value) {
        this.attributes.put(key, value);
    }
}
