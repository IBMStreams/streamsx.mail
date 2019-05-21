#--variantList='to to2 ccAttribute bccAttribute'

declare -A myExplain=(
	[to]='**** Send 3 simple mails with and check whether 3 e-mail with matching content are in inbox ****'
	[to2]='**** Send 3 simple mails to 2 recipents and check whether 3 e-mail with matching content are in inbox ****'
	[ccAttribute]='**** Send 3 simple to one and cc to second ****'
	[bccAttribute]='**** Send 3 simple to one and bcc to second ****'
)

PREPS=(
	'echo "${myExplain[$TTRO_variantCase]}"'
	'makeMailId; myId="$TTTT_result"; echo "The uniqe id for this test is: $myId"'
	'checkNoMailsWithUniqIdInInbox'
	'copyAndMorphSpl'
)

STEPS=(
	'myCompile'
	'submitJob'
	'checkJobNo'
	'myWaitForFin'
	'if ! jobHealthy; then setFailure "Job is not healthy"; fi'
	'cancelJobAndLog'
	'myEval'
)

FINS='cancelJobAndLog'

myWaitForFin() {
	local noValidMailsFound=0
	while [[ "$noValidMailsFound" -lt 3 ]]; do
		sleep "$TT_waitForFileInterval"
		getNumberUniqeIds "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailPass2" "$myId"
		noValidMailsFound="$TTTT_result"
	done
}

checkNoMailsWithUniqIdInInbox() {
	getNumberUniqeIds "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailPass2" "$myId"
	if [[ "$TTTT_result" -ne 0 ]]; then setFailure "uniq id found in mailbox 2"; fi
	getNumberUniqeIds "$TTPR_mailServer" "$TTPR_mailUser1" "$TTPR_mailPass1" "$myId"
	if [[ "$TTTT_result" -ne 0 ]]; then setFailure "uniq id found in mailbox 1"; fi
}

myCompile() {
	case "$TTRO_variantCase" in
		to)
			splCompile smtpHost=${TTPR_mailServer} "from=${TTPR_mailUser1}@${TTPR_mailDomain}" "to=${TTPR_mailUser2}@${TTPR_mailDomain}" "uniqId=$myId";;
		to2)
			splCompile smtpHost=${TTPR_mailServer} "from=${TTPR_mailUser1}@${TTPR_mailDomain}" "to=${TTPR_mailUser2}@${TTPR_mailDomain}, ${TTPR_mailUser1}@${TTPR_mailDomain}" "uniqId=$myId";;
		ccAttribute|bccAttribute)
			splCompile smtpHost=${TTPR_mailServer} "from=${TTPR_mailUser1}@${TTPR_mailDomain}" "to=${TTPR_mailUser2}@${TTPR_mailDomain}" "cc=${TTPR_mailUser1}@${TTPR_mailDomain}" "uniqId=$myId";;
	esac
}

myEval() {
	case "$TTRO_variantCase" in
		to)
			echo "Done";;
		*)
			getNumberUniqeIds "$TTPR_mailServer" "$TTPR_mailUser1" "$TTPR_mailPass2" "$myId"
			if [[ "$TTTT_result" -ne 3 ]]; then setFailure "Number of found mails im mailbox 1 must be 3 but are $TTTT_result"; fi;;
	esac
}
