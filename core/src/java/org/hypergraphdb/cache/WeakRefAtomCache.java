/* 
 * This file is part of the HyperGraphDB source distribution. This is copyrighted 
 * software. For permitted uses, licensing options and redistribution, please see  
 * the LicensingInformation file at the root level of the distribution.  
 * 
 * Copyright (c) 2005-2010 Kobrix Software, Inc.  All rights reserved. 
 */
package org.hypergraphdb.cache;

import java.lang.ref.ReferenceQueue;

import java.util.HashMap;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hypergraphdb.HGAtomAttrib;
import org.hypergraphdb.HGAtomCache;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.HGSystemFlags;
import org.hypergraphdb.HyperGraph;
import org.hypergraphdb.IncidenceSet;
import org.hypergraphdb.handle.DefaultManagedLiveHandle;
import org.hypergraphdb.handle.HGLiveHandle;
import org.hypergraphdb.handle.HGManagedLiveHandle;
import org.hypergraphdb.handle.WeakHandle;
import org.hypergraphdb.handle.WeakManagedHandle;
import org.hypergraphdb.transaction.HGTransactionConfig;
import org.hypergraphdb.transaction.TxCacheMap;
import org.hypergraphdb.transaction.TxMap;
import org.hypergraphdb.transaction.VBox;
import org.hypergraphdb.transaction.VBoxBody;
import org.hypergraphdb.util.CloseMe;
import org.hypergraphdb.util.DummyReadWriteLock;
import org.hypergraphdb.util.WeakIdentityHashMap;

/**
 * 
 * <p>
 * This cache implementation interacts with the Java garbage collector, by using
 * the <code>java.lang.ref</code> facilities, in order to implement its eviction 
 * policy. The eviction policy is the following: an atom is removed from the cache
 * if and only if either (1) its runtime Java instance is garbage collected or (2)
 * it is explicitly removed from the HyperGraph DB. Freezing an atom will keep it in
 * the cache even when it's garbage collected, but not if it's removed from DB altogether.  
 * </p>
 * 
 * <p>
 * For atom caching, two maps are maintained: handle->Java object and Java object->handle. 
 * The latter is an <em>identity map</code>, using the <code>==</code> operator and
 * the <code>System.identityHashCode</code> instead of the regular Object <code>equals</code>
 * and <code>hashCode</code> methods. This is because if an atom changes (e.g. some of its
 * properties are modified), the results of <code>equals</code> and <code>hashCode</code>
 * will most likely change as well and it will become impossible to recover the atom's 
 * handle. Unfortunately, a long standing bug in the JVM has <code>System.identityHashCode</code>
 * highly inefficient.   
 * </p>
 * 
 * @author Borislav Iordanov
 *
 */
public class WeakRefAtomCache implements HGAtomCache 
{	
	private HyperGraph graph = null;
	
	private HGCache<HGPersistentHandle, IncidenceSet> incidenceCache = null; // to be configured by the HyperGraph instance
	
    private CacheMap<HGPersistentHandle, WeakHandle> liveHandles = null; 
    private TxCacheMap<HGPersistentHandle, WeakHandle> liveHandlesTx = null;
    
	private CacheMap<Object, HGLiveHandle> atoms = null;
	private TxCacheMap<Object, HGLiveHandle> atomsTx = null;
	
	private Map<HGLiveHandle, Object> frozenAtoms =	null;
	
	private ColdAtoms coldAtoms = new ColdAtoms();
	
	public static final long DEFAULT_PHANTOM_QUEUE_POLL_INTERVAL = 500;
	
//	private ReadWriteLock lock = new DummyReadWriteLock(); // new ReentrantReadWriteLock();
	private ReadWriteLock gcLock = new ReentrantReadWriteLock();
    private ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();
	private PhantomCleanup cleanupThread = new PhantomCleanup();
	private HGTransactionConfig cleanupTxConfig = new HGTransactionConfig();
	private long phantomQueuePollInterval = DEFAULT_PHANTOM_QUEUE_POLL_INTERVAL;
	private boolean closing = false;
	
	//
	// This handle class is used to read atoms during closing of the cache. Because
	// closing the HyperGraph may involve a lot of cleanup activity where it's necessary
	// to read atoms (mostly types) into main memory just temporarily, we need some
	// way to enact this temporarily. 
	//
	private static class TempLiveHandle extends DefaultManagedLiveHandle
	{
		public TempLiveHandle(Object ref, HGPersistentHandle persistentHandle, byte flags)
		{
			super(ref, persistentHandle, flags, 0, 0);
		}
		
		public void setRef(Object ref)
		{
			this.ref = ref;
		}
	}	
	
	private void processRefQueue() throws InterruptedException
	{
        WeakHandle ref = (WeakHandle)refQueue.remove(phantomQueuePollInterval);
        while (ref != null)
        {
            liveHandles.drop(ref.getPersistentHandle());
            ref.clear();
            synchronized (ref) { ref.notifyAll(); }         
            ref = (WeakHandle)refQueue.poll();            
        }
	}
	
