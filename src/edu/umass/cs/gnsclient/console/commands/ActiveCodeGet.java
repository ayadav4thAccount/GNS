/*
 *
 *  Copyright (c) 2015 University of Massachusetts
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you
 *  may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  Initial developer(s): Westy
 *
 */
package edu.umass.cs.gnsclient.console.commands;

import java.util.StringTokenizer;
import edu.umass.cs.gnsclient.client.GNSClientCommands;
import edu.umass.cs.gnsclient.console.ConsoleModule;
import edu.umass.cs.gnscommon.exceptions.client.ClientException;
import edu.umass.cs.gnscommon.utils.StringUtil;
import java.io.IOException;

/**
 * Retrieve the active code field
 */
public class ActiveCodeGet extends ConsoleCommand {

  /**
   * Creates a new <code>ActiveCodeGet</code> object
   *
   * @param module
   */
  public ActiveCodeGet(ConsoleModule module) {
    super(module);
  }

  @Override
  public String getCommandDescription() {
    return "Get the current active code for the target GUID and action (using the credential of the current GUID/alias)";
  }

  @Override
  public String getCommandName() {
    return "activecode_get";
  }

  @Override
  public String getCommandParameters() {
    return "[target_guid_or_alias] action";
  }

  /**
   * Override execute to check for a selected guid
   *
   * @throws java.lang.Exception
   */
  @Override
  public void execute(String commandText) throws Exception {
    if (!module.isCurrentGuidSetAndVerified()) {
      return;
    }
    super.execute(commandText);
  }

  @Override
  public void parse(String commandText) throws Exception {
    GNSClientCommands gnsClient = module.getGnsClient();
    try {
      StringTokenizer st = new StringTokenizer(commandText.trim());
      String guid;
      switch (st.countTokens()) {
        case 1:
          guid = module.getCurrentGuid().getGuid();
          break;
        case 2:
          guid = st.nextToken();
          if (!StringUtil.isValidGuidString(guid)) {
            // We probably have an alias, lookup the GUID
            guid = gnsClient.lookupGuid(guid);
          } break;
        default:
          wrongArguments();
          return;
      }

      String action = st.nextToken();
      String value = gnsClient.activeCodeGet(guid, action, module.getCurrentGuid());

      if (value != null) {
        console.printString(value);
        console.printNewline();
      } else {
        console.printString("No activecode set for GUID " + guid + "for action '" + action + "'");
        console.printNewline();
      }

    } catch (IOException | ClientException e) {
      console.printString("Failed to access GNS ( " + e + ")\n");
    }
  }
}
