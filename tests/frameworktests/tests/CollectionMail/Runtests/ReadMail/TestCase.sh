#--variantList='read readExt readPort'

setCategory 'quick'

declare -A myExplain=(
  [read]='**** Send 3 simple mails to mailuser1 and receive ****'
  [readExt]='**** Send 3 simple mails to mailuser1 and receive with extendet output schema ****'
  [readPort]='**** Send 3 simple mails to mailuser1 and receive with Error Port ****'
)

PREPS=(
	'echo "${myExplain[$TTRO_variantCase]}"'
	'makeMailId; myId="$TTTT_result"; echo "The uniqe id for this test is: $myId"'
  'myPrepareMbox'
	'copyAndMorphSpl'
)

STEPS=(
	'myCompile'
	'submitJob'
	'checkJobNo'
	'waitForFin'
	'cancelJobAndLog'
	'myEval'
)

FINS='cancelJobAndLog'

myPrepareMbox() {
  sendMailTo "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailUser1" "$myId" 0
  sendMailTo "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailUser1" "$myId" 2
  sendMailTo "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailUser1" "$myId" 3
}

myCompile() {
	splCompile "imapHost=${TTPR_mailServer}" "username=${TTPR_mailUser1}" "password=$TTPR_mailPass1"
}

myEval() {
  local fname="$TTRO_workDirCase/data/Tuples"
  grep "$myId\"" "$fname"
  local count=$(grep "$myId\"" "$fname" | wc -l)
  if [[ "$count" -ne 6 ]]; then setFailure "Wrong count of received e mails. Count must be 6 but count is: $count"; fi
}
