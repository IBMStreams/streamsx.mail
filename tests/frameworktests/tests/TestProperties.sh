#samples path
setVar 'TTRO_streamsxMailSamplesPath' "$TTRO_inputDir/../../../samples"
#setVar 'TTRO_streamsxMailSamplesPath' "$STREAMS_INSTALL/samples/com.ibm.streamsx.mail"

#toolkit path
setVar 'TTPR_streamsxMailToolkit' "$TTRO_inputDir/../../../com.ibm.streamsx.mail"
#setVar 'TTPR_streamsxMailToolkit' "$STREAMS_INSTALL/toolkits/com.ibm.streamsx.mail"

setVar 'TT_toolkitPath' "${TTPR_streamsxMailToolkit}" #consider more than one tk...

#Mail configuration
setVar 'TTPR_mailDomain' 'strvm2.net1'
setVar 'TTPR_mailServer' 'strvm2.net1'
setVar 'TTPR_mailUser1' 'mailuser1'
setVar 'TTPR_mailUser2' 'mailuser2'
setVar 'TTPR_mailPass1' 'streams1'
setVar 'TTPR_mailPass2' 'streams1'
