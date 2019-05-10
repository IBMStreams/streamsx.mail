#--variantList=$(\
#--for x in $TTRO_streamsxMailSamplesPath/*; \
#--	do if [[ -f $x/Makefile ]]; then \
#--		echo -n "${x#$TTRO_streamsxMailSamplesPath/} "; \
#--	fi; \
#--	done\
#--)

setCategory 'quick'

PREPS=(
	'copyAndMorph "$TTRO_streamsxMailSamplesPath/$TTRO_variantCase" "$TTRO_workDirCase" ""'
	'export STREAMSX_MAIL_TOOLKIT="$TTPR_streamsxMailToolkit"'
	'export SPL_CMD_ARGS="-j $TTRO_treads"'
)
STEPS=( 'echoExecuteInterceptAndSuccess make' )
