import tensorflow as tf
import numpy as np
import os
import glob
import json
from termcolor import colored

def load_model_and_mapping():
    """Load model and hero mapping with enhanced error handling"""
    try:
        # Verify model file exists
        if not os.path.exists('best_model.keras'):
            raise FileNotFoundError("Model file 'best_model.keras' not found")
            
        # Verify mapping file exists
        if not os.path.exists('hero_mapping.json'):
            raise FileNotFoundError("Mapping file 'hero_mapping.json' not found")
            
        model = tf.keras.models.load_model('best_model.keras')
        
        with open('hero_mapping.json') as f:
            hero_mapping = json.load(f)
            
        id_to_hero = {v: k for k, v in hero_mapping.items()}
        return model, id_to_hero
        
    except Exception as e:
        print(colored(f"❌ Critical Error: {str(e)}", 'red'))
        print("\nPlease verify:")
        print("1. You've run train_model.py successfully")
        print("2. Both best_model.keras and hero_mapping.json exist")
        print("3. The files are not corrupted")
        exit()

def predict_image(model, img_path, id_to_hero):
    """Predict single image with comprehensive error handling"""
    if not os.path.exists(img_path):
        return None, 0.0, f"Image file not found: {img_path}"
    
    try:
        # Load and preprocess image
        img = tf.keras.utils.load_img(img_path, target_size=(224, 224))
        img_array = tf.keras.utils.img_to_array(img)
        img_array = np.expand_dims(img_array, axis=0) / 255.0
        
        # Get predictions
        preds = model.predict(img_array, verbose=0)[0]
        top_idx = np.argmax(preds)
        confidence = float(preds[top_idx])
        hero_name = id_to_hero.get(top_idx, "Unknown")
        
        return hero_name, confidence, None
        
    except Exception as e:
        return None, 0.0, f"Processing error: {str(e)}"

def get_confidence_color(confidence):
    """Return colored output based on confidence level"""
    if confidence >= 0.7:  # High confidence
        return 'green'
    elif confidence >= 0.5:  # Medium confidence
        return 'yellow'
    else:  # Low confidence
        return 'red'

def predict_all_heroes():
    """Predict all heroes with detailed reporting"""
    try:
        model, id_to_hero = load_model_and_mapping()
        
        print(colored("\n🎯 MLBB Hero Prediction Report", 'blue', attrs=['bold']))
        print(colored("="*50, 'blue'))
        print(colored(f"{'Image':<15} {'Prediction':<20} {'Confidence':<15} {'Status'}", 'cyan'))
        print(colored("-"*50, 'blue'))
        
        for img_path in sorted(glob.glob("cropped_heroes/*.png")):
            hero, confidence, error = predict_image(model, img_path, id_to_hero)
            filename = os.path.basename(img_path)
            
            if error:
                status = colored("FAILED", 'red')
                print(f"{filename:<15} {'-':<20} {'-':<15} {status} ({error})")
            else:
                conf_percent = f"{confidence*100:.1f}%"
                conf_color = get_confidence_color(confidence)
                
                if confidence >= 0.7:
                    status = colored("HIGH CONFIDENCE", 'green')
                elif confidence >= 0.5:
                    status = colored("MEDIUM CONFIDENCE", 'yellow')
                else:
                    status = colored("LOW CONFIDENCE", 'red')
                    hero = f"{hero} (?)"
                
                print(f"{filename:<15} {hero:<20} {colored(conf_percent, conf_color):<15} {status}")
        
        print(colored("\n🔍 Interpretation Guide:", 'magenta'))
        print(colored("- HIGH CONFIDENCE (≥70%): Reliable prediction", 'green'))
        print(colored("- MEDIUM CONFIDENCE (50-69%): Needs verification", 'yellow'))
        print(colored("- LOW CONFIDENCE (<50%): Unreliable", 'red'))
        
        print(colored("\n💡 Improvement Tips:", 'magenta'))
        print("- For LOW confidence: Check image cropping with crop_heroes.py")
        print("- For MEDIUM confidence: Add more training images of the hero")
        print("- For consistent failures: Retrain model with train_model.py")
        
    except Exception as e:
        print(colored(f"\n💀 Critical system error: {str(e)}", 'red'))
        print("Please check your setup and try again")

if __name__ == "__main__":
    predict_all_heroes()