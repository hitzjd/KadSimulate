/**
 * 
 */
package peersim.kademlia;

import peersim.kademlia.PhysicalNetwork;
import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;

/**
 * Create N physical nodes who hold kad nodes
 * and generate every nodes' capacity
 * 
 * @author Administrator
 *
 */
public class LoadGenerator implements Control {
	
	private final static String PAR_PROT = "protocol";
	private final static String MAX_CAPACITY = "MaxCapacity";
	
	private final int pid;
	private final int maxCapacity;
	
	
	public LoadGenerator(String prefix) {
		pid = Configuration.getPid(prefix + '.' + PAR_PROT);
		maxCapacity = Configuration.getInt(prefix + '.' + MAX_CAPACITY);
	}

	/* (non-Javadoc)
	 * @see peersim.core.Control#execute()
	 */
	@Override
	public boolean execute() {
		// init every physical node's capacity
		PhysicalNetwork network = PhysicalNetwork.getInstance();
		for(int i = 0; i < network.N; i++) {
			int capacity = CommonState.r.nextInt(maxCapacity);
			network.nodes[i].setCapacity(capacity);
		}
		
		// assign kad node to physical node
		for(int i = 0; i < Network.size(); i++) {
			int physicalId = CommonState.r.nextInt(network.N);
			Node node = Network.get(i);
			network.nodes[physicalId].add(node);
			((KademliaProtocol)(node.getProtocol(pid))).setPhysical(network.nodes[physicalId]);
		}
		
		return false;
	}

}
