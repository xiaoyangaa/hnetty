package transport.channel;

import io.netty.handler.codec.http.HttpRequest;

/**
 * @author yangyue
 * @Date 2018/11/15
 * @Description
 */
public class HRequest {

    private HttpRequest request;

    private int requestId;


    private RemoteAddress remoteAddress;

    public HttpRequest getRequest() {
        return request;
    }

    public void setRequest(HttpRequest request) {
        this.request = request;
    }

    public RemoteAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(RemoteAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }
}
