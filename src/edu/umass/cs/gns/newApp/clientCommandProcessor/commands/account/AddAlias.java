/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.newApp.clientCommandProcessor.commands.account;

import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.AccountAccess;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.AccountInfo;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.CommandResponse;
import static edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GnsProtocolDefs.*;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GuidInfo;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.CommandModule;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.GnsCommand;
import edu.umass.cs.gns.newApp.clientCommandProcessor.demultSupport.ClientRequestHandlerInterface;
import edu.umass.cs.gns.httpserver.Defs;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author westy
 */
public class AddAlias extends GnsCommand {

  public AddAlias(CommandModule module) {
    super(module);
  }

  @Override
  public String[] getCommandParameters() {
    return new String[]{GUID, NAME, SIGNATURE, SIGNATUREFULLMESSAGE};
  }

  @Override
  public String getCommandName() {
    return ADDALIAS;
  }

  @Override
  public CommandResponse<String> execute(JSONObject json, ClientRequestHandlerInterface handler) throws InvalidKeyException, InvalidKeySpecException,
          JSONException, NoSuchAlgorithmException, SignatureException {
//    if (CommandDefs.handleAcccountCommandsAtNameServer) {
//      return LNSToNSCommandRequestHandler.sendCommandRequest(json);
//    } else {
      String guid = json.getString(GUID);
      String name = json.getString(NAME);
      String signature = json.getString(SIGNATURE);
      String message = json.getString(SIGNATUREFULLMESSAGE);
      GuidInfo guidInfo;
      if ((guidInfo = AccountAccess.lookupGuidInfo(guid, handler)) == null) {
        return new CommandResponse<String>(BADRESPONSE + " " + BADGUID + " " + guid);
      }
      AccountInfo accountInfo = AccountAccess.lookupAccountInfoFromGuid(guid, handler);
        if (accountInfo == null) {
          return new CommandResponse<String>(BADRESPONSE + " " + BADACCOUNT + " " + guid);
        }
        if (!accountInfo.isVerified()) {
          return new CommandResponse<String>(BADRESPONSE + " " + VERIFICATIONERROR + " Account not verified");
        } else if (accountInfo.getAliases().size() > Defs.MAXALIASES) {
          return new CommandResponse<String>(BADRESPONSE + " " + TOMANYALIASES);
        } else {
          return AccountAccess.addAlias(accountInfo, name, guid, signature, message, handler);
        }
    
//      GuidInfo guidInfo;
//      if ((guidInfo = AccountAccess.lookupGuidInfo(guid)) == null) {
//        return new CommandResponse<String>(BADRESPONSE + " " + BADGUID + " " + guid);
//      }
//      if (AccessSupport.verifySignature(guidInfo, signature, message)) {
//        AccountInfo accountInfo = AccountAccess.lookupAccountInfoFromGuid(guid);
//        if (accountInfo == null) {
//          return new CommandResponse<String>(BADRESPONSE + " " + BADACCOUNT + " " + guid);
//        }
//        if (!accountInfo.isVerified()) {
//          return new CommandResponse<String>(BADRESPONSE + " " + VERIFICATIONERROR + " Account not verified");
//        } else if (accountInfo.getAliases().size() > GnsProtocolDefs.MAXALIASES) {
//          return new CommandResponse<String>(BADRESPONSE + " " + TOMANYALIASES);
//        } else {
//          return AccountAccess.addAlias(accountInfo, name);
//        }
//      } else {
//        return new CommandResponse<String>(BADRESPONSE + " " + BADSIGNATURE);
//      }
   // }
  }

  @Override
  public String getCommandDescription() {
    return "Adds a additional human readble name to the account associated with the GUID. "
            + "Must be signed by the guid. Returns " + BADGUID + " if the GUID has not been registered.";

  }
}
