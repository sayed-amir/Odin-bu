package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.File;
import java.io.PrintStream;
import java.math.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


import net.floodlightcontroller.odin.master.OdinApplication;
import net.floodlightcontroller.odin.master.OdinClient;
import net.floodlightcontroller.odin.master.OdinEventFlowDetection;
import net.floodlightcontroller.odin.master.FlowDetectionCallback;
import net.floodlightcontroller.odin.master.FlowDetectionCallbackContext;
import net.floodlightcontroller.odin.master.OdinMaster.SmartApSelectionParams;
import net.floodlightcontroller.util.MACAddress;

public class SecureApSelection extends OdinApplication {

  // IMPORTANT: this application only works if all the agents in the
  //poolfile are activated before the end of the INITIAL_INTERVAL.
  // Otherwise, the application looks for an object that does not exist
  //and gets stopped

  // SSID to scan
  private final String SCANNED_SSID = "*";

  //Params
  private SmartApSelectionParams SMARTAP_PARAMS;

  // Scanning agents
  Map<InetAddress, Integer> scanningAgents = new HashMap<InetAddress, Integer> ();
  int result; // Result for scanning

  HashSet<OdinClient> clients;
  Set<InetAddress> agents;

  private int[] channels = null;
  private int num_channels = 0;
  private int num_agents = 0;
  private String[][] vals_rx = null;


  private long time = 0L; // Compare timestamps in ms
  
  InetAddress nullAddr = null;
  InetAddress vipAPAddr = null;
  InetAddress nonVipAPAddr = null; // If the STA connects to VIP first, we need to handoff to other AP
  
  private int vip_index = 0;
  /**
  * Flow detection
  */
  private final String IPSrcAddress;        // Handle a IPSrcAddress or all IPSrcAddress ("*")
  private final String IPDstAddress;        // Handle a IPDstAddress or all IPDstAddress ("*")
  private final int protocol;           // Handle a protocol or all protocol ("*")
  private final int SrcPort;            // Handle a SrcPort or all SrcPort ("*")
  private final int DstPort;            // Handle a DstPort or all DstPort ("*")
  

  // Initialize the variables
  public SecureApSelection () {
    this.IPSrcAddress = "*";    // The controller will handle subscriptions from every IP source accress
    this.IPDstAddress = "*";
    this.protocol = 0;
    this.SrcPort = 0;
    this.DstPort = 0;
  }


