#!/bin/bash
echo "* build image: wedpr-model-service, branch: ${1}"
docker build --build-arg SOURCE_BRANCH=${1} -t wedpr-model-service .
echo "* build image: wedpr-model-service success, branch: ${1}"