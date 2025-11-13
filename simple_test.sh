#!/bin/bash
cd "/Users/hyeseo/3-2/데이터베이스/rtree"
mvn -q compile exec:java -Dexec.mainClass="org.dfpl.dbp.rtree.team1.Task1Test" 2>&1 | head -40
