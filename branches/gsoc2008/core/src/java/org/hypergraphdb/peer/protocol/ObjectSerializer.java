package org.hypergraphdb.peer.protocol;

import java.io.InputStream;
import java.io.OutputStream;

import org.hypergraphdb.peer.serializer.DefaultSerializerManager;
import org.hypergraphdb.peer.serializer.HGSerializer;



/**
 * @author Cipri Costa
 *
 * <p>
 * The root of all the serialization/deserialization mechanism.
 * </p>
 */
public class ObjectSerializer
{
	private static final byte[] DATA_SIGNATURE = "DATA".getBytes();
	private static final byte[] END_SIGNATURE = "END".getBytes();
	
	private static SerializerManager serializationManager = new DefaultSerializerManager();
	
	public ObjectSerializer()
	{
	}

	public void serialize(OutputStream out, Object data) 
	{
		HGSerializer serializer = serializationManager.getSerializer(data);

		ProtocolUtils.writeSignature(out, DATA_SIGNATURE);
		serializer.writeData(out, data);
		ProtocolUtils.writeSignature(out, END_SIGNATURE);
	}
	
	public Object deserialize(InputStream in) 
	{
		Object result = null;
		
		if (ProtocolUtils.verifySignature(in, DATA_SIGNATURE))
		{
			result = serializationManager.getSerializer(in).readData(in);

			if (!ProtocolUtils.verifySignature(in, END_SIGNATURE))
			{
				result = null;
			}
		}

		return result;
	}

}
