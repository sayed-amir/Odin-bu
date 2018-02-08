package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Arrays;
import java.io.File;
import java.io.PrintStream;

import net.floodlightcontroller.odin.master.OdinApplication;
import net.floodlightcontroller.odin.master.OdinClient;
import net.floodlightcontroller.odin.master.OdinMaster.SmartApSelectionParams;
import net.floodlightcontroller.util.MACAddress;

public class SmartApSelection extends OdinApplication {

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

  private int[] channels = null;
  private int num_channels = 0;
  private int num_agents = 0;
  private String[][] vals_rx = null;
  
  
  private long time = 0L; // Compare timestamps in ms
  
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
	System.out.println("[SmartAPSelection] Start");
	this.SMARTAP_PARAMS = getSmartApSelectionParams();
    try {
      System.out.println("[SmartAPSelection] Sleep for " + SMARTAP_PARAMS.time_to_start);
      Thread.sleep(SMARTAP_PARAMS.time_to_start);
    } catch (InterruptedException e) {
	  e.printStackTrace();
    }
    
    // Write on file integration
    PrintStream ps = null;
		
	if(SMARTAP_PARAMS.filename.length()>0){
        File f = new File(SMARTAP_PARAMS.filename);
        try {
			ps = new PrintStream(f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    num_agents = getAgents().size(); // Number of agents
    channels = new int[num_agents]; // Array to store the channels in use
    int[] channelsAux = new int[num_agents];
    
    ps.println("[SmartAPSelection] Log file " + SMARTAP_PARAMS.filename); // Log in file
    ps.println("[SmartAPSelection] Parameters:");
    ps.println("\tTime_to_start: " + SMARTAP_PARAMS.time_to_start);
	ps.println("\tScanning_interval: " + SMARTAP_PARAMS.scanning_interval);
	ps.println("\tAdded_time: " + SMARTAP_PARAMS.added_time);
	ps.println("\tSignal_threshold: " + SMARTAP_PARAMS.signal_threshold);
	ps.println("\tHysteresis_threshold: " + SMARTAP_PARAMS.hysteresis_threshold);
	ps.println("\tPrevius_data_weight (alpha): " + SMARTAP_PARAMS.weight);
	ps.println("\tPause between scans: " + SMARTAP_PARAMS.pause);
	ps.println("\tMode: " + SMARTAP_PARAMS.mode);
	ps.println("\tFilename: " + SMARTAP_PARAMS.filename);
    
    // Get channels from APs, assuming there is no change in all operation, if already in array->0
    for (InetAddress agentAddr: getAgents()) {
	  int chann = getChannelFromAgent(agentAddr);
	  //System.out.println("[SmartAPSelection] = Channels " + Arrays.toString(channels));
	  Arrays.sort(channelsAux);
	  //System.out.println("[SmartAPSelection] = Search for "+chann+": " + Arrays.binarySearch(channelsAux, chann));
	  
	  if(Arrays.binarySearch(channelsAux, chann) < 0){// if already in array, not necessary to add it
	    channelsAux[num_channels] = chann;
        channels[num_channels] = chann;
		//System.out.println("[SmartAPSelection] Chann added "+chann);
	  }
      System.out.println("[SmartAPSelection] AP " + agentAddr + " in channel: " + chann);
      ps.println("[SmartAPSelection] AP " + agentAddr + " in channel: " + chann); // Log in file
      num_channels++;
    }
    ps.println("[SmartAPSelection]");
    ps.flush();
    
    vals_rx = new String[num_channels][num_agents]; // Matrix to store the results from agents
    Map<MACAddress, Double[]> rssiData = new HashMap<MACAddress, Double[]> (); // Map to store RSSI for each STA in all APs
    Map<MACAddress, Long> handoffDate = new HashMap<MACAddress, Long> (); // Map to store last handoff for each STA FIXME: Maybe create struct
    
    System.out.print("\033[2J"); // Clear screen and cursor to 0,0
    char[] progressChar = new char[] { '-', '\\', '|', '/' };
    int progressIndex = 0;
    
	while (true) {
      try {
        Thread.sleep(100);
        
		clients = new HashSet<OdinClient>(getClients());
		
		int num_clients = clients.size(); // Number of STAs

		if (num_clients == 0){ // No clients, no need of scan
		  System.out.println("\033[K\r[SmartAPSelection] ====================");
		  System.out.println("\033[K\r[SmartAPSelection] Proactive AP Handoff");
		  System.out.println("\033[K\r[SmartAPSelection]");
		  System.out.println("\033[K\r[SmartAPSelection] " + progressChar[progressIndex++] + "No clients associated, waiting for connection");
		  System.out.println("\033[K\r[SmartAPSelection] ====================");
		  if(progressIndex==3)
		    progressIndex=0;
          System.out.print("\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[K\r\n\033[0;0H"); // Clear lines above and return to console 0,0
		  continue;
		}
		int[] clientsChannels = new int[num_clients]; // Array with the indexes of channels, better performance in data process
		
		// Various indexes
		int client_index = 0;
		int client_channel = 0;
		int ind_aux = 0;
		
        System.out.println("\033[K\r[SmartAPSelection] ====================");
		System.out.println("\033[K\r[SmartAPSelection] Proactive AP Handoff");
		System.out.println("\033[K\r[SmartAPSelection]");
		
		
		
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
        
		//For each channel used in owned APs
		
		for (int channel = 0 ; channel < num_channels ; ++channel) {
			
		  if(channels[channel]==0)
		    continue;
		
          int agent = 0;
          scanningAgents.clear();
          for (InetAddress agentAddr: getAgents()) {
	  
            // Request statistics
			result = requestScannedStationsStatsFromAgent(agentAddr, channels[channel], SCANNED_SSID);		
			scanningAgents.put(agentAddr, result);
          }					

          try {
            Thread.sleep(SMARTAP_PARAMS.scanning_interval + SMARTAP_PARAMS.added_time);
          } 
          catch (InterruptedException e) {
            e.printStackTrace();
          }
          
          for (InetAddress agentAddr: getAgents()) {
    
            // Reception statistics 
            if (scanningAgents.get(agentAddr) == 0) {
			  System.out.println("\033[K\r[SmartAPSelection] Agent BUSY during scanning operation");
              continue;				
            }
			vals_rx[channel][agent] = getScannedStaRssiFromAgent(agentAddr);
			agent++;
          }
          //Thread.sleep(200); // Give some time to the AP
        }
        
        System.out.println("\033[K\r[SmartAPSelection] Scanning done in: " + (System.currentTimeMillis()-time) + " ms");
        
        // All the statistics stored, now process
        time = System.currentTimeMillis();
        client_index = 0;
        ind_aux = 0;
      
        // For each client associated
      
        for (OdinClient oc: clients) {
        
          MACAddress eth = oc.getMacAddress(); // client MAC
      
          client_channel = clientsChannels[client_index]; // row in the matrix
          
          for ( ind_aux = 0; ind_aux < num_agents; ind_aux++){// For 
        
            String arr = vals_rx[client_channel][ind_aux]; // String with "MAC rssi\nMAC rssi\n..."
            
            Double rssi = getRssiFromRxStats(eth,arr); // rssi or -99.9
            
            Double[] client_average_dBm = new Double[num_agents];
            
            client_average_dBm = rssiData.get(eth);

            if (client_average_dBm == null){// First time STA is associated
              
              client_average_dBm = new Double[num_agents];
              Arrays.fill(client_average_dBm,-99.9);
              client_average_dBm[ind_aux] = rssi;
                
            }else{
              
              if((client_average_dBm[ind_aux]!=-99.9)&&(client_average_dBm[ind_aux]!=null)){
                if(rssi!=-99.9){
                  
                  Double client_signal = Math.pow(10.0, (rssi) / 10.0); // Linear power
                  Double client_average = Math.pow(10.0, (client_average_dBm[ind_aux]) / 10.0); // Linear power average
                  client_average = client_average*SMARTAP_PARAMS.weight + client_signal*(1-SMARTAP_PARAMS.weight);
                  client_average_dBm[ind_aux] = Double.valueOf((double)Math.round(1000*Math.log10(client_average))/100); //Average power in dBm with 2 decimals
                  
                }
              }else{
                client_average_dBm[ind_aux] = rssi;
              }
            }
            rssiData.put(eth,client_average_dBm);
          }
          client_index++;
        }
        System.out.println("\033[K\r[SmartAPSelection] Processing done in: " + (System.currentTimeMillis()-time) + " ms");
        System.out.println("\033[K\r[SmartAPSelection] ====================");
        
        // Now comparation and handoff if it's needed
        time = System.currentTimeMillis();
        
        ps.println(time + " ms"); // Log in file
        
        for (OdinClient oc: clients) {
		
		  client_index = 0;
		  
		  MACAddress eth = oc.getMacAddress(); // client MAC
		  
		  Double[] client_dBm = new Double[num_agents];
		  
		  InetAddress nullAddr = null;
		  
		  try { // Create Ip to compare with clients not assigned
            nullAddr = InetAddress.getByName("0.0.0.0");
          } 
          catch (UnknownHostException e) {
            e.printStackTrace();
          }
		  InetAddress clientAddr = oc.getIpAddress();
          InetAddress agentAddr = oc.getLvap().getAgent().getIpAddress();
          
          if(clientAddr.equals(nullAddr))// If client not assigned, next one
            continue;
          
          System.out.println("\033[K\r[SmartAPSelection] Client " + clientAddr + " in agent " + agentAddr);
          ps.println("\tClient " + clientAddr + " in agent " + agentAddr); // Log in file
          
          client_dBm = rssiData.get(eth);
          
          if (client_dBm != null){// Array with rssi
            
            Double maxRssi = client_dBm[0]; // Start with first rssi
            
            Double currentRssi = null;
            
            for(ind_aux = 1; ind_aux < client_dBm.length; ind_aux++){//Get max position
            
              if(client_dBm[ind_aux]>maxRssi){
                maxRssi=client_dBm[ind_aux];
                client_index = ind_aux;
              }
            }
            
            // Printf with colours
            System.out.print("\033[K\r[SmartAPSelection] ");
            
            InetAddress[] agents = getAgents().toArray(new InetAddress[0]);
          
            for(ind_aux = 0; ind_aux < client_dBm.length; ind_aux++){
            
              if(agents[ind_aux].equals(agentAddr)){ // Current AP
              
                currentRssi = client_dBm[ind_aux];
                System.out.print("[\033[48;5;29m" + client_dBm[ind_aux] + "\033[00m]"); // Dark Green
                ps.println("\t\t[Associated] Rssi in agent " + agents[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file
              
              }else{
                if(ind_aux==client_index){ // Max
                
                  System.out.print("[\033[48;5;88m" + client_dBm[ind_aux] + "\033[00m]"); // Dark red
                  ps.println("\t\t[BetterAP] Rssi in agent " + agents[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file
                
                }else{
                  System.out.print("["+ client_dBm[ind_aux] +"]"); //
                  ps.println("\t\t[WorseAP] Rssi in agent " + agents[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file
                }
              }
            
              
            }
            // End prinft with colours
            
            if (!agents[client_index].equals(agentAddr)){ // Change to the best RSSI
            
              //If Rssi threshold is reached, handoff
              if(currentRssi<SMARTAP_PARAMS.signal_threshold){
				  
                Long handoffTime = handoffDate.get(eth);
				
                //If Time threshold is reached, handoff
                if((handoffTime==null)||((System.currentTimeMillis()-handoffTime.longValue())/1000>SMARTAP_PARAMS.hysteresis_threshold)){

                  handoffClientToAp(eth,agents[client_index]);
                  handoffDate.put(eth,Long.valueOf(System.currentTimeMillis()));
                  System.out.println(" - Handoff >--->--->---> "+agents[client_index]);
                  
				  ps.println("\t\t[Action] Handoff to agent: " + agents[client_index]); // Log in file
				  
                }else{
                  System.out.println(" - No Handoff: Hysteresis time not reached");
                  ps.println("\t\t[No Action] No Handoff: Hysteresis time not reached"); // Log in file
                }
              }else{
                System.out.println(" - No Handoff: Rssi Threshold not reached");
                ps.println("\t\t[No Action] No Handoff: Rssi Threshold not reached"); // Log in file
              }
            }else{
              System.out.println(""); // Best AP already
              ps.println("\t\t[No Action] There is no better Rssi heard"); // Log in file
            }
            
          }else{
            System.out.println("\033[K\r[SmartAPSelection] No data received");
          }
		}
		ps.flush();
		System.out.println("\033[K\r[SmartAPSelection] Assignation done in: " + (System.currentTimeMillis()-time) + " ms");
        System.out.println("\033[K\r[SmartAPSelection] ====================");
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
}
