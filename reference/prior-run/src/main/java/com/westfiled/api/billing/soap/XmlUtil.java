package com.westfiled.api.billing.soap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JAXB marshal/unmarshal plus small DOM helpers for splicing fragments (a SAML assertion, a SOAP
 * fault) in and out of a larger XML document. There is no WSDL in the Mule source to generate a
 * client from, so callers hand-marshal small request beans and pull the response body back out of
 * the surrounding SOAP envelope by local element name.
 */
@Component
public class XmlUtil {

    private final ConcurrentMap<Class<?>, JAXBContext> contexts = new ConcurrentHashMap<>();

    public String marshal(Object jaxbBean) {
        try {
            Marshaller marshaller = contextFor(jaxbBean.getClass()).createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
            StringWriter writer = new StringWriter();
            marshaller.marshal(jaxbBean, writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to marshal " + jaxbBean.getClass().getSimpleName(), e);
        }
    }

    public <T> T unmarshal(String xml, Class<T> type) {
        try {
            Unmarshaller unmarshaller = contextFor(type).createUnmarshaller();
            return type.cast(unmarshaller.unmarshal(new StringReader(xml)));
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to unmarshal " + type.getSimpleName(), e);
        }
    }

    private JAXBContext contextFor(Class<?> type) {
        return contexts.computeIfAbsent(type, t -> {
            try {
                return JAXBContext.newInstance(t);
            } catch (JAXBException e) {
                throw new IllegalStateException("Unable to build JAXBContext for " + t.getSimpleName(), e);
            }
        });
    }

    /** Returns the outer XML (including the element itself) of the first element with the given local name, anywhere in the document. */
    public Optional<String> extractElement(String xml, String localName) {
        return findFirst(xml, localName).map(this::serialize);
    }

    /** Returns the text content of the first element with the given local name, anywhere in the document. */
    public Optional<String> extractElementText(String xml, String localName) {
        return findFirst(xml, localName).map(Node::getTextContent);
    }

    private Optional<Node> findFirst(String xml, String localName) {
        if (xml == null || xml.isBlank()) {
            return Optional.empty();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            NodeList matches = document.getElementsByTagNameNS("*", localName);
            return matches.getLength() > 0 ? Optional.of(matches.item(0)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse XML while looking for <" + localName + ">", e);
        }
    }

    private String serialize(Node node) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize XML node", e);
        }
    }
}
