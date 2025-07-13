import tensorflow as tf
import os
import sys

# ======================== PARAMETER YANG BISA DIUBAH ========================
TARGET_WIDTH = 2400        # Lebar gambar setelah resize
CROP_HEIGHT = 137          # Tinggi area crop awal
CROP_WIDTH = 145           # Lebar area crop awal
ALLY_X_RATIO = 0.0642      # Posisi horizontal ally
ENEMY_X_RATIO = 0.8746     # Posisi horizontal enemy
START_Y_RATIO = 0.115       # Posisi vertikal hero pertama
SPACING_RATIO = 0.162       # Jarak vertikal antar hero
FINAL_SIZE = 142           # UKURAN FINAL YANG DIINGINKAN (142x142)
# ============================================================================

def crop_hero_images(image_path, output_folder="cropped_heroes"):
    """
    Fungsi untuk crop icon hero dari screenshot draft pick MLBB
    Output akan di-resize ke 142x142 pixel
    """
    
    # 1. Validasi file input
    if not os.path.exists(image_path):
        print(f"❌ ERROR: File tidak ditemukan: {image_path}")
        print(f"   Pastikan file ada di direktori: {os.getcwd()}")
        print(f"   File di direktori ini: {os.listdir(os.path.dirname(image_path) or '.')}")
        return
    
    # 2. Buat folder output jika belum ada
    os.makedirs(output_folder, exist_ok=True)
    print(f"📂 Output akan disimpan di: {os.path.abspath(output_folder)}")
    print(f"🔍 Ukuran final: {FINAL_SIZE}x{FINAL_SIZE} pixel")
    
    try:
        # 3. Baca file gambar
        image_data = tf.io.read_file(image_path)
        
        # 4. Dekode gambar berdasarkan tipe file
        if image_path.lower().endswith(".jpg"):
            image = tf.image.decode_jpeg(image_data, channels=3)
        elif image_path.lower().endswith(".png"):
            image = tf.image.decode_png(image_data, channels=3)
        else:
            print(f"❌ Format file tidak didukung: {image_path}")
            print("   Gunakan file JPG atau PNG")
            return
    except Exception as e:
        print(f"❌ Gagal memproses gambar: {str(e)}")
        return

    # 5. Dapatkan dimensi asli gambar
    original_shape = tf.shape(image)
    original_height = original_shape[0].numpy()
    original_width = original_shape[1].numpy()
    print(f"📐 Dimensi asli: {original_width}x{original_height}px")

    # 6. Resize gambar (pertahankan aspect ratio)
    scale = TARGET_WIDTH / original_width
    target_height = int(original_height * scale)
    
    # Lakukan resize
    image_resized = tf.image.resize(image, [target_height, TARGET_WIDTH])
    image_resized = tf.cast(image_resized, tf.uint8)
    print(f"🖼️ Di-resize ke: {TARGET_WIDTH}x{target_height}px")

    # 7. Hitung parameter crop
    spacing = int(target_height * SPACING_RATIO)
    ally_x = int(TARGET_WIDTH * ALLY_X_RATIO)
    ally_y_start = int(target_height * START_Y_RATIO)
    enemy_x = int(TARGET_WIDTH * ENEMY_X_RATIO)
    enemy_y_start = ally_y_start

    print(f"⚙️ Parameter crop:")
    print(f"  - Ukuran crop awal: {CROP_WIDTH}x{CROP_HEIGHT}px")
    print(f"  - Ukuran final: {FINAL_SIZE}x{FINAL_SIZE}px")
    print(f"  - Spacing: {spacing}px")
    print(f"  - Posisi ally: X={ally_x}, Y-start={ally_y_start}")
    print(f"  - Posisi enemy: X={enemy_x}, Y-start={enemy_y_start}")

    def save_cropped_image(image, x, y, filename):
        """Crop dan resize ke 142x142 dengan padding"""
        try:
            # Crop area
            cropped = tf.image.crop_to_bounding_box(image, y, x, CROP_HEIGHT, CROP_WIDTH)
            
            # Resize ke 142x142 DENGAN PADDING (PENTING!)
            resized = tf.image.resize_with_pad(
                cropped,
                FINAL_SIZE,  # height
                FINAL_SIZE   # width
            )
            
            # Konversi dan simpan
            resized_uint8 = tf.cast(resized, tf.uint8)
            output_path = os.path.join(output_folder, filename)
            tf.io.write_file(output_path, tf.image.encode_png(resized_uint8))
            
            # Verifikasi ukuran
            img_verif = tf.io.decode_png(tf.io.read_file(output_path))
            h, w, _ = img_verif.shape
            if w != FINAL_SIZE or h != FINAL_SIZE:
                raise ValueError(f"Ukuran salah: {w}x{h}px (harusnya {FINAL_SIZE}x{FINAL_SIZE})")
                
            print(f"✅ Berhasil disimpan: {filename} ({w}x{h}px)")
            return True
        except Exception as e:
            print(f"❌ Gagal menyimpan {filename}: {str(e)}")
            return False

    # 8. Proses crop untuk 5 hero ally (kiri)
    print("\nMemproses hero ALLY (kiri):")
    for i in range(5):
        y = ally_y_start + i * spacing
        filename = f"ally_{i+1}.png"
        
        if y + CROP_HEIGHT > target_height:
            print(f"⚠️ Peringatan: Crop untuk {filename} mungkin melewati batas bawah gambar")
        
        save_cropped_image(image_resized, ally_x, y, filename)

    # 9. Proses crop untuk 5 hero enemy (kanan)
    print("\nMemproses hero ENEMY (kanan):")
    for i in range(5):
        y = enemy_y_start + i * spacing
        filename = f"enemy_{i+1}.png"
        
        if y + CROP_HEIGHT > target_height:
            print(f"⚠️ Peringatan: Crop untuk {filename} mungkin melewati batas bawah gambar")
        
        save_cropped_image(image_resized, enemy_x, y, filename)

    print("\n✨ Proses selesai! ✨")
    print("=====================================================")
    print("HASIL AKAN BERUKURAN 142x142 PIXEL")
    print("Jika crop tidak akurat, sesuaikan parameter:")
    print(f"ALLY_X_RATIO = {ALLY_X_RATIO}  # Geser kiri/kanan ally")
    print(f"ENEMY_X_RATIO = {ENEMY_X_RATIO} # Geser kiri/kanan enemy")
    print(f"START_Y_RATIO = {START_Y_RATIO} # Geser atas/bawah")
    print("=====================================================")

if __name__ == "__main__":
    input_file = "screenshots3.jpg"#Default
    
    if len(sys.argv) > 1:
        input_file = sys.argv[1]
    
    print(f"🚀 Memulai pemrosesan untuk file: {input_file}")
    print(f"📂 Direktori kerja: {os.getcwd()}")
    
    crop_hero_images(input_file)