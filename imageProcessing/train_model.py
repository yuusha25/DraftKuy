from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv2D, MaxPooling2D, Flatten, Dense
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.optimizers import Adam
import json

# Data generator dengan normalisasi
train_datagen = ImageDataGenerator(rescale=1./255)
train_data = train_datagen.flow_from_directory(
    'dataset',
    target_size=(83, 83),
    batch_size=16,
    class_mode='categorical',
    shuffle=True
)

# Buat model CNN
model = Sequential([
    Conv2D(32, (3,3), activation='relu', input_shape=(83,83,3)),
    MaxPooling2D(2,2),
    Conv2D(64, (3,3), activation='relu'),
    MaxPooling2D(2,2),
    Flatten(),
    Dense(128, activation='relu'),
    Dense(train_data.num_classes, activation='softmax')
])

# Kompilasi model
model.compile(optimizer=Adam(), loss='categorical_crossentropy', metrics=['accuracy'])

# Training model
model.fit(train_data, epochs=10)

# Simpan model
model.save('mlbb_hero_classifier.h5')

# Simpan mapping kelas
with open('hero_mapping.json', 'w') as f:
    json.dump({v: k for k, v in train_data.class_indices.items()}, f)
