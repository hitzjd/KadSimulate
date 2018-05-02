package peersim.kademlia;

/**
 * A Kademlia implementation for PeerSim extending the EDProtocol class.<br>
 * See the Kademlia bibliografy for more information about the protocol.
 *
 * 
 * @author Daniele Furlan, Maurizio Bonani
 * @version 1.0
 */

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.TreeMap;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.edsim.EDSimulator;
import peersim.transport.UnreliableTransport;
import peersim.kademlia.PhysicalNode;

//__________________________________________________________________________________________________
public class KademliaProtocol implements Cloneable, EDProtocol {

	// VARIABLE PARAMETERS
	final String PAR_K = "K";
	final String PAR_ALPHA = "ALPHA";
	final String PAR_BITS = "BITS";

	private static final String PAR_TRANSPORT = "transport";
	private static String prefix = null;
	private UnreliableTransport transport;
	private int tid;
	private int kademliaid;
	private int load;
	private PhysicalNode physical;

	/**
	 * allow to call the service initializer only once
	 */
	private static boolean _ALREADY_INSTALLED = false;

	/**
	 * nodeId of this pastry node
	 */
	public BigInteger nodeId;

	/**
	 * routing table of this pastry node
	 */
	public RoutingTable routingTable;

	/**
	 * trace message sent for timeout purpose
	 */
	private TreeMap<Long, Long> sentMsg;

	/**
	 * find operations set
	 */
	private LinkedHashMap<Long, FindOperation> findOp;

	/**
	 * Replicate this object by returning an identical copy.<br>
	 * It is called by the initializer and do not fill any particular field.
	 * 
	 * @return Object
	 */
	public Object clone() {
		KademliaProtocol dolly = new KademliaProtocol(KademliaProtocol.prefix);
		return dolly;
	}

	/**
	 * Used only by the initializer when creating the prototype. Every other instance call CLONE to create the new object.
	 * 
	 * @param prefix
	 *            String
	 */
	public KademliaProtocol(String prefix) {
		this.nodeId = null; // empty nodeId
		KademliaProtocol.prefix = prefix;

		_init();

		routingTable = new RoutingTable();

		sentMsg = new TreeMap<Long, Long>();

		findOp = new LinkedHashMap<Long, FindOperation>();

		tid = Configuration.getPid(prefix + "." + PAR_TRANSPORT);
		
		load = 0;
	}

	/**
	 * This procedure is called only once and allow to inizialize the internal state of KademliaProtocol. Every node shares the
	 * same configuration, so it is sufficient to call this routine once.
	 */
	private void _init() {
		// execute once
		if (_ALREADY_INSTALLED)
			return;

		// read paramaters
		KademliaCommonConfig.K = Configuration.getInt(prefix + "." + PAR_K, KademliaCommonConfig.K);
		KademliaCommonConfig.ALPHA = Configuration.getInt(prefix + "." + PAR_ALPHA, KademliaCommonConfig.ALPHA);
		KademliaCommonConfig.BITS = Configuration.getInt(prefix + "." + PAR_BITS, KademliaCommonConfig.BITS);

		_ALREADY_INSTALLED = true;
	}

	/**
	 * Search through the network the Node having a specific node Id, by performing binary serach (we concern about the ordering
	 * of the network).
	 * 
	 * @param searchNodeId
	 *            BigInteger
	 * @return Node
	 */
	private Node nodeIdtoNode(BigInteger searchNodeId) {
		if (searchNodeId == null)
			return null;

		int inf = 0;
		int sup = Network.size() - 1;
		int m;

		while (inf <= sup) {
			m = (inf + sup) / 2;

			BigInteger mId = ((KademliaProtocol) Network.get(m).getProtocol(kademliaid)).nodeId;

			if (mId.equals(searchNodeId))
				return Network.get(m);

			if (mId.compareTo(searchNodeId) < 0)
				inf = m + 1;
			else
				sup = m - 1;
		}

		// perform a traditional search for more reliability (maybe the network is not ordered)
		BigInteger mId;
		for (int i = Network.size() - 1; i >= 0; i--) {
			mId = ((KademliaProtocol) Network.get(i).getProtocol(kademliaid)).nodeId;
			if (mId.equals(searchNodeId))
				return Network.get(i);
		}

		return null;
	}

