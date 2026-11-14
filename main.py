import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from google import genai

app = FastAPI(title="ClipMaster AI Backend")

API_KEY = os.getenv("GEMINI_API_KEY")

class AnalyzeRequest(BaseModel):
transcript: str
duration: int = 30
count: int = 5

@app.get("/")
def home():
return {"status": "online", "app": "ClipMaster AI"}

@app.post("/analyze")
def analyze_video(data: AnalyzeRequest):
if not API_KEY:
raise HTTPException(
status_code=500,
detail="Gemini API key belum dikonfigurasi"
)

if not data.transcript.strip():
    raise HTTPException(
        status_code=400,
        detail="Transkrip video masih kosong"
    )

if len(data.transcript) > 30000:
    raise HTTPException(
        status_code=400,
        detail="Transkrip terlalu panjang"
    )

client = genai.Client(api_key=API_KEY)

prompt = f"""

Kamu adalah AI profesional untuk mencari momen terbaik
dari sebuah transkrip video.

Analisis transkrip berikut dan cari maksimal {data.count}
kandidat klip dengan durasi sekitar {data.duration} detik.

Prioritaskan:

- Momen menarik
- Cerita yang kuat
- Informasi mengejutkan
- Humor
- Kalimat pembuka yang menarik

Berikan hasil dalam JSON valid dengan format:

{{
"clips": [
{{
"title": "Judul klip",
"start_text": "Kalimat awal",
"end_text": "Kalimat akhir",
"reason": "Alasan rekomendasi",
"score": 90
}}
]
}}

Jangan mengarang timestamp karena transkrip belum tentu
memiliki penanda waktu. Gunakan kutipan awal dan akhir
agar posisi klip dapat dicocokkan pada tahap berikutnya.

TRANSKRIP:
{data.transcript}
"""

try:
    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=prompt
    )

    result = response.text.strip()

    if result.startswith("```"):
        result = result.split("\n", 1)[1]
        result = result.rsplit("```", 1)[0].strip()

    import json
    parsed = json.loads(result)

    return parsed

except Exception as e:
    raise HTTPException(
        status_code=502,
        detail=f"Analisis AI gagal: {str(e)}"
    )
