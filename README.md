Odin Master
===========

The Odin Master is implemented as an application on top of the Floodlight OpenFlow controller. It uses an inband control channel to invoke commands on the agents. In its current form, Odin commands can add and remove LVAPs and query for statistics. The master, through Floodlight, uses the OpenFlow protocol to update forwarding tables on the AP and switches. Odin applications (i.e. Mobility Manager and Load Balancer) execute as a thread on top of the Odin Master. Applications can view the statistics exposed by the master in a key-value format.

References
----------
 
Floodlight
An Apache licensed, Java based OpenFlow controller

Floodlight is a Java based OpenFlow controller originally written by David Erickson at Stanford
University. It is available under the Apache 2.0 license.

For documentation, forums, issue tracking and more visit:

http://www.openflowhub.org/display/Floodlight/Floodlight+Home
