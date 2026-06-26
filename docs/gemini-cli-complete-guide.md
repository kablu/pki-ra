# Gemini CLI — Complete Guide

**Version:** 0.49.0
**Date:** 2026-06-26

---

## 1. Gemini CLI Kya Hai?

```
+------------------------------------------------------------------+
|                        GEMINI CLI                                 |
|                                                                   |
|  Google ka official AI-powered command-line tool jo developers    |
|  ko terminal se directly Gemini AI models access karne deta hai. |
|                                                                   |
|  Think of it as: ChatGPT/Claude Code jaisa — but Google ka,      |
|  terminal mein, aur aapke codebase ke saath deeply integrated.   |
|                                                                   |
|  Open Source: https://github.com/google-gemini/gemini-cli         |
|  Package:    npm install -g @google/gemini-cli                    |
|  License:    Apache 2.0                                           |
+------------------------------------------------------------------+
```

### Kya Kar Sakta Hai?

```
  Developer                Terminal                   Google Cloud
  ┌──────┐    command     ┌──────────┐   API call    ┌──────────┐
  │ You  │ ──────────────→│ Gemini   │──────────────→│ Gemini   │
  │      │                │ CLI      │               │ Models   │
  │      │←──────────────-│          │←──────────────│ 2.5 Pro  │
  └──────┘    response    └──────────┘   response    │ 2.5 Flash│
                               │                     └──────────┘
                               │
                     ┌─────────┴─────────┐
                     │ Aapka Codebase    │
                     │ Files read/write  │
                     │ Git integration   │
                     │ Terminal commands │
                     └───────────────────┘
```

---

## 2. Installation

### Step 1: Node.js Install Karo (agar nahi hai)
```bash
# Check karo
node --version    # v18+ chahiye
npm --version

# Nahi hai to download karo: https://nodejs.org
```

### Step 2: Gemini CLI Install Karo
```bash
npm install -g @google/gemini-cli
```

### Step 3: Verify
```bash
gemini --version    # 0.49.0
where gemini        # path dikhayega
```

### Step 4: API Key Setup
```
Option A: Google AI Studio (FREE)
  1. https://aistudio.google.com/apikey
  2. "Create API Key" click karo
  3. Key copy karo
  4. ~/.gemini/settings.json mein daalo:
     {"apiKey":"YOUR_KEY_HERE"}

Option B: Environment Variable
  export GEMINI_API_KEY="YOUR_KEY_HERE"          # Linux/Mac
  $env:GEMINI_API_KEY = "YOUR_KEY_HERE"          # PowerShell

Option C: Google Cloud (Vertex AI — enterprise)
  export GOOGLE_GENAI_USE_VERTEXAI=true
  export GOOGLE_CLOUD_PROJECT=your-project-id
```

---

## 3. All Commands — Complete Reference

### 3.1 Main Command
```
gemini [options] [query..]
```

### 3.2 Command Flags

```
FLAG                          KYA KARTA HAI                           EXAMPLE
─────────────────────────────────────────────────────────────────────────────────────
-p, --prompt                  Non-interactive (headless) mode         gemini -p "explain this code"
-i, --prompt-interactive      Prompt run karo, phir interactive       gemini -i "read all files"
-m, --model                   Model select karo                      gemini -m gemini-2.5-flash
-d, --debug                   Debug mode (F12 se console)             gemini -d
-s, --sandbox                 Sandbox mode (safe execution)           gemini -s
-y, --yolo                    Auto-approve ALL actions (dangerous!)   gemini -y
-w, --worktree                New git worktree mein kaam karo         gemini -w feature-branch
-r, --resume                  Previous session resume karo            gemini -r latest
-o, --output-format           Output format: text/json/stream-json   gemini -o json -p "hello"
-e, --extensions              Specific extensions use karo            gemini -e my-ext
-v, --version                 Version dikha                           gemini -v
-h, --help                    Help dikha                              gemini -h
--skip-trust                  Trust dialog skip karo                  gemini --skip-trust
--approval-mode               Approval level set karo                 gemini --approval-mode yolo
--policy                      Policy files load karo                  gemini --policy rules.yaml
--list-sessions               Past sessions dikha                     gemini --list-sessions
--session-file                Session file se load karo               gemini --session-file s.json
--include-directories         Extra directories include karo          gemini --include-directories ../lib
--screen-reader               Accessibility mode                      gemini --screen-reader
--raw-output                  ANSI escape sequences allow karo        gemini --raw-output
```

