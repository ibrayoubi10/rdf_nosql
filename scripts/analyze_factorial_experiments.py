#!/usr/bin/env python3
"""
Factorial Experiments Analysis

Performs regression analysis on Experiment A and B using scikit-learn:
- Experiment A: Dataset Size × Memory
- Experiment B: Index Optimization × Selectivity

Generates:
- Regression coefficients with interpretation
- Interaction plots
- Summary tables for report
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.linear_model import LinearRegression
from pathlib import Path
import sys

# Configuration
RESULTS_DIR = Path("results/phase5")
OUTPUT_DIR = RESULTS_DIR / "figures"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Visualization style
sns.set_style("whitegrid")
plt.rcParams['figure.dpi'] = 300
plt.rcParams['font.size'] = 10


def load_experiment_results(experiment_name, config_ids):
    """Load results from all configurations of an experiment."""
    dfs = []
    for config_id in config_ids:
        file_path = RESULTS_DIR / f"experiment_{experiment_name}_{config_id}.csv"
        if not file_path.exists():
            print(f"⚠ Warning: {file_path} not found")
            continue
        df = pd.read_csv(file_path)
        df['time_ms'] = pd.to_numeric(df['time_ms'], errors='coerce')
        if df['time_ms'].isna().any():
            print(f"⚠ Warning: NaN values found in {file_path} after conversion")
        dfs.append(df)
    if not dfs:
        return None

    return pd.concat(dfs, ignore_index=True)


def analyze_experiment_a():
    """Analyze Experiment A: Dataset Size × Memory"""
    print("\\n" + "=" * 60)
    print("EXPERIMENT A: DATASET SIZE × MEMORY")
    print("=" * 60)

    # Load results
    df = load_experiment_results("A", ["A1", "A2", "A3", "A4"])
    if df is None:
        print("❌ No results found for Experiment A")
        return None

    print(f"\\nLoaded {len(df)} query results across 4 configurations")

    # Aggregate to configuration level
    config_means = df.groupby(['config_id', 'dataset_size', 'heap_mb']).agg({
        'time_ms': ['mean', 'median', 'std', 'count']
    }).reset_index()

    config_means.columns = ['config_id', 'dataset_size', 'heap_mb', 'mean_time', 'median_time', 'std_time', 'n_queries']

    print("\\nConfiguration-level results:")
    print(config_means)

    # Encode factors for regression
    config_means['S'] = (config_means['dataset_size'] == '2M').astype(int)  # 0=500K, 1=2M
    config_means['M'] = (config_means['heap_mb'] == 8192).astype(int)     # 0=4GB, 1=8GB
    config_means['S_x_M'] = config_means['S'] * config_means['M']           # Interaction
    config_means['log_time'] = np.log(config_means['mean_time'] + 0.001)   # Avoid log(0)

    # Regression model: log(T) = β₀ + β₁·S + β₂·M + β₁₂·(S×M)
    X = config_means[['S', 'M', 'S_x_M']].values
    y = config_means['log_time'].values

    model = LinearRegression()
    model.fit(X, y)

    # Extract coefficients
    beta0 = model.intercept_
    beta1, beta2, beta12 = model.coef_

    # Calculate R²
    r2 = model.score(X, y)

    # Predictions
    y_pred = model.predict(X)

    # Results dictionary
    results = {
        'data': config_means,
        'model': model,
        'beta0': beta0,
        'beta1': beta1,
        'beta2': beta2,
        'beta12': beta12,
        'r2': r2,
        'X': X,
        'y': y,
        'y_pred': y_pred
    }

    # Print regression results
    print("\\n" + "-" * 60)
    print("REGRESSION RESULTS")
    print("-" * 60)
    print(f"\\nModel: log(time) = β₀ + β₁·Size + β₂·Memory + β₁₂·(Size×Memory)")
    print(f"\\nCoefficients:")
    print(f"  β₀ (Intercept)    = {beta0:.4f}")
    print(f"  β₁ (Size)         = {beta1:.4f}  → exp({beta1:.4f}) = {np.exp(beta1):.3f}× (size effect)")
    print(f"  β₂ (Memory)       = {beta2:.4f}  → exp({beta2:.4f}) = {np.exp(beta2):.3f}× (memory effect)")
    print(f"  β₁₂ (Interaction) = {beta12:.4f}")
    print(f"\\nModel Fit:")
    print(f"  R² = {r2:.4f} ({100*r2:.1f}% variance explained)")

    # Interpretation
    print("\\n" + "-" * 60)
    print("INTERPRETATION")
    print("-" * 60)

    size_factor = np.exp(beta1)
    memory_factor = np.exp(beta2)

    print(f"\\n1. Dataset Size Effect (β₁ = {beta1:.4f}):")
    if beta1 > 0:
        print(f"   Increasing from 500K to 2M multiplies query time by {size_factor:.2f}×")
        print(f"   = {100*(size_factor-1):.1f}% slower on 2M dataset")
    else:
        print(f"   Increasing from 500K to 2M divides query time by {1/size_factor:.2f}×")

    print(f"\\n2. Memory Effect (β₂ = {beta2:.4f}):")
    if beta2 < 0:
        improvement = (1 - memory_factor) * 100
        print(f"   Doubling memory from 2GB to 4GB provides {improvement:.1f}% improvement")
    else:
        degradation = (memory_factor - 1) * 100
        print(f"   ⚠ Doubling memory increases time by {degradation:.1f}% (unexpected)")

    print(f"\\n3. Interaction Effect (β₁₂ = {beta12:.4f}):")
    if abs(beta12) < 0.05:
        print(f"   Weak interaction: Effects are nearly independent")
    elif beta12 < 0:
        print(f"   Negative interaction: Memory helps MORE on large datasets")
        print(f"   Combined effect on 2M: exp({beta1:.3f} + {beta12:.3f}) = {np.exp(beta1 + beta12):.2f}×")
    else:
        print(f"   Positive interaction: Memory helps LESS on large datasets")

    return results


def analyze_experiment_b():
    """Analyze Experiment B: Index Optimization × Selectivity"""
    print("\\n" + "=" * 60)
    print("EXPERIMENT B: INDEX OPTIMIZATION × SELECTIVITY")
    print("=" * 60)

    # Load results
    df = load_experiment_results("B", ["B1", "B2", "B3", "B4"])
    if df is None:
        print("❌ No results found for Experiment B")
        return None

    print(f"\\nLoaded {len(df)} query results across 4 configurations")

    # Aggregate to configuration level
    config_means = df.groupby(['config_id', 'optimization', 'selectivity']).agg({
        'time_ms': ['mean', 'median', 'std', 'count']
    }).reset_index()

    config_means.columns = ['config_id', 'optimization', 'selectivity', 'mean_time', 'median_time', 'std_time', 'n_queries']

    print("\\nConfiguration-level results:")
    print(config_means)

    # Encode factors for regression
    config_means['O'] = (config_means['optimization'] == 'indexed').astype(int)  # 0=noindex, 1=indexed
    config_means['Q'] = (config_means['selectivity'] == 'low').astype(int)       # 0=high, 1=low
    config_means['O_x_Q'] = config_means['O'] * config_means['Q']                 # Interaction
    config_means['log_time'] = np.log(config_means['mean_time'] + 0.001)         # Avoid log(0)

    # Regression model: log(T) = β₀ + β₁·O + β₂·Q + β₁₂·(O×Q)
    X = config_means[['O', 'Q', 'O_x_Q']].values
    y = config_means['log_time'].values

    model = LinearRegression()
    model.fit(X, y)

    # Extract coefficients
    beta0 = model.intercept_
    beta1, beta2, beta12 = model.coef_

    # Calculate R²
    r2 = model.score(X, y)

    # Predictions
    y_pred = model.predict(X)

    # Results dictionary
    results = {
        'data': config_means,
        'model': model,
        'beta0': beta0,
        'beta1': beta1,
        'beta2': beta2,
        'beta12': beta12,
        'r2': r2,
        'X': X,
        'y': y,
        'y_pred': y_pred
    }

    # Print regression results
    print("\\n" + "-" * 60)
    print("REGRESSION RESULTS")
    print("-" * 60)
    print(f"\\nModel: log(time) = β₀ + β₁·Optimization + β₂·Selectivity + β₁₂·(O×Q)")
    print(f"\\nCoefficients:")
    print(f"  β₀ (Intercept)    = {beta0:.4f}")
    print(f"  β₁ (Optimization) = {beta1:.4f}  → exp({beta1:.4f}) = {np.exp(beta1):.3f}× (index effect)")
    print(f"  β₂ (Selectivity)  = {beta2:.4f}  → exp({beta2:.4f}) = {np.exp(beta2):.3f}× (selectivity effect)")
    print(f"  β₁₂ (Interaction) = {beta12:.4f}")
    print(f"\\nModel Fit:")
    print(f"  R² = {r2:.4f} ({100*r2:.1f}% variance explained)")

    # Interpretation
    print("\\n" + "-" * 60)
    print("INTERPRETATION")
    print("-" * 60)

    opt_factor = np.exp(beta1)
    sel_factor = np.exp(beta2)

    print(f"\\n1. Index Optimization Effect (β₁ = {beta1:.4f}):")
    if beta1 < 0:
        improvement = (1 - opt_factor) * 100
        print(f"   Using RDFHexaStore (6 indexes) vs RDFGiantTable (no index):")
        print(f"   Provides {improvement:.1f}% improvement (speedup = {1/opt_factor:.2f}×)")
    else:
        degradation = (opt_factor - 1) * 100
        print(f"   ⚠ Index increases time by {degradation:.1f}% (unexpected)")

    print(f"\\n2. Query Selectivity Effect (β₂ = {beta2:.4f}):")
    if beta2 > 0:
        print(f"   Low-selectivity queries are {sel_factor:.2f}× slower than high-selectivity")
        print(f"   = {100*(sel_factor-1):.1f}% increase in time")
    else:
        print(f"   Low-selectivity queries are faster (unexpected)")

    print(f"\\n3. Interaction Effect (β₁₂ = {beta12:.4f}):")
    if abs(beta12) < 0.05:
        print(f"   Weak interaction: Index benefit is similar across query types")
    elif beta12 < 0:
        print(f"   Negative interaction: Index helps MORE on complex (low-selectivity) queries")
        combined_effect = np.exp(beta1 + beta12)
        improvement = (1 - combined_effect) * 100
        print(f"   Combined effect on complex queries: {improvement:.1f}% improvement")
    else:
        print(f"   Positive interaction: Index helps MORE on simple (high-selectivity) queries")

    return results


def plot_interaction_a(results_a):
    """Create interaction plot for Experiment A"""
    if results_a is None:
        return

    print("\\nGenerating Experiment A interaction plot...")

    data = results_a['data']

    fig, ax = plt.subplots(figsize=(10, 6))

    # Plot lines for each memory level
    sizes = ['500K', '2M']

    # 4GB line
    low_mem_data = data[data['M'] == 0].sort_values('S')
    ax.plot([0, 1], low_mem_data['mean_time'].values, marker='o', linewidth=2.5,
            markersize=10, label='4 GB Heap', color='steelblue')

    # 8GB line
    high_mem_data = data[data['M'] == 1].sort_values('S')
    ax.plot([0, 1], high_mem_data['mean_time'].values, marker='s', linewidth=2.5,
            markersize=10, label='8 GB Heap', color='coral')

    ax.set_xlabel('Dataset Size', fontsize=12, fontweight='bold')
    ax.set_ylabel('Mean Query Time (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Experiment A: Dataset Size × Memory Interaction', fontsize=14, fontweight='bold')
    ax.set_xticks([0, 1])
    ax.set_xticklabels(sizes)
    ax.legend(fontsize=11, loc='best')
    ax.grid(alpha=0.3)

    # Add annotations
    for i, (x, y) in enumerate(zip([0, 1], low_mem_data['mean_time'].values)):
        ax.annotate(f'{y:.1f} ms', (x, y), textcoords="offset points",
                   xytext=(0,10), ha='center', fontsize=9)
    for i, (x, y) in enumerate(zip([0, 1], high_mem_data['mean_time'].values)):
        ax.annotate(f'{y:.1f} ms', (x, y), textcoords="offset points",
                   xytext=(0,-15), ha='center', fontsize=9)

    plt.tight_layout()
    output_file = OUTPUT_DIR / "experiment_A_interaction.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "experiment_A_interaction.png", bbox_inches='tight', dpi=300)
    print(f"✓ Saved to: {output_file}")
    plt.close()


def plot_interaction_b(results_b):
    """Create interaction plot for Experiment B"""
    if results_b is None:
        return

    print("\\nGenerating Experiment B interaction plot...")

    data = results_b['data']

    fig, ax = plt.subplots(figsize=(10, 6))

    # Plot lines for each optimization level
    selectivities = ['High', 'Low']

    # No index line
    noindex_data = data[data['O'] == 0].sort_values('Q')
    ax.plot([0, 1], noindex_data['mean_time'].values, marker='o', linewidth=2.5,
            markersize=10, label='No Index (RDFGiantTable)', color='steelblue')

    # Indexed line
    indexed_data = data[data['O'] == 1].sort_values('Q')
    ax.plot([0, 1], indexed_data['mean_time'].values, marker='s', linewidth=2.5,
            markersize=10, label='Indexed (RDFHexaStore)', color='coral')

    ax.set_xlabel('Query Selectivity', fontsize=12, fontweight='bold')
    ax.set_ylabel('Mean Query Time (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Experiment B: Index Optimization × Selectivity Interaction', fontsize=14, fontweight='bold')
    ax.set_xticks([0, 1])
    ax.set_xticklabels(selectivities)
    ax.legend(fontsize=11, loc='best')
    ax.grid(alpha=0.3)

    # Add annotations
    for i, (x, y) in enumerate(zip([0, 1], noindex_data['mean_time'].values)):
        ax.annotate(f'{y:.1f} ms', (x, y), textcoords="offset points",
                   xytext=(0,10), ha='center', fontsize=9)
    for i, (x, y) in enumerate(zip([0, 1], indexed_data['mean_time'].values)):
        ax.annotate(f'{y:.1f} ms', (x, y), textcoords="offset points",
                   xytext=(0,-15), ha='center', fontsize=9)

    plt.tight_layout()
    output_file = OUTPUT_DIR / "experiment_B_interaction.pdf"
    plt.savefig(output_file, bbox_inches='tight', dpi=300)
    plt.savefig(OUTPUT_DIR / "experiment_B_interaction.png", bbox_inches='tight', dpi=300)
    print(f"✓ Saved to: {output_file}")
    plt.close()


def generate_summary_tables(results_a, results_b):
    """Generate summary tables for report"""
    print("\\n" + "=" * 60)
    print("GENERATING SUMMARY TABLES")
    print("=" * 60)

    output_file = RESULTS_DIR / "factorial_experiments_summary_tables.md"

    with open(output_file, 'w') as f:
        f.write("# Factorial Experiments - Summary Tables\\n\\n")

        # Experiment A
        if results_a:
            f.write("## Experiment A: Dataset Size × Memory\\n\\n")
            f.write("### Configuration Results\\n\\n")
            f.write("| Config | Dataset | Memory | Mean Time (ms) | Median Time (ms) | Log(Time) |\\n")
            f.write("|--------|---------|--------|----------------|------------------|-----------|\\n")
            for _, row in results_a['data'].iterrows():
                f.write(f"| {row['config_id']} | {row['dataset_size']} | {row['heap_mb']} MB | "
                       f"{row['mean_time']:.2f} | {row['median_time']:.2f} | {row['log_time']:.4f} |\\n")

            f.write("\\n### Regression Results\\n\\n")
            f.write("| Coefficient | Estimate | Interpretation |\\n")
            f.write("|-------------|----------|----------------|\\n")
            f.write(f"| β₀ (Intercept) | {results_a['beta0']:.4f} | Baseline: 2M, 2GB |\\n")
            f.write(f"| β₁ (Size) | {results_a['beta1']:.4f} | {np.exp(results_a['beta1']):.2f}× slowdown (2M vs 2M) |\\n")
            f.write(f"| β₂ (Memory) | {results_a['beta2']:.4f} | {(1-np.exp(results_a['beta2']))*100:.1f}% improvement (4GB vs 2GB) |\\n")
            f.write(f"| β₁₂ (Interaction) | {results_a['beta12']:.4f} | Memory effect on large data |\\n")
            f.write(f"| R² | {results_a['r2']:.4f} | {100*results_a['r2']:.1f}% variance explained |\\n")
            f.write("\\n")

        # Experiment B
        if results_b:
            f.write("## Experiment B: Index Optimization × Selectivity\\n\\n")
            f.write("### Configuration Results\\n\\n")
            f.write("| Config | Optimization | Selectivity | Mean Time (ms) | Median Time (ms) | Log(Time) |\\n")
            f.write("|--------|-------------|-------------|----------------|------------------|-----------|\\n")
            for _, row in results_b['data'].iterrows():
                f.write(f"| {row['config_id']} | {row['optimization']} | {row['selectivity']} | "
                       f"{row['mean_time']:.2f} | {row['median_time']:.2f} | {row['log_time']:.4f} |\\n")

            f.write("\\n### Regression Results\\n\\n")
            f.write("| Coefficient | Estimate | Interpretation |\\n")
            f.write("|-------------|----------|----------------|\\n")
            f.write(f"| β₀ (Intercept) | {results_b['beta0']:.4f} | Baseline: No index, High selectivity |\\n")
            f.write(f"| β₁ (Optimization) | {results_b['beta1']:.4f} | {(1-np.exp(results_b['beta1']))*100:.1f}% improvement (indexed vs no index) |\\n")
            f.write(f"| β₂ (Selectivity) | {results_b['beta2']:.4f} | {np.exp(results_b['beta2']):.2f}× slower (low vs high sel.) |\\n")
            f.write(f"| β₁₂ (Interaction) | {results_b['beta12']:.4f} | Index effect on complex queries |\\n")
            f.write(f"| R² | {results_b['r2']:.4f} | {100*results_b['r2']:.1f}% variance explained |\\n")
            f.write("\\n")

    print(f"\\n✓ Summary tables saved to: {output_file}")


def main():
    """Main analysis function"""
    print("=" * 60)
    print("FACTORIAL EXPERIMENTS ANALYSIS")
    print("=" * 60)

    # Analyze Experiment A
    results_a = analyze_experiment_a()

    # Analyze Experiment B
    results_b = analyze_experiment_b()

    # Generate interaction plots
    print("\\n" + "=" * 60)
    print("GENERATING INTERACTION PLOTS")
    print("=" * 60)

    plot_interaction_a(results_a)
    plot_interaction_b(results_b)

    # Generate summary tables
    generate_summary_tables(results_a, results_b)

    print("\\n" + "=" * 60)
    print("✓ ANALYSIS COMPLETED")
    print("=" * 60)
    print(f"\\nAll outputs saved to: {RESULTS_DIR}")
    print(f"Figures saved to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
