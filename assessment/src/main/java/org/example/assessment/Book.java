package org.example.assessment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.StringUtils;

/**
 * Simple POJO / JavaBean representation of a book, along with basic JCR load and store functions.
 * @author centgraf
 */
public class Book {
	/**
	 * Property name constants.
	 */
	static final String SHORT_DESCRIPTION = "book:shortDescription";
	static final String FIRST_PUBLICATION_DATE = "book:firstPublicationDate";
	static final String PUBLICATION_DATE = "book:publicationDate";
	static final String ISBN = "book:isbn";
	static final String AUTHOR = "book:author";
	static final String TITLE = "book:title";
	
	/**
	 * Sample text for testing.
	 */
	static final String DRAGONS_DESC = "Dragons love tacos. They love chicken tacos, beef tacos, great big tacos, and teeny tiny tacos. So if you want to lure a bunch of dragons to your party, you should definitely serve tacos. Buckets and buckets of tacos. Unfortunately, where there are tacos, there is also salsa. And if a dragon accidentally eats spicy salsa . . . oh, boy. You're in red-hot trouble.";

	/**
	 * Full title
	 */
	protected String title;
	
	/**
	 * Authors in print priority order
	 */
	protected List<String> author = Collections.emptyList();
	
	/**
	 * 13-digit unique ISBN number without separators
	 */
	protected String isbn;
	
	/**
	 * Official publication date for the edition identified by the ISBN
	 */
	protected Calendar publicationDate;
	
	/**
	 * Official publication date for the first edition of this book
	 */
	protected Calendar firstPublicationDate;
	
	/**
	 * Very brief description appropriate for product listings
	 */
	protected String shortDescription;
	
	//======================================================================
	// Non-trivial implementation logic
	//======================================================================

	/**
	 * Create three sample Book objects and save them. To be used for demo purposes only.
	 * @param booksRoot JCR node representing the root of the books content
	 * @throws RepositoryException
	 */
	public static void createSampleBooks(Node booksRoot) throws RepositoryException {
		Book book = new Book();
	    book.setISBN("978-0399226908");
	    book.setTitle("The Very Hungry Caterpillar");
	    book.setAuthor("Eric Carle");
	    Calendar date = Calendar.getInstance();
	    date.set(1994, 2, 23);
	    book.setPublicationDate(date);
	    book.setShortDescription("THE all-time classic picture book, from generation to generation, sold somewhere in the world every 30 seconds! Have you shared it with a child or grandchild in your life?");
	    book.save(booksRoot);
	    
	    book.setISBN("978-0803736801");
	    book.setTitle("Dragons Love Tacos");
	    book.setAuthor("Adam Rubin","Daniel Salmieri");
	    date = Calendar.getInstance();
	    date.set(2012, 5, 20);
	    book.setPublicationDate(date);
	    book.setShortDescription(Book.DRAGONS_DESC);
	    book.save(booksRoot);
	    
	    book.setISBN("978-0679805274");
	    book.setTitle("Oh, The Places You'll Go!");
	    book.setAuthor("Dr. Seuss");
	    date = Calendar.getInstance();
	    date.set(1990, 0, 22);
	    book.setPublicationDate(date);
	    book.setShortDescription("Dr. Seuss’s wonderfully wise Oh, the Places You’ll Go! is the perfect send-off for grads—from nursery school, high school, college, and beyond! From soaring to high heights and seeing great sights to being left in a Lurch on a prickle-ly perch, Dr. Seuss addresses life’s ups and downs with his trademark humorous verse and illustrations, while encouraging readers to find the success that lies within. In a starred review, Booklist notes, “Seuss’s message is simple but never sappy: life may be a ‘Great Balancing Act,’ but through it all ‘There’s fun to be done.’” A perennial favorite and a perfect gift for anyone starting a new phase in their life!");
	    book.save(booksRoot);
	}
	
	/**
	 * Load all Book instances stored here. For demo purposes only -- an
	 * implementation in earnest should support pagination, sorting, and data
	 * limiting at minimum.
	 * @param booksRoot JCR node representing the root of the books content
	 * @return a Collection of Book objects representing all stored books, in no particular order
	 */
	public static Collection<Book> loadAll(Node booksRoot) throws RepositoryException {
		// Straightforward iterate-and-accumulate
		// TODO: Implement output limits via sorting/paging
		ArrayList<Book> books = new ArrayList<>();
		for (NodeIterator folders = booksRoot.getNodes(); folders.hasNext();) {
			Node folder = folders.nextNode();
			for (NodeIterator bookNodes = folder.getNodes(); bookNodes.hasNext();) {
				Node bookNode = bookNodes.nextNode();
				Book book = new Book(bookNode);
				books.add(book);
			}
		}
		return books;
	}
	