### 3.3 Approval Modes

```
MODE          KYA HOTA HAI                                    RISK
────────────────────────────────────────────────────────────────────
default       Har action ke liye permission maangta hai        LOW
auto_edit     File edits auto-approve, baaki permission        MEDIUM
yolo          Sab kuch auto-approve (no questions asked!)      HIGH
plan          Read-only — koi change nahi karega               ZERO
```

---

## 4. Models

```
MODEL                  SPEED     CAPABILITY    CONTEXT     BEST FOR
───────────────────────────────────────────────────────────────────────────
gemini-2.5-pro         Slow      Highest       1M tokens   Complex coding, architecture
gemini-2.5-flash       Fast      High          1M tokens   Daily development, reviews
gemini-2.0-flash       Fastest   Good          1M tokens   Simple tasks, quick answers
gemma (local)          Offline   Basic         8K tokens   Privacy-sensitive, no internet
```

### Model Select Karna:
```bash
gemini -m gemini-2.5-pro      # best quality
gemini -m gemini-2.5-flash    # best speed/quality balance
```

---

## 5. Usage Modes — 4 Tarike Se Use Karo

### Mode 1: Interactive (Default)
```bash
gemini
# Terminal mein chat window khulega
# Type karo, Gemini jawab dega
# Exit: Ctrl+C ya /quit
```

### Mode 2: One-Shot (Headless)
```bash
gemini -p "explain what this project does"
# Ek jawab dega aur exit ho jayega
# CI/CD aur scripts mein useful
```

### Mode 3: Pipe Mode (POWERFUL)
```bash
# File ka content pipe karo
cat main.c | gemini -p "find all bugs"

# Git diff pipe karo
git diff | gemini -p "review this change"

# Logs analyze karo
kubectl logs pod-xyz | gemini -p "what errors happened?"

# Multiple files
cat *.h | gemini -p "generate documentation"

# Command output analyze karo
npm audit | gemini -p "which vulnerabilities are critical?"
```

### Mode 4: Resume Previous Session
```bash
gemini --list-sessions          # past sessions dekho
gemini -r latest                # last session resume karo
gemini -r 3                     # session #3 resume karo
```

---

## 6. Built-in Tools — Gemini Ke Paas Kya Kya Hai

```
Gemini CLI ke andar ye tools built-in aate hain jo wo
khud use karta hai jab aap koi task do:

TOOL              KYA KARTA HAI
──────────────────────────────────────────────────────────
ReadFile          Files read karta hai
WriteFile         Files create/overwrite karta hai
EditFile          Existing files mein changes karta hai
ListDirectory     Folder contents list karta hai
SearchFiles       File names search karta hai (glob)
GrepTool          File content search karta hai (regex)
ExecuteCommand    Terminal commands run karta hai
WebFetch          URLs se content fetch karta hai

Ye tools AUTOMATICALLY use hote hain — aapko manually
call nahi karna. Bas bolo "read main.c" aur wo ReadFile
tool use kar lega.
```

---

## 7. MCP Servers — External Tools Connect Karo

### MCP Kya Hai?
```
MCP = Model Context Protocol

Gemini CLI ko external services se connect karta hai:
  - Database se query karo
  - Slack mein message bhejo
  - Jira ticket create karo
  - GitHub PRs manage karo
  - Custom APIs call karo

┌──────────┐     MCP Protocol     ┌──────────────┐
│ Gemini   │ ←─────────────────→  │ MCP Server   │
│ CLI      │                      │ (Database)   │
│          │ ←─────────────────→  │ MCP Server   │
│          │                      │ (Slack)      │
│          │ ←─────────────────→  │ MCP Server   │
│          │                      │ (GitHub)     │
└──────────┘                      └──────────────┘
```

