if ! isExistingAndTrue 'TTPR_mailServer'; then
  setSkip "No mail server available"
fi

import mailtools.sh

setVar 'TTPR_timeout' 480

#Make sure instance and domain is running
PREPS='cleanUpInstAndDomainAtStart mkDomain startDomain mkInst startInst'
FINS='cleanUpMailBoxes cleanUpInstAndDomainAtStop'

cleanUpMailBoxes() {
	if isExistingAndTrue 'TTPR_mailServer'; then
		removeTestMails "$TTPR_mailServer" "$TTPR_mailUser1" "$TTPR_mailPass1"
		removeTestMails "$TTPR_mailServer" "$TTPR_mailUser2" "$TTPR_mailPass2"
	fi
}
