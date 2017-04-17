/* Options:
Date: 2017-03-31 16:20:39
Version: 4.58
Tip: To override a DTO option, remove "//" prefix before updating
BaseUrl: http://localhost:1337

Package: org.openhab.binding.atsadvanced.internal
GlobalNamespace: PanelClient
//AddPropertyAccessors: True
//SettersReturnThis: True
//AddServiceStackTypes: True
//AddResponseStatus: False
//AddDescriptionAsComments: True
//AddImplicitVersion:
//IncludeTypes:
//ExcludeTypes:
//TreatTypesAsStrings:
//DefaultImports: java.math.*,java.util.*,net.servicestack.client.*
*/

package org.openhab.binding.atsadvanced.internal;

import java.util.ArrayList;

import net.servicestack.client.IReturn;
import net.servicestack.client.Route;

public class PanelClient {

    @Route("/configurepanel")
    public static class ConfigurePanel implements IReturn<ConfigurePanelResponse> {
        public String Hostaddress = null;
        public Integer Port = null;
        public String Password = null;
        public Integer Hearbeat = null;
        public Integer Timeout = null;
        public Integer Retries = null;

        public String getHostaddress() {
            return Hostaddress;
        }

        public ConfigurePanel setHostaddress(String value) {
            this.Hostaddress = value;
            return this;
        }

        public Integer getPort() {
            return Port;
        }

        public ConfigurePanel setPort(Integer value) {
            this.Port = value;
            return this;
        }

        public String getPassword() {
            return Password;
        }

        public ConfigurePanel setPassword(String value) {
            this.Password = value;
            return this;
        }

        public Integer getHearbeat() {
            return Hearbeat;
        }

        public ConfigurePanel setHearbeat(Integer value) {
            this.Hearbeat = value;
            return this;
        }

        public Integer getTimeout() {
            return Timeout;
        }

        public ConfigurePanel setTimeout(Integer value) {
            this.Timeout = value;
            return this;
        }

        public Integer getRetries() {
            return Retries;
        }

        public ConfigurePanel setRetries(Integer value) {
            this.Retries = value;
            return this;
        }

        private static Object responseType = ConfigurePanelResponse.class;

        @Override
        public Object getResponseType() {
            return responseType;
        }
    }

    @Route("/message")
    public static class Message implements IReturn<MessageResponse> {
        public String name = null;
        public ArrayList<Property> Properties = null;

        public String getName() {
            return name;
        }

        public Message setName(String value) {
            this.name = value;
            return this;
        }

        public ArrayList<Property> getProperties() {
            return Properties;
        }

        public Message setProperties(ArrayList<Property> value) {
            this.Properties = value;
            return this;
        }

        private static Object responseType = MessageResponse.class;

        @Override
        public Object getResponseType() {
            return responseType;
        }
    }

    public static class ConfigurePanelResponse {
        public String Result = null;

        public String getResult() {
            return Result;
        }

        public ConfigurePanelResponse setResult(String value) {
            this.Result = value;
            return this;
        }
    }

    public static class MessageResponse {
        public String name = null;
        public ArrayList<Property> Properties = null;

        public String getName() {
            return name;
        }

        public MessageResponse setName(String value) {
            this.name = value;
            return this;
        }

        public ArrayList<Property> getProperties() {
            return Properties;
        }

        public MessageResponse setProperties(ArrayList<Property> value) {
            this.Properties = value;
            return this;
        }
    }

    public static class Program {

    }

    public static class Property {
        public String id = null;
        public Integer index = null;
        public Object value = null;

        public String getId() {
            return id;
        }

        public Property setId(String value) {
            this.id = value;
            return this;
        }

        public Integer getIndex() {
            return index;
        }

        public Property setIndex(Integer value) {
            this.index = value;
            return this;
        }

        public Object getValue() {
            return value;
        }

        public Property setValue(Object value) {
            this.value = value;
            return this;
        }
    }

}
