#--variantCount=2

explain=(
	'*** Wrong output schema at port 0 ***'
	'*** Wrong output schema at error port 1 ***'
)

PREPS=(
	'echo "${explain[$TTRO_variantCase]}"'
	'copyAndMorphSpl'
)

STEPS=(
	"splCompile"
	'executeLogAndError output/bin/standalone -t 2'
	'linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "true" "${patternsToMatch[$TTRO_variantCase]}"'
)

patternsToMatch=(
	"*ERROR*CDIST3702E Output schema must be of type*"
	"*ERROR*CDIST3704E The attribute peInfo must be of type: RSTRING*"
)
