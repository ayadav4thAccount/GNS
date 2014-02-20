/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.commands.group;

import edu.umass.cs.gns.client.FieldMetaData;
import edu.umass.cs.gns.client.GuidInfo;
import edu.umass.cs.gns.client.MetaDataTypeName;
import edu.umass.cs.gns.clientprotocol.AccessSupport;
import edu.umass.cs.gns.commands.CommandModule;
import edu.umass.cs.gns.commands.GnsCommand;
import static edu.umass.cs.gns.clientprotocol.Defs.*;
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
public class AddToGroup extends GnsCommand {

  public AddToGroup(CommandModule module) {
    super(module);
  }

  @Override
  public String[] getCommandParameters() {
    return new String[]{GUID, MEMBER, WRITER, SIGNATURE, SIGNATUREFULLMESSAGE};
  }

  @Override
  public String getCommandName() {
    return ADDTOGROUP;
  }

  @Override
  public String execute(JSONObject json) throws InvalidKeyException, InvalidKeySpecException,
          JSONException, NoSuchAlgorithmException, SignatureException {
    String guid = json.getString(GUID);
    String member = json.getString(MEMBER);
    // writer might be same as guid
    String writer = json.optString(WRITER, guid);
    // signature and message can be empty for unsigned cases
    String signature = json.optString(SIGNATURE, null);
    String message = json.optString(SIGNATUREFULLMESSAGE, null);
    GuidInfo guidInfo, writerInfo;
    if ((guidInfo = accountAccess.lookupGuidInfo(guid)) == null) {
      return BADRESPONSE + " " + BADGUID + " " + guid;
    }
    if (writer.equals(guid)) {
      writerInfo = guidInfo;
    } else if ((writerInfo = accountAccess.lookupGuidInfo(writer)) == null) {
      return BADRESPONSE + " " + BADWRITERGUID + " " + writer;
    }
    if (!AccessSupport.verifySignature(writerInfo, signature, message)) {
      return BADRESPONSE + " " + BADSIGNATURE;
    } else if (!AccessSupport.verifyAccess(MetaDataTypeName.WRITE_WHITELIST, guidInfo, GROUP_ACL, writerInfo)) {
      return BADRESPONSE + " " + ACCESSDENIED;
    } else if (groupAccess.addToGroup(guid, member)) {
      return OKRESPONSE;
    } else {
      return BADRESPONSE + " " + GENERICEERROR;
    }
  }

  @Override
  public String getCommandDescription() {
    return "Adds the member guid to the group specified by guid. Writer guid needs to have write access and sign the command.";
  }
}
