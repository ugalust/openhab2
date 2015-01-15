
package org.openhab.binding.atsadvanced.webservices.datacontract;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfProgram.Property complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfProgram.Property">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Program.Property" type="{http://schemas.datacontract.org/2004/07/AtsAdvancedTest}Program.Property" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfProgram.Property", propOrder = {
    "programProperty"
})
public class ArrayOfProgramProperty {

    @XmlElement(name = "Program.Property", nillable = true)
    protected List<ProgramProperty> programProperty;

    /**
     * Gets the value of the programProperty property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the programProperty property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProgramProperty().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ProgramProperty }
     * 
     * 
     */
    public List<ProgramProperty> getProgramProperty() {
        if (programProperty == null) {
            programProperty = new ArrayList<ProgramProperty>();
        }
        return this.programProperty;
    }

}
