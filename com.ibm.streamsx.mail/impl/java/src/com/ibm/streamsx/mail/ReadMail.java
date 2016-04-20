// *******************************************************************************
// * Copyright (C) 2011, 2016 International Business Machines Corporation
// * All Rights Reserved
// *******************************************************************************
package com.ibm.streamsx.mail;


import java.io.IOException;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.search.FlagTerm;

import org.apache.log4j.Logger;

import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.sun.mail.imap.IMAPFolder;

/**
 * A source operator that does not receive any input streams and produces new tuples. 
 * The method <code>produceTuples</code> is called to begin submitting tuples.
 * <P>
 * For a source operator, the following event methods from the Operator interface can be called:
 * </p>
 * <ul>
 * <li><code>initialize()</code> to perform operator initialization</li>
 * <li>allPortsReady() notification indicates the operator's ports are ready to process and submit tuples</li> 
 * <li>shutdown() to shutdown the operator. A shutdown request may occur at any time, 
 * such as a request to stop a PE or cancel a job. 
 * Thus the shutdown() may occur while the operator is processing tuples, punctuation marks, 
 * or even during port ready notification.</li>
 * </ul>
 * <p>With the exception of operator initialization, all the other events may occur concurrently with each other, 
 * which lead to these methods being called concurrently by different threads.</p> 
 */