	/**
	 * Perform the required operation upon receiving a message in response to a ROUTE message.<br>
	 * Update the find operation record with the closest set of neighbour received. Than, send as many ROUTE request I can
	 * (according to the ALPHA parameter).<br>
	 * If no closest neighbour available and no outstanding messages stop the find operation.
	 * 
	 * @param m
	 *            Message
	 * @param myPid
	 *            the sender Pid
	 */
	private void route(Message m, int myPid) {
		// add message source to my routing table
		if (m.src != null) {
			routingTable.addNeighbour(m.src);
		}

		// get corresponding find operation (using the message field operationId)
		FindOperation fop = this.findOp.get(m.operationId);

		if (fop != null) {
			// save received neighbour in the closest Set of fin operation
			try {
				fop.elaborateResponse((BigInteger[]) m.body);
			} catch (Exception ex) {
				fop.available_requests++;
			}

			while (fop.available_requests > 0) { // I can send a new find request

				// get an available neighbour
				BigInteger neighbour = fop.getNeighbour();

				if (neighbour != null) {
					// create a new request to send to neighbour
					Message request = new Message(Message.MSG_ROUTE);
					request.operationId = m.operationId;
					request.src = this.nodeId;
					request.dest = m.dest;

					// increment hop count
					fop.nrHops++;

					// send find request
					sendMessage(request, neighbour, myPid);
				} else if (fop.available_requests == KademliaCommonConfig.ALPHA) { // no new neighbour and no outstanding requests
					// search operation finished
					findOp.remove(fop.operationId);

					if (fop.body.equals("Automatically Generated Traffic") && fop.closestSet.containsKey(fop.destNode)) {
						// update statistics
						long timeInterval = (CommonState.getTime()) - (fop.timestamp);
						KademliaObserver.timeStore.add(timeInterval);
						KademliaObserver.hopStore.add(fop.nrHops);
						KademliaObserver.msg_deliv.add(1);
					}

					return;

				} else { // no neighbour available but exists oustanding request to wait
					return;
				}
			}
		} else {
			System.err.println("There has been some error in the protocol");
		}
	}

	/**
	 * Response to a route request.<br>
	 * Find the ALPHA closest node consulting the k-buckets and return them to the sender.
	 * 
	 * @param m
	 *            Message
	 * @param myPid
	 *            the sender Pid
	 */
	private void routeResponse(Message m, int myPid) {
		// get the ALPHA closest node to destNode
		BigInteger[] neighbours = this.routingTable.getNeighbours(m.dest, m.src);

		// create a response message containing the neighbours (with the same id of the request)
		Message response = new Message(Message.MSG_RESPONSE, neighbours);
		response.operationId = m.operationId;
		response.dest = m.dest;
		response.src = this.nodeId;
		response.ackId = m.id; // set ACK number

		// send back the neighbours to the source of the message
		sendMessage(response, m.src, myPid);
	}

	/**
	 * Start a find node opearation.<br>
	 * Find the ALPHA closest node and send find request to them.
	 * 
	 * @param m
	 *            Message received (contains the node to find)
	 * @param myPid
	 *            the sender Pid
	 */
	private void find(Message m, int myPid) {

		KademliaObserver.find_op.add(1);

		// create find operation and add to operations array
		FindOperation fop = new FindOperation(m.dest, m.timestamp);
		fop.body = m.body;
		findOp.put(fop.operationId, fop);

		// get the ALPHA closest node to srcNode and add to find operation
		BigInteger[] neighbours = this.routingTable.getNeighbours(m.dest, this.nodeId);
		fop.elaborateResponse(neighbours);
		fop.available_requests = KademliaCommonConfig.ALPHA;

		// set message operation id
		m.operationId = fop.operationId;
		m.type = Message.MSG_ROUTE;
		m.src = this.nodeId;

		// send ALPHA messages
		for (int i = 0; i < KademliaCommonConfig.ALPHA; i++) {
			BigInteger nextNode = fop.getNeighbour();
			if (nextNode != null) {
				sendMessage(m.copy(), nextNode, myPid);
				fop.nrHops++;
			}
		}

	}

	/**
	 * send a message with current transport layer and starting the timeout timer (wich is an event) if the message is a request
	 * 
	 * @param m
	 *            the message to send
	 * @param destId
	 *            the Id of the destination node
	 * @param myPid
	 *            the sender Pid
	 */
	public void sendMessage(Message m, BigInteger destId, int myPid) {
		// add destination to routing table
		this.routingTable.addNeighbour(destId);

		Node src = nodeIdtoNode(this.nodeId);
		Node dest = nodeIdtoNode(destId);

		transport = (UnreliableTransport) (Network.prototype).getProtocol(tid);
		transport.send(src, dest, m, kademliaid);

		if (m.getType() == Message.MSG_ROUTE) { // is a request
			Timeout t = new Timeout(destId, m.id, m.operationId);
			long latency = transport.getLatency(src, dest);

			// add to sent msg
			this.sentMsg.put(m.id, m.timestamp);
			EDSimulator.add(4 * latency, t, src, myPid); // set delay = 2*RTT
		}
	}
	
