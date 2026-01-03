#!/usr/bin/env python3
"""
Phase 4: Benchmark Results Analysis and Visualization

This script analyzes benchmark results to:
1. Validate measurement stability
2. Identify high-variance queries
3. Generate performance visualizations
4. Prepare data for factorial experiments
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
from pathlib import Path
import sys

# Configuration
RESULTS_DIR = Path("results/phase4")
OUTPUT_DIR = Path("results/phase4/figures")

# Create output directory
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Visualization style
sns.set_style("whitegrid")
plt.rcParams['figure.dpi'] = 300
plt.rcParams['font.size'] = 10


def load_benchmark_data(dataset_name):
    """Load benchmark results for a dataset."""
    detailed_file = RESULTS_DIR / f"benchmark_detailed_{dataset_name}.csv"
    summary_file = RESULTS_DIR / f"benchmark_summary_{dataset_name}.csv"

    if not detailed_file.exists():
        print(f"Error: Detailed results not found: {detailed_file}")
        return None, None

    detailed = pd.read_csv(detailed_file)
    summary = pd.read_csv(summary_file) if summary_file.exists() else None

    print(f"Loaded {len(detailed)} query benchmarks for {dataset_name} dataset")
    return detailed, summary


def analyze_variance(df):
    """Analyze variance and stability"""
    print("\\n" + "=" * 60)
    print("VARIANCE ANALYSIS")
    print("=" * 60)

    # Coefficient of Variation (CV) analysis
    high_variance = df[df['cv'] > 0.5]
    medium_variance = df[(df['cv'] >= 0.3) & (df['cv'] <= 0.5)]
    low_variance = df[df['cv'] < 0.3]

    print(f"\\nStability by Coefficient of Variation:")
    print(f"  Stable (CV < 0.3): {len(low_variance)} ({100*len(low_variance)/len(df):.1f}%)")
    print(f"  Moderate (0.3 ≤ CV ≤ 0.5): {len(medium_variance)} ({100*len(medium_variance)/len(df):.1f}%)")
    print(f"  High variance (CV > 0.5): {len(high_variance)} ({100*len(high_variance)/len(df):.1f}%)")

    if len(high_variance) > 0:
        print(f"\\n⚠ Warning: {len(high_variance)} queries have high variance (CV > 0.5)")
        print("Top 5 highest variance queries:")
        top_variance = high_variance.nlargest(5, 'cv')
        for _, row in top_variance.iterrows():
            print(f"  {row['template']} query {row['query_index']}: "
                  f"CV={row['cv']:.3f}, mean={row['mean_ms']:.2f}ms, stddev={row['stddev_ms']:.2f}ms")

    # Standard deviation relative to mean
    print(f"\\nStandard Deviation Statistics:")
    print(f"  Mean CV: {df['cv'].mean():.3f}")
    print(f"  Median CV: {df['cv'].median():.3f}")
    print(f"  Max CV: {df['cv'].max():.3f}")

    return high_variance, medium_variance, low_variance

#TODO: Fix Markdown escaping
def generate_validation_report(df, summary, dataset_name):
    """Generate validation report"""
    report_file = RESULTS_DIR / f"validation_report_{dataset_name}.md"

    with open(report_file, 'w') as f:
        f.write(f"# Measurement Validation Report - {dataset_name} Dataset\\n\\n")

        f.write("## Measurement Stability Validation\\n\\n")

        # Overall statistics
        f.write("### Overall Performance Statistics\\n\\n")
        f.write(f"- **Queries benchmarked**: {len(df)}\\n")
        f.write(f"- **Mean query time**: {df['mean_ms'].mean():.4f} ms\\n")
        f.write(f"- **Median query time**: {df['mean_ms'].median():.4f} ms\\n")
        f.write(f"- **Std dev query time**: {df['mean_ms'].std():.4f} ms\\n")
        f.write(f"- **Min query time**: {df['mean_ms'].min():.4f} ms\\n")
        f.write(f"- **Max query time**: {df['mean_ms'].max():.4f} ms\\n")
        f.write(f"- **Throughput**: {1000.0 / df['mean_ms'].mean():.2f} queries/sec\\n\\n")

        # Variance analysis
        f.write("### Variance Analysis\\n\\n")
        high_var = len(df[df['cv'] > 0.5])
        medium_var = len(df[(df['cv'] >= 0.3) & (df['cv'] <= 0.5)])
        low_var = len(df[df['cv'] < 0.3])

        f.write(f"| Stability Category | Count | Percentage |\\n")
        f.write(f"|-------------------|-------|------------|\\n")
        f.write(f"| Stable (CV < 0.3) | {low_var} | {100*low_var/len(df):.1f}% |\\n")
        f.write(f"| Moderate (0.3 ≤ CV ≤ 0.5) | {medium_var} | {100*medium_var/len(df):.1f}% |\\n")
        f.write(f"| High variance (CV > 0.5) | {high_var} | {100*high_var/len(df):.1f}% |\\n\\n")

        f.write(f"**Mean CV across all queries**: {df['cv'].mean():.3f}\\n\\n")

        # Acceptance criteria
        if df['cv'].mean() < 0.3:
            f.write("✅ **Result**: Excellent measurement stability (mean CV < 0.3)\\n\\n")
        elif df['cv'].mean() < 0.5:
            f.write("✓ **Result**: Acceptable measurement stability (mean CV < 0.5)\\n\\n")
        else:
            f.write("⚠ **Result**: High variance detected (mean CV ≥ 0.5) - investigate causes\\n\\n")

        # Performance categories
        f.write("### Query Performance Categories\\n\\n")
        fast = len(df[df['mean_ms'] < 1.0])
        medium = len(df[(df['mean_ms'] >= 1.0) & (df['mean_ms'] < 10.0)])
        slow = len(df[df['mean_ms'] >= 10.0])

        f.write(f"| Category | Time Range | Count | Percentage |\\n")
        f.write(f"|----------|-----------|-------|------------|\\n")
        f.write(f"| Fast | < 1 ms | {fast} | {100*fast/len(df):.1f}% |\\n")
        f.write(f"| Medium | 1-10 ms | {medium} | {100*medium/len(df):.1f}% |\\n")
        f.write(f"| Slow | > 10 ms | {slow} | {100*slow/len(df):.1f}% |\\n\\n")

        # Template analysis
        f.write("### Performance by Template\\n\\n")
        template_stats = df.groupby('template').agg({
            'mean_ms': ['mean', 'median', 'std'],
            'cv': 'mean',
            'query_index': 'count'
        }).round(4)

        f.write("| Template | Queries | Mean (ms) | Median (ms) | Std Dev (ms) | Mean CV |\\n")
        f.write("|----------|---------|-----------|-------------|--------------|---------|\\n")

        for template in template_stats.index:
            count = int(template_stats.loc[template, ('query_index', 'count')])
            mean = template_stats.loc[template, ('mean_ms', 'mean')]
            median = template_stats.loc[template, ('mean_ms', 'median')]
            std = template_stats.loc[template, ('mean_ms', 'std')]
            cv = template_stats.loc[template, ('cv', 'mean')]
            f.write(f"| {template} | {count} | {mean:.4f} | {median:.4f} | {std:.4f} | {cv:.3f} |\\n")

    print(f"\\n✓ Validation report saved to: {report_file}")


def main():
    """Main analysis function."""
    print("=" * 60)
    print("BENCHMARK ANALYSIS")
    print("=" * 60)

    # Analyze 500K dataset
    dataset_name = "500K"
    df, summary = load_benchmark_data(dataset_name)

    if df is None:
        print(f"\\n❌ Error: Could not load benchmark data for {dataset_name}")
        print("Make sure BenchmarkRunner has completed successfully.")
        sys.exit(1)

    # Variance analysis
    high_var, medium_var, low_var = analyze_variance(df)

    # Generate validation report
    print("\\n" + "=" * 60)
    print("GENERATING VALIDATION REPORT")
    print("=" * 60)
    generate_validation_report(df, summary, dataset_name)

    print("\\n" + "=" * 60)
    print("✓ ANALYSIS COMPLETED")
    print("=" * 60)
    print(f"\\nAll outputs saved to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()