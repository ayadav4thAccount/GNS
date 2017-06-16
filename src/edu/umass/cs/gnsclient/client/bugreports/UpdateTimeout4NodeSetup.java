package edu.umass.cs.gnsclient.client.bugreports;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import edu.umass.cs.gnsclient.client.GNSClient;
import edu.umass.cs.gnsclient.client.GNSCommand;
import edu.umass.cs.gnsclient.client.util.GuidEntry;
import edu.umass.cs.gnsclient.client.util.GuidUtils;
import edu.umass.cs.gnscommon.exceptions.client.ClientException;


/**
 * This class creates guids and issues updates on those guids 
 * in a round robin manner. The number of guids and the number of 
 * updates can be configured using input parameters. 
 * 
 * @author ayadav
 */
public class UpdateTimeout4NodeSetup 
{
	public static final String ALIAS_PREFIX		= "GUID";
	public static final String ALIAS_SUFFIX		= "@gmail.com";
	public static final String KEY_NAME			= "attr";
	
	public static final long GNS_REQ_TIMEOUT  	= 5000;
	public static final int GNS_NUM_RETRIES  	= 5;
	
	
	public static void main(String[] args)
	{
		int numGUIDs = Integer.parseInt(args[0]);
		int numUpdates = Integer.parseInt(args[1]);
		
		GNSClient gnsClient;
		GuidEntry[] guidEntryArray = new GuidEntry[numGUIDs];
		
		
		try 
		{
			gnsClient = new GNSClient();
			gnsClient = gnsClient.setForcedTimeout(GNS_REQ_TIMEOUT);
			gnsClient = gnsClient.setNumRetriesUponTimeout(GNS_NUM_RETRIES);
		} catch (IOException e) 
		{
			System.out.println("GNS client connection failed");
			e.printStackTrace();
			return;
		}
		
		System.out.println("Creating "+numGUIDs+" GUIDs");
		
		for(int i=0; i<numGUIDs; i++)
		{
			String alias = ALIAS_PREFIX+i+ALIAS_SUFFIX;
			try 
			{
				gnsClient.execute(GNSCommand.createAccount(alias));
				guidEntryArray[i] = GuidUtils.lookupGuidEntryFromDatabase(gnsClient, alias);
				assert(guidEntryArray[i] != null);
			} 
			catch (ClientException | NoSuchAlgorithmException | IOException e) {
				System.out.println("Exception while creating a GUID with alias "+alias);
				e.printStackTrace();
			}	
		}
		
		System.out.println("Issuing "+numUpdates
					+" in a round-robin manner on "+numGUIDs+" GUIDs");
		
		Random rand = new Random();
		int numFailed = 0;
		int failedGuidIndex = -1;
		boolean persistentFailure = true;
		
		for(int i=0; i<numUpdates; i++)
		{
			GuidEntry guidEntry = guidEntryArray[i%guidEntryArray.length];
			
			try 
			{
				gnsClient.execute
					(GNSCommand.fieldUpdate(guidEntry, KEY_NAME, rand.nextDouble()));
				if(failedGuidIndex == i)
				{
					persistentFailure = false;
				}
			} catch (ClientException | IOException e) 
			{
				numFailed++;
				if(failedGuidIndex == -1)
				{
					failedGuidIndex = i;
				}
				e.printStackTrace();
			}
		}
		
		if(failedGuidIndex == -1)
			persistentFailure = false;
		
		
		System.out.println("Total updates issued="+numUpdates
						+"; Total failed="+numFailed
						+"; PersistentFailure="+persistentFailure);
		gnsClient.close();
	}
}