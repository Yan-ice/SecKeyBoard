import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np


# Mapping Instruction ID to Coordinate Label (for X-axis)
instruction_mapping = {
    0: '(0,0)',
    1: '(1,0)',
    2: '(1,1)',
    4: '(2,0)',
    5: '(2,1)',
    8: '(2,2)'
}

# ==================== 2. AFTER TRANING ====================

labels = [
    5, 4, 4, 2, 0, 2, 4, 1, 5, 5, 4, 1, 1, 8, 4, 0, 2, 4, 5, 5, 4, 8, 8, 1, 
    4, 5, 5, 1, 4, 4, 0, 4, 0, 8, 5, 8, 0, 4, 0, 4, 1, 0, 1, 4, 5, 0, 4, 4, 
    1, 4, 2, 8, 5, 8, 5, 1, 1, 1, 0, 2
]
# Time row: Y-axis values (Standardized Time)
times = [
    0.928840728, -0.464420364, 0.232210182, -0.464420364, -1.16105091, 1.625471274, 
    1.625471274, -1.16105091, 0.232210182, 0.232210182, -1.16105091, -0.464420364, 
    0.183702358, -0.761052628, -0.761052628, 0.813539016, -1.075970956, -0.13121597, 
    -0.13121597, 0.498620687, 2.388130659, 0.183702358, 0.183702358, -1.390889285, 
    -1.543804824, -0.475016869, -0.118754217, 2.375084344, -0.118754217, 
    -0.475016869, -0.118754217, 0.237508434, -0.83127952, 1.306296389, 
    -0.118754217, -0.118754217, -1.117240775, 0.139655097, 0.139655097, 
    -1.536206066, 0.139655097, -0.698275485, 0.977585679, -0.698275485, 
    0.558620388, 0.139655097, -0.279310194, 2.234481551, 0.536388754, 
    0.237008984, -0.51144044, 0.686078639, 1.883597717, 1.584217948, 
    -0.661130325, -0.810820209, -0.51144044, -1.110199979, -0.21206067, 
    -1.110199979
]


# Check if data lengths match
if len(labels) != len(times):
    print("Error: The number of labels and time data points do not match. Please check your input.")
    exit()

# ==================== 2. DATA FORMATTING (Long Format) ====================
data_df = pd.DataFrame({
    'Instruction ID': labels,      # Original numerical ID
    'Standardized Time': times     # Y-axis data
})

# Apply the mapping to replace numerical IDs with coordinate labels
data_df['Instruction Label'] = data_df['Instruction ID'].map(instruction_mapping)

# Ensure instruction labels are sorted for better visualization order (based on original ID)
# The order is defined by the keys in instruction_mapping: 0, 1, 2, 4, 5, 8
sorted_categories = [instruction_mapping[key] for key in sorted(instruction_mapping.keys())]

data_df['Instruction Label'] = pd.Categorical(
    data_df['Instruction Label'], 
    categories=sorted_categories, 
    ordered=True
)

# ==================== 3. PLOT VIOLIN CHART ====================
# Set figure size (10 inches wide, 6 inches high)
plt.figure(figsize=(5, 5))

# Plot the Violin Chart using Seaborn
sns.violinplot(
    x='Instruction Label', 
    y='Standardized Time', 
    data=data_df,
    inner='quartile', # Show quartile lines (median, Q1, Q3) inside the violin
    palette='Set2',   # Use a defined color palette
    cut=0             # Ensure violin boundaries do not extend beyond min/max data
)

# ==================== 4. CUSTOMIZATION (Aesthetics) ====================

# Set Title and Axis Labels
plt.ylabel('Standardized Time (Z-Score)', fontsize=13)
plt.xlabel('Vibration Pattern', fontsize=13)

# Add the Y=0 reference line (crucial for standardized data)
plt.axhline(0, color='red', linestyle='--', linewidth=1.5, alpha=0.7)

# Adjust layout to prevent labels from being cut off
plt.tight_layout()

# Save the plot as a high-quality vector graphic
plt.savefig('violin_plot_standardized_time_en.pdf', format='pdf', dpi=300)

plt.show()