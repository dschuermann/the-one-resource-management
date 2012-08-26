/* 
 * Copyright 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;

/**
 * Resource Management (Forked from PingApplication)
 * 
 * The corresponding <code>ResourceManagementAppReporter</code> class can be
 * used to record information about the application behavior.
 * 
 * IMPORTANT: For this application to work, additional changes are needed in
 * ActiveRouter.dropExpiredMessage()!
 * 
 * @see ResourceManagementAppReporter, ResourceManagementBuffer, ActiveRouter
 * @author Dominik Schürmann
 */
public class ResourceManagementApplication extends Application {

    /**
     * Debug config
     * 
     * 0: deactivated, 1: infos when starting, 2: warning, 3: important, 4:
     * debug
     */
    public static int DEBUG = 3;

    public static void logDebug(String message) {
        if (DEBUG >= 4) {
            System.out.println(message);
        }
    }

    public static void logImportant(String message) {
        if (DEBUG >= 3) {
            System.out.println(message);
        }
    }

    public static void logWarning(String message) {
        if (DEBUG >= 2) {
            System.out.println(message);
        }
    }

    public static void logInfo(String message) {
        if (DEBUG >= 1) {
            System.out.println(message);
        }
    }

    /** Run in passive mode - don't generate requests but respond */
    public static final String PASSIVE = "passive";
    /** Request generation interval */
    public static final String INTERVAL = "interval";
    /** Request generation interval of res hogs */
    public static final String INTERVAL_RES_HOGS = "intervalResHogs";
    /** Request interval offset - avoids synchronization of request sending */
    public static final String OFFSET = "offset";
    /** Destination address range - inclusive lower, exclusive upper */
    public static final String DEST_RANGE = "destinationRange";
    /** Seed for the app's random number generator */
    public static final String SEED = "seed";
    /** Size of the request message */
    public static final String REQUEST_MIN_SIZE = "requestMinSize";
    public static final String REQUEST_MAX_SIZE = "requestMaxSize";

    /** Size of the response message */
    public static final String RESPONSE_MIN_SIZE = "responseMinSize";
    public static final String RESPONSE_MAX_SIZE = "responseMaxSize";

    /** Size of the unidirectional message */
    public static final String NORMAL_MIN_SIZE = "unidirectionalMinSize";
    public static final String NORMAL_MAX_SIZE = "unidirectionalMaxSize";

    /** Size of buffers */
    public static final String CLIENT_BUFFER_SIZE = "clientBufferSize";
    public static final String SERVER_BUFFER_SIZE = "serverBufferSize";

    /** percentage of servers */
    public static final String PERCENTAGE_OF_SERVERS = "percentageOfServers";

    /** percentage of resource hogs */
    public static final String PERCENTAGE_OF_RES_HOGS = "percentageOfResHogs";

    /** probability to send request to server or unidirectional to other clients */
    public static final String PROBABILITY_TO_SEND_REQUEST = "probabilityToSendRequest";

    /** distinguish between request and response */
    public static final String SIMULATE_PROXY_SIGNATURES = "simulateProxySignatures";

    /** Application ID */
    public static final String APP_ID = "fi.tkk.netlab.ResourceManagementApplication";

    // Private vars
    private double lastRequest = 0;
    private double lastRequestResHogs = 0;

    private double interval = 500;
    private double intervalResHogs = 500;

    private boolean passive = false;
    private int seed = 0;

    private int destMin = 0;
    private int destMax = 1;

    private int requestMinSize = 1;
    private int requestMaxSize = 1;
    private int responseMinSize = 1;
    private int responseMaxSize = 1;
    private int unidirectionalMinSize = 1;
    private int unidirectionalMaxSize = 1;

    private int clientBufferSize = 1;
    private int serverBufferSize = 1;

    private boolean simulateProxySignatures = false;

    private int percentageOfServers = 5;
    private int percentageOfResHogs = 5;
    private int probabilityToSendRequest = 80;

    private Random rng;

    private int numberOfServers;
    private ArrayList<Integer> serverNodes;

    private int numberOfResHogs;
    private ArrayList<Integer> resHogs;

    private ResourceManagementBuffer[] buffer;

