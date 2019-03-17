#!/bin/bash
for filename in "./fr94/"*; do
	cd $filename
	rename 's/(.*)\.([0-9])z/$1$2\.z/' *
	uncompress *
	sed -i 's/.*-- PJG.*/ /' *
	sed -i '/^[[:space:]]*$/d' *
	cd -
done
