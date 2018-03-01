package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Scanner;
import java.io.*;

import net.floodlightcontroller.odin.master.OdinApplication;
import net.floodlightcontroller.odin.master.OdinClient;
import net.floodlightcontroller.util.MACAddress;

public class DemoStatistics extends OdinApplication {

  // IMPORTANT: this application only works if all the agents in the
  //poolfile are activated before the end of the INITIAL_INTERVAL.
  // Otherwise, the application looks for an object that does not exist
  //and gets stopped

  // this interval is for allowing the agents to connect to the controller
  private final int INITIAL_INTERVAL = 30000; // in ms
  
  private Scanner in = new Scanner(System.in);
  
  private int option;

  HashSet<OdinClient> clients;
  Map<MACAddress, Map<String, String>> vals_tx;
  Map<MACAddress, Map<String, String>> vals_rx;

  @Override
  public void run() {
    try {
      Thread.sleep(INITIAL_INTERVAL);
    } catch (InterruptedException e) {
	  e.printStackTrace();
    }
    
    InetAddress[] agents = getAgents().toArray(new InetAddress[0]);

    while (true) {
      try {
        Thread.sleep(100);
        //clients = new HashSet<OdinClient>(getClients());

		System.out.println("[DemoStatistics] =================================");
		System.out.println("[DemoStatistics] Internal and external Statistics");
		System.out.println("[DemoStatistics]");

	    // for each Agent
	    for (InetAddress agentAddr: agents) {
	      System.out.println("[DemoStatistics] Agent: " + agentAddr);
	      clients = new HashSet<OdinClient>(getClientsFromAgent(agentAddr));
	      if(clients.size()!=0){
	        System.out.println("[DemoStatistics] \tClients:");
	        for (OdinClient oc: clients) {
	          System.out.println("[DemoStatistics] \t\t"+oc.getIpAddress().getHostAddress());
            }
          }else{
            System.out.println("[DemoStatistics] \tNo clients associated");
          }
        }
        System.out.println("[DemoStatistics] =================================");
        System.out.println("[DemoStatistics] 1) Internal statistics");
        System.out.println("[DemoStatistics] 2) External statistics");
        System.out.println("[DemoStatistics] =================================");
        System.out.print("\tSelect option to continue: ");
        option = promptKey();
        int agent_index = 0;
        switch (option) {
            case 1:  System.out.println("[DemoStatistics] =======Internal statistics=======");
                     // for each Agent
                     agent_index = 0;
                     for (InetAddress agentAddr: agents) {
                       
                       System.out.println("[DemoStatistics] Agent ["+agent_index+"]: " + agentAddr);
                       System.out.println("[DemoStatistics] \tTxpower: " + getTxPowerFromAgent(agentAddr)+" dBm");
                       System.out.println("[DemoStatistics] \tChannel: " + getChannelFromAgent(agentAddr));
                       System.out.println("[DemoStatistics] \tLast heard: " + (System.currentTimeMillis()-getLastHeardFromAgent(agentAddr)) + " ms ago");
                       System.out.println("[DemoStatistics]");
                       agent_index++;
                     
                     }
                     System.out.print("\tSelect agent [0-"+(agent_index-1)+"]: ");// FIXME: Assuming no mistake, key in range
                     agent_index = promptKey();
                     clients = new HashSet<OdinClient>(getClientsFromAgent(agents[agent_index]));
                     if(clients.size()==0){
                       System.out.println("[DemoStatistics] No clients associated");
                       break;
                     }
                     vals_tx = getTxStatsFromAgent(agents[agent_index]);
                     vals_rx = getRxStatsFromAgent(agents[agent_index]);
                     System.out.println("[DemoStatistics] =================================");
                     for (OdinClient oc: clients) {  // all the clients currently associated
                       // for each STA associated to the Agent
                       System.out.println("[DemoStatistics] <<<<<<<<< Rx statistics >>>>>>>>>");
                       for (Entry<MACAddress, Map<String, String>> vals_entry_rx: vals_rx.entrySet()) {

                         MACAddress staHwAddr = vals_entry_rx.getKey();
                         if (oc.getMacAddress().equals(staHwAddr) && oc.getIpAddress() != null && !oc.getIpAddress().getHostAddress().equals("0.0.0.0")) {
                           System.out.println("\tUplink station MAC: " + staHwAddr + " IP: " + oc.getIpAddress().getHostAddress());
                           System.out.println("\t\tnum packets: " + vals_entry_rx.getValue().get("packets"));
                           System.out.println("\t\tavg rate: " + vals_entry_rx.getValue().get("avg_rate") + " kbps");
                           System.out.println("\t\tavg signal: " + vals_entry_rx.getValue().get("avg_signal") + " dBm");
                           System.out.println("\t\tavg length: " + vals_entry_rx.getValue().get("avg_len_pkt") + " bytes");
                           System.out.println("\t\tair time: " + vals_entry_rx.getValue().get("air_time") + " ms");			
                           System.out.println("\t\tinit time: " + vals_entry_rx.getValue().get("first_received") + " sec");
                           System.out.println("\t\tend time: " + vals_entry_rx.getValue().get("last_received") + " sec");
                           System.out.println("");
                         }
                       }
                       System.out.println("[DemoStatistics] <<<<<<<<< Tx statistics >>>>>>>>>");
                       // for each STA associated to the Agent
                       for (Entry<MACAddress, Map<String, String>> vals_entry_tx: vals_tx.entrySet()) {
                         MACAddress staHwAddr = vals_entry_tx.getKey();
                         if (oc.getMacAddress().equals(staHwAddr) && oc.getIpAddress() != null && !oc.getIpAddress().getHostAddress().equals("0.0.0.0")) {
                           System.out.println("\tDownlink station MAC: " + staHwAddr + " IP: " + oc.getIpAddress().getHostAddress());
                           System.out.println("\t\tnum packets: " + vals_entry_tx.getValue().get("packets"));
                           System.out.println("\t\tavg rate: " + vals_entry_tx.getValue().get("avg_rate") + " kbps");
                           System.out.println("\t\tavg signal: " + vals_entry_tx.getValue().get("avg_signal") + " dBm");
                           System.out.println("\t\tavg length: " + vals_entry_tx.getValue().get("avg_len_pkt") + " bytes");
                           System.out.println("\t\tair time: " + vals_entry_tx.getValue().get("air_time") + " ms");			
                           System.out.println("\t\tinit time: " + vals_entry_tx.getValue().get("first_received") + " sec");
                           System.out.println("\t\tend time: " + vals_entry_tx.getValue().get("last_received") + " sec");
                           System.out.println("");
                         }
                       }
                     }
                     System.out.println("[DemoStatistics] =================================");
                     break;
            case 2:  System.out.println("[DemoStatistics] =======External statistics=======");//channel and agent ¿? stas or ap??¿
                     agent_index = 0;
                     int channel = 0;
                     int scanning_interval = 0;
                     int result; // Result for scanning
                     for (InetAddress agentAddr: agents) {
	  
                       System.out.println("[DemoStatistics] Agent ["+agent_index+"]: " + agentAddr);
                       System.out.println("[DemoStatistics]");
                       agent_index++;
                     
                     }
                     System.out.print("\tSelect agent [0-"+(agent_index-1)+"]: ");// FIXME: Assuming no mistake, key in range
                     agent_index = promptKey();
                     System.out.print("\tSelect channel to scan [1-11]: ");// FIXME: Assuming no mistake, channel in range
                     channel = promptKey();
                     System.out.print("\tSelect time to scan (msec): ");// FIXME: Assuming no mistake
                     scanning_interval = promptKey();
                     result = requestScannedStationsStatsFromAgent(agents[agent_index], channel, "*");
                     System.out.println("[DemoStatistics] <<<<< Scanning in channel "+channel+" >>>>>>");
                     try {
                       Thread.sleep(scanning_interval);
                     }catch (InterruptedException e) {
                       e.printStackTrace();
                     }
                     if (result == 0) {
					   System.out.println("[DemoStatistics] Agent BUSY during scanning operation");
					   break;
                     }
                     clients = new HashSet<OdinClient>(getClientsFromAgent(agents[agent_index]));
					 
					 /*if(clients.size()==0){
                       System.out.println("[DemoStatistics] No clients associated");
                       break;
                     }*/
					 
                     Map<MACAddress, Map<String, String>> vals_rx = getScannedStationsStatsFromAgent(agents[agent_index], "*");
                     // for each STA scanned by the Agent
                     for (Entry<MACAddress, Map<String, String>> vals_entry_rx: vals_rx.entrySet()) {
                     // NOTE: the clients currently scanned MAY NOT be the same as the clients who have been associated		
					   MACAddress staHwAddr = vals_entry_rx.getKey();
					   boolean isWi5Sta = false;
					   boolean isWi5Lvap= false;
                       System.out.println("\tStation MAC: " + staHwAddr);
                       System.out.println("\t\tnum packets: " + vals_entry_rx.getValue().get("packets"));
					   System.out.println("\t\tavg rate: " + vals_entry_rx.getValue().get("avg_rate") + " kbps");
					   System.out.println("\t\tavg signal: " + vals_entry_rx.getValue().get("avg_signal") + " dBm");
					   System.out.println("\t\tavg length: " + vals_entry_rx.getValue().get("avg_len_pkt") + " bytes");
					   System.out.println("\t\tair time: " + vals_entry_rx.getValue().get("air_time") + " ms");						
					   System.out.println("\t\tinit time: " + vals_entry_rx.getValue().get("first_received") + " sec");
					   System.out.println("\t\tend time: " + vals_entry_rx.getValue().get("last_received") + " sec");
					
					   for (OdinClient oc: clients) {  // all the clients currently associated							
						 if (oc.getMacAddress().equals(staHwAddr)) {
                           System.out.println("\t\tAP of client: " + oc.getLvap().getAgent().getIpAddress());
                           System.out.println("\t\tChannel of AP: " + getChannelFromAgent(oc.getLvap().getAgent().getIpAddress()));
                           System.out.println("\t\tCode: Wi-5 STA");
                           System.out.println("");
                           isWi5Sta = true;
                           break;
						 }
						 if (oc.getLvap().getBssid().equals(staHwAddr)){
                           System.out.println("\t\tAP of client: " + oc.getLvap().getAgent().getIpAddress());
                           System.out.println("\t\tChannel of AP: " + getChannelFromAgent(oc.getLvap().getAgent().getIpAddress()));
                           System.out.println("\t\tCode: Wi-5 LVAP");		
                           System.out.println("");
                           isWi5Lvap = true;
                           break;
						 }
                       }
					   if (isWi5Sta) {
                         continue;
					   }
					   if (isWi5Lvap) {
                         continue;
					   }		
					   System.out.println("\t\tAP of client: unknown");
					   System.out.println("\t\tChannel of AP: unknown");
					   if(vals_entry_rx.getValue().get("equipment").equals("AP")){
                         System.out.println("\t\tCode: non-Wi-5 AP");
					   }else{
                         System.out.println("\t\tCode: non-Wi-5 STA");
					   }
					   System.out.println("");		
				     }
                     System.out.println("[DemoStatistics] =================================");
                     break;
            default: System.out.println("[DemoStatistics] Invalid option");
                     break;
        }
        promptEnterKey();
        System.out.print("\033[2J"); // Clear screen and cursor to 0,0
        
	    } catch (InterruptedException e) {
	      e.printStackTrace();
	    }
    }
  }
  public int promptKey(){ // Function to ask for a key
    int key;
    Scanner scanner = new Scanner(System.in);
    key = scanner.nextInt();
    return key;
  }
  public void promptEnterKey(){ // Function to ask for "ENTER"
   System.out.print("Press \"ENTER\" to continue...");
   Scanner scanner = new Scanner(System.in);
   scanner.nextLine();
  }
}
