#!/bin/bash

set -euo pipefail

rev=$(git rev-parse HEAD)
remoteurl=$(git ls-remote --get-url origin)

git fetch
if [[ -z $(git branch -r --list origin/gh-pages) ]]; then
    # If repo doesn't have gh-pages branch, create it
    (
    mkdir doc
    cd doc
    git init
    git remote add origin "${remoteurl}"
    git checkout -b gh-pages
    git commit --allow-empty -m "Init"
    git push -u origin gh-pages
    )
elif [[ ! -d doc ]]; then
    # Clone existing gh-pages branch if not cloned
    git clone --branch gh-pages "${remoteurl}" doc
else
    # Reset existing clone to remote state
    (
    cd doc
    git fetch
    git reset --hard origin/gh-pages
    )
fi

mkdir -p doc
lein codox
cd doc
git add --all
git commit -m "Build docs from ${rev}."
git push origin gh-pages
