#!/bin/bash

sed -i -E 's/(FR[0-9]+)(- )([0-9]+)(- [0-9]+)/\1\3\4/g' /path/to/qrels

for filename in "./"*; do
    file=$(basename "${filename^^}")
    modfile="${file}-"
    result=$(grep -c "<DOCNO>" $filename)
    awk -v filename="$modfile" '{ if ($4 >= 1 && $4 <= '"$result"' && $3==filename) print $1 " " $2 " " $3 $4 " " $5 >> ("/home/eleanor/all_things_exp/qrels_fr")}' /same/path/to/qrels
done


