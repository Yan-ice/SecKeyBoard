"""
morse_band_scan.py

用法:
  python morse_band_scan.py input.aac

功能:
  自动扫描多个频段 (例如每500Hz一个带宽)，
  计算每段的信号清晰度（响度波动强度），
  自动选择最可能包含摩斯信号的频段。

输出:
  - best_band_annotated.png  （最佳频段的波形与响度曲线）
  - best_band_frames.csv     （对应帧数据）
"""

import numpy as np
import noisereduce as nr
import scipy.io.wavfile as wav

def professional_denoise(samples, fs, prop_decrease=1.0):
    """
    使用成熟的谱门限技术进行降噪
    :param samples: 原始信号数组
    :param fs: 采样率
    :param prop_decrease: 降噪强度 (0.0 到 1.0)
    """
    # 强制转为 float32 提高精度
    samples = samples.astype(np.float32)
    
    # 核心步骤：
    # stationary=False 表示处理非平稳噪声（随时间变化的背景音）
    # time_mask_smooth_ms 能够帮助保护那些“瞬间”出现的周期脉冲
    reduced_noise = nr.reduce_noise(
        y=samples, 
        sr=fs, 
        stationary=False, 
        prop_decrease=prop_decrease,
        time_mask_smooth_ms=50  # 针对 0.5s 周期，这个平滑度能保护脉冲边缘
    )
    
    return reduced_noise

import sys
import os
import numpy as np
from pydub import AudioSegment
import matplotlib.pyplot as plt
import pandas as pd
from scipy.signal import butter, filtfilt, lfilter, find_peaks, correlate
from scipy.ndimage import median_filter

import numpy as np
import matplotlib.pyplot as plt
from scipy.fft import fft, ifft
import seaborn as sns


def audio_to_waveform_array(y, width=1000):

    # 确保信号是平铺的
    y = y.flatten()
    
    # 计算每个“像素列”包含多少个采样点
    samples_per_pixel = len(y) // width
    
    # 截断音频使其能被 width 整除，方便重塑
    y_trimmed = y[:width * samples_per_pixel]
    
    # 重塑数组：(width, samples_per_pixel)
    reshaped_y = y_trimmed.reshape(width, samples_per_pixel)
    
    # 提取每个区间的最大值和最小值（捕捉脉冲的关键）
    v_max = np.max(reshaped_y, axis=1)
    v_min = np.min(reshaped_y, axis=1)
    
    # 合并成一个绘图数组
    waveform_data = np.column_stack((v_max, v_min))
    
    return waveform_data


def clarity_score(waveform_data):
    envelope = waveform_data[:, 0] - waveform_data[:, 1]
    dbfs = 20 * np.log10(envelope + 1e-9)
    std_val = np.std(dbfs)
    mean_abs_val = np.mean(np.abs(dbfs))
    score = std_val / (mean_abs_val + 1e-6)
    return score

from scipy.signal import butter, lfilter

def bandpass_filter(data, lowcut, highcut, fs, order=5):
    nyq = 0.5 * fs
    low = lowcut / nyq
    high = highcut / nyq
    b, a = butter(order, [low, high], btype='band')
    y = lfilter(b, a, data)
    return y

from itertools import combinations

def find_best_k_peaks(peaks, env_db, target_k=4, candidate_k=12, w_amp=1.0, w_gap=1.0):
    """
    在候选峰中寻找4个幅度最接近且间距最均匀的峰。
    
    参数:
    ----------
    peaks : ndarray
        所有检测到的峰的索引位置。
    env_db : ndarray
        信号的包络线数据（dB或幅值），用于评估峰的强度。
    top_k : int
        预选最强峰的数量。值越大搜索越精准，但组合数会增加。
    w_amp : float
        幅度接近度的权重。
    w_gap : float
        间距均匀度的权重。
        
    返回:
    -------
    selected_peaks : ndarray
        最终选定的4个峰的时间顺序索引。如果没有找到，返回前4个最强的峰。
    """
    # 1. 基础检查：如果峰数不足4个，直接返回所有峰
    if len(peaks) <= 4:
        return np.sort(peaks)

    # 2. 预筛选：为了计算性能，只在最强的 top_k 个候选峰中寻找最优组合
    # 这样可以将组合数控制在 C(top_k, 4) 以内 (例如 C(12,4)=495)
    peak_amplitudes = env_db[peaks]
    top_indices = np.argsort(peak_amplitudes)[-candidate_k:]
    candidate_peaks = np.sort(peaks[top_indices])

    best_score = float('inf')
    selected_peaks = candidate_peaks[:target_k] # 默认初始化

    # 3. 遍历所有 4 峰组合
    for combo in combinations(candidate_peaks, target_k):
        combo = np.array(combo)
        
        # --- 计算幅度一致性 (Coefficient of Variation) ---
        amps = env_db[combo]
        amp_mean = np.mean(amps)
        cv_amp = np.std(amps) / (amp_mean + 1e-6) if amp_mean != 0 else 1.0
        
        # --- 计算间距均匀性 (Coefficient of Variation) ---
        gaps = np.diff(combo)
        gap_mean = np.mean(gaps)
        cv_gap = np.std(gaps) / (gap_mean + 1e-6) if gap_mean != 0 else 1.0
        
        # --- 综合评分 (越低越好) ---
        score = (w_amp * cv_amp) + (w_gap * cv_gap)
        
        if score < best_score:
            best_score = score
            selected_peaks = combo

    return np.sort(selected_peaks)

