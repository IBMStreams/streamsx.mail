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
  TTTT_result="${hn}_${tim}"
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
  echo "$*"
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
  local uid mailcont
  local countMatchingMails=0
  for uid in $uidlist; do
    if ! $TTPR_curlCommand --insecure --user "$2:$3" "imaps://$1/inbox;UID=$uid" > "mailcontent_${uid}.txt"; then
      return 1
    fi
    if linewisePatternMatch "mailcontent_${uid}.txt" '' "*$4*"; then
      echo "Match found in uid=$uid pattern:$4"
      countMatchingMails=$((countMatchingMails+1))
    fi
  done
  TTTT_result="$countMatchingMails"
  return 0
}
export -f getNumberUniqeIds
