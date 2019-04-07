#!/bin/bash

for filename in "./"*; do
    fullfilepath=$(realpath $filename)
    cd /home/eleanor/Senior_Soph/fyp/trec_eval.8.1
    ./trec_eval /home/eleanor/all_things_exp/trec8/trec8_qrels_final_without_la "$fullfilepath" > "$fullfilepath"_trec
    cd -
done

mv *_trec ./trec_results/

./trec_eval ~/all_things_exp/trec8/trec8_qrels_final_without_la ~/all_things_exp/exps/proper_db/run5_trec8/no_la/all_p_normminusla 
