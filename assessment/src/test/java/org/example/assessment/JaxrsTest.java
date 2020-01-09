package org.example.assessment;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import java.io.IOException;
import java.util.HashMap;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.onehippo.cms7.services.HippoServiceRegistry;
import org.onehippo.repository.RepositoryService;
import org.onehippo.repository.jaxrs.RepositoryJaxrsServlet;
import org.onehippo.repository.testutils.PortUtil;
import org.onehippo.repository.testutils.RepositoryTestCase;
import com.jayway.restassured.authentication.PreemptiveBasicAuthScheme;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import net.sf.json.JSONObject;
public class JaxrsTest extends RepositoryTestCase {

	private static Tomcat tomcat;
	private static int portNumber;
	private static RequestSpecification spec;
	private static String baseUrl;

	@ClassRule
	public static TemporaryFolder tmpTomcatFolder = new TemporaryFolder();

	private static String getTmpTomcatFolderName() {
		return tmpTomcatFolder.getRoot().getAbsolutePath();
	}

	@BeforeClass
	public static void setupTomcat() throws LifecycleException {
		tomcat = new Tomcat();
		tomcat.setBaseDir(getTmpTomcatFolderName());
		portNumber = PortUtil.getPortNumber(JaxrsTest.class);
		tomcat.setPort(portNumber);
		tomcat.getConnector(); // Trigger the creation of the default connector
		Context context = tomcat.addContext("/cms", getTmpTomcatFolderName());
		Tomcat.addServlet(context, "RepositoryJaxrsServlet", new RepositoryJaxrsServlet());
		context.addServletMappingDecoded("/ws/*", "RepositoryJaxrsServlet");
		tomcat.start();  
		baseUrl = "http://localhost:" + portNumber + "/cms/ws";
		RequestSpec(baseUrl);
	}
	
	@Before
	public void before() {
		if (HippoServiceRegistry.getService(RepositoryService.class) == null) {
			HippoServiceRegistry.register((RepositoryService)server.getRepository(), RepositoryService.class);
		}
	}

	@AfterClass
	public static void tearDownTomcat() throws LifecycleException {
		tomcat.stop();
		tomcat.destroy();


	}
	@After
	@Override
	public void tearDown() throws Exception {
		removeNode("/books");       
		super.tearDown(); // removes /test node and checks repository clean state
	}

	private void expectOK(String path, String message) {
		RequestSpecification client =
				given().auth().preemptive().basic("admin", String.valueOf("admin"));
		String url = "http://localhost:" + portNumber + "/cms/ws" + path;
		client.get(url).then().statusCode(200).content(equalTo(message));
	}
	
/**
 * Create a common Request for API
 * @param strBaseurl
 */
	private static  void RequestSpec (String strBaseurl) {

		PreemptiveBasicAuthScheme auth = new PreemptiveBasicAuthScheme();
		auth.setUserName("admin");
		auth.setPassword("admin");      
		spec = new RequestSpecBuilder()
				.setContentType(ContentType.JSON)
				.setBaseUri (strBaseurl)
				.setAuth(auth)     
				.build();
	}
	
	/**
	 * Creates 3 demo books in system by seed end point
	 * @param path path of the resource in API
	 * @param message success message after hitting API
	 */
	private  void seedBooksInSystem(String path, String message) {
		String response = expectGetOK(path, 200);
		log.debug("createBooksInSystem response: \n {}", response);
		Assert.assertEquals("addBooks result Passed", message, response);		
	}
	/**
	 * Add books in repository by PUT method
	 * @param path path of the resource in API
	 * @param strPayload data to be sent in PUT request
	 */
	private  void addBookInSystem(String path, String strPayload) {
		String response = expectPutOK(path, strPayload);
		log.debug("addBookInSystem response: \n {}", response);
		Assert.assertNotNull("addBookInSystem response can not be null", response);

	}

	/**
	 * Performs GET request on given path
	 * @param iStatusCode status code for the request:200, 404 etc
	 * @param path path of the resource in API
	 * @return response API response
	 */
	private String expectGetOK(String path, int iStatusCode) {
		Response response =  given()
				.spec(spec)          
				.when()
				.get(path)
				.then()
				.statusCode(iStatusCode)
				.extract().response();


		return response.body().asString();
	}


