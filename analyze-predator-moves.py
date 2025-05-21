import re
import sys
import collections

def analyze_move_predator_commands(log_file_path: str):
    """
    Scans a log file for specific "_Command: MovePredator[" entries,
    extracts location ("RxC") and predator identifiers ("ID") from these
    lines, and outputs a count for each unique "predatorId locationId"
    combination found.

    The function operates as follows:

    1.  **Line Identification**:
        It reads the specified log file line by line. It specifically looks
        for lines that begin with the exact string `_Command: MovePredator[`.

    2.  **Pattern Matching and Data Extraction**:
        For lines that match the initial pattern, the function then expects
        and attempts to parse a specific structure immediately following the
        opening bracket `[`:
        - `id=RxC`: Where `RxC` is a location identifier (e.g., "14x93").
            This `RxC` value is extracted.
        - This is followed by one or more whitespace characters.
        - `predatorId=ID`: Where `ID` is the predator's unique identifier
            (e.g., "P001"). This `ID` value is extracted. The ID is assumed
            to not contain spaces and to terminate before a space or a
            potential closing square bracket `]`.
        A regular expression is used to enforce this structure and capture
        the required values.

    3.  **Key Formation**:
        If a line successfully matches the complete pattern and both the
        location ID (`RxC`) and predator ID (`ID`) are extracted, a
        composite key is formed. This key is a string concatenation of:
        `extracted_predator_ID` + (a single space) + `extracted_RxC_location_ID`.
        For example, if `predatorId=P001` and `id=14x93` are found, the
        key becomes "P001 14x93".

    4.  **Counting Occurrences**:
        The script maintains a count for each unique key generated. Every
        time a key is encountered, its corresponding count is incremented.

    5.  **Output**:
        After processing the entire log file:
        - If matching entries were found, the script prints each unique key
            along with its total count (e.g., "P001 14x93: 5"). The list
            of keys is typically sorted alphabetically for readability.
        - If no lines matched the specified criteria, a message indicating
            this is printed.

    Args:
        log_file_path (str): The path to the log file that needs to be scanned.

    Prints:
        - A list of unique "PredatorID LocationID" keys, each followed by its
          occurrence count, to standard output.
        - An informational message if no relevant log entries are found.
        - Error messages to standard error in case of issues like file not
          found or read permissions.
    """

    # Regex to match the line structure:
    # _Command: MovePredator id=ROWxCOL predatorId=PRED_ID ...
    # This regex expects the line to start with "_Command: MovePredator",
    # followed by whitespace, then "id=CAPTURE_THIS", then whitespace,
    # then "predatorId=CAPTURE_THIS_ID".
    # - group(1) will capture the RxC value (e.g., "14x93").
    # - group(2) will capture the predator ID value (e.g., "P777").
    line_regex = re.compile(
        r"^_Command: MovePredator\[" # Literal start of the command
        r"id=(\d+x\d+)"              # "id=" followed by the RxC format (e.g., 14x93)
        r",\s+"                      # Ensures at least one space separating id and predatorId
        r"predatorId=([^\s]+)"       # "predatorId=" followed by an ID (assumed to not contain spaces)
                                     # Other data might follow on the line, which this regex allows.
    )

    key_counts = collections.Counter()
    current_line_number = 0  # For better error reporting

    try:
        with open(log_file_path, 'r') as f:
            for line_num, line_content in enumerate(f, 1):
                current_line_number = line_num
                # rstrip to remove newline, not strip as regex anchors to start (^)
                line = line_content.rstrip('\n')

                match = line_regex.match(line) # Use match() since regex is anchored with ^
                if match:
                    location_id = match.group(1)  # The "RxC" value like "14x93"
                    pred_id_val = match.group(2)  # The predator ID like "P001"

                    # Concatenate predatorId + space + id to make the key
                    key = f"{pred_id_val} {location_id}"
                    key_counts[key] += 1

    except FileNotFoundError:
        print(f"Error: File not found at {log_file_path}", file=sys.stderr)
        return
    except Exception as e:
        print(f"An error occurred during file processing (around line {current_line_number}): {e}", file=sys.stderr)
        return

    # After scanning the entire log file, list out the key/values
    if not key_counts:
        print("No matching '_Command: MovePredator' lines with the specified format were found.")
    else:
        print("Count of 'PredatorID LocationID' occurrences:")
        # Sort by key for consistent and readable output.
        # The key is "predatorId RxC".
        for key, count in sorted(key_counts.items()):
            print(f"{key}: {count}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        # Suggest a script name in usage, e.g., analyze_predator_moves.py
        print("Usage: python analyze_predator_moves.py <log_file_path>", file=sys.stderr)
        sys.exit(1)

    log_file_to_scan = sys.argv[1]
    analyze_move_predator_commands(log_file_to_scan)

