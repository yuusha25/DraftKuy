import cv2
import numpy as np
import os

def zoom_in(image, scale=1.2):
    h, w = image.shape[:2]
    new_h, new_w = int(h / scale), int(w / scale)
    y1 = (h - new_h) // 2
    x1 = (w - new_w) // 2
    cropped = image[y1:y1+new_h, x1:x1+new_w]
    zoomed_in = cv2.resize(cropped, (w, h), interpolation=cv2.INTER_LINEAR)
    return zoomed_in

def zoom_out(image, scale=0.8):
    h, w = image.shape[:2]
    new_h, new_w = int(h * scale), int(w * scale)
    resized = cv2.resize(image, (new_w, new_h), interpolation=cv2.INTER_LINEAR)
    canvas = np.zeros_like(image)
    y1 = (h - new_h) // 2
    x1 = (w - new_w) // 2
    canvas[y1:y1+new_h, x1:x1+new_w] = resized
    return canvas

if __name__ == "__main__":
    # Buka gambar menggunakan OpenCV
    img_path = "dataset/aamon/1.png"  # ganti dengan path gambar mu
    image = cv2.imread(img_path)
    
    # Membuat folder output jika belum ada
    # os.makedirs("output_variasi_zoom", exist_ok=True)

    # Membuat 10 variasi zoom in dengan scale meningkat bertahap
    for i, scale in enumerate(np.linspace(1.1, 2.0, 10), start=1):
        zoomed_in_img = zoom_in(image, scale=scale)
        output_path = f"/zoom_in_{i:02d}_scale_{scale:.2f}.jpg"
        cv2.imwrite(output_path, zoomed_in_img)
        print(f"Saved: {output_path}")

    # Membuat 10 variasi zoom out dengan scale menurun bertahap
    for i, scale in enumerate(np.linspace(0.9, 0.5, 10), start=1):
        zoomed_out_img = zoom_out(image, scale=scale)
        output_path = f"output_variasi_zoom/zoom_out_{i:02d}_scale_{scale:.2f}.jpg"
        cv2.imwrite(output_path, zoomed_out_img)
        print(f"Saved: {output_path}")
