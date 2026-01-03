# Query Order Effect Analysis Report

## Experimental Setup

**Objective**: Test whether query execution order affects measured performance

**Configuration**:
- Dataset: 100K triples
- Queries: 25 queries
- Orderings: Original + Shuffled (seed=42)
- Repetitions: 10 per ordering
- Total executions: 500 query executions

## Statistical Test Results

### Workload Total Times

| Ordering | Mean (ms) | Std Dev (ms) |
|----------|-----------|-------------|
| Original | 222.71 | 26.77 |
| Shuffled | 225.61 | 21.72 |

### Paired t-test

**Null Hypothesis**: Query execution order has no effect on total workload time

- **t-statistic**: -0.2475
- **p-value**: 0.810106
- **Cohen's d**: -0.1251

**Result**: FAIL TO REJECT null hypothesis (p >= 0.05)

Query execution order **DOES NOT** significantly affect measured performance.

## Interpretation

### No Order Effect Detected

The lack of statistical significance suggests:

1. **Robust measurements**: Results are independent of execution order
2. **Validation of methodology**:
   - Warmup phase is adequate
   - System state is stable throughout workload
   - No significant cache/JIT bias

3. **Implications**:
   - Phase 4-6 results are methodologically sound
   - Query order does not need to be randomized
   - Measurements are reproducible and reliable

### Effect Size

Cohen's d = -0.1251 indicates a **negligible effect**.
Even if statistically significant, the practical difference is minimal.

