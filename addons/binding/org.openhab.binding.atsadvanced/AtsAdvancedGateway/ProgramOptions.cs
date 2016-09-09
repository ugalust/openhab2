namespace AtsAdvancedTest
{
    using System;
    using System.Diagnostics;
    using System.Net;
    using AtsAdvancedTest.Parsers;

    internal class ProgramOptions
    {
        private readonly bool help;
//
//        private TestActionType testActionType;
//
//        private ProgramMode programMode;

        /// <summary>
        /// The port number on which the server is listening.
        /// </summary>
        private int port = -1;
		private string driverpath;

        /// <summary>
        /// Initializes a new instance of the <see cref="OptionsParser"/> class.
        /// </summary>
        /// <param name="args">The command line arguments.</param>
        internal ProgramOptions(params string[] args)
        {
            var optionsReader = new OptionsReader(0, args);
            if (optionsReader.IsHelp())
            {
                this.help = true;
                return;
            }

            var optionParser = new ParserOptionType();
            while (!optionsReader.IsParsed())
            {
                var optionName = optionsReader.Next();
                var option = optionParser.Parse(optionName);
                switch (option)
                {
                    case OptionType.Port:
                        optionsReader.ReadOption(optionName, -1, new ParserPort(), ref this.port);
                        break;

				case OptionType.Driver:
					optionsReader.ReadOption(optionName, null, new ParserDriverFile(), ref this.driverpath);
					break;

                    default:
                        throw new NotImplementedException(string.Format("Option '{0}' is not implemented yet.", optionName));
                }
            }
        }

        internal bool Help
        {
            get
            {
                return this.help;
            }
        }

        internal int Port
        {
            get
            {
                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");
                if (this.port == -1)
                {
                    throw new ArgumentException("Missing option '--port' in which the server is being started.");
                }

                Debug.Assert((this.port >= IPEndPoint.MinPort) && (this.port <= IPEndPoint.MaxPort), "Invalid port number.");
                return this.port;
            }
        }
//
//        internal ProgramMode Mode
//        {
//            get
//            {
//                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");
//                if (this.programMode == ProgramMode.None)
//                {
//                    throw new ArgumentException("Missing option '--mode' that specify connecting method to the device.");
//                }
//
//                return this.programMode;
//            }
//        }
//
//        internal TestActionType ActionType
//        {
//            get
//            {
//                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");
//                if (this.testActionType == TestActionType.None)
//                {
//                    throw new ArgumentException("Missing option '--test' that specify operations to perform on connected device.");
//                }
//
//                return this.testActionType;
//            }
//        }
//
//        internal int Timeout
//        {
//            get
//            {
//                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");
//                if (this.timeout == -1)
//                {
//                    this.timeout = 5000;
//                    Program.Warn(string.Format("Using default timeout value {0}.", this.timeout));
//                }
//
//                Debug.Assert(this.timeout > 0, "Invalid timeout value.");
//                return this.timeout;
//            }
//        }
//
//        internal int Retries
//        {
//            get
//            {
//                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");
//                if (this.retries == -1)
//                {
//                    this.retries = 0;
//                    Program.Warn(string.Format("Using default retries value {0}.", this.retries));
//                }
//
//                Debug.Assert(this.retries >= 0, "Invalid retries value.");
//                return this.retries;
//            }
//        }
//
//        internal string Pin
//        {
//            get
//            {
//                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");
//                if (this.pin == null)
//                {
//                    this.pin = "1278";
//                    Program.Warn(string.Format("Using default PIN value '{0}'.", this.pin));
//                }
//
//                Debug.Assert(this.timeout > 0, "Invalid timeout value.");
//                return this.pin;
//            }
//        }
//
		internal string DriverPath
        {
            get
            {
                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");

				return this.driverpath;
            }
        }
//
//
//        internal bool Upload
//        {
//            get
//            {
//                return this.upload;
//            }
//        }
//
//        internal bool Download
//        {
//            get
//            {
//                return this.download;
//            }
//        }
//
//        internal bool Control
//        {
//            get
//            {
//                return this.control;
//            }
//        }
//
//        internal bool Monitor
//        {
//            get
//            {
//                return this.monitor;
//            }
//        }
//
//        internal bool Diagnostics
//        {
//            get
//            {
//                return this.diagnostics;
//            }
//        }
//
//        internal bool LogView
//        {
//            get
//            {
//                return this.logview;
//            }
//        }
//
//        internal int HeartBeat
//        {
//            get
//            {
//                return this.heartbeat;
//            }
//        }
//
//        internal int DueTime
//        {
//            get
//            {
//                return this.duetime;
//            }
//        }
//
//        internal string SerialPort
//        {
//            get
//            {
//                return this.serialPort;
//            }
//        }
//
//        internal IPAddress HostAddress
//        {
//            get
//            {
//                Debug.Assert(!this.help, "Unexpected access to the property while help option is specified.");
//                if (this.hostAddress == null)
//                {
//                    this.hostAddress = new IPAddress(0x7F000001);
//                    Program.Warn(string.Format("Using default host address value '{0}'.", this.hostAddress));
//                }
//                return this.hostAddress;
//            }
//        }

        internal void Usage()
        {
            Program.Usage(
                "[options]",
                "-h, --help, -?           This help string.",
//                "-m, --mode <mode>        Connecting mode.",
//                "-a, --test <action>      Action to be performed when connection is established.",
				"-p, --port <port>        The port number on which the web services are listening on.");
//                "-t, --timeout <timeout>  The timout value for sending commands.",
//                "-r, --retries <retries>  The number of retries for sending commands.",
//                "-d, --duetime <time>     The waiting time to logoff command in LOGIN action.",
//                "-c, --com <name>         The name of the serial port.",
//                "--host <address>         The IP address of the panel.",
//                "--heartbeat <period>     The time between consecutive automatic ping commands.",
//                "--pin <pin>              The user PIN for login purposes.",
//                "--password <password>    The password to create master encryption key.",
//                "--upload                 The switch to login for UPLOAD purposes.",
//                "--download               The switch to login for DOWNLOAD purposes.",
//                "--control                The switch to login for CONTROL purposes.",
//                "--monitor                The switch to login for MONITOR purposes.",
//                "--diagnostics            The switch to login for DIAGNOSTICS purposes.",
//                "--logview                The switch to login for LOGVIEW purposes.",
//                string.Empty,
//                "Modes:",
//                "\tTCP-SERVER The application starts TCP server on specified port.",
//                "\tTCP-CLIENT The application starts action over TCP client socket.",
//                "\tCOM-CLIENT The application starts action over serial port.",
//                string.Empty,
//                "Actions:",
//                "\tDISCOVERY  Simple discovery procedure.",
//                "\tLOGIN      Simple discovery procedure followed by:",
//                "\t           1. optional negotiation of session encryption key",
//                "\t           2. login to the panel for specific purposes",
//                "\t           3. logout from the panel.",
//                "\tNAMES      Login + reading names of all objects in the panel",
//                "\tDRYSET     Login + reading events that prevent from setting");
        }
    }
}
