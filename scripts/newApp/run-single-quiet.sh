#
java -ea -cp ../../dist/GNS.jar edu.umass.cs.gns.newApp.AppReconfigurableNode -test -nsfile ../../conf/single-server-info -consoleOutputLevel WARNING -fileLoggingLevel WARNING -demandProfileClass edu.umass.cs.gns.newApp.NullDemandProfile &
java -ea -cp ../../dist/GNS.jar edu.umass.cs.gns.localnameserver.LocalNameServer -nsfile ../../conf/single-server-info -consoleOutputLevel WARNING -fileLoggingLevel WARNING &
