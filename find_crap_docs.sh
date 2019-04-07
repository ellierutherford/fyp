#!/bin/bash

for filename in "./"*; do
    a=$(grep -c "<DOCNO>" $filename)
    b=$(grep -c "<TEXT>" $filename)
  
    if [ "$a" -ne "$b" ];
    then 
	echo $filename" DOC "$a" TEXT "$b
    fi
 
done
