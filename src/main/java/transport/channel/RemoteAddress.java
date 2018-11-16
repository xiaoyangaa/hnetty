package transport.channel;

/**
 * @author yangyue
 * @Date 2018/11/14
 * @Description
 */
public class RemoteAddress {

    private String host;

    private int port;

    public RemoteAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public int hashCode() {
        String obj = this.getHost() + this.getPort();
        return obj.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof RemoteAddress && ((RemoteAddress) obj).getHost().equals(this.getHost()) && ((RemoteAddress) obj).getPort() == this.getPort()) {
            return true;
        }
        return false;
    }
}
