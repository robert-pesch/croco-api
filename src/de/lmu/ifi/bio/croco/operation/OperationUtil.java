package de.lmu.ifi.bio.croco.operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.reflections.Reflections;

import de.lmu.ifi.bio.croco.connector.QueryService;
import de.lmu.ifi.bio.croco.data.NetworkOperationNode;
import de.lmu.ifi.bio.croco.network.Network;
import de.lmu.ifi.bio.croco.network.NetworkSummary;
import de.lmu.ifi.bio.croco.operation.progress.ProgressInformation;
import de.lmu.ifi.bio.croco.util.CroCoLogger;

public class OperationUtil {


	/**
	 * List all classes implementing the GeneralOperation interface.
	 * @Note reflection seems not to work within OSGI.
	 * @return Set<Class>
	 */
	public static Set<Class<? extends GeneralOperation>> getOperation(){
		Reflections reflections = new Reflections("");
		
		
	     Set<Class<? extends GeneralOperation>> subTypes = 
	               reflections.getSubTypesOf(GeneralOperation.class);
	     
		return subTypes;
	}
	/**
	 * Process a network operation hierarchy in order to produce a final network.
	 * @param service
	 * @param operation
	 * @return
	 * @throws Exception
	 */
	public static Network process(QueryService service, NetworkOperationNode operation) throws Exception  {
		return process(service,operation,null);
	}
	/**
	 * Estimates the number of operation defined in the network hierarchy (the number of non-leaf nodes)
	 * @param operatorable
	 * @return
	 */
	public static int getNumberOfOperations(NetworkOperationNode operatorable){
		int k = 0;
		Stack<NetworkOperationNode> stack = new Stack<NetworkOperationNode>();
		stack.add(operatorable);
		while(!stack.isEmpty()){
			NetworkOperationNode top = stack.pop();
			k++;
			if ( top.getChildren() != null){
				for(NetworkOperationNode child : top.getChildren()){
					stack.add(child);
				}
			}
		}
		
		return k;
	}
	/**
	 * Process a network operation hierarchy in order to produce a final network.
	 * @param service
	 * @param operation
	 * @return
	 * @throws Exception
	 */
	public static Network process(QueryService service, NetworkOperationNode operation, ProgressInformation pi) throws Exception  {
		List<Network> ret = new ArrayList<Network>();
		if ( pi != null && pi.isKill()) return null;
		for(NetworkOperationNode child : operation.getChildren()){
			
			if ( pi != null && pi.isKill()) return null;
			Network p = OperationUtil.process(service,child,pi);
			
			ret.add(p);
		}

		CroCoLogger.getLogger().debug(operation + " on " +  ret.size() + " networks");
		
		GeneralOperation operator = operation.getOperator();
		
		if ( pi != null && pi.isKill()) return null;
		if ( pi != null)pi.nextStep(operator);

		if ( ret.size() > 0)
		    operator.setInputNetwork(ret);
		CroCoLogger.getLogger().info("Set input: " + ret + " for:" + operation);
		Network network= operator.operate();
		if ( network.getNetworkSummary() == null){
			NetworkSummary summary = new NetworkSummary(String.format("%s (Operation: %s", network.getName() , operator.getClass().getSimpleName()), network.getNodes().size(),network.size());
			network.addNetworkSummary(summary);
		}
		for(Network net : ret){
			network.getNetworkSummary().addChild(net.getNetworkSummary());
		}

		return network;
		
	}
}
