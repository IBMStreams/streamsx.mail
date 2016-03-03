// *******************************************************************************
// * Copyright (C) 2011, 2016 International Business Machines Corporation
// * All Rights Reserved
// *******************************************************************************
package com.ibm.streamsx.mail;


import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;

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
@PrimitiveOperator(name="SendMail", namespace="com.ibm.streamsx.mail",
description="Java Operator SendMail")
@Libraries({"opt/downloaded/*"})
@InputPorts({@InputPortSet(description="Port that ingests tuples", cardinality=1, optional=false, windowingMode=WindowMode.NonWindowed, windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious), @InputPortSet(description="Optional input ports", optional=true, windowingMode=WindowMode.NonWindowed, windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious)})
public class SendMail extends MailOperator {
	
	protected int batch=1;
	protected String to;
	protected String cc="";
	protected String bcc="";
	protected String authentication="TLS";
	protected String hostname="smtp.gmail.com";
	protected int hostport=587;
	protected String username;
	protected String password;
	protected String[] subject={"ALERT form Streams !"};
	protected String[] content={"the message content"};

	@Parameter( description="number of messages to wait before sending",name="batch", optional=true)
	public void setBatch(int batch) {
	    this.batch = batch;
	}
	public int getBatch() {
	    return batch;
	}
	@Parameter( description="list of the to recipients",name="to", optional=false)
	public void setTo(String to) {
	    this.to = to;
	}
	public String getTo() {
	    return to;
	}
	@Parameter( description="list of the cc recipients",name="cc", optional=true)
	public void setCc(String cc) {
	    this.cc = cc;
	}
	public String getCc() {
	    return cc;
	}
	@Parameter( description="list of the bcc recipients",name="bcc", optional=true)
	public void setBcc(String bcc) {
	    this.bcc = bcc;
	}
	public String getBcc() {
	    return bcc;
	}
	@Parameter( description="The subject",name="subject", optional=false)
	public void setSubject(String[] subject) {
	    this.subject = subject;
	}
	public String[] getSubject() {
	    return subject;
	}
	@Parameter( description="Content of the message to send",name="content", optional=false)
	public void setContent(String[] content) {
	    this.content = content;
	}
	public String[] getContent() {
	    return content;
	}

	
	@Parameter( description="username for the connection",name="username", optional=false)
	public void setUsername(String username) {
	    this.username = username;
	}
	public String getUsername() {
	    return username;
	}
	@Parameter( description="password ",name="password", optional=false)
	public void setPassword(String password) {
	    this.password = password;
	}
	public String getPassword() {
	    return password;
	}
	@Parameter( description="Authentication method to use : NONE, TLS, SSL",name="authentication", optional=false)
	public void setAuthentication(String authentication) {
	    this.authentication = authentication;
	}
	public String getAuthentication() {
	    return authentication;
	}
	@Parameter( description="SMTP host name",name="hostname", optional=false)
	public void setSmtpHostname(String hostname) {
	    this.hostname = hostname;
	}
	public String getSmtpHostname() {
	    return hostname;
	}
	@Parameter( description="SMTP host port",name="hostport", optional=false)
	public void setSmtpHostPort(int hostport) {
	    this.hostport = hostport;
	}
	public int getSmtpHostPort() {
	    return hostport;
	}
	
	/**
     * Initialize this operator. Called once before any tuples are processed.
     * @param context OperatorContext for this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
    	// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
		if(authentication.equalsIgnoreCase("NONE")){
//			mailProperties.put("mail.smtp.host", "smtp.gmail.com");
//			mailProperties.put("mail.smtp.port", "587");
		}else if(authentication.equalsIgnoreCase("TLS")){
			mailProperties.put("mail.smtp.auth", "true");
			mailProperties.put("mail.smtp.starttls.enable", "true");
			mailProperties.put("mail.smtp.host", hostname);
			mailProperties.put("mail.smtp.port", String.valueOf(hostport));
		}else if(authentication.equalsIgnoreCase("SSL")){
			mailProperties.put("mail.smtp.auth", "true");
			mailProperties.put("mail.smtp.socketFactory.port", String.valueOf(hostport));
			mailProperties.put("mail.smtp.socketFactory.class","javax.net.ssl.SSLSocketFactory");
			mailProperties.put("mail.smtp.host", hostname);
			mailProperties.put("mail.smtp.port", String.valueOf(hostport));
		}
		session = Session.getInstance(mailProperties,
				  new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username, password);
					}
				  });
	}

    /**
     * Notification that initialization is complete and all input and output ports 
     * are connected and ready to receive and submit tuples.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void allPortsReady() throws Exception {
    	// This method is commonly used by source operators. 
    	// Operators that process incoming tuples generally do not need this notification. 
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
    }

    /**
     * Process an incoming tuple that arrived on the specified port.
     * @param stream Port the tuple is arriving on.
     * @param tuple Object representing the incoming tuple.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public void process(StreamingInput<Tuple> stream, Tuple tuple)
            throws Exception {
        Message message=new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setRecipients(Message.RecipientType.CC,  InternetAddress.parse(cc));
        message.setRecipients(Message.RecipientType.BCC,  InternetAddress.parse(bcc));
        StringBuffer sb = new StringBuffer();
        for(String s:subject){
        	if(tuple.getStreamSchema().getAttributeIndex(s) < 0){
        		sb.append(s);
        	}else{
        		sb.append(tuple.getObject(s).toString());
        	}
        }
        message.setSubject(sb.toString());
        sb=new StringBuffer();
        for(String s:content){
        	if(tuple.getStreamSchema().getAttributeIndex(s) < 0){
        		sb.append(s);
        	}else{
        		sb.append(tuple.getObject(s).toString());
        	}
        }
        message.setText(sb.toString());
        messages.add(message);
        sendMessages();
    }
    
    /**
     * Process an incoming punctuation that arrived on the specified port.
     * @param stream Port the punctuation is arriving on.
     * @param mark The punctuation mark
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public void processPunctuation(StreamingInput<Tuple> stream,
    		Punctuation mark) throws Exception {
    	sendMessages();
    }

    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void shutdown() throws Exception {
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        
        // TODO: If needed, close connections or release resources related to any external system or data store.
        sendMessages();
        // Must call super.shutdown()
        super.shutdown();
    }
    private void addMessage(Message message){
    	messages.add(message);
        if(messages.size() >= batch){
        	sendMessages();
        }
    }
    private void sendMessages(){
		List<Thread> tasks=new ArrayList<Thread>();
    	for(Message msg:messages){
    		Thread th=new Thread(new MyThread(msg));
			tasks.add(th);
			th.start();
//    		try {
//					Transport.send(msg);
//			} catch (MessagingException e) {
//				e.printStackTrace();
//			}
    	}
		/*
		 * Wait for all threads to finish before sending result
		 */
	    int running = 0;
	    do {
	    	try {
	    	    Thread.sleep(100);
	    	} catch(InterruptedException ex) {
	    	    Thread.currentThread().interrupt();
	    	}
	    	running = 0;
	      for (Thread thread : tasks) {
	        if (thread.isAlive()) {
		          running++;
	        }
	      }
	    } while (running > 0);
    	messages.clear();
    }
    public class MyThread extends Thread {
    	Message msg;
    	public MyThread(Message msg){
    		this.msg=msg;
    	}
        public void run(){
    		try {
					Transport.send(msg);
			} catch (MessagingException e) {
				e.printStackTrace();
			}
        }
      }
}
