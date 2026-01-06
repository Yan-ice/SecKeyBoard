import csv
import re
import glob

def batch_convert_logs_to_csv(input_pattern, output_file):
    # 正则表达式：匹配行尾由逗号分隔的四个数字
    pattern = re.compile(r'(\d+),\s*(\d+),\s*(\d+),\s*(\d+)$')
    
    # 获取所有匹配的文件列表
    files = glob.glob(input_pattern)
    if not files:
        print("未找到匹配的文件")
        return

    with open(output_file, mode='w', newline='', encoding='utf-8') as f_out:
        writer = csv.writer(f_out)
        # 1. 写入统一的表头
        writer.writerow(['click', 'time', 'labela', 'labelb'])
        
        count = 0
        for file_path in files:
            print(f"正在处理: {file_path}")
            with open(file_path, 'r', encoding='utf-8') as f_in:
                for line in f_in:
                    match = pattern.search(line.strip())
                    if match:
                        writer.writerow(match.groups())
                        count += 1
                        
    print(f"转换完成！共整合 {len(files)} 个文件，计 {count} 行数据至 {output_file}")

# --- 执行 ---
# 假设你的文件都在当前目录下，且命名为 logcat1.txt, logcat2.txt ...
# 使用通配符 'logcat*.txt' 匹配所有相关文件
batch_convert_logs_to_csv('logcat*.txt', 'combined_logs.csv')