	/**
	 * Performs DELETE request on given path
	 * @param iStatusCode status code for the request:200, 404 etc
	 * @param path path of the resource in API
	 * @return response API response
	 */
	private String expectDeleteOK(String path, int iStatusCode) {
		Response response =  given()
				.spec(spec)          
				.when()
				.delete(path)
				.then()
				.statusCode(iStatusCode)
				.extract().response();


		return response.body().asString();
	}

	/**
	 * Performs PUT request on given path
	 * @param strPayload data to be sent in PUT request
	 * @param path path of the resource in API
	 * @return response API response
	 */
	private String expectPutOK(String path, String strPayload) {
		Response response =  given()
				.spec(spec)          
				.when()
				.body(strPayload)
				.put(path)
				.then()
				.statusCode(200)
				.extract().response();


		return response.body().asString();
	}

	/**
	 * Searches for No books in repository
	 * @param path path of the resource in API	
	 */
	private void validateNoBooksInSystem(String path) {
		String response = expectGetOK(path, 200);		
		log.info("validateNoBooksInSystem response: {}", response);
		Assert.assertNotNull("validateNoBooksInSystem response can not be null", response);
		try {
			JsonPath jsonPath = new JsonPath(response);
			Assert.assertEquals("validateNoBooksInSystem response validation: ", jsonPath.getString("title"), "[]");
		} catch (Exception e) {
			log.error("Error occurred in validateSearchBooks " +e);
		}


	}

	/**
	 * Searches books in repository
	 * @param path path of the resource in API	
	 */
	private void validateBooksInSystem(String path) {
		String response = expectGetOK(path, 200);		
		log.info("validateBooksInSystem response: {}", response);
		Assert.assertNotNull("validateBooksInSystem response can not be null", response);
		try {
			JsonPath jsonPath = new JsonPath(response);
			Assert.assertNotEquals("validateBooksInSystem response validation: ", jsonPath.getString("title"), "[]");
			//Assert.assertFalse("validateBooksInSystem response validation: ", jsonPath.getString("title").isEmpty());
		} catch (Exception e) {
			log.error("Error occurred in validateSearchBooks " +e);
		}


	}

	/**
	 * Search for a book by text contents in repository
	 * @param path path of the resource in API	
	 * @param strQuery search string for the API
	 * @param iStatusCode status code for GET request
	 */
	private void validateSearchBooks(String path, String strQuery, int iStatusCode) {
		path = path+"?q="+strQuery;
		String response = expectGetOK(path, iStatusCode);	
		log.info("validateSearchBooks response: {}", response);
		Assert.assertNotNull("validateSearchBooks response can not be null", response);

		try {
			JsonPath jsonPath = new JsonPath(response);
			Assert.assertNotEquals("validateSearchBooks response validation: ", "[]", jsonPath.getString("shortDescription"));
			Assert.assertTrue("validateSearchBooks response validation: ", jsonPath.getString("shortDescription").contains(strQuery));

		} catch (Exception e) {			
			log.error("Error occurred in validateSearchBooks " +e);
		}

	}

	/**
	 * Search for a book by text contents in repository and gives No result as book is NOT present
	 * @param path path of the resource in API	
	 * @param strQuery search string for the API
	 * @param iStatusCode status code for GET request
	 */
	private void validateSearchBooksNegative(String path, String strQuery, int iStatusCode) {
		path = path+"?q="+strQuery;
		String response = expectGetOK(path, iStatusCode);	
		log.info("validateSearchBooksNegative response: {}", response);
		Assert.assertNotNull("validateSearchBooksNegative response can not be null", response);

		try {
			JsonPath jsonPath = new JsonPath(response);
			Assert.assertEquals("validateSearchBooksNegative response validation: ", "[]", jsonPath.getString("shortDescription"));

		} catch (Exception e) {			
			log.error("Error occurred in validateSearchBooksNegative " +e);
		}

	}