	private void processRefQueueTx() throws InterruptedException
	{
		WeakHandle ref = (WeakHandle)refQueue.remove(phantomQueuePollInterval);
		while (ref != null)
		{
		    final HGPersistentHandle h = ref.getPersistentHandle();
		    gcLock.writeLock().lock();
		    try
		    {
		        TxCacheMap<HGPersistentHandle, WeakHandle>.Box theBox = liveHandlesTx.boxOf(h);
		        if (theBox == null)
		            continue;
		        boolean keep = false;
		        for (VBoxBody<WeakHandle> body = theBox.getBody(); body != null && !keep; body = body.next)
		            if (atomsTx.boxOf(body.value.getRef()) != null)
		                keep = true;
		        if (!keep)
		            liveHandles.drop(h);
		    }
		    finally
		    {
		        gcLock.writeLock().unlock();
	            ref.clear();
	            synchronized (ref) { ref.notifyAll(); }         
	            ref = (WeakHandle)refQueue.poll();		        
		    }		    
		}
	}
	
	private class PhantomCleanup extends Thread 
	{
		private boolean done;
		
	    public void run() 
	    {
//			WeakHandle.returnEnqueued.set(Boolean.TRUE);
	        cleanupTxConfig.setNoStorage(true);
	        for (done = false; !done; ) 
	        {
	        	try 
	            {
	        	    if (graph.getConfig().isTransactional())
	        	        processRefQueueTx();
	        	    else
	        	        processRefQueue();
	            } 
	        	catch (InterruptedException exc) 
	        	{
	                Thread.currentThread().interrupt();
	            }
	        	catch (Throwable t)
	        	{
	        		System.err.println("PhantomCleanup thread caught an unexpected exception, stack trace follows:");
	        		t.printStackTrace(System.err);
	        	}
	        }
//			WeakHandle.returnEnqueued.set(Boolean.FALSE);
	    }

	    public void end()
	    {
	    	this.done = true;
	    }
	}
	
	public WeakRefAtomCache(HyperGraph graph)
	{
	    this.graph = graph;
	    if (graph.getConfig().isTransactional())
	    {
	        atoms = atomsTx = new TxCacheMap<Object, HGLiveHandle>(graph.getTransactionManager(), WeakIdentityHashMap.class);
            liveHandles = liveHandlesTx = new TxCacheMap<HGPersistentHandle, WeakHandle>(graph.getTransactionManager(), null); 	        
	        frozenAtoms = new TxMap<HGLiveHandle, Object>(graph.getTransactionManager(), null);
	    }
	    else
	    {
	        atoms = new HashCacheMap<Object, HGLiveHandle>(); // need a weak hash cache map?
	        liveHandles = new HashCacheMap<HGPersistentHandle, WeakHandle>();
	        frozenAtoms = new HashMap<HGLiveHandle, Object>();
	    }
		cleanupThread.setPriority(Thread.MAX_PRIORITY);
		cleanupThread.setDaemon(true);		
		cleanupThread.start();
	}
	
	public void setIncidenceCache(HGCache<HGPersistentHandle, IncidenceSet> cache)
	{
		this.incidenceCache= cache;
	}
	
	public HGCache<HGPersistentHandle, IncidenceSet> getIncidenceCache()
	{
		return incidenceCache;
	}
	
	public void setHyperGraph(HyperGraph hg) 
	{
		this.graph = hg;
		cleanupThread.setName("HGCACHE Cleanup - " + graph.getLocation());
	}
	
    public HGLiveHandle atomAdded(HGPersistentHandle pHandle, Object atom, final HGAtomAttrib attrib) 
    {
        if (closing)
        {
            HGLiveHandle result = new TempLiveHandle(atom, pHandle, attrib.getFlags());
            atoms.put(atom, result);
            return result;
        }       
        WeakHandle h = null;
        h = liveHandles.get(pHandle);
        if (h != null)
            return h;
        if ((attrib.getFlags() & HGSystemFlags.MANAGED) != 0)
            h = new WeakManagedHandle(atom, 
                                      pHandle, 
                                      attrib.getFlags(), 
                                      refQueue,
                                      attrib.getRetrievalCount(),
                                      attrib.getLastAccessTime());
        else
            h = new WeakHandle(atom, pHandle, attrib.getFlags(), refQueue);
        gcLock.readLock().lock();
        try
        {
            atoms.put(atom, h);
            liveHandles.put(pHandle, h);
        }
        finally
        {
            gcLock.readLock().unlock();
        }
        coldAtoms.add(atom);
        return h;
}
	
