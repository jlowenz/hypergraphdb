package org.hypergraphdb.app.owl.versioning.distributed.serialize;

import java.io.IOException;
import java.io.Writer;

import org.hypergraphdb.app.owl.HGDBOntologyManagerImpl;
import org.hypergraphdb.app.owl.HGDBOntologyRepository;
import org.hypergraphdb.app.owl.versioning.VHGDBOntologyRepository;
import org.hypergraphdb.app.owl.versioning.VersionedOntology;
import org.semanticweb.owlapi.io.AbstractOWLRenderer;
import org.semanticweb.owlapi.io.OWLRendererException;
import org.semanticweb.owlapi.io.OWLRendererIOException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.Namespaces;

/**
 * OWLXMLVersionedOntologyRenderer.
 * @author Thomas Hilpold (CIAO/Miami-Dade County)
 * @created Feb 24, 2012
 */
public class OWLXMLVersionedOntologyRenderer extends AbstractOWLRenderer {

	/**
	 * @param owlOntologyManager
	 */
	public OWLXMLVersionedOntologyRenderer(OWLOntologyManager owlOntologyManager) {
		super(owlOntologyManager);
	}

	/* (non-Javadoc)
	 * @see org.semanticweb.owlapi.io.AbstractOWLRenderer#render(org.semanticweb.owlapi.model.OWLOntology, java.io.Writer)
	 */
	@Override
	public void render(OWLOntology ontology, Writer writer) throws OWLRendererException {
		OWLOntologyManager m = getOWLOntologyManager();
		if (m instanceof HGDBOntologyManagerImpl) {
			HGDBOntologyRepository ho = ((HGDBOntologyManagerImpl) m).getOntologyRepository();
			if (ho instanceof VHGDBOntologyRepository) {
				VHGDBOntologyRepository vrep = (VHGDBOntologyRepository)ho;
				VersionedOntology vo = vrep.getVersionControlledOntology(ontology);
				if (vo != null) {
					render(vo, writer);
				} else {
					new OWLRendererException("The given ontology is not versioned." + ontology);
				}
			} else {
				throw new OWLRendererException("Need a versioned VHGDBOntologyRepository in the manager.");
			}
		} else {
			throw new OWLRendererException("Need a HGDBOntologyManagerImpl as manager.");
		}
	}

	public void render(VersionedOntology vonto, Writer writer) throws OWLRendererException {
    	render(vonto, writer, new VersionedOntologyRenderConfiguration());
    }

    public void render(VersionedOntology vonto, Writer writer, VersionedOntologyRenderConfiguration configuration) throws OWLRendererException {
        try {
        	OWLXMLVersionedOntologyWriter vw = new OWLXMLVersionedOntologyWriter(writer, vonto);
            
            vw.startDocument(vonto);

            vw.writePrefix("rdf:", Namespaces.RDF.toString());
            vw.writePrefix("rdfs:", Namespaces.RDFS.toString());
            vw.writePrefix("xsd:", Namespaces.XSD.toString());
            vw.writePrefix("owl:", Namespaces.OWL.toString());

            OWLXMLVersioningObjectRenderer vren = new OWLXMLVersioningObjectRenderer(vw, configuration);
            configuration.accept(vren);
            vonto.accept(vren);
            vw.endDocument();
            writer.flush();
        }
        catch (IOException e) {
            throw new OWLRendererIOException(e);
        }
    }
}