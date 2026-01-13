import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import matplotlib.pyplot as plt
import numpy as np

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

# data['Number Input']
# 创建图表：结合带散点的琴弦图

scores1 = data['Number Input']
y_ticks1 = [1.5, 1.75, 2, 2.25, 2.5, 2.75, 3.0]
scores1.sort()
rank1 = np.arange(1, len(scores1) + 1)

scores2 = data['Option Input']
y_ticks2 = [1, 1.25, 1.5, 1.75, 2, 2.25, 2.5]
scores2.sort()
rank2 = np.arange(1, len(scores2) + 1)

# # # 2. 创建画布
# fig = plt.figure(figsize=(5, 2))
# # 使用 GridSpec 分配空间，左侧占 3/4，右侧占 1/4
# gs = fig.add_gridspec(1, 2, width_ratios=[7, 3], wspace=0.05)

# # --- 左图：升序折线图 ---
# ax1 = fig.add_subplot(gs[0])
# ax1.plot(rank, scores, marker='o', color='#1E3D59', linestyle='-', linewidth=2, markersize=6)
# ax1.set_xlabel("Student Rank (Sorted)")
# ax1.set_ylabel("Scores")
# ax1.set_yticks(y_ticks)
# ax1.set_xticks(rank)
# ax1.grid(axis='y', linestyle='--', alpha=0.6)

# # --- 右图：横向频率直方图 ---
# ax2 = fig.add_subplot(gs[1], sharey=ax1) # 共享纵坐标
# sns.histplot(y=scores, bins=y_ticks, ax=ax2, color='#D0E1F9', edgecolor='white')
# ax2.set_xlabel("Frequency")
# ax2.set_xticks([1,2,3,4,5,6])
# ax2.set_ylabel("") # 隐藏重复的纵轴标签
# ax2.tick_params(labelleft=False) # 隐藏右侧图的坐标刻度

# plt.suptitle("Student Scores: Distribution and Individual Progression", fontsize=14)
# plt.show()




# 创建画布
# fig, ax1 = plt.subplots(figsize=(3.2, 3.5))

# # --- 1. 绘制直方图 (基于底部的 ax1) ---
# sns.histplot(y=scores, bins=y_ticks, ax=ax1, color='#D0E1F9', edgecolor='white', alpha=0.7)
# ax1.set_xlabel("Frequency", color='#1E3D59', fontsize=12)
# ax1.set_ylabel("Input Speed (sec/digit)", fontsize=12)
# ax1.set_yticks(y_ticks)
# ax1.set_xticks([0, 1, 2, 3, 4, 5, 6]) # 根据你的数据分布调整
# ax1.grid(axis='y', linestyle='--', alpha=0.4)

# # --- 2. 绘制折线图 (创建双横轴 ax2) ---
# ax2 = ax1.twiny() # 共享 Y 轴，创建新的 X 轴
# ax2.plot(rank, scores, marker='o', color='#1E3D59', linestyle='-', linewidth=2, markersize=5, label='Rank Progression')

# # --- 3. 细节调整 ---
# # 隐藏 ax2 (折线图) 的 X 轴刻度和标签，因为你说“用户编号无意义”
# ax2.set_xticks([]) 
# ax2.set_xlabel("")

# # 设置统一的 Y 轴范围
# ax1.set_ylim(min(y_ticks)-0.1, max(y_ticks)+0.1)

# plt.title("Score Distribution and Progression Overlay", fontsize=14, pad=20)
# plt.tight_layout()
# plt.show()

# 2. 创建画布：一行两列
fig, (ax1_left, ax2_left) = plt.subplots(1, 2, figsize=(6, 3))

# --- 绘制第一张图 (Number Input) ---
# 背景直方图
sns.histplot(y=scores1, bins=y_ticks1, ax=ax1_left, color='#D0E1F9', edgecolor='white', alpha=0.7)
ax1_left.set_yticks(y_ticks1)
ax1_left.set_xlabel("Frequency (Number Input)", color='#555555')

ax1_left.set_ylabel("Input Speed (sec/digit)")
ax1_left.grid(axis='y', linestyle='--', alpha=0.4)
ax1_left.set_ylim(min(y_ticks1)-0.1, max(y_ticks1)+0.1)
ax1_left.set_xticks([0,1,2,3,4,5,6]) 
ax1_left.set_xlim(0,6)

# 前景折线图 (双横轴)
ax1_right = ax1_left.twiny()
ax1_right.plot(rank1, scores1, marker='o', color='#1E3D59', markersize=4, linewidth=1.5)
ax1_right.set_xticks([]) # 隐藏排名横坐标
ax1_right.set_xlim(0.5, len(scores1) + 0.5) # 微调范围使点居中

# --- 绘制第二张图 (Option Input) ---
# 背景直方图
sns.histplot(y=scores2, bins=y_ticks2, ax=ax2_left, color='#E2F0CB', edgecolor='white', alpha=0.7)
ax2_left.set_yticks(y_ticks2)
ax2_left.set_xlabel("Frequency (Option Input)", color='#555555')
ax2_left.set_ylabel("Input Speed (sec/digit)")
ax2_left.grid(axis='y', linestyle='--', alpha=0.4)
ax2_left.set_ylim(min(y_ticks2)-0.1, max(y_ticks2)+0.1)
ax2_left.set_xticks([0,1,2,3,4,5,6]) 
ax2_left.set_xlim(0,6)

# 前景折线图 (双横轴)
ax2_right = ax2_left.twiny()
ax2_right.plot(rank2, scores2, marker='o', color='#467302', markersize=4, linewidth=1.5)
ax2_right.set_xticks([]) # 隐藏排名横坐标
ax2_right.set_xlim(0.5, len(scores2) + 0.5)

# 3. 总体调整
plt.suptitle("Comparative Analysis of Input Methods: Distribution & Progression", fontsize=14, y=1.05)
plt.tight_layout()
plt.show()