	/**
	 * Load a Book object based on an ISBN key. This method provides no caching
	 * or uniqueness guarantees.
	 * @param booksRoot JCR node representing the root of the books content
	 * @param isbn ISBN for the desired Book
	 * @return a Book instance or null, if this system doesn't know the ISBN
	 * @throws RepositoryException
	 */
	public static Book loadByISBN(Node booksRoot, String isbn) throws RepositoryException {
		// Sanitize ISBN input before searching
		isbn = sanitizeISBN(isbn);
		Node folder = findFolder(booksRoot, isbn);
		
		// Short-circuit data loading if no appropriate node exists
		if (folder == null || !folder.hasNode(isbn)) {
			return null;
		}
		Node bookNode = folder.getNode(isbn);
		Book book = new Book();
		
		// Peform the actual data copying
		book.load(bookNode);
		
		return book;
	}
	
	/**
	 * Remove a book by its ISBN number
	 * @param booksRoot JCR node representing the root of the books content
	 * @param isbn ISBN for the Book to be removed
	 * @return true if a book was actually removed, or false if no such book previously existed
	 * @throws RepositoryException
	 */
	public static boolean removeByISBN(Node booksRoot, String isbn) throws RepositoryException {
		// Sanitize ISBN input before searching
		isbn = sanitizeISBN(isbn);
		Node folder = findFolder(booksRoot, isbn);
		
		// Short-circuit removal if folder or book nodes don't exist
		if (folder == null || !folder.hasNode(isbn)) {
			return false;
		}
		else {
			// Remove book
			folder.getNode(isbn).remove();
			
			// Clean up folder node, if empty
			if (!folder.hasNodes()) {
				folder.remove();
			}
			
			return true;
		}
	}
	
	/**
	 * Remove all non-digit characters from ISBN input.
	 * @param isbn
	 * @return
	 */
	protected static String sanitizeISBN(String isbn) {
		return isbn.replaceAll("\\D", "");
	}
	
	/**
	 * Default JavaBean zero-arg constructor.
	 */
	public Book() {}
	
	/**
	 * Create a Book from an existing JCR Node with book data.
	 * @param bookNode a previously-saved book node
	 */
	public Book(Node bookNode) throws RepositoryException {
		this.load(bookNode);
	}

	/**
	 * Load data for a Book object from a JCR node representing the book.
	 * @param bookNode JCR node representing this book
	 * @throws RepositoryException
	 */
	protected void load(Node bookNode) throws RepositoryException {
		// Copy properties
		this.isbn = bookNode.getProperty(ISBN).getString();
		
		if (bookNode.hasProperty(TITLE)) {
			this.title = bookNode.getProperty(TITLE).getString();
		}
		if (bookNode.hasProperty(AUTHOR)) {
			Value[] authors = bookNode.getProperty(AUTHOR).getValues();
			List<String> authorList = new ArrayList<String>(authors.length);
			for (Value a: authors) {
				authorList.add(a.getString());
			}
			this.author = authorList;
		}
		if (bookNode.hasProperty(PUBLICATION_DATE)) {
			this.publicationDate = bookNode.getProperty(PUBLICATION_DATE).getDate();
		}
		if (bookNode.hasProperty(FIRST_PUBLICATION_DATE)) {
			this.firstPublicationDate = bookNode.getProperty(FIRST_PUBLICATION_DATE).getDate();
		}
		if (bookNode.hasProperty(SHORT_DESCRIPTION)) {
			this.shortDescription = bookNode.getProperty(SHORT_DESCRIPTION).getString();
		}
	}
	
	/**
	 * Create or update a JCR node representing this book, using the ISBN as
	 * primary identifier. This method updates Nodes but does not save the 
	 * session.
	 * @param booksRoot JCR node representing the root of the books content
	 */
	public void save(Node booksRoot) throws RepositoryException {
		// The only required field for a book in this system is the ISBN.
		if (StringUtils.isBlank(isbn)) {
			throw new IllegalStateException("ISBN field is required to save a book!");
		}
		
		// Minimal validation for ISBN value -- must be 13 digits.
		if (isbn.length() != 13) {
			throw new IllegalStateException("ISBN field must be 13 digits!");
		}
		
		// TODO: More validation checks...

		// Shard storage of books by ISBN
		Node folder = getFolder(booksRoot);
		Node bookNode = getBookNode(folder);
		
		// Store book data as direct properties
		// TODO: Perform field-by-field change detection to suppress redundant change events
		bookNode.setProperty(ISBN, isbn);
		if (StringUtils.isNotBlank(title)) {
			bookNode.setProperty(TITLE, title);
		}
		if (!author.isEmpty()) {
			bookNode.setProperty(AUTHOR, author.toArray(new String[author.size()]));
		}
		if (publicationDate != null) {
			bookNode.setProperty(PUBLICATION_DATE, publicationDate);
		}
		if (firstPublicationDate != null) {
			bookNode.setProperty(FIRST_PUBLICATION_DATE, firstPublicationDate);
		}
		if (StringUtils.isNotBlank(title)) {
			bookNode.setProperty(SHORT_DESCRIPTION, shortDescription);
		}
	}

