# System Comparison Report

## Overview

This report compares the performance of three RDF query engines:
- **Our Prototype**: Custom RDFHexaStore implementation
- **Colleague**: Alternative RDFHexaStore implementation
- **InteGraal**: Reference implementation (SimpleInMemoryGraphStore)

## Summary Statistics

| Metric | Our Prototype | Colleague | InteGraal |
|--------|---------------|-----------|------------|
| **Queries benchmarked** | 34 | 34 | 34 |
| **Mean query time** | 5.91 ms | 3.83 ms | 0.19 ms |
| **Median query time** | 0.005 ms | 0.372 ms | 0.031 ms |
| **95th percentile** | 54.57 ms | 32.48 ms | 0.99 ms |
| **Throughput** | 169.2 q/s | 261.1 q/s | 5247.5 q/s |

## Statistical Significance

**Wilcoxon Signed-Rank Test** (paired, non-parametric, vs InteGraal baseline):

**OurPrototype vs InteGraal:**
- p-value: 0.277755
- Result: No significant difference (p >= 0.05)

**Colleague vs InteGraal:**
- p-value: 0.000000
- Result: Highly significant difference (p < 0.001)

## Performance Breakdown (vs InteGraal)

**Our Prototype:**
- Faster: 26 queries (76.5%)
- Slower: 8 queries (23.5%)

**Colleague:**
- Faster: 6 queries (17.6%)
- Slower: 28 queries (82.4%)

### Our Prototype: >10x slower (4 queries)

| Query | Our Time (ms) | InteGraal Time (ms) | Slowdown |
|-------|---------------|---------------------|----------|
| Q_3_nationality_gender_type[4] | 54.57 | 0.95 | 57.4x |
| Q_3_location_gender_type[7] | 115.19 | 2.71 | 42.5x |
| Q_3_location_gender_type[4] | 29.65 | 0.99 | 29.9x |
| Q_2_includes_eligibleRegion[62] | 0.36 | 0.02 | 15.6x |

### Colleague: >10x slower (16 queries)

| Query | Colleague Time (ms) | InteGraal Time (ms) | Slowdown |
|-------|---------------------|---------------------|----------|
| Q_2_likes_nationality[6] | 1.69 | 0.03 | 63.5x |
| Q_2_includes_eligibleRegion[62] | 1.05 | 0.02 | 45.5x |
| Q_3_location_nationality_gender[92] | 1.27 | 0.03 | 45.1x |
| Q_2_includes_eligibleRegion[18] | 0.42 | 0.01 | 35.7x |
| Q_3_nationality_gender_type[4] | 32.48 | 0.95 | 34.1x |
| Q_2_tag_homepage[44] | 0.32 | 0.01 | 31.9x |
| Q_2_includes_eligibleRegion[68] | 0.38 | 0.01 | 31.5x |
| Q_3_location_gender_type[4] | 29.79 | 0.99 | 30.0x |
| Q_2_includes_eligibleRegion[38] | 0.37 | 0.01 | 28.1x |
| Q_3_location_nationality_gender[33] | 0.68 | 0.03 | 25.0x |

## Interpretation

### Key Findings

1. **Median Performance**: Our Prototype shows a better median time (0.0053 ms vs 0.0308 ms), indicating good performance on most queries.

2. **Mean Performance**: However, the mean time is much higher (5.91 ms vs 0.19 ms), which is 31.0x slower. This discrepancy is caused by a few very slow queries.

3. **Performance Bottleneck**: 4 queries show severe performance degradation (>10x slower):
   - `Q_3_nationality_gender_type[4]`: 57x slower (54.57 ms vs 0.95 ms)
   - `Q_3_location_gender_type[7]`: 42x slower (115.19 ms vs 2.71 ms)
   - `Q_3_location_gender_type[4]`: 30x slower (29.65 ms vs 0.99 ms)
   - `Q_2_includes_eligibleRegion[62]`: 16x slower (0.36 ms vs 0.02 ms)

### Hypothesis: Join Ordering and Execution Strategy

The problematic queries (`Q_3_location_gender_type` and `Q_3_nationality_gender_type`) have 3 triple patterns and produce non-empty results. These queries likely suffer from:

- **Suboptimal join ordering**: The current selectivity-based ordering may not account for intermediate result sizes after joins.
- **Nested loop join overhead**: The current implementation uses nested loops which can be inefficient when intermediate results are large.
- **Index structure**: InteGraal may use more efficient data structures for multi-pattern queries.

### Recommendations

1. **Profile the slow queries**: Use a profiler to identify the bottleneck in the execution pipeline.
2. **Improve join ordering**: Consider cardinality estimation and intermediate result sizes.
3. **Alternative join algorithms**: Consider hash joins or sort-merge joins for better performance.
4. **Index optimization**: Review index structure for multi-attribute lookups.