### MCP Commands
```bash
# MCP server add karo
gemini mcp add my-db npx @mcp/sqlite --db ./data.db

# List all servers
gemini mcp list

# Remove
gemini mcp remove my-db

# Enable/Disable
gemini mcp enable my-db
gemini mcp disable my-db
```

### MCP Server Examples
```bash
# GitHub integration
gemini mcp add github npx @modelcontextprotocol/server-github

# Filesystem (extra directories)
gemini mcp add fs npx @modelcontextprotocol/server-filesystem /path/to/data

# SQLite database
gemini mcp add sqlite npx @modelcontextprotocol/server-sqlite --db ./app.db

# Custom REST API
gemini mcp add myapi node ./my-mcp-server.js
```

---

## 8. Extensions — Gemini Ko Extend Karo

### Extension Kya Hai?
```
Extension = Gemini CLI ka plugin system

Claude Code mein "skills" hain, Gemini mein "extensions" hain.
Extensions se aap:
  - New tools add kar sakte ho
  - Custom commands bana sakte ho
  - Workflows automate kar sakte ho
```

### Extension Commands
```bash
# Install from GitHub
gemini extensions install https://github.com/user/my-extension

# Install from local path
gemini extensions install /path/to/extension

# List installed
gemini extensions list

# Update
gemini extensions update my-ext        # specific
gemini extensions update --all         # sab update

# Create new extension (boilerplate)
gemini extensions new ./my-new-ext

# Validate
gemini extensions validate ./my-ext

# Enable/Disable
gemini extensions enable my-ext
gemini extensions disable my-ext

# Uninstall
gemini extensions uninstall my-ext

# Link (live development — changes reflect immediately)
gemini extensions link ./my-ext-dev

# Configure
gemini extensions config my-ext setting-name value
```

### Extension Banana — Step by Step
```bash
# Step 1: Boilerplate generate karo
gemini extensions new ./pki-reviewer

# Step 2: Structure
pki-reviewer/
├── gemini-extension.json      ← metadata (name, version, tools)
├── src/
│   └── index.ts               ← tool implementations
├── package.json
└── README.md

# Step 3: gemini-extension.json
{
  "name": "pki-reviewer",
  "version": "1.0.0",
  "description": "Review C code for PKI/crypto vulnerabilities",
  "tools": [
    {
      "name": "review_c_crypto",
      "description": "Analyze C code for cryptographic issues",
      "parameters": {
        "type": "object",
        "properties": {
          "file_path": { "type": "string" }
        }
      }
    }
  ]
}

# Step 4: Install and test
gemini extensions install ./pki-reviewer
gemini "review crypto in main.c"    # extension auto-triggers
```

---

## 9. Skills — Agent Behaviors Define Karo

### Skill Kya Hai?
```
Skill = Predefined behavior/instruction set

Extension tools provide karta hai (functions),
Skill instructions provide karta hai (HOW to do something).

Example:
  Extension: "read file" tool deta hai
  Skill:     "jab C code review karo, in 10 points check karo" batata hai
```

### Skill Commands
```bash
# List all skills
gemini skills list
gemini skills list --all       # disabled bhi dikha

# Install from GitHub
gemini skills install https://github.com/user/my-skill

# Install from local
gemini skills install /path/to/skill

# Link (live dev)
gemini skills link ./my-skill-dev

# Enable/Disable
gemini skills enable my-skill
gemini skills disable my-skill

# Uninstall
gemini skills uninstall my-skill
```

### Skill Banana — Step by Step
```
Step 1: Folder banao
  mkdir my-skill/

Step 2: SKILL.md likho (YAML frontmatter + instructions)

  ---
  name: pki-c-reviewer
  description: >
    Review C code for PKI, cryptography, and security issues.
    Trigger when user says "review crypto code" or "check C security"
  ---

  # PKI C Code Reviewer

  When asked to review C code, follow these steps:

  1. Collect all .c and .h files
  2. Check for these 10 vulnerability categories:
     - Buffer overflow
     - Use-after-free
     - ... (detailed instructions)
  3. Report findings with severity levels
  4. Suggest fixes

Step 3: Install karo
  gemini skills install ./my-skill/

Step 4: Test karo
  gemini "review my C code for crypto issues"
  → Skill automatically trigger hoga
```

