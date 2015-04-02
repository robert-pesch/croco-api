package de.lmu.ifi.bio.croco.util.ontology;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import com.google.common.base.Joiner;

import de.lmu.ifi.bio.croco.connector.DatabaseConnection;
import de.lmu.ifi.bio.croco.connector.LocalService;
import de.lmu.ifi.bio.croco.data.CroCoNode;
import de.lmu.ifi.bio.croco.data.CroCoNode.FactorFilter;
import de.lmu.ifi.bio.croco.data.CroCoNode.Filter;
import de.lmu.ifi.bio.croco.data.CroCoNode.GeneralFilter;
import de.lmu.ifi.bio.croco.data.CroCoNode.NameFilter;
import de.lmu.ifi.bio.croco.data.Entity;
import de.lmu.ifi.bio.croco.data.NetworkHierachyNode;
import de.lmu.ifi.bio.croco.data.NetworkType;
import de.lmu.ifi.bio.croco.data.Option;
import de.lmu.ifi.bio.croco.data.Species;
import de.lmu.ifi.bio.croco.operation.ortholog.OrthologDatabaseType;
import de.lmu.ifi.bio.croco.operation.ortholog.OrthologMappingInformation;
import de.lmu.ifi.bio.croco.operation.ortholog.OrthologRepository;
import de.lmu.ifi.bio.croco.util.CroCoLogger;
import de.lmu.ifi.bio.croco.util.CroCoProperties;
import de.lmu.ifi.bio.croco.util.FileUtil;
import de.lmu.ifi.bio.croco.util.Pair;
import de.lmu.ifi.bio.croco.util.ontology.OboReader.OboElement;

public class NetworkOntology {
    private static String KHERAPOUR ="Kherapour et al., Reliable prediction of regulator targets using 12 Drosophila genomes, Genome Res., 2007";
    private static String NEPH ="Neph et al., Circuitry and Dynamics of Human Transcription Factor Regulatory Networks, Cell, 2012";
    private static File taxObo= new File("data/croco-sp.obo");
    private static File brendaOBO = new File("data/BrendaTissue.obo");
    private static File brendaMapping = new File("data/BrendaMapping");
    
    
    private LocalService service;
    
    public NetworkOntology()
    {
        this.service = new LocalService();
       
    }
    
    private void addConfidence(CroCoNode root){
        root.setChildren( new ArrayList<CroCoNode>());
        CroCoNode highConf = new CroCoNode("High confidence",root,new GeneralFilter(Option.ConfidenceThreshold,"1.0E-6"),false,root.getNetworks());
        CroCoNode midConf = new CroCoNode("Mid. confidence",root,new GeneralFilter(Option.ConfidenceThreshold,"1.0E-5"),false,root.getNetworks());
        
        CroCoNode neph = new CroCoNode("Neph et. al",root,new GeneralFilter(Option.reference,NEPH),true,root.getNetworks());
        
        root.getChildren().add(highConf);
        
        root.getChildren().add(midConf);
        root.getChildren().add(neph);
       
    }
   
    List<CroCoNode> categorie = new ArrayList<CroCoNode>();
    
    public static class LeafNode extends CroCoNode
    {
        public LeafNode(String name, NetworkHierachyNode network) {
            super(name);
            super.setNetworks( new HashSet<NetworkHierachyNode>());
            super.getNetworks().add(network);
        }
        
    }
    

