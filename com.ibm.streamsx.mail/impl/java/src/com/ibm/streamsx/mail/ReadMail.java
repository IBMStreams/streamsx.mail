// *******************************************************************************
// * Copyright (C) 2011, 2019 International Business Machines Corporation
// * All Rights Reserved
// *******************************************************************************
package com.ibm.streamsx.mail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;

import org.apache.log4j.Logger;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingData;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.state.ConsistentRegionContext;
import com.ibm.streams.operator.types.RString;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

@PrimitiveOperator(name="ReadMail", description=ReadMail.DESCRIPTION)
@Libraries({"lib/*"})
@OutputPorts(
		{
			@OutputPortSet(
					description="Port that produces tuples for each received e-mail. The schema must be of type [com.ibm.streamsx.mail::Mail|com.ibm.streamsx.mail::Mail] "
							+ "Every message part of type `text/plain` and `text/HTML` is converted to an `rstring` element of the attribute `content`."
							+ "Multipart messages are supported.",
					cardinality=1,
					optional=false,
					windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating
			),
			@OutputPortSet(
					description="Optional error output port. The error output stream must suport at least the attributes "
							+ "of type [com.ibm.streamsx.mail::RuntimeError|com.ibm.streamsx.mail::RuntimeError]",
					cardinality=1,
					optional=true,
					windowPunctuationOutputMode=WindowPunctuationOutputMode.Free
			)
		}
)
public class ReadMail extends MailOperator {
	
	public static final String DESCRIPTION=""
			+ "The ReadMail operator reads e-mails from an imap server and ingests one tuple for each received e-mail."
			+ "This operator supports the following encryption methods:\\n"
			+ "* NONE: no encryption\\n"
			+ "* STARTTLS: The client sends the command STARTLS and the communication switches to TLS encryption. The operator requires the STARTTLS method and the server must support it.\\n"
			+ "* TLS: The client requires TLS connection to the server.\\n"
			+ "The trust store may be changed with the appropriate System property like:\\n"
			+ "    vmArg: ' -Djavax.net.ssl.trustStore=mykeystore'\\n\\n"
			+ "The operatoy may send messages to the operator log if something wents wrong during imap operation. See parmater `enableOperatorLog`. "
			+ "Each scan cycle is finalized with a Window punctuation marker."
			+ "The operator provides authentication to the imap server. The parameter `password` is required. "
			+ "This operator should not be placed inside a consistent region.";

	//required out schema
	private static final String OUTPUT_SCHEMA   = "tuple<rstring to,rstring replyto,rstring from,int64 date,rstring subject,list<rstring> contentType,list<rstring> content>";
	private static final String OUTPUT_SCHEMA_E = "tuple<rstring to,rstring replyto,rstring from,int64 date,rstring subject,list<rstring> contentType,list<rstring> content,rstring error>";
	
	//register trace and log facility
	private static Logger logger = Logger.getLogger("com.ibm.streams.operator.log." + ReadMail.class.getName());
	private static final Logger tracer = Logger.getLogger(ReadMail.class.getName());

	//parameter values
	private EncryptionType encryptionType = EncryptionType.TLS;
	private String imapHost = null;
	private int    imapPort = -1;
	private String username = null;
	private String password = null;
	private String folder = "INBOX";
	private boolean readNewMailsOnly = false;
	private boolean deleteAfterRead = false;
	private long period = 60 * 1000; //in milliseconds
	private Long initDelay = null; //in milliseconds
	private long iterations = -1;
	private boolean enableOperatorLog = true;
	private boolean acceptAllCertificates = false;

	private Metric nEmailFailures;
	private Metric nIgnoredMessageParts;
	private Metric nScanCycles;

	//other operator state values
	private boolean hasErrorAttribute = false;
	private boolean isShutdown = false;
	private Properties properties = null;
	private Thread processThread = null;
	private Session session = null;
	private Store store = null;

