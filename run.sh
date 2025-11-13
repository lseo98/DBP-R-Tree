#!/bin/bash
# R-Tree Task 1 실행 스크립트

echo "=== R-Tree Task 1 실행 ==="
echo "GUI 창이 열립니다..."
echo ""

mvn compile exec:java -Dexec.mainClass="org.dfpl.dbp.rtree.team1.Task1Test" -q
