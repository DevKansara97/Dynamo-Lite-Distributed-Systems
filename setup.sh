#!/bin/bash
mkdir -p distributed-systems/dynamo-lite
cd distributed-systems/dynamo-lite
mkdir -pv src out config scripts
touch README.md
mkdir -p src/com/dynamo/lite/{config,server,protocol,storage,hashing,cluster,replication,routing,metrics,client}
echo "Project structure created successfully!"
