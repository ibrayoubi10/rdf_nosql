# Phase 5: Factorial Experiments - Summary Tables

## Experiment A: Dataset Size × Memory

### Configuration Results

| Config | Dataset | Memory | Mean Time (ms) | Median Time (ms) | Log(Time) |
|--------|---------|--------|----------------|-----------------|-----------|
| A1     | 500K    | 4096 MB | 2.87           | 0.00            | 1.0539    |
| A2     | 500K    | 8192 MB | 2.70           | 0.00            | 0.9939    |
| A3     | 2M      | 4096 MB | 74.54          | 0.01            | 4.3113    |
| A4     | 2M      | 8192 MB | 54.06          | 0.01            | 3.9902    |

### Regression Results

| Coefficient         | Estimate | Interpretation                |
|--------------------|----------|-------------------------------|
| β₀ (Intercept)      | 1.0539   | Baseline: 500K, 4GB           |
| β₁ (Size)           | 3.2574   | 25.98× slowdown (500K vs 2M)  |
| β₂ (Memory)         | -0.0600  | 5.8% improvement (4GB vs 8GB) |
| β₁₂ (Interaction)   | -0.2611  | Memory effect on large data   |
| R²                  | 1.0000   | 100% variance explained       |

---

## Experiment B: Index Optimization × Selectivity

### Configuration Results

| Config | Optimization | Selectivity | Mean Time (ms) | Median Time (ms) | Log(Time) |
|--------|-------------|------------|----------------|-----------------|-----------|
| B1     | noindex     | high       | 3.91           | 2.47            | 1.3640    |
| B2     | indexed     | high       | 0.22           | 0.00            | -1.5180   |
| B3     | noindex     | low        | 29.69          | 0.60            | 3.3910    |
| B4     | indexed     | low        | 11.79          | 0.00            | 2.4670    |

### Regression Results

| Coefficient         | Estimate | Interpretation                                 |
|--------------------|----------|------------------------------------------------|
| β₀ (Intercept)      | 1.3640   | Baseline: No index, High selectivity          |
| β₁ (Optimization)   | -2.8819  | 94.4% improvement (indexed vs no index)      |
| β₂ (Selectivity)    | 2.0270   | 7.59× slower (low vs high selectivity)       |
| β₁₂ (Interaction)   | 1.9579   | Index effect on complex queries               |
| R²                  | 1.0000   | 100% variance explained                        |
