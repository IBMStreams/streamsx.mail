// *******************************************************************************
// * Copyright (C) 2019 International Business Machines Corporation
// * All Rights Reserved
// *******************************************************************************
package com.ibm.streamsx.mail;


import java.util.Properties;
import java.util.Set;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.TupleAttribute;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.metrics.Metric;
import com.ibm.streams.operator.metrics.Metric.Kind;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.state.ConsistentRegionContext;

/**
 * Class for an operator that consumes tuples and does not produce an output stream. 
 * This pattern supports a number of input streams and no output streams. 
 * <P>
 * The following event methods from the Operator interface can be called:
 * </p>
 * <ul>
 * <li><code>initialize()</code> to perform operator initialization</li>
 * <li>allPortsReady() notification indicates the operator's ports are ready to process and submit tuples</li> 
 * <li>process() handles a tuple arriving on an input port 
 * <li>processPuncuation() handles a punctuation mark arriving on an input port 
 * <li>shutdown() to shutdown the operator. A shutdown request may occur at any time, 
 * such as a request to stop a PE or cancel a job. 
 * Thus the shutdown() may occur while the operator is processing tuples, punctuation marks, 
 * or even during port ready notification.</li>
 * </ul>
 * <p>With the exception of operator initialization, all the other events may occur concurrently with each other, 
 * which lead to these methods being called concurrently by different threads.</p> 
 */
@PrimitiveOperator(name="SendMail", description=SendMail.DESCRIPTION)
@Libraries({"lib/*"})
@InputPorts(
	{
		@InputPortSet(
			description="Every tuple received from this port triggers an outgoing e-mail. The attributes if the input stream "
				+ "may be used as parameters and content for the e-mail to sent. These attributes are configured through "
				+ "the parameters `toAttribute`, `ccAttribute`, `bccAttribute`, `subject` and `content`.",
			cardinality=1,
			optional=false,
			windowingMode=WindowMode.NonWindowed,
			windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious
		)
	}
)
public class SendMail extends AbstractOperator {

	public static final String DESCRIPTION = ""
			+ "This operator sends out an e-mail, when a tuple arrives at the input port. The operator has no output port.\\n"
			+ "This operator supports the following encryption methods:\\n"
			+ "* NONE: no encryption\\n"
			+ "* STARTTLS: The client sends the command STARTLS and the communication switches to TLS encryption. The server must support STARTTLS.\\n"
			+ "* TLS: The client requires TLS connection to the server.\\n"
			+ "The trust store may be changed with the appropriate System property like:\\n"
			+ "    vmArg: ' -Djavax.net.ssl.trustStore=mykeystore'\\n\\n"
			+ "The operatoy may send messages to the operator log if something wents wrong during smtp operation. See parmater `enableOperatorLog`. "
			+ "The operator provides authentication to the smtp server if the parameter `password` is provided. Authentication method is LOGIN PLAIN DIGEST-MD5 NTLM "
			+ "This operator should not be placed inside a consistent region.";

	//register trace and log facility
	protected static Logger logger = Logger.getLogger("com.ibm.streams.operator.log." + SendMail.class.getName());
	protected static final Logger tracer = Logger.getLogger(SendMail.class.getName());

	//operator enums
	public enum EncryptionType {
		NONE,
		STARTTLS,
		TLS
	}
	
	//parameter values
	private EncryptionType encryptionType = EncryptionType.NONE;
	private String smtpHost = null;
	private int	smtpPort = -1;
	private String from = null;
	private String username = null;
	private String password = null;
	private String to = null;
	private String cc = null;
	private String bcc = null;
	private TupleAttribute<Tuple, String> toAttribute = null;
	private TupleAttribute<Tuple, String> ccAttribute = null;
	private TupleAttribute<Tuple, String> bccAttribute = null;
	private String[] subject = {"ALERT form Streams !"};
	private String[] content = {" "};
	private boolean enableOperatorLog = true;
	private boolean acceptAllCertificates = false;

	private Metric nEmailFailures;

	//other operator state values
	private Properties mailProperties = new Properties();
	private Session session;

