package org.lsfn.starship;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.lsfn.starship.ConsoleServer.ServerStatus;
import org.lsfn.starship.NebulaConnection.ConnectionStatus;


public class Starship {

    private ConsoleServer consoleServer;
    private NebulaConnection nebulaConnection;
    private MessageHandler messageHandler;
    private boolean keepGoing;
    
    public Starship() {
        // TODO make sure ConsoleServer and NebulaConnection can do multiple runs without needing a new one of them. 
        this.consoleServer = new ConsoleServer();
        this.nebulaConnection = new NebulaConnection();
        this.messageHandler = new MessageHandler(this.consoleServer, this.nebulaConnection);
        this.keepGoing = true;
    }
    
    private void startConsoleServer(int port) {
        if(port == -1) {
            this.consoleServer.listen();
        } else {
            this.consoleServer.listen(port);
        }
        this.consoleServer.start();
        this.messageHandler.start();
    }
    
    private void startNebulaClient(String host, Integer port) {
        if(this.nebulaConnection.getConnectionStatus() == ConnectionStatus.DISCONNECTED) {
            System.out.println("Connecting...");
            ConnectionStatus status = ConnectionStatus.DISCONNECTED;
            if(host == null || port == null) {
                status = this.nebulaConnection.connect();
            } else {
                status = this.nebulaConnection.connect(host, port);
            }
            if(status == ConnectionStatus.CONNECTED) {
                this.nebulaConnection.start();
                System.out.println("Connected.");
            } else {
                System.out.println("Connection failed.");
            }
        }
    }

    private void stopNebulaClient() {
        if(this.nebulaConnection.getConnectionStatus() == ConnectionStatus.CONNECTED) {
            this.nebulaConnection.disconnect();
        }
        System.out.println("Disconnected.");
    }
    
    private void printHelp() {
        System.out.println("Starship commands:");
        System.out.println("\thelp                  : print this help text.");
        System.out.println("\tlisten                : opens the console server on the default port.");
        System.out.println("\tconnect <host> <port> : connects to the Nebula on the given host and port.");
        System.out.println("\tconnect               : connects to the Nebula on the default host and port.");
        System.out.println("\tdisconnect            : disconnects from the Nebula if connected.");
        System.out.println("\texit                  : end this program.");
    }
    
    private void processCommand(String commandStr) {
        String[] commandParts = commandStr.split(" ");
         
        if(commandParts[0].equals("listen")) {
            if(commandParts.length >= 2) {
                startConsoleServer(Integer.parseInt(commandParts[1]));
            } else {
                startConsoleServer(-1);
            }
        } else if(commandParts[0].equals("connect")) {
            if(commandParts.length == 3) {
                startNebulaClient(commandParts[1], Integer.parseInt(commandParts[2]));
            } else if(commandParts.length == 1) {
                startNebulaClient(null, null);
            }
        } else if(commandParts[0].equals("disconnect")) {
            stopNebulaClient();
        } else if(commandParts[0].equals("exit")) {
            this.keepGoing = false;
        } else if(commandParts[0].equals("help")) {
            printHelp();
        } else {
            System.out.println("You're spouting gibberish. Please try English.");
        }
    }
    
    public void run(String[] args) {
        printHelp();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while(this.keepGoing) {
            try {
                processCommand(reader.readLine());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // Close up the threads
        if(consoleServer.getListenStatus() == ServerStatus.OPEN) {
            consoleServer.shutDown();
        }
        if(consoleServer.isAlive()) {
            try {
                consoleServer.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        Starship starship = new Starship();
        starship.run(args);
    }

}
