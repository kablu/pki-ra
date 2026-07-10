# AsciiDoctor — Complete Guide

**Modern Documentation Toolchain**
*Version 1.0 | June 2026 | RipplesBit Documentation*

---

## Table of Contents

1. [What is AsciiDoctor](#1-what-is-asciidoctor)
2. [Why AsciiDoctor](#2-why-asciidoctor)
3. [Problem It Solves](#3-problem-it-solves)
4. [How It Works](#4-how-it-works)
5. [Installation](#5-installation)
6. [AsciiDoc Syntax](#6-asciidoc-syntax)
7. [Output Formats](#7-output-formats)
8. [Practical Scenarios](#8-practical-scenarios)
9. [AsciiDoctor vs Markdown vs LaTeX](#9-asciidoctor-vs-markdown-vs-latex)
10. [Tools & Ecosystem](#10-tools--ecosystem)
11. [Examples](#11-examples)
12. [Best Practices](#12-best-practices)

---

## 1. What is AsciiDoctor

### Definition

AsciiDoctor is a **text processor and publishing toolchain** that converts AsciiDoc markup into HTML, PDF, EPUB, DocBook, and other formats. It's the modern implementation of the AsciiDoc language.

```
AsciiDoc  = The markup LANGUAGE (like Markdown but more powerful)
AsciiDoctor = The TOOL that processes AsciiDoc files
```

### Simple Analogy

```
Markdown    = Bicycle     (simple, limited, gets you there)
AsciiDoc    = Car         (more features, comfortable, professional)
LaTeX       = Airplane    (powerful but complex, steep learning curve)
```

### Who Uses It

| Organization | What They Document |
|---|---|
| **Spring Framework** | All official documentation |
| **Red Hat** | Enterprise product docs |
| **GitHub** | Supports .adoc rendering |
| **JBoss / Hibernate** | Framework documentation |
| **Neo4j** | Database documentation |
| **Groovy / Gradle** | Build tool documentation |
| **O'Reilly Media** | Book publishing |

---

## 2. Why AsciiDoctor

### Reasons to Use AsciiDoctor

| # | Reason | Detail |
|---|---|---|
| 1 | **Richer than Markdown** | Tables, admonitions, footnotes, cross-references — all built-in |
| 2 | **Single source, multiple outputs** | One `.adoc` file → HTML + PDF + EPUB + DocBook |
| 3 | **Professional publishing** | Books, technical docs, API guides, manuals |
| 4 | **Include mechanism** | Split large docs into chapters, include code from actual files |
| 5 | **Table of Contents** | Auto-generated, configurable TOC |
| 6 | **Diagrams** | PlantUML, Mermaid, Ditaa diagrams inline |
| 7 | **Versioning friendly** | Plain text files → Git-friendly, diff-friendly |
| 8 | **IDE support** | IntelliJ, VS Code plugins available |
| 9 | **Spring ecosystem** | Spring docs use AsciiDoc — if you're a Java dev, this is your standard |
| 10 | **Free & open source** | MIT licensed, active community |

---

## 3. Problem It Solves

### Problem 1: Markdown Is Too Limited

```
Markdown:
  ❌ No admonitions (NOTE, WARNING, TIP blocks)
  ❌ No table cell spanning (colspan/rowspan)
  ❌ No footnotes (standard Markdown)
  ❌ No includes (can't split into chapters)
  ❌ No cross-references between documents
  ❌ No auto-numbered sections
  ❌ No built-in TOC (needs plugins)
  ❌ Every renderer handles it differently

AsciiDoc:
  ✅ All of the above built-in
  ✅ One specification, consistent rendering
```

### Problem 2: Word/Google Docs Don't Version Well

```
Word Document:
  ❌ Binary format → Git can't diff
  ❌ Merge conflicts are nightmare
  ❌ "final_v2_FINAL_really_final.docx"
  ❌ Formatting breaks across systems

AsciiDoc:
  ✅ Plain text → Git tracks every change
  ✅ Easy merge, easy diff
  ✅ Version history built into Git
  ✅ Consistent rendering everywhere
```

### Problem 3: Documentation Scattered Everywhere

```
Before AsciiDoctor:
  API docs     → Swagger/OpenAPI
  User guide   → Word document
  Architecture → Confluence wiki
  README       → Markdown
  Tutorials    → Blog posts
  😫 5 tools, 5 formats, nothing consistent

After AsciiDoctor:
  Everything   → .adoc files in Git repo
  Build        → One command generates HTML + PDF
  ✅ Single source of truth, docs-as-code
```

### Problem 4: Can't Generate Multiple Formats

```
Scenario: You wrote a 200-page technical manual

  Need HTML for website        → Reformat manually? 😫
  Need PDF for printing        → Reformat manually? 😫
  Need EPUB for kindle         → Reformat manually? 😫
  Need DocBook for publishing  → Reformat manually? 😫

AsciiDoctor:
  asciidoctor manual.adoc              → HTML ✅
  asciidoctor-pdf manual.adoc          → PDF ✅
  asciidoctor-epub3 manual.adoc        → EPUB ✅
  asciidoctor -b docbook manual.adoc   → DocBook ✅
  
  One source, all formats! 🎉
```

---

## 4. How It Works

### Processing Pipeline

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  .adoc file  │────▶│  AsciiDoctor │────▶│  Output      │
│  (plain text)│     │  (processor) │     │  HTML/PDF/   │
│              │     │              │     │  EPUB/DocBook│
└──────────────┘     └──────────────┘     └──────────────┘
                           │
                     ┌─────┴──────┐
                     │  Themes    │
                     │  Templates │
                     │  Extensions│
                     └────────────┘
```

### Detailed Flow

```
1. WRITE
   document.adoc (plain text with AsciiDoc markup)
       │
2. PROCESS
   AsciiDoctor reads the file
       │
   ├── Parses headers, sections, attributes
   ├── Resolves includes (include::chapter1.adoc[])
   ├── Processes macros (image::, link::, etc.)
   ├── Generates TOC
   ├── Numbers sections
   ├── Resolves cross-references
   └── Applies theme/template
       │
3. OUTPUT
   ├── document.html   (web)
   ├── document.pdf    (print)
   ├── document.epub   (ebook)
   └── document.xml    (DocBook)
```

---

## 5. Installation

### Ruby (Original)

```bash
# Install Ruby first (if not installed)
# Then:
gem install asciidoctor
gem install asciidoctor-pdf
gem install asciidoctor-diagram
```

### Node.js

```bash
npm install -g @asciidoctor/core
npm install -g asciidoctor
```

### Java / Maven (for Spring projects)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.asciidoctor</groupId>
    <artifactId>asciidoctor-maven-plugin</artifactId>
    <version>3.0.0</version>
    <configuration>
        <sourceDirectory>src/docs/asciidoc</sourceDirectory>
        <outputDirectory>target/generated-docs</outputDirectory>
        <backend>html5</backend>
    </configuration>
</plugin>
```

```bash
mvn asciidoctor:process-asciidoc
```

### Gradle (for Spring Boot projects)

```groovy
plugins {
    id 'org.asciidoctor.jvm.convert' version '4.0.2'
}

asciidoctor {
    sourceDir = file('src/docs/asciidoc')
    outputDir = file('build/docs')
    backends = ['html5', 'pdf']
}
```

### Docker

```bash
docker run --rm -v $(pwd):/documents/ asciidoctor/docker-asciidoctor \
    asciidoctor document.adoc
```

### Verify Installation

```bash
asciidoctor --version
# Asciidoctor 2.0.x
```

---

## 6. AsciiDoc Syntax

### Document Header

```asciidoc
= Document Title
Author Name <email@example.com>
:revnumber: 1.0
:revdate: June 2026
:toc: left
:toclevels: 3
:sectnums:
:icons: font
:source-highlighter: highlight.js
```

### Headings

```asciidoc
= Document Title (Level 0)
== Chapter (Level 1)
=== Section (Level 2)
==== Sub-section (Level 3)
===== Sub-sub-section (Level 4)
```

### Text Formatting

```asciidoc
*bold text*
_italic text_
`monospace code`
*_bold italic_*
#highlighted text#
[.underline]#underlined#
[.line-through]#strikethrough#
^superscript^
~subscript~
```

### Lists

```asciidoc
// Unordered
* Item 1
* Item 2
** Nested item
*** Deeper nested

// Ordered
. First
. Second
.. Sub-step a
.. Sub-step b
. Third

// Checklist
* [x] Done
* [ ] Not done
* [x] Also done

// Description list
Term 1:: Definition of term 1
Term 2:: Definition of term 2
```

### Links & Images

```asciidoc
// Links
https://ripplesbit.com[RipplesBit Website]
link:document.pdf[Download PDF]
<<section-id,Cross Reference>>
xref:other-doc.adoc[Link to another doc]

// Images
image::logo.png[RipplesBit Logo, 200, 100]
image::architecture.png[Architecture Diagram]

// Inline image
Click the image:icon.png[icon, 16, 16] button.
```

### Tables

```asciidoc
// Simple table
|===
| Header 1 | Header 2 | Header 3

| Cell 1   | Cell 2   | Cell 3
| Cell 4   | Cell 5   | Cell 6
|===

// Table with options
[cols="1,2,3", options="header,footer"]
|===
| ID | Name | Description

| 1  | PKI  | Public Key Infrastructure
| 2  | RA   | Registration Authority
| 3  | CA   | Certificate Authority

| 3+| Total: 3 entries
|===

// Column spanning
|===
| Col 1 | Col 2 | Col 3

3+| This cell spans all 3 columns
| A     2+| This spans 2 columns
|===

// Row spanning
|===
| Col 1 | Col 2

.2+| Spans 2 rows | Row 1
| Row 2
|===

// CSV data table
[%header, format=csv]
|===
Name,Age,City
Kablu,25,Delhi
Rahul,30,Mumbai
|===
```

### Code Blocks

```asciidoc
// Inline code
Use `System.out.println()` to print.

// Code block with syntax highlighting
[source,java]
----
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello World!");
    }
}
----

// Include actual file as code
[source,java]
----
include::src/main/java/App.java[lines=10..25]
----

// With callouts
[source,java]
----
String name = "Kablu";  // <1>
int age = 25;           // <2>
----
<1> User's name
<2> User's age
```

### Admonitions (Callout Boxes)

```asciidoc
NOTE: This is a note — helpful information.

TIP: This is a tip — best practice suggestion.

IMPORTANT: This is important — don't miss this.

WARNING: This is a warning — be careful.

CAUTION: This is a caution — potential danger.

// Block style
[NOTE]
====
This is a longer note that spans
multiple lines with *formatting*.
====
```

Renders as:

```
┌─────────────────────────────────────┐
│ 📝 NOTE                            │
│ This is a note — helpful information│
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ ⚠️ WARNING                         │
│ This is a warning — be careful     │
└─────────────────────────────────────┘
```

### Includes (Split Documents)

```asciidoc
// Main document
= PKI-RA Complete Guide

include::chapters/introduction.adoc[]

include::chapters/architecture.adoc[]

include::chapters/installation.adoc[]

include::chapters/api-reference.adoc[]

// Include with level offset (adjust heading levels)
include::chapters/appendix.adoc[leveloffset=+1]

// Include specific lines from a file
include::src/main/java/App.java[lines=1..20]

// Include with tags
include::src/main/java/App.java[tag=main-method]
```

### Cross References

```asciidoc
// Define an anchor
[[installation-section]]
== Installation

// Reference it
See <<installation-section>> for setup instructions.

// With custom text
See <<installation-section, how to install>> for details.

// Cross-document reference
See xref:api-guide.adoc#endpoints[API Endpoints].
```

### Footnotes

```asciidoc
PKI stands for Public Key Infrastructure.footnote:[First defined in ITU-T X.509]

SAML uses XML assertions.footnote:saml[Security Assertion Markup Language, OASIS Standard]
```

### Sidebar & Quotes

```asciidoc
// Sidebar
****
This is a sidebar with additional context
that sits alongside the main content.
****

// Block quote
[quote, Linus Torvalds]
____
Talk is cheap. Show me the code.
____

// Verse (preserves line breaks)
[verse, Robert Frost]
____
Two roads diverged in a wood, and I—
I took the one less traveled by,
And that has made all the difference.
____
```

### Diagrams (with asciidoctor-diagram)

```asciidoc
// PlantUML
[plantuml, target=auth-flow, format=png]
----
@startuml
actor User
User -> "Spring App" : Login
"Spring App" -> Keycloak : SAML Request
Keycloak -> User : Login Page
User -> Keycloak : Credentials
Keycloak -> "Spring App" : SAML Assertion
"Spring App" -> User : Welcome!
@enduml
----

// Mermaid
[mermaid]
----
graph TD
    A[CSR Submitted] --> B[Pending Review]
    B --> C{Approval Mode}
    C -->|Single| D[Approved]
    C -->|Dual| E[Maker Review]
    E --> F[Checker Accept]
    F --> D
    D --> G[Sent to CA]
    G --> H[Certificate Issued]
----
```

---

## 7. Output Formats

### Generate HTML

```bash
asciidoctor document.adoc
# → document.html
```

### Generate PDF

```bash
gem install asciidoctor-pdf

asciidoctor-pdf document.adoc
# → document.pdf

# With custom theme
asciidoctor-pdf -a pdf-theme=custom-theme.yml document.adoc
```

### Generate EPUB (eBook)

```bash
gem install asciidoctor-epub3

asciidoctor-epub3 document.adoc
# → document.epub (for Kindle, iBooks, etc.)
```

### Generate DocBook XML

```bash
asciidoctor -b docbook document.adoc
# → document.xml
```

### Generate Slide Deck (Reveal.js)

```bash
gem install asciidoctor-revealjs

asciidoctor-revealjs presentation.adoc
# → presentation.html (interactive slides)
```

### Output Comparison

| Format | Command | Use Case |
|---|---|---|
| **HTML** | `asciidoctor doc.adoc` | Website, online docs |
| **PDF** | `asciidoctor-pdf doc.adoc` | Print, offline reading |
| **EPUB** | `asciidoctor-epub3 doc.adoc` | eBooks, Kindle |
| **DocBook** | `asciidoctor -b docbook doc.adoc` | Publishing pipelines |
| **Slides** | `asciidoctor-revealjs doc.adoc` | Presentations |
| **Man page** | `asciidoctor -b manpage doc.adoc` | CLI tool documentation |

---

## 8. Practical Scenarios

### Scenario 1: Spring Boot API Documentation

```
Problem: Your PKI-RA has 25+ REST endpoints. Swagger is good for
         interactive testing but bad for a complete guide.

Solution: AsciiDoc + Spring REST Docs

  1. Write tests that generate API snippets
  2. Include snippets in AsciiDoc
  3. Generate HTML/PDF documentation
```

```asciidoc
= PKI-RA API Guide
:toc: left
:sectnums:

== Certificate Requests

=== Submit CSR

`POST /api/ra/requests`

==== Request

include::{snippets}/submit-csr/http-request.adoc[]

==== Response

include::{snippets}/submit-csr/http-response.adoc[]

==== Fields

include::{snippets}/submit-csr/request-fields.adoc[]
```

### Scenario 2: Technical Book / Training Manual

```
Problem: RipplesBit wants to publish a "Java Security" training manual.
         Need print PDF + online HTML + eBook.

Solution: Write once in AsciiDoc, publish everywhere.
```

```
training-manual/
  ├── book.adoc              ← Main file
  ├── chapters/
  │   ├── 01-intro.adoc
  │   ├── 02-java-basics.adoc
  │   ├── 03-spring-security.adoc
  │   ├── 04-pki.adoc
  │   └── 05-keycloak.adoc
  ├── images/
  ├── code-samples/
  └── themes/
      └── ripplesbit-theme.yml

# Generate all formats
asciidoctor book.adoc                    # HTML
asciidoctor-pdf book.adoc               # PDF
asciidoctor-epub3 book.adoc             # EPUB
```

### Scenario 3: Architecture Decision Records (ADR)

```
Problem: Team decisions not documented, new members don't know WHY
         something was chosen.

Solution: AsciiDoc ADRs in Git — versioned, searchable.
```

```asciidoc
= ADR-001: Use Maker-Checker for CSR Approval

== Status
Accepted

== Context
CSR approval needs dual control for security compliance.
Single person should not be able to approve their own requests.

== Decision
Implement configurable Maker-Checker pattern:
- SINGLE mode: one approver sufficient
- DUAL mode: Maker reviews, different Checker approves

== Consequences
- More complex workflow code
- Better audit trail
- Meets SOC2 compliance requirements
- Separation of duties enforced
```

### Scenario 4: CI/CD Documentation Pipeline

```
Problem: Docs go stale because updating is manual.

Solution: Docs build automatically in CI/CD pipeline.
```

```yaml
# .github/workflows/docs.yml
name: Build Documentation
on:
  push:
    paths: ['docs/**']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Build HTML
        uses: asciidoctor/github-action@v1
        with:
          input: docs/guide.adoc
          output: docs/output/
      
      - name: Deploy to GitHub Pages
        uses: peaceiris/actions-gh-pages@v3
        with:
          publish_dir: docs/output/
```

### Scenario 5: Release Notes

```asciidoc
= PKI-RA v2.0 Release Notes
RipplesBit Team
:revdate: June 2026

== New Features

* Maker-Checker approval workflow
* Async CA integration with callback support
* Configurable approval matrix

== Bug Fixes

* Fixed null pointer in CSR status transition
* Fixed race condition in concurrent approvals

== Breaking Changes

WARNING: The `/api/ra/approve` endpoint now requires `ROLE_OPERATOR`.
Previously it was open to all authenticated users.

== Migration Guide

. Update database schema:
+
[source,sql]
----
ALTER TABLE certificate_requests ADD COLUMN approval_mode VARCHAR(10);
----

. Update application.yml:
+
[source,yaml]
----
pki:
  approval:
    mode: DUAL
----
```

---

## 9. AsciiDoctor vs Markdown vs LaTeX

| Feature | Markdown | AsciiDoc | LaTeX |
|---|---|---|---|
| **Learning curve** | Very easy | Easy | Hard |
| **Table support** | Basic | Advanced (span, format) | Advanced |
| **Admonitions** | No | Yes (NOTE, TIP, WARNING) | Custom |
| **Includes** | No | Yes | Yes |
| **Cross-references** | No | Yes | Yes |
| **Auto-numbered sections** | No | Yes | Yes |
| **Footnotes** | Partial | Yes | Yes |
| **TOC generation** | Plugin needed | Built-in | Built-in |
| **PDF output** | Plugin needed | Built-in | Native |
| **Code highlighting** | Yes | Yes | Package needed |
| **Diagrams** | Plugin needed | Built-in (diagram ext) | TikZ |
| **Book publishing** | Not suitable | Yes | Yes |
| **Git-friendly** | Yes | Yes | Yes |
| **GitHub rendering** | Yes | Yes | No |
| **IDE support** | Excellent | Good | Good |
| **Ecosystem** | Huge | Growing | Mature |
| **Best for** | READMEs, quick docs | Technical docs, books | Academic papers |

### When to Use What

```
README, quick notes, GitHub wikis
  → Markdown ✅

Technical documentation, API guides, training manuals, books
  → AsciiDoc ✅

Academic papers, research, math-heavy documents
  → LaTeX ✅

Your RipplesBit training materials + PKI-RA documentation
  → AsciiDoc ✅ (Spring ecosystem standard)
```

---

## 10. Tools & Ecosystem

### Editors & IDE Plugins

| Tool | Plugin | Features |
|---|---|---|
| **IntelliJ IDEA** | AsciiDoc Plugin | Preview, syntax highlighting, navigation |
| **VS Code** | AsciiDoc Extension | Live preview, snippets |
| **Atom** | asciidoc-preview | Side-by-side preview |
| **Vim** | vim-asciidoctor | Syntax highlighting |
| **Online** | asciidoclive.com | Browser-based editor |

### Build Tools Integration

| Build Tool | Plugin | Command |
|---|---|---|
| **Maven** | asciidoctor-maven-plugin | `mvn asciidoctor:process-asciidoc` |
| **Gradle** | asciidoctor-gradle-plugin | `gradle asciidoctor` |
| **GitHub Actions** | asciidoctor/github-action | Auto-build on push |
| **GitLab CI** | Docker image | Build in pipeline |

### Diagram Extensions

| Extension | Diagram Type |
|---|---|
| **PlantUML** | UML, sequence, class, activity diagrams |
| **Mermaid** | Flowcharts, sequence, Gantt |
| **Ditaa** | ASCII art to diagrams |
| **GraphViz** | Graph visualization |
| **BlockDiag** | Block, sequence, activity, network diagrams |

### Theme & Styling

```yaml
# custom-theme.yml for PDF
extends: default
font:
  catalog:
    Noto:
      normal: NotoSans-Regular.ttf
      bold: NotoSans-Bold.ttf
page:
  background_color: '#FFFFFF'
  layout: portrait
  size: A4
  margin: [30mm, 25mm, 30mm, 25mm]
heading:
  font_color: '#0066B3'
  h1:
    font_size: 28
  h2:
    font_size: 22
```

---

## 11. Examples

### Minimal Document

```asciidoc
= My First AsciiDoc
Kablu <kablumndl546@gmail.com>
:toc:

== Introduction

This is my first AsciiDoc document.

== Features

* Simple syntax
* Multiple output formats
* Professional results
```

### Generate:

```bash
asciidoctor first.adoc         # → first.html
asciidoctor-pdf first.adoc     # → first.pdf
```

### Complete Project Documentation

```asciidoc
= PKI Registration Authority — Technical Guide
RipplesBit Engineering Team <engineering@ripplesbit.com>
:revnumber: 2.0
:revdate: June 2026
:toc: left
:toclevels: 3
:sectnums:
:icons: font
:source-highlighter: highlight.js
:imagesdir: images

== Overview

PKI-RA is a *Registration Authority* built with Spring Boot
that manages the certificate signing request (CSR) lifecycle.

[NOTE]
====
This document covers version 2.0 which introduces
the Maker-Checker approval workflow.
====

== Architecture

image::architecture.png[PKI-RA Architecture, 800]

The system follows a layered architecture:

[cols="1,3", options="header"]
|===
| Layer | Responsibility

| Controller
| REST API endpoints, request validation

| Service
| Business logic, state machine, workflow

| Repository
| Database access via Spring Data JPA

| Security
| Authentication, authorization, audit
|===

== API Reference

=== Submit CSR

`POST /api/ra/requests`

[source,json]
----
{
  "subjectDN": "CN=example.com,O=RipplesBit",
  "csrPem": "-----BEGIN CERTIFICATE REQUEST-----...",
  "keyAlgorithm": "RSA",
  "keySize": 2048
}
----

WARNING: The CSR PEM must be Base64 encoded and include
the BEGIN/END markers.

=== Approve CSR

`POST /api/ra/requests/{id}/approve`

TIP: In DUAL mode, use `/review` (Maker) followed by
`/accept` (Checker) instead of `/approve`.
```

---

## 12. Best Practices

| # | Practice | Reason |
|---|---|---|
| 1 | One sentence per line | Better Git diffs — each line change is one sentence |
| 2 | Use includes for large docs | Maintainable, reusable chapters |
| 3 | Put images in `images/` directory | Clean project structure |
| 4 | Use attributes for versions | `{project-version}` — update once, reflects everywhere |
| 5 | Add `:toc: left` | Auto TOC makes navigation easy |
| 6 | Use admonitions | NOTE, TIP, WARNING guide the reader |
| 7 | Include real code files | `include::src/App.java[]` — always up to date |
| 8 | Version docs with code | Same Git repo, same branch, same PR |
| 9 | Automate builds in CI | Docs never go stale |
| 10 | Use custom PDF themes | Professional branded output |

---

*End of Document*
