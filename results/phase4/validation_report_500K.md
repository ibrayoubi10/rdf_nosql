# Measurement Validation Report - 500K Dataset

## Measurement Stability Validation

### Overall Performance Statistics

- **Queries benchmarked**: 913
- **Mean query time**: 1.1806 ms
- **Median query time**: 0.0006 ms
- **Std dev query time**: 6.5039 ms
- **Min query time**: 0.0000 ms
- **Max query time**: 123.1670 ms
- **Throughput**: 847.00 queries/sec

### Variance Analysis

| Stability Category | Count | Percentage |
|-------------------|-------|------------|
| Stable (CV < 0.3) | 91 | 10.0% |
| Moderate (0.3 ≤ CV ≤ 0.5) | 106 | 11.6% |
| High variance (CV > 0.5) | 716 | 78.4% |

- **Mean CV across all queries**: 1.540
- ⚠ **Result**: High variance detected (mean CV ≥ 0.5) - investigate causes

### Query Performance Categories

| Category | Time Range | Count | Percentage |
|----------|-----------|-------|------------|
| Fast | < 1 ms | 813 | 89.0% |
| Medium | 1-10 ms | 74 | 8.1% |
| Slow | > 10 ms | 26 | 2.8% |

### Performance by Template

| Template | Queries | Mean (ms) | Median (ms) | Std Dev (ms) | Mean CV |
|----------|---------|-----------|-------------|--------------|---------|
| Q_1_eligibleregion | 58 | 0.0245 | 0.0046 | 0.0707 | 1.290 |
| Q_1_includes | 65 | 0.0003 | 0.0002 | 0.0004 | 2.156 |
| Q_1_likes | 65 | 0.0002 | 0.0001 | 0.0005 | 1.674 |
| Q_1_nationality | 53 | 0.0053 | 0.0010 | 0.0147 | 1.824 |
| Q_1_subscribes | 64 | 0.0010 | 0.0002 | 0.0026 | 2.425 |
| Q_2_includes_eligibleRegion | 100 | 0.0051 | 0.0002 | 0.0359 | 1.826 |
| Q_2_likes_nationality | 53 | 0.5354 | 0.0899 | 1.5567 | 1.017 |
| Q_2_subscribes_likes | 99 | 0.0004 | 0.0003 | 0.0004 | 2.506 |
| Q_2_tag_homepage | 66 | 0.0013 | 0.0002 | 0.0070 | 1.754 |
| Q_3_location_gender_type | 53 | 10.4016 | 2.7652 | 20.5139 | 0.498 |
| Q_3_location_nationality_gender | 95 | 0.9101 | 0.0828 | 2.4295 | 0.784 |
| Q_3_nationality_gender_type | 49 | 6.7238 | 1.4008 | 13.3309 | 0.750 |
| Q_4_location_nationality_gender_type | 93 | 0.8592 | 0.0488 | 2.6465 | 0.993 |
