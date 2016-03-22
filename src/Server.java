import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Jonathan & Damien
 */
public class Server extends Thread {

    private SSLServerSocket ss;
    private HashMap<String, String> users;
    private HashMap<String, ArrayList<String>> emails;

    // Create a new Server on the default port : 110
    public Server() {
        this(5284);
    }

    public Server(int _port) {
        try {
            ServerSocketFactory context = SSLServerSocketFactory.getDefault();
            ss = (SSLServerSocket)context.createServerSocket(_port);
            String[] cipher = ss.getSupportedCipherSuites();
            List<String> enabledCipher = new ArrayList<>();
            for (String cipher1 : cipher) {
                if (cipher1.contains("_anon_")) {
                    enabledCipher.add(cipher1);
                }
            }
            cipher = new String[enabledCipher.size()];
            for(int i = 0 ; i< enabledCipher.size(); i++) {
                cipher[i] = enabledCipher.get(i);
            }
            ss.setEnabledCipherSuites(cipher);
        } catch (IOException ex) {
            System.err.println("Problem on port : " + _port + " : " + ex.getMessage());
        }
        users = new HashMap<>();
        emails = new HashMap<>();

        // Fill the users and emails
        users.put("john", "doe");
        emails.put("john", new ArrayList<String>());
        for(int i = 1; i < 43; i++)
        {
            emails.get("john").add("From : god\r\nSubject : " + i + "\r\n\r\nMessage number " + i + "\r\n\r\n");
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Socket sock;
                // Waiting for connection
                sock = ss.accept();

                // Create connexion
                Connexion connexion = new Connexion(sock, users, emails);
                connexion.start();

            } catch (IOException ex) {
                System.err.println("Connection failed : " + ex.getMessage());
                break;
            }
        }
    }

    public static void main(String[] args) {
        Server s = new Server();
        s.run();
    }
}
