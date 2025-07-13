import os
import json

def create_hero_mapping(dataset_path='dataset'):
    """Membuat mapping hero dengan validasi folder kosong"""
    hero_folders = [
        f for f in os.listdir(dataset_path) 
        if os.path.isdir(os.path.join(dataset_path, f)) 
        and len(os.listdir(os.path.join(dataset_path, f))) > 0  # Skip folder kosong
    ]
    
    hero_folders.sort()
    mapping = {hero: idx for idx, hero in enumerate(hero_folders)}
    
    with open('hero_mapping.json', 'w') as f:
        json.dump(mapping, f, indent=2)
    
    print(f"✅ Mapping dibuat untuk {len(mapping)} hero (folder tidak kosong)")
    return mapping

if __name__ == "__main__":
    create_hero_mapping()