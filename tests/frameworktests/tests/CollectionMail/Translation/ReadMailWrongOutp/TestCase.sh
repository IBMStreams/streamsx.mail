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
	'myEval'
)

myEval() {
	case "$TTRO_variantSuite" in
	de_DE)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_de_DE[$TTRO_variantCase]}";;
	fr_FR)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_fr_FR[$TTRO_variantCase]}";;
	it_IT)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_it_IT[$TTRO_variantCase]}";;
	es_ES)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_es_ES[$TTRO_variantCase]}";;
	pt_BR)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_pt_BR[$TTRO_variantCase]}";;
	ja_JP)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_ja_JP[$TTRO_variantCase]}";;
	zh_CN)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_zh_CN[$TTRO_variantCase]}";;
	zh_TW)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes_zh_TW[$TTRO_variantCase]}";;
	en_US)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "${errorCodes[$TTRO_variantCase]}";;
	esac;
}

errorCodes=(
	"*ERROR*CDIST3702E Output schema must be of type*"
	"*ERROR*CDIST3704E The attribute peInfo must be of type: RSTRING*"
)

errorCodes_de_DE=(
	"*CDIST3702E Ausgabeschema muss den Typ*"
	"*CDIST3704E Das Attribut peInfo muss den Typ*"
)

errorCodes_fr_FR=(
	"*CDIST3702E Le schéma de sortie doit être du type*"
	"*CDIST3704E L'attribut peInfo doit être du type*"
)

errorCodes_it_IT=(
	"*CDIST3702E Lo schema di output deve essere di tipo*"
	"*CDIST3704E L'attributo peInfo deve essere di tipo*"
)

errorCodes_es_ES=(
	"*CDIST3702E El esquema de salida debe ser del tipo*"
	"*CDIST3704E El atributo peInfo debe ser del tipo*"
)

errorCodes_pt_BR=(
	"*CDIST3702E O esquema de saída deve ser do tipo*"
	"*DIST3704E O atributo peInfo deve ser do tipo*"
)

errorCodes_ja_JP=(
	"*CDIST3702E 出力スキーマはタイプ*"
	"*CDIST3704E 属性 peInfo はタイプ*"
)

errorCodes_zh_CN=(
	"*CDIST3702E 输出模式的类型必须为*"
	"*CDIST3704E 属性 peInfo 的类型必须为*"
)

errorCodes_zh_TW=(
	"*CDIST3702E 輸出綱目的類型必須是*"
	"*CDIST3704E 屬性 peInfo 的類型必須是*"
)
