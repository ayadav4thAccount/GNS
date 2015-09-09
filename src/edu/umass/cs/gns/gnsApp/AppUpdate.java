package edu.umass.cs.gns.gnsApp;

import edu.umass.cs.gns.gnsApp.clientCommandProcessor.commandSupport.MetaDataTypeName;
import edu.umass.cs.gns.database.ColumnFieldType;
import edu.umass.cs.gns.exceptions.FailedDBOperationException;
import edu.umass.cs.gns.exceptions.FieldNotFoundException;
import edu.umass.cs.gns.exceptions.RecordNotFoundException;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.gnsApp.clientSupport.NSAuthentication;
import edu.umass.cs.gns.gnsApp.packet.ConfirmUpdatePacket;
import edu.umass.cs.gns.gnsApp.packet.Packet;
import edu.umass.cs.gns.gnsApp.packet.UpdatePacket;
import edu.umass.cs.gns.gnsApp.recordmap.NameRecord;
import edu.umass.cs.utils.DelayProfiler;
import org.json.JSONException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

/**
 *
 * Contains code for executing an address update locally at each active replica. If name servers are replicated,
 * then methods in this class will be executed after the coordination among active replicas at name servers
 * is complete.
 *
 * Created by abhigyan on 2/27/14.
 */
public class AppUpdate {

