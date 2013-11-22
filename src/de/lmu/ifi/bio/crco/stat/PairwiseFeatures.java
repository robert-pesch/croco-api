package de.lmu.ifi.bio.crco.stat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.reflections.Reflections;

import de.lmu.ifi.bio.crco.connector.LocalService;
import de.lmu.ifi.bio.crco.connector.QueryService;
import de.lmu.ifi.bio.crco.data.Entity;
import de.lmu.ifi.bio.crco.data.Option;
import de.lmu.ifi.bio.crco.data.Species;
import de.lmu.ifi.bio.crco.network.DirectedNetwork;
import de.lmu.ifi.bio.crco.network.Network;
import de.lmu.ifi.bio.crco.operation.Transfer;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologDatabaseType;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologMappingInformation;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologRepository;
import de.lmu.ifi.bio.crco.stat.generator.PairwiseStatGenerator;
import de.lmu.ifi.bio.crco.stat.generator.PairwiseStatGenerator.FeatureType;
import de.lmu.ifi.bio.crco.util.CroCoLogger;
import de.lmu.ifi.bio.crco.util.NetworkHierachy;
import de.lmu.ifi.bio.crco.util.NetworkHierachy.CroCoRepositoryProcessor;
import de.lmu.ifi.bio.crco.util.Pair;

public class PairwiseFeatures {
	public class NetworkCollector implements CroCoRepositoryProcessor {
		private List<Network> networks;
		
		public NetworkCollector(){
			this.networks = new ArrayList<Network>();
		}
		public List<Network> getNetworks(){
			return networks;
		}
		
		@Override
		public void init(Integer rootId) throws Exception {}

		@Override
		public void process(Integer rootId, Integer networkId, File networkFile, File infoFile, File statFile) throws Exception {
			Network network = NetworkHierachy.getNetwork(infoFile, null, false);
			network.addNetworkInfo(Option.networkFile, networkFile.toString());
			networks.add(network);
		}

		@Override
		public void finish() throws Exception {}
		
	}
	public static class Check{
		public static void main(String[] args) throws Exception{
			HelpFormatter lvFormater = new HelpFormatter();
			CommandLineParser parser = new BasicParser();
		
			Options options = new Options();
			options.addOption(OptionBuilder.withLongOpt("repositoryDir").withArgName("DIR").withDescription("Location of the croco Repository").isRequired().hasArgs(1).create("repositoryDir"));
			
			CommandLine line = null;
			try{
				line = parser.parse( options, args );
			}catch(Exception e){
				System.err.println( e.getMessage());
				lvFormater.printHelp(120, "java " + PairwiseFeatures.class.getName(), "", options, "", true);
				System.exit(1);
			}
			File repositoryDir = new File(line.getOptionValue("repositoryDir"));

			PairwiseFeatures features = new PairwiseFeatures(repositoryDir);
			
			NetworkCollector collector = features.new NetworkCollector(); 
			NetworkHierachy hierachy = new NetworkHierachy();
			hierachy.processHierachy(repositoryDir, collector, null);
			
			List<Network> networks = collector.getNetworks();
			
			
			for(Network sourceNetwork : networks){
				File networkFile = new File(sourceNetwork.getOptionValue(Option.networkFile));
				
				File statFile =new File(networkFile.toString().replace(".network.gz", ".stat"));
				//sourceNetwork.getTaxId()
				sourceNetwork.addNetworkInfo( Option.networkFile, networkFile.toString());
				if ( !Species.isKnownSpecies(sourceNetwork.getTaxId())) continue;
				
				String sourceNetworkName =sourceNetwork.getOptionValue(Option.networkFile).replace(repositoryDir.toString(), "") ;
				
				HashMap<Option,HashSet<String>> computations = PairwiseFeatures.readCurrentOutputFile(repositoryDir, statFile);
				for(int i = 0 ; i< networks.size() ; i++){
					Network network = networks.get(i);
					if ( !Species.isKnownSpecies(network.getTaxId())) continue;
					
					String targetNetworkName = network.getOptionValue(Option.networkFile).replace(repositoryDir.toString(),"").replace("//", "/");

					if ( ! features.canSkip(computations, sourceNetwork, sourceNetworkName, network, targetNetworkName) ) {
						System.out.printf("Missing %s in %s\n", targetNetworkName,sourceNetworkName );
						break;
					}
					
					
				}
			}
		}

	}
	
