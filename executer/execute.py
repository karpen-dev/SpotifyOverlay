import subprocess
import sys
import os

def run_app():
    jar_path = os.path.join("bin", "SpotifyOverlay.jar")

    if not os.path.exists(jar_path):
        print(f"Error, file {jar_path} not found")
        sys.exit(1)

    command = [
        "java",
        "--enable-native-access=javafx.graphics",
        "--add-exports=javafx.graphics/com.sun.glass.utils=ALL-UNNAMED",
        "-jar",
        jar_path
    ]

    try:
        subprocess.run(command, check=True)
    except subprocess.CalledProcessError as e:
        print(f"Error starting app {e}")
    except FileNotFoundError:
        print("Error java not installed")


run_app()