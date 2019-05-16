if ! isExistingAndTrue 'TTPR_mailServer'; then
  setSkip "No mail server available"
fi

import mailtools.sh

setVar 'TTPR_timeout' 480
