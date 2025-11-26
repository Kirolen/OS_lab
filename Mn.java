import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class Mn {
    private static final int PORT_FN1 = 5001;
    private static final int PORT_FN2 = 5002;
    private static final long TIMEOUT_MS = 5000;

    private SocketChannel channelFn1;
    private SocketChannel channelFn2;
    private Selector selector;

    private Double resultFn1 = null;
    private Double resultFn2 = null;
    private boolean errorFn1 = false;
    private boolean errorFn2 = false;
    private String errorMsgFn1 = null;
    private String errorMsgFn2 = null;

    private long startTime;
    private boolean waitingForInput = false;
    private BufferedReader consoleReader;

    public static void main(String[] args) throws Exception {
        new Mn().run();
    }

    public void run() throws Exception {
        consoleReader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("\nEnter integer x (or 'quit'): ");
            String line = consoleReader.readLine();
            if (line == null || line.trim().equalsIgnoreCase("quit")) break;

            int x;
            try { x = Integer.parseInt(line.trim()); } 
            catch (NumberFormatException e) { System.out.println("Invalid number"); continue; }

            startComputation(x);
        }
    }

    private void startComputation(int x) throws Exception {
        System.out.println("\n--- Starting computation for x=" + x + " ---");

        resultFn1 = resultFn2 = null;
        errorFn1 = errorFn2 = false;
        errorMsgFn1 = errorMsgFn2 = null;

        Process p1 = new ProcessBuilder("java", "Fn1", String.valueOf(PORT_FN1), String.valueOf(x))
                .redirectErrorStream(true).start();
        Process p2 = new ProcessBuilder("java", "Fn2", String.valueOf(PORT_FN2), String.valueOf(x))
                .redirectErrorStream(true).start();

        Thread.sleep(500); // wait for workers to start

        selector = Selector.open();
        channelFn1 = connectWorker(PORT_FN1); if(channelFn1!=null) channelFn1.register(selector, SelectionKey.OP_READ,"Fn1"); else {errorFn1=true; errorMsgFn1="Cannot connect Fn1";}
        channelFn2 = connectWorker(PORT_FN2); if(channelFn2!=null) channelFn2.register(selector, SelectionKey.OP_READ,"Fn2"); else {errorFn2=true; errorMsgFn2="Cannot connect Fn2";}

        startTime = System.currentTimeMillis();
        waitResults();

        if(!errorFn1 && !errorFn2) {
            double sum = resultFn1 + resultFn2;
            System.out.println("RESULT: fn1=" + resultFn1 + ", fn2=" + resultFn2 + ", sum=" + sum);
        } else {
            System.out.println("Computation failed:");
            if(errorFn1) System.out.println("Fn1 error: "+errorMsgFn1);
            if(errorFn2) System.out.println("Fn2 error: "+errorMsgFn2);
        }

        cleanup(p1,p2);
    }

    private SocketChannel connectWorker(int port) {
        for(int i=0;i<5;i++) {
            try{
                SocketChannel ch = SocketChannel.open();
                ch.configureBlocking(false);
                ch.connect(new InetSocketAddress("localhost", port));
                while(!ch.finishConnect()) Thread.sleep(10);
                return ch;
            } catch(Exception e){ try{Thread.sleep(200);}catch(Exception ignored){} }
        }
        return null;
    }

    private void waitResults() throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(1024);

        while(true){
            if((resultFn1!=null||errorFn1)&&(resultFn2!=null||errorFn2)) break;

            long elapsed = System.currentTimeMillis()-startTime;
            if(elapsed>TIMEOUT_MS && !waitingForInput) handleTimeout();

            int ready = selector.select(100);
            if(ready>0){
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while(it.hasNext()){
                    SelectionKey key = it.next(); it.remove();
                    if(!key.isValid()) continue;
                    if(key.isReadable()){
                        SocketChannel ch = (SocketChannel)key.channel();
                        String worker = (String)key.attachment();
                        buf.clear();
                        int n = ch.read(buf);
                        if(n>0){
                            buf.flip();
                            String msg = new String(buf.array(),0,buf.limit()).trim();
                            processWorker(worker,msg);
                        } else if(n==-1){ ch.close(); if(worker.equals("Fn1")) {errorFn1=true; errorMsgFn1="Disconnected";} else {errorFn2=true; errorMsgFn2="Disconnected";}}
                    }
                }
            }
        }
    }

    private void processWorker(String worker, String msg){
        if(msg.startsWith("ERROR:")){
            if(worker.equals("Fn1")) {errorFn1=true; errorMsgFn1=msg.substring(6).trim();}
            else {errorFn2=true; errorMsgFn2=msg.substring(6).trim();}
        } else {
            double val = Double.parseDouble(msg);
            if(worker.equals("Fn1")) {resultFn1=val; System.out.println("Fn1 done: "+val);}
            else {resultFn2=val; System.out.println("Fn2 done: "+val);}
        }
    }

    private void handleTimeout() throws IOException {
        waitingForInput=true;
        System.out.println("\n--- TIMEOUT --- Options:\n1-10s more\n2-indefinite\n3-status\n4-cancel");
        while(true){
            System.out.print("Choice: ");
            String choice = consoleReader.readLine();
            if("1".equals(choice)){ startTime=System.currentTimeMillis(); waitingForInput=false; return;}
            else if("2".equals(choice)){ startTime=Long.MAX_VALUE-TIMEOUT_MS-1000; waitingForInput=false; return;}
            else if("3".equals(choice)){ System.out.println("Fn1: "+(resultFn1!=null?resultFn1:(errorFn1?"Error":"Running..."))+
                    ", Fn2: "+(resultFn2!=null?resultFn2:(errorFn2?"Error":"Running..."))); continue;}
            else if("4".equals(choice)){ if(resultFn1==null&&!errorFn1){errorFn1=true;errorMsgFn1="Cancelled";}
                                        if(resultFn2==null&&!errorFn2){errorFn2=true;errorMsgFn2="Cancelled";}
                                        return;}
        }
    }

    private void cleanup(Process p1, Process p2) {
        try{if(channelFn1!=null) channelFn1.close(); if(channelFn2!=null) channelFn2.close(); if(selector!=null) selector.close();}catch(Exception ignored){}
        if(p1!=null) p1.destroyForcibly();
        if(p2!=null) p2.destroyForcibly();
    }
}
