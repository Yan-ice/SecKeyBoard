import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np

import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
from sklearn.metrics import confusion_matrix
from sklearn.model_selection import KFold

def plot_confusion_matrix(y_true, y_pred, labels, title='Confusion Matrix', save_path=None):
    """
    绘制符合论文标准的混淆矩阵图。
    
    参数:
    y_true: 真实标签 (1D array)
    y_pred: 预测标签 (1D array)
    labels: 类别名称列表 (list of strings)
    """
    # 1. 计算混淆矩阵
    cm = confusion_matrix(y_true, y_pred)
    # 计算归一化矩阵（每一行的比例）
    cm_normalized = cm.astype('float') / cm.sum(axis=1)[:, np.newaxis]

    # 2. 设置绘图风格
    plt.figure(figsize=(7, 6), dpi=100) # 高分辨率
    sns.set_theme(style="white") # 纯白背景

    # 3. 绘制热力图
    # annot 为 True 显示数值，fmt='.2f' 保持两位小数
    # cmap 可选 'Blues', 'viridis', 'magma' 等学术常用色系
    ax = sns.heatmap(cm_normalized, annot=True, fmt='.2f', cmap='Blues',
                    xticklabels=labels, yticklabels=labels,
                    annot_kws={"size": 12, "weight": "bold"}, # 数值字体设置
                    cbar_kws={'label': 'Accuracy Rate'})

    # 4. 细节微调
    plt.title(title, fontsize=16, pad=20)
    plt.ylabel('True Label', fontsize=14)
    plt.xlabel('Predicted Label', fontsize=14)
    
    # 让坐标轴文字垂直/水平排列
    plt.xticks(rotation=0)
    plt.yticks(rotation=0)

    # 5. 布局优化
    plt.tight_layout()

    # 6. 保存与展示
    if save_path:
        plt.savefig(save_path, bbox_inches='tight')
        print(f"图像已保存至: {save_path}")
    
    plt.show()

# 1. 定义神经网络模型
class SimplePredictor(nn.Module):
    def __init__(self, input_size, hidden_size, output_size):
        super(SimplePredictor, self).__init__()
        # 定义第一层：输入层 -> 隐藏层
        self.fc1 = nn.Linear(input_size, hidden_size)
        # 定义激活函数：ReLU (Rectified Linear Unit)
        self.relu = nn.ReLU()
        # 定义第二层：隐藏层 -> 输出层
        self.fc2 = nn.Linear(hidden_size, output_size)

    def forward(self, x):
        # 前向传播过程
        out = self.fc1(x)
        out = self.relu(out)
        out = self.fc2(out)
        return out

# 2. 训练接口
def train_model(model, X_train, y_train, criterion, optimizer, num_epochs=100):
    """
    模型训练接口
    :param model: 待训练的模型实例
    :param X_train: 训练集特征 (PyTorch Tensor)
    :param y_train: 训练集标签 (PyTorch Tensor)
    :param criterion: 损失函数
    :param optimizer: 优化器
    :param num_epochs: 训练的轮次
    :return: 训练后的模型
    """
    model.train() # 设置模型为训练模式
    print("--- 开始训练 ---")
    for epoch in range(num_epochs):
        # 1. 前向传播
        outputs = model(X_train)
        loss = criterion(outputs, y_train)

        # 2. 反向传播与优化
        optimizer.zero_grad() # 梯度清零
        loss.backward()       # 计算梯度
        optimizer.step()      # 更新权重

        if (epoch + 1) % 10 == 0:
            print(f'Epoch [{epoch+1}/{num_epochs}], Loss: {loss.item():.4f}')
    print("--- 训练完成 ---")
    return model

# 3. 预测接口
def predict(model, X_new):
    """
    模型预测接口
    :param model: 训练好的模型实例
    :param X_new: 待预测的新数据 (PyTorch Tensor)
    :return: 模型的预测结果 (PyTorch Tensor)
    """
    model.eval() # 设置模型为评估模式 (不计算梯度，不启用Dropout等)
    with torch.no_grad(): # 在预测时，不需要计算梯度
        predictions = model(X_new)
    return predictions

from sklearn.metrics import accuracy_score, confusion_matrix

def evaluate(Y_predict, Y_label):
    """
    计算分类预测结果的准确率和混淆矩阵。
    
    :param Y_predict: 模型预测的类别标签 (e.g., [0, 1, 0, 2, ...])
    :param Y_label: 真实的类别标签 (e.g., [0, 1, 1, 2, ...])
    :return: 包含准确率和混淆矩阵的字典
    """
    
    # 1. 确保输入是 numpy 数组（如果它们还不是）
    Y_predict = np.array(Y_predict, dtype=int)
    Y_label = np.array(Y_label, dtype=int)
    
    # --- 1. 计算准确率 (Accuracy) ---
    accuracy = accuracy_score(Y_label, Y_predict)
    
    # --- 2. 计算混淆矩阵 (Confusion Matrix) ---
    all_labels = np.unique(np.concatenate((Y_label, Y_predict)))
    
    conf_matrix = confusion_matrix(
        y_true=Y_label, 
        y_pred=Y_predict, 
        labels=all_labels  # 确保矩阵包含所有类别
    )

    return accuracy, conf_matrix, all_labels.tolist()

import pandas as pd
import numpy as np
import torch
from sklearn.preprocessing import MinMaxScaler, StandardScaler

DIM_SIZE = 3
TOTAL_STATES = DIM_SIZE * DIM_SIZE # 9

