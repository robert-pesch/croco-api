package de.lmu.ifi.bio.crco.operation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Random;

import org.junit.Test;

import de.lmu.ifi.bio.crco.connector.BufferedService;
import de.lmu.ifi.bio.crco.connector.DatabaseConnection;
import de.lmu.ifi.bio.crco.connector.LocalService;
import de.lmu.ifi.bio.crco.connector.QueryService;
import de.lmu.ifi.bio.crco.data.Entity;
import de.lmu.ifi.bio.crco.data.exceptions.OperationNotPossibleException;
import de.lmu.ifi.bio.crco.data.exceptions.ParameterNotWellDefinedException;
import de.lmu.ifi.bio.crco.network.DirectedNetwork;
import de.lmu.ifi.bio.crco.network.Network;
import de.lmu.ifi.bio.crco.util.CroCoLogger;

public class ShuffleTest {

	/**
	 * Just one factor -> not shuffle possible
	 * @throws OperationNotPossibleException
	 * @throws ParameterNotWellDefinedException
	 */
	@Test
	public void testShuffleSimple() throws OperationNotPossibleException, ParameterNotWellDefinedException {
		DirectedNetwork network = new DirectedNetwork("Test",null,false);

		network.add(new Entity("A"), new Entity("B"));
		network.add(new Entity("A"), new Entity("C"));
		network.add(new Entity("A"), new Entity("D"));
		network.add(new Entity("A"), new Entity("E"));
			
		Shuffle shuffle = new Shuffle();
		shuffle.setInputNetwork(network);
		Network network2 = shuffle.operate();
		
		System.out.println(getSim(network,network2));
		assertEquals(network.size(),network2.size());
		assertTrue(network.equals(network2));
	
	}

	@Test
	public void testIntegrativ() throws Exception{
		ReadNetwork reader = new ReadNetwork();
		QueryService local = new LocalService(CroCoLogger.getLogger(),DatabaseConnection.getConnection());
		BufferedService service = new BufferedService(local,new File("/tmp"));
		
		reader.setInput(ReadNetwork.NetworkHierachyNode, service.getNetworkHierachyNode(4903));
		reader.setInput(ReadNetwork.QueryService, service);
		reader.setInput(ReadNetwork.GlobalRepository, true);
		Network network = reader.operate();
		Network in = network;
		for(int i = 0 ; i < 100; i++){
			Shuffle shuffleOperation = new Shuffle();
			shuffleOperation.setInput(Shuffle.RandomGenerator, new Random(0));
			shuffleOperation.setInputNetwork(in);
			in = shuffleOperation.operate();
			System.out.println("Sim:");
			System.out.println(getSim(network,in));
		}

	}
	private float getSim(Network network1, Network network2) throws OperationNotPossibleException, ParameterNotWellDefinedException{
		Intersect intersect = new Intersect();
		intersect.setInputNetwork(network1,network2);
		return (float)intersect.operate().size()/(float)network1.getSize();
	}
	
	@Test
	public void testShuffle2() throws OperationNotPossibleException, ParameterNotWellDefinedException {
		DirectedNetwork network = new DirectedNetwork("Test",null,false);

		network.add(new Entity("A"), new Entity("B"));
		network.add(new Entity("A"), new Entity("C"));
		network.add(new Entity("C"), new Entity("D"));
		network.add(new Entity("D"), new Entity("E"));
		
		Shuffle shuffle = new Shuffle();
		shuffle.setInputNetwork(network);
		shuffle.setInput(Shuffle.RandomGenerator, new Random(0));
		Network network2 = shuffle.operate();
	
		System.out.println(getSim(network,network2));
		assertEquals(network.size(),network2.size());
		
		assertFalse(network.equals(network2));
	}
}
