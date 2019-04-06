#!/bin/bash

for filename in "./"*; do
    file=$(basename "${filename^^}")
    modfile="${file}-"
    result=$(grep -c "<DOCNO>" $filename)
    awk -v filename="$modfile" '{ if ($4 >= 1 && $4 <= '"$result"' && $3==filename) print $1 " " $2 " " $3 $4 " " $5 >> ("./testpls")}' /home/eleanor/all_things_exp/qrels_exp
done


