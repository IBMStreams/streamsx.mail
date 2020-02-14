setCategory 'quick'

PREPS=(
	'copyOnly'
)

STEPS=(
	"splCompile"
	'executeLogAndError output/bin/standalone -t 2'
	'myEval'
)

myEval() {
	case "$TTRO_variantSuite" in
	de_DE)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E Das Ausgabeschema des Fehlerports muss den Typ*";;
	fr_FR)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E Le schéma de sortie de port d'erreur doit être du type*";;
	it_IT)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E Lo schema di output della porta in errore deve essere di tipo*";;
	es_ES)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E El esquema de salida del puerto de error debe ser del tipo*";;
	pt_BR)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E O esquema de saída da porta de erro deve ser do tipo*";;
	ja_JP)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E エラー・ポートの出力スキーマはタイプ*";;
	zh_CN)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E 错误端口输出模式的类型必须为*";;
	zh_TW)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E 錯誤埠輸出綱目的類型必須是*";;
	en_US)
		linewisePatternMatchInterceptAndSuccess "$TT_evaluationFile" "" "*CDIST3703E The error port output schema must be of type*";;
	esac;
}
