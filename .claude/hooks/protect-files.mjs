#!/usr/bin/env node
// PreToolUse guard for Edit/Write.
//
// - Secrets: .env files are never written by an agent (.env.example is fine).
// - Flyway: a committed V*__*.sql has a checksum recorded in every database it
//   ran against. Editing it fails startup with a checksum mismatch, so a change
//   becomes the next version instead. R__ (repeatable) migrations may change.
// - Generated/vendored: lock files and the design-system bundle are produced
//   by tools, not edited by hand.

import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const input = JSON.parse(readFileSync(0, 'utf8') || '{}');
const file = String(input?.tool_input?.file_path ?? '');
if (!file) process.exit(0);

const root = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
const rel = path.relative(root, path.resolve(root, file)).split(path.sep).join('/');
const base = path.basename(rel);

function decide(decision, reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: decision, permissionDecisionReason: reason },
  }));
  process.exit(0);
}

function isTracked(p) {
  try {
    execFileSync('git', ['ls-files', '--error-unmatch', p], { cwd: root, stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

if (/^\.env(\..+)?$/.test(base) && base !== '.env.example') {
  decide('deny', `${rel} holds local secrets. Edit .env.example and tell the user what to set.`);
}

const migration = rel.match(/\/db\/migration\/(V(\d+)__.+\.sql)$/);
if (migration && isTracked(rel)) {
  decide('deny', `${migration[1]} is a committed Flyway migration; editing it breaks the checksum in every database it already ran on. Add V${Number(migration[2]) + 1}__<what>.sql (or the next free number) in the same folder instead.`);
}

if (base === 'package-lock.json') {
  decide('ask', 'Lock files should change through npm install, not by hand.');
}

if (rel.startsWith('design/_ds/')) {
  decide('ask', 'design/_ds is the exported design-system bundle; it is reference material, not source.');
}

process.exit(0);
