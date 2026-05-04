import os
import re

ui_dir = r"c:\Users\jruiz\Desktop\JARVIS-main\android\app\src\main\kotlin\com\visionsoldier\app\ui\screens"

replacements = [
    (r'fontSize\s*=\s*11\.sp', 'fontSize = 10.sp'),
    (r'fontSize\s*=\s*12\.sp', 'fontSize = 11.sp'),
    (r'fontSize\s*=\s*13\.sp', 'fontSize = 12.sp'),
    (r'fontSize\s*=\s*14\.sp', 'fontSize = 13.sp'),
    (r'fontSize\s*=\s*15\.sp', 'fontSize = 14.sp'),
    (r'fontSize\s*=\s*16\.sp', 'fontSize = 15.sp'),
    
    # Titles that might be 20.sp -> 18.sp
    (r'fontSize\s*=\s*20\.sp', 'fontSize = 18.sp'),
]

for filename in os.listdir(ui_dir):
    if filename.endswith(".kt"):
        filepath = os.path.join(ui_dir, filename)
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
            
        original = content
        for pattern, repl in replacements:
            content = re.sub(pattern, repl, content)
            
        if original != content:
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Updated {filename}")