	private QueryService service;
	private File repositoryDir;
	private HashMap<Option, PairwiseStatGenerator> generators;
	private int bufferSize = 20;
	private HashMap<Pair<Integer,Integer>,OrthologMappingInformation> possibleMappings;
	//private HashMap<Option,HashSet<String>> computations = null;
	public PairwiseFeatures( File repositoryDir ) throws Exception{
		this.repositoryDir = repositoryDir;
		this.service = new LocalService();
		
		this.possibleMappings = new HashMap<Pair<Integer,Integer>,OrthologMappingInformation> ();
	
		this.generators =new HashMap<Option, PairwiseStatGenerator> ();
		//this.computations = new HashMap<Option,HashSet<String>>();
		

		for(int i = 0 ; i< Species.knownSpecies.size(); i++){
			for(int j = i+1 ; j< Species.knownSpecies.size(); j++){
				List<OrthologMappingInformation> orthologMappings = service.getOrthologMappingInformation(OrthologDatabaseType.EnsemblCompara, Species.knownSpecies.get(i),Species.knownSpecies.get(j));
				for(OrthologMappingInformation orthologMapping: orthologMappings){
					Integer tId1 = orthologMapping.getSpecies1().getTaxId();
					Integer tId2 = orthologMapping.getSpecies2().getTaxId();
					
					CroCoLogger.getLogger().info(String.format("Added ortholog mapping: %d to %d",tId1,tId2));
					possibleMappings.put(new Pair<Integer,Integer>(tId1,tId2),orthologMapping);
				}
			}	
		}

		CroCoLogger.getLogger().info(String.format("Ortholog mappings: %d",possibleMappings.size()));
		
		
		findGenerators();
	}
	
	private Network transfer(Network sourceNetwork,Network network)  throws Exception{
		
		Transfer transfer = new Transfer();
		ArrayList<OrthologMappingInformation> orthologMappings = new ArrayList<OrthologMappingInformation>();
		orthologMappings.add(possibleMappings.get(new Pair<Integer,Integer>(network.getTaxId(),sourceNetwork.getTaxId())));
		if ( orthologMappings.size() == 0) {
			CroCoLogger.getLogger().warn(String.format("No ortholog mappings given for network %s. Tried to transfer from TaxId %d to taxId %d ",network.getName(),network.getTaxId(),sourceNetwork.getTaxId()));
			return new DirectedNetwork(network);
		}
		transfer.setInput(Transfer.OrthologRepository,  OrthologRepository.getInstance(service));
		transfer.setInput(Transfer.OrthologMappingInformation,orthologMappings);
			
		transfer.setInputNetwork(network);
		Network transferred = transfer.operate();
	
		return transferred;
		
	}

