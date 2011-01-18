package org.hypergraphdb.handle;

import java.util.concurrent.atomic.AtomicInteger;

import org.hypergraphdb.HGHandleFactory;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.storage.BAUtils;

/**
 * 
 * <p>
 * Produces integer valued persistent handles. This leads to more compact storage
 * and an overall performance improvement due to less read/write time as well as
 * less time spent comparing and sorting handles. As with other handle factories 
 * based on a local seed, handles generated by this factory will not be universal 
 * and will certainly result in conflicts in a distributed environment. 
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
public class IntHandleFactory implements HGHandleFactory
{
    private static final IntPersistentHandle any = new IntPersistentHandle(1);
    private static final IntPersistentHandle nil = new IntPersistentHandle(0);
    
    private AtomicInteger next = new AtomicInteger(1000);
     
    public int getNext()
    {
        return next.get();
    }

    public void setNext(int next)
    {
        this.next.set(next);
    }

    public HGPersistentHandle anyHandle()
    {
        return any;
    }

    public HGPersistentHandle makeHandle()
    {
        return new IntPersistentHandle(next.getAndIncrement());
    }

    public HGPersistentHandle makeHandle(String handleAsString)
    {
        return new IntPersistentHandle(Integer.parseInt(handleAsString));
    }

    public HGPersistentHandle makeHandle(byte[] buffer)
    {
        return new IntPersistentHandle(BAUtils.readInt(buffer, 0));
    }

    public HGPersistentHandle makeHandle(byte[] buffer, int offset)
    {
        return new IntPersistentHandle(BAUtils.readInt(buffer, offset));
    }

    public HGPersistentHandle nullHandle()
    {
        return nil;
    }
}