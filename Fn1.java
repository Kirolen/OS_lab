import java.io.*;
import java.net.*;

public class Fn1 {
    public static void main(String[] args) throws Exception {
        if(args.length<2){ System.err.println("Usage: java Fn1 <port> <x>"); System.exit(1);}
        int port = Integer.parseInt(args[0]);
        int x = Integer.parseInt(args[1]);
        try(ServerSocket server = new ServerSocket(port)){
            Socket socket = server.accept();
            PrintWriter out = new PrintWriter(socket.getOutputStream(),true);

            if(x==5){ System.out.println("Fn1 simulating hang..."); Thread.sleep(20000);} // simulate hang

            double res = x*x;
            out.println(res);
            System.out.println("Fn1 sent: "+res);
            Thread.sleep(500);
        } catch(Exception e){ System.err.println("Fn1 error: "+e.getMessage()); }
    }
}