    /**
     * Creates a new request application with the given settings.
     * 
     * @param s
     *            Settings to use for initializing the application.
     */
    public ResourceManagementApplication(Settings s) {
        if (s.contains(PASSIVE)) {
            this.passive = s.getBoolean(PASSIVE);
        }
        if (s.contains(INTERVAL)) {
            this.interval = s.getDouble(INTERVAL);
        }
        if (s.contains(INTERVAL_RES_HOGS)) {
            this.intervalResHogs = s.getDouble(INTERVAL_RES_HOGS);
        }
        if (s.contains(OFFSET)) {
            this.lastRequest = s.getDouble(OFFSET);
        }
        if (s.contains(SEED)) {
            this.seed = s.getInt(SEED);
        }
        if (s.contains(REQUEST_MIN_SIZE)) {
            this.requestMinSize = s.getInt(REQUEST_MIN_SIZE);
        }
        if (s.contains(REQUEST_MAX_SIZE)) {
            this.requestMaxSize = s.getInt(REQUEST_MAX_SIZE);
        }
        if (s.contains(RESPONSE_MIN_SIZE)) {
            this.responseMinSize = s.getInt(RESPONSE_MIN_SIZE);
        }
        if (s.contains(RESPONSE_MAX_SIZE)) {
            this.responseMaxSize = s.getInt(RESPONSE_MAX_SIZE);
        }
        if (s.contains(NORMAL_MIN_SIZE)) {
            this.unidirectionalMinSize = s.getInt(NORMAL_MIN_SIZE);
        }
        if (s.contains(NORMAL_MAX_SIZE)) {
            this.unidirectionalMaxSize = s.getInt(NORMAL_MAX_SIZE);
        }
        if (s.contains(DEST_RANGE)) {
            int[] destination = s.getCsvInts(DEST_RANGE, 2);
            this.destMin = destination[0];
            this.destMax = destination[1];
        }

        if (s.contains(PERCENTAGE_OF_SERVERS)) {
            this.percentageOfServers = s.getInt(PERCENTAGE_OF_SERVERS);
        }

        if (s.contains(PERCENTAGE_OF_RES_HOGS)) {
            this.percentageOfResHogs = s.getInt(PERCENTAGE_OF_RES_HOGS);
        }

        if (s.contains(PROBABILITY_TO_SEND_REQUEST)) {
            this.probabilityToSendRequest = s
                    .getInt(PROBABILITY_TO_SEND_REQUEST);
        }

        if (s.contains(CLIENT_BUFFER_SIZE)) {
            this.clientBufferSize = s.getInt(CLIENT_BUFFER_SIZE);
        }
        if (s.contains(SERVER_BUFFER_SIZE)) {
            this.serverBufferSize = s.getInt(SERVER_BUFFER_SIZE);
        }

        if (s.contains(SIMULATE_PROXY_SIGNATURES)) {
            this.simulateProxySignatures = s
                    .getBoolean(SIMULATE_PROXY_SIGNATURES);
        }

        rng = new Random(this.seed);
        super.setAppID(APP_ID);

        initServers();

        initResHogs();

        initBuffer();
    }

    /**
     * Init server list
     */
    private void initServers() {
        double percentage = percentageOfServers / (double) 100;
        numberOfServers = (int) ((double) this.destMax * percentage);
        logInfo("Number of servers: " + numberOfServers);

        // select servers
        serverNodes = new ArrayList<Integer>();
        for (int i = 0; i < numberOfServers; i++) {

            // choose random server previously not in list
            int randomAddress = getRandomHostAddress();
            while (serverNodes.contains(randomAddress)) {
                randomAddress = getRandomHostAddress();
            }

            // add it
            serverNodes.add(randomAddress);
        }

        logInfo("Randomly chosen server nodes: " + serverNodes);
    }

    /**
     * Init resource hogs
     */
    private void initResHogs() {
        double percentage = percentageOfResHogs / (double) 100;
        numberOfResHogs = (int) ((double) this.destMax * percentage);
        logInfo("Number of resource hogs: " + numberOfResHogs);

        // select servers
        resHogs = new ArrayList<Integer>();
        for (int i = 0; i < numberOfResHogs; i++) {

            // choose random server previously not in list
            int randomAddress = getRandomHostAddress();
            while ((serverNodes.contains(randomAddress))
                    || (resHogs.contains(randomAddress))) {
                randomAddress = getRandomHostAddress();
            }

            // add it
            resHogs.add(randomAddress);
        }

        logInfo("Randomly chosen resource hogs: " + resHogs);
    }

