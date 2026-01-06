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

def run_noise_benchmark(fs=44100, duration=5.0, expected_period=0.5):
    # 1. 生成纯高斯白噪声
    num_samples = int(fs * duration)
    noise = np.random.normal(0, 1, num_samples)
    
    # 2. 预处理
    data = np.diff(noise)
    window = np.hanning(len(data))
    
    # 3. 倒谱计算
    spectrum = np.abs(fft(data * window))
    log_spectrum = np.log10(spectrum**2 + 1e-12)
    # 去线性趋势
    x = np.arange(len(log_spectrum))
    coeffs = np.polyfit(x, log_spectrum, 1)
    log_spectrum -= np.polyval(coeffs, x)
    
    ceps = np.real(ifft(log_spectrum))
    half_ceps = ceps[:len(ceps)//2]
    quefrency = np.arange(len(half_ceps)) / fs
    
    # 4. Z-Score 统计
    # 避开极低频（前20ms会有系统性抬升）
    valid_mask = (quefrency > 0.02)
    bg_mean = np.mean(half_ceps[valid_mask])
    bg_std = np.std(half_ceps[valid_mask])
    
    # 在目标周期附近寻找“最强假峰”
    target_mask = (quefrency >= expected_period * 0.9) & (quefrency <= expected_period * 1.1)
    fake_peak = np.max(half_ceps[target_mask])
    
    z_score = (fake_peak - bg_mean) / (bg_std + 1e-12)
    

    return z_score

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
    plt.figure(figsize=(5, 3))

    # --- 子图 1: 波形图 (Waveform) ---
    # 适合观察脉冲在时间轴上的位置
    plt.subplot(2, 1, 1)
    librosa.display.waveshow(audio*100, sr=sr, alpha=0.8)
    # plt.title(f"{title}")
    plt.xlabel("Time (s)")
    plt.ylabel("Amplitude")


    png_out = os.path.splitext(inpath)[0] + f"{name}.png"
    plt.savefig(png_out, dpi=200)

    # 3. 保存或显示
    plt.show()


def normalize_audio(audio):

    target_rms = 0.01

    rms = np.sqrt(np.mean(audio**2))

    mid = np.percentile(np.abs(audio), 70)

    out_sig = audio * (target_rms / ((rms+mid)/2 + 1e-9))
    out_sig = np.clip(out_sig, -1.0, 1.0)
    return out_sig

def save_audio(audio, name="output"):
    wav.write(f"{name}.wav", sr, (audio * 32767).astype(np.int16))
    print(f"降噪音频已保存为: {name}.wav")

# -----------------------------
# 主程序
# -----------------------------

if __name__ == "__main__":
    # 运行 10 次查看噪声得分的分布
    # noise_scores = [run_noise_benchmark() for _ in range(10)]
    # print(f"随机噪声平均得分: {np.mean(noise_scores):.2f}")
    # print(f"随机噪声最高得分: {np.max(noise_scores):.2f}")

    if len(sys.argv) < 2:
        print("Usage: python morse_band_scan.py input.aac")
        sys.exit(1)

    inpath = sys.argv[1]
    if not os.path.exists(inpath):
        print("File not found:", inpath)
        sys.exit(1)

    segt = 1100
    # --- 读取音频
    seg = AudioSegment.from_file(inpath)
    sr = seg.frame_rate
    seg = seg[segt:segt+2500]  # 取 1~6 秒

    num_samples = int(sr * 2.5)
    noise = np.random.normal(0, 1, num_samples)

    samples, sr = audiosegment_to_numpy(seg)

    # samples, sr = noise, 48000

    duration_s = len(samples) / sr
    print(f"Loaded {inpath}: {sr} Hz, {duration_s:.2f}s")

    frame_ms = 50
    hop_ms = 10
    frame_size = int(sr * frame_ms / 1000)
    hop_size = int(sr * hop_ms / 1000)

    best_score = -np.inf
    best_audio = None
    best_waveform = None

    for denoise in [0.2,0.4,0.6,0.8,1]:
        denoised_sample = professional_denoise(samples, sr, denoise)
        normalized_sample = normalize_audio(denoised_sample)

        waveform = audio_to_waveform_array(normalized_sample)

        score = clarity_score(waveform)
        if score > best_score:
            best_score = score
            best_waveform = waveform
            best_audio = normalized_sample

    print(f"best score: {score}")
    save_audio(best_audio)
    visualize_audio(best_audio, sr, f"{score}")
    # --- 保存 CSV
    # plot(best_samples, os.path.splitext(inpath)[0],best_score)
    # --- 绘图（支持自定义尺寸）

    print("Done.")
