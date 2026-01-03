for qt in testsuite/templates/*.sparql-template; 
do
   qt2=${qt##*templates/}
   bin/Release/watdiv -q model/wsdbm-data-model.txt ${qt} 100 1 > testsuite/queries/100/${qt2%.sparql-template}.queryset ;
done;
