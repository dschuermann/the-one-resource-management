/* 
 * Copyright 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import core.DTNHost;
import core.Message;

public class ResourceManagementBuffer {

    private HashMap<Integer, HashSet<Message>> buffer;
    private int maxBufferSize;

    /**
     * Constructor
     * 
     * @param maxBufferSize
     *            in bytes (1 MB = 1024 KB = 1048576 Byte)
     */
    public ResourceManagementBuffer(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;

        this.buffer = new HashMap<Integer, HashSet<Message>>();
    }

    /**
     * Debug logging of current buffer state
     */
    public void logThisBuffer() {
        if (ResourceManagementApplication.DEBUG >= 2) {

            String output = "[";
            // go through all buffers
            Iterator<Entry<Integer, HashSet<Message>>> it = buffer.entrySet()
                    .iterator();
            while (it.hasNext()) {
                Entry<Integer, HashSet<Message>> pairs = it.next();
                int partitionID = pairs.getKey();

                HashSet<Message> partition = pairs.getValue();

                output += " " + partitionID + ": " + partition;
            }

            output += "]";

            ResourceManagementApplication
                    .logImportant("ResourceManagement buffer: " + output);
        }
    }

    /**
     * Adds message to buffer
     * 
     * @param partitionID
     * @param msg
     * @param bufferHost
     */
    public void addMessage(int partitionID, Message msg, DTNHost bufferHost) {
        // if buffer does not contain such a host add it
        HashSet<Message> partition;
        if (!buffer.containsKey(partitionID)) {
            partition = new HashSet<Message>();
            partition.add(msg);

            buffer.put(partitionID, partition);
        } else {
            // add message to existing partition
            partition = buffer.get(partitionID);
            partition.add(msg);
        }

        // drop until there is enough space
        dropFromBufferAlgorithm(bufferHost, partitionID, msg);
    }

    /**
     * Removes specific message from buffer
     * 
     * @param bufferHost
     * @param messageID
     */
    public void deleteMessage(DTNHost bufferHost, String messageID) {

        Message messageToDelete = null;
        // go through all buffers
        Iterator<Entry<Integer, HashSet<Message>>> it = buffer.entrySet()
                .iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, HashSet<Message>> pairs = it.next();
            HashSet<Message> partition = (HashSet<Message>) pairs.getValue();

            Iterator<Message> it2 = partition.iterator();
            while (it2.hasNext()) {
                Message crMessage = it2.next();
                if (crMessage.getId() == messageID) {
                    messageToDelete = crMessage;
                }
            }

            try {
                partition.remove(messageToDelete);

                ResourceManagementApplication
                        .logImportant("Successfully dropped "
                                + messageToDelete.getId());
            } catch (Exception e) {
                ResourceManagementApplication
                        .logImportant("Had no success deleting msg from buffer! ("
                                + e.getMessage() + ")");
            }
        }
    }

    /**
     * Drops from buffer until there it is back under the threshold
     */
    private void dropFromBufferAlgorithm(DTNHost bufferHost,
            Integer partitionID, Message newMessage) {
        // drop from partitions until whole buffer usage is back under maximum
        int partitionIDToDropFrom = -1;

        ResourceManagementApplication.logDebug("whole buffer usage: "
                + getWholeBufferUsage());
        ResourceManagementApplication.logDebug("maxBufferSize: "
                + maxBufferSize);

        while (getWholeBufferUsage() > maxBufferSize) {
            partitionIDToDropFrom = getPartitionIDWithHighestUsage(false);

            /* Drops message from this partition */
            HashSet<Message> partition = buffer.get(partitionIDToDropFrom);

            // get oldest message from partition
            Message msgToDrop = getOldestMessage(partition, newMessage);

            // partition has only this message in it, then take second highest
            // partition
            if (msgToDrop == null) {
                ResourceManagementApplication
                        .logImportant("message to drop is the only message in partition "
                                + partitionIDToDropFrom);

                // get second highest partition
                partitionIDToDropFrom = getPartitionIDWithHighestUsage(true);
                partition = buffer.get(partitionIDToDropFrom);

                msgToDrop = getOldestMessage(partition, newMessage);
            }

            ResourceManagementApplication.logImportant("Dropping message "
                    + msgToDrop + " on node " + bufferHost.getAddress()
                    + " from partition: " + partitionIDToDropFrom);

            partition.remove(msgToDrop);

            // Drop from real buffer in the ONE
            if (msgToDrop != null) {
                try {
                    bufferHost.deleteMessage(msgToDrop.getId(), true);
                } catch (AssertionError e) {
                    ResourceManagementApplication
                            .logWarning("bufferHost.deleteMessage failed! This should not happen! error: "
                                    + e.getMessage());
                }
            } else {
                ResourceManagementApplication
                        .logWarning("msgToDrop is null! This should not happen!");
            }
        }
    }

    /**
     * 
     * Forked from ActiveRouter
     * 
     * Returns the oldest (by receive time) message in the message buffer (that
     * is not being sent if excludeMsgBeingSent is true).
     * 
     * @param excludeMsgBeingSent
     *            If true, excludes message(s) that are being sent from the
     *            oldest message check (i.e. if oldest message is being sent,
     *            the second oldest message is returned)
     * @return The oldest message or null if no message could be returned (no
     *         messages in buffer or all messages in buffer are being sent and
     *         exludeMsgBeingSent is true)
     */
    protected Message getOldestMessage(HashSet<Message> partition,
            Message excludeMessage) {
        Message oldest = null;
        for (Message m : partition) {

            if (!m.equals(excludeMessage)) {
                if (oldest == null) {
                    oldest = m;
                } else if (oldest.getReceiveTime() > m.getReceiveTime()) {
                    oldest = m;
                }
            }
        }

        return oldest;
    }

    /**
     * Gets ID of partition with highest partition usage
     * 
     * @param getSecond
     *            when true, method will return second highest partition
     * @return
     */
    private int getPartitionIDWithHighestUsage(boolean getSecond) {
        // get maximal exceeded buffer
        int highestPartitionID = -1;

        int highestUsage = -1;

        Iterator<Entry<Integer, HashSet<Message>>> it = buffer.entrySet()
                .iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, HashSet<Message>> pairs = it.next();
            HashSet<Message> partition = (HashSet<Message>) pairs.getValue();
            int currentPartitionID = (Integer) pairs.getKey();

            int currentUsage = getPartitionUsage(partition);

            ResourceManagementApplication.logDebug("Partiton ("
                    + currentPartitionID + ") with " + "partitionUsage:"
                    + currentUsage);

            // find one highest usage (the first is taken)
            if (highestUsage < currentUsage) {

                ResourceManagementApplication
                        .logDebug("NEW highest usage Partiton ("
                                + currentPartitionID + ") with "
                                + "partitionUsage:" + currentUsage);

                // new highest usage
                highestUsage = currentUsage;
                highestPartitionID = currentPartitionID;
            }

        }

        ResourceManagementApplication.logDebug("Partition "
                + highestPartitionID + " with highest usage " + highestUsage);

        int secondHighestPartitionID = -1;

        if (getSecond) {
            int secondHighestUsage = -1;

            Iterator<Entry<Integer, HashSet<Message>>> it2 = buffer.entrySet()
                    .iterator();
            while (it2.hasNext()) {
                Map.Entry<Integer, HashSet<Message>> pairs = it2.next();
                HashSet<Message> partition = (HashSet<Message>) pairs
                        .getValue();
                int currentPartitionID = (Integer) pairs.getKey();

                int currentUsage = getPartitionUsage(partition);

                ResourceManagementApplication.logDebug("Partiton ("
                        + currentPartitionID + ") with " + "partitionUsage:"
                        + currentUsage);

                // find one second highest usage (the first is taken)
                if ((secondHighestUsage < currentUsage)
                        && (currentPartitionID != highestPartitionID)) {

                    ResourceManagementApplication
                            .logDebug("NEW second highest usage Partiton ("
                                    + currentPartitionID + ") with "
                                    + "partitionUsage:" + currentUsage);

                    // second highest one
                    secondHighestUsage = currentUsage;
                    secondHighestPartitionID = currentPartitionID;
                }
            }

            ResourceManagementApplication.logDebug("Partition "
                    + secondHighestPartitionID + " with second highest usage "
                    + secondHighestUsage);
        }

        if (getSecond) {
            return secondHighestPartitionID;
        } else {
            return highestPartitionID;
        }
    }

    /**
     * Sums all message sizes in a partition
     */
    private int getPartitionUsage(HashSet<Message> partition) {
        int usage = 0;
        for (Message currentMessage : partition) {
            usage += currentMessage.getSize();
        }

        return usage;
    }

    /**
     * Sums up all messages in all partitions (whole buffer)
     * 
     * @return usage of buffer
     */
    private int getWholeBufferUsage() {
        int wholeUsage = 0;

        Iterator<Entry<Integer, HashSet<Message>>> it = buffer.entrySet()
                .iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, HashSet<Message>> pairs = it.next();
            HashSet<Message> partition = (HashSet<Message>) pairs.getValue();

            int partitionUsage = getPartitionUsage(partition);

            wholeUsage += partitionUsage;
        }

        ResourceManagementApplication.logDebug("whole buffer usage: "
                + wholeUsage);

        return wholeUsage;
    }

}
