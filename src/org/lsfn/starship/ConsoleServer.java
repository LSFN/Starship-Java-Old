package org.lsfn.starship;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.lsfn.starship.ConsoleListener.ListenerStatus;
import org.lsfn.starship.STS.STSdown;
import org.lsfn.starship.STS.STSup;

/**
 * Creates a listener for each connection.
 * Each connection assigned unique ID.
 * ID can be used to receive messages from and to send to a specific console.
 * @author Lukeus_Maximus
 *
 */
public class ConsoleServer extends Thread {
    
    private static final Integer defaultPort = 39460;
    private static final Integer pollWait = 50;
    
    private ServerSocket consoleServer;
    private Map<UUID, ConsoleListener> listeners;
    private Map<UUID, List<STSup>> buffers;
    private List<UUID> connectedConsoles;
    private List<UUID> disconnectedConsoles;
    
    public enum ServerStatus {
        CLOSED,
        OPEN
    }
    private ServerStatus serverStatus;
    
    public ConsoleServer() {
        clearServer();
        serverStatus = ServerStatus.CLOSED;
    }
    
    public void clearServer() {
        consoleServer = null;
        listeners = null;
        buffers = null;
        connectedConsoles = null;
        disconnectedConsoles = null;
    }
    
    public ServerStatus getListenStatus() {
        return serverStatus;
    }
    
    public ServerStatus listen() {
        return this.listen(defaultPort);
    }
    
    public ServerStatus listen(int port) {
        if(this.serverStatus == ServerStatus.CLOSED) {
            try {
                this.consoleServer = new ServerSocket(port);
                this.consoleServer.setSoTimeout(pollWait);
                this.listeners = new HashMap<UUID, ConsoleListener>();
                this.buffers = new HashMap<UUID, List<STSup>>();
                this.connectedConsoles = new ArrayList<UUID>();
                this.disconnectedConsoles = new ArrayList<UUID>();
                this.serverStatus = ServerStatus.OPEN;
                System.out.println("Listening on port " + this.consoleServer.getLocalPort());
            } catch (IOException e) {
                e.printStackTrace();
                clearServer();
                this.serverStatus = ServerStatus.CLOSED;
            }
        }
        return this.serverStatus;
    }
    
    /**
     * Returns a list of the consoles that have connected since last polled.
     * @return List of consoles that have connected.
     */
    public synchronized List<UUID> getConnectedConsoles() {
        List<UUID> result = new ArrayList<UUID>(this.connectedConsoles);
        this.connectedConsoles.clear();
        return result;
    }
    
    private synchronized void addConnectedConsole(UUID id) {
        this.connectedConsoles.add(id);
    }
    
    /**
     * Returns a list of the consoles that have disconnected since last polled.
     * @return List of consoles that have disconnected.
     */
    public synchronized List<UUID> getDisconnectedConsoles() {
        List<UUID> result = new ArrayList<UUID>(this.disconnectedConsoles);
        this.disconnectedConsoles.clear();
        return result;
    }
    
    private synchronized void addDisconnectedConsole(UUID id) {
        this.disconnectedConsoles.add(id);
    }
    
    public synchronized Map<UUID, List<STSup>> receiveMessagesFromConsoles() {
        Map<UUID, List<STSup>> result = this.buffers;
        
        this.buffers = new HashMap<UUID, List<STSup>>();
        for(UUID id : result.keySet()) {
            this.buffers.put(id, new ArrayList<STSup>());
        }
        
        return result;
    }
    
    private synchronized void addMessagesToBuffer(UUID id, List<STSup> upMessages) {
        this.buffers.get(id).addAll(upMessages);
    }
    
    public void disconnectConsole(UUID id) {
        if(this.listeners.containsKey(id)) {
            this.listeners.get(id).disconnect();
        }
    }
    
    public void sendMessageToConsole(UUID id, STSdown downMessage) {
        ConsoleListener listener = getListener(id);
        if(listener != null) {
            listener.sendMessageToConsole(downMessage);
        }
    }
    
    public void sendMessageToAllConsoles(STSdown downMessage) {
        Set<UUID> ids = getListenerIDs();
        for(UUID id: ids) {
            ConsoleListener listener = getListener(id);
            if(listener != null) {
                listener.sendMessageToConsole(downMessage);
            }
        }
    }
    
    private synchronized void addListener(UUID id, ConsoleListener listener) {
        this.listeners.put(id, listener);
        this.buffers.put(id, new ArrayList<STSup>());
    }
    
    private synchronized void removeListener(UUID id) {
        this.listeners.remove(id);
        this.buffers.remove(id);
    }
    
    private synchronized ConsoleListener getListener(UUID id) {
        return this.listeners.get(id);
    }
    
    private synchronized Set<UUID> getListenerIDs() {
        return new HashSet<UUID>(this.listeners.keySet());
    }
    
    public ServerStatus shutDown() {
        this.serverStatus = ServerStatus.CLOSED;
        try {
            this.consoleServer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this.serverStatus;
    }
    
    private void internalShutDown() {
        if(!this.consoleServer.isClosed()) {
            try {
                this.consoleServer.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        for(UUID id : getListenerIDs()) {
            getListener(id).disconnect();
        }
        clearServer();
    }
    
    @Override
    public void run() {
        while(this.serverStatus == ServerStatus.OPEN) {
            for(UUID id : getListenerIDs()) {
                ConsoleListener listener = getListener(id);
                List<STSup> upMessages = listener.receiveMessagesFromConsole();
                addMessagesToBuffer(id, upMessages);
                if(listener.getListenerStatus() == ListenerStatus.DISCONNECTED) {
                    System.out.println("Console " + id.toString() + " disconnected.");
                    removeListener(id);
                    addDisconnectedConsole(id);
                }
            }
            try {
                UUID id = UUID.randomUUID();
                Socket consoleSocket = this.consoleServer.accept();
                addListener(id, new ConsoleListener(consoleSocket));
                addConnectedConsole(id);
                System.out.println("New Console " + id.toString() + " connected from " + consoleSocket.getInetAddress());
            } catch (SocketTimeoutException e) {
                // Timeouts are normal, do nothing
            } catch (SocketException e) {
                // If the server is closed, it closed because we asked it to.
                if(this.serverStatus == ServerStatus.OPEN) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                // Shutdown if anything else goes wrong
                this.serverStatus = ServerStatus.CLOSED;
            }
        }
        internalShutDown();
    }

}
