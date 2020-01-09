package org.example.assessment;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import javax.annotation.PreDestroy;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.jackrabbit.util.Text;
import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.repository.RepositoryService;
import org.onehippo.repository.jaxrs.RepositoryJaxrsEndpoint;
import org.onehippo.repository.jaxrs.RepositoryJaxrsService;
import org.onehippo.repository.modules.DaemonModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

/**
 * Module to define a books JAX-RS repository service for CR**Q operations on a
 * simple representation of a book.
 * @author centgraf
 */
public class BooksModule implements DaemonModule {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Add books service to brXM at repository startup.
     */
    @Override
    public void initialize(final Session systemSession) throws RepositoryException {
        RepositoryJaxrsService.addEndpoint(
                new RepositoryJaxrsEndpoint("/books")
                		.singleton(new JacksonJsonProvider(
		                		// Enable pretty-printing output
		                		new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)))
                        .rootClass(BooksResource.class));
        log.debug("/books endpoint added");
    }

    /**
     * Cleanup service registration at repository shutdown.
     */
    @Override
    public void shutdown() {
        RepositoryJaxrsService.removeEndpoint("/books");
        log.debug("/books endpoint removed");
    }

    /**
     * The actual JAX-RS resource for book-related functions. This is a request-scoped resource, and
     * it creates a new session for each request.
     */
    public static class BooksResource {

        protected static final String SYSTEMUSER_ID = "admin";
        protected static final char[] SYSTEMUSER_PASSWORD = "admin".toCharArray();

        private final Session systemSession;

        public BooksResource() throws RepositoryException {
            final RepositoryService repository = HippoServiceRegistry.getService(RepositoryService.class);
            systemSession = repository.login(new SimpleCredentials(SYSTEMUSER_ID, SYSTEMUSER_PASSWORD));
        }

        @PreDestroy
        public void destroy() {
            System.out.println("destroying");
            systemSession.logout();
        }

		/**
		 * @return root node for books content
		 * @throws RepositoryException
		 */
		protected Node getBooksNode() throws RepositoryException {
			// Create the books node, if necessary
	        if (!systemSession.nodeExists("/books")) {
	        	Node rootNode = systemSession.getRootNode();
	        	rootNode.addNode("books");
	        	systemSession.save();
	        }
			return systemSession.getNode("/books");
		}

		/**
		 * Endpoint to return all stored Books in JSON format.
		 * @throws RepositoryException
		 */
        @Path("/")
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public Collection<Book> listBooks() throws RepositoryException {
            return Book.loadAll(getBooksNode());
        }
        
        /**
         * Endpoint to trigger creation of sample books in repository.
         * @return OK
         * @throws RepositoryException
         */
        @Path("/seed")
        // XXX: Semantically, this should be a PUT, but GET is convenient for manual tests
        @GET
       // @PUT 
        @Produces(MediaType.TEXT_PLAIN)
        public Response createSeedBooks() throws RepositoryException {
        	Book.createSampleBooks(getBooksNode());
        	systemSession.save();
        	return Response.ok().entity("3 books created").build();
        }

        /**
         * Store a single new book in the repository. Since the storage location and contents are
         * fully deterministic based on the provided ISBN and other properties, this is idempotent
         * and PUT is appropriate.
         * @return OK
         * @throws RepositoryException
         */
        @Path("/")
        @PUT 
        @Consumes(MediaType.APPLICATION_JSON)
        public Response storeBooks(List<Book> books) throws RepositoryException {
            for (Book book : books) {
                book.save(getBooksNode());
            }
        	systemSession.save();
        	return Response.ok().build();
        }

        /**
         * Lookup a single book in JSON format by its unique ISBN.
         * @param isbn ISBN of the desired book
         * @return JSON representation of the book or NOT_FOUND
         * @throws RepositoryException
         */
        @GET
        @Path("/{isbn}")
        @Produces(MediaType.APPLICATION_JSON)
        public Response findByISBN(@PathParam("isbn") String isbn) throws RepositoryException {
        	Book book = Book.loadByISBN(getBooksNode(), isbn);
        	if (book != null) {
        		return Response.ok().entity(book).build();
        	}
        	else {
        		// Sanitize input before producing it as output
        		isbn = StringEscapeUtils.escapeHtml4(isbn);
        		return Response.status(Status.NOT_FOUND).entity("No book found with ISBN: "+isbn).build();
        	}
        }

        /**
         * Remove a single book in JSON format by its unique ISBN.
         * @param isbn ISBN of the book to be removed
         * @return OK or NOT_FOUND
         * @throws RepositoryException
         */
        @DELETE
        @Path("/{isbn}")
        @Produces(MediaType.APPLICATION_JSON)
        public Response removeByISBN(@PathParam("isbn") String isbn) throws RepositoryException {
        	boolean removed = Book.removeByISBN(getBooksNode(), isbn);
        	systemSession.save();
        	
    		// Sanitize input before producing it as output
    		isbn = StringEscapeUtils.escapeHtml4(isbn);
			if (removed) {
        		return Response.ok().entity("Book removed with isbn: "+isbn).build();
        	}
        	else {
        		return Response.status(Status.NOT_FOUND).entity("No book found with ISBN: "+isbn).build();
        	}
        }
        
        /**
         * Search for a book by text matching on any field using default sort and data limits.
         * @param qString the search query string for a simple text contains match
         * @return a JSON array of matching books, possibly empty and in no particular order
         * @throws RepositoryException
         */
        @GET
        @Path("/search")
        @Produces(MediaType.APPLICATION_JSON)
        public Collection<Book> findByQuery(@QueryParam("q") String qString) throws RepositoryException {
        	QueryManager qm = systemSession.getWorkspace().getQueryManager();
        	
        	// Create and execute the query
        	// Filter out folders by requiring an ISBN property
        	//  and @"+Book.ISBN+"
        	Query q = qm.createQuery("//books//*[jcr:contains(.,'"+qString+"') and @"+Book.ISBN+"]", "xpath");
        	QueryResult qr = q.execute();
        	
        	// Iterate and accumulate results
        	LinkedList<Book> books = new LinkedList<>();
        	for (NodeIterator bookNodes = qr.getNodes(); bookNodes.hasNext();) {
        		Node bookNode = bookNodes.nextNode();
        		books.add(new Book(bookNode));
        	}
        	
        	return books;
        }
    }
}