	/**
	 * Search for a book by ISBN
	 * @param path path of the resource in API	
	 * @param strIsbn ISBN of the book
	 * @param iStatusCode status code for GET request
	 */
	private void validateSearchBooksByIsbn(String path, String strIsbn, int iStatusCode) {
		path = path+"/"+strIsbn;
		String response = expectGetOK(path, iStatusCode);	
		log.info("validateSearchBooksByIsbn response: {}", response);
		Assert.assertNotNull("validateSearchBooksByIsbn response can not be null", response);
		try {
			JsonPath jsonPath = new JsonPath(response);
			if (iStatusCode == 404)
			{
				Assert.assertEquals("validateSearchBooksByIsbn response validation: ", "No book found with ISBN: "+strIsbn+"", response);

			}
			else
			{
				Assert.assertNotEquals("validateSearchBooksByIsbn response validation: ", "[]", jsonPath.getString("isbn"));
			}
		} catch (Exception e) {
			log.error("Error occurred in validateSearchBooks " +e);
		}
	}

	/**
	 * Removes a book from repository by using ISBN
	 * @param path path of the resource in DELETE API	
	 * @param strIsbn ISBN of the book
	 * @param iStatusCode status code for GET request
	 */
	private void validateDeleteBooksInSystem(String path, String strIsbn, int iStatusCode) {
		path = path+"/"+strIsbn;
		String response = expectDeleteOK(path,iStatusCode);	
		log.info("validateDeleteBooksInSystem response: {}", response);
		Assert.assertNotNull("validateDeleteBooksInSystem response can not be null", response);
		if (iStatusCode == 404)
		{
			Assert.assertEquals("validateDeleteBooksInSystem message after delete","No book found with ISBN: "+strIsbn, response);

		}
		else
		{
			Assert.assertEquals("validateDeleteBooksInSystem message","Book removed with isbn: "+strIsbn, response);
		}

		//		response = expectGetOK(path, 404);
		//		log.info("validateDeleteBooksInSystem response after delete: {}", response);
		//		Assert.assertNotNull("validateDeleteBooksInSystem after delete, response can not be null", response);
		//		Assert.assertEquals("validateDeleteBooksInSystem message after delete","No book found with ISBN: "+strIsbn, response);


	}

	/**
	 * Create data for PUT request
	 * @return body for API
	 * @throws IOException
	 */
	public String generateDataForRequest() throws IOException {

		HashMap<String, Object>  hmData = new HashMap<>();
		hmData.put("title", "Automation Testing Skills");
		hmData.put("isbn", "9780399226907");
		hmData.put("publicationDate", "764417364669");
		hmData.put("firstPublicationDate", null);
		hmData.put("shortDescription","good book for automation");
		hmData.put("author", "[ \"Anurag Singh\" ]");
		JSONObject json = new JSONObject();    	
		json.putAll( hmData );    
		return "["+json.toString(4)+"]";


	}

	@Test
	public void test_hello() {
		expectOK("/hello", "Hello system!");
	}


	@Test
	public void test_SeedBooks() {
		seedBooksInSystem("/books/seed", "3 books created");    
		validateBooksInSystem("/books"); 

	}



	@Test
	public void test_NoBookInSystemSuccess() {
		validateNoBooksInSystem("/books");
	}


	@Test
	public void test_SearchBookPositive() {
		seedBooksInSystem("/books/seed", "3 books created"); 
		validateSearchBooks("/books/search", "Dragons", 200);

	}

	@Test
	public void test_SearchBookNegative() {
		seedBooksInSystem("/books/seed", "3 books created"); 
		validateSearchBooksNegative("/books/search", "testing123", 200);
	}

	@Test
	public void test_SearchBooksByIsbnPositive() {
		seedBooksInSystem("/books/seed", "3 books created"); 
		validateSearchBooksByIsbn("/books", "9780399226908", 200);
	}

	@Test
	public void test_SearchBooksByIsbnNegative() {
		seedBooksInSystem("/books/seed", "3 books created"); 
		validateSearchBooksByIsbn("/books", "97808037368011", 404);
	}

	@Test
	public void test_DeleteBooksInSystemPositive() {
		seedBooksInSystem("/books/seed", "3 books created"); 
		validateDeleteBooksInSystem("/books", "9780679805274", 200);
	}

	@Test
	public void test_DeleteBooksInSystemNegative() {
		seedBooksInSystem("/books/seed", "3 books created"); 
		validateDeleteBooksInSystem("/books", "97806798052745", 404);
	}

	@Test
	public void test_AddBooksInSystem() throws IOException {
		String strPayload = generateDataForRequest();		
		addBookInSystem("/books", strPayload); 
		String strIsbn = new  JsonPath(strPayload).getString("isbn");
		validateSearchBooksByIsbn("/books", strIsbn, 200);

	}

}
