import streamsutils.sh

getmailenv() {
	# the command to generate
	# openssl enc -e -aes-256-cbc -in mailenv -out mailenv.enc -k tesframeworkpass
	# the file mailenv must have the form
	# setVar 'TTPR_mailDomain' 'strvm2.net1'
	# setVar 'TTPR_mailServer' 'strvm2.net1'
	# setVar 'TTPR_mailUser1' 'mailuser1'
	# setVar 'TTPR_mailUser2' 'mailuser2'
	# setVar 'TTPR_mailPass1' 'planeSpring25Low'
	# setVar 'TTPR_mailPass2' 'planeSpring25Low'

	echo "${TTPR_envionmentFile}"
	openssl enc -d -aes-256-cbc -in ${TTPR_envionmentFile} -out tempfile -k tesframeworkpass
	while read; do
		echo "$REPLY"
		eval "$REPLY"
	done <  tempfile
	rm tempfile
}
export -f getmailenv

PREPS=getmailenv