	//Parameters
	@Parameter(optional=true, description="Encryption method to be used for the IMAP connection. Default is TLS")
	public void setEncryptionType(EncryptionType encryptionType) {
		this.encryptionType = encryptionType;
	}
	@Parameter(optional=false, description="The IMAP server host name/address.")
	public void setImapHost(String imapHost) {
		this.imapHost = imapHost;
	}
	@Parameter(optional=true, description="The IMAP host port. Defaults to 143 if `encryptionType` is `NONE` "
		+ "or `STARTTLS` and 993 if `EncryptionType` is `TLS`")
	public void setImapPort(int imapPort) {
		this.imapPort = imapPort;
	}
	@Parameter(optional=false, description="The user name for IMAP server.")
	public void setUsername(String username) {
		this.username = username;
	}
	@Parameter(optional=false, description="The password for the IMAP server")
	public void setPassword(String password) {
		this.password = password;
	}
	@Parameter(optional=true, description="Mailbox folder to read from. Default is INBOX")
	public void setFolder(String folder) {
		this.folder = folder;
	}
	@Parameter(optional=true, description="If true the ReadMail operator produces tuples for new e-mails only. Mails which "
			+ "have been seen before are ignored. Default is false.")
	public void setReadNewMailsOnly(boolean readNewMailsOnly) {
		this.readNewMailsOnly = readNewMailsOnly;
	}
	@Parameter(optional=true, description="If true the ReadMail operator deletes the e-mail after the tuple was produced. "
			+ "Default is false.")
	public void setDeleteAfterRead(boolean deleteAfterRead) {
		this.deleteAfterRead = deleteAfterRead;
	}
	@Parameter(optional=true, description="Specifies the time interval between successive tuple submissions, in seconds. "
			+ "When the parameter is not specified, the default value is 60.0.")
	public void setPeriod(double period) {
		this.period = Math.round(period * 1000);
	}
	@Parameter(optional=true, description="Specifies the number of seconds that the operator delays before starting to "
			+ "read e-mails.")
	public void setInitDelay(double initDelay) {
		long tt = Math.round(initDelay * 1000);
		this.initDelay = new Long(tt);
	}
	@Parameter(optional=true, description="Specifies the number of e-mail scan operations to be produced by the ReadMail operator. "
			+ "When the parameter is not specified or the has a negative value, the operator scans e-mails until the "
			+ "application is shut down.")
	public void setIterations(long iterations) {
		this.iterations = iterations;
	}
	@Parameter(optional=true, description="If enabled, every sucessfully smtp opoeration triggers an debug level entry in "
			+ "the operator log and every smtp failure triggers an error level entry in the operator log. Default is true.")
	public void setEnableOperatorLog(boolean enableOperatorLog) {
		this.enableOperatorLog = enableOperatorLog;
	}
	@Parameter(optional=true, description="Accept all SSL certificates, this means the server certificate is not checked. "
			+ "Setting this option will allow potentially insecure connections. Default is false.")
	public void setAcceptAllCertificates(boolean acceptAllCertificates) {
		this.acceptAllCertificates = acceptAllCertificates;
	}

	//Metrics
	@CustomMetric(kind=Kind.COUNTER, description="The number of e-mail scan operations performed.")
	public void setnScanCycles(Metric nScanCycles) {
		this.nScanCycles = nScanCycles;
	}
	@CustomMetric(kind=Kind.COUNTER, description="The number of ignored message parts due to a not supportet content type")
	public void setnIgnoredMessageParts(Metric nIgnoredMessageParts) {
		this.nIgnoredMessageParts = nIgnoredMessageParts;
	}
	@CustomMetric(kind=Kind.COUNTER, description="The number of failed e-mail operations.")
	public void setnEmailFailures(Metric nEmailFailures) {
		this.nEmailFailures = nEmailFailures;
	}

