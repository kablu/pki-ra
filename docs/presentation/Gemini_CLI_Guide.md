# Gemini CLI — Complete Guide

**Google's AI-Powered Command Line Tool**
*Version 1.0 | June 2026 | RipplesBit Documentation*

---

## Table of Contents

1. [Overview](#1-overview)
2. [Installation](#2-installation)
3. [Configuration](#3-configuration)
4. [Basic Usage](#4-basic-usage)
5. [Slash Commands](#5-slash-commands)
6. [Tools & Capabilities](#6-tools--capabilities)
7. [Memory System](#7-memory-system)
8. [GEMINI.md — Project Context](#8-geminimd--project-context)
9. [Extensions & MCP Servers](#9-extensions--mcp-servers)
10. [Settings & Customization](#10-settings--customization)
11. [Use Cases & Examples](#11-use-cases--examples)
12. [Gemini CLI vs Claude Code](#12-gemini-cli-vs-claude-code)
13. [Limitations](#13-limitations)
14. [Troubleshooting](#14-troubleshooting)
15. [Skills — Custom Capabilities](#15-skills--custom-capabilities)

---

## 1. Overview

### What is Gemini CLI?

Gemini CLI is Google's **AI-powered command line tool** that brings the Gemini model directly into your terminal. It can read your codebase, execute commands, edit files, search the web, and help you with software development tasks — all from the terminal.

### Key Features

| Feature | Description |
|---|---|
| **Code Understanding** | Reads and understands your entire codebase |
| **File Editing** | Can create, modify, and delete files |
| **Shell Commands** | Executes terminal commands on your behalf |
| **Web Search** | Searches the web for up-to-date information |
| **Memory** | Remembers context across sessions (experimental) |
| **Extensions** | Connect external tools via MCP servers |
| **Multi-modal** | Can process images, PDFs, and other file types |
| **Free Tier** | Generous free usage with Gemini 2.5 Pro |

### How It Works

```
You (Terminal)
    │
    │  "Fix the bug in UserService.java"
    ▼
┌──────────────┐
│  Gemini CLI  │
│              │
│  1. Reads your codebase
│  2. Understands the context
│  3. Plans the fix
│  4. Edits the file
│  5. Optionally runs tests
│              │
└──────────────┘
    │
    ▼
Bug Fixed! ✅
```

---

## 2. Installation

### Prerequisites

- **Node.js** 18 or higher
- **npm** (comes with Node.js)
- **Google Account** (for authentication)

### Install via npm

```bash
npm install -g @anthropic-ai/gemini-cli
```

Or using npx (no install needed):

```bash
npx @anthropic-ai/gemini-cli
```

### Verify Installation

```bash
gemini --version
```

### Authentication

On first run, Gemini CLI will open your browser for Google account authentication:

```bash
gemini
# → Browser opens → Login with Google → Authorize → Done
```

### API Key Authentication (Alternative)

```bash
# Set API key from Google AI Studio (https://aistudio.google.com/apikey)
export GEMINI_API_KEY="your-api-key-here"
gemini
```

---

## 3. Configuration

### Configuration Files

| File | Location | Scope |
|---|---|---|
| `~/.gemini/settings.json` | Home directory | Global (all projects) |
| `.gemini/settings.json` | Project root | Project-specific |
| `GEMINI.md` | Project root | Project context for AI |

### Global Settings

```bash
# Location
# Windows: C:\Users\YourName\.gemini\settings.json
# Linux/Mac: ~/.gemini/settings.json
```

```json
{
  "theme": "dark",
  "model": "gemini-2.5-pro",
  "experimental": {
    "autoMemory": true
  },
  "sandbox": true,
  "yolo": false
}
```

### Project Settings

```bash
# In your project root
mkdir .gemini
# Create .gemini/settings.json
```

```json
{
  "model": "gemini-2.5-flash",
  "sandbox": false
}
```

---

## 4. Basic Usage

### Starting a Session

```bash
# Start in current directory
gemini

# Start with a specific prompt
gemini "Explain this codebase"

# Start with a file
gemini "Review this file" -f src/main/java/App.java
```

### Chatting with Gemini

```
$ gemini

> What does this project do?

Gemini: This is a Spring Boot application that implements a PKI Registration
Authority (RA) with CSR approval workflow...

> Fix the null pointer exception in UserService.java

Gemini: I found the issue. Let me fix it...
[Edits file]
Done! The null check has been added at line 45.

> Run the tests

Gemini: [Executes: mvn test]
All 23 tests passed ✅
```

### Non-Interactive Mode

```bash
# One-shot command
gemini -p "How many Java files are in this project?"

# Pipe input
cat error.log | gemini "What's causing this error?"

# With specific file
gemini "Add input validation" -f src/Controller.java
```

---

## 5. Slash Commands

### Available Commands

| Command | Description |
|---|---|
| `/help` | Show all available commands |
| `/quit` or `/exit` | Exit Gemini CLI |
| `/clear` | Clear conversation history |
| `/chat` | Start a new chat session |
| `/tools` | List available tools |
| `/memory` | Show saved memories |
| `/stats` | Show token usage and session stats |
| `/model` | Show or change the current model |
| `/theme` | Change the UI theme |
| `/compress` | Compress conversation to save context |
| `/restore` | Restore previous session |

### Usage Examples

```
> /help
Shows all commands and their descriptions

> /tools
Lists: Shell, File Read, File Write, Web Search, etc.

> /memory
Shows saved memories from past sessions

> /model gemini-2.5-flash
Switched to gemini-2.5-flash model

> /stats
Tokens used: 15,234 | Cost: $0.00 (free tier)

> /clear
Conversation cleared. Starting fresh.
```

---

## 6. Tools & Capabilities

### Built-in Tools

Gemini CLI has several tools it can use automatically:

#### 1. Shell Execution

```
> Run the Spring Boot application

Gemini: [Executes: mvn spring-boot:run]
Application started on port 8080 ✅
```

#### 2. File Operations

```
> Create a new REST controller for products

Gemini: [Creates: src/main/java/com/app/controller/ProductController.java]
Created ProductController with CRUD endpoints ✅

> Read the pom.xml and tell me what dependencies we have

Gemini: [Reads: pom.xml]
Your project has the following dependencies:
- Spring Boot Starter Web 3.3.0
- Spring Security ...
```

#### 3. Web Search

```
> What's the latest version of Spring Boot?

Gemini: [Searches web]
The latest stable version of Spring Boot is 3.4.1 (as of June 2026)

> How to configure Keycloak SAML with Spring Security?

Gemini: [Searches web + reads docs]
Here's how to set it up...
```

#### 4. Code Analysis

```
> Find all security vulnerabilities in this project

Gemini: [Reads multiple files]
Found 3 potential issues:
1. SQL injection risk in UserRepository.java:45
2. Missing CSRF protection in SecurityConfig.java
3. Hardcoded credentials in application.yml
```

### Tool Permissions

| Mode | Behavior |
|---|---|
| **Default (sandbox)** | Asks permission before executing commands and writing files |
| **YOLO mode** | Executes without asking (use carefully!) |
| **Read-only** | Can only read files and run safe commands |

```json
// settings.json
{
  "sandbox": true,    // asks permission (default)
  "yolo": false       // auto-execute without asking
}
```

---

## 7. Memory System

### What is Auto Memory?

Auto Memory automatically extracts and saves important facts from your conversations, so Gemini remembers them in future sessions.

### Enable Auto Memory

```json
// ~/.gemini/settings.json
{
  "experimental": {
    "autoMemory": true
  }
}
```

Restart Gemini CLI after enabling.

### How It Works

```
Session 1:
  You: "I prefer tabs over spaces, tab size 4"
  You: "This project uses Spring Boot 3.3"
  
  Auto Memory saves:
    ├── "User prefers tabs, tab size 4"
    └── "Project uses Spring Boot 3.3"

Session 2 (next day):
  You: "Create a new service class"
  Gemini: (loads memories)
  → Uses tabs with size 4 ✅
  → Uses Spring Boot 3.3 patterns ✅
  → Without you telling again!
```

### Memory Storage

```
~/.gemini/memories/
  ├── preferences.json     # coding style, tools
  ├── project-context.json # project-specific facts
  └── user-info.json       # user details
```

### Memory Commands

```
> /memory
Shows all saved memories

> /memory clear
Clears all memories

> Remember that I use PostgreSQL for all projects
Gemini: Got it! I'll remember that you prefer PostgreSQL.
```

---

## 8. GEMINI.md — Project Context

### What is GEMINI.md?

`GEMINI.md` is a file you place in your project root to give Gemini permanent context about your project. It's loaded automatically every session.

### Location

```
your-project/
  ├── GEMINI.md          ← project context
  ├── src/
  ├── pom.xml
  └── ...
```

### Example GEMINI.md

```markdown
# Project: PKI Registration Authority

## Overview
This is a Spring Boot application implementing a PKI Registration Authority
with CSR approval workflow using Maker-Checker model.

## Tech Stack
- Java 21 + Spring Boot 3.3
- Spring Security (HTTP Basic → migrating to Keycloak OIDC)
- PostgreSQL + Flyway migrations
- Gradle build system

## Architecture
- Multi-module Gradle project: raservice, gui, common
- Maker-Checker approval workflow for CSR processing
- Observer pattern for audit events
- State machine for CSR status transitions

## Conventions
- Use 4 spaces for indentation
- Follow Google Java Style Guide
- All REST endpoints under /api/ra/
- Error codes follow pattern PKI_XXX_NNN
- Write Javadoc for all public methods

## Important Notes
- HSM integration is pluggable (not hardcoded)
- Never log private keys or PII
- All database changes via Flyway migrations
- Test coverage target: 80%+
```

### GEMINI.md vs Settings

| File | Purpose | What Goes Here |
|---|---|---|
| `GEMINI.md` | Project knowledge for AI | Architecture, conventions, codebase info |
| `.gemini/settings.json` | Tool configuration | Model, sandbox mode, memory, permissions |

---

## 9. Extensions & MCP Servers

### What are Extensions?

Extensions connect Gemini CLI to external tools and services using the **MCP (Model Context Protocol)** standard.

### Built-in Extensions

| Extension | What It Does |
|---|---|
| **File System** | Read/write files in your project |
| **Shell** | Execute terminal commands |
| **Web Search** | Search the internet |

### Adding Custom MCP Servers

```json
// .gemini/settings.json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_TOKEN": "ghp_xxxx"
      }
    },
    "postgres": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres"],
      "env": {
        "DATABASE_URL": "postgresql://user:pass@localhost:5432/mydb"
      }
    },
    "slack": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-slack"],
      "env": {
        "SLACK_TOKEN": "xoxb-xxxx"
      }
    }
  }
}
```

### Popular MCP Servers

| Server | Use Case |
|---|---|
| **GitHub** | PRs, issues, repos |
| **PostgreSQL** | Query databases |
| **Slack** | Send messages, read channels |
| **Google Drive** | Read/write documents |
| **Brave Search** | Enhanced web search |
| **Filesystem** | Extended file operations |
| **Memory** | Persistent knowledge base |

---

## 10. Settings & Customization

### Complete Settings Reference

```json
{
  "model": "gemini-2.5-pro",
  "theme": "dark",
  "sandbox": true,
  "yolo": false,
  "experimental": {
    "autoMemory": true
  },
  "mcpServers": {},
  "customInstructions": "Always respond in Hindi when asked in Hindi"
}
```

### Available Models

| Model | Speed | Quality | Best For |
|---|---|---|---|
| `gemini-2.5-pro` | Slower | Best | Complex tasks, architecture |
| `gemini-2.5-flash` | Fast | Good | Quick edits, simple questions |
| `gemini-2.0-flash` | Fastest | OK | Basic tasks |

### Theme Options

```
> /theme dark     # dark background
> /theme light    # light background
> /theme auto     # follow system
```

---

## 11. Use Cases & Examples

### 1. Code Review

```
$ cd my-project
$ gemini "Review src/main/java/AuthService.java for security issues"

Gemini analyzes and reports:
- Missing input validation at line 23
- SQL injection risk at line 45
- Hardcoded secret at line 12
```

### 2. Bug Fixing

```
$ gemini "The login endpoint returns 500 error. Fix it."

Gemini:
1. Reads error logs
2. Finds NullPointerException in AuthController.java:67
3. Adds null check
4. Runs tests to verify
Done! ✅
```

### 3. Code Generation

```
$ gemini "Create a REST API for managing students with CRUD operations"

Gemini creates:
- Student.java (entity)
- StudentRepository.java
- StudentService.java
- StudentController.java
- Flyway migration script
```

### 4. Documentation

```
$ gemini "Generate API documentation for all controllers"

Gemini reads all controllers and generates:
- Endpoint list with methods and paths
- Request/response examples
- Authentication requirements
```

### 5. Refactoring

```
$ gemini "Refactor UserService to use the Repository pattern"

Gemini:
1. Analyzes current code
2. Creates UserRepository interface
3. Moves database logic to repository
4. Updates UserService to use repository
5. Updates tests
```

### 6. Git Operations

```
$ gemini "Summarize what changed in the last 5 commits"

Gemini:
[Executes: git log --oneline -5]
[Reads changed files]
Summary:
- Commit 1: Added JWT authentication
- Commit 2: Fixed password hashing
- ...
```

### 7. Learning & Explaining

```
$ gemini "Explain how the Maker-Checker pattern works in this codebase"

Gemini reads your code and explains with specific file references:
- CsrStatusTransition.java handles state machine
- ApprovalService.java enforces separation of duties
- ...
```

---

## 12. Gemini CLI vs Claude Code

| Feature | Gemini CLI | Claude Code |
|---|---|---|
| **Company** | Google | Anthropic |
| **Model** | Gemini 2.5 Pro/Flash | Claude Sonnet/Opus |
| **Free Tier** | Generous (1000 req/day) | Limited |
| **File Editing** | Yes | Yes |
| **Shell Execution** | Yes | Yes |
| **Web Search** | Built-in | Via tools |
| **Memory** | Experimental | Stable, structured |
| **Project Context** | GEMINI.md | CLAUDE.md |
| **Extensions** | MCP Servers | MCP Servers |
| **Multi-modal** | Images, PDFs | Images, PDFs |
| **IDE Integration** | VS Code, JetBrains | VS Code, JetBrains |
| **Agent Mode** | Basic | Advanced (sub-agents) |
| **Task Tracking** | No | Yes |
| **Plan Mode** | No | Yes |
| **Best For** | Quick tasks, free usage | Complex projects, deep reasoning |

### When to Use Which

```
Use Gemini CLI when:
  ✅ Free usage needed (no billing)
  ✅ Quick code edits and explanations
  ✅ Web search integrated answers
  ✅ Google ecosystem (Firebase, GCP, Android)

Use Claude Code when:
  ✅ Complex multi-file refactoring
  ✅ Deep architectural planning
  ✅ Advanced agent capabilities (sub-agents)
  ✅ Structured memory and task tracking
  ✅ Enterprise security-sensitive code
```

---

## 13. Limitations

| Limitation | Details |
|---|---|
| **No true agent loop** | Cannot autonomously Think → Act → Observe like LangChain agents |
| **Memory is experimental** | Auto Memory may not save everything accurately |
| **No multi-agent** | Cannot spawn sub-agents for parallel work |
| **Context window** | Large codebases may exceed context limits |
| **No task tracking** | No built-in task/todo management |
| **Network required** | Cannot work offline (cloud-based model) |
| **Rate limits** | Free tier has daily request limits |
| **No plan mode** | Cannot create and approve implementation plans |

---

## 14. Troubleshooting

### Common Issues

#### Authentication Failed

```bash
# Clear cached credentials
rm -rf ~/.gemini/auth/

# Re-authenticate
gemini
```

#### Model Not Available

```bash
# Check available models
gemini --list-models

# Switch to available model
# In settings.json: "model": "gemini-2.5-flash"
```

#### MCP Server Not Connecting

```bash
# Test MCP server independently
npx @modelcontextprotocol/server-github

# Check logs
cat ~/.gemini/logs/mcp-error.log

# Verify environment variables are set
echo $GITHUB_TOKEN
```

#### Slow Responses

```json
// Switch to faster model in settings.json
{
  "model": "gemini-2.5-flash"
}
```

#### Rate Limited

```
Error: 429 Too Many Requests

Solution:
- Wait a few minutes and retry
- Use API key for higher limits
- Switch to flash model (lower cost)
```

#### Memory Not Working

```bash
# Verify auto memory is enabled
cat ~/.gemini/settings.json
# Should have: "autoMemory": true

# Restart CLI after enabling
# Check memories
> /memory
```

---

## 15. Skills — Custom Capabilities

### What are Skills?

Skills are **reusable, custom instructions** that teach Gemini CLI how to perform specific tasks. Think of them as **plugins/macros** — you define WHAT to do, and Gemini follows those instructions every time.

```
Without Skill:
  You: "Review this C code for crypto vulnerabilities"
  Gemini: Generic review, misses crypto-specific issues ❌

With Skill:
  You: "Review this C code for crypto vulnerabilities"
  Gemini: Loads crypto-review skill → checks all 10 security categories
          → structured output with SEVERITY, FILE, FIX ✅
```

### Skill File Structure

A skill is a **Markdown file** (`.md`) placed in a specific directory:

```
~/.gemini/skills/           ← Global skills (all projects)
  └── my-skill.md

.gemini/skills/             ← Project-specific skills
  └── project-skill.md
```

### Anatomy of a Skill File

```markdown
# Skill Name

## Description
What this skill does — Gemini uses this to decide when to activate the skill.

## When to Use
- Trigger conditions
- Keywords that activate this skill

## Instructions
Step-by-step instructions for Gemini to follow.

### Step 1: Gather Input
Explain what to collect/read

### Step 2: Process
Explain what to do with the input

### Step 3: Output
Explain the expected output format
```

### How to Create a Skill — Step by Step

#### Example 1: Java Code Review Skill

```bash
# Create skills directory
mkdir -p ~/.gemini/skills
```

Create `~/.gemini/skills/java-review.md`:

```markdown
# Java Security Code Review

## Description
Review Java source code for security vulnerabilities, focusing on
OWASP Top 10, Spring Security misconfigurations, and Java-specific issues.

## When to Use
- When the user asks to review Java code
- When the user mentions "security review" or "code audit"
- When reviewing Spring Boot controllers or security configs

## Instructions

### Step 1: Identify Files
Find all Java files in the specified scope (changed files, specific file, or entire project).

### Step 2: Review Checklist
Check each file for:

1. **SQL Injection**
   - Raw SQL queries with string concatenation
   - Missing parameterized queries
   - JPA @Query with SpEL injection

2. **Authentication Issues**
   - Hardcoded credentials
   - Missing authentication on endpoints
   - Weak password policies

3. **Authorization Flaws**
   - Missing @PreAuthorize or role checks
   - IDOR (Insecure Direct Object Reference)
   - Privilege escalation paths

4. **Input Validation**
   - Missing @Valid annotations
   - Unvalidated path variables
   - Missing size/pattern constraints

5. **Sensitive Data Exposure**
   - Logging PII or secrets
   - Missing @JsonIgnore on sensitive fields
   - API responses with too much data

6. **Spring Security Config**
   - CSRF disabled without justification
   - CORS misconfiguration
   - Missing security headers

### Step 3: Output Format
For each finding report:
- **Severity**: CRITICAL / HIGH / MEDIUM / LOW
- **File**: filename:line_number
- **Issue**: One-line description
- **Fix**: Code snippet showing the fix

End with a summary table of findings by severity.
```

#### Example 2: Git Commit Message Skill

Create `~/.gemini/skills/commit-message.md`:

```markdown
# Smart Commit Message Generator

## Description
Generate conventional commit messages based on staged changes.

## When to Use
- When the user asks to "write a commit message"
- When the user says "commit this"

## Instructions

### Step 1: Analyze Changes
Run `git diff --cached --stat` and `git diff --cached` to see staged changes.

### Step 2: Determine Type
Based on changes, pick the commit type:
- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation only
- `refactor`: code restructuring
- `test`: adding tests
- `chore`: build/tooling changes

### Step 3: Generate Message
Format:
```
type(scope): short description (max 72 chars)

- Bullet point explaining what changed
- Another bullet point if needed

Co-Authored-By: Gemini CLI
```

Keep the subject line under 72 characters.
Focus on WHY, not WHAT (the diff shows what).
```

#### Example 3: API Documentation Skill

Create `~/.gemini/skills/api-docs.md`:

```markdown
# REST API Documentation Generator

## Description
Generate OpenAPI/Swagger-style documentation for Spring Boot REST controllers.

## When to Use
- When the user asks for "API docs" or "endpoint documentation"
- When reviewing controllers

## Instructions

### Step 1: Find Controllers
Search for files with @RestController or @Controller annotations.

### Step 2: Extract Endpoints
For each controller, extract:
- HTTP method (@GetMapping, @PostMapping, etc.)
- URL path
- Request parameters (@RequestParam, @PathVariable)
- Request body (@RequestBody)
- Response type
- Authentication requirements

### Step 3: Generate Documentation
Output in markdown table format:

| Method | Endpoint | Description | Auth | Request Body | Response |
|--------|----------|-------------|------|--------------|----------|

Then for each endpoint, show:
- Example curl command
- Example request JSON
- Example response JSON
- Possible error codes
```

#### Example 4: Database Migration Skill

Create `.gemini/skills/migration.md` (project-specific):

```markdown
# Flyway Migration Generator

## Description
Generate Flyway SQL migration scripts for schema changes.

## When to Use
- When the user asks to "add a table", "modify schema", or "create migration"

## Instructions

### Step 1: Check Existing Migrations
Read all files in src/main/resources/db/migration/ to find the latest version number.

### Step 2: Generate Migration
Create a new migration file with:
- Filename: V{next_number}__{description}.sql
- Use IF NOT EXISTS for safety
- Add comments explaining the change
- Include rollback instructions as comments

### Step 3: Conventions
- Table names: snake_case, plural (e.g., certificate_requests)
- Column names: snake_case (e.g., created_at)
- Always add created_at and updated_at columns
- Always add primary key
- Use UUID for IDs when possible
- Add indexes for foreign keys and frequently queried columns
```

### Installing Skills

#### Global Skills (all projects)

```bash
# Create directory
mkdir -p ~/.gemini/skills

# Add skill files
cp my-skill.md ~/.gemini/skills/

# Restart Gemini CLI
# Skills auto-load on startup
```

#### Project-Specific Skills

```bash
# In your project root
mkdir -p .gemini/skills

# Add skill files
cp project-skill.md .gemini/skills/

# These only activate when Gemini runs in this project
```

#### From GitHub (sharing skills)

```bash
# Clone a skills collection
git clone https://github.com/someone/gemini-skills.git

# Copy to global skills
cp gemini-skills/*.md ~/.gemini/skills/

# Or symlink for auto-updates
ln -s /path/to/gemini-skills/*.md ~/.gemini/skills/
```

### Skills Directory Structure

```
~/.gemini/
  ├── settings.json
  ├── memories/
  └── skills/                    ← Global skills
      ├── java-review.md
      ├── commit-message.md
      ├── api-docs.md
      └── explain-code.md

your-project/
  ├── .gemini/
  │   ├── settings.json          ← Project settings
  │   └── skills/                ← Project-specific skills
  │       ├── migration.md
  │       └── deploy.md
  ├── GEMINI.md                  ← Project context
  ├── src/
  └── pom.xml
```

### Skill Loading Priority

```
1. Project skills (.gemini/skills/)     ← highest priority
2. Global skills (~/.gemini/skills/)    ← fallback
3. Built-in capabilities                ← always available

If same skill name exists in both:
  Project skill overrides global skill
```

### Tips for Writing Good Skills

| Tip | Why |
|---|---|
| Be specific in instructions | Vague instructions = vague output |
| Include output format | Consistent, structured results |
| Add examples | Gemini learns from examples |
| Use checklists | Ensures nothing is missed |
| Keep under 2000 words | Too long = context wasted |
| Test and iterate | Refine based on results |
| Add "When to Use" section | Helps Gemini auto-activate |

### Skills vs GEMINI.md vs Memory

| Feature | Skills | GEMINI.md | Memory |
|---|---|---|---|
| **Purpose** | HOW to do specific tasks | WHAT the project is about | WHO the user is + preferences |
| **Scope** | Task-specific instructions | Project-wide context | Cross-session facts |
| **Format** | Step-by-step instructions | Free-form description | Auto-extracted key-value |
| **Example** | "Review Java code this way" | "This is a Spring Boot PKI app" | "User prefers tabs, size 4" |
| **Location** | `.gemini/skills/` | Project root | `~/.gemini/memories/` |

---

## Quick Reference Card

```
INSTALL:     npm install -g @anthropic-ai/gemini-cli
START:       gemini
ONE-SHOT:    gemini -p "your question"
WITH FILE:   gemini "review this" -f file.java
HELP:        /help
EXIT:        /quit
CLEAR:       /clear
MEMORY:      /memory
TOOLS:       /tools
MODEL:       /model gemini-2.5-flash
CONTEXT:     Create GEMINI.md in project root
SETTINGS:    ~/.gemini/settings.json
EXTENSIONS:  Add MCP servers in settings.json
```

---

*End of Document*
