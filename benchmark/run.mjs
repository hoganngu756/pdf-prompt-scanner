/**
 * Scans the corpus and reports detection / false-positive rates.
 *
 * A document counts as DETECTED when any enabled layer flags it. A benign
 * document that any layer flags counts as a FALSE POSITIVE. That is the strict
 * reading: the user sees one verdict, so a flag from any layer is a flag.
 *
 * Usage:
 *   node run.mjs            deterministic layers only (visual, structure, heuristics)
 *   node run.mjs --llm      also enable the Gemini layer (slower, costs tokens)
 */
import { readdirSync, readFileSync } from 'fs';

const BASE = process.env.SCAN_URL ?? 'http://localhost:8080/api';
const USE_LLM = process.argv.includes('--llm');
const CONCURRENCY = 4;

async function scan(path) {
  const form = new FormData();
  form.append('file', new Blob([readFileSync(path)], { type: 'application/pdf' }), path.split('/').pop());
  form.append('useLLM', String(USE_LLM));
  form.append('useHeuristics', 'true');

  const res = await fetch(`${BASE}/scan`, { method: 'POST', body: form });
  if (!res.ok) throw new Error(`HTTP ${res.status} for ${path}`);
  return res.json();
}

/** Which layers flagged this document. */
function flaggedBy(result) {
  const layers = [];
  if (result.visualObfuscationResult && !result.visualObfuscationResult.safe) layers.push('visual');
  if (result.documentStructureResult && !result.documentStructureResult.safe) layers.push('structure');
  if (result.heuristicResult && !result.heuristicResult.safe) layers.push('heuristic');
  if (result.llmResult && !result.llmResult.safe) layers.push('llm');
  return layers;
}

async function runAll(dir, files) {
  const out = [];
  for (let i = 0; i < files.length; i += CONCURRENCY) {
    const batch = files.slice(i, i + CONCURRENCY);
    const results = await Promise.all(
      batch.map(async (f) => {
        try {
          return { file: f, layers: flaggedBy(await scan(`${dir}/${f}`)) };
        } catch (e) {
          return { file: f, layers: [], error: String(e.message) };
        }
      }),
    );
    out.push(...results);
    process.stdout.write(`\r  scanned ${out.length}/${files.length}`);
  }
  process.stdout.write('\r');
  return out;
}

const pct = (n, d) => (d === 0 ? 0 : (100 * n) / d);

async function main() {
  const malFiles = readdirSync('corpus/malicious').filter((f) => f.endsWith('.pdf'));
  const bngFiles = readdirSync('corpus/benign').filter((f) => f.endsWith('.pdf'));

  console.log(`\nCorpus: ${malFiles.length} malicious, ${bngFiles.length} benign`);
  console.log(`Layers: visual + structure + heuristics${USE_LLM ? ' + Gemini' : ' (no LLM)'}\n`);

  const mal = await runAll('corpus/malicious', malFiles);
  const bng = await runAll('corpus/benign', bngFiles);

  const errors = [...mal, ...bng].filter((r) => r.error);
  if (errors.length) {
    console.log(`  ${errors.length} request errors, e.g. ${errors[0].error}`);
  }

  const detected = mal.filter((r) => r.layers.length > 0);
  const missed = mal.filter((r) => r.layers.length === 0 && !r.error);
  const falsePos = bng.filter((r) => r.layers.length > 0);

  console.log('='.repeat(64));
  console.log(`  Detection rate    ${pct(detected.length, mal.length).toFixed(1)}%  (${detected.length}/${mal.length})`);
  console.log(`  False positives   ${pct(falsePos.length, bng.length).toFixed(1)}%  (${falsePos.length}/${bng.length})`);
  console.log('='.repeat(64));

  // Detection by delivery mechanism — shows which vectors are weak
  const byMech = {};
  for (const r of mal) {
    const mech = r.file.replace(/mal_\d+_/, '').replace('.pdf', '');
    byMech[mech] ??= { total: 0, hit: 0 };
    byMech[mech].total++;
    if (r.layers.length) byMech[mech].hit++;
  }
  console.log('\n  By delivery mechanism:');
  for (const [mech, s] of Object.entries(byMech).sort()) {
    console.log(`    ${mech.padEnd(18)} ${String(s.hit).padStart(2)}/${s.total}  ${pct(s.hit, s.total).toFixed(0)}%`);
  }

  // Which layer caught what
  const byLayer = {};
  for (const r of detected) for (const l of r.layers) byLayer[l] = (byLayer[l] ?? 0) + 1;
  console.log('\n  Caught by layer (documents may be caught by several):');
  for (const [layer, n] of Object.entries(byLayer).sort((a, b) => b[1] - a[1])) {
    console.log(`    ${layer.padEnd(18)} ${String(n).padStart(2)}`);
  }

  if (missed.length) {
    console.log(`\n  Missed (${missed.length}):`);
    for (const m of missed.slice(0, 12)) console.log(`    ${m.file}`);
  }
  if (falsePos.length) {
    console.log(`\n  False positives (${falsePos.length}):`);
    for (const f of falsePos.slice(0, 12)) console.log(`    ${f.file}  [${f.layers.join(', ')}]`);
  }
  console.log();
}

main().catch((e) => { console.error(e); process.exit(1); });