	// Send CollectLoad request
	public void SendCollectLoad(int ttl, int pid) {
		BigInteger[] neighbours = this.routingTable.getNeighbours(this.nodeId, this.nodeId);
		for(int i = 0; i < KademliaCommonConfig.K; i++) {
			Message request = Message.makeCollectLoad((Object)ttl);
			request.dest = neighbours[i];
			request.src = this.nodeId;
			sendMessage(request, neighbours[i], pid);
		}
		
	}
	
    // handle CollectLoad request
	public void handleClosedLoad(Message m, int myPid) {
		int ttl = (int)m.body;
		if(ttl < 0)
			return;
		
		// response physical node's load
		int load = this.physical.collectLoad();
		int physicalId = this.physical.getNodeId();
		
		Message response = Message.makeCollectresponse(physicalId, load);
		response.operationId = m.operationId;
		response.dest = m.src;
		response.src = this.nodeId;
		response.ackId = m.id;
		sendMessage(response, m.src, myPid);
		
		// ask k closest node's load and response to src		
		if(ttl-- > 0) {
			BigInteger[] neighbours = this.routingTable.getNeighbours(this.nodeId, m.src);
			for(int i = 0; i < KademliaCommonConfig.K; i++) {
				Message request = Message.makeCollectLoad((Object)ttl);
				request.operationId = m.operationId;
				request.dest = neighbours[i];
				request.src = m.src;
				sendMessage(request, neighbours[i], myPid);
			}
		}
	}
	
	//handle CollectLoad response
	public void handleCollectLoadResponse() {
		
	}

	/**
	 * manage the peersim receiving of the events
	 * 
	 * @param myNode
	 *            Node
	 * @param myPid
	 *            int
	 * @param event
	 *            Object
	 */
	public void processEvent(Node myNode, int myPid, Object event) {

		// Parse message content Activate the correct event manager fot the particular event
		this.kademliaid = myPid;
		++this.load;

		Message m;

		switch (((SimpleEvent) event).getType()) {
			case Message.MSG_COLLECTLOAD:
				m = (Message) event;
				handleClosedLoad(m, myPid);
				break;
			
			case Message.MSG_LOADRESPONSE:
				break;
		
			case Message.MSG_RESPONSE:
				m = (Message) event;
				sentMsg.remove(m.ackId);
				route(m, myPid);
				break;

			case Message.MSG_FINDNODE:
				m = (Message) event;
				find(m, myPid);
				break;

			case Message.MSG_ROUTE:
				m = (Message) event;
				routeResponse(m, myPid);
				break;

			case Message.MSG_EMPTY:
				// TO DO
				break;

			case Message.MSG_STORE:
				// TO DO
				break;

			case Timeout.TIMEOUT: // timeout
				Timeout t = (Timeout) event;
				if (sentMsg.containsKey(t.msgID)) { // the response msg isn't arrived
					// remove form sentMsg
					sentMsg.remove(t.msgID);
					// remove node from my routing table
					this.routingTable.removeNeighbour(t.node);
					// remove from closestSet of find operation
					this.findOp.get(t.opID).closestSet.remove(t.node);
					// try another node
					Message m1 = new Message();
					m1.operationId = t.opID;
					m1.src = nodeId;
					m1.dest = this.findOp.get(t.opID).destNode;
					this.route(m1, myPid);
				}
				break;

		}

	}

	/**
	 * set the current NodeId
	 * 
	 * @param tmp
	 *            BigInteger
	 */
	public void setNodeId(BigInteger tmp) {
		this.nodeId = tmp;
		this.routingTable.nodeId = tmp;
	}
	
	/**
	 * get the current Kad Node's load
	 * 
	 */
	public int getLoad() {
		return this.load;
	}
	
	/**
	 * reset the current Kad Node's load
	 * 
	 * @param tmp
	 *            int
	 */
	public void resetLoad(int load) {
		this.load = load;
	}
	
	public void setPhysical(PhysicalNode physical) {
		this.physical = physical;
	}
	
}
