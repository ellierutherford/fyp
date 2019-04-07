#!/bin/bash

for filename in "./"*; do
    file=$(basename "${filename}")
    grep -vf ~/all_things_exp/scripts/la_mess "$filename" > "$file"minusla
    mv "$file"minusla ./no_la
done