	//Context checks
	@ContextCheck(compile = true)
	public static void checkInConsistentRegion(OperatorContextChecker checker) {
		ConsistentRegionContext consistentRegionContext = checker.getOperatorContext().getOptionalContext(ConsistentRegionContext.class);
		if(consistentRegionContext != null) {
			checker.setInvalidContext(Messages.getString("CONSISTENT_CHECK_1"), new String[] {ReadMail.class.getName()});
		}
	}

	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
		// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);
		tracer.trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );

		//set port defaults
		if (imapPort == -1) {
			switch (encryptionType) {
			case NONE: imapPort = 143;
				break;
			case STARTTLS: imapPort = 143;
				break;
			case TLS: imapPort = 993;
				break;
			}
		}

		String oname = "Operator " + context.getName() + " ";
		System.out.println(oname + "Initialization values:");

		//check output schema
		StreamingOutput<OutputTuple> so = getOutput(0);
		StreamSchema ss = so.getStreamSchema();
		String spltype = ss.getLanguageType();
		System.out.println("Output schema: " + spltype);
		if (spltype.equals(OUTPUT_SCHEMA_E)) {
			hasErrorAttribute = true;
		} else if ( ! spltype.equals(OUTPUT_SCHEMA)) {
			throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR", (Object[]) new String[]{OUTPUT_SCHEMA, OUTPUT_SCHEMA_E}));
		}
		checkErrorPortSchema(context, 1);
		
		System.out.println(oname + "encryptionType: " + encryptionType.name());
		System.out.println(oname + "username: " + username);
		System.out.println(oname + "folder: " + folder);
		System.out.println(oname + "readNewMailsOnly: " + Boolean.toString(readNewMailsOnly));
		System.out.println(oname + "deleteAfterRead: " + Boolean.toString(deleteAfterRead));
		System.out.println(oname + "period: " + Double.toString(period));
		if (initDelay != null) System.out.println(oname + "initDelay: " + initDelay.toString());
		System.out.println(oname + "iterations: " + Long.toString(iterations));
		System.out.println(oname + "enableOperatorLog: " + Boolean.toString(enableOperatorLog));
		System.out.println(oname + "acceptAllCertificates: " + Boolean.toString(acceptAllCertificates));

		properties = new Properties();
		if ((encryptionType == EncryptionType.NONE) || (encryptionType == EncryptionType.STARTTLS)) {
			properties.put("mail.store.protocol", "imap");
			properties.put("mail.imap.host", imapHost);
			properties.put("mail.imap.port", imapPort);
			if (encryptionType == EncryptionType.STARTTLS) {
				properties.put("mail.imap.starttls.enable", "true");
				properties.put("mail.imap.starttls.required", "true");
			}
		} else {
			properties.put("mail.store.protocol", "imaps");
			properties.put("mail.imaps.host", imapHost);
			properties.put("mail.imaps.port", imapPort);
			if (acceptAllCertificates) {
				properties.put("mail.imaps.ssl.socketFactory.class", "com.ibm.streamsx.mail.DummySSLSocketFactory");
				properties.put("mail.imaps.ssl.socketFactory.fallback", "false");
			}
		}
		System.out.println("properties: " + properties.toString());
		
		session = Session.getDefaultInstance(properties);
		store = session.getStore();

		/*
		 * Create the thread for producing tuples. 
		 * The thread is created at initialize time but started.
		 * The thread will be started by allPortsReady().
		 */
		processThread = getOperatorContext().getThreadFactory().newThread(
				new Runnable() {
					@Override
					public void run() {
						try {
							if ((initDelay != null) && (initDelay.longValue() > 0)) {
								tracer.debug("Enter initial delay");
								Thread.sleep(initDelay.longValue());
							}
							long counter = 1; //set to positive value for infinite scan
							if (iterations >= 0)
								counter = iterations;
							
							while ((! isShutdown) && (counter > 0)) {
								long step = nScanCycles.getValue();
								if (step > 0)
									Thread.sleep(period);
								if (tracer.isDebugEnabled())
									tracer.debug("Enter e-mail scan:" + new Long(step).toString());
								scan();
								nScanCycles.increment();
								if (iterations >= 0) //no infinite scan
									counter--;
							}
						} catch (InterruptedException e) {
							tracer.warn("Thread.sleep was interrupted: " + e.getMessage(), e);
						} catch (TupleSendException e) {
							tracer.error("Run end TupleSendException", e);
							throw new RuntimeException("TupleSendException: " + e.getMessage(), e);
						} catch (FlagNotSupportedException e) {
							nEmailFailures.increment();
							tracer.error("Run end FlagNotSupportedException: " + e.getMessage(), e);
						}
					}
				}
		);

		/*
		 * Set the thread not to be a daemon to ensure that the SPL runtime
		 * will wait for the thread to complete before determining the
		 * operator is complete.
		 */
		processThread.setDaemon(false);
	}
	

	/**
	 * Notification that initialization is complete and all input and output ports 
	 * are connected and ready to receive and submit tuples.
	 * @throws Exception Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void allPortsReady() throws Exception {
		OperatorContext context = getOperatorContext();
		tracer.trace("Operator " + context.getName() + " all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
		// Start a thread for producing tuples because operator 
		// implementations must not block and must return control to the caller.
		processThread.start();
	}

	/**
	 * Shutdown this operator, which will interrupt the thread
	 * @throws Exception - Operator failure, will cause the enclosing PE to terminate.
	 */
	public synchronized void shutdown() throws Exception {
		isShutdown = true;
		OperatorContext context = getOperatorContext();
		tracer.trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
		// Must call super.shutdown()
		super.shutdown();
	}

	/**
	 * Scans an e-mail folder
	 * @throws TupleSendException - will cause the enclosing PE to terminate.
	 * @throws FlagNotSupportedException - will cause the enclosing PE to terminate.
	 */
	private void scan() throws TupleSendException, FlagNotSupportedException {
		Folder emailFolder = null;
		try {
			store.connect(username, password);
			emailFolder = store.getFolder(folder);
			emailFolder.open(Folder.READ_WRITE);
			if (readNewMailsOnly) {
				Flags pflags = emailFolder.getPermanentFlags();
				Flag[] sysflags = pflags.getSystemFlags();
				boolean supportsSeen = false;
				for (int i = 0; i < sysflags.length; i++) {
					if (sysflags[i].equals(Flags.Flag.SEEN))
						supportsSeen = true;
				}
				if (! supportsSeen)
					throw new FlagNotSupportedException("SEEN not supported in folder: " + folder + " flags: " + pflags.toString());
			}
			Message[] messages = null;
			if (readNewMailsOnly) {
				Flags seen = new Flags(Flags.Flag.SEEN);
				FlagTerm unseenFlagTerm = new FlagTerm(seen, false);
				messages = emailFolder.search(unseenFlagTerm);
			} else {
				messages = emailFolder.getMessages();
			}

			processMessages(messages);

		} catch (MessagingException e) {
			tracer.error("MessagingException: " + e.getMessage(), e);
			nEmailFailures.increment();
			sendErrorTuple("MessagingException: ", e);
			if (enableOperatorLog)
				logger.error(Messages.getString("LOG_ERROR_RECEIVE", new Object[]{imapHost, imapPort, username, folder}));
		} catch (IOException e) {
			tracer.error("IOException: " + e.getMessage(), e);
			nEmailFailures.increment();
			sendErrorTuple("IOException : ", e);
			if (enableOperatorLog)
				logger.error(Messages.getString("LOG_ERROR_RECEIVE", new Object[]{imapHost, imapPort, username, folder}));
		} finally {
			if (emailFolder != null)
				try {
					emailFolder.close(true);
					store.close();
				} catch (MessagingException e) {
					tracer.error("MessagingException: " + e.getMessage(), e);
					sendErrorTuple("MessagingException: ", e);
				}
		}
	}

	/**
	 * Sends an output tuple for every message in input array
	 * @param messages - the array with the e-mails to send
	 * @throws MessagingException
	 * @throws IOException
	 * @throws TupleSendException
	 */
	private void processMessages(Message[] messages) throws MessagingException, IOException, TupleSendException {
		Integer nc = new Integer(messages.length);
		tracer.debug(nc.toString() + " messages received from folder: " + folder);

		final StreamingOutput<OutputTuple> out = getOutput(0);
		for(Message msg:messages){
			OutputTuple tuple= out.newTuple();
			//from
			StringBuffer fromAddr = makeAddressList(msg.getFrom());
			tuple.setString("from", fromAddr.toString());
			//REPLY TO
			StringBuffer replTo = makeAddressList(msg.getReplyTo());
			tuple.setString("replyto", replTo.toString());
			//to
			StringBuffer to = makeAddressList(msg.getRecipients(Message.RecipientType.TO));
			tuple.setString("to", to.toString());
			//subject
			String subjstr = msg.getSubject();
			if (subjstr != null) tuple.setString("subject", subjstr);
			//date
			Date dateobj = msg.getSentDate();
			long date = 0;
			String datestr = "";
			if (dateobj != null) {
				datestr= dateobj.toString();
				date = dateobj.getTime() / 1000; //getTime is in msec
			}
			tuple.setLong("date", date);
			//mime type and content
			ArrayList<RString> mimeTypeList = new ArrayList<RString>();
			ArrayList<RString> contentList = new ArrayList<RString>();
			StringBuffer contentTypeErrors = new StringBuffer();
			parseContent(msg, contentList, mimeTypeList, fromAddr.toString(), datestr, contentTypeErrors);
			tuple.setList("contentType", mimeTypeList);
			tuple.setList("content", contentList);
			if (hasErrorAttribute && ! contentTypeErrors.toString().isEmpty())
				tuple.setString("error", contentTypeErrors.toString());

			try {
				out.submit(tuple);
				if ( ! contentTypeErrors.toString().isEmpty()) {
					sendErrorTuple(contentTypeErrors.toString(), "");
				}
			} catch (Exception e) {
				tracer.error("Can not send tuple", e);
				throw new TupleSendException("Can not send tuple");
			}
			
			if (deleteAfterRead) {
				msg.setFlag(Flags.Flag.DELETED, true);
			}
		}
		StreamingData.Punctuation mark = Punctuation.WINDOW_MARKER;
		try {
			out.punctuate(mark);
		} catch (Exception e) {
			tracer.error("Can not send Window Marker", e);
			throw new TupleSendException("Can not send Window Marker");
		}

	}
	
	/**
	 * Parse one message part. Works recursive on multipart messages
	 * @param part - the part to parse
	 * @param content - the output content
	 * @param mimeType - the output mime type of the message part
	 * @param from - input from address
	 * @param date - input message date
	 * @param contentTypeErrors - the output for the detected content type errors
	 * @throws MessagingException
	 * @throws IOException
	 */
	private void parseContent(Part part, List<RString> content, List<RString> mimeType, String from, String date, StringBuffer contentTypeErrors) throws MessagingException, IOException {
		if (part.isMimeType("text/plain") || part.isMimeType("text/HTML")) {
			String contpart = (String)part.getContent();
			if (contpart == null) contpart = "";
			RString contpartR = new RString(contpart);
			content.add(contpartR);
			String mimePart = part.getContentType();
			if (mimePart == null) mimePart = "";
			RString mimePartR = new RString(mimePart);
			mimeType.add(mimePartR);
		} else if(part.isMimeType("multipart/*")) {
			Multipart mp = (Multipart)part.getContent();
			int count = mp.getCount();
			for (int i = 0; i < count; i++) {
				parseContent(mp.getBodyPart(i), content, mimeType, from, date, contentTypeErrors);
			}
		} else {
			nIgnoredMessageParts.increment();
			String cntType = part.getContentType();
			if (cntType == null) cntType = "null";
			String message = "Content type: " + cntType + " is not supported/ignored in email imapHost: " + imapHost + ":" + Integer.toString(imapPort) + " from: " + from + " date: " + date;
			contentTypeErrors.append(message);
			tracer.warn(message);
			if (enableOperatorLog)
				logger.warn(Messages.getString("LOG_WARN_MIME_TYPE", new Object[]{cntType, imapHost, imapPort, from, date}));
		}
	}

	/**
	 * Make a comma separated list from address array
	 * @param - the input address array
	 */
	private StringBuffer makeAddressList(Address[] addresses) {
		StringBuffer sb = new StringBuffer();
		if (addresses != null) {
			int addressesLength = addresses.length;
			for(int idx = 0; idx < addressesLength; idx++){
				sb.append(addresses[idx].toString());
				if((idx + 1) < addressesLength)
					sb.append(",");
			}
		}
		return sb;
	}

}
