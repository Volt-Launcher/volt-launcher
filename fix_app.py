with open("ui/src/assets/base.css", "r") as f:
    text = f.read()
# remove display: flex; flex-direction: column; from body
text = text.replace("display: flex; flex-direction: column;", "")
# add to #app
text += "\n#app { display: flex; flex-direction: column; width: 100%; height: 100%; overflow: hidden; position: relative; z-index: 10; }\n"
with open("ui/src/assets/base.css", "w") as f:
    f.write(text)
