package hgtest.jxta;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.HGQuery.hg;
import org.hypergraphdb.handle.UUIDPersistentHandle;
import org.hypergraphdb.peer.DummyPolicy;
import org.hypergraphdb.peer.HyperGraphPeer;
import org.hypergraphdb.peer.PeerConfiguration;
import org.hypergraphdb.peer.PeerFilterEvaluator;
import org.hypergraphdb.peer.jxta.DefaultPeerFilterEvaluator;
import org.hypergraphdb.peer.jxta.JXTAPeerConfiguration;
import org.hypergraphdb.peer.workflow.QueryTaskClient;


public class HyperGraphDBClient{
	
	public static void main(String[] args) throws NumberFormatException, IOException{
		if (args.length != 2)
		{
			System.out.println("arguments: PeerName PeerGroup");
			System.exit(0);
		}

		String peerName = args[0];
		String groupName = args[1];

		System.out.println("Starting a HGDB client ...");

		JXTAPeerConfiguration jxtaConf = new JXTAPeerConfiguration("");
		jxtaConf.setPeerName(peerName);
		jxtaConf.setPeerGroupName(groupName);
		jxtaConf.setAdvTimeToLive(1*5*1000);//set to 5 minutes

		
		PeerConfiguration conf = new PeerConfiguration(true, "", 
				false, true, "org.hypergraphdb.peer.jxta.JXTAPeerInterface", jxtaConf,
				"./DBs/" + peerName + "CacheDB");
		
		HyperGraphPeer peer = new HyperGraphPeer(conf, new DummyPolicy(false));
		
		peer.start();

		try
		{
			Thread.sleep(3000);
		} catch (InterruptedException e){}
	
		HGPersistentHandle typeHandle = UUIDPersistentHandle.makeHandle("e917bda6-0932-4a66-9aeb-3fc84f04ce57");
		peer.registerType(typeHandle, User.class);
		System.out.println("Types registered...");

		peer.updateNetworkProperties();
		
		System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
		
		//getting users from Server1
		ArrayList<?> result;
		PeerFilterEvaluator evaluator = new DefaultPeerFilterEvaluator("Server1");
		result = peer.query(evaluator, hg.type(User.class), false);
		System.out.println("the client received: " + result);		
		
		for(Object elem:result)
		{
			HGHandle handle = (HGHandle)elem;
			System.out.println(elem + " -> " + peer.query(evaluator, handle));
		}
		
		result = peer.query(new DefaultPeerFilterEvaluator("Server1"), hg.type(User.class), true);
		System.out.println("the client received: " + result);
		
/*		HGHandle handle = null;
		Object retrievedData = null;
	
	    BufferedReader in = new BufferedReader(new InputStreamReader(System.in)) ;

	    System.out.println("user id = 0 ends the client");
	    int userId = 0;
	    do{
	    	System.out.print("User id: ");
		    userId = Integer.parseInt(in.readLine());	
		    
		    if (userId > 0)
		    {
		    	System.out.print("User name: ");
		    	String name = in.readLine();
		    	
		    	handle = peer.add(new User(userId, name));
		    	System.out.println("Client added handle: " + handle);
		    }
	    }while (userId > 0);
*/		

/*		HGHandle handle1 = peer.add("First atom to be sent");
		System.out.println("Client added handle: " + handle1);

		HGHandle handle2 = peer.add("Second atom to be sent");
		System.out.println("Client added handle: " + handle2);
*/
//		retrievedData = null;
//		if (handle1 != null) retrievedData = peer.get(handle1);
//		System.out.println("Client read: " + ((retrievedData == null) ? "null" : retrievedData.toString()));
		
		//retrievedData = null;
		//if (handle2 != null) retrievedData = peer.get(handle2);
		//System.out.println("Client read: " + ((retrievedData == null) ? "null" : retrievedData.toString()));

		/*
		SimpleBean b = new SimpleBean("test");
		
		handle = peer.add(b);

		System.out.println("Client added handle: " + ((handle == null) ? "null" : handle.toString()));

		retrievedData = peer.get(handle);
		
		System.out.println("Client read: " + ((retrievedData == null) ? "null" : retrievedData.toString()));
*/
		/*
		handle = ((HGTypeSystemPeer)peer.getTypeSystem()).getTypeHandle(SimpleBean.class);
		
		System.out.println("Handle for type simple: " + ((handle == null) ? "null" : handle.toString()));
		*/		
	}
}
