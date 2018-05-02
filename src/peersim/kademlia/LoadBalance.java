/**
 * 
 */
package peersim.kademlia;

import peersim.core.Control;
import peersim.kademlia.PhysicalNetwork;
import peersim.config.Configuration;

/**
 * @author Administrator
 *
 */
public class LoadBalance implements Control {
	private final String PAR_PROT = "protocol";
	private int pid;
	
	public LoadBalance(String prefix) {
		pid = Configuration.getPid(prefix + '.' + PAR_PROT);
	}

	/* (non-Javadoc)
	 * @see peersim.core.Control#execute()
	 */
	@Override
	public boolean execute() {
		// TODO Auto-generated method stub
		PhysicalNetwork network = PhysicalNetwork.getInstance();
		
		for(int i = 0; i < network.N; i++) {
			network.nodes[i].DoBalance(pid);
		}
		
		return false;
	}

}