## 1. 批量编码函数 (Batch Encoding)
def batch_loc_to_indices(ab_list):
    indices = []
    for a, b in ab_list:
        if not (0 <= a < DIM_SIZE and 0 <= b < DIM_SIZE):
            raise ValueError(f"输入值 ({a}, {b}) 必须在 0 到 {DIM_SIZE - 1} 之间。")
        index = a * DIM_SIZE + b
        indices.append(index)
    return indices

def batch_indices_to_loc(indices):
    indices_np = np.array(indices)
    # 批量计算 a 坐标
    a_coords = indices_np // DIM_SIZE
    # 批量计算 b 坐标
    b_coords = indices_np % DIM_SIZE
    decoded_list = list(zip(a_coords.tolist(), b_coords.tolist()))
    
    return decoded_list

def batch_one_hot_encode(indices):
    num_samples = len(indices)
    one_hot_matrix = np.zeros((num_samples, TOTAL_STATES), dtype=np.float32)
    one_hot_matrix[np.arange(num_samples), indices] = 1
    return one_hot_matrix

def batch_one_hot_decode(one_hot_matrix):
    indices = np.argmax(one_hot_matrix, axis=1)
    return indices


def load_and_prepare_data(file_path):
    """
    读取CSV文件，并将数据划分为特征 (X) 和标签 (Y)。

    :param file_path: CSV 文件路径
    :return: X_numpy, Y_numpy (Numpy 数组)
    """
    try:
        # 1. 读取 CSV 文件
        # header=0 表示文件的第一行是列名
        df = pd.read_csv(file_path)

        # 2. 定义特征 (X) 和标签 (Y) 的列名
        # 注意：你的要求是 Feature3 和 Feature4 作为 Y
        feature_cols = ['click', 'time']
        label_cols = ['labela', 'labelb']

        # 3. 提取 X 和 Y
        X_df = df[feature_cols]
        Y_df = df[label_cols]
        
        # 4. 转换为 Numpy 数组 (神经网络训练常用格式)
        X_numpy = X_df.values.astype(np.float32)
        Y_numpy = Y_df.values.astype(np.int32)
        Y_numpy = batch_loc_to_indices(Y_numpy)

        scaler = StandardScaler()
        X_numpy = scaler.fit_transform(X_numpy)

        return X_numpy, Y_numpy

    except FileNotFoundError:
        print(f"错误：文件未找到，请检查路径：{file_path}")
        return None, None
    except Exception as e:
        print(f"处理数据时发生错误：{e}")
        return None, None

def numpy_to_torch(X_numpy, Y_numpy):
    """
    将 Numpy 数组转换为 PyTorch Tensor。
    """
    if X_numpy is None or Y_numpy is None:
        return None, None
    
    # 转换为 PyTorch Tensor
    X_tensor = torch.from_numpy(X_numpy)
    Y_tensor = torch.from_numpy(Y_numpy)
    
    return X_tensor, Y_tensor

def k_fold_cross_validation(X, Y, k=5):

    INPUT_SIZE = 2
    HIDDEN_SIZE = 16
    OUTPUT_SIZE = 9
    LEARNING_RATE = 0.001

    """
    X: 全量特征 Tensor
    Y: 全量标签 Tensor (One-hot 编码)
    k: 折数
    """
    # 初始化 KFold 拆分器
    kf = KFold(n_splits=k, shuffle=True, random_state=42)
    
    # 用于存储全量预测结果的占位数组
    # 初始化大小与原标签一致，假设 Y 是 (N, num_classes)
    all_predictions = np.zeros(len(Y))
    
    # 记录每一折的索引，确保结果最后能对应上
    for fold, (train_idx, val_idx) in enumerate(kf.split(X)):
        print(f"\n--- 正在进行第 {fold + 1} / {k} 折训练 ---")
        
        # 1. 划分当前折的数据
        X_train, X_val = X[train_idx], X[val_idx]
        Y_train, Y_val = Y[train_idx], Y[val_idx]
        
        # 2. 每一折都需要重新初始化模型，防止“带记忆”训练
        model = SimplePredictor(INPUT_SIZE, HIDDEN_SIZE, OUTPUT_SIZE)
        criterion = nn.CrossEntropyLoss()
        optimizer = optim.Adam(model.parameters(), lr=LEARNING_RATE)
        
        # 3. 训练模型 (仅使用当前的 X_train)
        trained_model = train_model(model, X_train, Y_train, criterion, optimizer, num_epochs=1000)
        
        # 4. 在当前折的验证集上进行预测 (X_val)
        # 假设 predict 函数返回的是 Tensor，需要 decode 为原始类别索引
        val_probs = predict(trained_model, X_val)
        val_preds = batch_one_hot_decode(val_probs)
        
        # 5. 将预测结果填入对应的位置
        all_predictions[val_idx] = val_preds
        
    return all_predictions

# --- 运行示例 ---
if __name__ == '__main__':
    # 假设你的 CSV 文件名为 'data.csv' 并且在当前目录下
    file_path = 'combined_logs.csv'

    # 1. 调用读取器获取 Numpy 数组数据
    X_train_numpy, Y_train_numpy = load_and_prepare_data(file_path)

    if X_train_numpy is not None:


        X_train_tensor, Y_train_tensor = numpy_to_torch(X_train_numpy, batch_one_hot_encode(Y_train_numpy))

        # start K-fold
        predictions = k_fold_cross_validation(X_train_tensor, Y_train_tensor, 5)

        acc, conf_matrix, labels = evaluate(predictions, Y_train_numpy)

        print("accuacy:", acc)

        print("conf_matrix:", conf_matrix)
        print("labels:", labels)
        plot_confusion_matrix(Y_train_numpy,predictions,batch_indices_to_loc(labels))


        