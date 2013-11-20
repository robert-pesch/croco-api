package de.lmu.ifi.bio.crco.operation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import de.lmu.ifi.bio.crco.connector.DatabaseConnection;
import de.lmu.ifi.bio.crco.connector.LocalService;
import de.lmu.ifi.bio.crco.connector.QueryService;
import de.lmu.ifi.bio.crco.data.IdentifierType;
import de.lmu.ifi.bio.crco.data.Species;
import de.lmu.ifi.bio.crco.network.Network;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologDatabaseType;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologMappingInformation;
import de.lmu.ifi.bio.crco.operation.ortholog.OrthologRepository;
import de.lmu.ifi.bio.crco.util.CroCoLogger;

public class TransferTest {
	
	@Test
	public void transferFlyHuman() throws Exception{
		QueryService service = new LocalService(CroCoLogger.getLogger(),DatabaseConnection.getConnection());

		Species human = service.getSpecies("Human").get(0);
		Species fly = service.getSpecies("fruit fly").get(0);
		
		List<OrthologMappingInformation> orthologMappingInformatons = service.getOrthologMappingInformation(OrthologDatabaseType.EnsemblCompara, human, fly);
		assertEquals(1,orthologMappingInformatons.size());
		OrthologMappingInformation orthologMappingInformaton = orthologMappingInformatons.get(0);

		Network flyNetwork = service.readNetwork(86,null,false);
		assertTrue(flyNetwork.getSize() > 0);
		System.out.println("Fly network size:\t" + flyNetwork.getSize());
		
		Transfer transfer = new Transfer();
		transfer.setInputNetwork(flyNetwork);
		transfer.setInput(Transfer.OrthologMappingInformation, orthologMappingInformaton);
		transfer.setInput(Transfer.OrthologRepository, OrthologRepository.getInstance(service));
		
		Network transferred = transfer.operate();
		assertTrue(transferred.getSize() > 0);
		System.out.println("Human ensembl network size:\t" + transferred.getSize());

	}
	
	@Test
	public void transferHumanCow() throws Exception{
		QueryService service = new LocalService(CroCoLogger.getLogger(),DatabaseConnection.getConnection());

		Species human = service.getSpecies("Human").get(0);
		Species bovine = service.getSpecies("Cow").get(0);
		List<OrthologMappingInformation> orthologMappingInformatons = service.getOrthologMappingInformation(OrthologDatabaseType.InParanoid, human, bovine);
		assertEquals(1,orthologMappingInformatons.size());
		OrthologMappingInformation orthologMappingInformaton = orthologMappingInformatons.get(0);

		Network humanTestNetwork = service.readNetwork(106,null,false);
		assertTrue(humanTestNetwork.getSize() > 0);
		System.out.println("Human network size:\t" + humanTestNetwork.getSize());
		
		Transfer transfer = new Transfer();
		transfer.setInputNetwork(humanTestNetwork);
		transfer.setInput(Transfer.OrthologMappingInformation, orthologMappingInformaton);
		transfer.setInput(Transfer.OrthologRepository, OrthologRepository.getInstance(service));
		
		Network transferred = transfer.operate();
		assertTrue(transferred.getSize() > 0);
		System.out.println("Cow ensembl network size:\t" + transferred.getSize());

	}
}