    public  void addOntologyNodes(Option option,CroCoNode parent)  throws Exception
    {
        parent.setChildren(new ArrayList<CroCoNode>());
        
        if ( option == Option.AntibodyTargetMapped)
        {
            factoraddOntologyNodes(parent);
            return;
        }
        Statement stat = DatabaseConnection.getConnection().createStatement();
        String sql = null;
        if ( option == Option.TaxId )
        {
            sql = String.format("SELECT distinct(tax_id) FROM NetworkHierachy");
        }else if ( option == Option.NetworkType )
        {
            sql = String.format("SELECT distinct(network_type) FROM NetworkHierachy");
        }else{
            sql = String.format("SELECT distinct(value) from NetworkOption where option_id = %d ORDER BY value",option.ordinal());
        }
        CroCoLogger.getLogger().debug(sql);
        stat.execute(sql);
        ResultSet res = stat.getResultSet();
        while(res.next())
        {
            
            String value = res.getString(1);
            String name = value;
            

            if ( option == Option.TaxId )
            {
               name = Species.getSpecies(Integer.valueOf(value)).getName();
            }
            if ( option == Option.NetworkType )
            {
               name = NetworkType.values()[Integer.valueOf(value)].niceName;
               value =  NetworkType.values()[Integer.valueOf(value)].name();
            }
            
            Filter filter = new GeneralFilter(option,value);
            
            
            CroCoNode node = new CroCoNode(name,parent,filter,true,parent.getNetworks());
            
            parent.getChildren().add(node);
            
         
            if ( option == Option.NetworkType && value.equals(NetworkType.TextMining.name()))
            {
                node.setChildShowRootChildren(false);
                
                CroCoNode directed = new CroCoNode("Directed",node,new GeneralFilter(Option.EdgeType,"Directed"),false,node.getNetworks());
                CroCoNode undirected = new CroCoNode("Undirected",node,new GeneralFilter(Option.EdgeType,"Undirected"),false,node.getNetworks());
                
                node.setChildren(new ArrayList<CroCoNode>());
                node.getChildren().add(directed);
                node.getChildren().add(undirected);
                
                
                for(CroCoNode d : new CroCoNode[]{directed,undirected})
                {
                    d.setChildren( new ArrayList<CroCoNode>());
                    CroCoNode context = new CroCoNode("Species filtered",d,new GeneralFilter(Option.TextMiningSpeciesContext,"Yes"),true,d.getNetworks());
                    CroCoNode nocontext = new CroCoNode("Species unfiltered",d,new GeneralFilter(Option.TextMiningSpeciesContext,"No"),true,d.getNetworks());
                    
                    d.getChildren().add(context);
                    d.getChildren().add(nocontext);
                    
                }
            }
            
            if ( option == Option.NetworkType && value.equals(NetworkType.Database.name()))
            {
                node.setChildShowRootChildren(false);
                
                CroCoNode transfac = new CroCoNode("Transfac",node,new NameFilter("Transfac9.3"),true,node.getNetworks());
                CroCoNode redfly = new CroCoNode("REDfly",node,new NameFilter("REDfly3.2"),true,node.getNetworks());
                CroCoNode orgenanno = new CroCoNode("ORegAnno",node,new NameFilter("ORegAnno"),true,node.getNetworks());
                CroCoNode ncipathway = new CroCoNode("NCI-Pathway",node,new NameFilter("NCI-Pathway"),true,node.getNetworks());
                CroCoNode biocarta = new CroCoNode("Biocarta",node,new NameFilter("Biocarta"),true,node.getNetworks());
                
                node.setChildren( new ArrayList<CroCoNode>());
                node.getChildren().add(transfac);
                node.getChildren().add(redfly);
                node.getChildren().add(orgenanno);
                node.getChildren().add(ncipathway);
                node.getChildren().add(biocarta);
                
                
                
            }
            if ( option == Option.NetworkType && value.equals(NetworkType.TFBS.name()))
            {
                node.setChildShowRootChildren(false);
                addConfidence(node);
                
                for(CroCoNode child : node.getChildren())
                {
                    addMotifSet(child);
                    
                }   
                
                CroCoNode kherapour = new CroCoNode("Kherapour et al.",node,new GeneralFilter(Option.reference,KHERAPOUR),false,node.getNetworks());
                CroCoNode kherapour_500 = new CroCoNode("Promoter 500.",kherapour,new GeneralFilter(Option.Upstream,"500"),false,kherapour.getNetworks());
                CroCoNode kherapour_2000 = new CroCoNode("Promoter 2000.",kherapour,new GeneralFilter(Option.Downstream,"2000"),false,kherapour.getNetworks());
                
                
                kherapour.setChildren( new ArrayList<CroCoNode>());
                kherapour.getChildren().add(kherapour_500);
                kherapour.getChildren().add(kherapour_2000);
                
                kherapour_500.setChildren( new ArrayList<CroCoNode>());
                kherapour_2000.setChildren ( new ArrayList<CroCoNode>());
                
                for(int i = 0 ; i<= 10 ; i++)
                {
                    float c = (float)i/(float)10;
                    CroCoNode conf_1 = new CroCoNode(String.format("Kherapour conf. %.2f",c),kherapour_500,new GeneralFilter(Option.ConfidenceThreshold,String.format("%.1f",c)),true,kherapour_500.getNetworks());
                    CroCoNode conf_2 = new CroCoNode(String.format("Kherapour conf. %.2f",c),kherapour_2000,new GeneralFilter(Option.ConfidenceThreshold,String.format("%.1f",c)),true,kherapour_2000.getNetworks());
                    
                    kherapour_500.getChildren().add(conf_1);
                    kherapour_2000.getChildren().add(conf_2);
                    
                    //CroCoNode conf_2 = new CroCoNode(String.format("Kherapour conf. %.2f",c),kherapour_2000,new GeneralFilter(Option.reference,KHERAPOUR),false,node.networks);
                    
                         
                }
                
                node.getChildren().add(kherapour);
            }
            
            if ( option == Option.NetworkType && value.equals(NetworkType.OpenChrom.name()))
            {
                node.setChildShowRootChildren(false);
                
                CroCoNode dgf = new CroCoNode("Digital Genomic Footprinting (DGF)",node,new GeneralFilter(Option.OpenChromType,"DGF"),false,node.getNetworks());
                CroCoNode dnase = new CroCoNode("DNase I hypersensitive sites (DNase)",node,new GeneralFilter(Option.OpenChromType,"DNase"),false,node.getNetworks());
                CroCoNode fair = new CroCoNode("Formaldehyde-Assisted Isolation of Regulatory Elements (FAIRE)",node,new GeneralFilter(Option.OpenChromType,"Faire"),false,node.getNetworks());
                node.setChildren ( new ArrayList<CroCoNode>());
                node.getChildren().add(dgf);
                node.getChildren().add(dnase);
                node.getChildren().add(fair);
                
                for(CroCoNode openChromNode : new CroCoNode[]{dgf,dnase,fair})
                {
                    addConfidence(openChromNode);
                    
                    for(CroCoNode child : openChromNode.getChildren())
                    {
                        addMotifSet(child);
                        
                    }
                }
                
                
                //CroCoNode neph = new CroCoNode("Neph et. al",node,new GeneralFilter(Option.reference,"Neph at el., Circuitry and Dynamics of Human Transcription Factor Regulatory Networks, Cell, 2012"),false,node.networks);
                
            }
            
        }
        
        
        stat.close();
    
    }
    private void addMotifSet (CroCoNode root)
    {
        root.setChildren (new ArrayList<CroCoNode>());
        
        for(String motifSetName : getMotifSet(root.getNetworks()))
        {
            CroCoNode node = new CroCoNode(motifSetName,root,new GeneralFilter(Option.MotifSet,motifSetName),true,root.getNetworks());
            root.getChildren().add(node);
        }
        
    }
    private List<String> getMotifSet(Set<NetworkHierachyNode> networks)
    {
        Set<String> ret = new HashSet<String>();
        
        for(NetworkHierachyNode network : networks)
        {
            if ( network.getOptions().containsKey(Option.MotifSet)){
                ret.add(network.getOptions().get(Option.MotifSet));
            }
        }
        List<String> sorted = new ArrayList<String>(ret);
        Collections.sort(sorted);
        return sorted;
    }
    public Set<String> getFactors(Set<NetworkHierachyNode> nodes, NetworkType ... types)
    {
        Set<String> factors = new HashSet<String>();
        for(NetworkHierachyNode node : nodes)
        {
            for(NetworkType type : types)
            {
                if ( node.getOptions().get(Option.NetworkType).equals(type.name()))
                {
                    factors.addAll(node.getFactors());
                }    
            }
            
        }
        return factors;
    }
    public void persistNetworkOntology(CroCoNode root) throws Exception
    {
        PreparedStatement stat = DatabaseConnection.getConnection().prepareStatement("INSERT INTO NetworkOntology(node_id,parent_node_id,name,network_ids) values(?,?,?,?)");

        Stack<CroCoNode> stack = new Stack<CroCoNode>();
        stack.add(root);
        int id = 0;
        HashMap<CroCoNode,Integer> nodeToId = new HashMap<CroCoNode,Integer>();
        
        while(!stack.isEmpty())
        {
            CroCoNode top = stack.pop();
            if ( top.getNetworks().size() == 0) continue;
            Integer topId = nodeToId.containsKey(top)?nodeToId.get(top):id++;
            
            Integer parentId = null;
            
            if ( top.getParent() == null)
            {
                parentId = -1;
            }else
            {
                parentId = nodeToId.get(top.getParent());
            }
            nodeToId.put(top,topId);
            List<Integer> ids = new ArrayList<Integer>();
            for(NetworkHierachyNode nh : top.getNetworks())
            {
                ids.add(nh.getGroupId());
            }
            
            stat.setInt(1, topId);
            stat.setInt(2,parentId);
            
            stat.setString(3, top.getName());
            stat.setString(4,Joiner.on(" ").join(ids));

            stat.addBatch();
            
            if ( top.getChildren() != null)
            {
                for(CroCoNode child : top.getChildren())
                {
                    stack.add(child);
                }
            }
        }
        stat.executeBatch();
    }

   
    private void factoraddOntologyNodes(CroCoNode parent) throws Exception
    {
        Statement stat = DatabaseConnection.getConnection().createStatement();
        
        String sql ="SELECT distinct(gene.gene_name) as name,value,tax_id  FROM NetworkOption op JOIN Gene gene on gene.gene = op.value where option_id = 15 ORDER BY name";
        
        CroCoLogger.getLogger().debug(sql);
        stat.execute(sql);
        ResultSet res = stat.getResultSet();
        HashMap<String,List<Pair<String,Integer>>> names = new HashMap<String,List<Pair<String,Integer>>>();
       
        OrthologRepository orepo = OrthologRepository.getInstance(service);
        
        while(res.next())
        {
            String geneName = res.getString(1);
            String geneId = res.getString(2);
            Integer taxId = res.getInt(3);
            
            if ( !names.containsKey(geneName))
            {
                names.put(geneName, new ArrayList<Pair<String,Integer>>());
            }
            names.get(geneName).add(new Pair<String,Integer>(geneId,taxId));
        }
        for(Entry<String, List<Pair<String, Integer>>> geneName : names.entrySet())
        {
            Set<String> genesOfInterest = new HashSet<String>();
            for(Pair<String, Integer> v : geneName.getValue())
            {
                genesOfInterest.add(v.getFirst().toUpperCase());
                
                for(OrthologMappingInformation omep : orepo.getOrthologMappingInformation())
                {
                    if ( omep.getSpecies1().getTaxId().equals(v.getSecond()) || omep.getSpecies2().getTaxId().equals(v.getSecond()) )
                    {
                        Set<Entity> mappings = orepo.getOrthologMapping(omep).getOrthologs(new Entity(v.getFirst()));
                        
                        if(  mappings == null) continue;
                        
                        for(Entity e : mappings)
                            genesOfInterest.add(e.getIdentifier());
                    }
                }
            }
            
            CroCoNode factorNode = new CroCoNode(geneName.getKey(),parent,new FactorFilter(genesOfInterest),true,parent.getNetworks());
            parent.getChildren().add(factorNode);
        }
    }
    public void readOntology()
    {
        
    }
    public CroCoNode createNetworkOntology() throws Exception
    {
        
        List<Species> sp = new ArrayList<Species>(Species.knownSpecies);
        List<OrthologMappingInformation> mappings = new ArrayList<OrthologMappingInformation>();
        for(int  i = 0 ; i< sp.size(); i++)
        {
            for(int j = i+1; j< sp.size(); j++)
            {
                mappings.addAll(service.getOrthologMappingInformation(OrthologDatabaseType.EnsemblCompara,sp.get(i),sp.get(j)));
                
            }
        }
        
        for(OrthologMappingInformation mapping : mappings)
        {
            CroCoLogger.debug("Load ortholog mapping: %s", mapping);
            OrthologRepository.getInstance(service).getOrthologMapping(mapping);
        }
        
        List<NetworkHierachyNode> networks = service.getNetworkHierachy();
        readFactors(networks);
        
        CroCoNode root = new CroCoNode("Root",null,true,new HashSet<NetworkHierachyNode>(networks));
      
        CroCoNode cellLine = new CroCoNode("Tissue/Cell-line",root, new GeneralFilter(Option.cellLine),false,root.getNetworks());
        CroCoNode cellLine_flat = new CroCoNode("All tissue/cell-lines",cellLine,false,cellLine.getNetworks());
        cellLine.setChildren(new ArrayList<CroCoNode>());
        cellLine.getChildren().add(cellLine_flat);
        addOntologyNodes(Option.cellLine,cellLine_flat);
        addBrenda(cellLine);
        
        CroCoNode specie = new CroCoNode("Species",root, new GeneralFilter(Option.TaxId),false,root.getNetworks());
        CroCoNode specie_flat = new CroCoNode("All species",specie,false,specie.getNetworks());
        specie.setChildren(new ArrayList<CroCoNode>());
        specie.getChildren().add(specie_flat);
        addOntologyNodes(Option.TaxId,specie_flat);
        addSpeciesObo(specie,taxObo,"NCBITaxon:33213");
        
        
        CroCoNode compendium = new CroCoNode("Compendium",root, new GeneralFilter(Option.Compendium),false,root.getNetworks());
        addOntologyNodes(Option.Compendium,compendium);
       
      
        CroCoNode factor = new CroCoNode("ENCODE (ChIP)-Factor",root, false,root.getNetworks());
        addOntologyNodes(Option.AntibodyTargetMapped,factor);
        CroCoNode allFactors = new CroCoNode("All factors",factor,true,factor.getNetworks());
        factor.getChildren().add(allFactors);
        
        
        CroCoNode technique = new CroCoNode("Experimental technique",root, new GeneralFilter(Option.NetworkType),false,root.getNetworks());
        addOntologyNodes(Option.NetworkType,technique);
    
        CroCoNode devstage = new CroCoNode("Development stage",root, new GeneralFilter(Option.developmentStage),false,root.getNetworks());
        addOntologyNodes(Option.developmentStage,devstage);
    
        CroCoNode treatment = new CroCoNode("Treatment",root, new GeneralFilter(Option.treatment),false,root.getNetworks());
        addOntologyNodes(Option.treatment,treatment);
    
        root.setChildren (new ArrayList<CroCoNode>());
        
        root.getChildren().add(compendium);
        root.getChildren().add(cellLine);
        root.getChildren().add(factor);
        root.getChildren().add(specie);
        root.getChildren().add(technique);
        root.getChildren().add(devstage);
        root.getChildren().add(treatment); 
        
        return root;
    }
    private void addBrenda(CroCoNode root) throws Exception
    {
        String oboRootElement = "BTO:0000000";
        
        OboReader reader = new OboReader(brendaOBO);
        
        OboElement rootElement = reader.getElement(oboRootElement);
        
        CroCoNode brenda = new CroCoNode("Tissues",root, false,root.getNetworks());
        if ( root.getChildren() == null)
            root.setChildren(new ArrayList<CroCoNode>());
        
        root.getChildren().add(brenda);
        
        HashMap<String, String> mapping = FileUtil.mappingFileReader(0, 1, brendaMapping).readMappingFile();
        
        HashSet<String> notFound = new HashSet<String>();

        HashMap<OboElement,Set<NetworkHierachyNode>> elementsToNetwork  =new HashMap<OboElement,Set<NetworkHierachyNode>>();
        
        for(NetworkHierachyNode nh : root.getNetworks())
        {
            String cellLine = nh.getOptions().get(Option.cellLine) ;
            if ( cellLine == null)
                continue;
            String map = mapping.get(cellLine);
            if ( map == null){
                notFound.add(cellLine);
                continue;
            }

            map = map.replace("(non-specific)", "").trim();
            OboElement element = reader.getElement(map);
            if (! elementsToNetwork.containsKey(element))
            {
                elementsToNetwork.put(element, new HashSet<NetworkHierachyNode>());
            }
            elementsToNetwork.get(element).add(nh);
        }
          
        CroCoLogger.getLogger().warn("Not mapped:" + notFound);
       
        
        LinkedList<CroCoNode> par = new LinkedList<CroCoNode>();
        LinkedList<OboElement> list = new LinkedList<OboElement>();
        
        list.add(rootElement);
        par.add(brenda);

        HashSet<OboElement> proc = new HashSet<OboElement>();
        while(!list.isEmpty())
        {
            OboElement top = list.removeFirst();
            CroCoNode nodeParent = par.removeFirst();
            boolean canBeProc = true;
            for(OboElement parent : top.parents)
            {
                if (! proc.contains(parent))
                    canBeProc= false;
            }
            
            if ( !top.id.equals(oboRootElement) && !canBeProc)
            {
                list.add(top);
                par.add(nodeParent);
                continue;
            }
           
            List<OboElement> allChildren = top.getAllChildren();
            Set<NetworkHierachyNode> networks = new HashSet<NetworkHierachyNode>();
            
            for(OboElement child : allChildren)
            {
                if (! elementsToNetwork.containsKey(child))
                    continue;
                networks.addAll(elementsToNetwork.get(child));
            }
            if ( networks.size() == 0)
                continue;
            
            String name = top.name;
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
            
            CroCoNode node = new CroCoNode(name,nodeParent,top.children.size()==0,networks);
            
            
            if ( nodeParent.getChildren() == null)
                nodeParent.setChildren (new ArrayList<CroCoNode>());
            
            nodeParent.getChildren().add(node);
            proc.add(top);
            
            for(OboElement child : top.children)
            {
                par.add(node);
                list.add(child);
            }
            
        }
        makeSlim(brenda);
        
    }
    private void addSpeciesObo(CroCoNode root, File obo, String oboRootElement) throws Exception{
        OboReader reader = new OboReader(obo);
        
        OboElement rootElement = reader.getElement(oboRootElement);
        
        LinkedList<CroCoNode> par = new LinkedList<CroCoNode>();
        LinkedList<OboElement> list = new LinkedList<OboElement>();
        
        
        list.add(rootElement);
        par.add(root);

        HashMap<OboElement,Set<NetworkHierachyNode>> elementsToNetwork  =new HashMap<OboElement,Set<NetworkHierachyNode>>();
        
        for(OboElement element : reader.elements.values() )
        {
            GeneralFilter filter = new GeneralFilter(Option.TaxId,element.id.replaceAll("NCBITaxon:", ""));
            Set<NetworkHierachyNode> networks = new HashSet<NetworkHierachyNode>();
            
            for(NetworkHierachyNode nh : root.getNetworks())
            {
                if ( filter.accept(nh))
                    networks.add(nh);  
            }
            elementsToNetwork.put(element, networks);
        }
        
        HashSet<OboElement> proc = new HashSet<OboElement>();
        while(!list.isEmpty())
        {
            OboElement top = list.removeFirst();
            CroCoNode nodeParent = par.removeFirst();
            boolean canBeProc = true;
            for(OboElement parent : top.parents)
            {
                if (! proc.contains(parent))
                    canBeProc= false;
            }
            
            if ( !top.id.equals(oboRootElement) && !canBeProc)
            {
                list.add(top);
                par.add(nodeParent);
                continue;
            }
           
            List<OboElement> allChildren = top.getAllChildren();
            Set<NetworkHierachyNode> networks = new HashSet<NetworkHierachyNode>();
            
            for(OboElement child : allChildren)
            {
                networks.addAll(elementsToNetwork.get(child));
            }
            CroCoNode node = new CroCoNode(top.name,nodeParent,top.children.size()==0,networks);
            
            if ( nodeParent.getChildren() == null)
                nodeParent.setChildren (new ArrayList<CroCoNode>());
            
            nodeParent.getChildren().add(node);
            proc.add(top);
            
            for(OboElement child : top.children)
            {
                par.add(node);
                list.add(child);
            }
            
        }
        
    }
    
