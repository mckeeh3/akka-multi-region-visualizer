#!/usr/bin/env python3

import re
import sys
import collections
import numpy as np
import matplotlib.pyplot as plt

def scan_latency_elapsed_times(log_file_path: str):
    """
    Scans a log file for 'Latency: N ms' and 'Elapsed: N ms' patterns,
    counts occurrences for each time value, and creates graphs for raw counts,
    p50, p95, and p99 percentiles.
    
    Args:
        log_file_path (str): The path to the log file to be scanned.
    """
    # Regular expressions to match the patterns
    latency_regex = re.compile(r"Latency:\s+(\d+)\s+ms")
    elapsed_regex = re.compile(r"Elapsed:\s+(\d+)\s+ms")
    
    # Counters for latency and elapsed times
    latency_counts = collections.Counter()
    elapsed_counts = collections.Counter()
    
    # Lists to store all values for percentile calculations
    latency_values = []
    elapsed_values = []
    
    try:
        with open(log_file_path, 'r') as f:
            for line_num, line in enumerate(f, 1):
                line = line.strip()
                
                # Check for latency pattern
                latency_match = latency_regex.search(line)
                if latency_match:
                    latency_ms = int(latency_match.group(1))
                    latency_counts[latency_ms] += 1
                    latency_values.append(latency_ms)
                
                # Check for elapsed pattern
                elapsed_match = elapsed_regex.search(line)
                if elapsed_match:
                    elapsed_ms = int(elapsed_match.group(1))
                    elapsed_counts[elapsed_ms] += 1
                    elapsed_values.append(elapsed_ms)
                    
    except FileNotFoundError:
        print(f"Error: File not found at {log_file_path}", file=sys.stderr)
        return
    except Exception as e:
        print(f"An error occurred during file processing (around line {line_num}): {e}", file=sys.stderr)
        return
    
    # Print summary
    print_summary(latency_counts, elapsed_counts, latency_values, elapsed_values)
    
    # Create and save graphs
    create_graphs(latency_counts, elapsed_counts, latency_values, elapsed_values)

def print_summary(latency_counts, elapsed_counts, latency_values, elapsed_values):
    """
    Prints a summary of the latency and elapsed time counts and percentiles.
    
    Args:
        latency_counts (Counter): Counter of latency times
        elapsed_counts (Counter): Counter of elapsed times
        latency_values (list): List of all latency values
        elapsed_values (list): List of all elapsed values
    """
    print("\nLatency Times Summary:")
    if latency_counts:
        print(f"Total occurrences: {sum(latency_counts.values())}")
        if latency_values:
            print(f"Min: {min(latency_values)} ms")
            print(f"Max: {max(latency_values)} ms")
            print(f"Average: {np.mean(latency_values):.2f} ms")
            print(f"P50: {np.percentile(latency_values, 50):.2f} ms")
            print(f"P95: {np.percentile(latency_values, 95):.2f} ms")
            print(f"P99: {np.percentile(latency_values, 99):.2f} ms")
    else:
        print("No latency times found.")
    
    print("\nElapsed Times Summary:")
    if elapsed_counts:
        print(f"Total occurrences: {sum(elapsed_counts.values())}")
        if elapsed_values:
            print(f"Min: {min(elapsed_values)} ms")
            print(f"Max: {max(elapsed_values)} ms")
            print(f"Average: {np.mean(elapsed_values):.2f} ms")
            print(f"P50: {np.percentile(elapsed_values, 50):.2f} ms")
            print(f"P95: {np.percentile(elapsed_values, 95):.2f} ms")
            print(f"P99: {np.percentile(elapsed_values, 99):.2f} ms")
    else:
        print("No elapsed times found.")

