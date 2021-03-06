package de.lmu.ifi.bio.croco.connector;

import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;

import de.lmu.ifi.bio.croco.data.BindingEvidence;
import de.lmu.ifi.bio.croco.data.ContextTreeNode;
import de.lmu.ifi.bio.croco.data.CroCoNode;
import de.lmu.ifi.bio.croco.data.Entity;
import de.lmu.ifi.bio.croco.data.NetworkMetaInformation;
import de.lmu.ifi.bio.croco.data.NetworkMetaInformation.Restriction;
import de.lmu.ifi.bio.croco.data.NetworkType;
import de.lmu.ifi.bio.croco.data.Option;
import de.lmu.ifi.bio.croco.data.Species;
import de.lmu.ifi.bio.croco.data.genome.Gene;
import de.lmu.ifi.bio.croco.data.genome.Strand;
import de.lmu.ifi.bio.croco.data.genome.Transcript;
import de.lmu.ifi.bio.croco.intervaltree.peaks.ChIP;
import de.lmu.ifi.bio.croco.intervaltree.peaks.DNaseTFBSPeak;
import de.lmu.ifi.bio.croco.intervaltree.peaks.Peak;
import de.lmu.ifi.bio.croco.intervaltree.peaks.TFBSPeak;
import de.lmu.ifi.bio.croco.network.BindingEnrichedDirectedNetwork;
import de.lmu.ifi.bio.croco.network.DirectedNetwork;
import de.lmu.ifi.bio.croco.network.Network;
import de.lmu.ifi.bio.croco.network.Network.EdgeRepositoryStrategy;
import de.lmu.ifi.bio.croco.operation.ortholog.OrthologDatabaseType;
import de.lmu.ifi.bio.croco.operation.ortholog.OrthologMapping;
import de.lmu.ifi.bio.croco.operation.ortholog.OrthologMappingInformation;
import de.lmu.ifi.bio.croco.util.CroCoLogger;
import de.lmu.ifi.bio.croco.util.CroCoProperties;
import de.lmu.ifi.bio.croco.util.Pair;
import de.lmu.ifi.bio.croco.util.ontology.Obo;

/**
 * Uses a direct database connection to query the database.
 * @author pesch
 *
 */
public class LocalService implements QueryService{
	private Logger logger;
	
	public LocalService( ){
	    this(CroCoLogger.getLogger());
	}
	
