package org.example.assessment;

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import org.apache.commons.codec.binary.Base64;
import org.junit.After;
import org.junit.Test;
import org.onehippo.repository.testutils.RepositoryTestCase;

public class RepositoryTest extends RepositoryTestCase {
	
	String strPath = "/hippo:configuration/hippo:queries";

    @Test
    public void test_minimal() throws RepositoryException {
        Node rootNode = session.getRootNode();
        rootNode.addNode("test");
        rootNode.addNode("books");
        session.save();
        assertTrue(session.nodeExists("/test"));
        assertTrue(session.nodeExists("/books"));
    }

    @After
    @Override
    public void tearDown() throws Exception {
        removeNode("/books");
        super.tearDown(); // removes /test node and checks repository clean state
    }
    
    @Test
	public void test_TraverseNodewithDetails() throws RepositoryException, IOException {
		log.info(traverseNode(session.getNode(strPath)));
	}

	/**
	 *Recursive method for traversing node for the given path
	 *@return Nodes with properties
	 *@param node
	 *@throws RepositoryException
	 */
	private String traverseNode(Node node) throws RepositoryException, IOException {

		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append(getNodeDetails(node));		
		NodeIterator nodeIterator = node.getNodes();
		while (nodeIterator.hasNext()) {
			strBuilder.append(traverseNode(nodeIterator.nextNode()));
		}
		return strBuilder.toString();
	}

	/**
	 * Print node details
	 * @param node
	 * @throws RepositoryException, IOException	
	 */
	public String getNodeDetails(Node node) throws RepositoryException, IOException  {

		StringBuilder strBuilder = new StringBuilder();
		String strNodeDetail = "\n Node: "+node.getName()+ "\n Depth: "+node.getDepth() + "\n Identifier: "+node.getIdentifier() + "\n Path: "+node.getPath() + "\n Properties: ";
		strBuilder.append(strNodeDetail);	
		PropertyIterator propertyIterator = node.getProperties();
		while (propertyIterator.hasNext()) {
			strBuilder.append(getNodeProperty(propertyIterator.nextProperty())).append("\n");
		}
		return strBuilder.toString();
	}

	/**
	 * Print Node properties
	 * @param property
	 * @return node properties
	 * @throws RepositoryException, IOException
	 */
	private String getNodeProperty(Property property) throws RepositoryException, IOException {
		String propertyValue = null;
		StringBuilder strTmp = new StringBuilder();
		if (property.isMultiple()) {
			for(Value val : property.getValues())
			{			
				strTmp.append(val.getString()).append(",");				
			}	
			propertyValue = "["+strTmp.toString()+"]";
			//propertyValue = "["+strTmp.deleteCharAt(strTmp.length()-1).toString()+"]";

			//For Binary data: NODE: hippogallery:thumbnail
		} else if (property.getType() == 2) {					
			propertyValue = property.getValue().getString();
			byte[] encoded = Base64.encodeBase64(propertyValue.getBytes());
			String strEncodedString = new String(encoded, "US-ASCII");			
			propertyValue = strEncodedString;
		} else {
			propertyValue = property.getValue().getString();
		}
		String strPropDetails = property.getName()+ " = "+propertyValue;
		return strPropDetails;
	}
}
