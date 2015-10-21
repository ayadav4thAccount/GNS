package edu.umass.cs.gns.activecode.worker;

import java.io.BufferedReader;
import java.io.PrintWriter;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.gns.activecode.ActiveCodeUtils;
import edu.umass.cs.gns.activecode.protocol.ActiveCodeMessage;
import edu.umass.cs.gns.activecode.protocol.ActiveCodeQueryRequest;
import edu.umass.cs.gns.activecode.protocol.ActiveCodeQueryResponse;
import edu.umass.cs.gns.utils.ValuesMap;

public class ActiveCodeGuidQuerier {
	private PrintWriter out;
	private BufferedReader in;
	
	public ActiveCodeGuidQuerier(BufferedReader in, PrintWriter out) {
		this.out = out;
		this.in = in;
	}
	
	/**
	 * Queries (read or write) a guid
	 * @param acqreq the request params
	 * @return the response
	 */
	private ActiveCodeQueryResponse queryGuid(ActiveCodeQueryRequest acqreq) {
		try {
			ActiveCodeMessage acm = new ActiveCodeMessage();
			acm.acqreq = acqreq;
			// Send off the query request
			ActiveCodeUtils.sendMessage(out, acm);
			
			// Wait for a response
		    ActiveCodeMessage acmqr = ActiveCodeUtils.getMessage(in);
		    return acmqr.acqresp;
		    
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Return an empty response to designate failure
		return new ActiveCodeQueryResponse();
	}
	
	/**
	 * Reads a guid by passing the query on to the GNS process
	 * @param guid the guid
	 * @param field the field
	 * @return the ValuesMap response
	 */
	public ValuesMap readGuid(String guid, String field) {
		ActiveCodeQueryRequest acqreq = new ActiveCodeQueryRequest(guid, field, null, "read");
		ActiveCodeQueryResponse acqresp = queryGuid(acqreq);
		
		ValuesMap vm = null;
		
		try {
			vm = new ValuesMap(new JSONObject(acqresp.valuesMapString));
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		return vm;   
	}
	
	/**
	 * Writes to a guid by passing the query on to the GNS process
	 * (Only local guid currently supported)
	 * @param guid the guid
	 * @param field the field
	 * @param newValue the new values as a ValuesMap object
	 * @return whether or not the write succeeded
	 */
	public boolean writeGuid(String guid, String field, ValuesMap newValue) {
		ActiveCodeQueryRequest acqreq = new ActiveCodeQueryRequest(guid, field, newValue.toString(), "write");
		ActiveCodeQueryResponse acqresp = queryGuid(acqreq);
		return acqresp.success;  
	}
}