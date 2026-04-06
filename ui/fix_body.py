with open('src/assets/base.css', 'r') as f:
    text = f.read()
text = text.replace('.body { flex: 1;  overflow: hidden; position: relative; z-index: 10; }', '.body { flex: 1; display: flex; flex-direction: column; overflow: hidden; position: relative; z-index: 10; }')
with open('src/assets/base.css', 'w') as f:
    f.write(text)
