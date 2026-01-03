#!/usr/bin/env python3
"""
Query Workload Validation - Analysis on queries
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
from pathlib import Path
import sys

# Configuration
RESULTS_DIR = Path("results/phase2")
OUTPUT_DIR = Path("results/phase2/figures")
DATA_FILES = {
    "500K": RESULTS_DIR / "workload_analysis_data_500K.csv",
    "2M": RESULTS_DIR / "workload_analysis_data_2M.csv"
}

# Create output directory
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Visualization style
sns.set_style("whitegrid")
plt.rcParams['figure.dpi'] = 300
plt.rcParams['font.size'] = 10


def load_data():
    """Load workload analysis data for both datasets."""
    data = {}
    for name, file_path in DATA_FILES.items():
        if not file_path.exists():
            print(f"Error: Data file not found: {file_path}")
            sys.exit(1)
        data[name] = pd.read_csv(file_path)
        print(f"Loaded {len(data[name])} queries for {name} dataset")
    return data


def analyze_characteristics(data):
    """Analyze Query Characteristics"""
    print("\n" + "=" * 60)
    print("ANALYZE QUERY CHARACTERISTICS")
    print("=" * 60)

    results = {}

    for dataset_name, df in data.items():
        print(f"\n--- Analysis for {dataset_name} dataset ---")

        total_queries = len(df)
        print(f"Total queries: {total_queries}")

        # Count zero-result queries
        zero_results = (df['result_count'] == 0).sum()
        zero_percent = 100.0 * zero_results / total_queries if total_queries > 0 else 0
        print(f"Zero-result queries: {zero_results} ({zero_percent:.1f}%)")

        # Unique templates
        unique_templates = df['template'].nunique()
        print(f"Unique templates: {unique_templates}")

        # Distribution statistics
        print(f"\nResult count statistics:")
        print(f"  Mean: {df['result_count'].mean():.2f}")
        print(f"  Median: {df['result_count'].median():.2f}")
        print(f"  Std Dev: {df['result_count'].std():.2f}")
        print(f"  Min: {df['result_count'].min()}")
        print(f"  Max: {df['result_count'].max()}")

        # Queries by selectivity
        high_selectivity = (df['result_count'] < 10).sum()
        medium_selectivity = ((df['result_count'] >= 10) & (df['result_count'] < 100)).sum()
        low_selectivity = (df['result_count'] >= 100).sum()

        print(f"\nSelectivity distribution:")
        print(f"  High selectivity (<10 results): {high_selectivity} ({100.0 * high_selectivity / total_queries:.1f}%)")
        print(f"  Medium selectivity (10-99 results): {medium_selectivity} ({100.0 * medium_selectivity / total_queries:.1f}%)")
        print(f"  Low selectivity (>=100 results): {low_selectivity} ({100.0 * low_selectivity / total_queries:.1f}%)")

        results[dataset_name] = {
            'total_queries': total_queries,
            'zero_results': zero_results,
            'zero_percent': zero_percent,
            'unique_templates': unique_templates,
            'high_selectivity': high_selectivity,
            'medium_selectivity': medium_selectivity,
            'low_selectivity': low_selectivity
        }

    # Create validation table
    validation_table = pd.DataFrame([
        {
            'Metric': 'Total unique queries',
            '500K Dataset': results['500K']['total_queries'],
            '2M Dataset': results['2M']['total_queries'],
            'Notes': f"{results['500K']['unique_templates']} templates"
        },
        {
            'Metric': 'Zero-result queries',
            '500K Dataset': f"{results['500K']['zero_results']} ({results['500K']['zero_percent']:.1f}%)",
            '2M Dataset': f"{results['2M']['zero_results']} ({results['2M']['zero_percent']:.1f}%)",
            'Notes': 'Queries returning no results'
        }
    ])

    # Save validation table
    validation_file = OUTPUT_DIR / "table_validation.csv"
    validation_table.to_csv(validation_file, index=False)
    print(f"\n✓ Validation table saved to: {validation_file}")

    return results


def cardinality_histogram(data):
    """Create Result Cardinality Histogram"""
    print("\n" + "=" * 60)
    print("CREATE RESULT CARDINALITY HISTOGRAM")
    print("=" * 60)

    fig, axes = plt.subplots(1, 2, figsize=(14, 5))

    for idx, (dataset_name, df) in enumerate(data.items()):
        ax = axes[idx]

        result_counts = df['result_count'].values
        zero_count = (result_counts == 0).sum()
        total_count = len(result_counts)

        # Create bins
        bins = [0, 1, 10, 100, 1000, np.inf]
        hist, _ = np.histogram(result_counts, bins=bins)

        # Bar chart
        labels = ['0', '1-10', '11-100', '101-1000', '1000+']
        x_pos = np.arange(len(labels))

        bars = ax.bar(x_pos, hist, alpha=0.75, edgecolor='black', linewidth=1.2)

        # Color the zero-results bar differently
        bars[0].set_color('coral')
        for i in range(1, len(bars)):
            bars[i].set_color('steelblue')

        ax.set_xlabel('Number of Results', fontsize=11, fontweight='bold')
        ax.set_ylabel('Number of Queries', fontsize=11, fontweight='bold')
        ax.set_title(f'{dataset_name} Dataset (Deduplicated)\n({total_count} unique queries, {zero_count} with 0 results)',
                     fontsize=12, fontweight='bold')
        ax.set_xticks(x_pos)
        ax.set_xticklabels(labels, rotation=0)
        ax.grid(axis='y', alpha=0.3)

        # Add percentage labels on bars
        for i, (bar, count) in enumerate(zip(bars, hist)):
            height = bar.get_height()
            if height > 0:
                percentage = 100.0 * count / total_count
                ax.text(bar.get_x() + bar.get_width() / 2., height,
                        f'{count}\n({percentage:.1f}%)',
                        ha='center', va='bottom', fontsize=9)

    plt.suptitle('Distribution of Query Result Cardinalities',
                 fontsize=14, fontweight='bold', y=1.02)
    plt.tight_layout()

    output_file = OUTPUT_DIR / "figure1_cardinality_distribution.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "figure1_cardinality_distribution.png", bbox_inches='tight', dpi=300)
    print(f"✓ Figure 1 saved to: {output_file}")
    plt.close()


def zero_results_by_template(data):
    """Zero-Result Query Analysis by Template"""
    print("\n" + "=" * 60)
    print("ZERO-RESULT QUERY ANALYSIS BY TEMPLATE")
    print("=" * 60)

    zero_by_template = {}

    for dataset_name, df in data.items():
        template_stats = df.groupby('template').apply(
            lambda x: 100.0 * (x['result_count'] == 0).sum() / len(x), include_groups=False
        ).sort_values(ascending=False)
        zero_by_template[dataset_name] = template_stats

        print(f"\n{dataset_name} dataset - Zero-result queries by template:")
        for template, percentage in template_stats.items():
            count = (df[df['template'] == template]['result_count'] == 0).sum()
            total = len(df[df['template'] == template])
            print(f"  {template}: {count}/{total} ({percentage:.1f}%)")

    # Create visualization
    fig, ax = plt.subplots(figsize=(14, 7))

    templates = list(zero_by_template['500K'].index)
    x = np.arange(len(templates))
    width = 0.35

    bars1 = ax.bar(x - width/2, zero_by_template['500K'].values, width,
                   label='500K', alpha=0.8, color='steelblue', edgecolor='black')
    bars2 = ax.bar(x + width/2, zero_by_template['2M'].values, width,
                   label='2M', alpha=0.8, color='coral', edgecolor='black')

    ax.set_xlabel('Query Template', fontsize=12, fontweight='bold')
    ax.set_ylabel('Percentage of Zero-Result Queries (%)', fontsize=12, fontweight='bold')
    ax.set_title('Zero-Result Queries by Template',
                 fontsize=14, fontweight='bold')
    ax.set_xticks(x)
    ax.set_xticklabels(templates, rotation=45, ha='right', fontsize=9)
    ax.legend(fontsize=11)
    ax.grid(axis='y', alpha=0.3)
    ax.axhline(y=50, color='red', linestyle='--', alpha=0.5, linewidth=2, label='50% threshold')

    plt.tight_layout()

    output_file = OUTPUT_DIR / "figure2_zero_results_by_template.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "figure2_zero_results_by_template.png", bbox_inches='tight', dpi=300)
    print(f"\n✓ Figure 2 saved to: {output_file}")
    plt.close()

    return zero_by_template

def main():
    """Main execution function."""
    print("=" * 60)
    print("DEDUPLICATED QUERY WORKLOAD VALIDATION")
    print("=" * 60)

    try:
        data = load_data()
        characteristics = analyze_characteristics(data)
        cardinality_histogram(data)
        zero_by_template = zero_results_by_template(data)

        print("\n" + "=" * 60)
        print("✓ DEDUPLICATED ANALYSIS COMPLETED")
        print("=" * 60)
        print(f"\nAll outputs saved to: {OUTPUT_DIR}")

    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
