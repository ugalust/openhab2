namespace AtsAdvancedTest
{
    using System;
    using System.Collections.Generic;
    using System.Linq;
	using System.Net;
    using System.Text;
    using Ace.Ats;
    using Ace;
    using System.Diagnostics;
	using Ace.Communication;
	using System.Net.Sockets;
	using System.Threading;
	using AtsAdvancedTest.Actions;
	using Ace.Utilities;

    internal class Panel
    {
        private IMessageFactory factory = Program.Driver.CreateFactory();
        private AtsAdvancedPanel panel = new AtsAdvancedPanel();
		private TcpCommunicationChannel channel;

		internal Panel(IPAddress address, int port, string password, int heartbeat, int timeout, int retries)
		{
			this.panel.EventMessageFactory = factory;

			Debug.Assert (address != null, "Missing host name of the panel.");
			Debug.Assert (port != 0, "Missing port number of the panel.");
			Debug.Assert (heartbeat != 0, "Missing heartbeat period of the panel.");
			Debug.Assert (timeout != 0, "Missing timeout period of the panel.");
			Debug.Assert (retries != 0, "Missing number of retries of the panel.");

			this.Hostaddress = address;
			this.Port = port;
			this.Password = password;
			this.Heartbeat = heartbeat;
			this.Timeout = timeout;
			this.Retries = retries;

			if (Heartbeat != -1)
			{
				EnablePingSettings(this.Heartbeat, this.Timeout, this.Retries);
			}

			Console.WriteLine ("Channel with host: {0}",this.Hostaddress);
			Console.WriteLine ("Channel with port: {0}",this.Port);
			this.channel = new TcpCommunicationChannel(this.Hostaddress, this.Port);
			if (this.channel != null) {
				Console.WriteLine ("Channel is not null");
			}
			this.panel.Open(this.channel, true);

        }
			
		public IPAddress Hostaddress
		{
			get;
			set;
		}

		public int Port
		{
			get;
			set;
		}
		public String Password
		{
			get;
			set;
		}

		public int Heartbeat
		{
			get;
			set;
		}

		public int Timeout
        {
            get;
            set;
        }

		public int Retries
        {
            get;
            set;
        }

		public void AddListener(IEventListener aListener) {
			this.panel.AddListener (aListener);
		}

        internal void SetSerialNumber(long serialNumber)
        {
            this.panel.SerialNumber = serialNumber;
        }

        internal void SetEncryptionKey(byte[] key)
        {
            this.panel.SetEncryptionKey(key);
        }

        internal void EnablePingSettings(int period, int timeout, int retries)
        {
            this.panel.PingSettings = new PingSettings(period, timeout, retries);
        }

        internal IMessage CreateMessage(string name)
        {
            return this.factory.CreateMessage(name);
        }

        internal IAsyncResult BeginSend(IMessage message, AsyncCallback callback, object state)
        {
            return this.panel.BeginSend(message, this.Timeout, this.Retries, callback, state);
        }

        internal IMessage EndSend(IAsyncResult ar)
        {
            return this.panel.EndSend(ar);
        }

        internal void AdjustFactory(DeviceDiscovery info)
        {
            try
            {
                this.factory.SetProperty("model", 0, info.Model);
				}
            catch (Exception e)
            {
				throw new Exception (string.Format("Unable to setup model context in the factory: '{0}'. {1}", info.Model, e));
            }

            try
            {
                int protocol = int.Parse(info.Version.Split('.')[1]);
                do
                {
                    try
                    {
						this.factory.SetProperty("protocol", 0, protocol);
						break;
                    }
                    catch (Exception ex)
                    {
                        protocol--;
                    }
                } while (protocol > 0);
            }
            catch (Exception e)
            {
				throw new Exception (string.Format ("Unable to setup protocol context in the factory: '{0}'. {1}", info.Version, e));
            }
        }

        internal int GetMaximumAreaIndex()
        {
            return (int)this.factory.GetProperty("maximum-area-index", 0, typeof(int));
        }

		public String StartDiscover()
		{
			String feedbackMessage = "1";

			try
			{
				IMessage request = CreateMessage("device.getDescription");

				IAsyncResult sendResult = BeginSend(request, null,null);
				sendResult.AsyncWaitHandle.WaitOne ();
				var result = new DeviceDiscovery(this.panel.EndSend(sendResult));
				sendResult.AsyncWaitHandle.Close ();

				AdjustFactory(result);
				SetSerialNumber(Convert.ToInt64(result.SerialNumber, 16));

				if (result.EncryptionMode != 0)
				{
					SetEncryptionKey(new MasterKeyProvider(this.Password).GetKeyData(result.EncryptionMode));
					byte[] sessionKey = new byte[16];

					AtsUtils.FillRandomBytes(sessionKey);

					var sessionRequest = CreateMessage("begin.changeSessionKey");
					sessionRequest.SetProperty("data", 0, sessionKey);

					sendResult = BeginSend(sessionRequest, null, null);
					sendResult.AsyncWaitHandle.WaitOne ();
					IMessage sessionResponse = EndSend(sendResult);
					sendResult.AsyncWaitHandle.Close();

					int length = AtsUtils.GetEncryptionKeySize(result.EncryptionMode);
					byte[] newKey = new byte[length];
					Buffer.BlockCopy(sessionKey, 0, newKey, 0, length / 2);
					sessionKey = (byte[])sessionResponse.GetProperty("data", 0, typeof(byte[]));
					Buffer.BlockCopy(sessionKey, 0, newKey, length / 2, length / 2);

					var sessionEndRequest = CreateMessage("end.changeSessionKey");
					sendResult = BeginSend(sessionEndRequest, null, null);
					sendResult.AsyncWaitHandle.WaitOne ();
					IMessage sessionEndresponse = EndSend(sendResult);
					sendResult.AsyncWaitHandle.Close();

					this.panel.SetEncryptionKey(newKey);
				}
			}
			catch (AtsFaultException e)
			{
				feedbackMessage = string.Format("ATS Fault response {0}, {1}", e.Code, e.Message);
				Program.Error(feedbackMessage);

			}
			catch (Exception e)
			{
				feedbackMessage = string.Format("General Fault response {0}", e.Message);
				Program.Error(feedbackMessage);

			}

			return feedbackMessage;
		}

		internal static string GetDeviceDetails(DeviceDiscovery info)
		{
			return new StringBuilder()
				.AppendLine("DISCOVERY")
				.AppendFormat("\tname       = {0}", info.DeviceName)
				.AppendLine()
				.AppendFormat("\tmodel      = {0}", info.Model)
				.AppendLine()
				.AppendFormat("\tversion    = {0}", info.Version)
				.AppendLine()
				.AppendFormat("\tserial     = {0}", info.SerialNumber)
				.AppendLine()
				.AppendFormat("\tMAC        = {0}", info.Mac)
				.AppendLine()
				.AppendFormat("\tencryption = {0}", info.EncryptionMode)
				.AppendLine()
				.ToString();
		}

    }
}