    public void makeSlim(CroCoNode node)
    {
        Stack<CroCoNode> stack = new Stack<CroCoNode>();
        stack.add(node);
        
        while(!stack.isEmpty())
        {
            CroCoNode top = stack.pop();
            
            if ( top.getChildren() == null)
                continue;
                
            if ( top.getChildren().size() == 1 && top != node )
            {
                CroCoNode parent = top.getParent();
                CroCoNode child = top.getChildren().get(0);
                
                parent.getChildren().remove(top);
                parent.getChildren().add(child);
                child.setParent(parent);
                
            }
            
           
            for(CroCoNode child : top.getChildren())
            {
                stack.add(child);
            }
        }
    }
    
    private static String FACTOR_FILE="factors.gz";
    
    private void readFactors(List<NetworkHierachyNode> nodes) throws Exception
    {
        HashMap<Integer,NetworkHierachyNode> groupIdToNetwork = new HashMap<Integer,NetworkHierachyNode>();
        for(NetworkHierachyNode nh : nodes ) 
        {
            groupIdToNetwork.put(nh.getGroupId(), nh);
        }
        
        File file = new File(String.format("%s/%s",CroCoProperties.getInstance().getValue("service.Networks"),FACTOR_FILE));
        CroCoLogger.debug("Read: %s", file);
        if ( file.exists())
        {
            Iterator<String> it = FileUtil.getLineIterator(file);
         
            while(it.hasNext())
            {
                String[] tokens = it.next().split("\t");
                Integer groupId = Integer.valueOf(tokens[0]);
                groupIdToNetwork.get(groupId).addOption(Option.FactorList, tokens[1]);
            }
        }
    }
    
    public static void main(String[] args) throws Exception
    {
        NetworkOntology onto = new NetworkOntology();
        
        CroCoNode root = onto.createNetworkOntology();
        onto.persistNetworkOntology(root);
    }
}