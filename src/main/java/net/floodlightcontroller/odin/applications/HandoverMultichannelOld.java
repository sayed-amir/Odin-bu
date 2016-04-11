package net.floodlightcontroller.odin.applications;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.lang.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.odin.master.OdinApplication;
import net.floodlightcontroller.odin.master.NotificationCallback;
import net.floodlightcontroller.odin.master.NotificationCallbackContext;
import net.floodlightcontroller.odin.master.OdinClient;
import net.floodlightcontroller.odin.master.OdinEventSubscription;
import net.floodlightcontroller.odin.master.OdinMaster;
import net.floodlightcontroller.odin.master.OdinEventSubscription.Relation;
import net.floodlightcontroller.util.MACAddress;

/**
 * 
 * Working in progress ...
 * 
 * @author Luis Sequeira <sequeira@unizar.es>
 *
 */

public class HandoverMultichannelOld extends OdinApplication {
	protected static Logger log = LoggerFactory.getLogger(HandoverMultichannelOld.class);
	// a table including each client and its mobility statistics
	private ConcurrentMap<MACAddress, MobilityStats> clientMap = new ConcurrentHashMap<MACAddress, MobilityStats> ();
	private final long HYSTERESIS_THRESHOLD; // milliseconds
	private final long IDLE_CLIENT_THRESHOLD; // milliseconds
	private final long SIGNAL_STRENGTH_THRESHOLD; // dbm

	private final int INTERVAL = 40000; // time before running the application. This leaves you some time for starting the agents
	private final int CHANNEL_AP5 = 4;
	private final int CHANNEL_AP6 = 9;
	private int channel;
	private InetAddress agentAddr5;
	private InetAddress agentAddr6;	
	HashSet<OdinClient> clients;

	public HandoverMultichannelOld () {
		this.HYSTERESIS_THRESHOLD = 3000;
		this.IDLE_CLIENT_THRESHOLD = 4000;
		this.SIGNAL_STRENGTH_THRESHOLD = 40;
	}
	
	/* Used for testing
	public HandoverMultichannel (long hysteresisThresh, long idleClientThresh, long signalStrengthThresh) {
		this.HYSTERESIS_THRESHOLD = hysteresisThresh;
		this.IDLE_CLIENT_THRESHOLD = idleClientThresh;
		this.SIGNAL_STRENGTH_THRESHOLD = signalStrengthThresh;
	}*/
	
	@Override
	public void run() {
		/* you need some time to start the agents */
		//log.info("Scanner: Waiting");
		try {
			Thread.sleep(INTERVAL);
		} catch (InterruptedException e){
        		e.printStackTrace();
		}
		//asigmentChannel();
		init (); 
	}
		
	/**
	 * Register subscriptions
	 */
	private void init () {
		
		OdinEventSubscription oes = new OdinEventSubscription();
		oes.setSubscription("40:A5:EF:05:93:DC", "signal", Relation.GREATER_THAN, 0);
		NotificationCallback cb = new NotificationCallback() {
			@Override
			public void exec(OdinEventSubscription oes, NotificationCallbackContext cntx) {
				handover(oes, cntx);
			}
		};
		registerSubscription(oes, cb);
	}
	