	public LocalService(Logger logger ){
		this.logger = logger;
		
	}
	public static void main(String[] args) throws Exception{
	    System.out.println("Init instance");
	    LocalService service = new LocalService();
	    System.out.println("Get ontoloy");
	    CroCoNode<NetworkMetaInformation> node = service.getNetworkOntology(false);
	    System.out.println("Print root info");
	    System.out.println(node.getName());
	}
	private List<NetworkMetaInformation> getMetaInformation(boolean onlyPublic,Integer ... gIds) throws Exception
	{
	    String sql = "SELECT nh.group_id ,  name,  tax_id,network_type,restricted FROM NetworkMetaInformation nh";
	    String groupIdWhere = null;
	    
	    if ( gIds.length > 0)
	    {
	        if ( gIds.length == 1)
	        {
	            groupIdWhere = String.format(" where nh.group_id=%d",gIds[0]);
	        }else{
	            groupIdWhere =" where nh.group_id IN (" +gIds[0] ;

	            for(int i = 1; i< gIds.length;i++)
	            {
	                groupIdWhere +="," + gIds[i] ;
	            }
	            groupIdWhere +=")";
	        }
	        sql+=groupIdWhere;  
	    }
	    
	    if  (onlyPublic)
	    {
	        if( groupIdWhere!=null)
	            sql +=" and restricted=0";
	        else
	            sql +=" where restricted=0";
	    }
	    
	    PreparedStatement stat = DatabaseConnection.getConnection().prepareStatement(sql);
        CroCoLogger.getLogger().debug(sql);

        List<NetworkMetaInformation> networks = new ArrayList<NetworkMetaInformation>();

        stat.execute();

        ResultSet res = stat.getResultSet();
        HashMap<Integer,NetworkMetaInformation> groupIdToNH = new  HashMap<Integer,NetworkMetaInformation>();
        while(res.next()){
            Integer groupId = res.getInt(1);

            String name = res.getString(2);
            Integer taxId = res.getInt(3);

            if ( res.wasNull())  taxId = null;
            NetworkType type = null;
            Integer networkTypeID = res.getInt(4);
            
            boolean restricted= res.getBoolean(5);
            if ( NetworkType.values().length >networkTypeID){
                type = NetworkType.values()[networkTypeID];
            }else{
                CroCoLogger.getLogger().error(String.format("Unknown network type %d",networkTypeID));
            }

            NetworkMetaInformation nhn = new NetworkMetaInformation(groupId,name,taxId,type,restricted?Restriction.WEB:Restriction.NONE);
            networks.add(nhn);
            groupIdToNH.put(groupId, nhn);

        }
        res.close();
        stat.close();


        sql = "SELECT group_id,option_id,value FROM NetworkOption nh";
        if ( groupIdWhere != null)
        {
            sql+= groupIdWhere;
        }
        CroCoLogger.getLogger().debug(sql);
        stat = DatabaseConnection.getConnection().prepareStatement(sql);
        stat.execute();
        res = stat.getResultSet();
        while(res.next())
        {
            Integer groupId = res.getInt(1);
            Option option = Option.values()[res.getInt(2)];
            String value = res.getString(3);
            
            if (groupIdToNH.containsKey(groupId) )
                groupIdToNH.get(groupId).addOption(option, value);
        }
        
        
        logger.debug("Number of networks:\t" + networks.size());
        return networks;
	}
	
	@Override
	public List<NetworkMetaInformation> getNetworkMetaInformations() throws Exception {
	    return getMetaInformation(false);
	}


	@Override
	public NetworkMetaInformation getNetworkMetaInformation(Integer groupId) throws Exception {
	    List<NetworkMetaInformation> ret = getMetaInformation(false,groupId);
	    
	    if ( ret == null || ret.size() == 0)
	        return null;
	    
	    return ret.get(0);
	}

   

	@Override
	public List<OrthologMappingInformation> getOrthologMappingInformation(OrthologDatabaseType database, Species species1, Species species2) throws Exception {
		Statement stat = DatabaseConnection.getConnection().createStatement();
		String sql =String.format(
				"SELECT ortholog_database_id,tax_id_1,t1.name, database_identifier_id_1,tax_id_2,t2.name,database_identifier_id_2 FROM OrthologMappingInformation " +
				"JOIN Taxonomy t1 on t1.tax_id = tax_id_1  and t1.type like 'scientific name'  JOIN Taxonomy t2 on t2.tax_id = tax_id_2 and t2.type like 'scientific name' " 
				);
		StringBuffer where = new StringBuffer("");
		if ( database != null){
			where.append(String.format("WHERE ortholog_database_id=%d",database.ordinal()));
		}
		
		
		if ( species1 != null || species2 != null){
			if ( species1 != null && species2 != null){
				if ( species1.getTaxId() > species2.getTaxId()){
					Species tmp = species1;
					species1 = species2;
					species2 = tmp;
				}
			}
		
		
			if ( where.length() > 0){
				where.append(" AND ");
			}else{
				where.append("WHERE ");
			}
			if ( species1 != null){
				where.append(String.format(" tax_id_1 = %d",species1.getTaxId()));	
			}
			if ( species1 != null && species2 != null){
				where.append(" AND ");
			}else{
				where.append(String.format(" or tax_id_2 = %d ",species1.getTaxId()));
							
			}
			
			if ( species2 != null){
				where.append(String.format(" tax_id_2 = %d",species2.getTaxId()));	
			}
			
		}
		sql += where;
		logger.debug(sql);
		
		stat.execute(sql);
		
		ResultSet res = stat.getResultSet();
		List<OrthologMappingInformation> ret = new ArrayList<OrthologMappingInformation>();
		while(res.next()){
			OrthologDatabaseType orthologDatabase = OrthologDatabaseType.values()[res.getInt(1)];
			String commonName1 = res.getString(3);
			Integer taxId1 = res.getInt(2);
            Species sp1 = Species.taxIdMapping.containsKey(taxId1)?Species.taxIdMapping.get(taxId1):new Species(taxId1,commonName1);

			
			String commonName2 = res.getString(6);
			Integer taxId2 = res.getInt(5);
            
            Species sp2 = Species.taxIdMapping.containsKey(taxId2)?Species.taxIdMapping.get(taxId2):new Species(taxId2,commonName2);
    
			ret.add(new OrthologMappingInformation(orthologDatabase,sp1,sp2));
			
		}
		res.close();
		stat.close();
		
		return ret;
	}
	

