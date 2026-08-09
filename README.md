# PDF Prompt Scanner

**Live Deployment**: [pdf-prompt-scanner.vercel.app](https://pdf-prompt-scanner.vercel.app)
*(The backend is on Render's free tier — the first scan may take up to 50 seconds while the server wakes up.)*

A full-stack security auditing tool that detects hidden instructions and prompt injections inside PDF documents before they are fed to an LLM. It targets payloads that are invisible to a human reader but fully extractable by an AI system.

---

## Detection Layers

Three checks run on the backend, plus an optional AI pass:

| Layer | What it catches | Always on |
|---|---|---|
| **Visual Obfuscation Audit** | Invisible text rendering mode (`Tr 3`), fully transparent fill, white-on-white text, and fonts under 3pt | Yes |
| **Document Structure** | Injections in metadata (Title/Subject/Keywords), annotations, bookmarks and form field values, plus active content: `/OpenAction`, embedded JavaScript, embedded files, `/Launch` and URI actions | Yes |
| **Heuristics Engine** | Literal and regex rules from `heuristic-rules.yml`. Literal rules tolerate whitespace, punctuation, zero-width padding (`b.y.p.a.s.s`) and lookalike characters (Cyrillic `і` for Latin `i`), and are anchored at word starts | Optional |
| **AI Context Analysis** | Semantic intent — novel jailbreaks and subtle overrides that no static rule covers (Gemini) | Optional |

Text recovered from metadata, annotations, bookmarks, form fields and OCR of embedded images is all folded into what the heuristic and AI layers analyse.

## Other Features

- **Page preview highlighter** — renders the flagged pages with yellow highlights over the offending text, labelled with the real source page number.
- **Detection rules** — the active rule set is listed in the UI, read-only. Rules live in version control (`backend/src/main/resources/heuristic-rules.yml`), so changing what the scanner looks for is a reviewable diff.
- **Example PDFs** — ten one-click samples covering instruction override, role hijacking, data exfiltration, context manipulation, tiny text, white text, invisible render mode, metadata/annotation injection, lookalike characters, and a clean document for comparison.
- **Scan history** — kept in your own browser. The server stores nothing: no database, no record of any document anyone scanned.

---

## Tech Stack

- **Frontend**: React, Vite, TypeScript, vanilla CSS design tokens, Lucide icons
- **Backend**: Spring Boot 3 (Java 21), Apache PDFBox 3.0.3, Tess4J (OCR)
- **Storage**: none — the service is stateless; rules are configuration and history lives in the browser

---

## Local Setup

### 1. Clone
```bash
git clone https://github.com/hoganngu756/pdf-prompt-scanner.git
cd pdf-prompt-scanner
```

### 2. Backend (Java 21)
```bash
cd backend
export GEMINI_API_KEY="your-key"     # optional; needed only for the AI layer
export TRUSTED_PROXY_COUNT=0         # no proxy in front when running locally
./mvnw clean install
./mvnw spring-boot:run
```
Runs at `http://localhost:8080`. There is no database to set up and no credential to configure. Scanning works without a Gemini key — untick **AI context analysis** if you don't have one.

### 3. Frontend
```bash
cd frontend
npm install
npm run dev
```
Runs at `http://localhost:5173`.

To regenerate the sample PDFs: `node scripts/generate-samples.mjs`

---

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | *(none)* | Enables the AI Context Analysis layer. |
| `TRUSTED_PROXY_COUNT` | `1` | Proxy layers in front of the app. Render adds one; use `0` when running the server directly, or `X-Forwarded-For` becomes spoofable. |
| `ALLOWED_ORIGINS` | localhost + Vercel URL | Comma-separated CORS origins. |

**Limits**: 10 MB per upload, 50 pages per document, 10 scans/min per IP (120/min for other endpoints), OCR on the first 5 pages, previews capped at ~4 MP per page.

---

## Deployment

**Frontend (Vercel)** — link the repo and set `VITE_API_BASE_URL` to your backend URL, e.g. `https://pdf-scanner-api.onrender.com/api`.

**Backend (Render)** — the multi-stage `Dockerfile` packages Spring Boot with Tesseract OCR; Render detects it automatically. Set `GEMINI_API_KEY` in the service environment.

The service is stateless, so Render's lack of a persistent disk no longer matters: there is nothing to lose on redeploy.

---

## Testing

```bash
cd backend  && ./mvnw test    # 31 tests
cd frontend && npm test       # 21 tests
cd frontend && npm run build
```