	public static HashMap<Option,HashSet<String>>  readCurrentOutputFile(File repositoryDir,File outputFile) throws Exception{
		HashMap<Option,HashSet<String>>computations = new HashMap<Option,HashSet<String>>();
		if ( outputFile.exists()){
			BufferedReader br = new BufferedReader(new FileReader(outputFile));
			String line = null;
			while ((line=br.readLine())!=null){
				if ( line.startsWith("#")) continue;
				String[] tokens = line.split("\t");
				Option option = Option.valueOf(tokens[0]) ;
				String file1 = tokens[1];
				String file2 = tokens[2];
				if ( !computations.containsKey(option)){
					computations.put(option, new HashSet<String>());
				}
				computations.get(option).add(file2.replace(repositoryDir.getName(), ""));
			}
			br.close();
			//computations = FileUtil.readN1MappingFile(outputFile, "\t", 0,1, false, true, false);
		}
		return computations;
	}
	public void  findGenerators() throws Exception{
		Reflections reflections = new Reflections("de.lmu.ifi.bio.crco.stat.generator");
		Set<Class<? extends PairwiseStatGenerator>> subTypes = reflections.getSubTypesOf(PairwiseStatGenerator.class);

		
		for (Class<? extends PairwiseStatGenerator> c : subTypes) {
			PairwiseStatGenerator generator = c.getConstructor().newInstance();
			if (generators.containsKey(generator.getOption())) {
				throw new RuntimeException("Two stat processors for same type:"+ generator.getOption());
			}
			if ( generator.getOption() != null){
				CroCoLogger.getLogger().debug(String.format("Register generator %s for option %s",generator.getClass().getSimpleName(),generator.getOption().name()));
				generators.put(generator.getOption(), generator);
			}
		}
	}
	



	
	public boolean doOverlap( Integer taxId1, Set<String> factorList1, Integer taxId2, Set<String> factorList2){
		
		 if (! taxId1.equals(taxId2)){
			 Set<String> transferredList2 = new HashSet<String>();
			 OrthologMappingInformation mapping = possibleMappings.get(new Pair<Integer,Integer>(taxId1,taxId2));
			 if ( mapping == null) throw new RuntimeException("No ortholog mapping for" + taxId1 + " " + taxId2);
			 for(String factor :factorList2 ){
				 Set<Entity> mappedTargets = OrthologRepository.getInstance(service).getOrthologMapping(mapping).getOrthologs(new Entity(factor));
				 if (mappedTargets == null ) continue;
				 for(Entity mappedTarget: mappedTargets){
					 transferredList2.add(mappedTarget.getIdentifier());
				 }
			 }
			 factorList2 = transferredList2;
		 }
		 
		for(String factor1 :factorList1 ){
			if (factorList2.contains(factor1) ) return true;
		}
		return false;
	}
	public boolean canSkip(HashMap<Option,HashSet<String>> computations, Network sourceNetwork,String sourceNetworkName, Network network, String targetNetworkName ){
		boolean skip = true;
		for (Entry<Option, PairwiseStatGenerator> e : generators.entrySet()) {
	
			if ( !computations.containsKey(e.getKey()) || !computations.get(e.getKey()).contains(targetNetworkName)) {                    //when not yet computed
				if ( e.getValue().getFeatureType().equals(FeatureType.ASYMMETRIC) || sourceNetworkName.compareTo(targetNetworkName) <= 0) {// and metric is asymmetric, or sourceNetworkName precedes targetNetworkName lexicographically
					skip = false;																										  // than can not skip
					break;
				}
			}
		}
		if ( skip){
			CroCoLogger.getLogger().debug(String.format("Skip network %s, because of ASYMMETRIC (and or already computed) metric",network));
			return true;
		}
		
		Set<String> factorList1 = getFactorList(sourceNetwork.getOptionValue(Option.FactorList));
		Set<String> factorList2 = getFactorList(network.getOptionValue(Option.FactorList));
		
		
		if ( factorList1.size() > 0 && factorList2.size() >0){ //check only if factor list as provided 
			CroCoLogger.getLogger().debug(String.format("Check factor list of  %s and %s",sourceNetwork.toString(),network.toString()));
			if ( doOverlap( sourceNetwork.getTaxId(),factorList1,network.getTaxId(),factorList2) == false) {
				CroCoLogger.getLogger().debug(String.format("Skip network %s, because of none-factor overlap",network));
				return true;
			}
			CroCoLogger.getLogger().debug(String.format("Network %s and %s have factor overlap",sourceNetwork.toString(),network.toString()));
		}
		return false;
	}
	