	@Override
	public OrthologMapping getOrthologMapping(OrthologMappingInformation orthologMappingInformation) throws Exception{
		
		Statement stat = DatabaseConnection.getConnection().createStatement();
		OrthologMapping mapping = new OrthologMapping(orthologMappingInformation);
		int k = 0;
		String sql = String.format(
				"SELECT db_id_1,db_id_2  FROM Ortholog where tax_id1 = %d and tax_id2 = %d and ortholog_database_id =%d;",
				orthologMappingInformation.getSpecies1().getTaxId(),
				orthologMappingInformation.getSpecies2().getTaxId(),
				orthologMappingInformation.getDatabase().ordinal()
				);

		logger.debug(sql);
		stat = DatabaseConnection.getConnection().createStatement();

		stat.setFetchSize(Integer.MIN_VALUE);
		stat.execute(sql);
		ResultSet res = stat.getResultSet();


		while(res.next()){

			Entity e1 = new Entity(res.getString(1));
			Entity e2 = new Entity(res.getString(2));
			k++;

			mapping.addMapping(e1, e2);

		}
		res.close();
		
		logger.debug("Number of ortholog mappings:\t" +k);
		
		stat.close();
		return mapping;
		
	}
	@Override
	public BindingEnrichedDirectedNetwork readBindingEnrichedNetwork(Integer groupId, Integer contextId,Boolean gloablRepository) throws Exception {
		NetworkMetaInformation networkNode = this.getNetworkMetaInformation(groupId);
		
		if ( !networkNode.getType().equals(NetworkType.ChIP) && !networkNode.getType().equals(NetworkType.TFBS) &&  !networkNode.getType().equals(NetworkType.OpenChrom) ){
			throw new Exception(String.format("Network %d has no binding annotations",groupId));
		}
		String where = null;
		
		if ( contextId != null){
			where = String.format("JOIN GeneContext gc on gc.gene = network.gene1 "
							+ "JOIN GeneContext gc2 on gc2.gene = network.gene2 "
							+ "where gc.context_id = %d and gc2.context_id = %d and group_id = %d" ,
							contextId,contextId,groupId);
		}else{
			where = String.format("where group_id = %d",groupId);
		}
		String sql =String.format(
				"SELECT gene1,gene2,binding_chr,binding_start,binding_end,binding_p_value,binding_motif,open_chrom_start,open_chrom_end FROM Network2Binding network %s",
				where);
		logger.debug(sql);
		Statement stat = DatabaseConnection.getConnection().createStatement();
		stat.execute(sql);
		ResultSet res = stat.getResultSet();
		BindingEnrichedDirectedNetwork network = new BindingEnrichedDirectedNetwork(networkNode,gloablRepository?EdgeRepositoryStrategy.GLOBAL:EdgeRepositoryStrategy.LOCAL);
		while ( res.next()){
			Entity tf = new Entity(res.getString(1));
			Entity tg =new Entity(res.getString(2));
			String bindingChr = res.getString(3);
			Integer bindingStart = res.getInt(4);
			Integer bindingEnd = res.getInt(5);
			Float bindingPValue = res.getFloat(6);
			String motifId = res.getString(7);
			Integer openChromStart = res.getInt(8);
			Integer openChromEnd = res.getInt(9);
			
			Integer toAddGroupId = null;
			TFBSPeak tfbsPeak = new TFBSPeak(bindingChr,bindingStart,bindingEnd,motifId,bindingPValue,null);
			
			if  (! network.containsEdge(tf, tg) ) toAddGroupId = groupId;
			if ( openChromStart != null){
				DNaseTFBSPeak peak = new  DNaseTFBSPeak(tfbsPeak, new Peak(bindingChr,openChromStart,openChromEnd));
				
				network.addEdge(tf, tg, toAddGroupId, peak);
				
			}else{
				network.addEdge(tf, tg, toAddGroupId, tfbsPeak);
			}
			

		}
		
		stat.close();
		logger.debug(String.format("Number of edges:%d",network.size()));
		return network;
		
	}
	@Override
	public Network readNetwork(Integer groupId, Integer contextId, Boolean gloablRepository) throws Exception{
	
		logger.debug("Load:\t" + groupId + " with context " + contextId + " global repo:" + gloablRepository);

		NetworkMetaInformation networkNode = this.getNetworkMetaInformation(groupId);
		Network network = new DirectedNetwork(networkNode.getName(),networkNode.getTaxId(),gloablRepository?EdgeRepositoryStrategy.GLOBAL:EdgeRepositoryStrategy.LOCAL);
		if ( contextId == null){
			Statement stat = DatabaseConnection.getConnection().createStatement();
			stat.execute(String.format("SELECT network_file_location FROM NetworkMetaInformation where group_id = %d",groupId));
			ResultSet res = stat.getResultSet();
			File networkFile = null;
			if ( res.next()){
				networkFile = new File(	CroCoProperties.getInstance().getValue("service.Networks") + "/" + res.getString(1));
			}
			stat.close();
			if ( networkFile.exists()){
				return Network.getNetworkReader().setGroupId(groupId).setNetwork(network).setNetworkFile(networkFile).readNetwork();
			}else{
				CroCoLogger.getLogger().debug(String.format("Network file %s does not exist. Try to read from database",networkFile.toString()));
			}
		}
		
		String condition = null;
		
		if ( contextId != null ){
			condition = String.format("JOIN GeneContext gc on gc.gene = network.gene1 "
						+ "JOIN GeneContext gc2 on gc2.gene = network.gene2 "
						+ "where gc.context_id = %d and gc2.context_id = %d and group_id = %d",
						contextId,contextId,groupId);
		}else{
			condition = String.format("where group_id = %d ", groupId);
		}
		
		Statement stat = DatabaseConnection.getConnection().createStatement();
		

		String sql  = String.format("SELECT gene1,gene2 FROM Network network %s", condition);
	
		logger.debug(sql);
		stat.execute(sql);

		ResultSet res = stat.getResultSet();
		while (res.next()) {
			
			Entity e1 = new Entity(res.getString(1));
			Entity e2 = new Entity(res.getString(2));
			
			network.add(e1, e2, groupId);

		}
		res.close();

		logger.debug(String.format("Number of edges:%d",network.size()));
		stat.close();
		return network;
		
	}
	
