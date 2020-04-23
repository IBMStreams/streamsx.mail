#get the path to python3 and use curl from anaconda
if isNotExisting 'TTPR_curlCommand'; then
  mypath1=$(which python3)
  anacondapath=${mypath1%/*}
  setVar 'TTPR_curlCommand' "${anacondapath}/curl"
fi
echo "TTPR_curlCommand=$TTPR_curlCommand"
$TTPR_curlCommand --version

#Make a uniqe id for each test and host
#and return the result in TTTT_result
#store the mail id in file mailid.sav for further processing
makeMailId() {
  local tim=$(date +%s)
  local hn=$(hostname -f)
  TTTT_result="${hn}_${tim}_${TTRO_case}_${TTRO_variantCase}"
  echo -n "$TTTT_result" > "$TTRO_workDirCase/mailid.sav"
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
  #count mails with matching text
  local uid
  local countMatchingMails=0
  for ((uid=1;  uid<=noMessages; uid++)); do
    if $TTPR_curlCommand --insecure --user "$2:$3" -s "imaps://$1/inbox;UID=$uid" > "mailcontent_$2_${uid}.txt"; then
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

#send a e-mail with uniqe id to mbox
# parameters
#   $1 - smtp server name
#   $2 - from
#   $3 - to
#   $4 - uniqe id
#   $5 - count
# return
#   true
# abort if something wents wrong
sendMailTo() {
  echo "$FUNCNAME $*"
  local fname="$TTRO_workDirCase/mailtxt.txt"
  {
    echo "Subject: A message to Streams no: $5"
    echo " a text $4"
    echo "END"
  } > "$fname"
  $TTPR_curlCommand --mail-from "$2@$1" --mail-rcpt "$3@$1" --url smtp://$1 -T "$fname"
  rm "$fname"
}
export -f sendMailTo

#remove e-mails with matching uniqe id from mailbox
#unique ids are collected from file **/mailid.sav
# parameters
#   $1 - imap server name
#   $2 - username
#   $3 - password
# return
#   true
# abort if something wents wrong
removeTestMails() {
	echo "removeTestMails server=$1 user=$2"
	local uniqeIds=''
	local x
	for x in $TTRO_workDirSuite/**/mailid.sav; do
		local y=$(<"$x")
		if [[ -n $uniqeIds ]]; then
			uniqeIds="${uniqeIds}|${y}"
		else
			uniqeIds="$y"
		fi
		
	done
	echo "uniqeIds to delete fom server=$1 user=$2 : $uniqeIds"
	if [[ -z $uniqeIds ]]; then
		printError "Empty uniqueIds"
		return 0
	fi
	
	#check whether mbox is empty
	local resp
	if ! resp=$($TTPR_curlCommand --insecure --user "$2:$3" "imaps://$1/inbox" -X 'STATUS INBOX (MESSAGES)'); then
		return 1
	fi
	local noMessages
	if [[ "$resp" =~ \*\ STATUS\ INBOX\ \(MESSAGES\ (.*)\) ]]; then
		noMessages="${BASH_REMATCH[1]}"
		if [[ "$noMessages" -eq 0 ]]; then
			echo "No messages in mailbox server=$1 user=$2"
			return 0;
		fi
	else
		printErrorAndExit "No number of messages found in: $resp" $errRt
	fi
	
	#get uid list
	if ! $TTPR_curlCommand --insecure --user "$2:$3" "imaps://$1/inbox" -X 'FETCH 1:* (UID)' > mailDirectory.txt; then
		return 1
	fi
	local mailCount=0
	# mail number starts from one
	local -a uidArray=( '' )
	while read; do
		if [[ "$REPLY" =~ \*.*\(UID\ *(.*)\) ]]; then
			mailCount=$((mailCount+1))
			uidArray[$mailCount]="${BASH_REMATCH[1]}"
		else
			printError "No match found in $REPLY"
		fi
	done < mailDirectory.txt
	declare -p uidArray
	echo "mailCount=$mailCount"
	
	#deterine messages uids with matching content; clean the uids not to delete
	local uid
	local i
	local countMatchingMails=0
	#run from i=1 !
	for ((i=1; i<=mailCount; i++)); do
		# I have no idea why read of mails requires the number of the mail and delete requires the uid!
		uid="${uidArray[$i]}"
		if $TTPR_curlCommand --insecure --user "$2:$3" -s "imaps://$1/inbox;UID=$i" > "mailcontent_$2_${i}.txt"; then
			if egrep  "$uniqeIds" "mailcontent_$2_${i}.txt"; then
				echo "Mail to delete number=$i UID=$uid"
				countMatchingMails=$((countMatchingMails+1))
			else
				echo "Do not delete number=$i UID=$uid"
				uidArray[$i]=''
			fi
		else
			echo "can not read message number=$i UID=$uid"
		fi
		rm -f "mailcontent_$2_${i}.txt"
	done
	
	#delete run from i=1 !
	for ((i=1; i<=mailCount; i++)); do
		uid="${uidArray[$i]}"
		if [[ -n $uid ]]; then
			"$TTPR_curlCommand" --insecure --user "$2:$3" "imaps://$1/inbox;UID=$uid" -X 'STORE 1 +Flags \Deleted';
			"$TTPR_curlCommand" --insecure --user "$2:$3" "imaps://$1/inbox;UID=$uid" -X 'EXPUNGE';
		fi
	done
	echo "$countMatchingMails delete atempts made"
	return 0
}
export -f removeTestMails