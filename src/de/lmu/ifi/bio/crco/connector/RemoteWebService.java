package de.lmu.ifi.bio.crco.connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.StreamException;

import de.lmu.ifi.bio.crco.data.ContextTreeNode;
import de.lmu.ifi.bio.crco.data.Entity;
import de.lmu.ifi.bio.crco.data.NetworkHierachyNode;
import de.lmu.ifi.bio.crco.data.Option;
import de.lmu.ifi.bio.crco.data.Species;
import de.lmu.ifi.bio.crco.data.exceptions.CroCoException;
import de.lmu.ifi.bio.crco.intervaltree.peaks.TFBSPeak;
import de.lmu.ifi.bio.crco.network.DirectedNetwork;
import de.lmu.ifi.bio.crco.network.Network;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologDatabaseType;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologMapping;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologMappingInformation;
import de.lmu.ifi.bio.crco.util.CroCoLogger;
import de.lmu.ifi.bio.crco.util.Pair;

public class RemoteWebService implements QueryService{
	private String baseUrl;
	public RemoteWebService(String baseUrl){
		this.baseUrl = baseUrl;
	
	}
	public static InputStream getStreamedData(String baseUrl, String method, Object...parameters) throws Exception{
		URL url = new URL(String.format("%s/%s",baseUrl  , method));
		
		CroCoLogger.getLogger().debug(String.format("Query remote method %s",method));
		
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		
		XStream xstream = new XStream();
		
		ObjectOutputStream out = xstream.createObjectOutputStream(conn.getOutputStream());
		for(Object parameter: parameters){
			out.writeObject(parameter);
		}
		out.close();
		
		return conn.getInputStream();
	}
	
	public static Object performceOperation(String baseUrl, String method, Object...parameters) throws IOException{
		URL url = new URL(String.format("%s/%s",baseUrl  , method));
		
		CroCoLogger.getLogger().debug(String.format("Query remote method %s",method));
		
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		
		XStream xstream = new XStream();
		
		ObjectOutputStream out = xstream.createObjectOutputStream(conn.getOutputStream());
		for(Object parameter: parameters){
			out.writeObject(parameter);
		}
		out.close();
		
		ObjectInputStream in = null;
		try{
			 in = xstream.createObjectInputStream(conn.getInputStream());
		}catch(StreamException e){
			CroCoLogger.getLogger().fatal("Cannot create object:" + e.getMessage());
			return null;
		}
		Object object = null;
		CroCoLogger.getLogger().debug(String.format("Reading results"));
		
		
		try {
			object =  in.readObject() ;
		} catch (Exception e) {
			byte[] tmp = new byte[1024];
			conn.getInputStream().read(tmp);
			String errorMessage = new String(tmp).trim();
			CroCoLogger.getLogger().fatal(String.format("Cannnot read result from web service. Message from server %s",errorMessage));
		}
		return object;
	}
	@Override
	public OrthologMapping getOrthologMapping(OrthologMappingInformation orthologMappingInformation)throws Exception {
		return (OrthologMapping)performceOperation(baseUrl,"getOrthologMapping",orthologMappingInformation);
	}
	@Override
	public Network readNetwork(Integer groupId, Integer contextId,boolean globalRepository) throws Exception {
		NetworkHierachyNode networkNode = this.getNetworkHierachyNode(groupId);

		Network network = new DirectedNetwork(networkNode.getName(),networkNode.getTaxId(),globalRepository);
		
		InputStream is = getStreamedData(baseUrl,"readNetwork",groupId,contextId,globalRepository);
	
		
		BufferedReader br = null;
		try{
			br = new BufferedReader(new InputStreamReader(new GZIPInputStream(is)));
		}catch(Exception e){
			 byte[] tmp = new byte[1014];
			is.read(tmp);
			String errorMessage =new String(tmp).trim(); 
			throw new CroCoException(String.format("Can not read network %d. Message from server: %s",groupId,errorMessage));
		}
		String line = null;
		while (( line = br.readLine())!=null){
			String[] tokens = line.split("\t");
			Entity factor = new Entity(tokens[0]);
			Entity target = new Entity(tokens[1]);
			network.add(factor, target, groupId);
		}
		br.close();
		
		is.close();
		
		return network;
	}
	@Override
	public List<TFBSPeak> getTFBSBindings(int groupId, Integer contextId)throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	
	@Override
	public NetworkHierachyNode getNetworkHierachy(String path) throws Exception {
		return (NetworkHierachyNode)performceOperation(baseUrl,"getNetworkHierachy",path);
	}


	@Override
	public List<NetworkHierachyNode> findNetwork(List<Pair<Option, String>> options) throws Exception {
		return (List)performceOperation(baseUrl,"findNetwork",options);
	}

	@Override
	public NetworkHierachyNode getNetworkHierachyNode(Integer groupId) throws Exception {
		return (NetworkHierachyNode)performceOperation(baseUrl,"getNetworkHierachyNode",groupId);
	}

	@Override
	public List<Pair<Option, String>> getNetworkInfo(Integer groupId) throws Exception {
		return (List)performceOperation(baseUrl,"getNetworkInfo",groupId);
	}

	@Override
	public List<Entity> getEntities(Species species, String annotation, ContextTreeNode context) throws Exception {
		return (List)performceOperation(baseUrl,"getEntities",annotation,context);
	}



	@Override
	public Integer getNumberOfEdges(Integer groupId) throws Exception {
		return (Integer)performceOperation(baseUrl,"getNumberOfEdges",groupId);
	}




	@Override
	public List<OrthologMappingInformation> getTransferTargetSpecies(int taxId)
			throws Exception {
		return (List) performceOperation(baseUrl,"getTransferTargetSpecies",taxId);
		
	}



	@Override
	public List<OrthologMappingInformation> getOrthologMappingInformation(
			OrthologDatabaseType database, Species species1, Species species2)
			throws Exception {
		
		return (List) performceOperation(baseUrl,"getOrthologMappingInformation",database,species1,species2);
	}

	@Override
	public List<Species> getPossibleTransferSpecies() throws Exception {
		return (List)performceOperation(baseUrl,"getPossibleTransferSpecies");
	}




	@Override
	public List<ContextTreeNode> getContextTreeNodes(String name) throws Exception {
		return (List)performceOperation(baseUrl,"getContextTreeNodes",name);
	}

	@Override
	public List<ContextTreeNode> getChildren(ContextTreeNode node)throws Exception {
		return (List)performceOperation(baseUrl,"getChildren",node);
	}


	public ContextTreeNode getContextTreeNode(String sourceId) throws Exception {
		return (ContextTreeNode)performceOperation(baseUrl,"getSpecies",sourceId);
	}

	@Override
	public List<Species> getSpecies(String prefix) throws Exception {
		return (List)performceOperation(baseUrl,"getSpecies",prefix);
	}

	@Override
	public Species getSpecies(Integer taxId) throws Exception {
		return (Species)performceOperation(baseUrl,"getSpecies",taxId);
	}
	
	

}