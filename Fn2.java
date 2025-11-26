import java.io.*;
import java.net.*;

public class Fn2 {
    public static void main(String[] args) throws Exception {
        if(args.length<2){ System.err.println("Usage: java Fn2 <port> <x>"); System.exit(1);}
        int port = Integer.parseInt(args[0]);
        int x = Integer.parseInt(args[1]);
        try(ServerSocket server = new ServerSocket(port)){
            Socket socket = server.accept();
            PrintWriter out = new PrintWriter(socket.getOutputStream(),true);

            if(x==0){ out.println("ERROR: Division by zero"); return; }

            double res = 15.0/x;
            out.println(res);
            System.out.println("Fn2 sent: "+res);
            Thread.sleep(500);
        } catch(Exception e){ System.err.println("Fn2 error: "+e.getMessage()); }
    }
}
