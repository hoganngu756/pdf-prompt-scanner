import { readdirSync, readFileSync } from 'fs';
const BASE = 'http://localhost:8080/api';
const counts = {};
for (const f of readdirSync(`${process.env.CORPUS ?? 'corpus'}/benign`).filter(x => x.endsWith('.pdf'))) {
  const form = new FormData();
  form.append('file', new Blob([readFileSync(`${process.env.CORPUS ?? 'corpus'}/benign/${f}`)], { type: 'application/pdf' }), f);
  form.append('useLLM', 'false'); form.append('useHeuristics', 'true');
  const r = await (await fetch(`${BASE}/scan`, { method: 'POST', body: form })).json();
  for (const flag of r.heuristicResult?.flags ?? []) {
    // The matched rule is a structured field now, not prose to parse back out.
    if (flag.quote) counts[flag.quote] = (counts[flag.quote] ?? 0) + 1;
  }
}
console.log('\nRules firing on BENIGN documents (out of 75):');
for (const [rule, n] of Object.entries(counts).sort((a,b) => b[1]-a[1]))
  console.log(`  ${String(n).padStart(3)}  "${rule}"`);
