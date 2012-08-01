# Resource Management for the ONE

This is an Application class for the Opportunistic Network Environment simulator (the ONE) to simulate Resource Management.

see http://www.netlab.tkk.fi/tutkimus/dtn/theone/

## Implementation Details
The ResourceManagementBuffer implements a second buffer besides the buffer in the ONE. Every node has its own instance of ResourceManagementBuffer. The buffer is based on a ``HashMap<Integer, HashSet<Message>>`` where a node address corresponds to partition HashSet<Message>. When the buffer is full on new incoming messages, old messages are dropped from the most exceeding partition, until the new message fits in the buffer.

## ToDo
* Implement domains
* Improve Dropping Implementation

## Changes

It is based on the ONE 1.4.1 with the following additions:

New classes:
* applications/ResourceManagementApplication
* applications/ResourceManagementBuffer
* report/ResourceManagementAppReporter

Changes:
* routing/ActiveRouter.dropExpiredMessage()