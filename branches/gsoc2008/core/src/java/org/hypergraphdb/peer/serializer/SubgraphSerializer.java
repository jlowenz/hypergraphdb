package org.hypergraphdb.peer.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.peer.Subgraph;
import org.hypergraphdb.util.Pair;

public class SubgraphSerializer implements SerializerMapper, HGSerializer
{
	private static byte OBJECT_DATA = 0;
	private static byte LINK_DATA = 1;
	private static byte END = 2;	
	

	public HGSerializer accept(Class<?> clazz)
	{
		if (Subgraph.class.isAssignableFrom(clazz)) return this;
		else return null;
	}

	public HGSerializer getSerializer()
	{
		return this;
	}

	public Object readData(InputStream in)
	{
		Subgraph result = new Subgraph();
		
		byte type = 0;
		try
		{
			do 
			{
				type = (byte)in.read();
				if (type != END)
				{
					HGPersistentHandle handle = PersistentHandlerSerializer.deserializePersistentHandle(in);
					int length = SerializationUtils.deserializeInt(in);
					
					if (type == OBJECT_DATA)
					{
						byte[] value = new byte[length];
						in.read(value);
						
						result.addToBuffer(handle, value);
					}else{
						HGPersistentHandle[] link = new HGPersistentHandle[length];
						for(int i=0;i<length;i++)
						{
							link[i] = PersistentHandlerSerializer.deserializePersistentHandle(in);
						}
						result.addToBuffer(handle, link);
						
					}
				}
			}while(type != END);
		}catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return result;
	}

	public void writeData(OutputStream out, Object data)
	{
		Subgraph subgraph = (Subgraph)data;
		Iterator<Pair<HGPersistentHandle, Object>> iter = subgraph.iterator();
		
		while (iter.hasNext())
		{
			Pair<HGPersistentHandle, Object> item = iter.next();
			Object value = item.getSecond();

			try
			{
				byte[] byteValue = null;
				HGPersistentHandle[] link = null;
				
				//write type
				if (value instanceof byte[])
				{
					byteValue = (byte[])value;
					out.write(OBJECT_DATA);
				}else{
					link = (HGPersistentHandle[])value;
					out.write(LINK_DATA);
				}
			
				//write data
				PersistentHandlerSerializer.serializePersistentHandle(out, item.getFirst());
				if (byteValue != null)
				{
					SerializationUtils.serializeInt(out, byteValue.length);
					out.write(byteValue);
				}else{
					SerializationUtils.serializeInt(out, link.length);
					
					//write data 
					for(HGPersistentHandle handle : link)
					{
						PersistentHandlerSerializer.serializePersistentHandle(out, handle);
					}
				}
			} catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		try
		{
			out.write(END);
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
}