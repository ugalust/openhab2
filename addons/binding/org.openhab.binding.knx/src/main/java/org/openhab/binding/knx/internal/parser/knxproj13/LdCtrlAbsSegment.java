//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.03.09 at 08:34:29 PM CET 
//


package org.openhab.binding.knx.internal.parser.knxproj13;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;


/**
 * <p>Java class for LdCtrlAbsSegment complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="LdCtrlAbsSegment">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://www.w3.org/2001/XMLSchema>string">
 *       &lt;attribute name="LsmIdx" type="{http://www.w3.org/2001/XMLSchema}byte" />
 *       &lt;attribute name="Seg" type="{http://www.w3.org/2001/XMLSchema}byte" />
 *       &lt;attribute name="Address" type="{http://www.w3.org/2001/XMLSchema}short" />
 *       &lt;attribute name="Size" type="{http://www.w3.org/2001/XMLSchema}short" />
 *       &lt;attribute name="Access" type="{http://www.w3.org/2001/XMLSchema}byte" />
 *       &lt;attribute name="Mem" type="{http://www.w3.org/2001/XMLSchema}byte" />
 *       &lt;attribute name="SegFlags" type="{http://www.w3.org/2001/XMLSchema}short" />
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LdCtrlAbsSegment", propOrder = {
    "value"
})
public class LdCtrlAbsSegment {

    @XmlValue
    protected java.lang.String value;
    @XmlAttribute(name = "LsmIdx")
    protected Byte lsmIdx;
    @XmlAttribute(name = "Seg")
    protected Byte seg;
    @XmlAttribute(name = "Address")
    protected Short address;
    @XmlAttribute(name = "Size")
    protected Short size;
    @XmlAttribute(name = "Access")
    protected Byte access;
    @XmlAttribute(name = "Mem")
    protected Byte mem;
    @XmlAttribute(name = "SegFlags")
    protected Short segFlags;

    /**
     * Gets the value of the value property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setValue(java.lang.String value) {
        this.value = value;
    }

    /**
     * Gets the value of the lsmIdx property.
     * 
     * @return
     *     possible object is
     *     {@link Byte }
     *     
     */
    public Byte getLsmIdx() {
        return lsmIdx;
    }

    /**
     * Sets the value of the lsmIdx property.
     * 
     * @param value
     *     allowed object is
     *     {@link Byte }
     *     
     */
    public void setLsmIdx(Byte value) {
        this.lsmIdx = value;
    }

    /**
     * Gets the value of the seg property.
     * 
     * @return
     *     possible object is
     *     {@link Byte }
     *     
     */
    public Byte getSeg() {
        return seg;
    }

    /**
     * Sets the value of the seg property.
     * 
     * @param value
     *     allowed object is
     *     {@link Byte }
     *     
     */
    public void setSeg(Byte value) {
        this.seg = value;
    }

    /**
     * Gets the value of the address property.
     * 
     * @return
     *     possible object is
     *     {@link Short }
     *     
     */
    public Short getAddress() {
        return address;
    }

    /**
     * Sets the value of the address property.
     * 
     * @param value
     *     allowed object is
     *     {@link Short }
     *     
     */
    public void setAddress(Short value) {
        this.address = value;
    }

    /**
     * Gets the value of the size property.
     * 
     * @return
     *     possible object is
     *     {@link Short }
     *     
     */
    public Short getSize() {
        return size;
    }

    /**
     * Sets the value of the size property.
     * 
     * @param value
     *     allowed object is
     *     {@link Short }
     *     
     */
    public void setSize(Short value) {
        this.size = value;
    }

    /**
     * Gets the value of the access property.
     * 
     * @return
     *     possible object is
     *     {@link Byte }
     *     
     */
    public Byte getAccess() {
        return access;
    }

    /**
     * Sets the value of the access property.
     * 
     * @param value
     *     allowed object is
     *     {@link Byte }
     *     
     */
    public void setAccess(Byte value) {
        this.access = value;
    }

    /**
     * Gets the value of the mem property.
     * 
     * @return
     *     possible object is
     *     {@link Byte }
     *     
     */
    public Byte getMem() {
        return mem;
    }

    /**
     * Sets the value of the mem property.
     * 
     * @param value
     *     allowed object is
     *     {@link Byte }
     *     
     */
    public void setMem(Byte value) {
        this.mem = value;
    }

    /**
     * Gets the value of the segFlags property.
     * 
     * @return
     *     possible object is
     *     {@link Short }
     *     
     */
    public Short getSegFlags() {
        return segFlags;
    }

    /**
     * Sets the value of the segFlags property.
     * 
     * @param value
     *     allowed object is
     *     {@link Short }
     *     
     */
    public void setSegFlags(Short value) {
        this.segFlags = value;
    }

}