    /**
     * Init empty buffers per node
     */
    private void initBuffer() {
        buffer = new ResourceManagementBuffer[destMax];
        // init buffer array
        for (int i = 0; i < buffer.length; i++) {

            // set buffer size based on type
            int size = -1;
            if (serverNodes.contains(i)) {
                size = serverBufferSize;
            } else {
                size = clientBufferSize;
            }

            // init this buffer
            buffer[i] = new ResourceManagementBuffer(size);
        }
    }

    /**
     * Copy-constructor
     * 
     * @param a
     */
    public ResourceManagementApplication(ResourceManagementApplication a) {
        super(a);
        this.lastRequest = a.getLastRequest();
        this.interval = a.getInterval();
        this.passive = a.isPassive();
        this.destMax = a.getDestMax();
        this.destMin = a.getDestMin();
        this.seed = a.getSeed();
        this.responseMinSize = a.getResponseMinSize();
        this.responseMaxSize = a.getResponseMaxSize();

        this.requestMinSize = a.getRequestMinSize();
        this.requestMaxSize = a.getRequestMaxSize();

        this.unidirectionalMinSize = a.getUnidirectionalMinSize();
        this.unidirectionalMaxSize = a.getUnidirectionalMaxSize();

        this.serverNodes = a.getServerNodes();
        this.numberOfServers = a.getNumberOfServers();
        this.buffer = a.getBuffer();

        this.clientBufferSize = a.getClientBufferSize();
        this.serverBufferSize = a.getServerBufferSize();

        this.simulateProxySignatures = a.isWithProxySignatures();

        this.percentageOfServers = a.getPercentageOfServers();
        this.probabilityToSendRequest = a.getProbabilityToSendRequest();

        this.intervalResHogs = a.getIntervalResHogs();
        this.numberOfResHogs = a.getNumberOfResHogs();
        this.percentageOfResHogs = a.getPercentageOfResHogs();
        this.resHogs = a.getResHogs();
        this.lastRequestResHogs = a.getLastRequestResHog();

        this.rng = new Random(this.seed);
    }

    @Override
    public Application replicate() {
        return new ResourceManagementApplication(this);
    }

