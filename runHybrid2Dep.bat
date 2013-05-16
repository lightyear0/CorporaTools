REM This is how you transform to dependencies for syntax experiments.
perl -e "use LvCorporaTools::TreeTransformations::Hybrid2Dep qw($XPRED $COORD $PMC transformFile); $COORD = 'DEFAULT'; $PMC = 'DEFAULT'; $XPRED = 'DEFAULT'; transformFile(@ARGV)" testdata\Hybrid2Dep zeens.a zeens-synt.a

REM This is how you transform to dependencies for semantic experiments.
perl -e "use LvCorporaTools::TreeTransformations::Hybrid2Dep qw($XPRED $COORD $PMC transformFile); $COORD = 'ROW'; $PMC = 'BASELEM'; $XPRED = 'BASELEM'; transformFile(@ARGV)" testdata\Hybrid2Dep zeens.a zeens-sem.a

pause