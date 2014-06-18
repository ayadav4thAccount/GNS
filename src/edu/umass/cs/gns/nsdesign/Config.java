package edu.umass.cs.gns.nsdesign;

import edu.umass.cs.gns.database.DataStoreType;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nsdesign.replicationframework.ReplicationFrameworkType;

import java.util.HashMap;

/**
 * Should only contain config that is common across all name servers. Any name server specific configuration,
 * e.g.., nodeID, should be in gnsReconfigurable package or replicaController package.
 * Parameters are initialized once when name server is started, and thereafter accessed statically.
 *
 * Created by abhigyan on 3/27/14.
 */
public class Config {

  /** First version number for a GUID. */
  public static final short FIRST_VERSION = 1;

  public static final String NO_COORDINATOR_STATE_MARKER = "NoCoordinatorState";

  private static final String DEFAULTPAXOSLOGPATHNAME = "paxosLog";

  private static boolean initialized = false;

  public static boolean debugMode = false;

//  public static boolean experimentMode = false;
  
  // Useful for testing with resources in conf/testCodeResources if using "import from build file in IDE". Better way to do this?
  public static final String ARUN_GNS_DIR_PATH = "/Users/arun/GNS/"; 
  

  // paxos parameters
  public static int failureDetectionTimeoutSec = 30000;
  public static int failureDetectionPingSec = 10000;
  public static String paxosLogFolder = DEFAULTPAXOSLOGPATHNAME;

  public static DataStoreType dataStore = DataStoreType.MONGO;
  public static int mongoPort = 27017;

  // parameter related to replication of records
  public static double normalizingConstant = 0.5;
  public static int minReplica = 3;
  public static int maxReplica = 100;
  public static ReplicationFrameworkType replicationFrameworkType = ReplicationFrameworkType.LOCATION;
  public static int analysisIntervalSec = 1000000;
  public static int movingAverageWindowSize = 20;
  public static int nameServerVoteSize = 30;
  public static double maxReqRate = 50.0; // maximum request rate of name records
  public static final int NS_TIMEOUT_MILLIS = 5000;

  // parameters specific to beehive replication (used only for running experiments)
  public static double beehiveC = 0.5;
  public static double beehiveAlpha = 0.91;
  public static double beehiveBase = 16;
  public static int beehiveWorkloadSize = 11000;

  // testing related parameters
  public static boolean useGNSNIOTransport = true;
  public static boolean multiPaxos = false; // option to use multipaxos package
  public static boolean emulatePingLatencies = false;
  public static double latencyVariation = 0.1;
  public static boolean noPaxosLog = false;
  public static boolean dummyGNS = false; // should be set to false except when running experiments
  public static boolean singleNS = false; // only one replica of a name, as well as of its replica controller
  public static boolean readCoordination = false; // order read requests via paxos. this option give linearizable
                                                  // consistency semantics for a name
  public static boolean eventualConsistency = false;

