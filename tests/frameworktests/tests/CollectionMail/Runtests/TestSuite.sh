if ! isExistingAndTrue 'TTPR_mailServer'; then
  setSkip "No mail server available"
fi

import mailtools.sh

setVar 'TTPR_timeout' 480

#Make sure instance and domain is running
PREPS='cleanUpInstAndDomainAtStart mkDomain startDomain mkInst startInst'
FINS='cleanUpInstAndDomainAtStop'

