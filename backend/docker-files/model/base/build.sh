#!/bin/bash
echo "* build image: wedpr-model-service-base-image"
docker build -t wedpr-model-service-base-image .
echo "* build image: wedpr-model-service-base-image success"