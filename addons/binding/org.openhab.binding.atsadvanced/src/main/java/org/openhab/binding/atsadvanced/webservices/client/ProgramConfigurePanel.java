
package org.openhab.binding.atsadvanced.webservices.client;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Program.ConfigurePanel complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Program.ConfigurePanel">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Hearbeat" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="Hostaddress" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="Password" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="Port" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="Retries" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="Timeout" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Program.ConfigurePanel", propOrder = {
    "hearbeat",
    "hostaddress",
    "password",
    "port",
    "retries",
    "timeout"
})
public class ProgramConfigurePanel {

    @XmlElement(name = "Hearbeat")
    protected int hearbeat;
    @XmlElement(name = "Hostaddress", required = true, nillable = true)
    protected String hostaddress;
    @XmlElement(name = "Password", required = true, nillable = true)
    protected String password;
    @XmlElement(name = "Port")
    protected int port;
    @XmlElement(name = "Retries")
    protected int retries;
    @XmlElement(name = "Timeout")
    protected int timeout;

    /**
     * Gets the value of the hearbeat property.
     * 
     */
    public int getHearbeat() {
        return hearbeat;
    }

    /**
     * Sets the value of the hearbeat property.
     * 
     */
    public void setHearbeat(int value) {
        this.hearbeat = value;
    }

    /**
     * Gets the value of the hostaddress property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostaddress() {
        return hostaddress;
    }

    /**
     * Sets the value of the hostaddress property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostaddress(String value) {
        this.hostaddress = value;
    }

    /**
     * Gets the value of the password property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the value of the password property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPassword(String value) {
        this.password = value;
    }

    /**
     * Gets the value of the port property.
     * 
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the value of the port property.
     * 
     */
    public void setPort(int value) {
        this.port = value;
    }

    /**
     * Gets the value of the retries property.
     * 
     */
    public int getRetries() {
        return retries;
    }

    /**
     * Sets the value of the retries property.
     * 
     */
    public void setRetries(int value) {
        this.retries = value;
    }

    /**
     * Gets the value of the timeout property.
     * 
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the value of the timeout property.
     * 
     */
    public void setTimeout(int value) {
        this.timeout = value;
    }

}