  /**
   * Condition for a hand off
   *
   * Example of params in poolfile imported in SMARTAP_PARAMS:
   *
   * SMARTAP_PARAMS.HYSTERESIS_THRESHOLD = 4;
   * SMARTAP_PARAMS.SIGNAL_THRESHOLD = -56;
   *
   * With these parameters a hand off will start when:
   *
   * The Rssi received from a specific client is below -56 dBm, there is another AP with better received Rssi and a previous hand off has not happened in the last 4000 ms
   *
   */

@Override
  public void run() {
    System.out.println("[SecureAPSelection] Start");
    this.SMARTAP_PARAMS = getSmartApSelectionParams();  // Import the parameters of Poolfile, using this function of Odin Master
    
    // Wait a period in order to let the user start the agents
    try {
      System.out.println("[SecureAPSelection] Sleep for " + SMARTAP_PARAMS.time_to_start);
      Thread.sleep(SMARTAP_PARAMS.time_to_start);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    
    // Integration of write on file functionality
    PrintStream ps = null;

    if(SMARTAP_PARAMS.filename.length()>0){ // check that the parameter exists
      File f = new File(SMARTAP_PARAMS.filename);
      try {
        ps = new PrintStream(f);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    MACAddress evdpMACaddr = null;
    //evdpMACaddr = MACAddress.valueOf("78:31:C1:EC:AE:90");
    evdpMACaddr = MACAddress.valueOf("1C:BF:C0:78:DA:63");
    //evdpMACaddr = MACAddress.valueOf("D0:37:45:47:F2:D9");

    agents = getAgents(); // Fill the array of agents
    num_agents = agents.size(); // Number of agents
    channels = new int[num_agents]; // Array to store the channels in use
    int[] channelsAux = new int[num_agents];  // Array of the channels in use by the auxiliary interfaces of each AP

    // Add this information to the log file
    ps.println("[SecureAPSelection] Log file " + SMARTAP_PARAMS.filename); // Log in file
    ps.println("[SecureAPSelection] Parameters:");
    ps.println("\tTime_to_start: " + SMARTAP_PARAMS.time_to_start);
    ps.println("\tScanning_interval: " + SMARTAP_PARAMS.scanning_interval);
    ps.println("\tAdded_time: " + SMARTAP_PARAMS.added_time);
    ps.println("\tSignal_threshold: " + SMARTAP_PARAMS.signal_threshold);
    ps.println("\tHysteresis_threshold: " + SMARTAP_PARAMS.hysteresis_threshold);
    ps.println("\tPrevius_data_weight (alpha): " + SMARTAP_PARAMS.weight);
    ps.println("\tPause between scans: " + SMARTAP_PARAMS.pause);
    ps.println("\tMode: " + SMARTAP_PARAMS.mode);
    ps.println("\tTxpowerSTA: " + SMARTAP_PARAMS.txpowerSTA);
    ps.println("\tTxpowerSTA: " + SMARTAP_PARAMS.thReqSTA);
    ps.println("\tFilename: " + SMARTAP_PARAMS.filename);

    // Array created to show the line with the names of the APs
    String showAPsLine = "\033[K\r[SecureAPSelection] ";
    
    try { // Create IP to compare with clients not assigned
      nullAddr = InetAddress.getByName("0.0.0.0");
      vipAPAddr = InetAddress.getByName(getVipAPIpAddress());   // VIP AP
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    
    int ind_aux = 0;

    
    
    String[] AgentsIP = new String[num_agents];
    
    
    // Get channels from APs, assuming there is no change in all operation, if already in array->0
    for (InetAddress agentAddr: agents) {
      //AgentsIP[ind_aux]	= agentAddr;
      String hostIP = agentAddr.getHostAddress(); // Build line for user interface
      AgentsIP[ind_aux] = hostIP;
      ps.println("[SecureAPSelection] AP " + AgentsIP[ind_aux] + " is in: " + ind_aux);
      showAPsLine = showAPsLine + "\033[0;1m[ AP" + hostIP.substring(hostIP.lastIndexOf('.')+1,hostIP.length()) + " ]";

      int chann = getChannelFromAgent(agentAddr);
      //System.out.println("[SecureAPSelection] = Channels " + Arrays.toString(channels));
      Arrays.sort(channelsAux);   // ordered list of channels used by the main interface of the APs. A channel is only added once
      //System.out.println("[SecureAPSelection] = Search for "+chann+": " + Arrays.binarySearch(channelsAux, chann));

      if(Arrays.binarySearch(channelsAux, chann) < 0){// if already in array, not necessary to add it
        channelsAux[num_channels] = chann;
        channels[num_channels] = chann;
        //System.out.println("[SecureAPSelection] Chann added "+chann);
      }
      System.out.println("[SecureAPSelection] AP " + agentAddr + " in channel: " + chann);
      ps.println("[SecureAPSelection] AP " + agentAddr + " in channel: " + chann); // Log in file
      
      if(agentAddr.equals(vipAPAddr)){
        vip_index=ind_aux;
      }else{
        nonVipAPAddr = agentAddr;
      }
      num_channels++;
      ind_aux++;
    }
    ps.println("[SecureAPSelection]");
    ps.flush(); // write in the log file (empty the buffer)

    vals_rx = new String[num_channels][num_agents]; // Matrix to store the results from agents
    Map<MACAddress, Double[]> rssiData = new HashMap<MACAddress, Double[]> (); // Map to store RSSI for each STA in all APs
    Map<MACAddress, Long> handoffDate = new HashMap<MACAddress, Long> (); // Map to store last handoff for each STA FIXME: Maybe create struct
    Map<MACAddress, Double[]> ffData = new HashMap<MACAddress, Double[]> (); // Map to store Throughput available for each STA in all APs

    System.out.print("\033[2J"); // Clear screen and cursor to 0,0
    char[] progressChar = new char[] { '-', '\\', '|', '/' };
    int progressIndex = 0;



    // Main loop
    while (true) {
      try {
        Thread.sleep(100);  // milliseconds
        	
        // restart the clients hashset in order to see if there are new STAs
        clients = new HashSet<OdinClient>(getClients());
        int num_clients = clients.size(); // Number of STAs

        if (num_clients == 0){ // No clients, no need of scan
          System.out.println("\033[K\r[SecureAPSelection] ====================");
          System.out.println("\033[K\r[SecureAPSelection] Proactive AP Handoff");
          System.out.println("\033[K\r[SecureAPSelection]");
          System.out.println("\033[K\r[SecureAPSelection] " + progressChar[progressIndex++] + "No clients associated, waiting for connection");
          System.out.println("\033[K\r[SecureAPSelection] ====================");
          if(progressIndex==3)
            progressIndex=0;
          System.out.print("\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[0;0H"); // Clear lines above and return to console 0,0
          continue;
        }

        // if there are clients
        int[] clientsChannels = new int[num_clients]; // Array with the indexes of channels of the STAs, better performance in data process


        // Various indexes
        int client_index = 0;
        int client_channel = 0;
        ind_aux = 0;

        System.out.println("\033[K\r[SecureAPSelection] ====================");
        System.out.println("\033[K\r[SecureAPSelection] Proactive AP Handoff");
        System.out.println("\033[K\r[SecureAPSelection]");


        // For each STA, fill the array
        for (OdinClient oc: clients) { // Create array with client channels and their indexes for better data processing

          ind_aux = 0;

          client_channel = getChannelFromAgent(oc.getLvap().getAgent().getIpAddress());

          for (int chann: channels){

            if (chann == client_channel){

              clientsChannels[client_index] = ind_aux;
              client_index++;
              break;

            }
            ind_aux++;
          }

        }

        time = System.currentTimeMillis();

        //For each channel used in our APs
        for (int channel = 0 ; channel < num_channels ; ++channel) {
          
          if(channels[channel]==0)
            continue;
          int agent = 0;
          scanningAgents.clear();

          // For each agent, request the statistics
          for (InetAddress agentAddr: agents) {

            // Request statistics
            result = requestScannedStationsStatsFromAgent(agentAddr, channels[channel], SCANNED_SSID);    
            // Check if the request has been successful
            scanningAgents.put(agentAddr, result);
          }         

          // sleep during the scanning
          try {
            Thread.sleep(SMARTAP_PARAMS.scanning_interval + SMARTAP_PARAMS.added_time);
          } 
          catch (InterruptedException e) {
            e.printStackTrace();
          }

          // Recover the information after the scanning
          for (InetAddress agentAddr: agents) {

            // Reception statistics 
            if (scanningAgents.get(agentAddr) == 0) { // Busy agent
              System.out.println("\033[K\r[SecureAPSelection] Agent BUSY during scanning operation");
              continue;       
            }

            // Agent non busy, so we recover the information
            vals_rx[channel][agent] = getScannedStaRssiFromAgent(agentAddr);
            agent++;

            
          }
          //Thread.sleep(200); // Give some time to the AP
        }

        System.out.println("\033[K\r[SecureAPSelection] Scanning done in: " + (System.currentTimeMillis()-time) + " ms");

        // All the statistics stored, now process
        time = System.currentTimeMillis();
        client_index = 0;
        ind_aux = 0;
        Double[] evdpRSSI = new Double[num_agents];

        Arrays.fill(evdpRSSI, (double) -99); //initial value for eavesdropper RSSI

        Double evdpRSSIReader;

        // For each STA (client) associated, store the RSSI value with which the APs "see" it
        for (OdinClient oc: clients) {

          MACAddress eth = oc.getMacAddress(); // client MAC
          client_channel = clientsChannels[client_index]; // row in the matrix

          for ( ind_aux = 0; ind_aux < num_agents; ind_aux++){// For 

            String arr = vals_rx[client_channel][ind_aux]; // String with "MAC rssi\nMAC rssi\n..."

            Double rssi = getRssiFromRxStats(eth,arr); // rssi or -99.9
            evdpRSSIReader = getAnotherRSSI(AgentsIP[ind_aux], evdpMACaddr);
            ps.println("\t [amir] \t" + (System.currentTimeMillis()) + "\t RSSI of eavesdroper " + evdpMACaddr + " is " + evdpRSSIReader  + " dbm at agent " + AgentsIP[ind_aux]); // Log in file
            if (evdpRSSIReader!=null)
            	evdpRSSI[ind_aux] = evdpRSSIReader;
            Double rssiNew = getAnotherRSSI(AgentsIP[ind_aux], eth);
            ps.println("\t [amir] \t " + (System.currentTimeMillis()) + "\t New RSSI of user " + eth + " is " + rssiNew + " dbm at agent " + AgentsIP[ind_aux]); // Log in file
            Double[] client_average_dBm = new Double[num_agents];
            if (rssiNew != null)
            	rssi = rssiNew;
            // get the stored RSSI averaged value (historical data)
            client_average_dBm = rssiData.get(eth);

            if (client_average_dBm == null){// First time STA is associated

              client_average_dBm = new Double[num_agents];
              Arrays.fill(client_average_dBm,-99.9); // first, we put -99.9 everywhere
              client_average_dBm[ind_aux] = rssi;   // put the last value instead of -99.9

            } else {

              // Recalculate the new average
              if((client_average_dBm[ind_aux]!=-99.9)&&(client_average_dBm[ind_aux]!=null)){
                if(rssi!=-99.9){
                  Double client_signal = Math.pow(10.0, (rssi) / 10.0); // Linear power
                  Double client_average = Math.pow(10.0, (client_average_dBm[ind_aux]) / 10.0); // Linear power average
                  client_average = client_average*(1-SMARTAP_PARAMS.weight) + client_signal*SMARTAP_PARAMS.weight; // use the "alpha" parameter (weight) to calculate the new value
                  client_average_dBm[ind_aux] = Double.valueOf((double)Math.round(1000*Math.log10(client_average))/100); //Average power in dBm with 2 decimals
                }
              }else{
                // It is the first time we have data of this STA in this AP
                client_average_dBm[ind_aux] = rssi;
              }
            }
            rssiData.put(eth,client_average_dBm); // Store all the data
          }
          client_index++;
        }
        System.out.println("\033[K\r[SecureAPSelection] Processing done in: " + (System.currentTimeMillis()-time) + " ms");
        System.out.println("\033[K\r[SecureAPSelection] ====================");
        System.out.println("\033[K\r[SecureAPSelection] ");

        System.out.println(showAPsLine + " - RSSI [dBm]\033[00m");

        // Now comparation and handoff if it's needed
        time = System.currentTimeMillis();

        ps.println(time + " ms"); // Log file

        // Array with the IP addresses of the Agents
        InetAddress[] agentsArray = agents.toArray(new InetAddress[0]);

        // Write to screen the updated value of the averaged RSSI
        for (OdinClient oc: clients) {

          client_index = 0;
          int safe_AP_index = 0;
          MACAddress eth = oc.getMacAddress(); // client MAC

          Double[] client_dBm = new Double[num_agents];

          InetAddress clientAddr = oc.getIpAddress();
          InetAddress agentAddr = oc.getLvap().getAgent().getIpAddress();

          if(clientAddr.equals(nullAddr))// If client not assigned, go to next one (associated, but without IP address)
            continue;

          System.out.println("\033[K\r[SecureAPSelection] \t\t\t\tClient " + clientAddr + " in agent " + agentAddr);
          ps.println("\tClient " + clientAddr + " in agent " + agentAddr); // Log in file

          // Recover the information
          client_dBm = rssiData.get(eth);

          if (client_dBm != null){// Array with rssi

            Double maxRssi = client_dBm[0]; // Start with first rssi
            Double maxevdpDiff = client_dBm[0] - evdpRSSI[0]; //Start with first RSSI gap with eavesdropper 
            Double minEve = evdpRSSI[0];
            Double currentRssi = null;
            Double currentEvdpDiff = null;
            Double currentEveRSSI = null;

            for(ind_aux = 1; ind_aux < client_dBm.length; ind_aux++){//Get the index of the AP where the STA has the highest RSSI, VIP AP not considered

              if((client_dBm[ind_aux]>maxRssi)&&(!vipAPAddr.equals(agentsArray[ind_aux]))){
                maxRssi=client_dBm[ind_aux];
                client_index = ind_aux;
              }
            }
            //Repeat for eavesdropper to find max diff
            for(ind_aux = 1; ind_aux < client_dBm.length; ind_aux++){//Get the index of the AP where the STA has the highest RSSI superiority to eavesdropper

                if(((client_dBm[ind_aux]-evdpRSSI[ind_aux])>maxevdpDiff)&&(!vipAPAddr.equals(agentsArray[ind_aux]))){
                	maxevdpDiff=client_dBm[ind_aux]-evdpRSSI[ind_aux];
                	if (SMARTAP_PARAMS.mode.equals("MAXDIF")) {
                		safe_AP_index = ind_aux;
                	}                }
              }
            
            //Repeat for eavesdropper to find AP with minimum rssi
            for(ind_aux = 1; ind_aux < client_dBm.length; ind_aux++){//Get the index of the AP where the eve has the lowest RSSI and STA has RSSI above threshold

                if((evdpRSSI[ind_aux]<minEve)&&(client_dBm[ind_aux]>SMARTAP_PARAMS.signal_threshold)&&(!vipAPAddr.equals(agentsArray[ind_aux]))){
                	minEve=evdpRSSI[ind_aux];
                	if (SMARTAP_PARAMS.mode.equals("FAREV")) {
                		safe_AP_index = ind_aux;
                	}
                }
              }

            // Printf with colours
            System.out.print("\033[K\r[SecureAPSelection] ");

            // write to the screen the information with colours
            for(ind_aux = 0; ind_aux < client_dBm.length; ind_aux++){

              if(agentsArray[ind_aux].equals(agentAddr)){ // Current AP

                currentRssi = client_dBm[ind_aux];
                currentEvdpDiff = client_dBm[ind_aux] - evdpRSSI[ind_aux];
                currentEveRSSI = evdpRSSI[ind_aux];
                if(agentsArray[ind_aux].equals(vipAPAddr)){
                  System.out.print("[\033[48;5;3;1m" + String.format("%.2f",client_dBm[ind_aux]) + "\033[00m]"); // Olive
                }else{
                  System.out.print("[\033[48;5;29;1m" + String.format("%.2f",client_dBm[ind_aux]) + "\033[00m]"); // Dark Green
                }
                ps.println("\t\t[Associated] Rssi in agent " + agentsArray[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file
                ps.println("\t\t[Associated] Diff Rssi in agent " + agentsArray[ind_aux] + ": " + currentEvdpDiff + " dBm"); // Log in file

              }else{
       //original_code         //if(ind_aux==client_index){ // Max, VIP AP not considered
                if(ind_aux==safe_AP_index){ // Max, VIP AP not considered

                  System.out.print("[\033[48;5;88m" + String.format("%.2f",client_dBm[ind_aux]) + "\033[00m]"); // Dark red
                  ps.println("\t\t[BetterAP] Rssi in agent " + agentsArray[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file
                  ps.println("\t\t[BetterAP] Diff Rssi in agent " + agentsArray[ind_aux] + ": " + (client_dBm[ind_aux]-evdpRSSI[ind_aux]) + " dBm");
                }else{
                  if(agentsArray[ind_aux].equals(vipAPAddr)){
                    System.out.print("[\033[48;5;94m" + String.format("%.2f",client_dBm[ind_aux]) + "\033[00m]"); // Orange
                  }else{
                    System.out.print("["+ String.format("%.2f",client_dBm[ind_aux]) +"]"); // No color
                  }
                  ps.println("\t\t[WorseAP] Rssi in agent " + agentsArray[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file
                  ps.println("\t\t[WorseAP] Diff Rssi in agent " + agentsArray[ind_aux] + ": " + (client_dBm[ind_aux]-evdpRSSI[ind_aux]) + " dBm"); // Log in file

                }
              } 
            }
            // End prinft with colours


            // this is used for all the modes except FF. If you also want a threshold in FF mode, substitute the next line with if(true){
            //if(!SMARTAP_PARAMS.mode.equals("xx")){ ping pong effect
			if(SMARTAP_PARAMS.mode.equals("MAXDIF")){ 
            //Original_code  //if (!agentsArray[client_index].equals(agentAddr)){ // If the agent to which the STA is associated is not the one with the highest RSSI, change to the best RSSI
              if (!agentsArray[safe_AP_index].equals(agentAddr)){ // If the agent to which the STA is associated is not the one with the highest RSSI gap to eavesdropper, change to the best 

                //If Rssi threshold is reached, check hystheresis    
                //if(currentRssi<SMARTAP_PARAMS.signal_threshold){
            	  if(maxevdpDiff>(currentEvdpDiff-2)){
                  Long handoffTime = handoffDate.get(eth);

                  //If hystheresis has expired, handoff to the AP with the highest RSSI
                  if((handoffTime==null)||((System.currentTimeMillis()-handoffTime.longValue())/1000>SMARTAP_PARAMS.hysteresis_threshold)){

                    handoffClientToAp(eth,agentsArray[safe_AP_index]);
                    handoffDate.put(eth,Long.valueOf(System.currentTimeMillis())); // store the time for checking the hysteresis next time
                    System.out.println(" - Handoff >--->--->---> "+agentsArray[safe_AP_index]);
                    ps.println("\t\t[Action] Handoff to agent: " + agentsArray[safe_AP_index]+" as maximum RSSI gap"); // Log in file

                  }else{
                    System.out.println(" - No Handoff: Hysteresis time not expired");
                    ps.println("\t\t[No Action] No Handoff: Hysteresis time not expired"); // Log in file
                  }

                }else{
                  // The threshold is not reached
                    System.out.println(" - No Handoff: 2 dBm Rssi gap threshold not reached");
                    ps.println("\t\t[No Action] No Handoff: 2 dBm Rssi gap threshold not reached"); // Log in file

                }
              }else{
                System.out.println(""); // Best AP already
                ps.println("\t\t[No Action] There is no safer Rssi measured"); // Log in file
              }
            } else	if(SMARTAP_PARAMS.mode.equals("FAREV")){ 
	            //Original_code  //if (!agentsArray[client_index].equals(agentAddr)){ // If the agent to which the STA is associated is not the one with the highest RSSI, change to the best RSSI
	              if (!agentsArray[safe_AP_index].equals(agentAddr)){ // If the agent to which the STA is associated is not the one with the min RSSI of eavesdropper, change to the best 

	                //If RSSI threshold is reached, check hysteresis    
	                //if(currentRssi<SMARTAP_PARAMS.signal_threshold){
	            	  if(minEve<(currentEveRSSI-3)){
	                  Long handoffTime = handoffDate.get(eth);
	                  ps.println(" this is FAREV ");
	                  //If hysteresis has expired, handoff to the AP with the highest RSSI
	                  if((handoffTime==null)||((System.currentTimeMillis()-handoffTime.longValue())/1000>SMARTAP_PARAMS.hysteresis_threshold)){

	                    handoffClientToAp(eth,agentsArray[safe_AP_index]);
	                    handoffDate.put(eth,Long.valueOf(System.currentTimeMillis())); // store the time for checking the hysteresis next time
	                    System.out.println(" - Handoff >--->--->---> "+agentsArray[safe_AP_index]);
	                    ps.println("\t\t[Action] Handoff to agent: " + agentsArray[safe_AP_index] + "as furthest AP to Eve"); // Log in file

	                  }else{
	                    System.out.println(" - No Handoff: Hysteresis time not expired");
	                    ps.println("\t\t[No Action] No Handoff: Hysteresis time not expired"); // Log in file
	                  }

	                }else{
	                  // The threshold is not reached
	                    System.out.println(" - No Handoff: 3 dBm Rssi gap threshold not reached");
	                    ps.println("\t\t[No Action] No Handoff: 3 dBm Rssi gap threshold not reached"); // Log in file
	                }
	              }else{
	                System.out.println(""); // Best AP already
	                ps.println("\t\t[No Action] There is no safer Rssi measured"); // Log in file
	              }
	            }
			

          }else{
            System.out.println("\033[K\r[SecureAPSelection] No data received");
          }
        }
        

        ///////////////////////////////////////////////////////////////////////////////
 
          
        ps.flush();
        System.out.println("\033[K\r[SecureAPSelection] Assignation done innn: " + (System.currentTimeMillis()-time) + " ms");
        System.out.println("\033[K\r[SecureAPSelection] ====================");
        System.out.println("\033[K\r");
        Thread.sleep(SMARTAP_PARAMS.pause); // If a pause or a period is needed
        System.out.print("\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[0;0H"); // Clear lines above and return to console 0,0
    } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private Double getRssiFromRxStats(MACAddress clientMAC, String arr){ // Process the string with all the data, it saves 2 ms if done inside the app vs. in agent

    for (String elem : arr.split("\n")){//Split string in STAs

      String row[] = elem.split(" ");//Split string in MAC and rssi

      if (row.length != 2) { // If there is more than 2 items, next one
        continue;
      }

      MACAddress eth = MACAddress.valueOf(row[0].toLowerCase());

      if (clientMAC.equals(eth)){//If it belongs to the client, return rssi

        Double rssi = Double.parseDouble(row[1]);

        if(rssi!=null)
          return rssi;
      }
    }
    return -99.9;//Not heard by the AP, return -99.9
  }

  


@SuppressWarnings("finally")
//private Double getAnotherRSSI (InetAddress agentIP, MACAddress macSTA) throws InterruptedException {
private Double getAnotherRSSI (String agentIP, MACAddress macSTA) throws InterruptedException {
	
  BufferedReader objReader = null;
  Double rssi = null;
  try {
	  String strCurrentLine;
	  Process p;
	  String[] cmd = new String[]{"ssh", "root@" + agentIP , "grep " + macSTA.toString() + " /tmp/myOutput-01.csv"};

	  p=Runtime.getRuntime().exec(cmd);
	  objReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	  if ((strCurrentLine = objReader.readLine()) != null) {
		  String[] values = strCurrentLine.split(",");

		  MACAddress recMacSTA = null;
		  recMacSTA = MACAddress.valueOf(values[0]);
		  //System.out.println("MAC is " + recMacSTA.toString() + "so it is " + macSTA.equals(recMacSTA));
		  if (macSTA.equals(recMacSTA)) {
			  rssi = Double.parseDouble(values[3]);

		  }
	  }
	  p.waitFor();
	  //System.out.println ("exit: " + p.exitValue());
	  p.destroy();
	  } 
  
  catch (IOException e) {

   e.printStackTrace();
   } 
  finally {
	  try {
		  if (objReader != null)
			  objReader.close();
		  }
	  catch (final IOException ex) {
		  ex.printStackTrace();
		  }
   
   return rssi;

        
  }
 
 }
}

