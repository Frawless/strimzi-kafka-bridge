#!/usr/bin/env bash

set -e

openssl aes-256-cbc -K $encrypted_92c411a5688b_key -iv $encrypted_92c411a5688b_iv -in .travis/github_deploy_key.enc -out github_deploy_key -d
chmod 600 github_deploy_key
eval `ssh-agent -s`
ssh-add github_deploy_key

git clone git@github.com:strimzi/strimzi.github.io.git /tmp/website
cp -v documentation/htmlnoheader/master.html /tmp/website/docs/bridge/master/master.html
cp -v documentation/html/index.html /tmp/website/docs/bridge/master/full.html
rm -rf /tmp/website/docs/bridge/master/images
cp -vrL documentation/htmlnoheader/images /tmp/website/docs/bridge/master/images

pushd /tmp/website

if [[ -z $(git status -s) ]]; then
    echo "No changes to the output on this push; exiting."
    exit 0
fi

git config user.name "Travis CI"
git config user.email "ci@travis.tld"

git add -A
git commit -m "Update Kafka Bridge documentation (Travis CI build ${TRAVIS_BUILD_NUMBER})" --allow-empty
git push origin master

popd
