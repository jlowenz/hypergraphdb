package org.hypergraphdb.app.owl.test.versioning;

import org.hypergraphdb.app.owl.HGDBOntology;
import org.hypergraphdb.app.owl.gc.GarbageCollector;
import org.hypergraphdb.app.owl.newver.ChangeMark;
import org.hypergraphdb.app.owl.newver.Revision;
import org.hypergraphdb.app.owl.newver.RevisionMark;
import org.hypergraphdb.app.owl.newver.VersionManager;
import org.hypergraphdb.app.owl.newver.VersionedOntology;
import org.hypergraphdb.app.owl.test.TU;
import org.hypergraphdb.app.owl.versioning.distributed.activity.ActivityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import static org.junit.Assert.*;

import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.IRI;

import static org.hypergraphdb.app.owl.test.TU.*;

public class VersionSerializationTests extends VersioningTestBase
{
	
	@Before public void beforeTest() throws Exception
	{ 
		ctx = (TestContext)TU.ctx;
		ctx.o = (HGDBOntology)ctx.m.createOntology(IRI.create(iri_prefix + "_" + "serialization")); 
		ctx.vr = new VersionManager(ctx.graph, "testuser");
		ctx.vo = ctx.vr.versioned(ctx.graph.getHandle(ctx.o));				
	}
	
	@After public void afterTest() throws Exception
	{
		remove(ctx.vo);
	}

	/**
	 * Test ability to serialize and deserialize an empty ontology.
	 */
	@Test
	public void serializeEmpty()
	{
		String asxml = ActivityUtils.renderVersionedOntology(ctx.vo);
		System.out.println(asxml);
		remove(ctx.vo);
		VersionedOntology vo2 = ActivityUtils.storeVersionedOntology(new StringDocumentSource(asxml), ctx.m);
		assertEquals(ctx.vo.getAtomHandle(), vo2.getAtomHandle());
		assertEquals(ctx.vo.getRootRevision(), vo2.getRootRevision());
		assertEquals(ctx.vo.getCurrentRevision(), vo2.getCurrentRevision());
		assertEquals(ctx.vo.getOntology(), vo2.getOntology());
		ctx.vo = vo2;
	}

	/**
	 * Test ability to serialize an ontology with a flushed change set
	 * but no revision yet - the changeset shouldn't be serialized. 
	 */
	@Test
	public void serializeNoRevisions()
	{
		a(aInstanceOf(owlClass("Bla"), individual("foo")));
		assertEquals(1, ctx.vo.changes().size());
		ctx.vo.flushChanges();
		assertTrue(ctx.vo.changes().isEmpty());
		ChangeMark mark = ctx.graph.get(ctx.vo.getRevisionMark(ctx.vo.getCurrentRevision()).mark());
		assertEquals(1, mark.children().size());
		String asxml = ActivityUtils.renderVersionedOntology(ctx.vo);
		System.out.println(asxml);
		remove(ctx.vo);
		VersionedOntology vo2 = ActivityUtils.storeVersionedOntology(new StringDocumentSource(asxml), ctx.m);
		assertEquals(ctx.vo.getAtomHandle(), vo2.getAtomHandle());
		assertEquals(ctx.vo.getRootRevision(), vo2.getRootRevision());
		assertEquals(ctx.vo.getCurrentRevision(), vo2.getCurrentRevision());
		assertEquals(ctx.vo.getOntology(), vo2.getOntology());
		assertTrue(vo2.changes().isEmpty());		
		System.out.println("current revision " + vo2.getCurrentRevision());
		System.out.println("current revision mark " + vo2.getRevisionMark(vo2.getCurrentRevision()));
		mark = ctx.graph.get(vo2.getRevisionMark(vo2.getCurrentRevision()).mark());
		assertEquals(0, mark.children().size());
		ctx.vo = vo2;
	}
	
