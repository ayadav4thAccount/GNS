package edu.umass.cs.protocoltask.examples.thresholdfetch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.newApp.packet.Packet;
import edu.umass.cs.gns.newApp.packet.Packet.PacketType;
import edu.umass.cs.nio.GenericMessagingTask;
import edu.umass.cs.nio.NIOTransport;
import edu.umass.cs.nio.nioutils.MessagingTask;
import edu.umass.cs.protocoltask.ProtocolEvent;
import edu.umass.cs.protocoltask.ProtocolTask;
import edu.umass.cs.protocoltask.ThresholdProtocolTask;
import edu.umass.cs.protocoltask.examples.PingPongPacket;
import edu.umass.cs.protocoltask.json.ProtocolPacket;
import edu.umass.cs.utils.Util;

/**
@author V. Arun
 */

/* This protocol task waits for responses from a majority of nodes in 
 * the specified set. It instantiates ThresholdProtocolTask that 
 * requires implementing a "boolean handleEvent(event)" method that 
 * determines whether the response is valid or not. If it is valid,
 * ThresholdProtocolTask automatically marks the corresponding
 * sender as having responded and does not retry ("restart") the 
 * request to that node anymore.
 */
public class MajorityFetchProtocolTask extends ThresholdProtocolTask<Integer, Packet.PacketType, String> {

	private String key = null;

	private final int[] nodes;
	private final Integer myID;

	private static Packet.PacketType[] types = {Packet.PacketType.TEST_PONG};

	private Logger log =  NIOTransport.LOCAL_LOGGER ? Logger.getLogger(getClass().getName()) : GNS.getLogger();

	public MajorityFetchProtocolTask(int id, Set<Integer> nodes) {
		super(nodes, nodes.size()/2+1);
		this.nodes = Util.setToIntArray(nodes);
		this.myID = id;
		log.info("Node" + myID + " constructing protocol task with nodeIDs " +
				nodes);
	}

	/***************************Start of overridden methods *****************************************/
	@Override
	public String getKey() {return this.key;}

	@Override
	public String refreshKey() {
		return (this.key =
				(this.myID.toString() + (int) (Math.random() * Integer.MAX_VALUE)));
	}

	@Override
	public boolean handleEvent(ProtocolEvent<Packet.PacketType,String> event) {

		JSONObject msg = null;
		try {
			msg = ((ProtocolPacket<?,?>)event.getMessage()).toJSONObject();
		} catch(JSONException je) {
			je.printStackTrace();
			return false;
		}
		boolean responded = false;
		try {
			switch(Packet.getPacketType(msg)) {
			case TEST_PONG:
				responded = handlePingPong(new PingPongPacket(msg));
				break;
			default:
				throw new RuntimeException("Unrecognizable message type: " + Packet.getPacketType(msg));
			}
		} catch(JSONException je) {
			je.printStackTrace();
		}
		return responded;
	}

	@Override
	public MessagingTask[] start() {
		PingPongPacket ppp = new PingPongPacket(this.myID, Packet.PacketType.TEST_PING);
		log.info("Node"+myID+" starting protocol task with nodeIDs " + Util.arrayOfIntToString(nodes));
		return new MessagingTask(nodes, ppp).toArray();
	}
	
	@Override
	public GenericMessagingTask<Integer,?>[] handleThresholdEvent(
			ProtocolTask<Integer, PacketType, String>[] ptasks) {
		// do nothing, default is to cancel anyway
		return null;
	}

	@Override
	public MessagingTask[] restart() {
		return start();
	}
	/***************************End of overridden methods *****************************************/

	/***************************Private or testing methods below *********************************/
	private boolean handlePingPong(PingPongPacket ppp) {
		return handlePong(ppp);
	}
	private boolean handlePong(PingPongPacket pong) {
		assert(Integer.valueOf(pong.getInitiator())==this.myID);
		pong.incrCounter();
		int sender = Integer.valueOf(pong.flip(this.myID));
		log.info("Node"+myID+" protocol task received pong from " + sender + ": " + pong);
		return true;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Not unit-testable. Run ExampleNode instead.");
	}

	@Override
	public Set<Packet.PacketType> getEventTypes() {
		return new HashSet<Packet.PacketType>(Arrays.asList(types));//types;
	}
}
