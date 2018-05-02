/**
 * 
 */
package peersim.kademlia;

import peersim.kademlia.PhysicalNode;

/**
 * @author Administrator
 *
 */
public class PhysicalNetwork{
	
	public static PhysicalNetwork network = null;
	public static PhysicalNode[] nodes = null;
	public static int N = 0;
	
	public static PhysicalNetwork getInstance() {
		if(network == null) {
			network = new PhysicalNetwork(500);
		}
		return network;
	}
	
	private PhysicalNetwork(int n) {
		N = n;
		nodes = new PhysicalNode[N];
		for(int i = 0; i < N; i++)
			nodes[i] = new PhysicalNode(i);
	}
}
