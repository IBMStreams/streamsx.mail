#!/bin/bash

#some setup to be save
IFS=$' \t\n'
#some recomended security settings
unset -f unalias
\unalias -a
unset -f command
#more setting to be save
set -o posix;
set -o errexit; set -o errtrace; set -o nounset; set -o pipefail
shopt -s globstar nullglob

usage() {
	echo "usage:"
	echo
	echo "purgeMailbox.sh curlCommand imapServerName username password"
}

if [[ $# -ne 4 ]]; then
	usage
	exit 1
fi

curlCommand="$1"
imapServerName="$2"
username="$3"
password="$4"

echo "Purge mailbox $imapServerName"
echo "username=$username"
echo
echo
echo "your curl version is:"
"$curlCommand" --version
echo
echo "curl version must be > 7.29"
echo "recomended curl version 7.49 or higher"
echo

"$curlCommand" --insecure --user "$username:$password" "imaps://$imapServerName" -X 'EXAMINE INBOX'

inputWasY=''
while read -p "Continue or not? y/n "; do
	if [[ $REPLY == y* || $REPLY == Y* || $REPLY == c* || $REPLY == C* ]]; then
		inputWasY='true'
		break
	elif [[ $REPLY == e* || $REPLY == E* || $REPLY == n* || $REPLY == N* ]]; then
		inputWasY=''
		break
	fi
done
if [[ -z $inputWasY ]]; then
	exit 0
fi

list1=$("$curlCommand" --insecure --user "$username:$password" "imaps://$imapServerName/inbox" -X 'FETCH 1:* (UID)')

uidlist=$(echo "$list1" | while read; do
	if [[ "$REPLY" =~ \*.*\(UID\ *(.*)\) ]]; then
		echo -n " ${BASH_REMATCH[1]}"
	else
		printError "No match found in $REPLY"
	fi
done
)

count=0
for x in $uidlist; do
	count=$((count+1))
done

echo "Delete $count mails"

for x in $uidlist; do
	echo "delete UID $x";
	"$curlCommand" --insecure --user "$username:$password" "imaps://$imapServerName/inbox;UID=$x" -X 'STORE 1 +Flags \Deleted';
	"$curlCommand" --insecure --user "$username:$password" "imaps://$imapServerName/inbox;UID=$x" -X 'EXPUNGE';
done

echo "*********************** new mailbox state **********************"
"$curlCommand" --insecure --user "$username:$password" "imaps://$imapServerName" -X 'EXAMINE INBOX'

echo END
exit 0
