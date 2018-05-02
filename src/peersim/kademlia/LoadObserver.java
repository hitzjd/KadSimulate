/**
 * 
 */
package peersim.kademlia;

import peersim.core.CommonState;
import peersim.core.Control;
import peersim.kademlia.PhysicalNetwork;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import peersim.config.Configuration;

/**
 * @author Administrator
 *
 */
public class LoadObserver implements Control {
	private final String PAR_PROT = "protocol";
	
	private int pid;
	
	public LoadObserver(String prefix) {
		pid = Configuration.getPid(prefix + '.' + PAR_PROT);
	}
	
	/* (non-Javadoc)
	 * @see peersim.core.Control#execute()
	 */
	@Override
	public boolean execute() {
		// TODO Auto-generated method stub
		PhysicalNetwork network = PhysicalNetwork.getInstance();
		String output = "";
		for(int i = 0; i < network.N; i++) {
			output = output + String.valueOf(network.nodes[i].collectLoad()) + " ";
		}
		output += "\n";
		
		if (CommonState.getTime() == 3600000) {
			try {
				FileWriter out = new FileWriter("output.txt");
				out.write(output);
				out.close();
			}catch (IOException e) {
				// TODO: handle exception
			}
		}
		
		return false;
	}

}
