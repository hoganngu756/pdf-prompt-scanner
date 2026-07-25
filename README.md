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
| **Heuristics Engine** | Literal and regex rules from a user-editable database. Literal rules tolerate whitespace, punctuation and zero-width-space obfuscation (`b.y.p.a.s.s`) and are anchored at word starts | Optional |
| **AI Context Analysis** | Semantic intent — novel jailbreaks and subtle overrides that no static rule covers (Gemini) | Optional |

Text recovered from metadata, annotations, bookmarks, form fields and OCR of embedded images is all folded into what the heuristic and AI layers analyse.

## Other Features

- **Page preview highlighter** — renders the flagged pages with yellow highlights over the offending text, labelled with the real source page number.
- **Rules Manager** — create, edit, toggle and delete heuristic rules from the UI. Invalid regex is rejected rather than silently ignored.
- **Example PDFs** — nine one-click samples covering instruction override, role hijacking, data exfiltration, context manipulation, tiny text, white text, invisible render mode, metadata/annotation injection, and a clean document for comparison.
- **Scan history** — past scans with their flags and AI analysis (requires the admin key).

---

## Tech Stack

- **Frontend**: React, Vite, TypeScript, vanilla CSS design tokens, Lucide icons
- **Backend**: Spring Boot 3 (Java 21), Apache PDFBox 3.0.3, Tess4J (OCR)
- **Database**: SQLite via JPA/Hibernate

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
export ADMIN_API_KEY="pick-any-value"   # required for the Rules and History tabs
export GEMINI_API_KEY="your-key"        # optional; needed only for the AI layer
export TRUSTED_PROXY_COUNT=0            # no proxy in front when running locally
./mvnw clean install
./mvnw spring-boot:run
```
Runs at `http://localhost:8080`. Scanning works without either key — untick **AI Analysis** if you have no Gemini key.

### 3. Frontend
```bash
cd frontend
npm install
npm run dev
```
Runs at `http://localhost:5173`. Paste your `ADMIN_API_KEY` into the field on the **Rules** tab to manage rules and view history.

To regenerate the sample PDFs: `node scripts/generate-samples.mjs`

---

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `ADMIN_API_KEY` | *(none)* | Gates rule writes and history reads. **Unset means those endpoints are disabled**, not open. |
| `GEMINI_API_KEY` | *(none)* | Enables the AI Context Analysis layer. |
| `TRUSTED_PROXY_COUNT` | `1` | Proxy layers in front of the app. Render adds one; use `0` when running the server directly, or `X-Forwarded-For` becomes spoofable. |
| `ALLOWED_ORIGINS` | localhost + Vercel URL | Comma-separated CORS origins. |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlite:scanner-history.db` | Point at a persistent volume to survive redeploys. |

**Limits**: 10 MB per upload, 50 pages per document, 10 scans/min per IP (120/min for other endpoints), OCR on the first 5 pages, previews capped at ~4 MP per page.

---

## Deployment

**Frontend (Vercel)** — link the repo and set `VITE_API_BASE_URL` to your backend URL, e.g. `https://pdf-scanner-api.onrender.com/api`.

**Backend (Render)** — the multi-stage `Dockerfile` packages Spring Boot with Tesseract OCR; Render detects it automatically. Set `ADMIN_API_KEY` and `GEMINI_API_KEY` in the service environment.

> **Note on persistence**: Render's free tier has no persistent disk, so the SQLite file is lost on redeploy and rules reset to the ten defaults. Attach a disk or point `SPRING_DATASOURCE_URL` at a hosted database to retain rules and history.

---

## Testing

```bash
cd backend && ./mvnw test     # 20 tests
cd frontend && npm run build
```

Tests run against a throwaway database under `target/`, so they never touch `scanner-history.db`.