	@Parameter(optional=true, description="Encryption method to be used for the SMTP connection. Default is NONE")
	public void setEncryptionType(EncryptionType encryptionType) {
		this.encryptionType = encryptionType;
	}
	@Parameter(optional=false, description="The SMTP relay host name/address.")
	public void setSmtpHost(String smtpHost) {
		this.smtpHost = smtpHost;
	}
	@Parameter(optional=true, description="The SMTP host port. Defaults to 25 if `encryptionType` is `NONE`, 587 if `encryptionType` "
		+ "is `STARTTLS` and 587 if `EncryptionType` is `TLS`")
	public void setSmtpPort(int smtpPort) {
		this.smtpPort = smtpPort;
	}
	@Parameter(optional=false, description="Email address to use for SMTP MAIL command. This sets the envelope return "
		+ "address. This address should be a valid and active e-mail account.")
	public void setFrom(String from) throws AddressException {
		//check address format
		@SuppressWarnings("unused")
		InternetAddress ia = new InternetAddress(from);
		this.from = from;
	}
	@Parameter(optional=true, description="The user name for SMTP. This parameter is required if the SMTP server requires "
		+ "authorization. The default is the value of parameter from.")
	public void setUsername(String username) {
		this.username = username;
	}
	@Parameter(optional=true, description="The password for the SMTP account")
	public void setPassword(String password) {
		this.password = password;
	}
	@Parameter(optional=true, description="Comma separated list of the to recipients. If this parameter is set, parameter "
		+ "toAttribute is not allowed. One of `to` and `toAttribute` is required.")
	public void setTo(String to) throws AddressException {
		@SuppressWarnings("unused")
		InternetAddress ias[] = InternetAddress.parse(to);
		this.to = to;
	}
	@Parameter(optional=true, description="Comma separated list of the cc recipients. If this parameter is set, parameter "
		+ "`ccAttribute` is not allowed.")
	public void setCc(String cc) throws AddressException {
		@SuppressWarnings("unused")
		InternetAddress ias[] = InternetAddress.parse(cc);
		this.cc = cc;
	}
	@Parameter(optional=true, description="Comma separated list of the bcc recipients. If this parameter is set, parameter `bccAttribute` "
		+ "is not allowed.")
	public void setBcc(String bcc) throws AddressException {
		@SuppressWarnings("unused")
		InternetAddress ias[] = InternetAddress.parse(bcc);
		this.bcc = bcc;
	}
	@Parameter(optional=true, description="The name of the input stream attribute with a comma separated list of the to "
		+ "recipients. If this parameter is set, parameter `to` is not allowed. One of `to` and `toAttribute` is required.")
	public void setToAttribute(TupleAttribute<Tuple, String> toAttribute) {
		 this.toAttribute = toAttribute;
	}
	@Parameter(optional=true, description="The name of the input stream attribute with a comma separated list of the cc "
		+ "recipients. If this parameter is set, parameter `cc` is not allowed.")
	public void setCcAttribute(TupleAttribute<Tuple, String> ccAttribute) {
		this.ccAttribute = ccAttribute;
	}
	@Parameter(optional=true, description="The name of the input stream attribute with a comma separated list of the bcc "
		 + "recipients. If this parameter is set, parameter `bcc` is not allowed.")
	public void setBccAttribute(TupleAttribute<Tuple, String> bccAttribute) {
		this.bccAttribute = bccAttribute;
	}
	@Parameter(optional=true, description="The subject of the message. The subject string of the message is concatenated "
		+ "from the parameter components. If one component equals the name of an input string attribute, the attribute "
		+ "value is taken instead. The default value is 'ALERT form Streams !'")
	public void setSubject(String[] subject) {
		this.subject = subject;
	}
	@Parameter(optional=true, description="Content of the message to send. The content string of the message is concatenated "
		+ "from the parameter components. If one component equals the name of an input string attribute, the attribute "
		+ "value is taken instead. The default is am empty line")
	public void setContent(String[] content) {
		this.content = content;
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

	@CustomMetric(kind = Kind.COUNTER, description ="The number of failed e-mail transmissions.")
	public void setnEmailFailures(Metric nEmailFailures) {
		this.nEmailFailures = nEmailFailures;
	}

	@ContextCheck(compile = true)
	public static void checkInConsistentRegion(OperatorContextChecker checker) {
		ConsistentRegionContext consistentRegionContext = checker.getOperatorContext().getOptionalContext(ConsistentRegionContext.class);
		if(consistentRegionContext != null) {
			checker.setInvalidContext(Messages.getString("CONSISTENT_CHECK_1"), new String[] {SendMail.class.getName()});
		}
	}

	@ContextCheck(compile = true)
	public static void checkMethodParams(OperatorContextChecker occ) {
		occ.checkExcludedParameters("to", "toAttribute");
		occ.checkExcludedParameters("cc", "ccAttribute");
		occ.checkExcludedParameters("bcc", "bccAttribute");
		Set<String> parameterNames = occ.getOperatorContext().getParameterNames();
		if ( ! parameterNames.contains("to") && ! parameterNames.contains("toAttribute")) {
			occ.setInvalidContext(Messages.getString("PARAM_CHECK_1"), new Object[]{"to", "toAttribute"});
		}
	}

	/**
	 * Initialize this operator. Called once before any tuples are processed.
	 * @param context OperatorContext for this operator.
	 * @throws Exception Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void initialize(OperatorContext context) throws Exception {
		// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);

		tracer.trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );

		//set port defaults
		if (smtpPort == -1) {
			switch (encryptionType) {
			case NONE: smtpPort = 25;
				break;
			case STARTTLS: smtpPort = 587;
				break;
			case TLS: smtpPort = 465;
				break;
			}
		}
		//set props from params and log
		String oname = "Operator " + context.getName() + " ";
		System.out.println(oname + "Initialization values:");

		mailProperties.put("mail.smtp.from", from);
		System.out.println(oname + "mail.smtp.from: " + from);
		
		mailProperties.put("mail.smtp.host", smtpHost);
		System.out.println(oname + "mail.smtp.host: " + smtpHost);
		
		mailProperties.put("mail.smtp.port", String.valueOf(smtpPort));
		System.out.println(oname + "mail.smtp.port: " + String.valueOf(smtpPort));
		
		if (username == null) {
			username = from;
		}
		mailProperties.put("mail.smtp.user", username);
		System.out.println(oname + "mail.smtp.user: " + username);
		
		if (password != null) {
			mailProperties.put("mail.smtp.auth", "true");
			System.out.println(oname + "mail.smtp.auth: true");
		}
		
		switch (encryptionType) {
		case NONE: break;
		case STARTTLS:
			mailProperties.put("mail.smtp.starttls.enable", "true");
			System.out.println(oname + "mail.smtp.starttls.enable: true");
			mailProperties.put("mail.smtp.starttls.required", "true");
			System.out.println(oname + "mail.smtp.starttls.required: true");
			//mailProperties.put("mail.smtp.ssl.trust ","*");
			if (acceptAllCertificates) {
				mailProperties.put("mail.smtp.ssl.socketFactory.class", "com.ibm.streamsx.mail.DummySSLSocketFactory");
				//mailProperties.put("mail.smtp.ssl.socketFactory", com.ibm.streamsx.mail.DummySSLSocketFactory.class);
				mailProperties.put("mail.smtp.ssl.socketFactory.fallback", "false");
				System.out.println(oname + "mail.smtp.ssl.socketFactory.class: com.ibm.streamsx.mail.DummySSLSocketFactory");
				System.out.println(oname + "mail.smtp.ssl.socketFactory.fallback: false");
			}
			break;
		case TLS:
			mailProperties.put("mail.smtp.socketFactory.port", String.valueOf(smtpPort));
			System.out.println(oname + "mail.smtp.socketFactory.port: " + String.valueOf(smtpPort));
			mailProperties.put("mail.smtp.ssl.enable",true);
			System.out.println(oname + "mail.smtp.ssl.enable: true");
			//mailProperties.put("mail.smtp.ssl.trust ","*");
			if (acceptAllCertificates) {
				mailProperties.put("mail.smtp.ssl.socketFactory.class", "com.ibm.streamsx.mail.DummySSLSocketFactory");
				//mailProperties.put("mail.smtp.ssl.socketFactory", com.ibm.streamsx.mail.DummySSLSocketFactory().class);
				mailProperties.put("mail.smtp.ssl.socketFactory.fallback", "false");
				System.out.println(oname + "mail.smtp.ssl.socketFactory.class: com.ibm.streamsx.mail.DummySSLSocketFactory");
				System.out.println(oname + "mail.smtp.ssl.socketFactory.fallback: false");
			}
			break;
		}
		System.out.println("mailProperties: " + mailProperties.toString());
		
		if (password == null) {
			session = Session.getInstance(mailProperties);
		} else {
			session = Session.getInstance(mailProperties,
				new Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username, password);
					}
				}
			);
		}
	}

	/**
	 * Process an incoming tuple that arrived on the specified port.
	 * @param stream Port the tuple is arriving on.
	 * @param tuple Object representing the incoming tuple.
	 * @throws MessagingException 
	 * @throws AddressException 
	 * @throws Exception Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void process(StreamingInput<Tuple> stream, Tuple tuple) {
		Message message=new MimeMessage(session);
		String toString = null;
		if (to != null) {
			toString = to;
		} else {
			toString = toAttribute.getValue(tuple);
		}
		String ccString = "";
		if (cc != null) {
			ccString =  cc;
		} else if ( ccAttribute != null) {
			ccString = ccAttribute.getValue(tuple);
		}
		String bccString = "";
		if (bcc != null) {
			bccString =  bcc;
		} else if ( bccAttribute != null) {
			bccString = bccAttribute.getValue(tuple);
		}
		StringBuffer subjectbuff = new StringBuffer();
		for(String s:subject){
			if(tuple.getStreamSchema().getAttributeIndex(s) < 0){
				subjectbuff.append(s);
			}else{
				subjectbuff.append(tuple.getObject(s).toString());
			}
		}
		StringBuffer contentbuff = new StringBuffer();
		for(String s:content){
			if(tuple.getStreamSchema().getAttributeIndex(s) < 0){
				contentbuff.append(s);
			}else{
				contentbuff.append(tuple.getObject(s).toString());
			}
		}
		try {
			message.setFrom(new InternetAddress(from));
			message.setRecipients(Message.RecipientType.TO,  InternetAddress.parse(toString));
			message.setRecipients(Message.RecipientType.CC,  InternetAddress.parse(ccString));
			message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccString));
			message.setSubject(subjectbuff.toString());
			message.setText(contentbuff.toString());
			Transport transport = session.getTransport();
			URLName un = transport.getURLName();
			if (tracer.isDebugEnabled())
				tracer.debug("send message URLname: " + un.toString());
			Transport.send(message);
			if (enableOperatorLog)
				if (logger.isDebugEnabled())
					logger.debug(Messages.getString("LOG_INFO_MAIL_SEND", new Object[]{toString, subjectbuff.toString()}));
		} catch (AddressException e) {
			nEmailFailures.increment();
			tracer.error("Wrong e-mail address", e);
			if (enableOperatorLog)
				logger.error(Messages.getString("LOG_ERROR_WRONG_ADDRESS", new Object[]{smtpHost, smtpPort, toString}));
		} catch (MessagingException e) {
			nEmailFailures.increment();
			tracer.error("Can not send e-mail", e);
			if (enableOperatorLog)
				logger.error(Messages.getString("LOG_ERROR_CAN_NOT_SEND", new Object[]{smtpHost, smtpPort, toString}));
		}
	}

	/**
	 * Shutdown this operator.
	 * @throws Exception Operator failure, will cause the enclosing PE to terminate.
	 */
	@Override
	public synchronized void shutdown() throws Exception {
		OperatorContext context = getOperatorContext();
		tracer.trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
		// Must call super.shutdown()
		super.shutdown();
	}
}
