/*
 * Copyright (C) 2013
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.localnameserver;

import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nsdesign.Shutdownable;
import edu.umass.cs.gns.nsdesign.packet.Packet;
import edu.umass.cs.gns.nsdesign.packet.admin.*;
import edu.umass.cs.gns.statusdisplay.StatusClient;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * A separate thread that runs in the LNS that handles administrative (AKA non-data related, non-user)
 * type operations. All of the things in here are for server administration and debugging.
 *
 * @author Westy
 */
@SuppressWarnings("unchecked")
public class LNSListenerAdmin extends Thread implements Shutdownable {

  /**
   * Socket over which active name server request arrive *
   */
  private ServerSocket serverSocket;
  /**
   * Keeps track of how many responses are outstanding for a request *
   */
  private static Map<Integer, Integer> replicationMap;
  
  private ClientRequestHandlerInterface handler;

  /**
   *
   * Creates a new listener thread for handling response packet
   *
   * @throws IOException
   */
  public LNSListenerAdmin(ClientRequestHandlerInterface handler) throws IOException {
    super("ListenerAdmin");
    this.serverSocket = new ServerSocket(GNS.DEFAULT_LNS_ADMIN_PORT);
    replicationMap = new HashMap<Integer, Integer>();
    this.handler = handler;
  }

  /**
   *
   * Start executing the thread.
   */
  @Override
  public void run() {
    int numRequest = 0;
    GNS.getLogger().info("LNS Node " + handler.getNodeAddress() + " starting Admin Server on port " + serverSocket.getLocalPort());
    while (true) {
      Socket socket;
      JSONObject incomingJSON;
      try {
        socket = serverSocket.accept();
        //Read the packet from the input stream
        incomingJSON = Packet.getJSONObjectFrame(socket);
      } catch (Exception e) {
        GNS.getLogger().warning("Ignoring error accepting socket connection: " + e);
        e.printStackTrace();
        continue;
      }
      handlePacket(incomingJSON, socket, handler);
      try {
        socket.close();
      } catch (IOException e) {
        GNS.getLogger().warning("Error closing socket: " + e);
        e.printStackTrace();
      }
    }
  }

