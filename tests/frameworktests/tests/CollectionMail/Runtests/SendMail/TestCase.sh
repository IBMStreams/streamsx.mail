# test case varians must start with a common pattern because we search for the pattern in the emails
#--variantList='0smtpp 1smtps 2smtpp_subject 3smtps_subject 4smtpp_port 5smtps_port'

setCategory 'quick'

declare -A myExplain=(
	[0smtpp]='**** Send 3 simple mails with (plain) SMTP and check whether 3 e-mail with matching content are in inbox ****'
	[1smtps]='**** Send 3 simple mails with SMTPS and check whether 3 e-mail with matching content are in inbox ****'
	[2smtpp_subject]='**** Send 3 simple mails with (plain) SMTP and put the uniq id into header ****'
	[3smtps_subject]='**** Send 3 simple mails with SMTPS and put the uniq id into header ****'
	[4smtpp_port]='**** Send 3 simple mails with (plain) SMTP use optional error port ****'
	[5smtps_port]='**** Send 3 simple mails with SMTPS use optional error port ****'
)

PREPS=(
	'echo "${myExplain[$TTRO_variantCase]}"'
	'makeMailId'
	'myId="$TTTT_result"'
	'echo "The uniqe id for this test is: $myId"'
	'getNumberUniqeIds "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailPass2" "$myId"'
	'if [[ "$TTTT_result" -ne 0 ]]; then setFailure "uniq id found in mailbox"; fi'
	'copyAndMorphSpl'

)

STEPS=(
	'splCompile smtpHost=${TTPR_mailServer} "from=${TTPR_mailUser1}@${TTPR_mailDomain}" "to=${TTPR_mailUser2}@${TTPR_mailDomain}" "uniqId=$myId"'
	'submitJob'
	'checkJobNo'
	'myWaitForFin'
	'if ! jobHealthy; then setFailure "Job is not healthy"; fi'
	'cancelJobAndLog'
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
