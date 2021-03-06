package de.lmu.ifi.bio.croco.processor.TFBS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;

import de.lmu.ifi.bio.croco.data.NetworkType;
import de.lmu.ifi.bio.croco.data.Option;
import de.lmu.ifi.bio.croco.data.genome.Transcript;
import de.lmu.ifi.bio.croco.intervaltree.Interval;
import de.lmu.ifi.bio.croco.intervaltree.IntervalTree;

public class Kherapour {
	public static class KherapourPromoter extends Interval{

		public KherapourPromoter(int start, int end) {
			super(start,end);
		}
		String gene;
		String ensemblId;
		String direction;
		String type;
		public KherapourPromoter(Integer start, Integer end, String gene, String ensemblId, String direction, String type) {
			super(start,end);
			this.gene = gene;
			this.ensemblId = ensemblId;
			this.direction = direction;
			this.type = type;
		}
		
	}
	
	public static void main(String[] args) throws Exception{
		CommandLine lvCmd = null;
		HelpFormatter lvFormater = new HelpFormatter();
		CommandLineParser parser = new BasicParser();
		
		Options options = new Options();
		options.addOption(OptionBuilder.withLongOpt("repositoryDir").withDescription("Repository directory").isRequired().hasArgs().create("repositoryDir"));
		options.addOption(OptionBuilder.withLongOpt("compositeName").withDescription("Composite name").isRequired().hasArgs().create("compositeName"));
		options.addOption(OptionBuilder.withLongOpt("name").withDescription("Name").isRequired().hasArgs(1).create("name"));
		options.addOption(OptionBuilder.withLongOpt("promoter").withDescription("Promoter (500 or 1000)").isRequired().hasArgs(1).create("promoter"));
		options.addOption(OptionBuilder.withLongOpt("instanceFile").withArgName("FILE").withDescription("Instances").isRequired().hasArgs(1).create("instanceFile"));
		options.addOption(OptionBuilder.withLongOpt("regionFile").withArgName("FILE").withDescription("Instances").isRequired().hasArgs(1).create("regionFile"));
		options.addOption(OptionBuilder.withLongOpt("mappingFile").withArgName("FILE").withDescription("Mapping file").isRequired().hasArgs(1).create("mappingFile"));
		options.addOption(OptionBuilder.withLongOpt("cutOff").withDescription("Confidence cut-off").isRequired().hasArgs(1).create("cutOff"));
			
		CommandLine line = null;
		try{
			line = parser.parse( options, args );
		}catch(Exception e){
			System.err.println( e.getMessage());
			lvFormater.printHelp(120, "java " + Kherapour.class.getName(), "", options, "", true);
			System.exit(1);
		}
	
		File mappingFile = new File(line.getOptionValue("mappingFile"));
		HashMap<String, String> map = readMapping(mappingFile);
		Double confidenceCutOff = Double.valueOf(line.getOptionValue("cutOff"));
		File regionFile = new File(line.getOptionValue("regionFile"));
		File instanceFile = new File(line.getOptionValue("instanceFile"));

		File repositoryDir = new File(line.getOptionValue("repositoryDir"));
		if (! repositoryDir.isDirectory()){
			System.err.println(repositoryDir + " is not a directory");
			System.exit(1);
		}
		String promoterSetting = line.getOptionValue("promoter");
		String composite = line.getOptionValue("compositeName");
		String name = line.getOptionValue("name");
	
		System.out.println("Region file:\t" + regionFile);
		System.out.println("Instance file:\t" + instanceFile);
		System.out.println("Confidence cut-off:\t" + confidenceCutOff);
		System.out.println("Number of mappings:\t" + map.size());
		System.out.println("Composite name:\t"  +composite );
		System.out.println("Repository dir:\t" + repositoryDir);
		System.out.println("Name:\t" + name);
		
		File ouputBaseFile = new File(repositoryDir + "/" + composite);
		

		//Files.createDirectories(ouputBaseFile.toPath()); only java 1.7
		ouputBaseFile.mkdirs();

		
		HashMap<String,IntervalTree<KherapourPromoter>> interval = new HashMap<String,IntervalTree<KherapourPromoter>>();
		BufferedReader br = new BufferedReader(new FileReader(regionFile));
		String currentLine  = null;
		HashSet<String> notMappedGenes = new HashSet<String>();
		while ( (currentLine=br.readLine())!=null){
			String[] tokens = currentLine.split("\\s+");
			String gene = tokens[0].trim().toUpperCase();
			String ensembl = map.get(gene);
			if ( ensembl == null){
				notMappedGenes.add(gene);
				continue;
				//	br.close();
				//throw new RuntimeException("No mapping for:\t"  + gene);
			}
	
			
			String chr = tokens[1];
			Integer start = Integer.valueOf(tokens[2]);
			Integer end = Integer.valueOf(tokens[3]);
			String direction = tokens[4];
			String type = tokens[5];
			//TODO:fix
			Transcript transcript = new Transcript(null, type);
			
			KherapourPromoter o = new KherapourPromoter(start,end,gene,ensembl,direction,type);
			
			if (! interval.containsKey(chr)){
				interval.put(chr, new IntervalTree<KherapourPromoter>());
			}
		
			interval.get(chr).insert(o);
		}
		br.close();
		System.out.println(notMappedGenes);
		System.out.println("Not mapped genes:" + notMappedGenes.size());
		
		br = new BufferedReader(new FileReader(instanceFile));
		currentLine  = null;
	
		HashMap<String,Set<String>> network = new HashMap<String,Set<String>>();
		HashSet<String> notMapped = new HashSet<String>();
		while ( (currentLine=br.readLine())!=null){
			
			String[] tokens = currentLine.split("\\s+");
			if ( tokens.length < 8) continue;
			String sequence = tokens[0];
			String chr = tokens[1];
			Integer start = Integer.valueOf(tokens[2]);
			Integer end = Integer.valueOf(tokens[3]);
			String direction = tokens[4];
	
			Double confidence = Double.valueOf(tokens[6]);
			String factor = tokens[7].toUpperCase();
			String factorEnsembl =  map.get(factor);
			
			if ( factorEnsembl == null){
				notMapped.add(factor);
				continue;
			}
			
			if ( confidence <confidenceCutOff) continue;
			List<KherapourPromoter> targets = interval.get(chr).searchAll(new Interval(start,end));
			if ( targets.size() == 0 || targets == null){
				continue;
			}
			for(KherapourPromoter target : targets){
				String gene =target.ensemblId;
				if (! network.containsKey(factorEnsembl)){
					network.put(factorEnsembl, new HashSet<String>());
				}
				network.get(factorEnsembl).add(gene);
			}
		
		}
		System.out.println(notMapped);
		System.out.println("Number of not mapped factors:\t" + notMapped.size());

		
		File infoFile = new File(ouputBaseFile + "/" + name + ".info");
		BufferedWriter bwInfo = new BufferedWriter(new FileWriter(infoFile));
		
		bwInfo.write(String.format("%s: %s\n",Option.NetworkName, name ));
		bwInfo.write(String.format("%s: %d\n",Option.TaxId.name(),7227));
		bwInfo.write(String.format("%s: %s\n",Option.EdgeType,"Directed"));
		bwInfo.write(String.format("%s: %s\n",Option.NetworkType.name(), NetworkType.TFBS.name()));
		bwInfo.write(String.format("%s: %s\n",Option.ConfidenceThreshold.name(), confidenceCutOff +""));
		bwInfo.write(String.format("%s: %s\n",Option.Upstream.name(), promoterSetting + ""));
		bwInfo.write(String.format("%s: %s\n",Option.Downstream.name(), promoterSetting +""));
		bwInfo.write(String.format("%s: %s\n",Option.reference.name(), "Kherapour et al., Reliable prediction of regulator targets using 12 Drosophila genomes, Genome Res., 2007"));
	
		Set<String> factors = new HashSet<String>();
		File networkFile = new File(ouputBaseFile + "/" + name + ".network.gz");
		BufferedWriter bwNetwork = new BufferedWriter(new OutputStreamWriter( new GZIPOutputStream(new FileOutputStream(networkFile)) ));
		
		for(Entry<String, Set<String>> e : network.entrySet()){
			for(String target : e.getValue()){
				factors.add(e.getKey());
				bwNetwork.write(e.getKey() + "\t" + target + "\n");
			}
		}	
		StringBuffer factorStr = new StringBuffer();
		for(String factor: factors){
			factorStr.append(factor + " ");
		}
		bwInfo.write(String.format("%s: %s\n",Option.FactorList,factorStr.toString().trim()));
		
		bwNetwork.flush();
		bwNetwork.close();
		
		bwInfo.flush();
		bwInfo.close();
	
	//	System.out.println(network);
	//	System.out.println(k);
	}
	public static HashMap<String,String> readMapping(File file) throws IOException{
		HashMap<String,String> map = new HashMap<String,String>();
		BufferedReader br = new BufferedReader(new FileReader(file));
		String line = null;
		while (( line = br.readLine())!= null){
			
			String[] tokens = line.split("\\s+");
			if ( tokens.length < 2) continue;
			String ensemblID = tokens[0].trim().toUpperCase();
			String mapping = tokens[1].trim().toUpperCase();
			
			if ( ensemblID.trim().length() == 0){
				continue;
			}
			map.put(mapping, ensemblID);
		}
		return map;
	}
}
