#!/usr/bin/env python3
"""
Query Order Effect Analysis

Analyzes whether query execution order affects measured performance.

Statistical test: Paired t-test (comparing original vs shuffled ordering)
Visualization: Box plots, time series, distribution comparisons
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
from pathlib import Path
from scipy import stats

# Configuration
RESULTS_DIR = Path("results/phase7")
OUTPUT_DIR = Path("results/phase7/figures")

# Create output directory
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Visualization style
sns.set_style("whitegrid")
plt.rcParams['figure.dpi'] = 300
plt.rcParams['font.size'] = 10


def load_data():
    """Load experimental results."""
    detailed_file = RESULTS_DIR / "order_experiment_detailed.csv"
    totals_file = RESULTS_DIR / "order_experiment_totals.csv"

    if not detailed_file.exists():
        print(f"Error: Results not found: {detailed_file}")
        return None, None

    detailed = pd.read_csv(detailed_file)
    totals = pd.read_csv(totals_file)

    print(f"Loaded data:")
    print(f"  - Detailed: {len(detailed)} query executions")
    print(f"  - Totals: {len(totals)} workload executions")
    print(f"  - Orderings: {detailed['ordering'].unique()}")
    print(f"  - Repetitions per ordering: {detailed.groupby('ordering')['repetition'].nunique().values[0]}")

    return detailed, totals


def workload_level_analysis(totals):
    """Analyze workload-level total times."""
    print("\n" + "=" * 60)
    print("WORKLOAD-LEVEL ANALYSIS")
    print("=" * 60)

    # Summary statistics
    summary = totals.groupby('ordering')['total_time_ms'].agg(['mean', 'std', 'min', 'max'])
    print("\nWorkload Total Time Summary (ms):")
    print(summary)

    # Paired t-test
    original_times = totals[totals['ordering'] == 'original']['total_time_ms'].values
    shuffled_times = totals[totals['ordering'] == 'shuffled']['total_time_ms'].values

    # Paired t-test (each repetition paired)
    t_stat, p_value = stats.ttest_rel(original_times, shuffled_times)

    print(f"\n**Paired t-test** (Original vs Shuffled):")
    print(f"  t-statistic: {t_stat:.4f}")
    print(f"  p-value: {p_value:.6f}")

    if p_value < 0.001:
        print(f"  Result: Highly significant difference (p < 0.001)")
        interpretation = "SIGNIFICANT"
    elif p_value < 0.01:
        print(f"  Result: Very significant difference (p < 0.01)")
        interpretation = "SIGNIFICANT"
    elif p_value < 0.05:
        print(f"  Result: Significant difference (p < 0.05)")
        interpretation = "SIGNIFICANT"
    else:
        print(f"  Result: No significant difference (p >= 0.05)")
        interpretation = "NOT SIGNIFICANT"

    # Effect size (Cohen's d)
    mean_diff = np.mean(original_times - shuffled_times)
    pooled_std = np.sqrt((np.var(original_times) + np.var(shuffled_times)) / 2)
    cohens_d = mean_diff / pooled_std if pooled_std > 0 else 0

    print(f"\n**Effect Size** (Cohen's d): {cohens_d:.4f}")
    if abs(cohens_d) < 0.2:
        print(f"  Interpretation: Negligible effect")
    elif abs(cohens_d) < 0.5:
        print(f"  Interpretation: Small effect")
    elif abs(cohens_d) < 0.8:
        print(f"  Interpretation: Medium effect")
    else:
        print(f"  Interpretation: Large effect")

    return t_stat, p_value, cohens_d, interpretation


def query_level_analysis(detailed):
    """Analyze per-query timing consistency across orderings."""
    print("\n" + "=" * 60)
    print("QUERY-LEVEL ANALYSIS")
    print("=" * 60)

    # Average time per query across all repetitions
    query_times = detailed.groupby(['ordering', 'query_id'])['time_ms'].mean().unstack(fill_value=0)

    # Check if times are similar across orderings
    if 'original' in query_times.columns and 'shuffled' in query_times.columns:
        correlation = query_times['original'].corr(query_times['shuffled'])
        print(f"\nCorrelation between orderings: {correlation:.4f}")

        # Queries with biggest differences
        query_times['diff'] = abs(query_times['original'] - query_times['shuffled'])
        query_times['ratio'] = query_times[['original', 'shuffled']].max(axis=1) / query_times[['original', 'shuffled']].min(axis=1)

        top_diff = query_times.nlargest(5, 'diff')
        print("\nTop 5 queries with largest absolute differences:")
        for query_id in top_diff.index:
            orig = query_times.loc[query_id, 'original']
            shuf = query_times.loc[query_id, 'shuffled']
            diff = query_times.loc[query_id, 'diff']
            print(f"  {query_id}: Original={orig:.2f}ms, Shuffled={shuf:.2f}ms, Diff={diff:.2f}ms")


def position_effect_analysis(detailed):
    """Test if position in execution order affects timing."""
    print("\n" + "=" * 60)
    print("POSITION EFFECT ANALYSIS")
    print("=" * 60)

    # For each ordering, check correlation between position and time
    for ordering in detailed['ordering'].unique():
        ordering_data = detailed[detailed['ordering'] == ordering]

        # Average time by position
        position_avg = ordering_data.groupby('position')['time_ms'].mean()

        # Spearman correlation (robust to outliers)
        corr, p_val = stats.spearmanr(ordering_data['position'], ordering_data['time_ms'])

        print(f"\n{ordering.upper()} ordering:")
        print(f"  Position-Time correlation: {corr:.4f} (p={p_val:.6f})")

        if p_val < 0.05:
            if corr > 0:
                print(f"  → Queries get SLOWER as execution progresses (significant)")
            else:
                print(f"  → Queries get FASTER as execution progresses (significant)")
        else:
            print(f"  → No significant position effect")


def plot_workload_times(totals):
    """Box plot comparing workload total times."""
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))

    # Box plot
    ax = axes[0]
    data = [totals[totals['ordering'] == ord]['total_time_ms'].values
            for ord in ['original', 'shuffled']]
    bp = ax.boxplot(data, labels=['Original', 'Shuffled'],
                    patch_artist=True, showmeans=True)

    for patch in bp['boxes']:
        patch.set_facecolor('steelblue')
        patch.set_alpha(0.7)

    ax.set_ylabel('Total Workload Time (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Workload Time Distribution by Ordering', fontsize=13, fontweight='bold')
    ax.grid(axis='y', alpha=0.3)

    # Add mean values as text
    for i, ord in enumerate(['original', 'shuffled']):
        mean_val = totals[totals['ordering'] == ord]['total_time_ms'].mean()
        ax.text(i+1, mean_val, f'{mean_val:.0f} ms',
                ha='center', va='bottom', fontsize=9, fontweight='bold')

    # Time series plot
    ax = axes[1]
    for ordering in ['original', 'shuffled']:
        data = totals[totals['ordering'] == ordering].sort_values('repetition')
        ax.plot(data['repetition'], data['total_time_ms'],
                marker='o', linewidth=2, markersize=8,
                label=ordering.capitalize(), alpha=0.8)

    ax.set_xlabel('Repetition', fontsize=12, fontweight='bold')
    ax.set_ylabel('Total Workload Time (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Workload Time Across Repetitions', fontsize=13, fontweight='bold')
    ax.legend(fontsize=11)
    ax.grid(alpha=0.3)

    plt.tight_layout()
    output_file = OUTPUT_DIR / "workload_comparison.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "workload_comparison.png", bbox_inches='tight', dpi=300)
    print(f"\n✓ Workload comparison saved to: {output_file}")
    plt.close()


def plot_position_effects(detailed):
    """Plot timing vs position in execution order."""
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))

    orderings = ['original', 'shuffled']
    colors = ['steelblue', 'coral']

    for idx, (ordering, color) in enumerate(zip(orderings, colors)):
        ax = axes[idx]
        ordering_data = detailed[detailed['ordering'] == ordering]

        # Average time by position
        position_avg = ordering_data.groupby('position')['time_ms'].mean()
        position_std = ordering_data.groupby('position')['time_ms'].std()

        ax.plot(position_avg.index, position_avg.values,
                marker='o', linewidth=2, markersize=6,
                color=color, alpha=0.8, label='Mean')
        ax.fill_between(position_avg.index,
                        position_avg.values - position_std.values,
                        position_avg.values + position_std.values,
                        alpha=0.3, color=color, label='±1 std dev')

        ax.set_xlabel('Position in Execution Order', fontsize=12, fontweight='bold')
        ax.set_ylabel('Query Time (ms)', fontsize=12, fontweight='bold')
        ax.set_title(f'Position Effect: {ordering.capitalize()} Ordering',
                    fontsize=13, fontweight='bold')
        ax.legend(fontsize=10)
        ax.grid(alpha=0.3)

    plt.tight_layout()
    output_file = OUTPUT_DIR / "position_effects.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "position_effects.png", bbox_inches='tight', dpi=300)
    print(f"✓ Position effects saved to: {output_file}")
    plt.close()


def plot_query_consistency(detailed):
    """Plot per-query consistency across orderings."""
    # Average time per query across all repetitions and positions
    query_times = detailed.groupby(['ordering', 'query_id'])['time_ms'].mean().unstack(fill_value=0)

    if 'original' not in query_times.columns or 'shuffled' not in query_times.columns:
        print("⚠ Skipping query consistency plot (missing orderings)")
        return

    fig, ax = plt.subplots(figsize=(10, 6))

    ax.scatter(query_times['original'], query_times['shuffled'],
              alpha=0.6, s=100, edgecolors='black', linewidth=0.5,
              color='steelblue')

    # Add diagonal line (perfect consistency)
    max_val = max(query_times['original'].max(), query_times['shuffled'].max())
    ax.plot([0, max_val], [0, max_val], 'r--', linewidth=2, alpha=0.7,
            label='Perfect Consistency')

    ax.set_xlabel('Original Ordering (ms)', fontsize=12, fontweight='bold')
    ax.set_ylabel('Shuffled Ordering (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Per-Query Time Consistency Across Orderings',
                fontsize=13, fontweight='bold')
    ax.set_xscale('log')
    ax.set_yscale('log')
    ax.grid(alpha=0.3)
    ax.legend(fontsize=11)

    # Add correlation text
    corr = query_times['original'].corr(query_times['shuffled'])
    ax.text(0.05, 0.95, f'Correlation: {corr:.4f}',
            transform=ax.transAxes, fontsize=11, verticalalignment='top',
            bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))

    plt.tight_layout()
    output_file = OUTPUT_DIR / "query_consistency.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "query_consistency.png", bbox_inches='tight', dpi=300)
    print(f"✓ Query consistency saved to: {output_file}")
    plt.close()


def generate_report(totals, detailed, t_stat, p_value, cohens_d, interpretation):
    """Generate comprehensive analysis report."""
    report_file = RESULTS_DIR / "order_effect_report.md"

    with open(report_file, 'w') as f:
        f.write("# Query Order Effect Analysis Report\n\n")

        f.write("## Experimental Setup\n\n")
        f.write("**Objective**: Test whether query execution order affects measured performance\n\n")

        reps = totals['repetition'].nunique()
        queries = detailed['query_id'].nunique()

        f.write("**Configuration**:\n")
        f.write(f"- Dataset: 100K triples\n")
        f.write(f"- Queries: {queries} queries\n")
        f.write(f"- Orderings: Original + Shuffled (seed=42)\n")
        f.write(f"- Repetitions: {reps} per ordering\n")
        f.write(f"- Total executions: {len(detailed)} query executions\n\n")

        f.write("## Statistical Test Results\n\n")

        # Workload times
        orig_mean = totals[totals['ordering'] == 'original']['total_time_ms'].mean()
        orig_std = totals[totals['ordering'] == 'original']['total_time_ms'].std()
        shuf_mean = totals[totals['ordering'] == 'shuffled']['total_time_ms'].mean()
        shuf_std = totals[totals['ordering'] == 'shuffled']['total_time_ms'].std()

        f.write("### Workload Total Times\n\n")
        f.write("| Ordering | Mean (ms) | Std Dev (ms) |\n")
        f.write("|----------|-----------|-------------|\n")
        f.write(f"| Original | {orig_mean:.2f} | {orig_std:.2f} |\n")
        f.write(f"| Shuffled | {shuf_mean:.2f} | {shuf_std:.2f} |\n\n")

        f.write("### Paired t-test\n\n")
        f.write("**Null Hypothesis**: Query execution order has no effect on total workload time\n\n")

        f.write(f"- **t-statistic**: {t_stat:.4f}\n")
        f.write(f"- **p-value**: {p_value:.6f}\n")
        f.write(f"- **Cohen's d**: {cohens_d:.4f}\n\n")

        if interpretation == "SIGNIFICANT":
            f.write(f"**Result**: REJECT null hypothesis (p < 0.05)\n\n")
            f.write(f"Query execution order **DOES** significantly affect measured performance.\n\n")
        else:
            f.write(f"**Result**: FAIL TO REJECT null hypothesis (p >= 0.05)\n\n")
            f.write(f"Query execution order **DOES NOT** significantly affect measured performance.\n\n")

        f.write("## Interpretation\n\n")

        if interpretation == "SIGNIFICANT":
            f.write("### Order Effect Detected\n\n")
            f.write("The statistically significant difference suggests:\n\n")
            f.write("1. **Measurement bias**: Results may depend on execution order\n")
            f.write("2. **Possible causes**:\n")
            f.write("   - JIT compilation patterns differ between orderings\n")
            f.write("   - Cache warming effects not fully addressed by warmup\n")
            f.write("   - Resource accumulation/exhaustion over workload\n\n")
            f.write("3. **Implications**:\n")
            f.write("   - Single-ordering benchmarks may be biased\n")
            f.write("   - Should randomize or counterbalance ordering in experiments\n")
            f.write("   - Phase 4-6 results should be interpreted cautiously\n\n")
        else:
            f.write("### No Order Effect Detected\n\n")
            f.write("The lack of statistical significance suggests:\n\n")
            f.write("1. **Robust measurements**: Results are independent of execution order\n")
            f.write("2. **Validation of methodology**:\n")
            f.write("   - Warmup phase is adequate\n")
            f.write("   - System state is stable throughout workload\n")
            f.write("   - No significant cache/JIT bias\n\n")
            f.write("3. **Implications**:\n")
            f.write("   - Phase 4-6 results are methodologically sound\n")
            f.write("   - Query order does not need to be randomized\n")
            f.write("   - Measurements are reproducible and reliable\n\n")

        # Effect size interpretation
        f.write("### Effect Size\n\n")
        if abs(cohens_d) < 0.2:
            f.write(f"Cohen's d = {cohens_d:.4f} indicates a **negligible effect**.\n")
            f.write(f"Even if statistically significant, the practical difference is minimal.\n\n")
        elif abs(cohens_d) < 0.5:
            f.write(f"Cohen's d = {cohens_d:.4f} indicates a **small effect**.\n")
            f.write(f"The difference is detectable but may not be practically important.\n\n")
        elif abs(cohens_d) < 0.8:
            f.write(f"Cohen's d = {cohens_d:.4f} indicates a **medium effect**.\n")
            f.write(f"The difference is both statistically and practically significant.\n\n")
        else:
            f.write(f"Cohen's d = {cohens_d:.4f} indicates a **large effect**.\n")
            f.write(f"The ordering has a substantial impact on measurements.\n\n")

    print(f"\n✓ Report saved to: {report_file}")


def main():
    """Main analysis function."""
    print("=" * 60)
    print("QUERY ORDER EFFECT ANALYSIS")
    print("=" * 60)

    # Load data
    detailed, totals = load_data()
    if detailed is None or totals is None:
        return

    # Workload-level analysis (primary test)
    t_stat, p_value, cohens_d, interpretation = workload_level_analysis(totals)

    # Query-level analysis
    query_level_analysis(detailed)

    # Position effect analysis
    position_effect_analysis(detailed)

    # Generate visualizations
    print("\n" + "=" * 60)
    print("GENERATING VISUALIZATIONS")
    print("=" * 60)

    plot_workload_times(totals)
    plot_position_effects(detailed)
    plot_query_consistency(detailed)

    # Generate report
    print("\n" + "=" * 60)
    print("GENERATING REPORT")
    print("=" * 60)
    generate_report(totals, detailed, t_stat, p_value, cohens_d, interpretation)

    print("\n" + "=" * 60)
    print("✓ ANALYSIS COMPLETED")
    print("=" * 60)
    print(f"\nAll outputs saved to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
