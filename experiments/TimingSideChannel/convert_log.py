import csv
import re
import glob

def batch_convert_logs_to_csv(input_pattern, output_file):
    pattern = re.compile(r'(\d+),\s*(\d+),\s*(\d+),\s*(\d+)$')
    
    files = glob.glob(input_pattern)
    if not files:
        print("File not found.")
        return

    with open(output_file, mode='w', newline='', encoding='utf-8') as f_out:
        writer = csv.writer(f_out)

        writer.writerow(['click', 'time', 'labela', 'labelb'])
        
        count = 0
        for file_path in files:
            print(f"Processing: {file_path}")
            with open(file_path, 'r', encoding='utf-8') as f_in:
                for line in f_in:
                    match = pattern.search(line.strip())
                    if match:
                        writer.writerow(match.groups())
                        count += 1
                        
    print(f"Complete ({output_file})! {len(files)} files are converted, {count} lines in total.")


batch_convert_logs_to_csv('logcat/logcat*.txt', 'combined_logs.csv')