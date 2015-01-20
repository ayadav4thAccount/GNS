/*
 * Copyright (C) 2014
 * University of Massachusetts
 * All Rights Reserved 
 *
 * Initial developer(s): Westy.
 */
package edu.umass.cs.gns.nsdesign.nodeconfig;

import edu.umass.cs.gns.main.GNS;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a host file (hosts addresses one per line) and returns a list of HostSpec objects.
 *
 * @author westy
 */
public class HostFileLoader {

  private static final Long INVALID_FILE_VERSION = -1L;
  private static Long fileVersion = INVALID_FILE_VERSION;

  private static final boolean debuggingEnabled = false;

  /**
   * Reads a host file (hosts addresses one per line) and returns a list of HostSpec objects.
   * 
   * The first line of the file can be a Long representing the file version. This will be ignored by this
   * call. To read that call <code>readVersionLine</code>.
   *
   * This currently supports three line formats (one of these per line):
   * <code>
   * {hostname}
   * or
   * {number}{whitespace}{hostname}
   * or
   * {number}{whitespace}{hostname}{whitespace}{startingport}
   * </code>
   * Also, you can't mix the above line formats in one file.
   *
   * @param hostsFile
   * @return a List of hostnames
   */
  public static List<HostSpec> loadHostFile(String hostsFile) throws Exception {
    List<HostSpec> result = new ArrayList<HostSpec>();
    BufferedReader br = new BufferedReader(new FileReader(hostsFile));
    boolean readFirstLine = false;
    try {
      while (br.ready()) {
        String line = br.readLine();
        if (line == null || line.equals("") || line.equals(" ")
                || line.startsWith("#")) {
          // do nothing
        } else if (!readFirstLine && isLineTheFileVersion(line)) {
          fileVersion = getTheVersionFromLine(line);
          if (debuggingEnabled) {
            GNS.getLogger().info("Read version line: " + fileVersion);
          }
        } else {
          result.add(parseHostline(line));
        }
        readFirstLine = true;
      }
      br.close();
    } catch (IOException e) {
      throw new Exception("Problem reading hosts file: " + e);
    }
    return result;
  }

  private static boolean hostFileHasNumbers = false;

  private static HostSpec parseHostline(String line) throws IOException {
    String[] tokens = line.split("\\s+");
    if (tokens.length > 1) {
      String idString = tokens[0];
      Integer port = tokens.length > 2 ? Integer.parseInt(tokens[2]) : null;
      hostFileHasNumbers = true;
      // Parse as an Integer if we can otherwise a String.
      Object nodeID = -1;
      try {
        nodeID = Integer.parseInt(idString);
      } catch (NumberFormatException e) {
        nodeID = idString;
      }
      if (debuggingEnabled) {
        GNS.getLogger().info("Read ID: " + nodeID);
      }
      return new HostSpec(nodeID, tokens[1], port);
    } else if (tokens.length == 1) {
      if (hostFileHasNumbers) {
        throw new IOException("Can't mix format with IDs provided and not provided:" + line);
      }
      return new HostSpec(tokens[0], tokens[0], null);
    } else {
      throw new IOException("Bad host format:" + line);
    }
  }

  /**
   * Reads the version form the first line of the host file.
   * Returns null if it doesn't exist.
   *
   * @param hostsFile
   * @return
   * @throws FileNotFoundException
   * @throws IOException
   */
  public static Long readVersionLine(String hostsFile) throws IOException {
    BufferedReader br = new BufferedReader(new FileReader(hostsFile));
    String line = br.readLine();
    return getTheVersionFromLine(line);
  }

  public static boolean isChangedFileVersion(String hostsFile) throws IOException {
    Long newVersion = readVersionLine(hostsFile);
    if (debuggingEnabled) {
      GNS.getLogger().info("Old version: " + fileVersion + " new version: " + newVersion);
    }
    return newVersion != null && newVersion != INVALID_FILE_VERSION && newVersion != fileVersion;
  }

  private static Long getTheVersionFromLine(String line) {
    Long version = null;
    String[] tokens = line.split("\\s+");
    // check to see if it's a host spec on the first line
    if (tokens.length == 1) {
      try {
        version = Long.parseLong(line);
      } catch (NumberFormatException e) {
      }
    }
    return version;
  }

  private static boolean isLineTheFileVersion(String line) {
    return getTheVersionFromLine(line) != null;
  }

  public static Long getFileVersion() {
    return fileVersion;
  }

}