	/**
	 * Test ability to serialize an ontology with one revision only. 
	 */
	@Test
	public void serializeOneRevision()
	{
		a(aInstanceOf(owlClass("Bla"), individual("foo")));
		assertEquals(1, ctx.vo.changes().size());
		ctx.vo.commit("unittest", "one revision serialization");
		assertTrue(ctx.vo.changes().isEmpty());
		assertNotSame(ctx.vo.getRootRevision(), ctx.vo.getCurrentRevision());
		ChangeMark mark = ctx.graph.get(ctx.vo.getRevisionMark(ctx.vo.getCurrentRevision()).mark());
		assertEquals(1, mark.parents().size());
		assertEquals(0, mark.children().size());
		String asxml = ActivityUtils.renderVersionedOntology(ctx.vo);
		System.out.println(asxml);
		remove(ctx.vo);
		VersionedOntology vo2 = ActivityUtils.storeVersionedOntology(new StringDocumentSource(asxml), ctx.m);
		assertEquals(ctx.vo.getAtomHandle(), vo2.getAtomHandle());
		assertEquals(ctx.vo.getRootRevision(), vo2.getRootRevision());
		assertEquals(ctx.vo.getCurrentRevision(), vo2.getCurrentRevision());
		assertEquals(ctx.vo.getOntology(), vo2.getOntology());
		assertTrue(vo2.changes().isEmpty());
		RevisionMark revisionMark = vo2.getRevisionMark(vo2.getCurrentRevision());
		mark = ctx.graph.get(revisionMark.mark());
		assertEquals(1, mark.parents().size());
		assertEquals(0, mark.children().size());
		ChangeMark parentMark = ctx.graph.get(mark.parents().iterator().next());
		assertEquals(ctx.m.getVersionManager().emptyChangeSetHandle(), parentMark.changeset());
		assertEquals(ctx.vo.getCurrentRevision(), revisionMark.revision());
		assertEquals(vo2.getCurrentRevision(), revisionMark.revision());
		ctx.vo = vo2;
	}

	/**
	 * This tests the ability to have more than one revision with multiple
	 * intermediate ChangeSet commits in between.
	 */
	@Test
	public void serializeTwoRevisionsMultipleChangeCommits()
	{
		Revision initialRevision = ctx.vo.revision();
		declare(owlClass("User"));
		declare(owlClass("Employee"));
		aSubclassOf(owlClass("User"), owlClass("Employee"));
		Revision revision1 = ctx.vo.commit("anonymous", "First version");
		
		declare(individual("Pedro"));
		aInstanceOf(owlClass("Employee"), individual("Pedro"));
		aSubclassOf(owlClass("User"), owlClass("Customer"));
		declare(owlClass("LoyalCustomer"));
		ChangeMark mark1 = ctx.vo.flushChanges();
		
		aSubclassOf(owlClass("Customer"), owlClass("LoyalCustomer"));
		aInstanceOf(owlClass("LoyalCustomer"), individual("Mary"));
		aInstanceOf(owlClass("LoyalCustomer"), individual("Tom"));
		ChangeMark mark2 = ctx.vo.flushChanges();
		
		aInstanceOf(owlClass("Customer"), individual("John"));
		aInstanceOf(owlClass("Employee"), individual("Fred"));
		declare(oprop("isServing"));		
		aProp(oprop("isServing"), individual("Fred"), individual("Tom"));
		ChangeMark mark3 = ctx.vo.flushChanges();
		// no changes between last flush and the creation of a new revision
		Revision revision2 = ctx.vo.commit("administrator", "Second version by admin");		
		
		// some more in working set, shouldn't be serialized
		aProp(dprop("hasAge"), individual("Mary"), literal("54"));
		
		assertEquals(1, revision2.changeMarks().size());
		assertEquals(mark3.getAtomHandle(), revision2.changeMarks().iterator().next());
		
		String asxml = ActivityUtils.renderVersionedOntology(ctx.vo);
		System.out.println(asxml);
		remove(ctx.vo);
		
		VersionedOntology vo2 = ActivityUtils.storeVersionedOntology(new StringDocumentSource(asxml), ctx.m);
		assertEquals(ctx.vo.getAtomHandle(), vo2.getAtomHandle());
		assertEquals(ctx.vo.getRootRevision(), vo2.getRootRevision());
		assertEquals(ctx.vo.getCurrentRevision(), vo2.getCurrentRevision());
		assertEquals(ctx.vo.getOntology(), vo2.getOntology());
		assertTrue(vo2.changes().isEmpty());
		RevisionMark revisionMark = vo2.getRevisionMark(vo2.getCurrentRevision());
		ChangeMark mark = ctx.graph.get(revisionMark.mark());
		assertEquals(1, mark.parents().size());
		assertEquals(0, mark.children().size());
		assertEquals(mark3, mark);
		assertEquals(TU.set(vo2.revision().parents().toArray()), TU.set(revision1.getAtomHandle()));
		assertEquals(mark.parents(), TU.set(mark2.getAtomHandle()));
	}
	
	public static void main(String []argv)
	{
		JUnitCore junit = new JUnitCore();
		Result result = junit.run(Request.method(VersionSerializationTests.class, "serializeTwoRevisionsMultipleChangeCommits"));
		System.out.println("Failures " + result.getFailureCount());
		if (result.getFailureCount() > 0)
		{
			for (Failure failure : result.getFailures())
			{
				failure.getException().printStackTrace();
			}
		}
	}
}
