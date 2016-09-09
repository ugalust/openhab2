namespace AtsAdvancedTest
{
	using System;
	using System.Diagnostics;
	using System.Collections.Generic;
	using System.IO;
	using System.Text;
	using System.Net;
	using AtsAdvancedTest.Actions;
	using Ace;
	using Ace.Messages;
	using Ace.Ats;
	using Ace.Utilities;
//	using AtsAdvancedTest.Server;
	using ServiceStack;
	using System.Threading;
	using ServiceStack.Text;
	using ServiceStack.Serialization;
	using ServiceStack.DataAnnotations;
	using System.Runtime.Serialization;
	using System.Net.NetworkInformation;
	using System.Security.Permissions;
	using System.Security;


	internal static class Program
	{
		private static ManualResetEvent completed = new ManualResetEvent (false);
		private static Panel panel;
		private static String openHABURL;
		private static EventLoggerService loggerService;
		private static ProgramOptions options;


		[DataContract (Namespace = "ATSAdvanced.DTO")]
		public class ConfigurePanel
		{
			[DataMember]
			public string Hostaddress { get; set; }

			[DataMember]
			public int Port { get; set; }

			[DataMember]
			public string Password { get; set; }

			[DataMember]
			public int Hearbeat { get; set; }

			[DataMember]
			public int Timeout { get; set; }

			[DataMember]
			public int Retries { get; set; }
		}

		[DataContract (Namespace = "ATSAdvanced.DTO")]
		public class ConfigurePanelResponse
		{
			[DataMember]
			public string Result { get; set; }
		}

		[DataContract (Namespace = "ATSAdvanced.DTO")]
		public class ConfigureGateway
		{
			[DataMember]
			public string openHABURL { get; set; }
		}

		[DataContract (Namespace = "ATSAdvanced.DTO")]
		public class ConfigureGatewayResponse
		{
			[DataMember]
			public string Result { get; set; }
		}

		[DataContract (Namespace = "ATSAdvanced.DTO")]
		public class SendMessage
		{
			[DataMember]
			public string name { get; set; }

			[DataMember]
			public List<Property> Properties { get; set; }
		}

		public class Property
		{
			public string id;
			public int index;
			public object value;
		}

		[DataContract (Namespace = "ATSAdvanced.DTO")]
		public class SendMessageResponse
		{
			[DataMember]
			public string name { get; set; }

			[DataMember]
			public List<Property> Properties { get; set; }
		}

		public class PanelEventListener : IEventListener
		{
			private EventLoggerService panelLoggerService;

			public PanelEventListener (EventLoggerService service)
			{
				panelLoggerService = service;
			}

			public bool Received (ISender device, IMessage message)
			{
				Console.WriteLine ("Received event from Panel : {0}: {1}", message.ToString (), message.Info.Id);

				ProgramSendMessage logEntry = new ProgramSendMessage ();
				var propertyList = new List<ProgramProperty> ();
				logEntry.name = message.Info.Id;

				IMessageInfo finalInfo = message.Info;

				IPropertyInfoCollection finalProperties = finalInfo.Properties;

				int propertyCounter = 0;

				foreach (IPropertyInfo someInfo in finalProperties) {
					Console.WriteLine ("Property: {0}", someInfo.Id);
					IAttributeCollection propertyAttributes = someInfo.Attributes;

					String multiplicity = propertyAttributes ["multiplicity"];
					if (multiplicity == "dynamic") {
						for (int i = 0; i < message.Count; i++) {
							Console.WriteLine ("Property added: {0} : {1} : {2}", someInfo.Id, i + 1, message.GetProperty (someInfo.Id, i + 1, null));
							ProgramProperty logProperty = new ProgramProperty ();
							logProperty.id = someInfo.Id;
							logProperty.index = i + 1;
							logProperty.value = message.GetProperty (someInfo.Id, i + 1, null);
							propertyList.Add (logProperty);
							propertyCounter++;
						}
					} else {
						if (message.GetPropertyStatus (someInfo.Id, 0) == PropertyStatus.Ok) {
							Console.WriteLine ("Property added: {0} : {1}", someInfo.Id, message.GetProperty (someInfo.Id, 0, null));
							ProgramProperty logProperty = new ProgramProperty ();
							logProperty.id = someInfo.Id;
							logProperty.index = 0;
							logProperty.value = message.GetProperty (someInfo.Id, 0, null);
							propertyList.Add (logProperty);
							propertyCounter++;
						} else {
							Console.WriteLine ("Property not OK: {0}", someInfo.Id);
						}
					}
				}

				logEntry.Properties = propertyList.ToArray ();

				Console.WriteLine ("{0} properties will be send to openHAB", propertyCounter);

				ProgramSendMessage response = panelLoggerService.logMessage (logEntry);

				Console.WriteLine ("event was acknowledged by openHAB");

				return true;
			}
		}

		public class PanelService : Service
		{
			public IMessage response;

			public object Any (ConfigurePanel request)
			{
				Console.WriteLine ("Receiving a request to configure the panel: {0}",request.Hostaddress);
				//base.Response.AddHeader("Content-Type", "text/xml; charset=ISO-8859-1");

				Program.panel = new Panel (IPAddress.Parse (request.Hostaddress), request.Port, "0000000000000000000000000000000000000000000000000000", request.Hearbeat, request.Timeout, request.Retries);
				Console.WriteLine ("Starting discovery of the panel");
				String result = Program.panel.StartDiscover ();
				Console.WriteLine ("Sending response of ConfigurePanel to openHAB: {0}",result);



				return new ConfigurePanelResponse { Result = result };

			}

			public object Any (ConfigureGateway request)
			{
				Console.WriteLine ("Receiving a request to configure the gateway: {0}",request.openHABURL);
				loggerService = new EventLoggerService (request.openHABURL);
				if (Program.panel != null) {
					Program.panel.AddListener (new PanelEventListener (loggerService));
					Console.WriteLine ("Panel Event Listener Created at {0}, posting on events to {1}",
						DateTime.Now, request.openHABURL);
				}

				Console.WriteLine ("Sending response of ConfigureGateway to openHAB: {0}","1");
	
				return new ConfigureGatewayResponse { Result = "1"  };
			}

			public object Any (SendMessage request)
			{
				SendMessageResponse soapResponse = new SendMessageResponse ();
				soapResponse.Properties = new List<Property> ();

				try {
					Console.WriteLine ("Received message from openHAB: {0}", request.name);
					IMessage msg = panel.CreateMessage (request.name);

					Console.WriteLine ("\t {0} properties", request.Properties.Count);
					foreach (Property property in request.Properties) {
						Console.WriteLine ("\t setting properties {0}, index {1} with value {2}", property.id, property.index, property.value);
						msg.SetProperty (property.id, property.index, property.value);
					}

					Console.WriteLine ("Sending message to Panel: {0}", request.name);
					IAsyncResult sendResult = panel.BeginSend (msg, null, null);
					sendResult.AsyncWaitHandle.WaitOne ();
					IMessage finalResponse = panel.EndSend (sendResult);
					sendResult.AsyncWaitHandle.Close ();

					Console.WriteLine ("Received response from Panel: {0}", finalResponse.Info.Id);
					IMessageInfo finalInfo = finalResponse.Info;

					IPropertyInfoCollection finalProperties = finalInfo.Properties;

					soapResponse.name = finalResponse.Info.Id;

					foreach (IPropertyInfo someInfo in finalProperties) {
						IAttributeCollection propertyAttributes = someInfo.Attributes;

						String multiplicity = propertyAttributes ["multiplicity"];
						if (multiplicity == "dynamic") {
							for (int i = 0; i < finalResponse.Count; i++) {
								Property soapProperty = new Property ();
								soapProperty.id = someInfo.Id;
								soapProperty.index = i;
								soapProperty.value = finalResponse.GetProperty (someInfo.Id, i + 1, null);								soapResponse.Properties.Add (soapProperty);

							}
						} else {

							if (finalResponse.GetPropertyStatus (someInfo.Id, 0) == PropertyStatus.Ok) {

								Property soapProperty = new Property ();
								soapProperty.id = someInfo.Id;
								soapProperty.index = 0;

								if (someInfo.Id == "device.FWID_MAC") {
									var address = (byte[])finalResponse.GetProperty ("device.FWID_MAC", 0, typeof(byte[]));
									PhysicalAddress someAddress = new PhysicalAddress (address);
									soapProperty.value = someAddress;
									if (Array.TrueForAll (address, (b) => b == 0)) {
										soapProperty.value = null;
									}
								} else if (someInfo.Id == "userCARD") {
									var address = (byte[])finalResponse.GetProperty ("userCARD", 0, typeof(byte[]));
									//							PhysicalAddress someAddress = new PhysicalAddress (address);
									soapProperty.value = "test";
									//							if (Array.TrueForAll (address, (b) => b == 0)) {
									//								soapProperty.value = null;
									//							}
								} else if (someInfo.Id == "bitSet") {
									byte[] bitSet = (byte[])finalResponse.GetProperty ("bitSet", 0, typeof(byte[]));
									soapProperty.value = bitSet;
								} else {
									soapProperty.value = finalResponse.GetProperty (someInfo.Id, 0, null);
								}

								soapResponse.Properties.Add (soapProperty);

							} else {
							}

						}
					}
				}
				catch (AtsFaultException e) {
					soapResponse.name = "return.error";
					Property soapProperty = new Property ();
					soapProperty.id = "result";
					soapProperty.index = 0;
					soapProperty.value = string.Format ("Fault response {0}, {1}", e.Code, e.Message);
					soapResponse.Properties.Add (soapProperty);
				}
				catch (Exception e) {
					soapResponse.name = "return.error";
					Property soapProperty = new Property ();
					soapProperty.id = "result";
					soapProperty.index = 0;
					soapProperty.value = e.Message;
					soapResponse.Properties.Add (soapProperty);;
				}

				Console.WriteLine ("Sending response to openHAB: {0}", soapResponse.name);
				return soapResponse;

			}

			public void SendMessageCompleted (IAsyncResult ar)
			{
				try {
					response = panel.EndSend (ar);
				} catch (Exception e) {
					Program.Error (e.Message);
					IMessage faultResponse = panel.CreateMessage ("return.bool");
					faultResponse.SetProperty ("result", 0, 1);
					response = faultResponse;
				}

			}
		}
		//Define the Web Services AppHost
		public class AppHost : AppHostHttpListenerBase
		{
			public AppHost ()
					: base ("HttpListener Self-Host", typeof(PanelService).Assembly)
			{
			}

			public override void Configure (Funq.Container container)
			{

				SetConfig (new HostConfig { WsdlServiceNamespace = "ATSAdvanced.DTO", });

				Routes
						.Add<ConfigurePanel> ("/configurepanel")
						.Add<SendMessage> ("/sendmessage");
			}
		}

		private static IMessageDriver driver;

		internal static IMessageDriver Driver {
			get {
				IMessageDriver result = driver;
				if (driver == null) {
					result = LoadMessageDriver ();
					if (System.Threading.Interlocked.CompareExchange (ref driver, result, null) != null) {
						result = driver;
					}
				}

				Debug.Assert (result != null, "Missing message driver.");
				return result;
			}
		}

		internal static void Usage (string options, params string[] details)
		{
			var syntax = new StringBuilder ()
	                .AppendFormat ("Syntax: {0} {1}", Path.GetFileNameWithoutExtension (Process.GetCurrentProcess ().MainModule.ModuleName), options)
	                .AppendLine ();
			if (details.Length > 0) {
				syntax
	                    .AppendLine ()
	                    .AppendLine ("Options:")
	                    .AppendLine ();
				foreach (var detail in details) {
					syntax.AppendLine (detail);
				}
			}

			Console.WriteLine (syntax.ToString ());
		}



		internal static void logMessage(String name, String message) {

			ProgramSendMessage logEntry = new ProgramSendMessage ();
			var propertyList = new List<ProgramProperty> ();
			logEntry.name = name;

			ProgramProperty logProperty = new ProgramProperty ();
			logProperty.id = "result";
			logProperty.index = 0;
			logProperty.value = message;

			propertyList.Add (logProperty);
			logEntry.Properties = propertyList.ToArray ();

			if (loggerService != null) {
				ProgramSendMessage response = loggerService.logMessage (logEntry);
			}
		}

		internal static void Info (string message)
		{
			Console.WriteLine ("INFO: {0}", message);
			logMessage ("msg.info", message);

		}

		internal static void Warn (string message)
		{
			Console.WriteLine ("WARNING: {0}", message);
			logMessage ("msg.warn", message);

//			if (Environment.ExitCode == 0) {
//				Environment.ExitCode = -1;
//			}
		}

		internal static void Error (string message)
		{
			Console.Error.WriteLine ("ERROR: {0}", message);
			if ((Environment.ExitCode == 0) || (Environment.ExitCode == -1)) {
				Environment.ExitCode = -2;
			}

			if (message.Contains ("Address already in use") || message.Contains ("The socket has been shut down") || message.Contains ("Connection reset by peer") || message.Contains("Object reference not set to an instance of an object")) {
				logMessage ("msg.error", message);
				Console.Error.WriteLine ("Exiting with code: {0}", Environment.ExitCode);
				Environment.Exit (-2);
			}


			logMessage ("msg.error", message);


		}

		// Set the UnmanagedCode property.
		[SecurityPermissionAttribute(SecurityAction.Assert, UnmanagedCode = true)]

		private static void Main (string[] args)
		{
			try {

				options = new ProgramOptions (args);
				if (options.Help) {
					options.Usage ();
					return;
				}
					
				var listeningOn = "http://*:" + options.Port.ToString() + "/";


				var appHost = new AppHost ();
				appHost.Init ();
				appHost.Start (listeningOn);

				Console.WriteLine ("AppHost Created at {0}, listening on {1}",
					DateTime.Now, listeningOn);
				}
			catch (Exception e) {
				Error (e.Message);
			}
				
			completed.WaitOne ();
		
		}

		private static IMessageDriver LoadMessageDriver ()
		{
			string driverFileName = "ats.advanced.drv"; 
			//Console.WriteLine ("Full path is {0}", Path.GetFullPath (options.DriverPath));
			Console.WriteLine ("Driver path is {0}", Path.Combine (Path.GetDirectoryName (Process.GetCurrentProcess ().MainModule.ModuleName), driverFileName));

			IMessageDriver result = LoadMessageDriverFrom (Path.GetFullPath (options.DriverPath))
			                                  ?? LoadMessageDriverFrom (Path.Combine (Path.GetDirectoryName (Process.GetCurrentProcess ().MainModule.ModuleName), driverFileName))
			                                  ?? LoadBuildInDriver ();
			return result;
		}

		private static IMessageDriver LoadMessageDriverFrom (string path)
		{
			if (!File.Exists (path)) {
				return null;
			}

			Stream input = null;
			try {
				input = File.OpenRead (path);
				IMessageDriver result = MessageDriverLoader.LoadDriver (input);
				if (result != null) {
					Info(string.Format ("Using message driver from '{0}'.", path));
				}

				return result;
			} catch (Exception e) {
				Warn (string.Format ("Cannot load message driver file from '{0}': {1}", path, e));
				return null;
			} finally {
				if (input != null) {
					input.Dispose ();
				}
			}
		}

		private static IMessageDriver LoadBuildInDriver ()
		{
			using (Stream input = System.Reflection.Assembly.GetExecutingAssembly ().GetManifestResourceStream ("AtsAdvancedTest.ats.advanced.drv")) {
				Info("INFO: Using build-in message driver.");
				return MessageDriverLoader.LoadDriver (input);
			}
		}
	}
}
