#!/usr/bin/env python3
"""
System Comparison Analysis and Visualization

This script analyzes the system comparison results to:
1. Compare Our Prototype vs InteGraal performance
2. Identify performance gaps and advantages
3. Generate comparison visualizations
4. Perform statistical significance testing
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
from pathlib import Path
from scipy import stats

# Configuration
RESULTS_DIR = Path("results/phase6")
OUTPUT_DIR = Path("results/phase6/figures")

# Create output directory
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Visualization style
sns.set_style("whitegrid")
plt.rcParams['figure.dpi'] = 300
plt.rcParams['font.size'] = 10


def load_comparison_data():
    """Load comparison results."""
    detailed_file = RESULTS_DIR / "system_comparison_results.csv"
    summary_file = RESULTS_DIR / "system_comparison_summary.csv"

    if not detailed_file.exists():
        print(f"Error: Results not found: {detailed_file}")
        return None, None

    detailed = pd.read_csv(detailed_file)
    summary = pd.read_csv(summary_file) if summary_file.exists() else None

    print(f"Loaded comparison results:")
    print(f"  - {len(detailed)} query benchmarks")
    print(f"  - Systems: {detailed['system'].unique()}")

    return detailed, summary


def compute_speedup(df):
    """Compute speedup for each query (baseline: InteGraal)."""
    # Pivot to get systems side by side
    pivot = df.pivot_table(
        values='mean_ms',
        index=['template', 'query_index'],
        columns='system'
    ).reset_index()

    # Compute speedup relative to InteGraal (baseline)
    # > 1.0 means system is slower than InteGraal (took more time)
    # < 1.0 means system is faster than InteGraal (took less time)
    if 'OurPrototype' in pivot.columns:
        pivot['speedup_our'] = pivot['OurPrototype'] / pivot['InteGraal']
        pivot['our_is_faster'] = pivot['speedup_our'] < 1.0

    if 'Colleague' in pivot.columns:
        pivot['speedup_colleague'] = pivot['Colleague'] / pivot['InteGraal']
        pivot['colleague_is_faster'] = pivot['speedup_colleague'] < 1.0

    return pivot


def statistical_significance_test(df):
    """Wilcoxon signed-rank test for paired comparison."""
    print("\n" + "=" * 60)
    print("STATISTICAL SIGNIFICANCE TESTING")
    print("=" * 60)

    # Get InteGraal baseline
    integraal_times = df[df['system'] == 'InteGraal']['mean_ms'].values

    results = {}

    # Test Our Prototype vs InteGraal
    if 'OurPrototype' in df['system'].values:
        our_times = df[df['system'] == 'OurPrototype']['mean_ms'].values
        stat, p_val = stats.wilcoxon(our_times, integraal_times)
        results['OurPrototype'] = (stat, p_val)

        print(f"\nOur Prototype vs InteGraal:")
        print(f"  Statistic: {stat:.2f}")
        print(f"  p-value: {p_val:.6f}")
        if p_val < 0.05:
            print(f"  Result: Significant difference (p < 0.05)")
        else:
            print(f"  Result: No significant difference (p >= 0.05)")

    # Test Colleague vs InteGraal
    if 'Colleague' in df['system'].values:
        colleague_times = df[df['system'] == 'Colleague']['mean_ms'].values
        stat, p_val = stats.wilcoxon(colleague_times, integraal_times)
        results['Colleague'] = (stat, p_val)

        print(f"\nColleague vs InteGraal:")
        print(f"  Statistic: {stat:.2f}")
        print(f"  p-value: {p_val:.6f}")
        if p_val < 0.05:
            print(f"  Result: Significant difference (p < 0.05)")
        else:
            print(f"  Result: No significant difference (p >= 0.05)")

    return results


def identify_performance_gaps(speedup_df):
    """Identify queries where performance differs significantly."""
    print("\n" + "=" * 60)
    print("PERFORMANCE GAP ANALYSIS (vs InteGraal baseline)")
    print("=" * 60)

    # Our Prototype analysis
    if 'speedup_our' in speedup_df.columns:
        much_slower_our = speedup_df[speedup_df['speedup_our'] > 10.0].sort_values('speedup_our', ascending=False)
        print(f"\n=== OUR PROTOTYPE ===")
        print(f"Our Prototype is >10x slower ({len(much_slower_our)} queries):")
        for _, row in much_slower_our.iterrows():
            print(f"  {row['template']}[{row['query_index']}]: "
                  f"{row['speedup_our']:.1f}x slower "
                  f"({row['OurPrototype']:.2f} ms vs {row['InteGraal']:.2f} ms)")

        faster_our = speedup_df[speedup_df['our_is_faster']].sort_values('speedup_our')
        print(f"\nOur Prototype is faster ({len(faster_our)} queries):")
        for _, row in faster_our.head(10).iterrows():
            print(f"  {row['template']}[{row['query_index']}]: "
                  f"{(1.0/row['speedup_our']):.1f}x faster "
                  f"({row['OurPrototype']:.4f} ms vs {row['InteGraal']:.4f} ms)")

    # Colleague analysis
    if 'speedup_colleague' in speedup_df.columns:
        much_slower_colleague = speedup_df[speedup_df['speedup_colleague'] > 10.0].sort_values('speedup_colleague', ascending=False)
        print(f"\n=== COLLEAGUE IMPLEMENTATION ===")
        print(f"Colleague is >10x slower ({len(much_slower_colleague)} queries):")
        for _, row in much_slower_colleague.iterrows():
            print(f"  {row['template']}[{row['query_index']}]: "
                  f"{row['speedup_colleague']:.1f}x slower "
                  f"({row['Colleague']:.2f} ms vs {row['InteGraal']:.2f} ms)")

        faster_colleague = speedup_df[speedup_df['colleague_is_faster']].sort_values('speedup_colleague')
        print(f"\nColleague is faster ({len(faster_colleague)} queries):")
        for _, row in faster_colleague.head(5).iterrows():
            print(f"  {row['template']}[{row['query_index']}]: "
                  f"{(1.0/row['speedup_colleague']):.1f}x faster "
                  f"({row['Colleague']:.4f} ms vs {row['InteGraal']:.4f} ms)")

    return speedup_df


def plot_bar_chart_comparison(summary):
    """Create bar chart comparing overall performance."""
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))

    systems = summary['system'].values
    x = np.arange(len(systems))
    colors = ['steelblue', 'mediumseagreen', 'coral'][:len(systems)]

    # Mean query time
    ax = axes[0]
    bars = ax.bar(x, summary['mean_time_ms'].values, alpha=0.8, edgecolor='black',
                  color=colors)
    ax.set_xlabel('System', fontsize=12, fontweight='bold')
    ax.set_ylabel('Mean Query Time (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Mean Query Execution Time', fontsize=13, fontweight='bold')
    ax.set_xticks(x)
    ax.set_xticklabels(systems, fontsize=10)
    ax.grid(axis='y', alpha=0.3)

    # Add values on bars
    for i, bar in enumerate(bars):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2., height,
                f'{height:.2f} ms', ha='center', va='bottom', fontsize=9)

    # Median query time
    ax = axes[1]
    bars = ax.bar(x, summary['median_time_ms'].values, alpha=0.8, edgecolor='black',
                  color=colors)
    ax.set_xlabel('System', fontsize=12, fontweight='bold')
    ax.set_ylabel('Median Query Time (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Median Query Execution Time', fontsize=13, fontweight='bold')
    ax.set_xticks(x)
    ax.set_xticklabels(systems, fontsize=10)
    ax.grid(axis='y', alpha=0.3)

    for i, bar in enumerate(bars):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2., height,
                f'{height:.4f} ms', ha='center', va='bottom', fontsize=9)

    # Throughput
    ax = axes[2]
    bars = ax.bar(x, summary['throughput_qps'].values, alpha=0.8, edgecolor='black',
                  color=colors)
    ax.set_xlabel('System', fontsize=12, fontweight='bold')
    ax.set_ylabel('Throughput (queries/sec)', fontsize=12, fontweight='bold')
    ax.set_title('Query Throughput', fontsize=13, fontweight='bold')
    ax.set_xticks(x)
    ax.set_xticklabels(systems, fontsize=10)
    ax.grid(axis='y', alpha=0.3)

    for i, bar in enumerate(bars):
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2., height,
                f'{height:.1f} q/s', ha='center', va='bottom', fontsize=9)

    plt.tight_layout()
    output_file = OUTPUT_DIR / "comparison_bar_chart.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "comparison_bar_chart.png", bbox_inches='tight', dpi=300)
    print(f"\n✓ Bar chart saved to: {output_file}")
    plt.close()


def plot_sorted_times_comparison(df):
    """Create sorted time plot comparing all systems."""
    fig, ax = plt.subplots(figsize=(12, 6))

    systems_info = [
        ('OurPrototype', 'Our Prototype', 'steelblue'),
        ('Colleague', 'Colleague', 'mediumseagreen'),
        ('InteGraal', 'InteGraal', 'coral')
    ]

    for system_id, label, color in systems_info:
        if system_id in df['system'].values:
            system_data = df[df['system'] == system_id].sort_values('mean_ms')
            times = system_data['mean_ms'].values
            ranks = np.arange(1, len(times) + 1)
            ax.plot(ranks, times, linewidth=2.5, alpha=0.9, label=label, color=color)

    ax.set_xlabel('Query Rank (sorted by mean time)', fontsize=12, fontweight='bold')
    ax.set_ylabel('Mean Query Time (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Performance Profiles: System Comparison', fontsize=14, fontweight='bold')
    ax.set_yscale('log')
    ax.grid(alpha=0.3)
    ax.legend(fontsize=11, loc='upper left')

    plt.tight_layout()
    output_file = OUTPUT_DIR / "comparison_sorted_times.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "comparison_sorted_times.png", bbox_inches='tight', dpi=300)
    print(f"✓ Sorted times comparison saved to: {output_file}")
    plt.close()

def generate_comparison_report(df, summary, speedup_df, stat_results):
    """Generate comprehensive comparison report."""
    report_file = RESULTS_DIR / "comparison_report.md"

    with open(report_file, 'w') as f:
        f.write("# System Comparison Report\n\n")
        f.write("## Overview\n\n")
        f.write("This report compares the performance of three RDF query engines:\n")
        f.write("- **Our Prototype**: Custom RDFHexaStore implementation\n")
        f.write("- **Colleague**: Alternative RDFHexaStore implementation\n")
        f.write("- **InteGraal**: Reference implementation (SimpleInMemoryGraphStore)\n\n")

        # Summary statistics
        f.write("## Summary Statistics\n\n")

        # Check which systems are present
        systems = summary['system'].tolist()
        has_colleague = 'Colleague' in systems

        if has_colleague:
            f.write("| Metric | Our Prototype | Colleague | InteGraal |\n")
            f.write("|--------|---------------|-----------|------------|\n")
        else:
            f.write("| Metric | Our Prototype | InteGraal |\n")
            f.write("|--------|---------------|-----------||\n")

        our_summary = summary[summary['system'] == 'OurPrototype'].iloc[0]
        int_summary = summary[summary['system'] == 'InteGraal'].iloc[0]
        if has_colleague:
            col_summary = summary[summary['system'] == 'Colleague'].iloc[0]

        # Write metrics
        if has_colleague:
            f.write(f"| **Queries benchmarked** | {our_summary['query_count']} | {col_summary['query_count']} | {int_summary['query_count']} |\n")
            f.write(f"| **Mean query time** | {our_summary['mean_time_ms']:.2f} ms | {col_summary['mean_time_ms']:.2f} ms | {int_summary['mean_time_ms']:.2f} ms |\n")
            f.write(f"| **Median query time** | {our_summary['median_time_ms']:.3f} ms | {col_summary['median_time_ms']:.3f} ms | {int_summary['median_time_ms']:.3f} ms |\n")
            f.write(f"| **95th percentile** | {our_summary['p95_ms']:.2f} ms | {col_summary['p95_ms']:.2f} ms | {int_summary['p95_ms']:.2f} ms |\n")
            f.write(f"| **Throughput** | {our_summary['throughput_qps']:.1f} q/s | {col_summary['throughput_qps']:.1f} q/s | {int_summary['throughput_qps']:.1f} q/s |\n\n")
        else:
            f.write(f"| **Queries benchmarked** | {our_summary['query_count']} | {int_summary['query_count']} |\n")
            f.write(f"| **Mean query time** | {our_summary['mean_time_ms']:.4f} ms | {int_summary['mean_time_ms']:.4f} ms |\n")
            f.write(f"| **Median query time** | {our_summary['median_time_ms']:.4f} ms | {int_summary['median_time_ms']:.4f} ms |\n")
            f.write(f"| **95th percentile** | {our_summary['p95_ms']:.4f} ms | {int_summary['p95_ms']:.4f} ms |\n")
            f.write(f"| **Throughput** | {our_summary['throughput_qps']:.2f} q/s | {int_summary['throughput_qps']:.2f} q/s |\n\n")

        # Statistical significance
        f.write("## Statistical Significance\n\n")
        f.write(f"**Wilcoxon Signed-Rank Test** (paired, non-parametric, vs InteGraal baseline):\n\n")

        for system_name, (stat, p_val) in stat_results.items():
            f.write(f"**{system_name} vs InteGraal:**\n")
            f.write(f"- p-value: {p_val:.6f}\n")
            if p_val < 0.001:
                f.write(f"- Result: Highly significant difference (p < 0.001)\n\n")
            elif p_val < 0.05:
                f.write(f"- Result: Significant difference (p < 0.05)\n\n")
            else:
                f.write(f"- Result: No significant difference (p >= 0.05)\n\n")

        # Performance breakdown
        f.write("## Performance Breakdown (vs InteGraal)\n\n")

        if 'speedup_our' in speedup_df.columns:
            faster_our = speedup_df[speedup_df['our_is_faster']]
            slower_our = speedup_df[~speedup_df['our_is_faster']]
            f.write(f"**Our Prototype:**\n")
            f.write(f"- Faster: {len(faster_our)} queries ({100*len(faster_our)/len(speedup_df):.1f}%)\n")
            f.write(f"- Slower: {len(slower_our)} queries ({100*len(slower_our)/len(speedup_df):.1f}%)\n\n")

        if 'speedup_colleague' in speedup_df.columns:
            faster_col = speedup_df[speedup_df['colleague_is_faster']]
            slower_col = speedup_df[~speedup_df['colleague_is_faster']]
            f.write(f"**Colleague:**\n")
            f.write(f"- Faster: {len(faster_col)} queries ({100*len(faster_col)/len(speedup_df):.1f}%)\n")
            f.write(f"- Slower: {len(slower_col)} queries ({100*len(slower_col)/len(speedup_df):.1f}%)\n\n")

        # Our Prototype problematic queries
        if 'speedup_our' in speedup_df.columns:
            very_slow_our = speedup_df[speedup_df['speedup_our'] > 10.0]
            f.write(f"### Our Prototype: >10x slower ({len(very_slow_our)} queries)\n\n")
            f.write("| Query | Our Time (ms) | InteGraal Time (ms) | Slowdown |\n")
            f.write("|-------|---------------|---------------------|----------|\n")
            for _, row in very_slow_our.sort_values('speedup_our', ascending=False).iterrows():
                f.write(f"| {row['template']}[{row['query_index']}] | {row['OurPrototype']:.2f} | {row['InteGraal']:.2f} | {row['speedup_our']:.1f}x |\n")
            f.write("\n")

        # Colleague problematic queries
        if 'speedup_colleague' in speedup_df.columns:
            very_slow_col = speedup_df[speedup_df['speedup_colleague'] > 10.0]
            f.write(f"### Colleague: >10x slower ({len(very_slow_col)} queries)\n\n")
            f.write("| Query | Colleague Time (ms) | InteGraal Time (ms) | Slowdown |\n")
            f.write("|-------|---------------------|---------------------|----------|\n")
            for _, row in very_slow_col.sort_values('speedup_colleague', ascending=False).head(10).iterrows():
                f.write(f"| {row['template']}[{row['query_index']}] | {row['Colleague']:.2f} | {row['InteGraal']:.2f} | {row['speedup_colleague']:.1f}x |\n")
            f.write("\n")

        # Interpretation
        f.write("## Interpretation\n\n")
        f.write("### Key Findings\n\n")
        f.write("1. **Median Performance**: Our Prototype shows a better median time ")
        f.write(f"({our_summary['median_time_ms']:.4f} ms vs {int_summary['median_time_ms']:.4f} ms), ")
        f.write("indicating good performance on most queries.\n\n")

        f.write("2. **Mean Performance**: However, the mean time is much higher ")
        f.write(f"({our_summary['mean_time_ms']:.2f} ms vs {int_summary['mean_time_ms']:.2f} ms), ")
        f.write(f"which is {our_summary['mean_time_ms']/int_summary['mean_time_ms']:.1f}x slower. ")
        f.write("This discrepancy is caused by a few very slow queries.\n\n")

        if 'speedup_our' in speedup_df.columns:
            very_slow_analysis = speedup_df[speedup_df['speedup_our'] > 10.0]
            f.write(f"3. **Performance Bottleneck**: {len(very_slow_analysis)} queries show severe performance degradation (>10x slower):\n")
            for _, row in very_slow_analysis.sort_values('speedup_our', ascending=False).head(5).iterrows():
                f.write(f"   - `{row['template']}[{row['query_index']}]`: {row['speedup_our']:.0f}x slower ({row['OurPrototype']:.2f} ms vs {row['InteGraal']:.2f} ms)\n")
        f.write("\n")

        f.write("### Hypothesis: Join Ordering and Execution Strategy\n\n")
        f.write("The problematic queries (`Q_3_location_gender_type` and `Q_3_nationality_gender_type`) ")
        f.write("have 3 triple patterns and produce non-empty results. These queries likely suffer from:\n\n")
        f.write("- **Suboptimal join ordering**: The current selectivity-based ordering may not account ")
        f.write("for intermediate result sizes after joins.\n")
        f.write("- **Nested loop join overhead**: The current implementation uses nested loops which ")
        f.write("can be inefficient when intermediate results are large.\n")
        f.write("- **Index structure**: InteGraal may use more efficient data structures for multi-pattern queries.\n\n")

        f.write("### Recommendations\n\n")
        f.write("1. **Profile the slow queries**: Use a profiler to identify the bottleneck in the execution pipeline.\n")
        f.write("2. **Improve join ordering**: Consider cardinality estimation and intermediate result sizes.\n")
        f.write("3. **Alternative join algorithms**: Consider hash joins or sort-merge joins for better performance.\n")
        f.write("4. **Index optimization**: Review index structure for multi-attribute lookups.\n\n")

    print(f"\n✓ Comparison report saved to: {report_file}")


def main():
    """Main analysis function."""
    print("=" * 60)
    print("SYSTEM COMPARISON ANALYSIS")
    print("=" * 60)

    # Load data
    df, summary = load_comparison_data()
    if df is None:
        return

    # Compute speedup
    speedup_df = compute_speedup(df)

    # Statistical testing
    stat_results = statistical_significance_test(df)

    # Performance gap analysis
    speedup_df = identify_performance_gaps(speedup_df)

    # Generate visualizations
    print("\n" + "=" * 60)
    print("GENERATING VISUALIZATIONS")
    print("=" * 60)

    plot_bar_chart_comparison(summary)
    plot_sorted_times_comparison(df)

    # Generate report
    print("\n" + "=" * 60)
    print("GENERATING COMPARISON REPORT")
    print("=" * 60)
    generate_comparison_report(df, summary, speedup_df, stat_results)

    print("\n" + "=" * 60)
    print("✓ ANALYSIS COMPLETED")
    print("=" * 60)
    print(f"\nAll outputs saved to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
