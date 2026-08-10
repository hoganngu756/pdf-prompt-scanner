import { readFileSync } from 'fs';
const form = new FormData();
form.append('file', new Blob([readFileSync('corpus/benign/bng_008.pdf')], { type: 'application/pdf' }), 'bng_008.pdf');
form.append('useLLM', 'false'); form.append('useHeuristics', 'true');
const r = await (await fetch('http://localhost:8080/api/scan', { method: 'POST', body: form })).json();
console.log('full flag:', JSON.stringify(r.heuristicResult.flags, null, 2));