def impulse_significance_score(waveform_data, sr, target_peaks=4):
    """
    逻辑：动态调整阈值寻找前N个峰值，计算峰值平均值与最终阈值的差距。
    """

    env_db = (waveform_data[:, 0] - waveform_data[:, 1]) *100

    energy = env_db ** 2
    rms = np.sqrt(np.mean(energy))
    threshold = np.percentile(energy, 80)
    filtered_energy = energy[energy <= threshold]
    if len(filtered_energy) > 0:
        rms = np.sqrt(np.mean(filtered_energy))

    # 设定步长进行迭代（或者直接取前N个局部极大值）
    # 这里采用更高效的方法：寻找所有的局部极大值（峰值）
    from scipy.signal import find_peaks
    
    # 寻找所有可能的峰值点
    peaks, properties = find_peaks(env_db, distance=20) # distance防止重复计数同一个脉冲

    if len(peaks) < target_peaks:
        # 如果峰值数量不足，说明信号过于平坦或脉冲太少
        print(f"len(peak_values) < target_peaks")
        return 0.0
        
    final_peaks_indices = find_best_k_peaks(peaks, env_db,4,12)

    selected_peaks = np.sort(final_peaks_indices)

    # 4. 计算强度得分
    peak_avg = np.mean(env_db[selected_peaks])

    base_significance = peak_avg - rms

    # intervals = np.diff(selected_peaks)
    # remainder = intervals % 0.75
    # distance_to_nearest_05 = np.minimum(remainder, 0.75 - remainder)
    # interval_scores = 1.0 - (distance_to_nearest_05 / 0.25)
    # spacing_score = np.mean(interval_scores)

    # if spacing_score < 0.3:
    #     return 0

    return base_significance

def audiosegment_to_numpy(seg: AudioSegment):
    samples = np.array(seg.get_array_of_samples())
    if seg.channels > 1:
        samples = samples.reshape((-1, seg.channels)).T
        samples = samples.mean(axis=0)
    samples = samples.astype(np.float32) / (2 ** (8 * seg.sample_width - 1))
    return samples, seg.frame_rate


def frame_rms(signal_array, frame_size, hop_size, sr):
    n = len(signal_array)
    frames, times = [], []
    for start in range(0, n - frame_size + 1, hop_size):
        frame = signal_array[start:start + frame_size]
        rms = np.sqrt(np.mean(frame.astype(np.float64) ** 2))
        frames.append(rms)
        times.append((start + frame_size / 2) / sr)
    return np.array(frames), np.array(times)


def rms_to_dbfs(rms_array, ref=1.0, floor_db=-120.0):
    with np.errstate(divide='ignore'):
        db = 20.0 * np.log10(np.maximum(rms_array, 1e-12) / ref)
    return np.maximum(db, floor_db)

import librosa
import librosa.display

def visualize_audio(audio, sr, name="output"):

    # 2. 创建画布
    plt.figure(figsize=(3.5, 2))

    # --- 子图 1: 波形图 (Waveform) ---
    # 适合观察脉冲在时间轴上的位置
    plt.subplot(2, 1, 1)
    librosa.display.waveshow(audio[:sr*3]*100, sr=sr, alpha=0.8)
    # plt.title(f"{title}")
    plt.xlabel("Time (s)")
    plt.ylabel("Amplitude")

    png_out = f"{name}.png"
    plt.savefig(png_out, dpi=200)

    # 3. 保存或显示
    # plt.show()


