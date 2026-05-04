import os
import re

ui_dir = r"c:\Users\jruiz\Desktop\JARVIS-main\android\app\src\main\kotlin\com\visionsoldier\app\ui\screens"

# Regexes to remove font family and letter spacing
replacements = [
    (r',\s*fontFamily\s*=\s*FontFamily\.Monospace', ''),
    (r'fontFamily\s*=\s*FontFamily\.Monospace\s*,', ''),
    (r'fontFamily\s*=\s*FontFamily\.Monospace', ''),
    (r',\s*letterSpacing\s*=\s*[\d.]+\.sp', ''),
    (r'letterSpacing\s*=\s*[\d.]+\.sp\s*,', ''),
    (r'letterSpacing\s*=\s*[\d.]+\.sp', ''),
    
    # Increase font sizes gently
    (r'fontSize\s*=\s*8\.sp', 'fontSize = 11.sp'),
    (r'fontSize\s*=\s*9\.sp', 'fontSize = 12.sp'),
    (r'fontSize\s*=\s*10\.sp', 'fontSize = 13.sp'),
    (r'fontSize\s*=\s*11\.sp', 'fontSize = 14.sp'),
    (r'fontSize\s*=\s*12\.sp', 'fontSize = 15.sp'),
    (r'fontSize\s*=\s*13\.sp', 'fontSize = 15.sp'),
    (r'fontSize\s*=\s*14\.sp', 'fontSize = 16.sp'),
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