    /**
     * Handles an incoming message. If the message is a request message replies
     * with a response message. Generates events for request and response
     * messages.
     * 
     * @param msg
     *            message received by the router
     * @param host
     *            host to which the application instance is attached
     */
    @Override
    public Message handle(Message msg, DTNHost host) {
        String type = (String) msg.getProperty("type");
        if (type == null)
            return msg; // Not a request/response/unidirectional message

        logImportant("");
        logImportant("-----------handle--------------");

        logImportant("On node " + host.getAddress()
                + ": Incoming message (ID: " + msg.getId() + ", from:"
                + msg.getFrom().getAddress() + ", to:"
                + msg.getTo().getAddress() + ")");

        logBuffer(host);

        // forward and do resource management if we are not the recipient!
        if (msg.getTo().getAddress() != host.getAddress()) {
            if (type.equalsIgnoreCase("request")
                    || type.equalsIgnoreCase("request_reshog")
                    || type.equalsIgnoreCase("unidirectional")) {

                // buffer by source
                logDebug("buffering " + type + " (ID: " + msg.getId()
                        + ") on node " + host.getAddress() + " into partition "
                        + msg.getFrom().getAddress());
                buffer[host.getAddress()].addMessage(
                        msg.getFrom().getAddress(), msg, host);
            }

            if (type.equalsIgnoreCase("response")
                    || type.equalsIgnoreCase("response_reshog")) {

                if (simulateProxySignatures) {
                    // buffer by destination -> proxy sig!
                    logDebug("buffering response on " + host.getAddress()
                            + " into " + msg.getTo().getAddress());
                    buffer[host.getAddress()].addMessage(msg.getTo()
                            .getAddress(), msg, host);
                } else {
                    // buffer by source
                    logDebug("buffering response on " + host.getAddress()
                            + " into " + msg.getFrom().getAddress());
                    buffer[host.getAddress()].addMessage(msg.getFrom()
                            .getAddress(), msg, host);
                }

            }

            logBuffer(host);

            // forward that message again
            return msg;
        } else if (msg.getTo().getAddress() == host.getAddress()) {
            // when we are the recipient...

            // Respond with response if we're the recipient
            if (type.equalsIgnoreCase("request")
                    || type.equalsIgnoreCase("request_reshog")) {

                // random size of response
                int size = rng.nextInt(responseMaxSize - responseMinSize)
                        + responseMinSize;

                // id: response-time-source-destination
                String id = "response" + SimClock.getTime() + "-"
                        + host.getAddress() + "-" + msg.getFrom().getAddress();
                Message m = new Message(host, msg.getFrom(), id, size);

                if (type.equalsIgnoreCase("request")) {
                    m.addProperty("type", "response");

                    // Send event to listeners
                    super.sendEventToListeners("GotRequest", null, host);
                    super.sendEventToListeners("SentResponse", null, host);

                } else if (type.equalsIgnoreCase("request_reshog")) {
                    m.addProperty("type", "response_reshog");

                    // Send event to listeners
                    super.sendEventToListeners("GotRequestResHog", null, host);
                    super.sendEventToListeners("SentResponseResHog", null, host);
                }
                m.setAppID(APP_ID);
                host.createNewMessage(m);

                // buffer the response in own buffer!!!
                if (simulateProxySignatures) {
                    // buffer by destination -> proxy sig!
                    logDebug("buffering response on " + host.getAddress()
                            + " into " + m.getTo().getAddress());
                    buffer[host.getAddress()].addMessage(
                            m.getTo().getAddress(), m, host);
                } else {
                    // buffer by source
                    logDebug("buffering response on " + host.getAddress()
                            + " into " + m.getFrom().getAddress());
                    buffer[host.getAddress()].addMessage(m.getFrom()
                            .getAddress(), m, host);
                }

            }

            // Received a response
            if (type.equalsIgnoreCase("response")) {
                // Send event to listeners
                super.sendEventToListeners("GotResponse", null, host);
            }

            // Received a response
            if (type.equalsIgnoreCase("response_reshog")) {
                // Send event to listeners
                super.sendEventToListeners("GotResponseResHog", null, host);
            }

            // Received a unidirectional message
            if (type.equalsIgnoreCase("unidirectional")) {
                // Send event to listeners
                super.sendEventToListeners("GotUnidirectional", null, host);
            }

            logBuffer(host);

            // We have received something, we don't need to send out something
            // again
            return null;
        } else {

            // this should not happen
            return msg;
        }
    }

    /**
     * Draws a random host from the destination range
     * 
     * @return host
     */
    private int getRandomHostAddress() {
        int destaddr = 0;
        if (destMax == destMin) {
            destaddr = destMin;
        }

        destaddr = destMin + rng.nextInt(destMax - destMin);
        return destaddr;
    }

    /**
     * Gets random server from possible servers
     * 
     * @return
     */
    private int getRandomServerAddress() {
        int randomIndex = rng.nextInt(numberOfServers);
        int server = serverNodes.get(randomIndex);

        return server;
    }

    /**
     * Get node that is not in the server list
     * 
     * @return
     */
    private int getRandomClientAddress() {
        // choose dest not in list
        int randomAddress = getRandomHostAddress();
        while (serverNodes.contains(randomAddress)) {
            randomAddress = getRandomHostAddress();
        }

        return randomAddress;
    }

    /**
     * Get destination DTNHost
     * 
     * probabilityToSendRequest -> request to server
     * 
     * 100-probabilityToSendRequest -> unidirectional to other node
     * 
     * @return
     */
    private DTNHost getRandomDestinationHost() {
        int destaddr = 0;

        int random = rng.nextInt(99) + 1;

        if (random <= probabilityToSendRequest) {
            // send to a server
            destaddr = getRandomServerAddress();
        } else {
            destaddr = getRandomClientAddress();
        }

        World w = SimScenario.getInstance().getWorld();
        return w.getNodeByAddress(destaddr);
    }

