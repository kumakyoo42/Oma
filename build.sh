#!/bin/sh

set -e

javac de/kumakyoo/oma/*.java
jar cmf META-INF/MANIFEST.MF oma.jar de/kumakyoo/oma/*.class default.bbs default.type
