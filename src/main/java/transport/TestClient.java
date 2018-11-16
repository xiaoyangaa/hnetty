package transport;

import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import transport.channel.HRequest;
import transport.channel.RemoteAddress;
import transport.impl.DefaultHClient;
import transport.impl.DefaultHConnector;

import java.net.URI;
import java.util.ArrayList;

/**
 * @author yangyue
 * @Date 2018/11/16
 * @Description
 */
public class TestClient {


    public static void main(String[] args) throws Exception {

        ArrayList<RemoteAddress> addressArrayList = new ArrayList<>();

        addressArrayList.add(new RemoteAddress("127.0.0.1", 80));

        HConnector<NioSocketChannel> hConnector = new DefaultHConnector(1, 100, 100, addressArrayList);

        HClient defaultHClient = new DefaultHClient(hConnector);

        defaultHClient.init();

        Thread.sleep(1000);

//        for (int i = 0; i < 100; i++) {
//            HRequest hRequest = buildRequest(i);
//            System.out.println("excutenum: " + i + "  >>>>>>>");
//            ChannelFuture future = defaultHClient.excute(hRequest);
//        }
    }


    private static HRequest buildRequest(int i) throws Exception {
        URI uri = new URI("http://127.0.0.1:80/test.do?user=yang");
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toASCIIString());
        // 构建http请求
        request.headers().set(HttpHeaders.Names.HOST, "http://127.0.0.1");
        request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        request.headers().set(HttpHeaders.Names.CONTENT_LENGTH, request.content().readableBytes());

        HRequest hRequest = new HRequest();
        hRequest.setRemoteAddress(new RemoteAddress("127.0.0.1", 80));
        hRequest.setRequest(request);
        hRequest.setRequestId(i);
        return hRequest;
    }
}