	@Override
	public Integer getNumberOfEdges(Integer groupId) throws Exception {
		Statement stat = DatabaseConnection.getConnection().createStatement();
		Integer ret =null;
		stat.execute(String.format("SELECT  count(*) FROM Network where group_id = %d",groupId));
		ResultSet res = stat.getResultSet();
		if ( res.next()){
			ret = res.getInt(1);
		}
		res.close();
		stat.close();
		return ret;
		
	}

	@Override
	public List<OrthologMappingInformation> getTransferTargetSpecies(Integer taxId) throws Exception {
		List<OrthologMappingInformation> ret = new ArrayList<OrthologMappingInformation>();
		Statement stat = DatabaseConnection.getConnection().createStatement();
		stat.execute(
				String.format(
						"SELECT ortholog_database_id,tax_id_1,t1.name, database_identifier_id_1,tax_id_2,t2.name,database_identifier_id_2 FROM OrthologMappingInformation " +
						"JOIN Taxonomy t1 on t1.tax_id = tax_id_1  and t1.type like 'scientific name'  JOIN Taxonomy t2 on t2.tax_id = tax_id_2  and t2.type like 'scientific name' " +
						" where tax_id_1 = %d or tax_id_2 = %d", taxId, taxId
						)
				);
		ResultSet res = stat.getResultSet();
		while(res.next()){
			OrthologDatabaseType orthologDatabase = OrthologDatabaseType.values()[res.getInt(1)];
			Integer taxId1 = res.getInt(2);
			String commonName1 = res.getString(3);
			Species sp1 = new Species(taxId1,commonName1);
			
			Integer taxId2 = res.getInt(5);
			String commonName2 = res.getString(6);
			Species sp2 = new Species(taxId2,commonName2);
			
			OrthologMappingInformation mapping = new OrthologMappingInformation(orthologDatabase,sp1,sp2);
			ret.add(mapping);
			
		}
		res.close();
		
		return ret;
	}


