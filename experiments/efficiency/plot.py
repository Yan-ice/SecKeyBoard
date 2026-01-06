import pandas as pd
import matplotlib.pyplot as plt

# 1. 准备数据
data = {
    'Number Input': [
        1.518246147,
        2.561191891,
        1.844266521,
        2.653295266,
        1.782980551,
        1.53861001,
        2.646943992,
        2.896260417,
        2.116010329,
        2.103089259,
        2.885454687,
        2.679300839,
        2.694171815,
        2.632410034,
        2.925294393
    ],
    'Option Input': [
        1.205822797,
        1.612122613,
        2.279953441,
        1.313668945,
        1.824850109,
        1.783010146,
        1.96091181,
        1.968500532,
        2.000153071,
        1.269292918,
        2.000317895,
        1.747390786,
        1.860958524,
        2.406213072,
        1.63135634
    ]
}
df = pd.DataFrame(data)

# 2. 定义区间边缘和标签
# float('-inf') 表示负无穷，float('inf') 表示正无穷
boundary_bins = [1.2,1.5, 1.8, 2.1, 2.4, 2.7]

# 3. 绘图
plt.figure(figsize=(4, 4))

# 使用 plt.hist 绘制，rwidth 控制柱子宽度（0.9 表示留出 10% 缝隙，1.0 则完全紧挨）
n, bins, patches = plt.hist(df['Option Input'], bins=boundary_bins, color='skyblue', 
                            edgecolor='black', rwidth=1)
# 4. 美化调整
# 设置刻度在分界点上
plt.xticks(boundary_bins)

# 纵向留白
plt.ylim(0, 7)

# 添加数值标注 (标注在柱子中心)
for i in range(len(n)):
    if n[i] > 0: # 只标注有数据的柱子
        plt.text((bins[i] + bins[i+1]) / 2, n[i] + 0.1, 
                 str(int(n[i])), ha='center', fontweight='bold')

# 标题与轴标签
plt.title('Distribution of Option Input Speed', fontsize=14, pad=15)
plt.xlabel('sec/digit (Boundary Points)', fontsize=12)
plt.ylabel('Frequency', fontsize=12)

plt.grid(axis='y', linestyle='--', alpha=0.5)
plt.tight_layout()
plt.show()