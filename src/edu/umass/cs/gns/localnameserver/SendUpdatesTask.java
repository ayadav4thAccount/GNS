package edu.umass.cs.gns.localnameserver;

import edu.umass.cs.gns.client.Intercessor;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.main.StartLocalNameServer;
import edu.umass.cs.gns.packet.ConfirmUpdateLNSPacket;
import edu.umass.cs.gns.packet.Packet;
import edu.umass.cs.gns.packet.UpdateAddressPacket;
import edu.umass.cs.gns.statusdisplay.StatusClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.TimerTask;

public class SendUpdatesTask extends TimerTask
{

//	int MAX_TIMEOUTS = 3;

  String name;
  //NameRecordKey nameRecordKey;
  UpdateAddressPacket updateAddressPacket;
  InetAddress senderAddress;
  int senderPort;
  int updateRequestID;
  HashSet<Integer> activesQueried;
  int timeoutCount = -1;
  long requestRecvdTime;

  public SendUpdatesTask(UpdateAddressPacket updateAddressPacket,
                         InetAddress senderAddress, int senderPort, long requestRecvdTime,
                         HashSet<Integer> activesQueried)
  {
    this.name = updateAddressPacket.getName();
    //this.nameRecordKey = updateAddressPacket.getRecordKey();
    this.updateAddressPacket = updateAddressPacket;
    this.senderAddress = senderAddress;
    this.senderPort = senderPort;
    this.activesQueried = activesQueried;
    this.requestRecvdTime = requestRecvdTime;
  }