### Skill vs Extension — Kab Kya Use Karo?

```
+------------------+-----------------------------+-----------------------------+
|                  | SKILL                       | EXTENSION                   |
+------------------+-----------------------------+-----------------------------+
| Kya hai          | Instructions (Markdown)     | Code (TypeScript/JS)        |
| Kya deta hai     | Behavior/workflow           | New tools/functions         |
| Banana kitna     | Easy (sirf .md file)        | Medium (code likhna padta)  |
| mushkil          |                             |                             |
| Example          | "Review code this way"      | "Connect to Jira API"       |
| Kab use karo     | Workflow define karna       | New capability add karna    |
| File structure   | SKILL.md (single file OK)   | package.json + source code  |
+------------------+-----------------------------+-----------------------------+
```

---

## 10. Gemma — Local/Offline AI Model

### Gemma Kya Hai?
```
Gemma = Google ka open-source LLM jo LOCAL machine pe chalta hai

Benefits:
  ✅ No internet required
  ✅ Data Google servers pe nahi jaata
  ✅ Free — no API costs
  ✅ Private/classified code ke liye safe

Limitations:
  ❌ Small model — complex tasks mein weak
  ❌ 8K context window (vs 1M for cloud models)
  ❌ GPU recommended for speed
```

### Gemma Commands
```bash
# Setup (download model)
gemini gemma setup

# Start local server
gemini gemma start

# Check status
gemini gemma status

# View logs
gemini gemma logs

# Stop
gemini gemma stop

# Use Gemma
gemini -m gemma       # local model use hoga
```

---

## 11. Hooks — Automated Actions

```
Hooks = Commands jo automatically run hote hain events pe

Example: har file edit ke baad auto-format
Example: har command ke baad security check

Claude Code se migrate karo:
  gemini hooks migrate
```

---

## 12. Policy Engine — Rules Define Karo

```
Policy = YAML files jo define karti hain ki Gemini kya kar
sakta hai aur kya nahi kar sakta.

Example policy.yaml:
  rules:
    - name: "no-delete"
      description: "Never delete production files"
      deny:
        - tool: ExecuteCommand
          pattern: "rm -rf /prod/*"

    - name: "read-only-config"
      description: "Config files are read-only"
      deny:
        - tool: WriteFile
          pattern: "*.config"
          
Usage:
  gemini --policy ./policy.yaml
  gemini --admin-policy ./admin-rules.yaml    # override nahi ho sakti
```

---

## 13. Sessions — Conversation History

```bash
# List past sessions
gemini --list-sessions

# Resume latest
gemini -r latest

# Resume specific session
gemini -r 3

# Load from file
gemini --session-file ./saved-session.json

# Delete session
gemini --delete-session 5
```

---

## 14. Development Mein Fayda

### 14.1 Code Review
```bash
# Changed files review karo
git diff | gemini -p "review for bugs and security issues"

# PR review
git diff main...feature | gemini -p "thorough code review"

# Specific file
cat crypto.c | gemini -p "find all security vulnerabilities"
```

### 14.2 Bug Finding
```bash
# Error logs analyze karo
cat error.log | gemini -p "root cause analysis"

# Stack trace explain karo
cat crash.txt | gemini -p "explain this crash and suggest fix"

# Test failures
npm test 2>&1 | gemini -p "why are these tests failing?"
```

### 14.3 Code Generation
```bash
# Test generate karo
cat UserService.java | gemini -p "write unit tests with JUnit 5"

# Documentation generate karo
cat api.py | gemini -p "write OpenAPI documentation"

# Boilerplate
gemini -p "create a Spring Boot REST controller for User CRUD"
```

