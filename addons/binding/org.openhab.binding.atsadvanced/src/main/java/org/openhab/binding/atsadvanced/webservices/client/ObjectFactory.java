
package org.openhab.binding.atsadvanced.webservices.client;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the org.openhab.binding.atsadvanced package. 
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

    private final static QName _ProgramSendMessage_QNAME = new QName("ATSAdvanced.DTO", "Program.SendMessage");
    private final static QName _ProgramConfigureGateway_QNAME = new QName("ATSAdvanced.DTO", "Program.ConfigureGateway");
    private final static QName _ProgramConfigureGatewayResponse_QNAME = new QName("ATSAdvanced.DTO", "Program.ConfigureGatewayResponse");
    private final static QName _ProgramConfigurePanelResponse_QNAME = new QName("ATSAdvanced.DTO", "Program.ConfigurePanelResponse");
    private final static QName _ProgramSendMessageResponse_QNAME = new QName("ATSAdvanced.DTO", "Program.SendMessageResponse");
    private final static QName _ProgramConfigurePanel_QNAME = new QName("ATSAdvanced.DTO", "Program.ConfigurePanel");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.openhab.binding.atsadvanced
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link ProgramConfigurePanel }
     * 
     */
    public ProgramConfigurePanel createProgramConfigurePanel() {
        return new ProgramConfigurePanel();
    }

    /**
     * Create an instance of {@link ProgramSendMessage }
     * 
     */
    public ProgramSendMessage createProgramSendMessage() {
        return new ProgramSendMessage();
    }

    /**
     * Create an instance of {@link ProgramConfigureGatewayResponse }
     * 
     */
    public ProgramConfigureGatewayResponse createProgramConfigureGatewayResponse() {
        return new ProgramConfigureGatewayResponse();
    }

    /**
     * Create an instance of {@link ProgramSendMessageResponse }
     * 
     */
    public ProgramSendMessageResponse createProgramSendMessageResponse() {
        return new ProgramSendMessageResponse();
    }

    /**
     * Create an instance of {@link ProgramConfigurePanelResponse }
     * 
     */
    public ProgramConfigurePanelResponse createProgramConfigurePanelResponse() {
        return new ProgramConfigurePanelResponse();
    }

    /**
     * Create an instance of {@link ProgramConfigureGateway }
     * 
     */
    public ProgramConfigureGateway createProgramConfigureGateway() {
        return new ProgramConfigureGateway();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProgramSendMessage }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "ATSAdvanced.DTO", name = "Program.SendMessage")
    public JAXBElement<ProgramSendMessage> createProgramSendMessage(ProgramSendMessage value) {
        return new JAXBElement<ProgramSendMessage>(_ProgramSendMessage_QNAME, ProgramSendMessage.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProgramConfigureGateway }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "ATSAdvanced.DTO", name = "Program.ConfigureGateway")
    public JAXBElement<ProgramConfigureGateway> createProgramConfigureGateway(ProgramConfigureGateway value) {
        return new JAXBElement<ProgramConfigureGateway>(_ProgramConfigureGateway_QNAME, ProgramConfigureGateway.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProgramConfigureGatewayResponse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "ATSAdvanced.DTO", name = "Program.ConfigureGatewayResponse")
    public JAXBElement<ProgramConfigureGatewayResponse> createProgramConfigureGatewayResponse(ProgramConfigureGatewayResponse value) {
        return new JAXBElement<ProgramConfigureGatewayResponse>(_ProgramConfigureGatewayResponse_QNAME, ProgramConfigureGatewayResponse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProgramConfigurePanelResponse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "ATSAdvanced.DTO", name = "Program.ConfigurePanelResponse")
    public JAXBElement<ProgramConfigurePanelResponse> createProgramConfigurePanelResponse(ProgramConfigurePanelResponse value) {
        return new JAXBElement<ProgramConfigurePanelResponse>(_ProgramConfigurePanelResponse_QNAME, ProgramConfigurePanelResponse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProgramSendMessageResponse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "ATSAdvanced.DTO", name = "Program.SendMessageResponse")
    public JAXBElement<ProgramSendMessageResponse> createProgramSendMessageResponse(ProgramSendMessageResponse value) {
        return new JAXBElement<ProgramSendMessageResponse>(_ProgramSendMessageResponse_QNAME, ProgramSendMessageResponse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ProgramConfigurePanel }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "ATSAdvanced.DTO", name = "Program.ConfigurePanel")
    public JAXBElement<ProgramConfigurePanel> createProgramConfigurePanel(ProgramConfigurePanel value) {
        return new JAXBElement<ProgramConfigurePanel>(_ProgramConfigurePanel_QNAME, ProgramConfigurePanel.class, null, value);
    }

}
