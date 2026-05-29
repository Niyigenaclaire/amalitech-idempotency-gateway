#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script
# ----------------------------------------------------------------------------

set -e

# Find the project base dir
findBaseDir () {
  BASEDIR="$(pwd)"
  WDIR="$(pwd)"
  while [ "$WDIR" != '/' ] ; do
    if [ -d "$WDIR"/.mvn ] ; then
      BASEDIR=$WDIR
      break
    fi
    WDIR=$(dirname "$WDIR")
  done
}

findBaseDir

MAVEN_PROJECTBASEDIR="${BASEDIR}"
export MAVEN_PROJECTBASEDIR

MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ -r "$MAVEN_WRAPPER_PROPERTIES" ]; then
  DISTRIBUTION_URL=$(grep distributionUrl "$MAVEN_WRAPPER_PROPERTIES" | sed 's/distributionUrl=//')
fi

MAVEN_DIST_DIR="$HOME/.m2/wrapper/dists"
MAVEN_ZIP_NAME=$(basename "$DISTRIBUTION_URL")
MAVEN_HOME="$MAVEN_DIST_DIR/${MAVEN_ZIP_NAME%.zip}"

if [ ! -d "$MAVEN_HOME" ]; then
  mkdir -p "$MAVEN_DIST_DIR"
  echo "Downloading Maven from $DISTRIBUTION_URL"
  curl -fsSL -o "$MAVEN_DIST_DIR/$MAVEN_ZIP_NAME" "$DISTRIBUTION_URL"
  unzip -q "$MAVEN_DIST_DIR/$MAVEN_ZIP_NAME" -d "$MAVEN_DIST_DIR"
  MAVEN_HOME=$(find "$MAVEN_DIST_DIR" -maxdepth 1 -type d -name "apache-maven-*" | head -1)
fi

MVN_CMD="$MAVEN_HOME/bin/mvn"

exec "$MVN_CMD" "$@"
