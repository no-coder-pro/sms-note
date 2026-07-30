import os
import subprocess
import sys

def check_env():
    print("--- Android Build Check ---")
    gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
    
    if os.path.exists(gradle_cmd):
        print(f"Found Gradle Wrapper: {gradle_cmd}")
        return gradle_cmd
    else:
        print("Gradle wrapper not found in root directory.")
        print("To build APK from Android Studio:")
        print("1. Open this folder (c:\\Users\\abc\\Downloads\\hwi_851_6030) in Android Studio.")
        print("2. Click 'Build' -> 'Build Bundle(s) / APK(s)' -> 'Build APK(s)'.")
        print("3. The release APK will be generated under app/build/outputs/apk/release/app-release.apk.")
        return None

if __name__ == "__main__":
    check_env()
