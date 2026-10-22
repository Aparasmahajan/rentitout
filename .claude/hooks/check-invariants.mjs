#!/usr/bin/env node
// PostToolUse check: after an edit lands, scan the file for breaches of the
// non-negotiables in CLAUDE.md. It does not block (the edit already happened);
// exit 2 feeds the findings back so they get fixed in the next step.
//
// Deliberately narrow patterns. A check that cries wolf gets ignored, so each
// one was run against the codebase as it stood and matches nothing there today.

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';

const input = JSON.parse(readFileSync(0, 'utf8') || '{}');
const file = String(input?.tool_input?.file_path ?? '');
if (!file || !existsSync(file)) process.exit(0);

const rel = file.split(path.sep).join('/');
const ext = path.extname(file);
if (!['.java', '.sql'].includes(ext)) process.exit(0);

const lines = readFileSync(file, 'utf8').split(/\r?\n/);
const findings = [];
const flag = (i, msg) => findings.push(`  ${path.basename(file)}:${i + 1}  ${msg}`);

lines.forEach((line, i) => {
  if (/^\s*(\/\/|\*|--)/.test(line)) return; // comments explain the rules; do not flag them

  if (ext === '.java' && /\b(double|float|Double|Float|BigDecimal)\s+\w*(price|amount|fee|deposit|total|payout)\w*/i.test(line)) {
    flag(i, 'Money must be a long in minor units (paise), never floating point or BigDecimal.');
  }
  if (/ST_Distance\s*\([^)]*\)\s*(<|<=|>|>=)/i.test(line)) {
    flag(i, 'Distance filter via ST_Distance bypasses the GiST index. Use ST_DWithin in the WHERE clause.');
  }
  if (ext === '.java' && /\bkafka\w*\.send\s*\(/i.test(line) && !rel.endsWith('/outbox/OutboxPublisher.java')) {
    flag(i, 'Publish through OutboxService.publish(...) inside the transaction, not straight to Kafka.');
  }
  const isQueryText = /\b(select|where|update|set)\b.*['"]/i.test(line); // JPQL/SQL comparing status, not assigning it
  if (ext === '.java' && !isQueryText && /\.status\s*=(?!=)|\bsetStatus\s*\(/.test(line) && rel.includes('/booking-service/') && !rel.endsWith('/domain/BookingRequest.java')) {
    flag(i, 'BookingRequest status changes only through transitionTo(), driven from RequestService.');
  }
});

if (findings.length) {
  process.stderr.write(`Radius invariants (see CLAUDE.md, "Non-negotiables"):\n${findings.join('\n')}\n`);
  process.exit(2);
}
process.exit(0);