	/**
	 * Remove this book from the repository.
	 * @see #removeByISBN(Node, String)
	 * @param booksRoot JCR node representing the root of the books content
	 */
	public boolean remove(Node booksRoot) throws RepositoryException {
		return removeByISBN(booksRoot, isbn);
	}
	
	/**
	 * Find, but do not create, a folder for books by ISBN sharding. Note that
	 * this implementation must match {@link #getFolder(Node)}.
	 * @param booksRoot the root node for books storage
	 * @param isbn the ISBN for which we want a folder
	 * @return the appropriate folder Node or null, if it doesn't exist yet
	 */
	protected static Node findFolder(Node booksRoot, String isbn) throws RepositoryException {
		String folderKey = getFolderKey(isbn);
		if (booksRoot.hasNode(folderKey)) {
			return booksRoot.getNode(folderKey);
		}
		else {
			return null;
		}
	}
	
	/**
	 * Helper method to find or create the folder Node appropriate for the ISBN
	 * of this book for storage sharding.
	 * @param booksNode the root node for books storage
	 * @return the appropriate folder Node for this book
	 * @throws RepositoryException 
	 */
	protected Node getFolder(Node booksRoot) throws RepositoryException {
		String folderKey = getFolderKey(isbn);
		if (booksRoot.hasNode(folderKey)) {
			return booksRoot.getNode(folderKey);
		}
		else {
			return booksRoot.addNode(folderKey);
		}
	}

	/**
	 * Get a sharding key for a given ISBN.
	 * @param isbn the ISBN
	 * @return a short sharding key
	 */
	protected static String getFolderKey(String isbn) {
		// Use the last three digits of the ISBN to subdivide storage.
		// NOTE: Almost all 13-digit ISBNs share the same first 3 digits,
		//       so it is an inappropriate sharding key.
		return StringUtils.right(isbn, 3);
	}

	/**
	 * Helper method to find or create the Node appropriate for the ISBN
	 * of this book within a sharded folder for storage.
	 * @param folder the sharded folder node appropriate for this book
	 * @return the appropriate Node for this book
	 * @throws RepositoryException 
	 */
	protected Node getBookNode(Node folder) throws RepositoryException {
		if (folder.hasNode(isbn)) {
			return folder.getNode(isbn);
		}
		else {
			return folder.addNode(isbn, "book:Book");
		}
	}

	//======================================================================
	// Basic property get/set methods
	//======================================================================
	
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public List<String> getAuthor() {
		return author;
	}

	@JsonProperty("author")
	public void setAuthor(List<String> author) {
		// Null safety protection
		if (author == null) {
			author = Collections.emptyList();
		}
		this.author = author;
	}

	public void setAuthor(String... author) {
		this.author = Arrays.asList(author);
	}

	public String getISBN() {
		return isbn;
	}

	public void setISBN(String isbn) {
		this.isbn = sanitizeISBN(isbn);
	}

	public Calendar getPublicationDate() {
		return publicationDate;
	}

	public void setPublicationDate(Calendar publicationDate) {
		this.publicationDate = publicationDate;
	}

	public Calendar getFirstPublicationDate() {
		return firstPublicationDate;
	}

	public void setFirstPublicationDate(Calendar firstPublicationDate) {
		this.firstPublicationDate = firstPublicationDate;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public void setShortDescription(String shortDescription) {
		this.shortDescription = shortDescription;
	}

	/**
	 * Simple auto-generated toString implementation.
	 */
	public String toString() {
		return "Book [" + (title != null ? "title=" + title + ", " : "")
				+ (author != null ? "author=" + author + ", " : "") + (isbn != null ? "isbn=" + isbn + ", " : "")
				+ (publicationDate != null ? "publicationDate=" + publicationDate + ", " : "")
				+ (firstPublicationDate != null ? "firstPublicationDate=" + firstPublicationDate + ", " : "")
				+ (shortDescription != null ? "shortDescription=" + shortDescription : "") + "]";
	}
	
}
