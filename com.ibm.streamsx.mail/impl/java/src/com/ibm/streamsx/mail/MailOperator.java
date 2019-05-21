package com.ibm.streamsx.mail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.Set;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.Type.MetaType;
import com.ibm.streams.operator.types.Timestamp;

public class MailOperator extends AbstractOperator {

	protected static final String ERROR_OUTPUT_SCHEMA = "tuple<ts,rstring operatorName,rstring peInfo,rstring message,rstring cause>";

	protected boolean hasErrorPort = false;
	protected StreamingOutput<OutputTuple> errOut = null;

	//operator enums
	public enum EncryptionType {
		NONE,
		STARTTLS,
		TLS
	}

	/**
	 * Check of the optional error output port schema
	 * @param context
	 * @param portNo
	 */
	protected void checkErrorPortSchema(OperatorContext context, int portNo) {
		if (context.getNumberOfStreamingOutputs() > portNo) {
			errOut = getOutput(portNo);
			hasErrorPort = true;
			StreamSchema errorSchema = errOut.getStreamSchema();
			Set<String> errorAttributeNames = errorSchema.getAttributeNames();
			String errorSpltype = errorSchema.getLanguageType();
			System.out.println("Error output schema: " + errorSpltype);
			if (errorAttributeNames.contains("ts")) {
				MetaType paramType = errorSchema.getAttribute("ts").getType().getMetaType();
				if(paramType != MetaType.TIMESTAMP) {
					throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_2", "ts", MetaType.TIMESTAMP));
				}
			} else {
				throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_1", (Object[]) new String[]{ERROR_OUTPUT_SCHEMA}));
			}
			if (errorAttributeNames.contains("operatorName")) {
				MetaType paramType = errorSchema.getAttribute("operatorName").getType().getMetaType();
				if(paramType != MetaType.RSTRING) {
					throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_2", "operatorName", MetaType.RSTRING));
				}
			} else {
				throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_1", (Object[]) new String[]{ERROR_OUTPUT_SCHEMA}));
			}
			if (errorAttributeNames.contains("peInfo")) {
				MetaType paramType = errorSchema.getAttribute("peInfo").getType().getMetaType();
				if(paramType != MetaType.RSTRING) {
					throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_2", "peInfo", MetaType.RSTRING));
				}
			} else {
				throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_1", (Object[]) new String[]{ERROR_OUTPUT_SCHEMA}));
			}
			if (errorAttributeNames.contains("message")) {
				MetaType paramType = errorSchema.getAttribute("message").getType().getMetaType();
				if(paramType != MetaType.RSTRING) {
					throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_2", "message", MetaType.RSTRING));
				}
			} else {
				throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_1", (Object[]) new String[]{ERROR_OUTPUT_SCHEMA}));
			}
			if (errorAttributeNames.contains("cause")) {
				MetaType paramType = errorSchema.getAttribute("cause").getType().getMetaType();
				if(paramType != MetaType.RSTRING) {
					throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_2", "cause", MetaType.RSTRING));
				}
			} else {
				throw new IllegalArgumentException(Messages.getString("OUTPUT_SCHEMA_ERROR_1", (Object[]) new String[]{ERROR_OUTPUT_SCHEMA}));
			}
		}
	}

	/**
	 * Send the error tuple if error port is here
	 * @param message
	 * @param cause
	 * @throws TupleSendException
	 */
	protected void sendErrorTuple(String message, String cause) throws TupleSendException {
		if (hasErrorPort) {
			OutputTuple tuple= errOut.newTuple();
			tuple.setTimestamp("ts", Timestamp.currentTime());
			tuple.setString("operatorName", getOperatorContext().getLogicalName());
			BigInteger peid = getOperatorContext().getPE().getPEId();
			tuple.setString("peInfo", peid.toString());
			tuple.setString("message", message);
			tuple.setString("cause", cause);
			try {
				errOut.submit(tuple);
			} catch (Exception e) {
				throw new TupleSendException("Can not send error tuple");
			}
		}
	}

	/**
	 * Send the error tuple if error port is here
	 * @param message
	 * @param e
	 * @throws TupleSendException
	 */
	protected void sendErrorTuple(String message, Throwable e) throws TupleSendException {
		if (hasErrorPort) {
			StringWriter strw = new StringWriter();
			PrintWriter prtw = new PrintWriter(strw);
			e.printStackTrace(prtw);
			sendErrorTuple(message, e.getMessage() + ": " + strw.toString());
		}
	}

}
