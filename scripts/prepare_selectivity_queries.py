#!/usr/bin/env python3
"""
Prepare Selectivity-Based Query Sets for Experiment B

Creates two query sets:
1. High selectivity: result_count < 100 (simple, fast queries)
2. Low selectivity: result_count >= 100 (complex, slow queries)
"""

import pandas as pd
import random
from pathlib import Path

# Configuration
PHASE4_RESULTS = Path("results/phase4/benchmark_detailed_500K.csv")
OUTPUT_DIR = Path("results/phase5")
QUERIES_PER_SET = 25

# Create output directory
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

def main():
    print("=" * 60)
    print("PREPARING SELECTIVITY-BASED QUERY SETS")
    print("=" * 60)
    print()

    # Load Phase 4 results
    print(f"Loading results from: {PHASE4_RESULTS}")
    df = pd.read_csv(PHASE4_RESULTS)
    print(f"Loaded {len(df)} query results")
    print()

    # Define selectivity categories
    high_sel = df[df['result_count'] < 100].copy()
    low_sel = df[df['result_count'] >= 100].copy()

    print(f"High selectivity queries (<100 results): {len(high_sel)}")
    print(f"Low selectivity queries (≥100 results): {len(low_sel)}")
    print()

    # Sample queries
    random.seed(42)

    if len(high_sel) >= QUERIES_PER_SET:
        high_sel_sample = high_sel.sample(n=QUERIES_PER_SET, random_state=42)
    else:
        print(f"⚠ Warning: Only {len(high_sel)} high-sel queries available, using all")
        high_sel_sample = high_sel

    if len(low_sel) >= QUERIES_PER_SET:
        low_sel_sample = low_sel.sample(n=QUERIES_PER_SET, random_state=42)
    else:
        print(f"⚠ Warning: Only {len(low_sel)} low-sel queries available, using all")
        low_sel_sample = low_sel

    print(f"\\nSampled {len(high_sel_sample)} high-selectivity queries")
    print(f"Sampled {len(low_sel_sample)} low-selectivity queries")
    print()

    # Save query lists
    high_sel_file = OUTPUT_DIR / "queries_high_selectivity.csv"
    low_sel_file = OUTPUT_DIR / "queries_low_selectivity.csv"

    high_sel_sample[['template', 'query_index', 'result_count', 'mean_ms']].to_csv(
        high_sel_file, index=False
    )
    low_sel_sample[['template', 'query_index', 'result_count', 'mean_ms']].to_csv(
        low_sel_file, index=False
    )

    print(f"✓ High-selectivity queries saved to: {high_sel_file}")
    print(f"✓ Low-selectivity queries saved to: {low_sel_file}")
    print()

    # Print statistics
    print("=" * 60)
    print("QUERY SET STATISTICS")
    print("=" * 60)
    print()

    print("High Selectivity Set:")
    print(f"  Count: {len(high_sel_sample)}")
    print(f"  Result count range: {high_sel_sample['result_count'].min()} - {high_sel_sample['result_count'].max()}")
    print(f"  Mean time: {high_sel_sample['mean_ms'].mean():.4f} ms")
    print(f"  Median time: {high_sel_sample['mean_ms'].median():.4f} ms")
    print()

    print("Low Selectivity Set:")
    print(f"  Count: {len(low_sel_sample)}")
    print(f"  Result count range: {low_sel_sample['result_count'].min()} - {low_sel_sample['result_count'].max()}")
    print(f"  Mean time: {low_sel_sample['mean_ms'].mean():.4f} ms")
    print(f"  Median time: {low_sel_sample['mean_ms'].median():.4f} ms")
    print()

    # Show template distribution
    print("Template Distribution:")
    print("\\nHigh Selectivity:")
    print(high_sel_sample['template'].value_counts())
    print("\\nLow Selectivity:")
    print(low_sel_sample['template'].value_counts())

if __name__ == "__main__":
    main()