	@Override
	public List<ContextTreeNode> getChildren(ContextTreeNode parent) throws Exception {
		String sql = String.format
				(
						"SELECT childNode.source_id, childNode.source_name, childNode.context_id, childNode.num_children FROM ContextRelation relation " +
								"JOIN ContextHierachyNode parentNode on parentNode.context_id=relation.parent_context_id " +
								"JOIN ContextHierachyNode childNode on childNode.context_id=relation.context_id " +
								"where relation.parent_context_id = '%d'",
								parent.getContextId()
						);

		logger.debug(sql);
		Statement stat = DatabaseConnection.getConnection().createStatement();
		stat.execute(sql);
		ResultSet res = stat.getResultSet();
		ArrayList<ContextTreeNode> children = new ArrayList<ContextTreeNode>();

		while(res.next()){
			String term = res.getString(1);
			String name = res.getString(2);
			Integer contextId = res.getInt(3);
			Integer leafs = res.getInt(4);

			ContextTreeNode node = new  ContextTreeNode(contextId,term,name,leafs);

			children.add(node);


		}
		return children;
	}

	@Override
	public ContextTreeNode getContextTreeNode(String sourceId) throws Exception {
		String sql = String.format(" SELECT context_id,context_type_id,source_id,source_name,num_children FROM ContextHierachyNode where source_id like '%s' ", sourceId);
		logger.debug(sql);
		Statement stat = DatabaseConnection.getConnection().createStatement();
		stat.execute(sql);
		ResultSet res = stat.getResultSet();
		ContextTreeNode ret = null;
		if ( res.next()){
			Integer contextId = res.getInt(1);
			//ContextType type = ContextType.values()[res.getInt(2)];
			//String sourceId = res.getString(2);
			String name = res.getString(4);
			Integer numChildren = res.getInt(5);
			ret = new ContextTreeNode(contextId,sourceId,name,numChildren);
		}
		
		stat.close();
		
		return ret;
	}

	@Override
	public List<ContextTreeNode> getContextTreeNodes(String namenToken) throws Exception {
		String sql = String.format(" SELECT context_id,context_type_id,source_id,source_name,num_children FROM ContextHierachyNode where source_name like '%%%s%%' LIMIT 100 ", namenToken);
		logger.debug(sql);
		Statement stat = DatabaseConnection.getConnection().createStatement();
		stat.execute(sql);
		ResultSet res = stat.getResultSet();
		List<ContextTreeNode> ret = new ArrayList<ContextTreeNode>();
		while ( res.next()){
			Integer contextId = res.getInt(1);
			//ContextType type = ContextType.values()[res.getInt(2)];
			String sourceId = res.getString(3);
			String name = res.getString(4);
			Integer numChildren = res.getInt(5);
			ContextTreeNode node = new ContextTreeNode(contextId,sourceId,name,numChildren);
			ret.add(node);
		}
		
		stat.close();
		
		return ret;
	}

