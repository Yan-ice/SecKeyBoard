import numpy as np
import sys
import os

from pydub import AudioSegment
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from scipy.signal import butter, filtfilt, lfilter, find_peaks

import noisereduce as nr
import scipy.io.wavfile as wav
import librosa
import librosa.display



def professional_denoise(samples, fs, prop_decrease=1.0):
    samples = samples.astype(np.float32)
    reduced_noise = nr.reduce_noise(
        y=samples, 
        sr=fs, 
        stationary=False, 
        prop_decrease=prop_decrease,
        time_mask_smooth_ms=50
    )
    
    return reduced_noise

def audio_to_waveform_array(y, width=1000):

    y = y.flatten()
    samples_per_pixel = len(y) // width
    y_trimmed = y[:width * samples_per_pixel]
    reshaped_y = y_trimmed.reshape(width, samples_per_pixel)
    v_max = np.max(reshaped_y, axis=1)
    v_min = np.min(reshaped_y, axis=1)
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
    Finds a subset of k peaks that best satisfy amplitude consistency 
    and interval uniformity.

    Parameters:
    ----------
    peaks : ndarray
        Indices of detected peaks.
    env_db : ndarray
        Signal envelope data used to evaluate peak magnitude.
    target_k : int
        Number of peaks to select.
    candidate_k : int
        Number of strongest peaks to consider for combinations.
    w_amp : float
        Weight for amplitude consistency (Coefficient of Variation).
    w_gap : float
        Weight for interval uniformity (Coefficient of Variation).

    Returns:
    -------
    selected_peaks : ndarray
        Sorted indices of the optimal k peaks.
    """

    # Return all peaks if count is below target
    if len(peaks) <= target_k:
        return np.sort(peaks)

    # Pre-select the strongest candidate_k peaks to reduce search space
    peak_amplitudes = env_db[peaks]
    top_indices = np.argsort(peak_amplitudes)[-candidate_k:]
    candidate_peaks = np.sort(peaks[top_indices])

    best_score = float('inf')
    selected_peaks = candidate_peaks[:target_k] # Initialization

    for combo in combinations(candidate_peaks, target_k):
        combo = np.array(combo)
        
        # Calculate Amplitude Consistency (CV)
        amps = env_db[combo]
        amp_mean = np.mean(amps)
        cv_amp = np.std(amps) / (amp_mean + 1e-6) if amp_mean != 0 else 1.0
        
        # Calculate Interval Uniformity (CV of gaps)
        gaps = np.diff(combo)
        gap_mean = np.mean(gaps)
        cv_gap = np.std(gaps) / (gap_mean + 1e-6) if gap_mean != 0 else 1.0
        
        # Composite score (lower is better)
        score = (w_amp * cv_amp) + (w_gap * cv_gap)
        
        if score < best_score:
            best_score = score
            selected_peaks = combo

    return np.sort(selected_peaks)

def impulse_significance_score(waveform_data, sr, target_peaks=4):

    env_db = (waveform_data[:, 0] - waveform_data[:, 1]) *100

    energy = env_db ** 2
    rms = np.sqrt(np.mean(energy))
    threshold = np.percentile(energy, 80)
    filtered_energy = energy[energy <= threshold]
    if len(filtered_energy) > 0:
        rms = np.sqrt(np.mean(filtered_energy))

    peaks, properties = find_peaks(env_db, distance=20)

    if len(peaks) < target_peaks:
        print(f"len(peak_values) < target_peaks")
        return 0.0
        
    final_peaks_indices = find_best_k_peaks(peaks, env_db,4,12)
    selected_peaks = np.sort(final_peaks_indices)

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

def visualize_audio(audio, sr, name="output"):

    plt.figure(figsize=(3.5, 2))

    plt.subplot(2, 1, 1)
    librosa.display.waveshow(audio[:sr*3]*100, sr=sr, alpha=0.8)
    # plt.title(f"{title}")
    plt.xlabel("Time (s)")
    plt.ylabel("Amplitude")

    png_out = f"{name}.png"
    plt.savefig(png_out, dpi=200)


def normalize_audio(audio):

    target_rms = 0.02

    rms = np.sqrt(np.mean(audio**2))

    mid = np.percentile(np.abs(audio), 70)

    out_sig = audio * (target_rms / ((rms+mid)/2 + 1e-9))
    out_sig = np.clip(out_sig, -1.0, 1.0)
    return out_sig

def save_audio(audio, sr, name="output"):
    wav.write(f"{name}.wav", sr, (audio * 32767).astype(np.int16))
    print(f"Audio Saved: {name}.wav")


# -----------------------------

def test_audio(device, amp, dis):

    inpath = f"soundtrack/{device}{amp}{dis}.m4a"

    if not os.path.exists(inpath):
        print("File not found:", inpath)
        return 0.0

    segt = 10000

    seg = AudioSegment.from_file(inpath)
    sr = seg.frame_rate
    seg = seg[segt:segt+8000]  #  11~19 sec

    samples, sr = audiosegment_to_numpy(seg)

    duration_s = len(samples) / sr
    print(f"Loaded {inpath}: {sr} Hz, {duration_s:.2f}s")

    best_score = -np.inf
    best_audio = None
    best_waveform = None

    normalized_sample = normalize_audio(samples)
    normalized_sample = professional_denoise(normalized_sample, sr, 0.5)
    normalized_sample = normalize_audio(normalized_sample[sr:])

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
        # save_audio(best_audio, sr, f"{device}{amp}{dis}")
        visualize_audio(best_audio, sr, f"{device}{amp}{dis}")

    return best_score

if __name__ == "__main__":

    if len(sys.argv) > 1:
        amp = int(sys.argv[1])
        dis = int(sys.argv[2])
        score = test_audio(amp, dis)
        print(f"score: {score}")
        sys.exit(0)

    device = 'N' # P=Pixel2, N=Nova12, H=Honor8
    title = 'Huawei Nova 12'

    results = []
    for amp in [1, 2, 3, 4, 5]:
        for dis in [2, 4, 6, 8, 10]:
            score = test_audio(device, amp, dis)
            results.append({'amp': amp*10, 'dis': dis*10, 'score': score})
    df = pd.DataFrame(results)
    df['score'] = pd.to_numeric(df['score'], errors='coerce')

    print(df)
    matrix = df.pivot(index='amp', columns='dis', values='score')

    plt.figure(figsize=(4.5, 3.8))
    
    sns.heatmap(matrix, annot=True, fmt=".2f", cmap='YlGnBu',
        vmin=0, vmax=8, cbar_kws={'label': 'Score'})

    plt.title(f'(c) {title}')
    plt.xlabel('Distance (cm)')
    plt.ylabel('Vibration Time (ms)')

    plt.gca().invert_yaxis()
    
    plt.show()
    