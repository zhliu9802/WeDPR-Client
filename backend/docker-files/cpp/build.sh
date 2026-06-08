#!/bin/bash
echo "========= BUILD IMAGES for WEDPR-COMPONENT, BRANCH: ${1} ========="

echo "* build wedpr-gateway-service image"
docker build --target wedpr-gateway-service --build-arg SOURCE_BRANCH=${1}  -t wedpr-gateway-service .
echo "* build wedpr-gateway-service image success"

echo "* build wedpr-pro-node-service image"
docker build --target wedpr-pro-node-service --build-arg SOURCE_BRANCH=${1}  -t wedpr-pro-node-service .
echo "* build wedpr-pro-node-service image success"

echo "* build wedpr-mpc-service image"
docker build --target wedpr-mpc-service --build-arg SOURCE_BRANCH=${1}  -t wedpr-mpc-service .
echo "* build wedpr-mpc-service image success"

echo "========= BUILD IMAGES for WEDPR-COMPONENT, BRANCH: ${1} ========="