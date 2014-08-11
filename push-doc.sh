#!/bin/bash

cd doc
git checkout gh-pages
git add .
git commit -am $1
git push -u origin gh-pages
cd ..
