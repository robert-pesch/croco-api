package de.lmu.ifi.bio.crco.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import de.lmu.ifi.bio.crco.data.ContextTreeNode;
import de.lmu.ifi.bio.crco.data.NetworkHierachyNode;
import de.lmu.ifi.bio.crco.data.Species;
import de.lmu.ifi.bio.crco.network.Network;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologMapping;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologMappingInformation;
import de.lmu.ifi.bio.crco.test.IntegrationTest;

@Category(IntegrationTest.class)
public class RemoteWebServiceTest {

	@Test
	public void testGetOrthologMapping() throws Exception{
		RemoteWebService service = new RemoteWebService("http://localhost:8080/croco-web/services/");
		
		List<OrthologMappingInformation> orthologMappings = service.getOrthologMappingInformation(null,Species.Human, Species.Worm);
		OrthologMapping mapping = service.getOrthologMapping(orthologMappings.get(0));
		assertEquals(16957,mapping.getSize());
	}
	
	@Test
	public void testGetOrthologMappingInformation() throws Exception{
		RemoteWebService service = new RemoteWebService("http://localhost:8080/croco-web/services/");
			
		List<OrthologMappingInformation> orthologMappings = service.getOrthologMappingInformation(null,Species.Human, null);
		assertTrue(orthologMappings.size() > 0);
	}
	
	@Test
	public void testGetContext() throws Exception{
		RemoteWebService service = new RemoteWebService("http://localhost:8080/croco-web/services/");
		ContextTreeNode node = service.getContextTreeNode("GO:0008150");
		assertEquals(node.getDescription(),"biological_process");
	}
	
	@Test
	public void testListNetwork() throws Exception {
		RemoteWebService service = new RemoteWebService("http://localhost:8080/croco-web/services/");
		NetworkHierachyNode networks = service.getNetworkHierachy(null);
		System.out.println(networks);
		assertTrue(networks != null);
	}

	@Test
	public void testReadNetwork() throws Exception{
		
		RemoteWebService service = new RemoteWebService("http://localhost:8080/croco-web/services/");
		Network networks = service.readNetwork(8567, null,true);
		System.out.println(networks.getSize());
		assertTrue(networks.getSize() > 0);
	
	}
}