	public HGLiveHandle atomRead(HGPersistentHandle pHandle, 
								 Object atom,
								 final HGAtomAttrib attrib) 
	{
		if (closing)
		{
			HGLiveHandle result = new TempLiveHandle(atom, pHandle, attrib.getFlags());
			atoms.put(atom, result);
			return result;
		}		
		WeakHandle h = null;
		h = liveHandles.get(pHandle);
		if (h != null)
			return h;
		if ((attrib.getFlags() & HGSystemFlags.MANAGED) != 0)
		    h = new WeakManagedHandle(atom, 
                                      pHandle, 
                                      attrib.getFlags(), 
                                      refQueue,
                                      attrib.getRetrievalCount(),
                                      attrib.getLastAccessTime());
		else
		    h = new WeakHandle(atom, pHandle, attrib.getFlags(), refQueue);
		
		// Important to updates the atoms map first to prevent garbage collection
		// of the liveHandles entry due to previously removed runtime instance of the
		// same atom.
		gcLock.readLock().lock();
        try
        {
            atoms.load(atom, h);
            liveHandles.load(pHandle, h);
        }
        finally
        {
            gcLock.readLock().unlock();
        }		
		coldAtoms.add(atom);
		return h;
	}

	public void atomRefresh(HGLiveHandle handle, Object atom, boolean replace) 
	{
	    if (handle.getRef() == atom)
	        return; // same atom, nothing to do
		if (closing)
		{
			if (handle instanceof WeakHandle)
				((WeakHandle)handle).clear();
			else
				((TempLiveHandle)handle).setRef(atom);
			return;
		}
	    WeakHandle newLive = null;
	    if (handle instanceof WeakManagedHandle)
	        newLive = new WeakManagedHandle(atom, 
	                                        handle.getPersistentHandle(), 
	                                        handle.getFlags(), 
	                                        refQueue,
	                                        ((WeakManagedHandle)handle).getRetrievalCount(),
	                                        ((WeakManagedHandle)handle).getRetrievalCount());
	    else
	        newLive = new WeakHandle(atom, 
	                                 handle.getPersistentHandle(),
	                                 handle.getFlags(),
	                                 refQueue);
	    
	    // We are updating two maps here. Because the atoms map is weak-ref-based, the
	    // correct order is to update it first for otherwise a previously kicked in
	    // garbage collection of the corresponding 'liveHandles' entry will create a 
	    // race condition
	    
	    Object curr = handle.getRef();
	    // If we have some other Java instance as the atom, we need to force a replace
	    // even if strictly speaking we don't need to (e.g. this happens with type wrappers)
	    // because in case of a roll back the obligatory atoms.remove will be reversed.
	    gcLock.readLock().lock();
	    try
	    {
    	    if (replace || curr != null)
    	    {
    	        atoms.remove(curr);
    	        atoms.put(atom, newLive);
    	        liveHandles.put(handle.getPersistentHandle(), newLive);
    	    }		    
    	    else
    	    {
                atoms.load(atom, newLive);		        
    	        liveHandles.load(handle.getPersistentHandle(), newLive);
    	    }
	    }
	    finally
	    {
	        gcLock.readLock().unlock();	        
	    }
        coldAtoms.add(atom);
	}

	public void close() 
	{	
		closing = true;
		cleanupThread.end();	
		while (cleanupThread.isAlive() )
			try { cleanupThread.join(); } catch (InterruptedException ex) { }
//		WeakHandle.returnEnqueued.set(Boolean.TRUE);
//		try { processRefQueue(); } catch (InterruptedException ex) { }
//		for (Iterator<Map.Entry<HGPersistentHandle, WeakHandle>> i = liveHandles.entrySet().iterator(); 
//			 i.hasNext(); ) 
//		{
//		    WeakHandle h = i.next().getValue();
//			Object x = h.fetchRef();
//			graph.getEventManager().dispatch(graph, new HGAtomEvictEvent(h, x));			
//			if (h.isEnqueued())
//			{
//				h.clear();
//			}
//		}
//		WeakHandle.returnEnqueued.set(Boolean.FALSE);
		frozenAtoms.clear();		
		incidenceCache.clear();
		if (incidenceCache instanceof CloseMe)
			((CloseMe)incidenceCache).close();
		atoms.clear();
		liveHandles.clear();
	}

	public HGLiveHandle get(HGPersistentHandle pHandle) 
	{
	    if (pHandle.toString().equals("6c46f2f2-04a7-11db-aae2-8dc354b70291"))
	    {
	        System.out.println("Looking for ArrayType ");
	    }
	    WeakHandle h = liveHandles.get(pHandle);
		if (h != null)
			h.accessed();
        if (pHandle.toString().equals("6c46f2f2-04a7-11db-aae2-8dc354b70291"))
        {
            System.out.println("found-> " + ((h == null) ? "no" : "yes"));
        }
		
		return h;
	}

	public HGLiveHandle get(Object atom) 
	{
		return atoms.get(atom);
	}

	public void remove(HGLiveHandle handle) 
	{
		atoms.remove(handle.getRef());
		liveHandles.remove(handle.getPersistentHandle());			
	}

	
	public boolean isFrozen(HGLiveHandle handle) 
	{
		synchronized (frozenAtoms)
		{
			return frozenAtoms.containsKey(handle);
		}
	}

	public void freeze(HGLiveHandle handle) 
	{
		Object atom = handle.getRef();
		if (atom != null)
			synchronized (frozenAtoms)
			{
				frozenAtoms.put(handle, atom);
			}
	}	

	public void unfreeze(HGLiveHandle handle) 
	{
		synchronized (frozenAtoms)
		{		
			frozenAtoms.remove(handle);
		}
	}
}
