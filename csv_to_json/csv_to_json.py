import pandas as pd
import json

df = pd.read_csv("heroes.csv")

# isi NaN dengan isi sebelumnya (fill down)
df['hero'].fillna(method='ffill', inplace=True)
df['role'].fillna(method='ffill', inplace=True)

result = {}
for _, row in df.iterrows():
    hero = str(row['hero']).strip()
    role = str(row['role']).strip()
    counter = str(row['counter']).strip()
    explain = str(row['explain']).strip()

    if hero not in result:
        result[hero] = {}
    if role not in result[hero]:
        result[hero][role] = []

    result[hero][role].append({
        "counter": counter,
        "explain": explain
    })

with open("heroes.json", "w", encoding="utf-8") as f:
    json.dump(result, f, indent=2, ensure_ascii=False)

print("✅ File heroes.json berhasil dibuat!")
