package HwpDoc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class HwpxManifest {

	
	/*
	<odf:manifest xmlns:odf="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
		<odf:file-entry full-path="Contents/header.xml" media-type="application/xml" size="35485">
			<odf:encryption-data checksum-type="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#sha256-1k" checksum="axrtFWccmPDGJOpM1jzyFvQw8xzu58tmoz7D/ZMrnuw=">
				<odf:algorithm algorithm-name="http://www.w3.org/2001/04/xmlenc#aes256-cbc" initialisation-vector="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:key-derivation key-derivation-name="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#pbkdf2" key-size="32" iteration-count="1024" salt="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:start-key-generation start-key-generation-name="http://www.w3.org/2000/09/xmldsig#sha256" key-size="32"/>
			</odf:encryption-data>
		</odf:file-entry>
		<odf:file-entry full-path="Contents/section0.xml" media-type="application/xml" size="3434">
			<odf:encryption-data checksum-type="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#sha256-1k" checksum="OtrtJCE1VN08j8zbd1OvpfBM7nE5pkQkLfgzqmpjj3E=">
				<odf:algorithm algorithm-name="http://www.w3.org/2001/04/xmlenc#aes256-cbc" initialisation-vector="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:key-derivation key-derivation-name="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#pbkdf2" key-size="32" iteration-count="1024" salt="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:start-key-generation start-key-generation-name="http://www.w3.org/2000/09/xmldsig#sha256" key-size="32"/>
			</odf:encryption-data>
		</odf:file-entry>
		<odf:file-entry full-path="Preview/PrvText.txt" media-type="text/xml" size="16">
			<odf:encryption-data checksum-type="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#sha256-1k" checksum="hIflHpsUCuyi97klxEGwZ0OqxZdpRRpL2tvqoXhTURA=">
				<odf:algorithm algorithm-name="http://www.w3.org/2001/04/xmlenc#aes256-cbc" initialisation-vector="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:key-derivation key-derivation-name="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#pbkdf2" key-size="32" iteration-count="1024" salt="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:start-key-generation start-key-generation-name="http://www.w3.org/2000/09/xmldsig#sha256" key-size="32"/>
			</odf:encryption-data>
		</odf:file-entry>
		<odf:file-entry full-path="settings.xml" media-type="text/xml" size="681">
			<odf:encryption-data checksum-type="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#sha256-1k" checksum="IbeneL8p34jCf3tkWS7EIU6JbsRZwShA7yewQQTLDdQ=">
				<odf:algorithm algorithm-name="http://www.w3.org/2001/04/xmlenc#aes256-cbc" initialisation-vector="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:key-derivation key-derivation-name="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0#pbkdf2" key-size="32" iteration-count="1024" salt="60UzpZlurcNkOAIh8XzCKQ=="/>
				<odf:start-key-generation start-key-generation-name="http://www.w3.org/2000/09/xmldsig#sha256" key-size="32"/>
			</odf:encryption-data>
		</odf:file-entry>
	</odf:manifest>
	*/
	Map<String, Map<String, String>> entryMap = new HashMap<>();
	
	
	// Owpml형식 (hwpx)
	boolean parse(Document document) {
        Element manifest = document.getDocumentElement();
        
        NodeList fileEntryList = manifest.getElementsByTagName("odf:file-entry");
        for (int j = 0; j < fileEntryList.getLength(); j++) {
        	Node fileEntry = fileEntryList.item(j);
        	Element fileEntryEl = (Element) fileEntry;
        	
        	Map<String, String> fileMap = new HashMap<>();
        	String fullPath = fileEntryEl.getAttribute("full-path");
        	fileMap.put("fullPath", fullPath);
        	entryMap.put(fullPath, fileMap);
        	String fileSize = fileEntryEl.getAttribute("size");
        	fileMap.put("fileSize", fileSize);
        	
        	NodeList encryptionDataList = ((Element) fileEntry).getElementsByTagName("odf:encryption-data");
        	for (int k = 0; k < encryptionDataList.getLength(); k++) {
        		Node encryptionData = encryptionDataList.item(k);
        		
        		String checksum = ((Element) encryptionData).getAttribute("checksum");
        		fileMap.put("checksum", checksum);

                NodeList children = encryptionData.getChildNodes();
                for (int l=0; l<children.getLength(); l++) {

                    Node childNode = children.item(l);
                    switch(childNode.getNodeName()) {
                    case "odf:algorithm":
                        String iv = ((Element)childNode).getAttribute("initialisation-vector");
                    	fileMap.put("iv", iv);
                        String algorithm = ((Element)childNode).getAttribute("algorithm-name"); 
                    	fileMap.put("algorithm", algorithm);
                        break;
                    case "odf:key-derivation":
                    	String keyDerivation = ((Element)childNode).getAttribute("key-derivation-name");
                    	fileMap.put("keyDerivation", keyDerivation);
                    	String keySize = ((Element)childNode).getAttribute("key-size");
                    	fileMap.put("keySize", keySize);
                    	String iterCount = ((Element)childNode).getAttribute("iteration-count");
                    	fileMap.put("iterCount", iterCount);
                    	String salt = ((Element)childNode).getAttribute("salt");
                    	fileMap.put("salt", salt);
                    	break;
                    case "odf:start-key-generation":
                    	String startKeyAlgorithm = ((Element)childNode).getAttribute("start-key-generation-name");
                    	fileMap.put("startKeyAlgorithm", startKeyAlgorithm);
                    	String startKeySize = ((Element)childNode).getAttribute("key-size");
                    	fileMap.put("startKeySize", startKeySize);
                        break;
                    }
                }
        	}
        }
        
        return true;
	}

}
