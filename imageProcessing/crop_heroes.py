import tensorflow as tf
import os
import sys

# ======================== CONFIGURATION ========================
TARGET_WIDTH = 2400          # Base width for resizing
CROP_HEIGHT = 137           # Initial crop height
CROP_WIDTH = 145            # Initial crop width
ALLY_X_RATIO = 0.0642       # Ally horizontal position
ENEMY_X_RATIO = 0.8746      # Enemy horizontal position
START_Y_RATIO = 0.115       # First hero vertical position
SPACING_RATIO = 0.162       # Vertical spacing between heroes

# Customizable final size (change these values as needed)
FINAL_HEIGHT = 141         # Set your desired final height
FINAL_WIDTH = 133           # Set your desired final width
# ===============================================================

def crop_hero_images(image_path, output_folder="cropped_heroes"):
    """
    Crop hero icons with customizable final output size
    """
    
    # Input validation
    if not os.path.exists(image_path):
        print(f"❌ Error: File not found - {image_path}")
        return

    os.makedirs(output_folder, exist_ok=True)
    print(f"📁 Output folder: {os.path.abspath(output_folder)}")
    print(f"🎯 Target size: {FINAL_WIDTH}x{FINAL_HEIGHT}px")

    try:
        # Load and decode image
        image_data = tf.io.read_file(image_path)
        if image_path.lower().endswith(".jpg"):
            image = tf.image.decode_jpeg(image_data, channels=3)
        elif image_path.lower().endswith(".png"):
            image = tf.image.decode_png(image_data, channels=3)
        else:
            print("❌ Only JPG/PNG supported")
            return
    except Exception as e:
        print(f"❌ Image loading failed: {str(e)}")
        return

    # Resize base image
    original_height, original_width = image.shape[:2]
    scale = TARGET_WIDTH / original_width
    target_height = int(original_height * scale)
    image_resized = tf.image.resize(image, [target_height, TARGET_WIDTH])
    image_resized = tf.cast(image_resized, tf.uint8)
    print(f"🖼 Base resized to: {TARGET_WIDTH}x{target_height}px")

    # Calculate positions
    spacing = int(target_height * SPACING_RATIO)
    ally_x = int(TARGET_WIDTH * ALLY_X_RATIO)
    ally_y_start = int(target_height * START_Y_RATIO)
    enemy_x = int(TARGET_WIDTH * ENEMY_X_RATIO)

    def save_cropped(image, x, y, filename):
        try:
            # 1. Initial crop
            cropped = tf.image.crop_to_bounding_box(image, y, x, CROP_HEIGHT, CROP_WIDTH)
            
            # 2. Custom final resize with padding
            resized = tf.image.resize_with_pad(
                cropped,
                FINAL_HEIGHT,  # Custom height
                FINAL_WIDTH    # Custom width
            )
            
            # 3. Save with verification
            output_path = os.path.join(output_folder, filename)
            tf.io.write_file(output_path, tf.image.encode_png(tf.cast(resized, tf.uint8)))
            
            # Verify dimensions
            img_verif = tf.io.decode_png(tf.io.read_file(output_path))
            h, w, _ = img_verif.shape
            if w != FINAL_WIDTH or h != FINAL_HEIGHT:
                raise ValueError(f"Size mismatch: {w}x{h} (expected {FINAL_WIDTH}x{FINAL_HEIGHT})")
            
            print(f"✅ Saved {filename} ({w}x{h}px)")
            return True
            
        except Exception as e:
            print(f"❌ Failed {filename}: {str(e)}")
            return False

    # Process allies
    print("\n⚔ Processing allies:")
    for i in range(5):
        y = ally_y_start + i * spacing
        if not save_cropped(image_resized, ally_x, y, f"ally_{i+1}.png"):
            if y + CROP_HEIGHT > target_height:
                print("⚠ Crop exceeds image bounds")

    # Process enemies
    print("\n💀 Processing enemies:")
    for i in range(5):
        y = ally_y_start + i * spacing
        if not save_cropped(image_resized, enemy_x, y, f"enemy_{i+1}.png"):
            if y + CROP_HEIGHT > target_height:
                print("⚠ Crop exceeds image bounds")

    print("\n✨ All done! ✨")
    print(f"📐 Final size for all images: {FINAL_WIDTH}x{FINAL_HEIGHT}px")

if __name__ == "__main__":
    input_file = sys.argv[1] if len(sys.argv) > 1 else "screenshots3.jpg"
    print(f"🚀 Processing: {input_file}")
    crop_hero_images(input_file)