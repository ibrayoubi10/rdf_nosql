#!/usr/bin/env python3
"""
This script properly checks for duplicate queries by reading the actual query files
and comparing their normalized SPARQL text.
"""

import hashlib
from pathlib import Path
from collections import defaultdict
import re

QUERIES_DIR = Path("watdiv-mini-projet-partie-2/testsuite/queries/100")

def normalize_query(query_text):
    """
    Normalize a SPARQL query by removing whitespace variations
    to enable proper duplicate detection.
    """
    # Remove comments
    query_text = re.sub(r'#.*', '', query_text)
    # Normalize whitespace
    query_text = ' '.join(query_text.split())
    # Convert to lowercase for comparison
    query_text = query_text.lower()
    return query_text.strip()

def read_queries_from_file(file_path):
    """Read all queries from a .queryset file."""
    queries = []
    current_query = []
    
    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip()
            if line:
                current_query.append(line)
                # Check if this line ends the query
                if line.endswith('}'):
                    query_text = '\n'.join(current_query)
                    queries.append(query_text)
                    current_query = []
    
    return queries

def detect_duplicates():
    """Detect duplicate queries across all templates."""
    
    print("=" * 60)
    print("DUPLICATE QUERY DETECTION")
    print("=" * 60)
    
    # Dictionary to store query hash -> list of (template, query_index)
    query_hashes = defaultdict(list)
    
    # Dictionary to store normalized queries
    normalized_queries = {}
    
    total_queries = 0
    queries_by_template = {}
    
    # Process each .queryset file
    for query_file in sorted(QUERIES_DIR.glob("*.queryset")):
        template_name = query_file.stem
        queries = read_queries_from_file(query_file)
        queries_by_template[template_name] = queries
        
        for idx, query in enumerate(queries):
            total_queries += 1
            normalized = normalize_query(query)
            query_hash = hashlib.md5(normalized.encode()).hexdigest()
            
            query_hashes[query_hash].append((template_name, idx, normalized))
            normalized_queries[query_hash] = normalized
    
    # Find duplicates
    duplicates = {h: locations for h, locations in query_hashes.items() if len(locations) > 1}
    
    print(f"\nTotal queries analyzed: {total_queries}")
    print(f"Unique query structures: {len(query_hashes)}")
    print(f"Duplicate query structures: {len(duplicates)}")
    
    if duplicates:
        # Count total duplicate instances
        duplicate_instances = sum(len(locations) - 1 for locations in duplicates.values())
        print(f"Total duplicate instances: {duplicate_instances}")
        print(f"Duplication rate: {100.0 * duplicate_instances / total_queries:.1f}%\n")
        
        # Show examples of duplicates
        print("\nDuplicate Examples (first 5):")
        print("-" * 60)
        for i, (query_hash, locations) in enumerate(list(duplicates.items())[:5]):
            print(f"\nDuplicate Group {i+1}: ({len(locations)} instances)")
            for template, idx, normalized in locations:
                print(f"  - {template}: query #{idx+1}")
            # Show the normalized query
            print(f"  Query: {normalized[:100]}...")
    else:
        print("\n✓ No duplicate queries found!")
    
    # Duplicates within same template
    print("\n" + "=" * 60)
    print("DUPLICATES WITHIN SAME TEMPLATE")
    print("=" * 60)
    
    within_template_dupes = 0
    for query_hash, locations in duplicates.items():
        # Group by template
        by_template = defaultdict(list)
        for template, idx, normalized in locations:
            by_template[template].append(idx)
        
        # Check if any template has multiple instances
        for template, indices in by_template.items():
            if len(indices) > 1:
                within_template_dupes += len(indices) - 1
                print(f"\n{template}: {len(indices)} duplicate queries")
                print(f"  Query indices: {[i+1 for i in indices]}")
    
    if within_template_dupes == 0:
        print("\n✓ No duplicates within the same template")
    
    # Cross-template duplicates
    print("\n" + "=" * 60)
    print("CROSS-TEMPLATE DUPLICATES")
    print("=" * 60)
    
    cross_template_dupes = []
    for query_hash, locations in duplicates.items():
        templates = set(template for template, _, _ in locations)
        if len(templates) > 1:
            cross_template_dupes.append((query_hash, locations))
    
    if cross_template_dupes:
        print(f"\nFound {len(cross_template_dupes)} query structures shared across templates:")
        for i, (query_hash, locations) in enumerate(cross_template_dupes[:10]):
            templates = set(template for template, _, _ in locations)
            print(f"\n  {i+1}. Shared by {len(templates)} templates: {', '.join(sorted(templates))}")
            print(f"     Total instances: {len(locations)}")
    else:
        print("\n✓ No queries shared across different templates")
    
    # Save detailed duplicate report
    output_file = Path("results/phase2/figures/duplicate_analysis.txt")
    with open(output_file, 'w') as f:
        f.write("=" * 60 + "\n")
        f.write("DUPLICATE QUERY ANALYSIS REPORT\n")
        f.write("=" * 60 + "\n\n")
        
        f.write(f"Total queries analyzed: {total_queries}\n")
        f.write(f"Unique query structures: {len(query_hashes)}\n")
        f.write(f"Duplicate query structures: {len(duplicates)}\n")
        
        if duplicates:
            duplicate_instances = sum(len(locations) - 1 for locations in duplicates.values())
            f.write(f"Total duplicate instances: {duplicate_instances}\n")
            f.write(f"Duplication rate: {100.0 * duplicate_instances / total_queries:.1f}%\n\n")
            
            f.write("\nDETAILED DUPLICATE LIST:\n")
            f.write("=" * 60 + "\n")
            
            for i, (query_hash, locations) in enumerate(duplicates.items()):
                f.write(f"\nDuplicate Group {i+1}: ({len(locations)} instances)\n")
                for template, idx, normalized in locations:
                    f.write(f"  - {template}: query #{idx+1}\n")
                f.write(f"\nNormalized Query:\n{normalized}\n")
                f.write("-" * 60 + "\n")
    
    print(f"\n✓ Detailed report saved to: {output_file}")
    
    # Return statistics
    return {
        'total_queries': total_queries,
        'unique_queries': len(query_hashes),
        'duplicate_structures': len(duplicates),
        'duplicate_instances': sum(len(locations) - 1 for locations in duplicates.values()) if duplicates else 0,
        'within_template': within_template_dupes,
        'cross_template': len(cross_template_dupes)
    }

if __name__ == "__main__":
    stats = detect_duplicates()
    
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Total queries: {stats['total_queries']}")
    print(f"Unique queries: {stats['unique_queries']}")
    print(f"Effective duplication: {stats['duplicate_instances']} ({100.0 * stats['duplicate_instances'] / stats['total_queries']:.1f}%)")
    print(f"  - Within same template: {stats['within_template']}")
    print(f"  - Cross-template: {stats['cross_template']}")
