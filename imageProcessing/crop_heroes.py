import tensorflow as tf
import os


def crop_hero_images_tf(image_path, output_folder="cropped_heroes_tf"):
    os.makedirs(output_folder, exist_ok=True)

    # Load image
    image_data = tf.io.read_file(image_path)
    image = tf.image.decode_jpeg(image_data, channels=3) if image_path.lower().endswith(".jpg") else tf.image.decode_png(image_data, channels=3)
    
    original_shape = tf.shape(image)
    original_height = original_shape[0].numpy()
    original_width = original_shape[1].numpy()

    # Resize ke width = 2400 dan height menyesuaikan
    target_width = 2400
    scale = target_width / original_width
    target_height = int(original_height * scale)

    image_resized = tf.image.resize(image, [target_height, target_width])
    image_resized = tf.cast(image_resized, tf.uint8)

    print(f"✅ Gambar di-resize ke: {target_width}x{target_height}")

    # Gunakan gambar resize untuk crop
    img_height = target_height
    img_width = target_width

    crop_h = 128
    crop_w = 128
    spacing = int(img_height * 0.16)

    ally_x = int(img_width * 0.075)
    ally_y_start = int(img_height * 0.13)

    enemy_x = int(img_width * 0.885)
    enemy_y_start = ally_y_start

    print(f"📐 Ukuran crop: {crop_w}x{crop_h}, spacing: {spacing}")

    # Crop dan simpan hero kiri (ally)
    for i in range(5):
        y = ally_y_start + i * spacing
        if y + crop_h > img_height or ally_x + crop_w > img_width:
            print(f"[❌] Crop melebihi batas: ally_{i+1}")
            continue

        cropped = tf.image.crop_to_bounding_box(image_resized, y, ally_x, crop_h, crop_w)
        resized = tf.image.resize(cropped, [83, 83])
        resized_uint8 = tf.cast(resized, tf.uint8)
        tf.io.write_file(os.path.join(output_folder, f"crop{i+1}.png"), tf.image.encode_png(resized_uint8))

    # Crop dan simpan hero kanan (enemy)
    for i in range(5):
        y = enemy_y_start + i * spacing
        if y + crop_h > img_height or enemy_x + crop_w > img_width:
            print(f"[❌] Crop melebihi batas: enemy_{i+6}")
            continue

        cropped = tf.image.crop_to_bounding_box(image_resized, y, enemy_x, crop_h, crop_w)
        resized = tf.image.resize(cropped, [83, 83])
        resized_uint8 = tf.cast(resized, tf.uint8)
        tf.io.write_file(os.path.join(output_folder, f"crop{i+6}.png"), tf.image.encode_png(resized_uint8))

    print("✅ Selesai crop dan resize (83x83) semua hero.")


# Contoh pemanggilan
crop_hero_images_tf("screenshots2.jpg")