  public static synchronized void initialize(HashMap<String, String> allValues) {
    if (initialized) return;

    initialized = true;

    if (allValues.containsKey(NSParameterNames.PRIMARY_REPLICAS)) {
      GNS.numPrimaryReplicas = Integer.parseInt(allValues.get(NSParameterNames.PRIMARY_REPLICAS));
    }
    if (allValues.containsKey(NSParameterNames.FILE_LOGGING_LEVEL)) {
      GNS.fileLoggingLevel = allValues.get(NSParameterNames.FILE_LOGGING_LEVEL);
    }
    if (allValues.containsKey(NSParameterNames.CONSOLE_OUTPUT_LEVEL)) {
      GNS.consoleOutputLevel = allValues.get(NSParameterNames.CONSOLE_OUTPUT_LEVEL);
    }
    if (allValues.containsKey(NSParameterNames.STAT_FILE_LOGGING_LEVEL)) {
      GNS.statFileLoggingLevel = allValues.get(NSParameterNames.STAT_FILE_LOGGING_LEVEL);
    }
    if (allValues.containsKey(NSParameterNames.STAT_CONSOLE_OUTPUT_LEVEL)) {
      GNS.statConsoleOutputLevel = allValues.get(NSParameterNames.STAT_CONSOLE_OUTPUT_LEVEL);
    }

    // paxos parameters
    if (allValues.containsKey(NSParameterNames.PAXOS_LOG_FOLDER)) {
      paxosLogFolder = allValues.get(NSParameterNames.PAXOS_LOG_FOLDER);
    } else {
      paxosLogFolder = DEFAULTPAXOSLOGPATHNAME;
    }

    if (allValues.containsKey(NSParameterNames.FAILURE_DETECTION_MSG_INTERVAL)) {
      failureDetectionPingSec = Integer.parseInt(allValues.get(NSParameterNames.FAILURE_DETECTION_MSG_INTERVAL));
    }

    if (allValues.containsKey(NSParameterNames.FAILURE_DETECTION_TIMEOUT_INTERVAL)) {
      failureDetectionTimeoutSec = Integer.parseInt(allValues.get(NSParameterNames.FAILURE_DETECTION_TIMEOUT_INTERVAL));
    }

    // replication parameters
    if (allValues.containsKey(NSParameterNames.STATIC)) {
      replicationFrameworkType = ReplicationFrameworkType.STATIC;
    } else if (allValues.containsKey(NSParameterNames.LOCATION)) {
      replicationFrameworkType = ReplicationFrameworkType.LOCATION;
    } else if (allValues.containsKey(NSParameterNames.RANDOM)) {
      replicationFrameworkType = ReplicationFrameworkType.RANDOM;
    }

    if (allValues.containsKey(NSParameterNames.REPLICATION_INTERVAL)) {
      analysisIntervalSec = Integer.parseInt(allValues.get(NSParameterNames.REPLICATION_INTERVAL));
    }

    if (allValues.containsKey(NSParameterNames.NORMALIZING_CONSTANT)) {
      normalizingConstant = Double.parseDouble(allValues.get(NSParameterNames.NORMALIZING_CONSTANT));
    }

    if (allValues.containsKey(NSParameterNames.MIN_REPLICA)) {
      minReplica =  Integer.parseInt(allValues.get(NSParameterNames.MIN_REPLICA));
    }

    if (allValues.containsKey(NSParameterNames.MAX_REPLICA)) {
      maxReplica =  Integer.parseInt(allValues.get(NSParameterNames.MAX_REPLICA));
    }

    if (allValues.containsKey(NSParameterNames.MAX_REQ_RATE)) {
      maxReqRate =  Double.parseDouble(allValues.get(NSParameterNames.MAX_REQ_RATE));
    }

    // data-store parameters
    if (allValues.containsKey(NSParameterNames.MONGO_PORT)) {
      mongoPort = Integer.parseInt(allValues.get(NSParameterNames.MONGO_PORT));
    }

    // Testing-related parameters

    // singleNS option must be read after GNS.numPrimaryReplicas is read.

    if (allValues.containsKey(NSParameterNames.READ_COORDINATION)) {
      readCoordination = Boolean.parseBoolean(allValues.get(NSParameterNames.READ_COORDINATION));
    }
    if (allValues.containsKey(NSParameterNames.EMULATE_PING_LATENCIES)) {
      emulatePingLatencies = Boolean.parseBoolean(allValues.get(NSParameterNames.EMULATE_PING_LATENCIES));
      if (allValues.containsKey(NSParameterNames.VARIATION)) {
        latencyVariation = Double.parseDouble(allValues.get(NSParameterNames.VARIATION));
      }
      GNS.getLogger().info("Emulating ping latency at name server: emulatePingLatencies = " + emulatePingLatencies);
    }

    if (allValues.containsKey(NSParameterNames.NO_PAXOS_LOG)) {
      noPaxosLog = Boolean.parseBoolean(allValues.get(NSParameterNames.NO_PAXOS_LOG));
    }

    if (allValues.containsKey(NSParameterNames.DEBUG_MODE)) {
      debugMode = Boolean.parseBoolean(allValues.get(NSParameterNames.DEBUG_MODE));
    }

    if (allValues.containsKey(NSParameterNames.USE_GNS_NIO_TRANSPORT)) {
      useGNSNIOTransport = Boolean.parseBoolean(allValues.get(NSParameterNames.USE_GNS_NIO_TRANSPORT));
    }

    if (allValues.containsKey(NSParameterNames.MULTI_PAXOS)) {
      multiPaxos = Boolean.parseBoolean(allValues.get(NSParameterNames.MULTI_PAXOS));
    }

    if (allValues.containsKey(NSParameterNames.DUMMY_GNS)) {
      dummyGNS = Boolean.parseBoolean(allValues.get(NSParameterNames.DUMMY_GNS));
    }
    if (allValues.containsKey(NSParameterNames.SINGLE_NS) || GNS.numPrimaryReplicas == 1) {
      GNS.numPrimaryReplicas = 1;
      Config.minReplica = 1;
      singleNS = true;
      if (Config.debugMode) GNS.getLogger().fine("Number of primary: " + GNS.numPrimaryReplicas + " \tSingleNS\t" + singleNS);
    }
  }

}
