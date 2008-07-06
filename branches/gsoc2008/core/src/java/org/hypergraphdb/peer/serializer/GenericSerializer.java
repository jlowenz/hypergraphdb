package org.hypergraphdb.peer.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.HyperGraph;
import org.hypergraphdb.peer.Subgraph;
import org.hypergraphdb.peer.SubgraphManager;

/**
 * @author ciprian.costa
 * Class that serializes objects based on their HGDB representation
 */
public class GenericSerializer implements HGSerializer
{
	private static HyperGraph tempDB;
	private SubgraphSerializer serializer = new SubgraphSerializer();
	
	
	public static HyperGraph getTempDB()
	{
		return tempDB;
	}

	public static void setTempDB(HyperGraph tempDB)
	{
		GenericSerializer.tempDB = tempDB;
	}

	public Object readData(InputStream in)
	{
		Subgraph result = null;
		int getSubgraphContent = 0;
		try
		{
			getSubgraphContent = in.read();
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (getSubgraphContent == 0)
		{
			result = (Subgraph) serializer.readData(in);
			return result;
		}else
		{
			result = (Subgraph) serializer.readData(in);
	
			HGHandle handle = SubgraphManager.store(result, tempDB.getStore());
		
			return tempDB.get(handle);
		}
	}

	public void writeData(OutputStream out, Object data)
	{
		Subgraph subGraph;
		HGPersistentHandle tempHandle = null;
		
		if (data instanceof Subgraph) 
		{
			try
			{
				out.write(0);
			} catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			subGraph = (Subgraph)data;
		}else{
			try
			{
				out.write(1);
			} catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			tempHandle = tempDB.getPersistentHandle(tempDB.add(data));
			
			subGraph = new Subgraph(tempDB, tempHandle);
		}
		
		serializer.writeData(out, subGraph);
		if (tempHandle != null)
		{
			tempDB.remove(tempHandle);
		}
	}
	

	
}
