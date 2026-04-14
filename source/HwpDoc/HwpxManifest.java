package HwpDoc;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class HwpxManifest {

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
