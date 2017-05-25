package net.floodlightcontroller.odin.master;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.odin.master.OdinClient;
import net.floodlightcontroller.util.MACAddress;

interface IOdinMasterToApplicationInterface {

	/**
	 * VAP-Handoff a client to a new AP. This operation is idempotent.
	 * 
	 * @param newApIpAddr IPv4 address of new access point
	 * @param hwAddrSta Ethernet address of STA to be handed off
	 */
	void handoffClientToAp (String pool, MACAddress staHwAddr, InetAddress newApIpAddr);

	
	/**
	 * Get the list of clients currently registered with Odin
	 * 
	 * @return a map of OdinClient objects keyed by HW Addresses
	 */
	Set<OdinClient> getClients (String pool);
	
	
	/**
	 * Get the OdinClient type from the client's MACAddress
	 * 
	 * @param pool that the invoking application corresponds to
	 * @return a OdinClient instance corresponding to clientHwAddress
	 */
	OdinClient getClientFromHwAddress (String pool, MACAddress clientHwAddress);
	
	long getLastHeardFromAgent (String pool, InetAddress agentAddr);

	Map<MACAddress, Map<String, String>> getTxStatsFromAgent (String pool, InetAddress agentAddr);
	
	Map<MACAddress, Map<String, String>> getRxStatsFromAgent (String pool, InetAddress agentAddr);
	
	/**
	 * Get a list of Odin agents from the agent tracker
	 * @return a map of OdinAgent objects keyed by Ipv4 addresses
	 */
	Set<InetAddress> getAgentAddrs (String pool);
	
	
	/**
	 * Add a subscription for a particular event defined by oes. cb is
	 * defines the application specified callback to be invoked during
	 * notification. If the application plans to delete the subscription,
	 * later, the onus is upon it to keep track of the subscription
	 * id for removal later.
	 * 
	 * @param oes the susbcription
	 * @param cb the callback
	 */
	long registerSubscription (String pool, OdinEventSubscription oes, NotificationCallback cb);
	
	
	/**
	 * Remove a subscription from the list
	 * 
	 * @param id subscription id to remove
	 * @return
	 */
	void unregisterSubscription (String pool, long id);
	
	
	/**
	 * Add a flow detection for a particular event defined by oefd. cb is
	 * defines the application specified callback to be invoked during
	 * flow detection. If the application plans to delete the flow detection,
	 * later, the onus is upon it to keep track of the flow detection
	 * id for removal later.
	 * 
	 * @param oefd the flow detection 
	 * @param cb the callback
	 */
	long registerFlowDetection (String pool, OdinEventFlowDetection oefd, FlowDetectionCallback cb);
	
	
	/**
	 * Remove a flow detection from the list
	 * 
	 * @param id flow detection id to remove
	 * @return
	 */
	void unregisterFlowDetection (String pool, long id);


	/**
	 * Add an SSID to the Odin network.
	 * 
	 * @param networkName
	 * @return true if the network could be added, false otherwise
	 */
	boolean addNetwork (String pool, String ssid);
	
	
	/**
	 * Remove an SSID from the Odin network.
	 * 
	 * @param networkName
	 * @return true if the network could be removed, false otherwise
	 */
	boolean removeNetwork (String pool, String ssid);
	
	/**
	 * Change the Wi-Fi channel of an specific agent (AP)
	 * 
	 * @param Pool
	 * @param Agent InetAddress
	 * @param Channel
	 * @author Luis Sequeira <sequeira@unizar.es>
	 */
	void setChannelToAgent (String pool, InetAddress agentAddr, int channel);
	
	
	/**
	 * Get channel from and specific agent (AP)
	 * 
	 * @param Pool
	 * @param Agent InetAddress
	 * @return Channel number
	 * @author Luis Sequeira <sequeira@unizar.es>
	 */
	int getChannelFromAgent (String pool, InetAddress agentAddr);
	
	
	/**
	 * Channel Switch Announcement, to the clients of an specific agent (AP)
	 * 
	 * @param Pool
	 * @param Agent InetAddress
	 * @param Client MAC
	 * @param SSID
	 * @param Channel
	 * @author Luis Sequeira <sequeira@unizar.es>
	 */
	//void sendChannelSwitchToClient (String pool, InetAddress agentAddr, MACAddress clientHwAddr, List<String> lvapSsids, int channel);
	
	/**
	 * Scanning for a client in a specific agent (AP)
	 * 
	 * @param Pool
	 * @param Agent InetAddress
	 * @param Client MAC
	 * @param Channel
	 * @param Scanning time
	 * @return Signal power
	 * @author Luis Sequeira <sequeira@unizar.es>
	 */
	int scanClientFromAgent (String pool, InetAddress agentAddr, MACAddress clientHwAddr, int channel, int time);	


}