# PDF Prompt Scanner

🚀 **Live Deployment**: [pdf-prompt-scanner.vercel.app](https://pdf-prompt-scanner.vercel.app)  
*(Note: The backend is hosted on Render's free tier. The first scan may take up to 50 seconds to wake up the server).*

PDF Prompt Scanner is a full-stack security-auditing web application developed to detect hidden instructions, visual obfuscations, or prompt injections inside PDF documents that could hijack or jailbreak downstream LLM AI systems.

It combines static heuristic analysis (custom SQLite-backed rules), computer-vision visual obfuscation audits (for invisible white text or tiny text), and deep AI context scanning (powered by the Gemini API) to ensure documents are safe for consumption by AI models.

---

## Key Features

- **Multi-layered Scanner**:
  - **Visual Obfuscation Audit**: Checks for stealthy injection attempts such as text rendered in white-on-white/near-white color or written in extremely small font sizes (`< 3.0pt`).
  - **Static Heuristics Engine**: Performs regex and literal phrase checks against a database of known injection patterns (punctuation-insensitive, zero-width space, and whitespace tolerant).
  - **AI Context Analysis**: Leverages the Gemini API to analyze document semantic intent and detect jailbreak patterns or hidden overrides.
- **Inline PDF Preview Highlighter**: Generates high-resolution PDF page previews, rendering interactive yellow highlights over any flagged phrases, white-on-white text, or tiny text.
- **Scan Option Tooltips**: Hover cards explaining the scan mechanics of each check directly within the upload zone.
- **Preloaded Examples & Bad PDFs**: Built-in visual cards in the main dashboard for loading and scanning standard injection scenarios (Instruction Overrides, Role Hijacking, Data Exfiltration, Context Manipulation, and Visual Obfuscation) along with safe documents for reference.
- **Heuristics Rules Manager**: Edit, create, delete, or toggle heuristic rules (literal or regex) directly from the UI.
- **History Logs**: Retain, search, and audit past scanning details, flags, dates, and AI analysis reports.

---

## Architecture & Technology Stack

- **Frontend**: React, Vite, TypeScript, Vanilla CSS (Light SaaS Design), Lucide icons.
- **Backend**: Spring Boot 3 (Java 21), Apache PDFBox 3.0.3 (text extraction, graphics color parsing, page highlight injection), Tess4J (OCR analysis of embedded images).
- **Database**: SQLite (local persistence of rules and scan history via JPA/Hibernate).

---

## Local Setup & Installation

### 1. Clone the Repository
```bash
git clone https://github.com/hoganngu756/pdf-prompt-scanner.git
cd pdf-prompt-scanner
```

### 2. Backend Setup (Java 21)
Build and run the Maven project:
```bash
cd backend
./mvnw clean install
./mvnw spring-boot:run
```
The backend server runs locally at `http://localhost:8080`.

### 3. Frontend Setup (Node.js & npm)
Install dependencies and start the development server:
```bash
cd frontend
npm install
npm run dev
```
The frontend application will start locally at `http://localhost:5173`.

---

## Deployment

The application is architected to be easily deployed to production cloud platforms.

### Frontend Deployment (Vercel)
The React/Vite frontend is deployed on **Vercel** at [pdf-prompt-scanner.vercel.app](https://pdf-prompt-scanner.vercel.app).
To configure or redeploy:
1. Push your repository to GitHub.
2. Link your repository to a new project in **Vercel**.
3. Set the production environment variable:
   - `VITE_API_BASE_URL`: The URL of your deployed backend service on Render (e.g., `https://pdf-prompt-scanner-backend.onrender.com/api`).
4. Vercel will deploy the application automatically.

### Backend Deployment (Render)
The backend service is containerized via Docker and deployed on **Render.com** (utilizing the multi-stage `Dockerfile` packaging Spring Boot alongside Tesseract OCR).
To redeploy or update:
1. Link your backend repository to Render as a Web Service.
2. Render will automatically detect the `Dockerfile` and build the container.
3. Configure the following environment variables on Render:
   - `GEMINI_API_KEY`: API key for the Gemini AI Scanner.
   - `SPRING_DATASOURCE_URL`: Custom database location if persisting SQLite rules database state across redeploys.