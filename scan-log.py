import re
import sys
import collections
from datetime import datetime

# Using a fixed year for parsing MM-dd timestamps. This is generally fine for
# calculating deltas if logs don't span year boundaries in a way that makes
# MM-dd ambiguous for chronological order. If logs have explicit years,
# parsing should be adjusted.
LOG_YEAR = 2000  # A common choice for year-less log timestamps

def parse_timestamp_to_datetime(ts_str: str) -> datetime | None:
    """Converts an MM-dd HH:mm:ss.SSS string to a datetime object."""
    try:
        return datetime.strptime(f"{LOG_YEAR}-{ts_str}", "%Y-%m-%d %H:%M:%S.%f")
    except ValueError:
        print(f"Warning: Could not parse timestamp string: {ts_str}", file=sys.stderr)
        return None

def print_predator_counts(counts: collections.Counter):
    """Prints the summary of predator ID counts."""
    print("\nPredator ID Counts:")
    if counts:
        for predator_id_val, count in sorted(counts.items()):
            print(f"{predator_id_val}: {count}")
    else:
        print("No predator IDs found to count.")

def scan_log_file(log_file_path: str):
    """
    Scans a log file to extract timestamped "predatorId=VALUE" events,
    calculates the elapsed time between consecutive appearances of each unique
    predatorId, and prints a sorted list of these events followed by a
    summary of counts for each predatorId.

    The function performs the following main steps:

    1.  **Data Collection**:
        * Reads the specified log file line by line.
        * Identifies lines starting with a timestamp in "MM-dd HH:mm:ss.SSS" format.
        * When a timestamp is found, it becomes the "active timestamp".
        * It then looks for a "predatorId=VALUE" pattern on the same line as
            the timestamp or on subsequent lines.
        * Once a "predatorId" is found and associated with the active
            timestamp, that timestamp is considered "consumed" and will not be
            used for further "predatorId" matches until a new timestamp line appears.
        * For each valid pairing, it stores the original timestamp string,
            a datetime object representation of the timestamp, the full
            "predatorId=VALUE" pattern string, and the extracted predator ID value.
        * It also maintains a count of how many times each unique predator ID
            value appears in these valid pairings.

    2.  **Elapsed Time Calculation**:
        * After processing the entire file, the collected events are first
            sorted by predator ID value, then by their timestamp (datetime object).
        * For each unique predator ID, it calculates the time difference
            between its consecutive appearances.
        * The elapsed time is formatted as seconds with three decimal places (s.SSS).
        * The first recorded event for any given predator ID will show an
            elapsed time of "0.000s".

    3.  **Output Generation**:
        * The processed events, now consisting of the original timestamp string,
            the full "predatorId=VALUE" pattern, and the calculated elapsed time
            string, are prepared for final output.
        * This list is then sorted primarily by the "predatorId=VALUE" pattern
            string and secondarily by the original timestamp string.
        * The sorted list of events (timestamp, pattern, elapsed time) is printed
            to standard output.
        * Finally, a summary listing each unique predator ID value and its total
            count is printed, sorted alphabetically by the predator ID value.

    Args:
        log_file_path (str): The path to the log file to be scanned.

    Prints:
        - A sorted list of processed log events, each including:
            - The original timestamp string.
            - The matched "predatorId=VALUE" pattern string.
            - The calculated elapsed time (e.g., "1.234s") since the previous
              event for that specific predatorId.
        - A summary of total counts for each unique predator ID value.
        - Warnings or error messages to standard error if issues occur
          (e.g., file not found, timestamp parsing problems).
    """
    timestamp_regex = re.compile(r"^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}")
    # Regex for predatorId pattern, Group 1 captures the ID value
    predator_id_regex = re.compile(r"predatorId=([^ ,]+)")

    active_ts_str = None  # Holds the string of the last seen, unconsumed timestamp
    
    # Phase 1: Data Collection
    # Store tuples: (datetime_obj, full_pattern_text, predator_value, original_timestamp_str)
    collected_events = []
    predator_counts = collections.Counter()
    line_number_for_error = 0


    try:
        with open(log_file_path, 'r') as f:
            for line_num, line_content in enumerate(f, 1):
                line_number_for_error = line_num # For error reporting
                line = line_content.rstrip('\n')
                
                is_timestamp_line_match = timestamp_regex.match(line)
                predator_match_this_line = predator_id_regex.search(line)

                if is_timestamp_line_match:
                    active_ts_str = is_timestamp_line_match.group(0) # New timestamp becomes active
                    # If predatorId is also on this timestamp line
                    if predator_match_this_line:
                        dt_obj = parse_timestamp_to_datetime(active_ts_str)
                        if dt_obj:
                            full_pattern = predator_match_this_line.group(0)
                            p_value = predator_match_this_line.group(1)
                            
                            collected_events.append((dt_obj, full_pattern, p_value, active_ts_str))
                            predator_counts[p_value] += 1
                        active_ts_str = None # Timestamp consumed by this predatorId
                
                # Else (not a timestamp line OR was a timestamp line but predatorId wasn't on it and active_ts_str is still set)
                # If a predatorId is found on the current line AND there's an unconsumed active_ts_str
                elif predator_match_this_line and active_ts_str:
                    dt_obj = parse_timestamp_to_datetime(active_ts_str)
                    if dt_obj:
                        full_pattern = predator_match_this_line.group(0)
                        p_value = predator_match_this_line.group(1)
                        
                        collected_events.append((dt_obj, full_pattern, p_value, active_ts_str))
                        predator_counts[p_value] += 1
                    active_ts_str = None # Timestamp consumed
                        
    except FileNotFoundError:
        print(f"Error: File not found at {log_file_path}", file=sys.stderr)
        return
    except Exception as e:
        print(f"An error occurred during file reading (around line {line_number_for_error}): {e}", file=sys.stderr)
        return

    if not collected_events:
        print("No relevant log entries found matching the specified patterns.")
        print_predator_counts(predator_counts) # Still print counts, if any
        return

    # Phase 2: Calculate Elapsed Times
    # Sort by predator_value, then by datetime_obj to process in order for deltas
    collected_events.sort(key=lambda x: (x[2], x[0])) # x[2]=predator_value, x[0]=datetime_obj

    # This list will store: (original_ts_str, full_pattern_text, elapsed_time_str)
    final_output_list = []
    last_dt_obj_for_id = {} # Stores the last datetime_obj for each predator_value

    for dt_obj, full_pattern, p_value, original_ts_str in collected_events:
        elapsed_s_sss_str = "0.000" # Default for the first occurrence of an ID
        if p_value in last_dt_obj_for_id:
            delta = dt_obj - last_dt_obj_for_id[p_value]
            # Handle rare cases of non-sequential timestamps for an ID if data is unusual
            if delta.total_seconds() < 0:
                print(f"Warning: Negative or zero time delta calculated for {p_value} at {original_ts_str}. "
                      f"Previous: {last_dt_obj_for_id[p_value]}, Current: {dt_obj}. Using 0.000s.", file=sys.stderr)
                elapsed_s_sss_str = "0.000" # Reset to 0 for this case
            else:
                elapsed_s_sss_str = f"{delta.total_seconds():.3f}"
        
        final_output_list.append((original_ts_str, full_pattern, elapsed_s_sss_str))
        last_dt_obj_for_id[p_value] = dt_obj # Update last seen timestamp for this ID

    # Phase 3: Final Sorting for Output
    # Sort by pattern (item[1]), then by original timestamp string (item[0])
    final_output_list.sort(key=lambda x: (x[1], x[0]))

    # Phase 4: Printing the Main Output
    for ts_str, pattern, elapsed_str in final_output_list:
        print(f"{ts_str} {pattern} {elapsed_str}s") # Added 's' suffix

    # Print predator ID counts summary
    print_predator_counts(predator_counts)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python script_name.py <log_file_path>", file=sys.stderr)
        sys.exit(1)

    log_file = sys.argv[1]
    scan_log_file(log_file)

