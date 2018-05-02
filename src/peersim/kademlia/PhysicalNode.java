package peersim.kademlia;

import java.util.ArrayList;
import peersim.kademlia.KademliaProtocol;
import peersim.core.Node;
import peersim.config.Configuration; 

public class PhysicalNode {
	private int capacity;
	private int load;
	private ArrayList<Node> kadNodes;
	private int pid;
	private int nodeId;
	private final int ttl = 2;
	
	public PhysicalNode(int nodeId) {
		this.capacity = 0;
		this.load = 0;
		this.pid = Configuration.getPid("init.4loadGenerator.protocol");
		this.kadNodes = new ArrayList<Node>();
		this.nodeId = nodeId;
	}
	
	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}
	
	public void setLoad(int load) {
		this.load = load;
	}
	
	public int getCapacity() {
		return this.capacity;
	}
	
	public int getLoad() {
		return this.load;
	}
	
	public int getNodeId() {
		return this.nodeId;
	}
	
	public void add(Node node) {
		kadNodes.add(node);
	}
	
	public int collectLoad() {
		int totalLoad = 0;
		for(int i = 0; i < kadNodes.size(); i++) {
			if(kadNodes.get(i).isUp())
				totalLoad += ((KademliaProtocol)(kadNodes.get(i).getProtocol(pid))).getLoad();
		}
		return totalLoad;
	}
	
	

	
	/**
	 * DO Load Balance
	 * 
	 * @author zjd
	 */
	public void DoBalance(int pid) {
		// TODO Auto-generated method stub
		
		for(int i = 0; i < this.kadNodes.size(); i++) {
			CollectCloseLoad(kadNodes.get(i), pid);
		}
		DoTransfer();
	}
	
	public void CollectCloseLoad(Node kad, int pid) {
		((KademliaProtocol)(kad.getProtocol(pid))).SendCollectLoad(ttl, pid);
	}
	
	public void DoTransfer() {
		
	}
}
