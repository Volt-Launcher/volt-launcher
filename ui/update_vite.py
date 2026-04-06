import os
import re
vite_conf = "vite.config.ts"
with open(vite_conf, "r") as f:
    text = f.read()
if "tailwindcss" not in text:
    text = re.sub(
        r"import vue from '@vitejs/plugin-vue'",
        "import vue from '@vitejs/plugin-vue'\nimport tailwindcss from '@tailwindcss/vite'",
        text
    )
    text = re.sub(
        r"plugins: \[",
        "plugins: [\n    tailwindcss(),",
        text
    )
    with open(vite_conf, "w") as f:
        f.write(text)
css_file = "src/assets/base.css"
with open(css_file, "r") as f:
    text = f.read()
if "@import \"tailwindcss\";" not in text:
    text = f"@import \"tailwindcss\";\n" + text
    with open(css_file, "w") as f:
        f.write(text)
