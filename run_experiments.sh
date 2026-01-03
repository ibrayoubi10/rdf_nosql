#!/usr/bin/env bash
set -e

echo "▶ Cleaning and building project"
mvn clean package -DskipTests

echo "▶ Checking duplicate queries and deduplicating queryset"
python3 scripts/check_duplicates.py
python3 scripts/deduplicate_queries.py
echo "✔ Duplicate check and deduplication completed"

echo "▶ Running Workload Analysis"
MAVEN_OPTS="-Xmx4096m -Xms4096m" mvn exec:java -Dexec.mainClass="qengine.program.WorkloadAnalysis"
python3 scripts/generate_histograms.py
echo "✔ Workload analysis completed"

echo "▶ Running System Verification"
MAVEN_OPTS="-Xmx4096m -Xms4096m" mvn exec:java -Dexec.mainClass="qengine.program.SystemVerification"
echo "✔ System verification completed"

echo "▶ Running Performance Evaluation"
MAVEN_OPTS="-Xmx4096m -Xms4096m" mvn exec:java -Dexec.mainClass="qengine.program.BenchmarkRunner"
python3 scripts/analyze_benchmark.py
echo "✔ Performance evaluation completed"

echo "▶ Preparing Selectivity Queries for Factorial Experiment B"
python3 scripts/prepare_selectivity_queries.py
echo "✔ Preparation of selectivity queries completed"

echo "▶ Running Factorial Experiments"
MAVEN_OPTS="-Xmx4096m -Xms4096m" mvn exec:java -Dexec.mainClass="qengine.program.ExperimentA" -Dexec.args="500K 4096"
MAVEN_OPTS="-Xmx8192m -Xms8192m" mvn exec:java -Dexec.mainClass="qengine.program.ExperimentA" -Dexec.args="500K 8192"
MAVEN_OPTS="-Xmx4096m -Xms4096m" mvn exec:java -Dexec.mainClass="qengine.program.ExperimentA" -Dexec.args="2M 4096"
MAVEN_OPTS="-Xmx8192m -Xms8192m" mvn exec:java -Dexec.mainClass="qengine.program.ExperimentA" -Dexec.args="2M 8192"
python3 scripts/analyze_factorial_experiments.py
echo "✔ Factorial experiments completed"

echo "▶ Running System Comparison"
MAVEN_OPTS="-Xmx4096m -Xms4096m" mvn exec:java -Dexec.mainClass="qengine.program.SystemComparison"
python3 scripts/analyze_comparison.py
echo "✔ System comparison completed"

echo "▶ Running Query Order Analysis"
MAVEN_OPTS="-Xmx4096m -Xms4096m" mvn exec:java -Dexec.mainClass="qengine.program.QueryOrderAnalysis"
python3 scripts/analyze_order_effect.py
echo "✔ Query order analysis completed"

echo "✔ All experiments completed successfully"

