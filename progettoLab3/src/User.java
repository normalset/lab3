import java.net.DatagramSocket;
import java.net.Socket;

public class User {
    String username;
    String password;
    Boolean logged;
    Socket socket ;
    int udpPort ;

    public User(String username, String password, Socket socket) {
        this.username = username;
        this.password = password;
        this.socket = socket;
        this.logged = false;
    }
}
