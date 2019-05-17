#get the path to python3 and use curl from anaconda
mypath1=$(which python3)
anacondapath=${mypath1%/*}
setVar 'TTPR_curlCommand' "${anacondapath}/curl"
echo "TTPR_curlCommand=$TTPR_curlCommand"
$TTPR_curlCommand --version

#Make a uniqe id for each test and host
#and return the result in TTTT_result
makeMailId() {
  local tim=$(date +%s)
  local hn=$(hostname -f)
  TTTT_result="${hn}_${tim}_${TTRO_case}_${TTRO_variantCase}"
}
export -f makeMailId

#return the number of e-mails with the uniqe mail id in inbox
# parameters
#   $1 - imap server name
#   $2 - username
#   $3 - password
#   $4 - uniqe id
# return
#   TTTT_result
# abort if something wents wrong
getNumberUniqeIds() {
  echo "$FUNCNAME $*"
  #check whether mbox is empty
  local resp
  if ! resp=$($TTPR_curlCommand --insecure --user "$2:$3" "imaps://$1/inbox" -X 'STATUS INBOX (MESSAGES)'); then
    return 1
  fi
  local noMessages
  if [[ "$resp" =~ \*\ STATUS\ INBOX\ \(MESSAGES\ (.*)\) ]]; then
    noMessages="${BASH_REMATCH[1]}"
    if [[ "$noMessages" -eq 0 ]]; then
      TTTT_result=0
      return 0;
    fi
  else
    printErrorAndExit "No number of messages found in: $resp" $errRt
  fi
  #get uid list
  local list1
  if ! list1=$($TTPR_curlCommand --insecure --user "$2:$3" "imaps://$1/inbox" -X 'FETCH 1:* (UID)'); then
    return 1
  fi
  local uidlist=''
  local uidlist=$(echo "$list1" | while read; do
    if [[ "$REPLY" =~ \*.*\(UID\ *(.*)\) ]]; then
      echo -n " ${BASH_REMATCH[1]}"
    else
      printError "No match found in $REPLY"
    fi
  done
  )
  echo "uidlist=$uidlist"
  #count mails with matching text
  local uid
  local countMatchingMails=0
  for ((uid=1;  uid<=noMessages; uid++)); do
    if $TTPR_curlCommand --insecure --user "$2:$3" "imaps://$1/inbox;UID=$uid" > "mailcontent_$2_${uid}.txt"; then
      if linewisePatternMatch "mailcontent_$2_${uid}.txt" '' "*$4*"; then
        echo "Match found in uid=$uid pattern:$4"
        countMatchingMails=$((countMatchingMails+1))
      fi
    else
      echo "can not read message id=$uid"
    fi
  done
  TTTT_result="$countMatchingMails"
  echo "$TTTT_result matching messages found in mbox: $2"
  return 0
}
export -f getNumberUniqeIds