	public void compute(File networkFile, File networkInfo, File outputFile) throws Exception{
		
		HashMap<Option,HashSet<String>> computations = readCurrentOutputFile(repositoryDir,outputFile);
		BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile,true));
			
		NetworkCollector collector = new NetworkCollector(); 
		
		Network sourceNetwork =  NetworkHierachy.getNetwork(networkInfo, networkFile, false);
		String sourceNetworkName =sourceNetwork.getOptionValue(Option.networkFile).replace(repositoryDir.toString(), "") ;
		
		CroCoLogger.getLogger().info(String.format("Source network: %d", sourceNetwork.getSize()));
		
		NetworkHierachy hierachy = new NetworkHierachy();
	
		hierachy.processHierachy(repositoryDir, collector, null);
		List<Network> networks = collector.getNetworks();

		CroCoLogger.getLogger().info("Number of networks: " + networks.size());
		for(int i = 0 ; i< networks.size() ; i+=bufferSize){
			CroCoLogger.getLogger().info(String.format("State: %d of %d",i,networks.size()));
			CroCoLogger.getLogger().debug("Buffer networks");
			List<Network> bufferedNetworks = new ArrayList<Network>();
			for(int j = i ; j <i+bufferSize && j < networks.size();j++){

				Network network = networks.get(j);
				String targetNetworkName = network.getOptionValue(Option.networkFile).replace(repositoryDir.toString(),"").replace("//", "/");

				if ( canSkip( computations, sourceNetwork,sourceNetworkName,network,targetNetworkName)) continue;
				
				File file = new File(networks.get(j).getOptionValue(Option.networkFile).toString());
				Network tmpNetwork = new DirectedNetwork(network);
			
				NetworkHierachy.readNetwork(tmpNetwork,file);
				if (! tmpNetwork.getTaxId().equals(sourceNetwork.getTaxId())){
					tmpNetwork = transfer( sourceNetwork,tmpNetwork);
					tmpNetwork.setNetworkInfo(network.getNetworkInfo());
				}
				if ( tmpNetwork.size() == 0){
					CroCoLogger.getLogger().warn("No edges in network" + tmpNetwork.getOptionValues() + "\t" + file);
				}
				bufferedNetworks.add(tmpNetwork);
				
			}
			CroCoLogger.getLogger().debug("Buffered:" +bufferedNetworks.size() );
			CroCoLogger.getLogger().debug("Compute metric");
			for(Network network : bufferedNetworks){
				String targetNetworkName = network.getOptionValue(Option.networkFile).replace(repositoryDir.toString(),"").replace("//", "/");
				for (Entry<Option, PairwiseStatGenerator> e : generators.entrySet()) {
					if ( !computations.containsKey(e.getKey()) || !computations.get(e.getKey()).contains(targetNetworkName)) {                    //when not yet computed
						if ( e.getValue().getFeatureType().equals(FeatureType.ASYMMETRIC) || sourceNetworkName.compareTo(targetNetworkName) <= 0) {// and metric is asymmetric, or sourceNetworkName precedes targetNetworkName lexicographically
							float sim = e.getValue().compute(sourceNetwork,network);
							bw.write(e.getKey().name() + "\t" + sourceNetworkName + "\t" + targetNetworkName + "\t" + sim+ "\n");
							bw.flush();
						}
					}
				}
			}
			
			bufferedNetworks = null;
		}
		bw.flush();
		bw.close();
		
	}
	


	private Set<String> getFactorList(String factorList) {
		Set<String> ret = new HashSet<String>();
		if ( factorList != null){
			for(String factor : factorList.split("\\s+")){
				ret.add(factor.toUpperCase().trim());
			}
		}
		return ret;
	}

	public static void main(String[] args) throws Exception {
		
		HelpFormatter lvFormater = new HelpFormatter();
		CommandLineParser parser = new BasicParser();
	
		Options options = new Options();

		options.addOption(OptionBuilder.withLongOpt("repositoryDir").withArgName("DIR").withDescription("Location of the croco Repository").isRequired().hasArgs(1).create("repositoryDir"));
		options.addOption(OptionBuilder.withLongOpt("infoFile").withArgName("FILE").withDescription("Valid croco network info file used to compute metrics").isRequired().hasArgs(1).create("infoFile"));
		options.addOption(OptionBuilder.withLongOpt("networkFile").withArgName("FILE").withDescription("Valid croco network network file used to compute metrics").isRequired().hasArgs(1).create("networkFile"));
		options.addOption(OptionBuilder.withLongOpt("output").withArgName("DIR").withDescription("Output dir").isRequired().hasArgs(1).create("output"));
		
		CommandLine line = null;
		try{
			line = parser.parse( options, args );
		}catch(Exception e){
			System.err.println( e.getMessage());
			lvFormater.printHelp(120, "java " + PairwiseFeatures.class.getName(), "", options, "", true);
			System.exit(1);
		}
		File repositoryDir = new File(line.getOptionValue("repositoryDir"));
		File infoFile = new File(line.getOptionValue("infoFile"));
		if (! infoFile.exists()){
			CroCoLogger.getLogger().fatal(String.format("Cannot find infoFile %s",infoFile.getName()));
			return;
		}
		File networkFile = new File(line.getOptionValue("networkFile"));
		if ( !networkFile.exists()){
			CroCoLogger.getLogger().fatal(String.format("Cannot locate network file (%s) for info file %s",networkFile.getName(),infoFile.getName()));
		}
		File output = new File(line.getOptionValue("output"));
		CroCoLogger.getLogger().info(String.format("Repository dir: %s",repositoryDir.toString()));
		CroCoLogger.getLogger().info(String.format("Network info file: %s",infoFile.toString()));
		CroCoLogger.getLogger().info(String.format("Network file: %s",networkFile.toString()));
		CroCoLogger.getLogger().info(String.format("Output file: %s",output.toString()));
		
		
		PairwiseFeatures featureGenerator = new PairwiseFeatures(repositoryDir);
		
		featureGenerator.compute(networkFile,infoFile, output);
		CroCoLogger.getLogger().info("Finished");
	}

}
