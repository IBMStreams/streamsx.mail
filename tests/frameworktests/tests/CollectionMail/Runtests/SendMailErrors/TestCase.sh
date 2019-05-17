#--variantList='noErrorPort errorPort'

declare -A myExplain=(
	[noErrorPort]='**** Send 3 simple mails to mailuser1, adress with error and to mailuser1 and check whether there are 2 e-mail with matching content are in inbox ****'
	[errorPort]='**** same case but with error port ****'
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
	while [[ "$noValidMailsFound" -lt 2 ]]; do
		sleep "$TT_waitForFileInterval"
		getNumberUniqeIds "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailPass2" "$myId"
		noValidMailsFound="$TTTT_result"
	done
}

checkNoMailsWithUniqIdInInbox() {
	getNumberUniqeIds "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailPass2" "$myId"
	if [[ "$TTTT_result" -ne 0 ]]; then setFailure "uniq id found in mailbox 2"; fi
}

myCompile() {
	local rcptList="[\"${TTPR_mailUser2}@${TTPR_mailDomain}\",\"${TTPR_mailUser2}@${TTPR_mailDomain}->\", \"${TTPR_mailUser2}@${TTPR_mailDomain}\"]"
	splCompile smtpHost=${TTPR_mailServer} "from=${TTPR_mailUser1}@${TTPR_mailDomain}" "toList=$rcptList" "uniqId=$myId";
}

myEval() {
	checkTokenIsInFiles 'true' 'Wrong e-mail address' $TTTT_jobLogDirs/*.out
	if [[ "$TTRO_variantCase" == 'errorPort' ]]; then
		checkTokenIsInFiles 'true' 'Wrong e-mail address' "$TTRO_workDirCase/data/Tuples"
	fi
}
