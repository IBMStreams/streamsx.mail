PREPS=(
	'copyOnly'
)

STEPS=(
	"splCompile"
	'executeLogAndError output/bin/standalone -t 2'
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "true" "*ERROR*CDIST3703E The error port output schema must*"'
)

