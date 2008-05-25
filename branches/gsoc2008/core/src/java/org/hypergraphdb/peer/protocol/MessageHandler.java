package org.hypergraphdb.peer.protocol;

import org.hypergraphdb.peer.HyperGraphPeer;

/**
 * @author Cipri Costa
 *
 * <p>
 * Interface used by messages to execute functions of the <code>HyperGraphPeer</code> class. Since the interface of this class 
 * is relatively stable it is better to do this then use reflection.
 * </p>
 */
public interface MessageHandler {
	public Object handleRequest(HyperGraphPeer hg, Object[] params);
}