    /**
     * Send requests only to servers, all other communication is just one way
     * (type unidirectional)
     * 
     * @param destination
     * @return
     */
    private String getTypeBasedOnDestination(int destination, boolean resHog) {
        if (serverNodes.contains(destination)) {
            if (resHog) {
                return "request_reshog";
            } else {
                return "request";
            }
        } else {
            return "unidirectional";
        }
    }

    /**
     * This method is executed by hard code in
     * routing/MessageRouter.deleteMessage()
     * 
     * It also deletes messages from our buffer system
     * 
     * @param host
     * @param messageID
     */
    public void deleteMessage(DTNHost host, String messageID) {
        buffer[host.getAddress()].deleteMessage(host, messageID);
    }

    /**
     * Sends a request packet if this is an active application instance.
     * 
     * @param host
     *            to which the application instance is attached
     */
    @Override
    public void update(DTNHost host) {
        if (this.passive)
            return;
        double curTime = SimClock.getTime();

        // if host is a res hog
        double thisLastRequest = -1;
        double thisInterval = -1;
        boolean resHog = false;
        if (resHogs.contains(host.getAddress())) {
            resHog = true;
            thisLastRequest = this.lastRequestResHogs;
            thisInterval = this.intervalResHogs;
        } else {
            resHog = false;
            thisLastRequest = this.lastRequest;
            thisInterval = this.interval;
        }

        if (curTime - thisLastRequest >= thisInterval) {

            logImportant("");
            logImportant("-----------update--------------");

            // Time to send a new message
            DTNHost destinationHost = getRandomDestinationHost();
            String type = getTypeBasedOnDestination(
                    destinationHost.getAddress(), resHog);

            logBuffer(host);

            // size of message
            int size = -1;
            if (type.equals("request") || type.equals("request_reshog")) {
                // random size of request
                size = rng.nextInt(requestMaxSize - requestMinSize)
                        + requestMinSize;
            } else if (type.equals("unidirectional")) {
                // random size of unidirectional
                size = rng.nextInt(unidirectionalMaxSize
                        - unidirectionalMinSize)
                        + unidirectionalMinSize;
            }

            // type is "request" (to server) or "unidirectional" (to other
            // clients)
            // ID syntax: request/unidirectional-time-source-destination
            Message m = new Message(host, destinationHost, type
                    + SimClock.getTime() + "-" + host.getAddress() + "-"
                    + destinationHost.getAddress(), size);
            m.addProperty("type", type);
            m.setAppID(APP_ID);
            host.createNewMessage(m);

            // Call listeners
            if (type.equals("request")) {
                super.sendEventToListeners("SentRequest", null, host);
            } else if (type.equals("request_reshog")) {
                super.sendEventToListeners("SentRequestResHog", null, host);
            } else if (type.equals("unidirectional")) {
                super.sendEventToListeners("SentUnidirectional", null, host);
            }

            // buffer by source
            logDebug("update: buffering own message" + type + " (ID: "
                    + m.getId() + ") on node " + host.getAddress()
                    + " into partition " + host.getAddress());
            buffer[host.getAddress()].addMessage(m.getFrom().getAddress(), m,
                    host);

            logBuffer(host);

            if (resHog) {
                this.lastRequestResHogs = curTime;
            } else {
                this.lastRequest = curTime;
            }
        }
    }

    /**
     * Displays buffer of application and the ONE
     * 
     * @param host
     */
    private void logBuffer(DTNHost host) {
        if (DEBUG >= 2) {

            logImportant("Buffers on node " + host.getAddress() + ":");

            // log application buffer
            buffer[host.getAddress()].logThisBuffer();

            // log the ONE buffer
            Collection<Message> messages = host.getMessageCollection();
            logImportant("The ONE buffer: " + messages);
        }
    }

    public double getLastRequest() {
        return lastRequest;
    }

    public void setLastRequest(double lastRequest) {
        this.lastRequest = lastRequest;
    }

    public double getInterval() {
        return interval;
    }

    public void setInterval(double interval) {
        this.interval = interval;
    }

    public boolean isPassive() {
        return passive;
    }