def create_graphs(latency_counts, elapsed_counts, latency_values, elapsed_values):
    """
    Creates and saves graphs for raw counts, p50, p95, and p99 percentiles.
    
    Args:
        latency_counts (Counter): Counter of latency times
        elapsed_counts (Counter): Counter of elapsed times
        latency_values (list): List of all latency values
        elapsed_values (list): List of all elapsed values
    """
    # Create a figure with 2x4 subplots (2 rows, 4 columns)
    fig, axes = plt.subplots(2, 4, figsize=(20, 10))
    fig.suptitle('Latency and Elapsed Time Analysis', fontsize=16)
    
    # Plot raw counts for latency
    if latency_counts:
        sorted_latency = sorted(latency_counts.items())
        x_latency, y_latency = zip(*sorted_latency) if sorted_latency else ([], [])
        
        axes[0, 0].bar(x_latency, y_latency, color='blue', alpha=0.7)
        axes[0, 0].set_title('Latency Raw Counts')
        axes[0, 0].set_xlabel('Latency (ms)')
        axes[0, 0].set_ylabel('Count')
        axes[0, 0].grid(True, linestyle='--', alpha=0.7)
    else:
        axes[0, 0].text(0.5, 0.5, 'No data available', ha='center', va='center')
        axes[0, 0].set_title('Latency Raw Counts')
    
    # Plot raw counts for elapsed
    if elapsed_counts:
        sorted_elapsed = sorted(elapsed_counts.items())
        x_elapsed, y_elapsed = zip(*sorted_elapsed) if sorted_elapsed else ([], [])
        
        axes[1, 0].bar(x_elapsed, y_elapsed, color='green', alpha=0.7)
        axes[1, 0].set_title('Elapsed Raw Counts')
        axes[1, 0].set_xlabel('Elapsed (ms)')
        axes[1, 0].set_ylabel('Count')
        axes[1, 0].grid(True, linestyle='--', alpha=0.7)
    else:
        axes[1, 0].text(0.5, 0.5, 'No data available', ha='center', va='center')
        axes[1, 0].set_title('Elapsed Raw Counts')
    
    # Plot percentiles for latency
    if latency_values:
        # Calculate percentiles
        latency_p50 = calculate_percentile_bins(latency_values, 50)
        latency_p95 = calculate_percentile_bins(latency_values, 95)
        latency_p99 = calculate_percentile_bins(latency_values, 99)
        
        # P50 plot
        plot_percentile(axes[0, 1], latency_p50, 'Latency P50', 'blue')
        
        # P95 plot
        plot_percentile(axes[0, 2], latency_p95, 'Latency P95', 'blue')
        
        # P99 plot
        plot_percentile(axes[0, 3], latency_p99, 'Latency P99', 'blue')
    else:
        for i in range(1, 4):
            axes[0, i].text(0.5, 0.5, 'No data available', ha='center', va='center')
            axes[0, i].set_title(f'Latency P{[50, 95, 99][i-1]}')
    
    # Plot percentiles for elapsed
    if elapsed_values:
        # Calculate percentiles
        elapsed_p50 = calculate_percentile_bins(elapsed_values, 50)
        elapsed_p95 = calculate_percentile_bins(elapsed_values, 95)
        elapsed_p99 = calculate_percentile_bins(elapsed_values, 99)
        
        # P50 plot
        plot_percentile(axes[1, 1], elapsed_p50, 'Elapsed P50', 'green')
        
        # P95 plot
        plot_percentile(axes[1, 2], elapsed_p95, 'Elapsed P95', 'green')
        
        # P99 plot
        plot_percentile(axes[1, 3], elapsed_p99, 'Elapsed P99', 'green')
    else:
        for i in range(1, 4):
            axes[1, i].text(0.5, 0.5, 'No data available', ha='center', va='center')
            axes[1, i].set_title(f'Elapsed P{[50, 95, 99][i-1]}')
    
    plt.tight_layout(rect=[0, 0, 1, 0.96])
    plt.savefig('latency_elapsed_analysis.png')
    print("\nGraphs saved to 'latency_elapsed_analysis.png'")
    
    # Optionally show the plot (comment out if running in a non-interactive environment)
    # plt.show()

def calculate_percentile_bins(values, percentile):
    """
    Calculates percentile bins for the given values.
    
    Args:
        values (list): List of values
        percentile (int): Percentile to calculate (50, 95, or 99)
        
    Returns:
        tuple: (bin_edges, bin_values) for the histogram
    """
    # Calculate the percentile threshold
    threshold = np.percentile(values, percentile)
    
    # Filter values below or equal to the threshold
    filtered_values = [v for v in values if v <= threshold]
    
    # Create histogram data
    hist, bin_edges = np.histogram(filtered_values, bins=min(20, len(set(filtered_values))))
    
    # Get the midpoints of the bins for plotting
    bin_midpoints = (bin_edges[:-1] + bin_edges[1:]) / 2
    
    return bin_midpoints, hist

def plot_percentile(ax, percentile_data, title, color):
    """
    Plots percentile data on the given axis.
    
    Args:
        ax (matplotlib.axes.Axes): The axis to plot on
        percentile_data (tuple): (bin_midpoints, hist) from calculate_percentile_bins
        title (str): Title for the plot
        color (str): Color for the bars
    """
    if percentile_data and percentile_data[0].size > 0:
        bin_midpoints, hist = percentile_data
        ax.bar(bin_midpoints, hist, width=(bin_midpoints[1]-bin_midpoints[0]) if len(bin_midpoints) > 1 else 1, 
               color=color, alpha=0.7)
        ax.set_title(title)
        ax.set_xlabel('Time (ms)')
        ax.set_ylabel('Count')
        ax.grid(True, linestyle='--', alpha=0.7)
    else:
        ax.text(0.5, 0.5, 'No data available', ha='center', va='center')
        ax.set_title(title)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python latency-elapsed-times.py <log_file_path>", file=sys.stderr)
        sys.exit(1)

    log_file = sys.argv[1]
    scan_latency_elapsed_times(log_file)
