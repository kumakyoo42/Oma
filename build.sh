#!/bin/sh

set -e

javac de/kumakyoo/*.java
jar cmf META-INF/MANIFEST.MF oma.jar de/kumakyoo/*.class default.bbs default.type