@PrimitiveOperator(name="ReadMail", namespace="com.ibm.streamsx.mail",
description="Java Operator ReadMail")
@Libraries({"opt/downloaded/*"})
@OutputPorts({@OutputPortSet(description="Port that produces tuples", cardinality=1, optional=false, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating), @OutputPortSet(description="Optional output ports", optional=true, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating)})
public class ReadMail extends MailOperator {
	protected String username;
	protected String password;
	protected String folder;
	protected String hostname;
	protected long period=60;
	@Parameter( description="period to want before searching for new emails if the folder doesn't support idle. Default 60 secs",name="period", optional=true)
	public void setPeriod(long period) {
	    this.period = period*1000;
	}
	public long getPeriod() {
	    return period;
	}
	@Parameter( description="host name",name="hostname", optional=false)
	public void setSmtpHostname(String hostname) {
	    this.hostname = hostname;
	}
	public String getSmtpHostname() {
	    return hostname;
	}
	@Parameter( description="Mailbox folder",name="folder", optional=false)
	public void setFolder(String folder) {
	    this.folder = folder;
	}
	public String getFolder() {
	    return folder;
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
	private Store store;
	private Folder inbox;
	/**
	 * Thread for calling <code>produceTuples()</code> to produce tuples 
	 */
    private Thread processThread;

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
        

		connect(context);
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
                                produceTuples();
                            } catch (Exception e) {
                                Logger.getLogger(this.getClass()).error("Operator error", e);
                                e.printStackTrace();
                            }                    
                    }
                    
                });
        
        /*
         * Set the thread not to be a daemon to ensure that the SPL runtime
         * will wait for the thread to complete before determining the
         * operator is complete.
         */
        processThread.setDaemon(false);
    }
	private void connect(OperatorContext context) throws NoSuchProviderException, MessagingException, Exception {
		session = Session.getInstance(mailProperties,null);
		store=session.getStore("imaps");
		store.connect(hostname, username,password);
        inbox=store.getFolder(folder);
        setPeriod(period);
		if (inbox == null || !inbox.exists()) {
	        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() +" Invalid folder : "+folder);
			throw new Exception("The folder : "+folder+ " doesn't exists !");
		}
	}
	
	private void closeConnections() {
		
		try {
			inbox.close(true);
			store.close();
		} catch (MessagingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

    /**
     * Notification that initialization is complete and all input and output ports 
     * are connected and ready to receive and submit tuples.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void allPortsReady() throws Exception {
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
    	// Start a thread for producing tuples because operator 
    	// implementations must not block and must return control to the caller.
        processThread.start();
    }
    
    /**
     * Submit new tuples to the output stream
     * @throws Exception if an error occurs while submitting a tuple
     */
    private void produceTuples() throws Exception  {
        final StreamingOutput<OutputTuple> out = getOutput(0);
        
		listenForEmails(out);
	    processUnreadMails();
	    
	    boolean supportsIdle = false;
		try {
			if (inbox instanceof IMAPFolder) {
				IMAPFolder f = (IMAPFolder)inbox;
				f.idle();
				supportsIdle = true;
			}
		} catch (FolderClosedException fex) {			
			closeConnections();
			
			Thread.sleep(period);
						
			connect(getOperatorContext());
			produceTuples();
			
			
		} catch (MessagingException mex) {
			supportsIdle = false;
		}
		try {
			startIdleLoop(supportsIdle);
		} catch (Exception e) {
						
			closeConnections();
			
			Thread.sleep(period);
						
			connect(getOperatorContext());
			produceTuples();
		}
    }
	private void startIdleLoop(boolean supportsIdle) throws MessagingException, InterruptedException {
			
		for (;;) {
			if (supportsIdle && inbox instanceof IMAPFolder) {
				IMAPFolder f = (IMAPFolder)inbox;
				f.idle();
			} else {
				Thread.sleep(period); // sleep for freq milliseconds
				// This is to force the IMAP server to send us
				// EXISTS notifications. 
				inbox.getMessageCount();
			}
		}
	}
	private void listenForEmails(final StreamingOutput<OutputTuple> out) throws MessagingException {
				
		inbox.open(Folder.READ_WRITE);
	    
	    inbox.addMessageCountListener(new MessageCountAdapter() {
			public void messagesAdded(MessageCountEvent ev) {
				try {
					processMessages(ev.getMessages());
			        out.punctuate(Punctuation.WINDOW_MARKER);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	private void processUnreadMails() throws Exception, MessagingException {		
		/*
	     * Process all unread mails
	     */
	    Flags seen = new Flags(Flags.Flag.SEEN);
	    FlagTerm unseenFlagTerm = new FlagTerm(seen,false);
		processMessages(inbox.search(unseenFlagTerm));
	}

    private void processMessages(Message[] messages) throws Exception{
        final StreamingOutput<OutputTuple> out = getOutput(0);
		OutputTuple tuple= out.newTuple();
    	Address[] addresses;
    	int addressesLength;
	    for(Message msg:messages){
	    	StringBuffer sb = new StringBuffer();
	    	/*
	    	 * FROM
	    	 */
	    	addresses=msg.getFrom();
	    	if(addresses != null){
		    	addressesLength=addresses.length;
		    	for(int idx=0;idx<addressesLength;idx++){
		    		sb.append(addresses[idx].toString());
		    		if((idx+1)<addressesLength)
		    		sb.append(",");
		    	}
		    	tuple.setString("from", sb.toString());
	    	}
	    	/*
	    	 * REPLY TO
	    	 */
	    	sb=new StringBuffer();
	    	addresses=msg.getReplyTo();
	    	if(addresses != null){
		    	addressesLength=addresses.length;
		    	for(int idx=0;idx<addressesLength;idx++){
		    		sb.append(addresses[idx].toString());
		    		if((idx+1)<addressesLength)
		    		sb.append(",");
		    	}
		    	tuple.setString("replyto", sb.toString());
	    	}

	    	/*
	    	 * TO
	    	 */
	    	sb=new StringBuffer();
	    	addresses=msg.getRecipients(Message.RecipientType.TO);
	    	if(addresses != null){
		    	addressesLength=addresses.length;
		    	for(int idx=0;idx<addressesLength;idx++){
		    		sb.append(addresses[idx].toString());
		    		if((idx+1)<addressesLength)
		    		sb.append(",");
		    	}
		    	tuple.setString("to", sb.toString());
	    	}

	    	/*
	    	 * SUBJECT
	    	 */
	    	tuple.setString("subject", msg.getSubject());

	    	/*
	    	 * DATE
	    	 */
	    	tuple.setLong("date", msg.getSentDate().getTime());

	    	/*
	    	 * CONTENT
	    	 */
	    	sb=new StringBuffer();
	    	if(msg.isMimeType("text/plain") || msg.isMimeType("text/HTML")){
	    		sb.append((String)msg.getContent());
	    	}else if(msg.isMimeType("multipart/*")){
	    		sb.append(parseContent(msg));
	    	}
	    	tuple.setString("content", sb.toString());

	    	out.submit(tuple);
	    }
    }
    private String parseContent(Part p) throws MessagingException, IOException{
    	StringBuffer sb = new StringBuffer();
    	if(p.isMimeType("text/plain") || p.isMimeType("text/HTML")){
    		sb.append((String)p.getContent());
    	}else if(p.isMimeType("multipart/*")){
    	    Multipart mp = (Multipart)p.getContent();
    	    int count = mp.getCount();
    	    for (int i = 0; i < count; i++)
    	    	sb.append(parseContent(mp.getBodyPart(i)));
//    		sb.append((String)msg.getContent());
    	}
    	return sb.toString();
    }
    /**
     * Shutdown this operator, which will interrupt the thread
     * executing the <code>produceTuples()</code> method.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    public synchronized void shutdown() throws Exception {
        if (processThread != null) {
            processThread.interrupt();
            processThread = null;
        }
        OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
        
        // Must call super.shutdown()
        super.shutdown();
    }
}
