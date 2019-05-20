# README --  IBMStreams/streamsx.mail

The toolkit for sending or receiving emails in an IBM Streams application.

Find the project Documentation at: http://ibmstreams.github.io/streamsx.mail/

## How to use this toolkit

1. Download and unpack the latest release archive from: [releases]: https://github.com/IBMStreams/streamsx.mail/releases 
2. Configure the SPL compiler to find the toolkit root directory. Use one of the following methods:
  * Set the **STREAMS_SPLPATH** environment variable to the root directory of a toolkit
    or multiple toolkits (with : as a separator).  For example:
      export STREAMS_SPLPATH=$STREAMS_INSTALL/toolkits/com.ibm.streamsx.mail
  * Specify the **-t** or **--spl-path** command parameter when you run the **sc** command. For example:
      sc -t $HOME/toolkits/com.ibm.streamsx.inet -M MyMain
    where MyMain is the name of the SPL main composite.
    **Note**: These command parameters override the **STREAMS_SPLPATH** environment variable.
  * Add the toolkit location in InfoSphere Streams Studio.
3. Develop your application. To avoid the need to fully qualify the operators, add a use directive in your application.
  * For example, you can add the following clause in your SPL source file:
      use com.ibm.streamsx.mail::*;
    You can also specify a use clause for individual operators by replacing the asterisk (\*) with the operator name. For example:
      use com.ibm.streamsx.mail::SendMail;
4. Build your application.  You can use the **sc** command or Streams Studio.  
5. Start the InfoSphere Streams instance.
6. Run the application. You can submit the application as a job by using the **streamtool submitjob** command or by using Streams Studio.

