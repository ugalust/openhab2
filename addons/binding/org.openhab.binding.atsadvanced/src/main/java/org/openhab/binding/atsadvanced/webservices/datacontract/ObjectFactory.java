
package org.openhab.binding.atsadvanced.webservices.datacontract;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the org.datacontract.schemas._2004._07.atsadvancedtest package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _ArrayOfProgramProperty_QNAME = new QName("http://schemas.datacontract.org/2004/07/AtsAdvancedTest", "ArrayOfProgram.Property");
    private final static QName _ProgramProperty_QNAME = new QName("http://schemas.datacontract.org/2004/07/AtsAdvancedTest", "Program.Property");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.datacontract.schemas._2004._07.atsadvancedtest
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link ProgramProperty }
     * 
     */
    public ProgramProperty createProgramProperty() {
        return new ProgramProperty();
    }

    /**
     * Create an instance of {@link ArrayOfProgramProperty }
     * 
     */
    public ArrayOfProgramProperty createArrayOfProgramProperty() {
        return new ArrayOfProgramProperty();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ArrayOfProgramProperty }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://schemas.datacontract.org/2004/07/AtsAdvancedTest", name = "ArrayOfProgram.Property")
    public JAXBElement<ArrayOfProgramProperty> createArrayOfProgramProperty(ArrayOfProgramProperty value) {
        return new JAXBElement<ArrayOfProgramProperty>(_ArrayOfProgramProperty_QNAME, ArrayOfProgramProperty.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProgramProperty }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://schemas.datacontract.org/2004/07/AtsAdvancedTest", name = "Program.Property")
    public JAXBElement<ProgramProperty> createProgramProperty(ProgramProperty value) {
        return new JAXBElement<ProgramProperty>(_ProgramProperty_QNAME, ProgramProperty.class, null, value);
    }

}
