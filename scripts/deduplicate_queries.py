#!/usr/bin/env python3
"""
Remove duplicate queries and create deduplicated query files
"""

import hashlib
from pathlib import Path
from collections import defaultdict
import re

QUERIES_DIR = Path("watdiv-mini-projet-partie-2/testsuite/queries/100")
OUTPUT_DIR = Path("watdiv-mini-projet-partie-2/testsuite/queries/100_deduplicated")

def normalize_query(query_text):
    """Normalize a SPARQL query for comparison."""
    query_text = re.sub(r'#.*', '', query_text)
    query_text = ' '.join(query_text.split())
    return query_text.strip()

def read_queries_from_file(file_path):
    """Read all queries from a .queryset file."""
    queries = []
    current_query = []
    
    with open(file_path, 'r') as f:
        for line in f:
            line_stripped = line.rstrip('\n')
            if line_stripped.strip():
                current_query.append(line_stripped)
                if line_stripped.strip().endswith('}'):
                    query_text = '\n'.join(current_query)
                    queries.append(query_text)
                    current_query = []
    
    return queries

def deduplicate_queries():
    """Remove duplicates from query files."""
    
    # Create output directory
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    print("=" * 60)
    print("DEDUPLICATING QUERY FILES")
    print("=" * 60)
    
    total_original = 0
    total_deduplicated = 0
    
    stats = []
    
    # Process each .queryset file
    for query_file in sorted(QUERIES_DIR.glob("*.queryset")):
        template_name = query_file.stem
        queries = read_queries_from_file(query_file)
        
        # Track unique queries
        seen_hashes = set()
        unique_queries = []
        
        for query in queries:
            normalized = normalize_query(query)
            query_hash = hashlib.md5(normalized.encode()).hexdigest()
            
            if query_hash not in seen_hashes:
                seen_hashes.add(query_hash)
                unique_queries.append(query)
        
        # Write deduplicated queries
        output_file = OUTPUT_DIR / query_file.name
        with open(output_file, 'w') as f:
            for query in unique_queries:
                f.write(query)
                f.write('\n\n')
        
        original_count = len(queries)
        deduplicated_count = len(unique_queries)
        duplicates_removed = original_count - deduplicated_count
        
        total_original += original_count
        total_deduplicated += deduplicated_count
        
        print(f"\n{template_name}:")
        print(f"  Original: {original_count} queries")
        print(f"  Unique: {deduplicated_count} queries")
        print(f"  Removed: {duplicates_removed} duplicates ({100.0 * duplicates_removed / original_count:.1f}%)")
        
        stats.append({
            'template': template_name,
            'original': original_count,
            'unique': deduplicated_count,
            'removed': duplicates_removed
        })
    
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Total original queries: {total_original}")
    print(f"Total unique queries: {total_deduplicated}")
    print(f"Total duplicates removed: {total_original - total_deduplicated}")
    print(f"Reduction: {100.0 * (total_original - total_deduplicated) / total_original:.1f}%")
    print(f"\nDeduplicated queries saved to: {OUTPUT_DIR}")
    
    return stats

if __name__ == "__main__":
    deduplicate_queries()
