package de.lmu.ifi.bio.crco.operation;

import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import de.lmu.ifi.bio.crco.data.Entity;
import de.lmu.ifi.bio.crco.data.exceptions.OperationNotPossibleException;
import de.lmu.ifi.bio.crco.network.Network;
import de.lmu.ifi.bio.crco.util.Tuple;

/**
 * Difference between a given network and a set of (n) networks
 * @author robert
 *
 */
public class Difference extends GeneralOperation{



	@Override
	public List<Parameter> getParameters() {
		
		return null;
	}

	@Override
	public void accept(List<Network> networks) throws OperationNotPossibleException{
		if ( networks.size() == 0) return;
		Integer taxId = networks.get(0).getTaxId();
		for(int i = 1 ; i< networks.size();i++){
			if ( !networks.get(i).getTaxId().equals(taxId)){
				throw new OperationNotPossibleException("Intersect not possible for different tax ids");
			}
		}
		
		
	}

	@Override
	public void checkParameter() throws OperationNotPossibleException {
		// TODO Auto-generated method stub
	}

	@Override
	public Network doOperation() {
	
		
		Network ret = Network.getEmptyNetwork(this.getNetworks().get(0).getClass(), "Difference", this.getNetworks().get(0).getTaxId(), false);
		
		Network net0 = this.getNetworks().get(0);
		
		List<Network> networks = this.getNetworks(); 
		
		for(int edgeId : net0.getEdgeIds()){
			boolean unique = true;
			
			for(int i = 1 ; i < networks.size(); i++){
				boolean consistent = networks.get(i).containsEdge(net0.getEdge(edgeId));
				if ( consistent){
					unique=false;
					break;
				}
				
			}
			if ( unique){
				Tuple<Entity, Entity> edge = net0.getEdge(edgeId);
				ret.add(edge.getFirst(),edge.getSecond(),net0.getAnnotation(edgeId));
			}
		}
		
	
		return ret;
	}

}