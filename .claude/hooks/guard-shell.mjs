#!/usr/bin/env node
// PreToolUse guard for Bash and PowerShell.
//
// Hard-denies what is never right in this repo, and turns what is sometimes
// right but hard to undo into an explicit question. Written in Node rather than
// bash because the team runs Windows, and hooks must work from either shell.

import { readFileSync } from 'node:fs';

const input = JSON.parse(readFileSync(0, 'utf8') || '{}');
const cmd = String(input?.tool_input?.command ?? '');

const deny = [
  [/git\s+push\b[^\n]*\s(--force(?!-with-lease)|-f)\b/, 'Force-push is off. Use --force-with-lease on your own branch, and only when the user asks.'],
  [/git\s+push\b[^\n]*\b(origin\s+)?(main|master)\b(?!-)/, 'Do not push to main directly. Push the branch and open a PR.'],
  [/--no-verify\b/, 'Skipping git hooks is off. Fix whatever the hook is reporting instead.'],
];

// The POMs need Maven 3.6+ and JDK 21. Team machines get Java from Docker and
// may carry an old Maven or a Java 8 JDK on the host, so bare mvn is blocked
// wherever either is what the host would actually use.
const oldMaven = /apache-maven-(2\.|3\.[0-5]\.)/i.test(process.env.PATH ?? '');
const jdkMajor = (process.env.JAVA_HOME ?? '').match(/(?:jdk|jre)[-_]?(?:1\.)?(\d+)/i); // jdk-8.0.x, jdk1.8.0_x, jdk-21.0.x
const oldJdk = jdkMajor !== null && Number(jdkMajor[1]) < 21;
if (oldMaven || oldJdk) {
  deny.push([/(^|[\s;&|"'])mvn(\.cmd)?\s/, `Host ${oldJdk ? 'JDK' : 'Maven'} is too old for these POMs (need JDK 21). Use .\\scripts\\mvn-docker.ps1 [goals] - host Maven 3.9 inside a JDK 21 container.`]);
}

const ask = [
  [/git\s+reset\s+--hard/, 'git reset --hard discards uncommitted work.'],
  [/git\s+clean\s+-[a-z]*f/, 'git clean deletes untracked files.'],
  [/git\s+(checkout|restore)\s+(--\s+)?\.(\s|$)/, 'This discards every unstaged change.'],
  [/devstack\.ps1\s+reset/, 'devstack reset wipes every local database, topic and bucket.'],
  [/docker\s+compose\b[^\n]*\bdown\b[^\n]*(\s-v\b|--volumes)/, 'compose down -v deletes the Postgres, Redis and MinIO volumes - all local data.'],
  [/docker\s+(volume\s+(rm|prune)|system\s+prune)/, 'This deletes Docker volumes or images, which may hold the local data.'],
  [/radius-dev/i, 'This touches the dev stack directory (%LOCALAPPDATA%\\radius-dev).'],
  [/\brm\s+-[a-z]*r[a-z]*f|\brm\s+-[a-z]*f[a-z]*r|Remove-Item\b[^\n]*-Recurse/i, 'Recursive delete.'],
  [/DROP\s+(TABLE|DATABASE|SCHEMA)|TRUNCATE\s/i, 'Destructive SQL.'],
];

function decide(decision, reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: decision, permissionDecisionReason: reason },
  }));
  process.exit(0);
}

for (const [re, why] of deny) if (re.test(cmd)) decide('deny', why);
for (const [re, why] of ask) if (re.test(cmd)) decide('ask', why);
process.exit(0);