### 14.4 Refactoring
```bash
gemini "refactor this class to use Strategy pattern"
gemini "convert callbacks to async/await"
gemini "split this 500-line function into smaller methods"
```

### 14.5 CI/CD Integration
```bash
# Pre-commit hook
git diff --cached | gemini -p "any security issues?" -o json

# PR description generate karo
git log main..HEAD --oneline | gemini -p "write PR description"

# Release notes
git log v1.0..v2.0 --oneline | gemini -p "write release notes"
```

### 14.6 Learning & Exploration
```bash
# Codebase samjho
gemini "explain the architecture of this project"

# Specific concept
gemini "how does the authentication flow work in this codebase?"

# Compare approaches
gemini "should I use Redis or Memcached for this use case?"
```

---

## 15. PKI/Crypto Development Mein Specific Fayda

```
PKI developer ke liye Gemini CLI ye kaam kar sakta hai:

┌─────────────────────────────────────────────────────────┐
│ 1. CERTIFICATE CODE REVIEW                              │
│    cat cert_handler.c | gemini -p "check X.509 issues" │
│                                                         │
│ 2. OPENSSL API VALIDATION                               │
│    gemini "is my EVP_EncryptInit usage correct?"        │
│                                                         │
│ 3. ASN.1 PARSER AUDIT                                   │
│    cat asn1_parse.c | gemini -p "DER encoding issues?" │
│                                                         │
│ 4. MEMORY SAFETY CHECK                                  │
│    cat *.c | gemini -p "buffer overflow risks?"         │
│                                                         │
│ 5. CRYPTO ALGORITHM REVIEW                              │
│    gemini "is AES-256-CBC with PKCS7 correct here?"    │
│                                                         │
│ 6. KEY MANAGEMENT AUDIT                                 │
│    cat keystore.c | gemini -p "key material leaks?"    │
│                                                         │
│ 7. PROTOCOL IMPLEMENTATION CHECK                        │
│    cat tls_handshake.c | gemini -p "TLS 1.3 correct?" │
│                                                         │
│ 8. COMPLIANCE VERIFICATION                              │
│    gemini "does this code meet FIPS 140-3?"            │
└─────────────────────────────────────────────────────────┘
```

---

## 16. Gemini CLI vs Claude Code vs Copilot CLI

```
+──────────────────+───────────────+───────────────+──────────────+
│ Feature          │ Gemini CLI    │ Claude Code   │ Copilot CLI  │
+──────────────────+───────────────+───────────────+──────────────+
│ Developer        │ Google        │ Anthropic     │ GitHub/MSFT  │
│ Open Source      │ Yes           │ Yes           │ No           │
│ Free Tier        │ Yes           │ Yes (limited) │ No           │
│ Context Window   │ 1M tokens     │ 200K tokens   │ Unknown      │
│ Models           │ 3+ cloud      │ 4+ cloud      │ GPT-4o       │
│                  │ + Gemma local │               │              │
│ File Edit        │ Yes           │ Yes           │ Limited      │
│ Pipe Support     │ Yes           │ Yes           │ No           │
│ MCP Protocol     │ Yes           │ Yes           │ No           │
│ Extensions       │ Yes           │ Skills        │ No           │
│ Local Model      │ Yes (Gemma)   │ No            │ No           │
│ Git Worktree     │ Yes (-w flag) │ Yes           │ No           │
│ Policy Engine    │ Yes (YAML)    │ Yes           │ No           │
│ Session Resume   │ Yes           │ Yes           │ No           │
│ Sandbox Mode     │ Yes           │ Yes           │ No           │
│ YOLO Mode        │ Yes           │ Yes           │ No           │
│ Output JSON      │ Yes           │ No            │ No           │
│ Thinking/Reason  │ Yes (2.5 pro) │ Yes (Opus)    │ Limited      │
│ Best For         │ Google stack  │ General dev   │ GitHub users │
+──────────────────+───────────────+───────────────+──────────────+
```

---

## 17. Tips & Best Practices

