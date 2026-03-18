import urllib.request
import zipfile
import os
import ssl

ssl._create_default_https_context = ssl._create_unverified_context

url = 'https://github.com/FreeRTOS/FreeRTOS-Kernel/archive/refs/tags/V11.1.0.zip'
zip_path = 'FreeRTOS_Kernel.zip'

print("Downloading FreeRTOS Kernel...")
urllib.request.urlretrieve(url, zip_path)

print("Extracting...")
with zipfile.ZipFile(zip_path, 'r') as zip_ref:
    zip_ref.extractall('.')

if os.path.exists('FreeRTOS-Kernel-11.1.0'):
    if os.path.exists('FreeRTOS'):
        import shutil
        shutil.rmtree('FreeRTOS')
    os.rename('FreeRTOS-Kernel-11.1.0', 'FreeRTOS')
    os.remove(zip_path)
    print("FreeRTOS Downloaded and Extracted!")
else:
    print("Extraction failed!")
