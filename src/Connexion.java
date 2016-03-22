import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.util.*;

/**
 * @author Jonathan & Damien
 */
public class Connexion extends Thread {

    private final int TIMEOUT = 0; // TimeOut de 1 minute

    private HashMap<String, String> users;
    private HashMap<String, ArrayList<String>> emails;
    private ArrayList<Boolean> deletes;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private BufferedOutputStream outData;

    private State state;

    private enum State {
        AUTHORIZATION,
        WAITING_FOR_PASSWORD,
        TRANSACTION;
    }

    public Connexion(Socket _socket, HashMap<String, String> _users, HashMap<String, ArrayList<String>> _emails) {
        socket = _socket;
        try {
            socket.setSoTimeout(TIMEOUT);
        } catch (SocketException ex) {
            System.out.println("Error socket time-out : " + ex.getMessage());
        }
        users = _users;
        emails = _emails;
    }

    private boolean ok(){
        return send("+OK");
    }

    private boolean ok(String msg){
        return send("+OK " + msg);
    }

    private boolean err(){
        return send("-ERR Invalid");
    }

    private boolean err(String msg){
        return send("-ERR " + msg);
    }

    private boolean send(String msg){
        msg += "\r\n";

        try {
            outData.write(msg.getBytes(), 0, msg.getBytes().length);
            outData.flush();
            System.out.println("> " + msg);
            return true;

        } catch (IOException e) {
            System.out.println("IOException : " + e.getMessage());
            return false;
        }
    }

    private String readLine() throws IOException{
        InputStream input = socket.getInputStream();
        String line = "";
        int reading;

        do {
            reading = input.read();

            if(reading == -1){
                return null;
            }

            line += (char) reading;
        } while(!line.contains("\r\n"));
        return line.replace("\r\n", "");
    }

    private void success(String user) {
        deletes = new ArrayList<>();
        int total = 0;
        for(int i = 0; i < emails.get(user).size(); i++)
        {
            total += emails.get(user).get(i).length();
            deletes.add(false);
        }
        ok(emails.get(user).size() + " " + total + " octets");
        state = State.TRANSACTION;
    }

    @Override
    public void run() {
        try {
            outData = new BufferedOutputStream(socket.getOutputStream());
            Random rand = new Random();
            UUID uuid = UUID.randomUUID();
            String timestamp = uuid.toString().substring(0, rand.nextInt(25 - 15 + 1) + 15);
            timestamp = "<"+timestamp+">";
            ok("Server POP3 ready "+timestamp);

            state = State.AUTHORIZATION;

            String line;
            String user = "";
            while (true) {
                line = readLine();
                System.out.println("< "+line);
                if (line != null) {
                    if(Objects.equals(line, "QUIT"))
                    {
                        try
                        {
                            for(int i = emails.get(user).size() - 1; i >= 0; --i)
                            {
                                if(deletes.get(i))
                                    emails.get(user).remove(i);
                            }
                            // Delete success
                            ok();
                            System.out.println(emails.get(user).size());
                        } catch(Exception e) {
                            // Delete failed
                            err("Delete failed");
                        }
                        break;
                    }

                    String[] lines = line.split(" ");

                    switch (state) {
                        case AUTHORIZATION:
                            switch(lines[0])
                            {
                                case "APOP":
                                    if(lines.length < 3)
                                    {
                                        err("Bad APOP");
                                        break;
                                    }
                                    String password = users.get(lines[1]);
                                    
                                    try
                                    {
                                      password = timestamp + password;
                                      MessageDigest m=MessageDigest.getInstance("MD5");
                                      m.update(password.getBytes(),0,password.length());
                                      password = new BigInteger(1,m.digest()).toString(16);
                                    }
                                    catch(Exception e)
                                    {
                                        err("Error");
                                        break;
                                    }
                                     
                                    if(password != null && (Objects.equals(lines[2], password)))
                                    {
                                        // APOP success
                                        success(user = lines[1]);
                                    }
                                    else
                                    {
                                        err("Bad APOP");
                                    }
                                    break;
                                case "USER":
                                    if(lines.length > 1 && users.containsKey(lines[1]))
                                    {
                                        ok();
                                        state = State.WAITING_FOR_PASSWORD;
                                        user = lines[1];
                                    }
                                    else
                                    {
                                        err("Bad USER");
                                    }
                                    break;
                                default:
                                    err();
                            }
                            break;
                        case TRANSACTION:
                            if(lines.length == 2) {
                                switch(lines[0])
                                {
                                    case "RETR":
                                        try{
                                            int i = Integer.parseInt(lines[1]) - 1;
                                            if(!deletes.get(i))
                                            {
                                                send("+OK "+emails.get(user).get(i).length() + " octets \r\n" + emails.get(user).get(Integer.parseInt(lines[1]) - 1));
                                            }
                                        } catch (Exception e) {
                                            err();
                                        }
                                        break;
                                    case "DELE":
                                        try{
                                            deletes.set(Integer.parseInt(lines[1]), true);
                                            ok();
                                        } catch (Exception e) {
                                            err();
                                        }
                                        break;
                                    default:
                                        err();
                                }
                            }
                            else {
                                err();
                            }
                            break;
                        case WAITING_FOR_PASSWORD:
                            if(lines.length > 1 && Objects.equals(users.get(user), lines[1])) {
                                success(user);
                            }
                            else {
                                err("Bad PASS");
                            }
                            break;
                    }
                } else {
                    err();
                    break;
                }
            }

        } catch (SocketTimeoutException e) {
            System.out.println("Time out : " + e.getMessage());
        } catch (IOException ex) {
            System.out.println("Error : " + ex.getMessage());
        } finally {
            close(in);
            close(out);
            close(outData);
            close(socket);
        }
    }

    /**
     * Close a stream
     *
     * @param stream stream need to be closed
     */
    public void close(Object stream) {
        if (stream == null) {
            return;
        }
        try {
            if (stream instanceof Reader) {
                ((Reader) stream).close();
            } else if (stream instanceof Writer) {
                ((Writer) stream).close();
            } else if (stream instanceof InputStream) {
                ((InputStream) stream).close();
            } else if (stream instanceof OutputStream) {
                ((OutputStream) stream).close();
            } else if (stream instanceof Socket) {
                ((Socket) stream).close();
            } else {
                System.err.println("Unable to close object: " + stream);
            }
        } catch (Exception e) {
            System.err.println("Error closing stream: " + e);
        }
    }
}