```
TIP 1: Approval Mode Use Karo
  Development:  gemini --approval-mode auto_edit
  Dangerous:    gemini --approval-mode yolo     (CAREFUL!)
  Read-only:    gemini --approval-mode plan

TIP 2: Model Selection
  Complex task → gemini-2.5-pro
  Quick task   → gemini-2.5-flash
  Offline      → gemma

TIP 3: Pipe Mode Master Karo
  Ye sabse powerful feature hai.
  Koi bhi output Gemini ko pipe kar sakte ho.

TIP 4: Sessions Save Karo
  Long debugging sessions mein --resume use karo
  Context lose nahi hoga

TIP 5: Policy Files Banao
  Team ke liye standard policies define karo
  "Kya allowed hai, kya nahi" — documented

TIP 6: Extensions Explore Karo
  Community extensions check karo
  Custom tools banao apne workflow ke liye

TIP 7: Worktree Mode
  Experimental changes ke liye -w use karo
  Main branch safe rahega
```

---

## 18. Troubleshooting

```
ERROR                              FIX
──────────────────────────────────────────────────────────────────
"not a trusted directory"          gemini --skip-trust
                                   OR set GEMINI_CLI_TRUST_WORKSPACE=true

"API key not set"                  Set GEMINI_API_KEY env var
                                   OR add to ~/.gemini/settings.json

"503 Service Unavailable"          Server busy — retry after few minutes
                                   OR switch model: -m gemini-2.5-flash

"rate limit exceeded"              Free tier limit — wait or upgrade
                                   OR use gemma (local, no limits)

"JSON parse error in settings"     Check for BOM in settings.json
                                   Rewrite without BOM encoding

"Ripgrep not available"            Install ripgrep: scoop install ripgrep
                                   Falls back to GrepTool (slower)
```

---

## 19. Configuration Files

```
~/.gemini/
├── settings.json              ← API key, default model, preferences
├── GEMINI.md                  ← Global instructions (like CLAUDE.md)
└── extensions/                ← Installed extensions

Project root:
├── .gemini/
│   ├── GEMINI.md              ← Project-specific instructions
│   ├── settings.json          ← Project settings
│   └── policies/              ← Policy YAML files
└── ...

settings.json example:
{
  "apiKey": "your-key-here",
  "model": "gemini-2.5-flash",
  "theme": "dark",
  "sandbox": false
}
```

---

## 20. Quick Reference Card

```
╔══════════════════════════════════════════════════════════════╗
║                 GEMINI CLI — QUICK REFERENCE                ║
╠══════════════════════════════════════════════════════════════╣
║                                                              ║
║  BASICS:                                                     ║
║    gemini                          interactive mode           ║
║    gemini -p "prompt"              one-shot headless          ║
║    gemini -m gemini-2.5-pro        select model               ║
║    gemini -y                       yolo (auto-approve all)    ║
║    gemini -r latest                resume last session        ║
║                                                              ║
║  PIPE:                                                       ║
║    cat file | gemini -p "review"   pipe file to gemini        ║
║    git diff | gemini -p "review"   pipe diff                  ║
║    cmd 2>&1 | gemini -p "explain"  pipe any output            ║
║                                                              ║
║  MCP:                                                        ║
║    gemini mcp add NAME CMD ARGS    add MCP server             ║
║    gemini mcp list                 list servers               ║
║    gemini mcp remove NAME          remove server              ║
║                                                              ║
║  EXTENSIONS:                                                 ║
║    gemini extensions new PATH      create extension           ║
║    gemini extensions install SRC   install extension          ║
║    gemini extensions list          list installed             ║
║                                                              ║
║  SKILLS:                                                     ║
║    gemini skills install SRC       install skill              ║
║    gemini skills list              list skills                ║
║    gemini skills link PATH         link for dev               ║
║                                                              ║
║  GEMMA (LOCAL):                                              ║
║    gemini gemma setup              download local model       ║
║    gemini gemma start              start local server         ║
║    gemini -m gemma                 use local model            ║
║                                                              ║
║  OUTPUT:                                                     ║
║    gemini -o json -p "hello"       JSON output                ║
║    gemini -o stream-json -p "hi"   streaming JSON             ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```
