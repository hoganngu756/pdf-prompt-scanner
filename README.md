# PDF Prompt Scanner

PDF Prompt Scanner is a full-stack web application. It's developed to detect hidden instructions or prompt injections inside PDF documents that could jailbreak AI systems.

## Install

### Standard install
The easiest way to get started is to clone the repository and run the frontend and backend separately. 

First, grab it from GitHub:
```bash
git clone https://github.com/hoganngu756/pdf-prompt-scanner.git
cd pdf-prompt-scanner
```

### Setup dependencies
PDF Prompt Scanner has its own dependencies for both the Java backend and the React frontend. 

**Backend (Java/Spring Boot):**
The backend uses Maven, which is included via the wrapper. No manual installation is needed besides having Java 21+.
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

## Getting started

The general workflow requires running both the frontend and backend servers.

To start the backend server, open a terminal and run:
```bash
cd backend
./mvnw spring-boot:run
```

To start the frontend server, open a separate terminal and run:
```bash
cd frontend
npm run dev
```

The application needs a PDF document to scan. By default, it will upload the file and allow you to test it against different vulnerability detectors (heuristics or LLM-based). 

To use the tool:
1. Open your browser and go to `http://localhost:5173`.
2. Click the upload box and select your PDF file.
3. Choose your scan types (Heuristic Scan or LLM Deep Scan).
4. Click "Scan PDF".

The backend comes with two built-in test files (`backend/safe.pdf` and `backend/injected.pdf`) that you can use to verify that the scanner correctly identifies prompt injections.