import csv
import re


def convert_log_to_csv(input_text, output_file):
    # 正则表达式：匹配行尾由逗号分隔的四个数字
    pattern = re.compile(r'(\d+),\s*(\d+),\s*(\d+),\s*(\d+)$')
    
    with open(output_file, mode='w', newline='') as f:
        writer = csv.writer(f)
        # 写入表头（根据你的数据含义命名，这里暂定为 Val1-Val4）
        writer.writerow(['Value1', 'Value2', 'Value3', 'Value4'])
        
        for line in input_text.strip().split('\n'):
            match = pattern.search(line.strip())
            if match:
                # 提取匹配到的四个数字组
                writer.writerow(match.groups())

# 执行转换
# 如果你从文件读取，请替换为 open('log.txt').read()
FILE = 'logcat1'
convert_log_to_csv(open(f"{FILE}.txt", F'{FILE}.csv')
print("转换完成")
