package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.floodlightcontroller.odin.master.OdinApplication;
import net.floodlightcontroller.odin.master.OdinClient;
import net.floodlightcontroller.util.MACAddress;

public class SimpleLoadBalancer extends OdinApplication {

	/*do the balancing every minute*/
	private final int INTERVAL = 60000;
	
	/* define the signal threshold to consider moving a client to an AP */
	private final int SIGNAL_THRESHOLD = 160;

	HashSet<OdinClient> clients;

	Map<MACAddress, Set<InetAddress>> hearingMap = new HashMap<MACAddress, Set<InetAddress>> ();
	Map<InetAddress, Integer> newMapping = new HashMap<InetAddress, Integer> ();
	
	
	@Override
	public void run() {
		
		
		while (true) {
			try {
				Thread.sleep(INTERVAL);
				
				/*all the clients Odin has heared (even non-connected) */				
				clients = new HashSet<OdinClient>(getClients());
				
				hearingMap.clear();
				newMapping.clear();
				
				/*
				 * Probe each AP to get the list of MAC addresses that it can "hear".
				 * We define "able to hear" as "signal strength > SIGNAL_THRESHOLD".
				 * 
				 *  We then build the hearing table.
				 *
				 * Note that the hearing table may not match the current distribution
				 *of clients between the APs
				 */
				 
				/* for each of the agents (APs)*/
				for (InetAddress agentAddr: getAgents()) {
					/* FIXME: if the next line is run before the APs are started,
					*the program blocks here */
					Map<MACAddress, Map<String, String>> vals = getRxStatsFromAgent(agentAddr);
					
					/* for each STA connected to that agent (AP) */
					for (Entry<MACAddress, Map<String, String>> vals_entry: vals.entrySet()) {
						
						MACAddress staHwAddr = vals_entry.getKey();
						
						/* for all the clients Odin has heared (even non-connected) */
						for (OdinClient oc: clients) {
							/* 
							* Check four conditions:
							* - the MAC address of the client must be that of the connected STA
							* - the IP address of the STA cannot be null
							* - the IP address of the STA cannot be 0.0.0.0
							* - the received signal must be over the threshold
							*/
							if (oc.getMacAddress().equals(staHwAddr)
									&& oc.getIpAddress() != null
									&& !oc.getIpAddress().getHostAddress().equals("0.0.0.0")
									&& Integer.parseInt(vals_entry.getValue().get("signal")) >= SIGNAL_THRESHOLD) {
							
								if (!hearingMap.containsKey(staHwAddr))
									hearingMap.put(staHwAddr, new HashSet<InetAddress> ());
									
								hearingMap.get(staHwAddr).add(agentAddr);
							}
						}
					}
				}
				
				balance();
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void balance() {
		
		if (hearingMap.size() == 0)
			return;
		
		/*
		 *  Now that the hearing map is populated, we re-assign
		 *  clients to each AP in a round robin fashion, constrained
		 *  by the hearing map.
		 */
		 
		/* for all the clients Odin has heared (even non-connected) */
		for (OdinClient client: clients) {

			InetAddress minNode = null;
			int minVal = 0;
			
			/* if the client does not have an IP address, do nothing */
			if ( client.getIpAddress() == null
					|| client.getIpAddress().getHostAddress().equals("0.0.0.0"))
				continue;

			/* if the MAC of the client is not in the (just built) hearing map, do nothing */			
			if(hearingMap.get(client.getMacAddress()) == null) {
				System.err.println("Skipping for client: " + client.getMacAddress());
				continue;
			}
				
			/* for each agent (AP) in the hearing table who is associated to that client */				
			for (InetAddress agentAddr: hearingMap.get(client.getMacAddress())) {
										
				if (!newMapping.containsKey(agentAddr)) {
					newMapping.put(agentAddr, 0);
				}
				
				int val = newMapping.get(agentAddr);
				
				/* assign the most suitable agent */
				if (minNode == null || val < minVal) {
					minVal = val;
					minNode = agentAddr;
				}
			}

			if (minNode == null)
				continue;
			
			/* I move the client to another AP */
			/* FIXME: check if this has to be done if we are moving a client to the AP where it already is */
			handoffClientToAp(client.getMacAddress(), minNode);
			newMapping.put (minNode, newMapping.get(minNode) + 1);
		}
	}
}