    public void setPassive(boolean passive) {
        this.passive = passive;
    }

    public int getDestMin() {
        return destMin;
    }

    public void setDestMin(int destMin) {
        this.destMin = destMin;
    }

    public int getDestMax() {
        return destMax;
    }

    public void setDestMax(int destMax) {
        this.destMax = destMax;
    }

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public int getNumberOfServers() {
        return numberOfServers;
    }

    public void setNumberOfServers(int numberOfServers) {
        this.numberOfServers = numberOfServers;
    }

    public ArrayList<Integer> getServerNodes() {
        return serverNodes;
    }

    public void setServerNodes(ArrayList<Integer> serverNodes) {
        this.serverNodes = serverNodes;
    }

    public ResourceManagementBuffer[] getBuffer() {
        return buffer;
    }

    public void setBuffer(ResourceManagementBuffer[] buffer) {
        this.buffer = buffer;
    }

    public boolean isWithProxySignatures() {
        return simulateProxySignatures;
    }

    public void setWithProxySignatures(boolean withProxySignatures) {
        this.simulateProxySignatures = withProxySignatures;
    }

    public int getRequestMinSize() {
        return requestMinSize;
    }

    public void setRequestMinSize(int requestMinSize) {
        this.requestMinSize = requestMinSize;
    }

    public int getRequestMaxSize() {
        return requestMaxSize;
    }

    public void setRequestMaxSize(int requestMaxSize) {
        this.requestMaxSize = requestMaxSize;
    }

    public int getResponseMinSize() {
        return responseMinSize;
    }

    public void setResponseMinSize(int responseMinSize) {
        this.responseMinSize = responseMinSize;
    }

    public int getResponseMaxSize() {
        return responseMaxSize;
    }

    public void setResponseMaxSize(int responseMaxSize) {
        this.responseMaxSize = responseMaxSize;
    }

    public int getUnidirectionalMinSize() {
        return unidirectionalMinSize;
    }

    public void setUnidirectionalMinSize(int unidirectionalMinSize) {
        this.unidirectionalMinSize = unidirectionalMinSize;
    }

    public int getUnidirectionalMaxSize() {
        return unidirectionalMaxSize;
    }

    public void setUnidirectionalMaxSize(int unidirectionalMaxSize) {
        this.unidirectionalMaxSize = unidirectionalMaxSize;
    }

    public int getClientBufferSize() {
        return clientBufferSize;
    }

    public void setClientBufferSize(int clientBufferSize) {
        this.clientBufferSize = clientBufferSize;
    }

    public int getServerBufferSize() {
        return serverBufferSize;
    }

    public void setServerBufferSize(int serverBufferSize) {
        this.serverBufferSize = serverBufferSize;
    }

    public int getPercentageOfServers() {
        return percentageOfServers;
    }

    public void setPercentageOfServers(int percentageOfServers) {
        this.percentageOfServers = percentageOfServers;
    }

    public int getProbabilityToSendRequest() {
        return probabilityToSendRequest;
    }

    public void setProbabilityToSendRequest(int probabilityToSendRequest) {
        this.probabilityToSendRequest = probabilityToSendRequest;
    }

    public double getIntervalResHogs() {
        return intervalResHogs;
    }

    public void setIntervalResHogs(double intervalResHogs) {
        this.intervalResHogs = intervalResHogs;
    }

    public int getPercentageOfResHogs() {
        return percentageOfResHogs;
    }

    public void setPercentageOfResHogs(int percentageOfResHogs) {
        this.percentageOfResHogs = percentageOfResHogs;
    }

    public int getNumberOfResHogs() {
        return numberOfResHogs;
    }

    public void setNumberOfResHogs(int numberOfResHogs) {
        this.numberOfResHogs = numberOfResHogs;
    }

    public ArrayList<Integer> getResHogs() {
        return resHogs;
    }

    public void setResHogs(ArrayList<Integer> resHogs) {
        this.resHogs = resHogs;
    }

    public double getLastRequestResHog() {
        return lastRequestResHogs;
    }

    public void setLastRequestResHog(double lastRequestResHog) {
        this.lastRequestResHogs = lastRequestResHog;
    }

}