	public Logger getLogger() {
		return logger;
	}

	@Override
	public BufferedImage getRenderedNetwork(Integer groupId) throws Exception {
		File networkFile = null;
		String sql =String.format("SELECT network_file_location from NetworkMetaInformation where group_id=%d",groupId);
		CroCoLogger.getLogger().debug(sql);
		Statement stat = DatabaseConnection.getConnection().createStatement();
		stat.execute(sql);
		ResultSet res = stat.getResultSet();
		if ( res.next()) {
			String f = res.getString(1);
			if (f!= null) networkFile=new File(CroCoProperties.getInstance().getValue("service.Networks") + "/" + f);
		}
		res.close();
		stat.close();
		BufferedImage ret= null;
		if ( networkFile != null){
			File imageFile = new File(networkFile.toString().replace("network.gz","network.png"));
			if (  imageFile.exists()){
				ret =  ImageIO.read(imageFile);
			}
		}
		return ret;
	}

	
	@Override
	public List<Gene> getGenes(Species species,Boolean onlyCoding,ContextTreeNode context) throws Exception {
		
		String sql ="SELECT gene.gene,gene_name,transcript.transcript_id,tss_start,transcript.tss_end,transcript.bio_type,gene.chrom,gene.strand,gene.tax_id From Gene gene "+
				"JOIN Transcript transcript on transcript.gene = gene.gene ";
		if ( context != null){
			sql += String.format("JOIN GeneContext context on context.gene = gene.gene and context_id=%d ",context.getContextId());
		}
		if ( species != null || onlyCoding || context != null ){
			sql+="where";
		}
		if ( species != null)sql+=String.format(" tax_id=%d",species.getTaxId());
		if ( onlyCoding  ){
			if ( species != null) sql +=" and ";
			sql+=String.format(" bio_type like 'protein_coding'");
		}
		
		
		Statement stat = DatabaseConnection.getConnection().createStatement();
		
		CroCoLogger.getLogger().debug(sql.toString());
		stat.execute(sql);
		
		List<Gene> genes= new ArrayList<Gene>();
		Gene gene = null;
		
		ResultSet res = stat.getResultSet();
		
		while(res.next()){
			String geneId = res.getString(1);
			String geneName = res.getString(2);
			String transcriptId = res.getString(3);
			Integer tssStart = res.getInt(4);
			Integer tssEnd = res.getInt(5);
			String bioType = res.getString(6);
			String chrom = res.getString(7);
			Strand strand = null;
			if( res.getString(8).equals("0"))
				strand = Strand.PLUS;
			else
				strand = Strand.MINUS;
			Integer taxID = Integer.valueOf(res.getInt(9));
			if( gene == null || !gene.getIdentifier().equals(geneId)){
				if( gene != null) genes.add(gene);
				gene = new Gene(chrom,geneId,geneName,strand);
				gene.setTaxId(taxID);
			}
			
			Transcript transcript = new Transcript(gene,transcriptId,null,tssStart,tssEnd,bioType);
			gene.addTranscript(transcript);
		}
		
		stat.close();
		if( gene != null) genes.add(gene);
		return genes;
	}

