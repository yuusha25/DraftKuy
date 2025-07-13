import tensorflow as tf
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras import layers, models
from tensorflow.keras.preprocessing.image import ImageDataGenerator
import os
import shutil
import json

# 1. Siapkan Dataset
def prepare_dataset():
    """Pisahkan 1 gambar per hero untuk validasi"""
    os.makedirs('val_dataset', exist_ok=True)
    for hero in os.listdir('dataset'):
        hero_dir = os.path.join('dataset', hero)
        if os.path.isdir(hero_dir) and len(os.listdir(hero_dir)) >= 2:
            os.makedirs(f'val_dataset/{hero}', exist_ok=True)
            shutil.move(f'dataset/{hero}/2.png', f'val_dataset/{hero}/val.png')

# 2. Buat Hero Mapping
def create_mapping():
    heroes = [d for d in os.listdir('dataset') 
             if os.path.isdir(os.path.join('dataset', d))]
    hero_mapping = {hero: idx for idx, hero in enumerate(heroes)}
    with open('hero_mapping.json', 'w') as f:
        json.dump(hero_mapping, f)
    return hero_mapping

# 3. Model Definition
def build_model(num_classes):
    base_model = MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights='imagenet'
    )
    base_model.trainable = False

    model = models.Sequential([
        base_model,
        layers.GlobalAveragePooling2D(),
        layers.Dropout(0.5),
        layers.Dense(num_classes, activation='softmax')
    ])
    
    model.compile(
        optimizer='adam',
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )
    return model

# 4. Training
def train():
    prepare_dataset()
    hero_mapping = create_mapping()
    num_classes = len(hero_mapping)

    # Data Generator
    train_datagen = ImageDataGenerator(
        rescale=1./255,
        rotation_range=15,
        zoom_range=0.2,
        horizontal_flip=True
    )

    val_datagen = ImageDataGenerator(rescale=1./255)

    train_generator = train_datagen.flow_from_directory(
        'dataset',
        target_size=(224, 224),
        batch_size=8,
        class_mode='categorical'
    )

    val_generator = val_datagen.flow_from_directory(
        'val_dataset',
        target_size=(224, 224),
        batch_size=8,
        class_mode='categorical'
    )

    # Build and Train
    model = build_model(num_classes)
    
    history = model.fit(
        train_generator,
        epochs=20,
        validation_data=val_generator,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(patience=3),
            tf.keras.callbacks.ModelCheckpoint('best_model.keras', save_best_only=True)
        ]
    )
    
    model.save('mlbb_classifier.keras')
    print("✅ Training selesai! Model disimpan sebagai mlbb_classifier.keras")

if __name__ == "__main__":
    train()