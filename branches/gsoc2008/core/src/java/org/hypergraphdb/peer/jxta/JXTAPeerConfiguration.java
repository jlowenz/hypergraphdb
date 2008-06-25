package org.hypergraphdb.peer.jxta;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Cipri Costa
 *
 * Configuration parameters to be sent to the peer interface.
 */
public class JXTAPeerConfiguration {

	private boolean needsRdvConn;
	
	private String peerName;
	private String peerId;
	private String peerGroupName;

	private String messageFactory;
	private HashMap<String, Object> messageFactoryParams;

	private ArrayList<String> peers = new ArrayList<String>();
	
	public JXTAPeerConfiguration()
	{
		needsRdvConn = true;
	}
	public JXTAPeerConfiguration(String peerId){
		this.peerId = peerId;
	}

	public String getPeerId() {
		return peerId;
	}

	public void setPeerId(String peerId) {
		this.peerId = peerId;
	}

	public ArrayList<String> getPeers() {
		return peers;
	}

	public void setPeers(ArrayList<String> peers) {
		this.peers = peers;
	}
	
	public void addPeer(String peerId){
		peers.add(peerId);
	}

	public String getPeerName()
	{
		return peerName;
	}

	public void setPeerName(String peerName)
	{
		this.peerName = peerName;
	}

	public String getPeerGroupName()
	{
		return peerGroupName;
	}

	public void setPeerGroupName(String peerGroupName)
	{
		this.peerGroupName = peerGroupName;
	}

	public boolean getNeedsRdvConn()
	{
		return needsRdvConn;
	}

	public void setNeedsRdvConn(boolean needsRdvConn)
	{
		this.needsRdvConn = needsRdvConn;
	}
	public String getMessageFactory()
	{
		return messageFactory;
	}
	public void setMessageFactory(String messageFactory)
	{
		this.messageFactory = messageFactory;
	}
	public HashMap<String, Object> getMessageFactoryParams()
	{
		return messageFactoryParams;
	}
	public void setMessageFactoryParams(HashMap<String, Object> messageFactoryParams)
	{
		this.messageFactoryParams = messageFactoryParams;
	}
}