  @Override
  public void run()
  {


    timeoutCount++;
//    long t0 = System.currentTimeMillis();
//    if (timeoutCount == 0 && t0 - requestRecvdTime > 10) {
//      GNS.getLogger().severe(" Long delay in Startup " + (t0 - requestRecvdTime));
//    }

    if (StartLocalNameServer.debugMode) GNS.getLogger().fine("ENTER name = " + name + " timeout = " + timeoutCount);

    if (timeoutCount > 0 && LocalNameServer.getUpdateInfo(updateRequestID) == null) {
      if (StartLocalNameServer.debugMode) GNS.getLogger().fine("UpdateInfo not found. Either update complete or invalid actives. Cancel task.");
      throw  new RuntimeException();
    }

    if (timeoutCount > 0 && System.currentTimeMillis() - requestRecvdTime > StartLocalNameServer.maxQueryWaitTime) {
      UpdateInfo updateInfo = LocalNameServer.removeUpdateInfo(updateRequestID);

      if (updateInfo == null) {
        GNS.getLogger().fine("TIME EXCEEDED: UPDATE INFO IS NULL!!: " + updateAddressPacket);
        throw  new RuntimeException();
      }
      if (StartLocalNameServer.debugMode) GNS.getLogger().fine("UPDATE FAILED no response until MAX-wait time: " + updateRequestID + " name = " + name);
      ConfirmUpdateLNSPacket confirmPkt = ConfirmUpdateLNSPacket.createFailPacket(updateAddressPacket);
      try {
        if (updateInfo.senderAddress != null && updateInfo.senderAddress.length() > 0 && updateInfo.senderPort > 0) {
          LNSListener.udpTransport.sendPacket(confirmPkt.toJSONObject(),
                  InetAddress.getByName(updateInfo.senderAddress), updateInfo.senderPort);
        } else if (StartLocalNameServer.runHttpServer) {
          Intercessor.getInstance().checkForResult(confirmPkt.toJSONObject());
        }
      } catch (JSONException e) {
        e.printStackTrace();
      } catch (UnknownHostException e) {
        e.printStackTrace();
      }
      String updateStats = updateInfo.getUpdateFailedStats(activesQueried, LocalNameServer.nodeID, updateAddressPacket.getRequestID());
      if (StartLocalNameServer.debugMode) GNS.getLogger().fine(updateStats);
      GNS.getStatLogger().fine(updateStats);
      throw  new RuntimeException();
    }




    if (LocalNameServer.isValidNameserverInCache(name)) {
      int nameServerID = LocalNameServer.getClosestActiveNameServerFromCache(name, activesQueried);
      if (nameServerID == -1) {

        if (StartLocalNameServer.debugMode) GNS.getLogger().fine("ERROR: No more actives left to query. Actives Queried " + activesQueried);
        activesQueried.clear();
        nameServerID = LocalNameServer.getClosestActiveNameServerFromCache(name, activesQueried);
        if (nameServerID == -1) return;
      }

      activesQueried.add(nameServerID);

      if (timeoutCount == 0) {
        String hostAddress = null;
        if (senderAddress != null) hostAddress = senderAddress.getHostAddress();
        updateRequestID = LocalNameServer.addUpdateInfo(name, nameServerID,
                requestRecvdTime, hostAddress, senderPort);
        if (StartLocalNameServer.debugMode) GNS.getLogger().fine("Update Info Added: Id = " + updateRequestID);
      }
      // create the packet that we'll send to the primary
      UpdateAddressPacket pkt = new UpdateAddressPacket(Packet.PacketType.UPDATE_ADDRESS_LNS,
              updateAddressPacket.getRequestID(), updateRequestID, -1,
              name, updateAddressPacket.getRecordKey(),
              updateAddressPacket.getUpdateValue(),
              updateAddressPacket.getOldValue(),
              updateAddressPacket.getOperation(),
              LocalNameServer.nodeID, nameServerID);
//      pkt.setPrimaryNameServers(LocalNameServer.getPrimaryNameServers(name));

      if (StartLocalNameServer.debugMode) GNS.getLogger().fine("Sending Update to Node: " + nameServerID);

      // and send it off
      try {
        JSONObject jsonToSend = pkt.toJSONObject();
        LNSListener.tcpTransport.sendToID(nameServerID, jsonToSend);
        // remote status
        StatusClient.sendTrafficStatus(LocalNameServer.nodeID, nameServerID, GNS.PortType.LNS_TCP_PORT, pkt.getType(), name,
                //nameRecordKey.getName(),
                updateAddressPacket.getUpdateValue().toString());
        if (StartLocalNameServer.debugMode) GNS.getLogger().fine("LNSListenerUpdate: Send to: " + nameServerID + " Name:" + name + " Id:" + updateRequestID
                + " Time:" + System.currentTimeMillis()
                + " --> " + jsonToSend.toString());
      } catch (JSONException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
    }
    else {
      // remove update info from LNS
      LocalNameServer.removeUpdateInfo(updateRequestID);
      // add to pending requests task
      try {
        PendingTasks.addToPendingRequests(name, //nameRecordKey,
                new SendUpdatesTask(updateAddressPacket, senderAddress, senderPort, requestRecvdTime,activesQueried),
                StartLocalNameServer.queryTimeout, senderAddress, senderPort,
                ConfirmUpdateLNSPacket.createFailPacket(updateAddressPacket).toJSONObject());

      } catch (JSONException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }
      // request new actives
      SendActivesRequestTask.requestActives(name//, nameRecordKey
      );

      if (StartLocalNameServer.debugMode) GNS.getLogger().fine("Created a request actives task.");
      // cancel this task
      throw  new RuntimeException();


    }

//    if (activesQueried.size() == 3) return;
//
//    int nameServerID = activesQueried.size();
////    if (activesQueried.size() == 0) (nameServerID);
//    activesQueried.add(nameServerID);
//
//    if (timeoutCount == 0) {
//      String hostAddress = null;
//      if (senderAddress != null) hostAddress = senderAddress.getHostAddress();
//      updateRequestID = LocalNameServer.addUpdateInfo(name, nameServerID,
//              requestRecvdTime, hostAddress, senderPort);
//      if (StartLocalNameServer.debugMode) GNS.getLogger().fine("Update Info Added: Id = " + updateRequestID);
//    }
//    // create the packet that we'll send to the primary
//    UpdateAddressPacket pkt = new UpdateAddressPacket(Packet.PacketType.UPDATE_ADDRESS_LNS,
//            updateAddressPacket.getRequestID(), updateRequestID, -1,
//            name, updateAddressPacket.getRecordKey(),
//            updateAddressPacket.getUpdateValue(),
//            updateAddressPacket.getOldValue(),
//            updateAddressPacket.getOperation(),
//            LocalNameServer.nodeID, nameServerID);
////      pkt.setPrimaryNameServers(LocalNameServer.getPrimaryNameServers(name));
//
//    if (StartLocalNameServer.debugMode) GNS.getLogger().fine("Sending Update to Node: " + nameServerID);
//
//    // and send it off
//    try {
//      JSONObject jsonToSend = pkt.toJSONObject();
//      LNSListener.tcpTransport.sendToID(nameServerID, jsonToSend);
//      // remote status
////      StatusClient.sendTrafficStatus(LocalNameServer.nodeID, nameServerID, GNS.PortType.LNS_TCP_PORT, pkt.getType(),
////              name,updateAddressPacket.getUpdateValue().toString());
//      if (StartLocalNameServer.debugMode) GNS.getLogger().fine("LNSListenerUpdate: Send to: " + nameServerID + " Name:" + name + " Id:" + updateRequestID
//              + " Time:" + System.currentTimeMillis()
//              + " --> " + jsonToSend.toString());
//    } catch (JSONException e) {
//      e.printStackTrace();
//    } catch (IOException e) {
//      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//    }

//    long t1 = System.currentTimeMillis();
//    if (t1 - t0 > 10) {
//      GNS.getLogger().severe(" long delay in SendUpdatesTask " + (t1 - t0));
//    }


  }



}
