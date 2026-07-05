# PDF Prompt Scanner

PDF Prompt Scanner is a full-stack web application developed to detect hidden instructions or prompt injections inside PDF documents that could jailbreak AI systems. It supports both static heuristic detection (powered by a SQLite-backed custom rule engine) and AI-driven deep content analysis (powered by the Gemini API).

## Features

- **Upload and Scan:** Upload a PDF file to analyze its text context for malicious instructions or prompt injection patterns.
- **Visual Preview:** Generates a dynamic visual page preview of the PDF, highlighting flagged words inline.
- **Custom Heuristics Rules Manager:** Create, update, toggle, or delete detection rules directly from the web interface. Rules support regular expressions (regex) or plain text phrases (which tolerate punctuation, zero-width spaces, and whitespace obfuscation).
- **AI Context Analysis:** Leverages Gemini to perform deep context audits on extracted document text to identify semantic jailbreak vectors.
- **History Log:** View the result logs, details, and timestamps of all past scans.

## Install

### Standard Install
Clone the repository:
```bash
git clone https://github.com/hoganngu756/pdf-prompt-scanner.git
cd pdf-prompt-scanner
```

### Setup Dependencies
The application requires dependencies for both the Java Spring Boot backend and the React Vite frontend.

**Backend (Java/Spring Boot):**
The backend uses Maven wrapper. You only need Java 21+ installed on your machine.
```bash
cd backend
./mvnw clean install
```

**Frontend (React/Vite):**
The frontend requires Node.js and npm.
```bash
cd frontend
npm install
```

## Getting Started

Start the backend and frontend servers in separate terminal windows.

To start the backend server:
```bash
cd backend
./mvnw spring-boot:run
```

To start the frontend server:
```bash
cd frontend
npm run dev
```

## Usage

1. Open your browser and go to `http://localhost:5173`.
2. Select your PDF file in the upload zone.
3. Configure your scanning options (Heuristic Scan, LLM AI Analysis, or both).
4. Click "Analyze Document".
5. Go to the "Rules" tab to manage the active detection words and regex patterns used by the heuristic scanner.
6. Check the "History" tab to review previous scan logs.