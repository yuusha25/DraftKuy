import tensorflow as tf
from tensorflow.keras.preprocessing import image
import numpy as np
import json

# Fungsi untuk mempersiapkan gambar (dengan normalisasi)
def prepare(filepath):
    img_array = image.load_img(filepath, target_size=(83, 83))
    img_array = image.img_to_array(img_array)
    img_array = img_array / 255.0  # Normalisasi
    img_array = np.expand_dims(img_array, axis=0)
    return img_array

# Load model
model = tf.keras.models.load_model('mlbb_hero_classifier.h5')

# Load mapping hero
with open('hero_mapping.json') as f:
    hero_mapping = json.load(f)
    hero_mapping = {int(k): v for k, v in hero_mapping.items()}  # Pastikan key jadi int

# Gambar untuk diprediksi
cropped_heroes = ['crop1.png', 'crop2.png', 'crop3.png', 'crop4.png', 'crop5.png','crop6.png', 'crop7.png','crop8.png','crop9.png','crop10.png']

# Prediksi tiap gambar
for hero_image in cropped_heroes:
    img_array = prepare(hero_image)
    prediction = model.predict(img_array)
    predicted_class = np.argmax(prediction)
    hero_name = hero_mapping.get(predicted_class, 'Unknown')
    print(f'Prediksi untuk {hero_image}: {hero_name}')
