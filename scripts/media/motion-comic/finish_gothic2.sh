set -e
cd /d/media-tools/motion-comic/gothic2
PY=/d/media-tools/Wan2GP/venv/Scripts/python.exe
$PY - <<'PYEOF'
import sys
sys.path.insert(0, r"D:\media-tools\image")
from manga_compose import detect_faces
from PIL import Image, ImageDraw, ImageFilter
img = Image.open("f1.png").convert("RGB"); W, H = img.size
faces = detect_faces(img)
x0, y0, x1, y1 = max(faces, key=lambda f: (f[2]-f[0])*(f[3]-f[1])) if faces else (W*0.4, H*0.25, W*0.6, H*0.6)
fw, fh = x1-x0, y1-y0
m = Image.new("L", img.size, 0)
ImageDraw.Draw(m).ellipse([x0-fw*0.25, y0+fh*0.4, x1+fw*0.25, y1+fh*0.6], fill=255)
m.filter(ImageFilter.GaussianBlur(18)).save("f2-mask.png")
print("face", faces[:1])
PYEOF
$PY ../mc_image.py f2.json 2>&1 | grep -E "RESULT|Error"
$PY -c "
from PIL import Image
for k in ['f1','f2']: Image.open(k+'.png').convert('RGB').resize((832,480),Image.LANCZOS).save(k+'-v.png')"
$PY ../clips.py . fella-clips.json
$PY ../mc_voice.py voices.json
$PY ../mc_compose.py . plan.json
cp "モーションコミック_ゴスロリ_AI版.mp4" /c/Users/sd190901/Desktop/
echo FINISHED
