#!/usr/bin/env bash

set -e

DIR=$(dirname "$0")
F=$(basename "$0")
export JGRAB_HOME="$DIR/.jgrab"
mkdir "$JGRAB_HOME"
cp "$DIR"/../jgrab-runner/build/libs/jgrab.jar "$JGRAB_HOME"/jgrab.jar
ls -al "$JGRAB_HOME"
jgrab="$DIR"/target/debug/jgrab-client

trap 'catch $? $LINENO' ERR
catch() {
  echo "Error code: $1 on $F:$2"
  rm -r "$JGRAB_HOME" || true
}

# start the daemon
$jgrab -t

result=$($jgrab -e "2 + 2")

if test "$result" = "4"
then
  echo "OK"
else
  echo "Failed, result is not 4: $result"
  exit 1
fi

# cleanup
$jgrab -s
rm -r "$JGRAB_HOME" || true
