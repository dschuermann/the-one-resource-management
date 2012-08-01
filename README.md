# Resource Management for the ONE

This is an Application class for the Opportunistic Network Environment simulator (the ONE) to simulate Resource Management.

see http://www.netlab.tkk.fi/tutkimus/dtn/theone/

# Changes

It is based on the ONE 1.4.1 with the following additions:

New classes:
* applications/ResourceManagementApplication
* applications/ResourceManagementBuffer
* report/ResourceManagementAppReporter

Changes:
* ActiveRouter.dropExpiredMessage()