  public static void handlePacket(JSONObject incomingJSON, Socket incomingSocket, ClientRequestHandlerInterface handler) {
    try {
      switch (Packet.getPacketType(incomingJSON)) {
        case DUMP_REQUEST:
          DumpRequestPacket dumpRequestPacket = new DumpRequestPacket(incomingJSON, handler.getGnsNodeConfig());
          if (dumpRequestPacket.getPrimaryNameServer() == null) {
            // OUTGOING - multicast it to all the nameservers
            int id = dumpRequestPacket.getId();
            GNS.getLogger().fine("ListenerAdmin: Request from local HTTP server");
            JSONObject json = dumpRequestPacket.toJSONObject();
            Set<Object> serverIds = handler.getGnsNodeConfig().getNodeIDs();
            replicationMap.put(id, serverIds.size());
            Packet.multicastTCP(handler.getGnsNodeConfig(), serverIds, json, 2, GNS.PortType.NS_ADMIN_PORT, null);
            GNS.getLogger().fine("ListenerAdmin: Multicast out to " + serverIds.size() + " hosts for " + id + " --> " + dumpRequestPacket.toString());
          } else {
            // INCOMING - send it out to original requester
            DumpRequestPacket incomingPacket = new DumpRequestPacket(incomingJSON, handler.getGnsNodeConfig());
            int incomingId = incomingPacket.getId();
            handler.getAdmintercessor().handleIncomingDumpResponsePackets(incomingJSON, handler);
            GNS.getLogger().fine("ListenerAdmin: Relayed response for " + incomingId + " --> " + dumpRequestPacket.toJSONObject());
            int remaining = replicationMap.get(incomingId);
            remaining = remaining - 1;
            if (remaining > 0) {
              replicationMap.put(incomingId, remaining);
            } else {
              GNS.getLogger().fine("ListenerAdmin: Saw last response for " + incomingId);
              replicationMap.remove(incomingId);
              SentinalPacket sentinelPacket = new SentinalPacket(incomingId);
              handler.getAdmintercessor().handleIncomingDumpResponsePackets(sentinelPacket.toJSONObject(), handler);
            }
          }
          break;
        case ADMIN_REQUEST:
          AdminRequestPacket incomingPacket = new AdminRequestPacket(incomingJSON);
          switch (incomingPacket.getOperation()) {
            // Calls remove record on every record
            case DELETEALLRECORDS:
            // Clears the database and reinitializes all indices.
            case RESETDB:
              GNS.getLogger().fine("LNSListenerAdmin (" + handler.getNodeAddress() + ") "
                      + ": Forwarding " + incomingPacket.getOperation().toString() + " request");
              Set<Object> serverIds = handler.getGnsNodeConfig().getNodeIDs();
              Packet.multicastTCP(handler.getGnsNodeConfig(), serverIds, incomingJSON, 2, GNS.PortType.NS_ADMIN_PORT, null);
              // clear the cache
              handler.invalidateCache();
              break;
            case CLEARCACHE:
              GNS.getLogger().fine("LNSListenerAdmin (" + handler.getNodeAddress() + ") Clearing Cache as requested");
              handler.invalidateCache();
              break;
            case DUMPCACHE:
              JSONObject jsonResponse = new JSONObject();
              jsonResponse.put("CACHE", handler.getCacheLogString("CACHE:\n"));
              AdminResponsePacket responsePacket = new AdminResponsePacket(incomingPacket.getId(), jsonResponse);
              handler.getAdmintercessor().handleIncomingAdminResponsePackets(responsePacket.toJSONObject());
              break;
            case PINGTABLE:
              String node = new String(incomingPacket.getArgument());
              // null means return the LNS data
              if (node == null || handler.getGnsNodeConfig().getNodeIDs().contains(node)) {
                if (node == null) {
                  jsonResponse = new JSONObject();
                  jsonResponse.put("PINGTABLE", handler.getPingManager().tableToString(null));
                  // send a response back to where the request came from
                  responsePacket = new AdminResponsePacket(incomingPacket.getId(), jsonResponse);
                  returnResponsePacketToSender(incomingPacket.getLnsAddress(), responsePacket, handler);
                } else {
                incomingPacket.setLnsAddress(new InetSocketAddress(handler.getNodeAddress().getAddress(), GNS.DEFAULT_LNS_ADMIN_PORT));
                //incomingPacket.sethandlerId(handler.getNodeID()); // so the receiver knows where to return it
                Packet.sendTCPPacket(handler.getGnsNodeConfig(), incomingPacket.toJSONObject(), node, GNS.PortType.NS_ADMIN_PORT);
                }
              } else { // the incoming packet contained an invalid host number
                jsonResponse = new JSONObject();
                jsonResponse.put("ERROR", "Bad host number");
                responsePacket = new AdminResponsePacket(incomingPacket.getId(), jsonResponse);
                returnResponsePacketToSender(incomingPacket.getLnsAddress(), responsePacket, handler);
                //returnResponsePacketToSender(incomingPacket.gethandlerId(), responsePacket);
              }
              break;
            case PINGVALUE:
              String node1 = new String(incomingPacket.getArgument());
              String node2 = new String(incomingPacket.getArgument2());
              // null means return the LNS data
              if (node1 == null || handler.getGnsNodeConfig().nodeExists(node1)
                      && handler.getGnsNodeConfig().nodeExists(node2)) {
                if (node1 == null) {
                  // handle it here
                  jsonResponse = new JSONObject();
                  jsonResponse.put("PINGVALUE", handler.getPingManager().nodeAverage(node2));
                  // send a response back to where the request came from
                  responsePacket = new AdminResponsePacket(incomingPacket.getId(), jsonResponse);
                  returnResponsePacketToSender(incomingPacket.getLnsAddress(), responsePacket, handler);
                } else {
                // send it to the server that can handle it
                incomingPacket.setLnsAddress(new InetSocketAddress(handler.getNodeAddress().getAddress(), GNS.DEFAULT_LNS_ADMIN_PORT));
                //incomingPacket.sethandlerId(handler.getNodeID()); // so the receiver knows where to return it
                Packet.sendTCPPacket(handler.getGnsNodeConfig(), incomingPacket.toJSONObject(), node1, GNS.PortType.NS_ADMIN_PORT);
                }
              } else { // the incoming packet contained an invalid host number
                jsonResponse = new JSONObject();
                jsonResponse.put("ERROR", "Bad host number");
                responsePacket = new AdminResponsePacket(incomingPacket.getId(), jsonResponse);
                returnResponsePacketToSender(incomingPacket.getLnsAddress(), responsePacket, handler);
                //returnResponsePacketToSender(incomingPacket.gethandlerId(), responsePacket);
              }
              break;
            case CHANGELOGLEVEL:
              Level level = Level.parse(incomingPacket.getArgument());
              GNS.getLogger().info("Changing log level to " + level.getName());
              GNS.getLogger().setLevel(level);
              // send it on to the NSs
              GNS.getLogger().fine("LNSListenerAdmin (" + handler.getNodeAddress() + ") "
                      + ": Forwarding " + incomingPacket.getOperation().toString() + " request");
              serverIds = handler.getGnsNodeConfig().getNodeIDs();
              Packet.multicastTCP(handler.getGnsNodeConfig(), serverIds, incomingJSON, 2, GNS.PortType.NS_ADMIN_PORT, null);
              break;
            default:
              GNS.getLogger().severe("Unknown admin request in packet: " + incomingJSON);
              break;
          }
          break;
        case ADMIN_RESPONSE:
          // forward and admin response packets recieved from NSs back to client
          AdminResponsePacket responsePacket = new AdminResponsePacket(incomingJSON);
          handler.getAdmintercessor().handleIncomingAdminResponsePackets(responsePacket.toJSONObject());
          break;
        case STATUS_INIT:
          StatusClient.handleStatusInit(incomingSocket.getInetAddress());
          // FIXME: Should send address instead for LNSs (fix other end of this)
          StatusClient.sendStatus(null, "LNS Ready");
          break;
        default:
          GNS.getLogger().severe("Unknown packet type in packet: " + incomingJSON);
          break;
      }
    } catch (Exception e) {
      GNS.getLogger().warning("Ignoring error handling packets: " + e);
      e.printStackTrace();
    }
  }

  private static void returnResponsePacketToSender(InetSocketAddress address, AdminResponsePacket packet,
          ClientRequestHandlerInterface handler) throws IOException, JSONException {
    if (address == null) {
      // it came from our client
      handler.getAdmintercessor().handleIncomingAdminResponsePackets(packet.toJSONObject());
    } else {
      // it came from another LNS
      Packet.sendTCPPacket(packet.toJSONObject(), address);
      //Packet.sendTCPPacket(handler.getGnsNodeConfig(), packet.toJSONObject(), senderId, GNS.PortType.LNS_ADMIN_PORT);
    }
  }

  @Override
  public void shutdown() {
    try {
      this.serverSocket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