  /**
   * Executes the local update in response to an UpdatePacket.
   *
   * @param updatePacket
   * @param app
   * @param doNotReplyToClient
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeySpecException
   * @throws InvalidKeyException
   * @throws SignatureException
   * @throws JSONException
   * @throws IOException
   * @throws FailedDBOperationException
   */
  public static void executeUpdateLocal(UpdatePacket<String> updatePacket, 
          GnsApplicationInterface<String> app,
          boolean doNotReplyToClient)
          throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException, JSONException, IOException, FailedDBOperationException {
    Long receiptTime = System.currentTimeMillis();
    if (AppReconfigurableNodeOptions.debuggingEnabled) {
      GNS.getLogger().info("Processing UPDATE with " + " "
              + "doNotReplyToClient= " + doNotReplyToClient 
              + " packet: " + updatePacket);
    }

    // First we do signature and ACL checks
    String guid = updatePacket.getName();
    String field = updatePacket.getRecordKey() != null ? updatePacket.getRecordKey() : null;
    String writer = updatePacket.getAccessor();
    String signature = updatePacket.getSignature();
    String message = updatePacket.getMessage();
    NSResponseCode errorCode = NSResponseCode.NO_ERROR;
    // FIXME : handle ACL checks for full JSON user updates
    if (writer != null && field != null) { // writer will be null for internal system reads
      errorCode = NSAuthentication.signatureAndACLCheck(guid, field, writer, signature, message, MetaDataTypeName.WRITE_WHITELIST,
              app, updatePacket.getCppAddress());
    }
    DelayProfiler.updateDelay("totalUpdateAuth", receiptTime);
    // return an error packet if one of the checks doesn't pass
    if (errorCode.isAnError()) {
      @SuppressWarnings("unchecked")
      ConfirmUpdatePacket<String> failConfirmPacket = ConfirmUpdatePacket.createFailPacket(updatePacket, errorCode);
      if (!doNotReplyToClient) {
        app.getClientCommandProcessor().injectPacketIntoCCPQueue(failConfirmPacket.toJSONObject());
        //replica.getMessenger().sendToAddress(updatePacket.getCppAddress(), failConfirmPacket.toJSONObject());

      }
      return;
    }

    NameRecord nameRecord;

    if (updatePacket.getOperation().isAbleToSkipRead()) { // some operations don't require a read first
      nameRecord = new NameRecord(app.getDB(), guid);
    } else {
      try {
        if (field == null) {
          nameRecord = NameRecord.getNameRecord(app.getDB(), guid);
        } else {
          nameRecord = NameRecord.getNameRecordMultiField(app.getDB(), guid, null, ColumnFieldType.LIST_STRING, field);
        }
      } catch (RecordNotFoundException e) {
        GNS.getLogger().severe(" Error: name record not found before update. Return. Name = " + guid + " Packet = " + updatePacket.toString());
        e.printStackTrace();
        @SuppressWarnings("unchecked")
        ConfirmUpdatePacket<String> failConfirmPacket = ConfirmUpdatePacket.createFailPacket(updatePacket, NSResponseCode.ERROR);
        if (!doNotReplyToClient) {
          app.getClientCommandProcessor().injectPacketIntoCCPQueue(failConfirmPacket.toJSONObject());
          //app.getMessenger().sendToAddress(updatePacket.getCppAddress(), failConfirmPacket.toJSONObject());

        }
        return;
      }
    }

    // Apply update
    if (AppReconfigurableNodeOptions.debuggingEnabled) {
      if (field != null) {
        GNS.getLogger().info("****** field=" + field + " operation= " + updatePacket.getOperation().toString()
                + " value= " + updatePacket.getUpdateValue()
                + " name Record=" + nameRecord.toString());
      }
    }
    boolean result;
    try {
      result = nameRecord.updateNameRecord(field,
              updatePacket.getUpdateValue(), updatePacket.getOldValue(), updatePacket.getArgument(),
              updatePacket.getUserJSON(),
              updatePacket.getOperation());
      if (AppReconfigurableNodeOptions.debuggingEnabled) {
        GNS.getLogger().fine("Update operation result = " + result + "\t"
                + updatePacket.getUpdateValue());
      }

      if (!result) { // update failed
        if (AppReconfigurableNodeOptions.debuggingEnabled) {
          GNS.getLogger().info("Update operation failed " + updatePacket);
        }
        if (updatePacket.getNameServerID().equals(app.getNodeID())) {
          // IF this node proposed this update send error message to client (CCP).
          ConfirmUpdatePacket<String> failPacket
                  = new ConfirmUpdatePacket<String>(Packet.PacketType.UPDATE_CONFIRM,
                          updatePacket.getSourceId(),
                          updatePacket.getRequestID(), updatePacket.getCCPRequestID(), NSResponseCode.ERROR);
          if (AppReconfigurableNodeOptions.debuggingEnabled) {
            GNS.getLogger().info("Error msg sent to client for failed update " + updatePacket);
          }
          if (!doNotReplyToClient) {
            app.getClientCommandProcessor().injectPacketIntoCCPQueue(failPacket.toJSONObject());
          }
        }

      } else {
        if (AppReconfigurableNodeOptions.debuggingEnabled) {
          GNS.getLogger().info("Update applied" + updatePacket);
        }

        // If this node proposed this update send the confirmation back to the client (CCP).
        if (updatePacket.getNameServerID().equals(app.getNodeID())) {
          ConfirmUpdatePacket<String> confirmPacket = new ConfirmUpdatePacket<String>(Packet.PacketType.UPDATE_CONFIRM,
                  updatePacket.getSourceId(),
                  updatePacket.getRequestID(), updatePacket.getCCPRequestID(), NSResponseCode.NO_ERROR);

          if (!doNotReplyToClient) {
            app.getClientCommandProcessor().injectPacketIntoCCPQueue(confirmPacket.toJSONObject());
            //app.getMessenger().sendToAddress(updatePacket.getCppAddress(), confirmPacket.toJSONObject());
            if (AppReconfigurableNodeOptions.debuggingEnabled) {
              GNS.getLogger().info("NS Sent confirmation to CCP. Sent packet: " + confirmPacket.toJSONObject());
            }
          }
        }
      }
    } catch (FieldNotFoundException e) {
      GNS.getLogger().severe("Field not found exception. Exception = " + e.getMessage());
      e.printStackTrace();
    }
    DelayProfiler.updateDelay("totalUpdate", receiptTime);
  }

}
