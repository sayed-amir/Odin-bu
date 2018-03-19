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
import java.math.*;

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
  Set<InetAddress> agents;

  private int[] channels = null;
  private int num_channels = 0;
  private int num_agents = 0;
  private String[][] vals_rx = null;


  private long time = 0L; // Compare timestamps in ms
  
  InetAddress nullAddr = null;

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
    agents = getAgents();
    num_agents = agents.size(); // Number of agents
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
    ps.println("\tTxpowerSTA: " + SMARTAP_PARAMS.txpowerSTA);
    ps.println("\tTxpowerSTA: " + SMARTAP_PARAMS.thReqSTA);
    ps.println("\tFilename: " + SMARTAP_PARAMS.filename);

    String showAPsLine = "\033[K\r[SmartAPSelection] ";

    // Get channels from APs, assuming there is no change in all operation, if already in array->0
    for (InetAddress agentAddr: agents) {

      String hostIP = agentAddr.getHostAddress(); // Build line for user interface
      showAPsLine = showAPsLine + "\033[0;1m[ AP" + hostIP.substring(hostIP.lastIndexOf('.')+1,hostIP.length()) + " ]";

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

    Map<MACAddress, Double[]> ffData = new HashMap<MACAddress, Double[]> (); // Map to store Throughput available for each STA in all APs

    System.out.print("\033[2J"); // Clear screen and cursor to 0,0
    char[] progressChar = new char[] { '-', '\\', '|', '/' };
    int progressIndex = 0;

    

    try { // Create Ip to compare with clients not assigned
      nullAddr = InetAddress.getByName("0.0.0.0");
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }

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
          for (InetAddress agentAddr: agents) {

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

          for (InetAddress agentAddr: agents) {

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
                  client_average = client_average*(1-SMARTAP_PARAMS.weight) + client_signal*SMARTAP_PARAMS.weight;
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
        System.out.println("\033[K\r[SmartAPSelection] ");

        System.out.println(showAPsLine + " - RSSI [dBm]\033[00m");

        // Now comparation and handoff if it's needed
        time = System.currentTimeMillis();

        ps.println(time + " ms"); // Log in file

        InetAddress[] agentsArray = agents.toArray(new InetAddress[0]);

        for (OdinClient oc: clients) {

          client_index = 0;

          MACAddress eth = oc.getMacAddress(); // client MAC

          Double[] client_dBm = new Double[num_agents];

          InetAddress clientAddr = oc.getIpAddress();
          InetAddress agentAddr = oc.getLvap().getAgent().getIpAddress();

          if(clientAddr.equals(nullAddr))// If client not assigned, next one
            continue;

          System.out.println("\033[K\r[SmartAPSelection] \t\t\t\tClient " + clientAddr + " in agent " + agentAddr);
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



            for(ind_aux = 0; ind_aux < client_dBm.length; ind_aux++){

              if(agentsArray[ind_aux].equals(agentAddr)){ // Current AP

                currentRssi = client_dBm[ind_aux];
                System.out.print("[\033[48;5;29;1m" + String.format("%.2f",client_dBm[ind_aux]) + "\033[00m]"); // Dark Green
                ps.println("\t\t[Associated] Rssi in agent " + agentsArray[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file

              }else{
                if(ind_aux==client_index){ // Max

                  System.out.print("[\033[48;5;88m" + String.format("%.2f",client_dBm[ind_aux]) + "\033[00m]"); // Dark red
                  ps.println("\t\t[BetterAP] Rssi in agent " + agentsArray[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file

                }else{
                  System.out.print("["+ String.format("%.2f",client_dBm[ind_aux]) +"]"); //
                  ps.println("\t\t[WorseAP] Rssi in agent " + agentsArray[ind_aux] + ": " + client_dBm[ind_aux] + " dBm"); // Log in file
                }
              } 
            }
            // End prinft with colours
            if(SMARTAP_PARAMS.mode.equals("RSSI")||SMARTAP_PARAMS.mode.equals("BALANCER")){ // In BALANCER mode, it will assign STAs to APs always with higher RSSI than threshold, so there is not ping pong effect
              if (!agentsArray[client_index].equals(agentAddr)){ // Change to the best RSSI

                //If Rssi threshold is reached, handoff
                if(currentRssi<SMARTAP_PARAMS.signal_threshold){

                  Long handoffTime = handoffDate.get(eth);

                  //If Time threshold is reached, handoff
                  if((handoffTime==null)||((System.currentTimeMillis()-handoffTime.longValue())/1000>SMARTAP_PARAMS.hysteresis_threshold)){

                    handoffClientToAp(eth,agentsArray[client_index]);
                    handoffDate.put(eth,Long.valueOf(System.currentTimeMillis()));
                    System.out.println(" - Handoff >--->--->---> "+agentsArray[client_index]);
                    ps.println("\t\t[Action] Handoff to agent: " + agentsArray[client_index]); // Log in file

                  }else{
                    System.out.println(" - No Handoff: Hysteresis time not reached");
                    ps.println("\t\t[No Action] No Handoff: Hysteresis time not reached"); // Log in file
                  }
                }else{
                  if(!SMARTAP_PARAMS.mode.equals("BALANCER")){
                    System.out.println(" - No Handoff: Rssi Threshold not reached");
                    ps.println("\t\t[No Action] No Handoff: Rssi Threshold not reached"); // Log in file
                  }else{
                    System.out.println(" - Assigned by BALANCER");
                    ps.println("\t\t[No Action] No Handoff: Assigned by BALANCER"); // Log in file
                  }
                }
              }else{
                System.out.println(""); // Best AP already
                ps.println("\t\t[No Action] There is no better Rssi heard"); // Log in file
              }
            }
            if(SMARTAP_PARAMS.mode.equals("FF")){ // Calculate FF data
              System.out.println("");

              ind_aux = 0;
              Double[] TH_av = new Double[num_agents];

              for (InetAddress agentAddrFF: agents) {

                if(oc.getLvap().getAgent().getIpAddress().equals(agentAddrFF)){ // If associated
                  // Reception statistics
                  Map<MACAddress, Map<String, String>> vals_rx_FF = getRxStatsFromAgent(agentAddrFF);
                  Map<String, String> vals_entry_rx = vals_rx_FF.get(eth);
                  if(vals_entry_rx != null){
                    //System.out.println("\033[K\r[SmartAPSelection] avg rate: " + vals_entry_rx.get("avg_rate") + " kbps");
                    Double clientRate = Double.parseDouble(vals_entry_rx.get("avg_rate"));
                    // t and T
                    double[] tTValues = getTransmissionTime(clientRate.doubleValue());
                    double p = 0.98*(tTValues[1]/tTValues[0]);
                    //System.out.println("\033[K\r[SmartAPSelection] Th_av["+ind_aux+"]: " + String.format("%.2f",clientRate.doubleValue()*p));
                    TH_av[ind_aux] = clientRate.doubleValue()*p;
                  }else{
                    TH_av[ind_aux] = 0.0;
                  }

                }else{ // Not associated
                  double txpowerAP = Math.pow(10.0, (getTxPowerFromAgent(agentAddrFF)) / 10.0);
                  double txpowerSTA = Math.pow(10.0, (SMARTAP_PARAMS.txpowerSTA) / 10.0);
                  double rssiDL = client_dBm[ind_aux]+10.0*Math.log10(txpowerAP/txpowerSTA);
                  double snr = 90.0 + rssiDL;
                  double maxRate = getOFDMRates(snr);
                  double[] tTValues = getTransmissionTime(maxRate);

                  HashSet<OdinClient> clients_FF = new HashSet<OdinClient>(getClientsFromAgent(agentAddrFF));
                  double t2Value = calculateT2(clients_FF.size(),tTValues[0]);
                  double p = 0.98*(tTValues[1]/t2Value);
                  //System.out.println("\033[K\r[SmartAPSelection] Th_av["+ind_aux+"]: " + String.format("%.2f",maxRate*p));
                  TH_av[ind_aux] = maxRate*p;
                }
                ind_aux++;
              }
              // Save TH_av in map
              ffData.put(eth,TH_av);
            }
          }else{
            System.out.println("\033[K\r[SmartAPSelection] No data received");
          }
        }
        if(SMARTAP_PARAMS.mode.equals("FF")){ // Show FF results and handoff if necessary
          System.out.println("\033[K\r[SmartAPSelection] ====================");
          System.out.println(showAPsLine + " - FF Throughput available [Mbps]\033[00m");

          for (OdinClient oc: clients) {


            client_index = 0;
            MACAddress eth = oc.getMacAddress(); // client MAC
            InetAddress clientAddr = oc.getIpAddress();
            InetAddress agentAddr = oc.getLvap().getAgent().getIpAddress();

            if(clientAddr.equals(nullAddr))// If client not assigned, next one
              continue;

            System.out.println("\033[K\r[SmartAPSelection] \t\t\t\tClient " + clientAddr + " in agent " + agentAddr);
            ps.println("\tClient " + clientAddr + " in agent " + agentAddr); // Log in file

            Double[] th_avFF = ffData.get(eth);
            //System.out.println("\033[K\r[SmartAPSelection] th_avFF= "+Arrays.toString(th_avFF));
            Double maxFF = calculateFittingnessFactor(SMARTAP_PARAMS.thReqSTA,th_avFF[0]); // Start with first Th_av
            Double currentTh_av = null;
            System.out.println("\033[K\r[SmartAPSelection] ff[0]="+maxFF);
            for(ind_aux = 1; ind_aux < th_avFF.length; ind_aux++){//Get max position and calculate FF

              Double currentFF = calculateFittingnessFactor(SMARTAP_PARAMS.thReqSTA,th_avFF[ind_aux]);

              if(currentFF>maxFF){
                maxFF=currentFF;
                client_index = ind_aux;
              }
              System.out.println("\033[K\r[SmartAPSelection] ff["+ind_aux+"]="+currentFF);
            }

            System.out.print("\033[K\r[SmartAPSelection] ");

            for(ind_aux = 0; ind_aux < th_avFF.length; ind_aux++){

              if(agentsArray[ind_aux].equals(agentAddr)){ // Current AP 

                currentTh_av = th_avFF[ind_aux];
                System.out.print("[ \033[48;5;29;1m" + String.format("%.2f",th_avFF[ind_aux]/1000.0) + "\033[00m]"); // Dark Green
                ps.println("\t\t[Associated] Throughput in agent " + agentsArray[ind_aux] + ": " + th_avFF[ind_aux] + " kbps"); // Log in file

              }else{
                if(ind_aux==client_index){ // Max

                  System.out.print("[ \033[48;5;88m" + String.format("%.2f",th_avFF[ind_aux]/1000.0) + "\033[00m]"); // Dark red
                  ps.println("\t\t[BetterAP] Throughput in agent " + agentsArray[ind_aux] + ": " + th_avFF[ind_aux] + " kbps"); // Log in file

                }else{
                  System.out.print("[ "+ String.format("%.2f",th_avFF[ind_aux]/1000.0) +"]"); //
                  ps.println("\t\t[WorseAP] Throughput in agent " + agentsArray[ind_aux] + ": " + th_avFF[ind_aux] + " kbps"); // Log in file
                }
              } 
            }
            if (!agentsArray[client_index].equals(agentAddr)){ // Change to the best FF

              Long handoffTime = handoffDate.get(eth);

              //If Time threshold is reached, handoff
              if((handoffTime==null)||((System.currentTimeMillis()-handoffTime.longValue())/1000>SMARTAP_PARAMS.hysteresis_threshold)){

                if(!(currentTh_av==0.0)){
                  handoffClientToAp(eth,agentsArray[client_index]);
                  handoffDate.put(eth,Long.valueOf(System.currentTimeMillis()));
                  System.out.println("\033[0;1m - Handoff >--->--->---> "+agentsArray[client_index]+"\033[00m");
                  ps.println("\t\t[Action] Handoff to agent: " + agentsArray[client_index]); // Log in file
                }else{
                  System.out.println(""); // No rate received
                }  
              }else{
                System.out.println("\033[0;1m - No Handoff: Hysteresis time not reached\033[00m");
                ps.println("\t\t[No Action] No Handoff: Hysteresis time not reached"); // Log in file
              }

            }else{
              System.out.println(""); // Best AP already
              ps.println("\t\t[No Action] There is no better FF"); // Log in file
            }
          }
          System.out.println("\033[K\r[SmartAPSelection] ====================");
        }
        if(SMARTAP_PARAMS.mode.equals("BALANCER")){

          Map<MACAddress, InetAddress> assignedClients = new HashMap<MACAddress, InetAddress> (); // Array with the balancer decission

          System.out.println("\033[K\r[SmartAPSelection] ====================");
          System.out.println(showAPsLine + " - Balancer\033[00m");
          System.out.print("\033[K\r[SmartAPSelection] ");

          assignedClients = simpleBalancerAlgorithm(rssiData, agentsArray, clients, SMARTAP_PARAMS.signal_threshold); // Very simple balancer algorithm
          
          System.out.println("");
          
          
          for(MACAddress eth:assignedClients.keySet()){
          
            Long handoffTime = handoffDate.get(eth);
            System.out.print("\033[K\r[SmartAPSelection] ");
            OdinClient clientHandoff = getClientFromHwAddress(eth);
            
            if((handoffTime==null)||((System.currentTimeMillis()-handoffTime.longValue())/1000>SMARTAP_PARAMS.hysteresis_threshold)){
              
              InetAddress assignedAgent = assignedClients.get(eth);
              if(getClientFromHwAddress(eth)!=null){
                handoffClientToAp(eth,assignedAgent);
                handoffDate.put(eth,Long.valueOf(System.currentTimeMillis()));
                System.out.print("\033[0;1mHandoff "+clientHandoff.getIpAddress()+" >--->--->---> "+assignedAgent+"\033[00m");
                ps.println("\t\t[Action] Handoff "+clientHandoff.getIpAddress()+" to agent: " + assignedAgent); // Log in file
              }
              
            }else{
            
              System.out.print("No Handoff "+clientHandoff.getIpAddress()+": Hysteresis time not reached");
              ps.println("\t\t[No Action] No Handoff: Hysteresis time not reached"); // Log in file
              
            }
            System.out.println("");
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
  private double calculateT2(int numberOfStas, double tValue){
    double cwMin = 15.0;
    double slot = 0.000009;
    double result = tValue;

    if(numberOfStas>0){

      double pcValue = 1-Math.pow(1-(1/cwMin),numberOfStas);
      double tCont = (cwMin/2)*slot*(1+pcValue)/(2*(numberOfStas+1));

      result = tValue + tCont;

    }
    return result;
  }
  private double[] getTransmissionTime(double avgRate){ // Returns transmission times to calculate THavg

    double[] result;

    if(avgRate==54000.0){ // If higher value not reached, we use the next lower values
      result = new double[]{326.0,228.0};
      return result;
    }else if (avgRate>=48000.0){
      result = new double[]{354.0,256.0};
      return result;
    }else if (avgRate>=36000.0){
      result = new double[]{442.0,344.0};
      return result;
    }else if (avgRate>=24000.0){
      result = new double[]{610.0,512.0};
      return result;
    }else if (avgRate>=18000.0){
      result = new double[]{786.0,684.0};
      return result;
    }else if (avgRate>=12000.0){
      result = new double[]{1126.0,1024.0};
      return result;
    }else if (avgRate>=9000.0){
      result = new double[]{1478.0,1364.0};
      return result;
    }else { // 6 Mbps
      result = new double[]{2158.0,2044.0};
      return result;
    }
  }
  private double getOFDMRates(double SNR){ // Returns ODFM rates to calculate THavg
	
	double result = 0.0;
	
	if(SNR>21.0){ // If higher value not reached, we use the next lower values
		result = 54000.0;
		return result;
	}else if (SNR>=20.0){
		result = 48000.0;
		return result;
	}else if (SNR>=16.0){
		result = 36000.0;
		return result;
	}else if (SNR>=12.0){
		result = 24000.0;
		return result;
	}else if (SNR>=9.0){
		result = 18000.0;
		return result;
	}else if (SNR>=7.0){
		result = 12000.0;
		return result;
	}else if (SNR>=5.0){
		result = 9000.0;
		return result;
	}else if (SNR>=4.0){
		result = 6000.0;
		return result;
	}else { // Less than 4 dB => 0.0
		return result;
	}
  }
  private double calculateFittingnessFactor(double Rreq, double Rb){

    double shaping = 5, shaping_k = 1, shaping_mine = 1.3, ff = 0;

    if(Math.abs(Rb) > 1e-6){
      double U = Math.pow(Rb * shaping_mine/Rreq, shaping) / (1 + Math.pow(Rb*shaping_mine/Rreq, shaping));
      double lambda = 1 - Math.pow(Math.E, -shaping_k / (Math.pow(shaping-1, 1/shaping) + Math.pow(shaping-1, (1-shaping)/shaping)));
      ff = (1 - Math.pow(Math.E, -shaping_k * U / (Rb * shaping_mine / Rreq))) / lambda;
    }
    return ff;
  }
  private Map<MACAddress, InetAddress> simpleBalancerAlgorithm(Map<MACAddress, Double[]> rssiData, InetAddress[] agentsArray, HashSet<OdinClient> clients, Double threshold){ // Print load in each AP and returns array of agents to assign

    int ind_aux = 0;
    int agent_index = 0;
    int max_index = 0;
    int[] numStasPerAgent = new int[agentsArray.length];
    boolean orderHandoff = false;
    Map<MACAddress, InetAddress> arrayHandoff = new HashMap<MACAddress, InetAddress> ();

    HashSet<OdinClient> clients_Balancer;

    int maxStas = 0;
    int minStas = Integer.MAX_VALUE;
    int numberOfAgentsAvailable = 0;
    int num_clients = clients.size();
    

    for (InetAddress agentAddrBalancer: agentsArray) { // Create array with number of STAs for each AP, find the one with lower number

      clients_Balancer = new HashSet<OdinClient>(getClientsFromAgent(agentAddrBalancer));

      int numberOfStas = clients_Balancer.size();
      
      boolean stasToMove = false;

      numStasPerAgent[ind_aux] = numberOfStas;
      
      for(MACAddress eth:rssiData.keySet()){ // If there is not a STA with enougth RSSI, not handoff
        Double[] client_dBm = rssiData.get(eth);
        if(client_dBm[ind_aux]>SMARTAP_PARAMS.signal_threshold){
          stasToMove=true;
        }
      }

      if((numberOfStas <= minStas)&&(stasToMove)){
        minStas = numberOfStas;
        agent_index = ind_aux;
      }
      if(numberOfStas >= maxStas){
        maxStas = numberOfStas;
        max_index = ind_aux;
      }
      if(stasToMove)
        numberOfAgentsAvailable++;
      ind_aux++;
    }
    ind_aux = 0;
    float mean = (float)num_clients/numberOfAgentsAvailable;
    int meanStas = Math.round(mean);

    for (InetAddress agentAddrBalancer: agentsArray) { // Print APs and number of STAs associated at it

      if((ind_aux==agent_index)&&(numStasPerAgent[ind_aux]<meanStas)&&(maxStas>meanStas)){ // min<mean and max>mean try to handoff a STA
        System.out.print("[\033[48;5;88;1m  "+numStasPerAgent[ind_aux]+"   \033[00m]");
        orderHandoff = true;
      }else{
        System.out.print("[  "+numStasPerAgent[ind_aux]+"   ]");
      }
      ind_aux++;
    }
    OdinClient clientHandoff=null;
    if(orderHandoff){
      clients_Balancer = new HashSet<OdinClient>(getClientsFromAgent(agentsArray[max_index]));
      double maxRssi=-99.9;
      for (OdinClient oc: clients_Balancer) {
        Double[] client_dBm = new Double[num_agents];
        MACAddress eth = oc.getMacAddress();
        InetAddress clientAddr = oc.getIpAddress();
        if(clientAddr.equals(nullAddr))// If client not assigned, next one
          continue;
        client_dBm = rssiData.get(eth);
        if (client_dBm != null){
          if((client_dBm[agent_index]>=maxRssi)&&(client_dBm[agent_index]>SMARTAP_PARAMS.signal_threshold)){
            maxRssi = client_dBm[agent_index];
            clientHandoff = oc;
          }
        }
      }
    }
    if(clientHandoff!=null){
      arrayHandoff.put(clientHandoff.getMacAddress(),agentsArray[agent_index]);
      System.out.print("\033[0;1m - Handoff ordered\033[00m");
    }
    return arrayHandoff;
  }
}
