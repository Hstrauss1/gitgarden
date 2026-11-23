#!/bin/sh
# Builds the project, checks that the API compiles without JavaFX, and runs the JUnit tests
# (64 of them, about a second).
#
# Needs a JDK that includes JavaFX (such as BellSoft Liberica JDK 21 "Full"), because the GUI
# classes are compiled too. With a plain JDK, set JAVAFX_LIB to a folder of JavaFX jars:
#   JAVAFX_LIB=~/javafx-sdk-21/lib scripts/test.sh
# Extra arguments go to the JUnit launcher, e.g.  scripts/test.sh --details=tree
set -e
cd "$(dirname "$0")/.."

if [ -z "$JAVA_HOME" ] && [ -x "$HOME/.jdks/liberica-full-21/bin/javac" ]; then
    JAVA_HOME="$HOME/.jdks/liberica-full-21"
fi
BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
JUNIT=lib/junit-platform-console-standalone-1.10.2.jar
if [ ! -f "$JUNIT" ]; then
    mkdir -p lib
    echo "Downloading JUnit to $JUNIT"
    curl -sSfL -o "$JUNIT" https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar
fi
FX=""
if [ -n "$JAVAFX_LIB" ]; then FX="$JAVAFX_LIB/*"; fi

rm -rf out out-test out-api
mkdir -p out/garden
cp src/garden/*.fxml src/garden/*.css src/garden/*.md out/garden/
"${BIN}javac" -cp "$FX" -d out $(find src -name '*.java')
# The API must build on a plain JDK: compile it alone with only java.base visible, so any
# JavaFX reference that creeps into the model or the API fails here.
"${BIN}javac" --limit-modules java.base -d out-api -sourcepath src \
    src/GardenSimulator.java src/GardenSimulationAPI.java src/garden/api/*.java
echo "The Garden Simulation API compiles without JavaFX."
"${BIN}javac" -cp "out:$JUNIT" -d out-test $(find test -name '*.java')
"${BIN}java" -jar "$JUNIT" execute -cp out:out-test --scan-classpath --disable-banner "$@"
