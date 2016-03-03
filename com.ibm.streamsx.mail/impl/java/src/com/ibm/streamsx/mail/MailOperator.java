// *******************************************************************************
// * Copyright (C) 2011, 2016 International Business Machines Corporation
// * All Rights Reserved
// *******************************************************************************

package com.ibm.streamsx.mail;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

import org.apache.log4j.Logger;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.model.Parameter;

public class MailOperator extends AbstractOperator {
	protected Properties mailProperties = new Properties();


	protected Session session;
	protected ArrayList<Message> messages;

	@Override
	public synchronized void initialize(OperatorContext context)
			throws Exception {
    	// Must call super.initialize(context) to correctly setup an operator.
		super.initialize(context);
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );

        /*
         * Iterate over parameters and auto-assign java variables
         */
		Set<String> paramNames = context.getParameterNames();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " ------------------------------------------------------ ");
        Field f;
		for (Iterator<String> iterator = paramNames.iterator(); iterator.hasNext();) {
			String paramName = (String) iterator.next();
			f=this.getClass().getDeclaredField(paramName);
			if (null != f){
				if(f.getType().equals(int.class)){
					f.set(this, Integer.parseInt(context.getParameterValues(paramName).get(0)));
				}else if(f.getType().equals(boolean.class)){
					f.set(this, Boolean.parseBoolean(context.getParameterValues(paramName).get(0)));
				}else if(f.getType().equals(float.class)){
					f.set(this, Float.parseFloat(context.getParameterValues(paramName).get(0)));
				}else if(f.getType().equals(long.class)){
					f.set(this, Long.parseLong(context.getParameterValues(paramName).get(0)));
				}else if(f.getType().equals(String[].class)){
					int l=context.getParameterValues(paramName).size();
					Object o=Array.newInstance(String.class, l);
					for(int i=0;i<l;i++){
						Array.set(o, i, context.getParameterValues(paramName).get(i));
					}
					f.set(this, o);
				}else {
					f.set(this, context.getParameterValues(paramName).get(0));
				}
			}
			List<String> paramValue = context.getParameterValues(paramName);
			Logger.getLogger(this.getClass()).info("Operator " + context.getName() + " - parameter name = " + paramName + ", parameter value="+ paramValue);
		}
		messages=new ArrayList<Message>();
		Logger.getLogger(this.getClass()).info("Operator " + context.getName() + " ------------------------------------------------------ ");
	}
}