	@Override
	public List<BindingEvidence> getBindings(String factor, String target) throws Exception {
		PreparedStatement stat = null;
		if ( target != null && factor != null){
			stat = DatabaseConnection.getConnection().prepareStatement(
					"SELECT n.group_id,nm.network_type,binding_chr,binding_start , binding_end, binding_p_value, binding_motif, open_chrom_start , open_chrom_end " +
					"FROM Network2Binding n " +
					"JOIN NetworkMetaInformation nm on nm.group_id = n.group_id " +
					"where gene1 = ? and gene2 = ?"
			);
			stat.setString(1, factor);
			stat.setString(2, target);
		}else{
			throw new Exception("Target and factor must not be null");
		}
		Map<Integer,BindingEnrichedDirectedNetwork> groupIdToNetworkSummary = new HashMap<Integer,BindingEnrichedDirectedNetwork>();
		long start = System.currentTimeMillis();
		CroCoLogger.getLogger().debug(stat);
		stat.execute();
		CroCoLogger.getLogger().debug("Time:" + (System.currentTimeMillis()-start)/1000 + " sec.");
		
		HashSet<Integer> groupIds = new HashSet<Integer> ();
		List<Pair<Peak,Integer>> peaks = new ArrayList<Pair<Peak,Integer>>();
		ResultSet res = stat.getResultSet();
		while(res.next()){
			Integer groupId = res.getInt(1);
			
			NetworkType type = NetworkType.values()[res.getInt(2)];
			
			String chr = res.getString(3);
			Entity tf = new Entity(factor);
			Entity tg = new Entity(target);
			
			Integer bindingStart = res.getInt(4);
			Integer bindingEnd = res.getInt(5);

			Float bindingPValue = res.getFloat(6);
			if ( res.wasNull())
			    bindingPValue = null;
			String motifId = res.getString(7);
			if ( res.wasNull())
			    motifId = null;
			
			Peak peak = null;
			
			TFBSPeak tfbsPeak = new TFBSPeak(chr,bindingStart,bindingEnd,motifId,bindingPValue,null);
			
			Integer openChromStart = res.getInt(8);
			if ( res.wasNull())
			    openChromStart = null;
			Integer openChromEnd = res.getInt(9);
			if ( res.wasNull())
			    openChromEnd = null;
			
			if ( type.equals(NetworkType.OpenChrom))
				peak = new  DNaseTFBSPeak(tfbsPeak, new Peak(openChromStart,openChromEnd));
			else if (type.equals(NetworkType.TFBS) )
			    peak = tfbsPeak;
			else if (type.equals(NetworkType.ChIP) )
	            peak = new ChIP(chr,bindingStart,bindingEnd,bindingPValue);
			
			groupIds.add(groupId);
			peaks.add(Pair.create(peak,groupId));
		}
		res.close();
		List<NetworkMetaInformation> metaInformation = this.getMetaInformation(false,groupIds.toArray(new Integer[groupIds.size()]));
		HashMap<Integer,NetworkMetaInformation> groupIdToNetwork = new HashMap<Integer,NetworkMetaInformation>();
		
		for(NetworkMetaInformation nmi: metaInformation){
		    groupIdToNetwork.put(nmi.getGroupId(), nmi);
		}
		
		List<BindingEvidence> ret = new ArrayList<BindingEvidence>();
		
		for(Pair<Peak,Integer> peak : peaks)
		{
		    ret.add(new BindingEvidence(groupIdToNetwork.get(peak.getSecond()),peak.getFirst()));
		}
		
		return ret;
	}
	@Override
	public Long getVersion() {
		return version;
	}

    @Override
    public CroCoNode<NetworkMetaInformation> getNetworkOntology(Boolean onlyPublic) throws Exception{
        
        File oboFile =  new File( CroCoProperties.getInstance().getValue("service.CroCoOntologyFile") );
        File oboMapping =  new File( CroCoProperties.getInstance().getValue("service.CroCoOntoloyMappingFile") );
        
        CroCoLogger.getLogger().debug(String.format("Read obo/mapping: %s/%s", oboFile.toString(),oboMapping.toString()));
        
        if ( !oboFile.exists() || !oboMapping.exists())
            throw new Exception("CroCo ontology not found. Check configuration.");
        
        
        CroCoNode<NetworkMetaInformation> root = Obo.readOntology(oboFile,oboMapping,new HashSet<NetworkMetaInformation>(this.getMetaInformation(onlyPublic)));
        
        return root;
    }



}
