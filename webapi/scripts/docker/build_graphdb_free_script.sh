#!/usr/bin/env bash

/run.sh &
sleep 5

cd /builds/salsah-suite/rapier-scala
./webapi/scripts/graphdb-free-ci-prepare.sh

kill $!
/run.sh &
sleep 5

cd /builds/salsah-suite/rapier-scala
sbt "project webapi" "graphdb-free:test"