	/**
	 * This scanner will ...
	 * 
	 * @param oes
	 * @param cntx
	 */
	private void handover (OdinEventSubscription oes, NotificationCallbackContext cntx) {
		String ap5 = "192.168.1.5";
		String ap6 = "192.168.1.6";
		String ssid = "odin-wi5-demo";
		channel = 100;
		try {
			agentAddr5 = InetAddress.getByName(ap5);
			agentAddr6 = InetAddress.getByName(ap6);
			log.info ("HandoverMultichannel: Setting " + agentAddr5.getHostAddress() + " " + agentAddr6.getHostAddress()); // for testing
		} catch (UnknownHostException e) {
					e.printStackTrace();
		}
		for (InetAddress agentAddr: getAgents()) {
			log.info("HandoverMultichannel: Agents " + agentAddr.getHostAddress());
		}
		while (true) {
			log.info ("HandoverMultichannel: Entering to ping-pong ....");		
			//for (InetAddress agentAddr: getAgents()) { 
				/* for each of the agents defined in the Poolfile (APs) */
				//log.info ("HandoverMultichannel: Looking for all Ap's ...." + agentAddr.getHostAddress());
				//log.info ("HandoverMultichannel: Looking for all Ap's .... if");
				if (cntx.agent.getIpAddress().equals(agentAddr5)){
				//if (agentAddr.getHostAddress().equals(agentAddr5.getHostAddress())){
					//log.info ("HandoverMultichannel: Sending csa-beacon from agent: " + agentAddr.getHostAddress() + " to client " + cntx.clientHwAddress);
					log.info ("HandoverMultichannel: Sending csa-beacon from agent: " + cntx.agent.getIpAddress() + " to client " + cntx.clientHwAddress);
					channel = getChannelFromAgent(cntx.agent.getIpAddress());
					log.info ("HandoverMultichannel: Channel: " + Integer.toString(channel));
					//log.info ("HandoverMultichannel: Agent: " + agentAddr.getHostAddress() + " in channel " + Integer.toString(channel));					
					sendChannelSwitchToClient(cntx.agent.getIpAddress(), cntx.clientHwAddress, ssid, CHANNEL_AP6);
					handoffClientToAp(cntx.clientHwAddress, agentAddr6);
					channel = getChannelFromAgent(cntx.agent.getIpAddress());
					log.info ("HandoverMultichannel: Channel: " + Integer.toString(channel));
				} else {
					//log.info ("HandoverMultichannel: Looking for all Ap's .... else");
					if (cntx.agent.getIpAddress().equals(agentAddr6)){
					//if (agentAddr.getHostAddress().equals(agentAddr6.getHostAddress())){
						log.info ("HandoverMultichannel: Sending csa-beacon from agent: " + cntx.agent.getIpAddress() + " to client " + cntx.clientHwAddress);
						channel = getChannelFromAgent(cntx.agent.getIpAddress());
						log.info ("HandoverMultichannel: Channel: " + Integer.toString(channel));
						//log.info ("HandoverMultichannel: Agent: " + agentAddr.getHostAddress() + " in channel " + Integer.toString(channel));
						sendChannelSwitchToClient(cntx.agent.getIpAddress(), cntx.clientHwAddress, ssid, CHANNEL_AP5);
						handoffClientToAp(cntx.clientHwAddress, agentAddr5);
						channel = getChannelFromAgent(cntx.agent.getIpAddress());
						log.info ("HandoverMultichannel: Channel: " + Integer.toString(channel));
					}
				}
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			//}
		}	
	}

	private void asigmentChannel () {
		for (InetAddress agentAddr: getAgents()) {
			log.info("HandoverMultichannel: Agent IP: " + agentAddr.getHostAddress());
			if (agentAddr.getHostAddress().equals("192.168.1.7")){
				log.info ("HandoverMultichannel: Agent channel: " + getChannelFromAgent(agentAddr));
				setChannelToAgent(agentAddr, 4);
				log.info ("HandoverMultichannel: Agent channel: " + getChannelFromAgent(agentAddr));
			}
			if (agentAddr.getHostAddress().equals("192.168.1.8")){
				log.info ("HandoverMultichannel: Agent channel: " + getChannelFromAgent(agentAddr));
				setChannelToAgent(agentAddr, 10);
				log.info ("HandoverMultichannel: Agent channel: " + getChannelFromAgent(agentAddr));
			}
		}
	}
	
private class MobilityStats {
	public long signalStrength;
	public long lastHeard;			// timestamp where it was heard the last time
	public long assignmentTimestamp;	// timestamp it was assigned
	
	public MobilityStats (long signalStrength, long lastHeard, long assignmentTimestamp) {
		this.signalStrength = signalStrength;
		this.lastHeard = lastHeard;
		this.assignmentTimestamp = assignmentTimestamp;
	}
}
}