def normalize_audio(audio):

    target_rms = 0.02

    rms = np.sqrt(np.mean(audio**2))

    mid = np.percentile(np.abs(audio), 70)

    out_sig = audio * (target_rms / ((rms+mid)/2 + 1e-9))
    out_sig = np.clip(out_sig, -1.0, 1.0)
    return out_sig

def save_audio(audio, sr, name="output"):
    wav.write(f"{name}.wav", sr, (audio * 32767).astype(np.int16))
    print(f"降噪音频已保存为: {name}.wav")

# -----------------------------
# 主程序
# -----------------------------

def test_audio(device, amp, dis):

    inpath = f"soundtrack/{device}{amp}{dis}.m4a"

    if not os.path.exists(inpath):
        print("File not found:", inpath)
        return 0.0

    segt = 10000
    # --- 读取音频
    seg = AudioSegment.from_file(inpath)
    sr = seg.frame_rate
    seg = seg[segt:segt+8000]  # 取 11~19 秒

    samples, sr = audiosegment_to_numpy(seg)

    # samples, sr = noise, 48000

    duration_s = len(samples) / sr
    print(f"Loaded {inpath}: {sr} Hz, {duration_s:.2f}s")

    frame_ms = 50
    hop_ms = 10

    best_score = -np.inf
    best_audio = None
    best_waveform = None

    normalized_sample = normalize_audio(samples)
    normalized_sample = professional_denoise(normalized_sample, sr, 0.5)
    normalized_sample = normalize_audio(normalized_sample[sr:])

    # filter_bands =  [
    #     (20, 10000),
    #     (3000, 12000),
    #     (1000, 6000)
    # ]
    # for (low, high) in filter_bands:
        # actual_high = min(high, sr // 2 - 1) 
        # filtered_sample = bandpass_filter(normalized_sample, low, actual_high, sr)
        # if np.max(np.abs(filtered_sample)) < 1e-6:
        #     continue
    if True:
        for denoise in [0.6,0.8,1]:
            denoised_sample = professional_denoise(normalized_sample, sr, denoise)[sr:]
            waveform = audio_to_waveform_array(denoised_sample)

            score = impulse_significance_score(waveform, sr, 4)
            if score > best_score:
                best_score = score
                best_waveform = waveform
                best_audio = denoised_sample

    print(f"best score: {best_score}")
    if best_score > 0:
        save_audio(best_audio, sr, f"{device}{amp}{dis}")
        visualize_audio(best_audio, sr, f"{device}{amp}{dis}")
    # --- 保存 CSV
    # plot(best_samples, os.path.splitext(inpath)[0],best_score)
    # --- 绘图（支持自定义尺寸）

    return best_score

if __name__ == "__main__":

    if len(sys.argv) > 1:
        amp = int(sys.argv[1])
        dis = int(sys.argv[2])
        score = test_audio(amp, dis)
        print(f"score: {score}")
        sys.exit(0)

    device = 'P'

    results = []
    for amp in [1, 2, 3, 4, 5]:
        for dis in [2, 4, 6, 8, 10]:
            score = test_audio(device, amp, dis)
            results.append({'amp': amp*10, 'dis': dis*10, 'score': score})
    df = pd.DataFrame(results)
    df['score'] = pd.to_numeric(df['score'], errors='coerce')
    # values (格点内容) 对应 score
    print(df)
    matrix = df.pivot(index='amp', columns='dis', values='score')

    plt.figure(figsize=(4.5, 3.8))
    
    # annot=True 会在方格中显示具体的 score 数值
    # cmap 可选: 'viridis', 'magma', 'Blues', 'RdYlGn' 等
    sns.heatmap(matrix, annot=True, fmt=".2f", cmap='YlGnBu',
        vmin=0, vmax=8, cbar_kws={'label': 'Score'})

    # 设置标题和标签
    plt.title('(c) Huawei Nova 12')
    plt.xlabel('Distance (cm)')
    plt.ylabel('Vibration Time (ms)')
    
    # 让 Y 轴从小到大向上增长（更符合数学坐标系习惯）
    plt.gca().invert_yaxis()
    
    plt.show()
    