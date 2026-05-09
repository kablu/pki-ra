// ============================================================================
// detail-component-design.docx generator
// Uses: docx@9.x  |  Node.js 24
// Run:  node generate.js
// ============================================================================
"use strict";

const fs   = require("fs");
const path = require("path");
const {
    Document, Packer, Paragraph, TextRun, HeadingLevel,
    Header, Footer, PageNumber, PageNumberElement, NumberFormat,
    AlignmentType, BorderStyle, WidthType,
    Table, TableRow, TableCell, ShadingType,
    TabStopPosition, TabStopType,
    convertInchesToTwip, PageBreak,
    UnderlineType, LineRuleType,
} = require("docx");

// ── Paths ─────────────────────────────────────────────────────────────────────
const DOCS = path.resolve(__dirname, "..");
const OUT  = path.resolve(__dirname, "..", "detail-component-design.docx");

// ── Colour palette ────────────────────────────────────────────────────────────
const C = {
    headerBg  : "1F3864",  // dark navy — header/footer background
    h1Fg      : "1F3864",  // dark navy — Heading 1 text
    h2Fg      : "2E5FA3",  // medium blue — Heading 2
    h3Fg      : "2E74B5",  // lighter blue — Heading 3
    tableBg   : "D6E4F0",  // light blue — table header row
    tableRowAlt: "EBF3FB", // alternate row tint
    codeBg    : "F4F4F4",  // light grey — code blocks
    accent    : "C00000",  // red — important labels
    bodyFg    : "1A1A1A",  // near-black body text
    white     : "FFFFFF",
};

// ── Fonts ─────────────────────────────────────────────────────────────────────
const FONT_BODY = "Calibri";
const FONT_CODE = "Courier New";
const FONT_HEAD = "Calibri Light";

// ── Sizes (half-points) ───────────────────────────────────────────────────────
const SZ = { h1: 36, h2: 28, h3: 24, body: 22, small: 18, code: 20 };

// ── Spacing helpers ───────────────────────────────────────────────────────────
const sp = (before = 0, after = 0, line, lineRule) => ({
    spacing: {
        before, after,
        ...(line ? { line, lineRule: lineRule || LineRuleType.AUTO } : {}),
    },
});

// ============================================================================
// PRIMITIVE BUILDERS
// ============================================================================

/** Plain text run */
function run(text, opts = {}) {
    return new TextRun({
        text,
        font: opts.font || FONT_BODY,
        size: opts.size || SZ.body,
        color: opts.color || C.bodyFg,
        bold: opts.bold || false,
        italics: opts.italics || false,
        underline: opts.underline ? { type: UnderlineType.SINGLE } : undefined,
        highlight: opts.highlight || undefined,
    });
}

/** Code-style run */
function codeRun(text) {
    return run(text, { font: FONT_CODE, size: SZ.code, color: "2B2B2B" });
}

// ============================================================================
// PARAGRAPH BUILDERS
// ============================================================================

/** Heading 1 — major section */
function h1(text) {
    return new Paragraph({
        children: [new TextRun({
            text,
            font: FONT_HEAD,
            size: SZ.h1,
            color: C.h1Fg,
            bold: true,
        })],
        heading: HeadingLevel.HEADING_1,
        ...sp(360, 120),
        border: { bottom: { color: C.h2Fg, size: 6, style: BorderStyle.SINGLE } },
    });
}

/** Heading 2 — subsection */
function h2(text) {
    return new Paragraph({
        children: [new TextRun({
            text,
            font: FONT_HEAD,
            size: SZ.h2,
            color: C.h2Fg,
            bold: true,
        })],
        heading: HeadingLevel.HEADING_2,
        ...sp(280, 80),
    });
}

/** Heading 3 — sub-subsection */
function h3(text) {
    return new Paragraph({
        children: [new TextRun({
            text,
            font: FONT_HEAD,
            size: SZ.h3,
            color: C.h3Fg,
            bold: true,
            italics: true,
        })],
        heading: HeadingLevel.HEADING_3,
        ...sp(200, 60),
    });
}

/** Body paragraph with inline bold/code parsing */
function body(line, extraOpts = {}) {
    const runs = parseInline(line);
    return new Paragraph({
        children: runs,
        ...sp(0, 100, 276, LineRuleType.AUTO),
        indent: extraOpts.indent ? { left: convertInchesToTwip(0.25) } : {},
    });
}

/** Bullet point */
function bullet(text, level = 0) {
    const indent = convertInchesToTwip(0.25 + level * 0.25);
    return new Paragraph({
        children: parseInline(text),
        bullet: { level },
        ...sp(0, 80),
        indent: { left: indent + convertInchesToTwip(0.25), hanging: convertInchesToTwip(0.25) },
    });
}

/** Code block line */
function codeLine(text) {
    return new Paragraph({
        children: [codeRun(text === "" ? " " : text)],
        ...sp(0, 0),
        shading: { type: ShadingType.SOLID, color: C.codeBg, fill: C.codeBg },
        indent: { left: convertInchesToTwip(0.25), right: convertInchesToTwip(0.25) },
    });
}

/** Empty spacer paragraph */
function spacer(pts = 80) {
    return new Paragraph({ children: [run("")], ...sp(0, pts) });
}

/** Horizontal rule (thin border paragraph) */
function rule() {
    return new Paragraph({
        children: [run("")],
        border: { bottom: { color: C.h2Fg, size: 4, style: BorderStyle.SINGLE } },
        ...sp(80, 80),
    });
}

/** Page break paragraph */
function pageBreak() {
    return new Paragraph({ children: [new PageBreak()] });
}

// ============================================================================
// INLINE PARSER  (**bold**, `code`, plain)
// ============================================================================
function parseInline(text) {
    const runs = [];
    // Pattern: **bold**, `code`, or plain text
    const re = /(\*\*(.+?)\*\*|`([^`]+)`)/g;
    let last = 0, m;
    while ((m = re.exec(text)) !== null) {
        if (m.index > last) runs.push(run(text.slice(last, m.index)));
        if (m[2] !== undefined) {
            runs.push(run(m[2], { bold: true }));
        } else {
            runs.push(codeRun(m[3]));
        }
        last = m.index + m[0].length;
    }
    if (last < text.length) runs.push(run(text.slice(last)));
    return runs.length ? runs : [run("")];
}

// ============================================================================
// TABLE BUILDER
// ============================================================================
function buildTable(headerCells, rows) {
    const colCount = headerCells.length;
    const pct = Math.floor(5000 / colCount); // evenly distribute in 50ths of pct

    const makeCell = (text, isHeader, altRow) => {
        const bg = isHeader ? C.tableBg : (altRow ? C.tableRowAlt : C.white);
        return new TableCell({
            children: [new Paragraph({
                children: parseInline(text || ""),
                ...sp(80, 80),
            })],
            shading: { type: ShadingType.SOLID, color: bg, fill: bg },
            margins: {
                top: 80, bottom: 80,
                left: convertInchesToTwip(0.08),
                right: convertInchesToTwip(0.08),
            },
            width: { size: pct, type: WidthType.PERCENTAGE },
        });
    };

    const headerRow = new TableRow({
        children: headerCells.map(c => makeCell(c, true, false)),
        tableHeader: true,
    });

    const dataRows = rows.map((row, ri) =>
        new TableRow({
            children: row.map(c => makeCell(c, false, ri % 2 === 1)),
        })
    );

    return new Table({
        rows: [headerRow, ...dataRows],
        width: { size: 100, type: WidthType.PERCENTAGE },
        margins: { top: 80, bottom: 80, left: 80, right: 80 },
    });
}

// ============================================================================
// MARKDOWN PARSER  →  docx elements
// Handles: # h1, ## h2, ### h3, | table |, ``` code ```, - bullet, body text
// ============================================================================
function parseMarkdown(md) {
    const lines  = md.split(/\r?\n/);
    const elems  = [];
    let inCode   = false;
    let inTable  = false;
    let tableHdr = [];
    let tableRows= [];

    const flushTable = () => {
        if (!inTable) return;
        elems.push(buildTable(tableHdr, tableRows));
        elems.push(spacer(120));
        inTable = false; tableHdr = []; tableRows = [];
    };

    for (let i = 0; i < lines.length; i++) {
        const raw  = lines[i];
        const line = raw.trimEnd();

        // ── Code fence ─────────────────────────────────────────────────────
        if (line.startsWith("```")) {
            if (!inCode) { flushTable(); inCode = true; elems.push(spacer(60)); }
            else          { inCode = false; elems.push(spacer(80)); }
            continue;
        }
        if (inCode) { elems.push(codeLine(line)); continue; }

        // ── Table row ──────────────────────────────────────────────────────
        if (line.startsWith("|")) {
            const cells = line.split("|").slice(1, -1).map(c => c.trim());
            if (cells.every(c => /^[-: ]+$/.test(c))) continue; // separator row
            if (!inTable) { tableHdr = cells; inTable = true; }
            else          { tableRows.push(cells); }
            continue;
        }
        flushTable();

        // ── Skip pure HR lines ─────────────────────────────────────────────
        if (/^---+$/.test(line.trim())) { elems.push(rule()); continue; }

        // ── Headings ───────────────────────────────────────────────────────
        if (/^# /.test(line))   { elems.push(pageBreak()); elems.push(h1(line.replace(/^# /, ""))); continue; }
        if (/^## /.test(line))  { elems.push(h2(line.replace(/^## /, ""))); continue; }
        if (/^### /.test(line)) { elems.push(h3(line.replace(/^### /, ""))); continue; }

        // ── Bullets ────────────────────────────────────────────────────────
        const bulletMatch = line.match(/^(\s*)[-*] (.+)/);
        if (bulletMatch) {
            const level = Math.floor(bulletMatch[1].length / 2);
            elems.push(bullet(bulletMatch[2], level));
            continue;
        }

        // ── Checkbox bullets ───────────────────────────────────────────────
        const cbMatch = line.match(/^(\s*)- \[([ x])\] (.+)/);
        if (cbMatch) {
            const done  = cbMatch[2] === "x";
            const level = Math.floor(cbMatch[1].length / 2);
            const txt   = (done ? "☑ " : "☐ ") + cbMatch[3];
            elems.push(bullet(txt, level));
            continue;
        }

        // ── Numbered list ──────────────────────────────────────────────────
        const numMatch = line.match(/^(\s*)\d+\. (.+)/);
        if (numMatch) { elems.push(bullet(numMatch[2], 0)); continue; }

        // ── Blank line ─────────────────────────────────────────────────────
        if (line.trim() === "") { elems.push(spacer(80)); continue; }

        // ── Body paragraph ─────────────────────────────────────────────────
        elems.push(body(line));
    }

    flushTable();
    return elems;
}

// ============================================================================
// COVER PAGE
// ============================================================================
function makeCoverPage() {
    return [
        spacer(1200),
        new Paragraph({
            children: [new TextRun({
                text: "RA AD CSR Service",
                font: FONT_HEAD, size: 56, color: C.h1Fg, bold: true,
            })],
            alignment: AlignmentType.CENTER,
            ...sp(0, 160),
        }),
        new Paragraph({
            children: [new TextRun({
                text: "Detail Component Design",
                font: FONT_HEAD, size: 44, color: C.h2Fg, bold: false,
            })],
            alignment: AlignmentType.CENTER,
            ...sp(0, 80),
        }),
        rule(),
        spacer(200),
        new Paragraph({
            children: [new TextRun({
                text: "PKI Registration Authority Platform",
                font: FONT_BODY, size: 24, color: C.h3Fg, italics: true,
            })],
            alignment: AlignmentType.CENTER,
            ...sp(0, 80),
        }),
        new Paragraph({
            children: [new TextRun({
                text: "Spring Boot 4  ·  Java 26  ·  Active Directory Authentication",
                font: FONT_BODY, size: 22, color: "555555",
            })],
            alignment: AlignmentType.CENTER,
            ...sp(0, 400),
        }),

        // Metadata table
        buildTable(["Field", "Value"], [
            ["Document Title",   "Detail Component Design — ra-ad-csr-service"],
            ["Project",          "pki-ra (Multi-Module)"],
            ["Module",           "ra-ad-csr-service"],
            ["Version",          "1.0 — Phase 1 Draft"],
            ["Status",           "For Review"],
            ["Date",             new Date().toLocaleDateString("en-IN", { day:"2-digit", month:"long", year:"numeric" })],
            ["Stack",            "Spring Boot 4 · Java 26 · Bouncy Castle 1.80"],
            ["Author",           "PKI-RA Development Team"],
            ["Reviewer",         "RA Architect / Security Lead"],
        ]),
        pageBreak(),
    ];
}

// ============================================================================
// TOC PAGE  (manual — Word TOC fields require complex XML; using styled list)
// ============================================================================
function makeTocPage(sections) {
    const items = [
        pageBreak(),
        h1("Table of Contents"),
        spacer(120),
    ];

    sections.forEach(({ num, title, subs }) => {
        items.push(new Paragraph({
            children: [
                new TextRun({ text: `${num}  `, font: FONT_BODY, size: SZ.body, bold: true, color: C.h2Fg }),
                new TextRun({ text: title,      font: FONT_BODY, size: SZ.body, bold: true, color: C.bodyFg }),
            ],
            ...sp(100, 40),
        }));
        (subs || []).forEach(sub => {
            items.push(new Paragraph({
                children: [
                    new TextRun({ text: `       ${sub.num}  `, font: FONT_BODY, size: SZ.small, color: C.h3Fg }),
                    new TextRun({ text: sub.title,             font: FONT_BODY, size: SZ.small, color: "444444" }),
                ],
                ...sp(20, 20),
            }));
        });
    });

    items.push(pageBreak());
    return items;
}

// ============================================================================
// HEADER & FOOTER
// ============================================================================
function makeHeader() {
    return new Header({
        children: [
            new Paragraph({
                children: [
                    new TextRun({ text: "RA AD CSR Service — Detail Component Design", font: FONT_BODY, size: SZ.small, color: C.white, bold: true }),
                    new TextRun({ text: "   |   PKI Registration Authority Platform",   font: FONT_BODY, size: SZ.small, color: "BBCCDD" }),
                ],
                shading: { type: ShadingType.SOLID, color: C.headerBg, fill: C.headerBg },
                ...sp(80, 80),
                indent: { left: convertInchesToTwip(0.15), right: convertInchesToTwip(0.15) },
            }),
        ],
    });
}

function makeFooter() {
    return new Footer({
        children: [
            new Paragraph({
                children: [
                    new TextRun({ text: "CONFIDENTIAL — Internal Use Only   |   ", font: FONT_BODY, size: SZ.small, color: C.white }),
                    new TextRun({ text: "Page ", font: FONT_BODY, size: SZ.small, color: "BBCCDD" }),
                    new PageNumberElement(PageNumber.CURRENT, { font: FONT_BODY, size: SZ.small, color: "BBCCDD" }),
                    new TextRun({ text: "   |   © PKI-RA Team " + new Date().getFullYear(), font: FONT_BODY, size: SZ.small, color: "BBCCDD" }),
                ],
                shading: { type: ShadingType.SOLID, color: C.headerBg, fill: C.headerBg },
                alignment: AlignmentType.RIGHT,
                ...sp(80, 80),
                indent: { left: convertInchesToTwip(0.15), right: convertInchesToTwip(0.15) },
            }),
        ],
    });
}

// ============================================================================
// MAIN  — read files, parse, build document
// ============================================================================
const DOC_FILES = [
    "01-analysis-and-plan.md",
    "02-component-design.md",
    "03-api-specification.md",
    "04-common-bean-reuse.md",
];

const TOC_STRUCTURE = [
    { num: "1", title: "Analysis and Plan", subs: [
        { num: "1.1", title: "Problem Statement" },
        { num: "1.2", title: "Actors and Stakeholders" },
        { num: "1.3", title: "Scope" },
        { num: "1.4", title: "Assumptions and Constraints" },
        { num: "1.5", title: "Risk Analysis" },
        { num: "1.6", title: "Technology Decisions" },
        { num: "1.7", title: "High-Level Architecture" },
        { num: "1.8", title: "Delivery Plan" },
    ]},
    { num: "2", title: "Component Design", subs: [
        { num: "2.1", title: "Module Structure" },
        { num: "2.2", title: "Component Descriptions" },
        { num: "2.3", title: "Security Design" },
        { num: "2.4", title: "Request / Response Flow" },
        { num: "2.5", title: "Configuration Reference" },
    ]},
    { num: "3", title: "API & Development Specification", subs: [
        { num: "3.1", title: "Gradle Module Setup" },
        { num: "3.2", title: "Application Entry Point" },
        { num: "3.3", title: "Controller Specification" },
        { num: "3.4", title: "DTO Specifications" },
        { num: "3.5", title: "Filter Specification" },
        { num: "3.6", title: "Validation Specifications" },
        { num: "3.7", title: "Service Specifications" },
        { num: "3.8", title: "Repository Specification" },
        { num: "3.9", title: "Exception Specifications" },
        { num: "3.10", title: "Security Config Specification" },
        { num: "3.11", title: "Test Specification" },
    ]},
    { num: "4", title: "Common Bean Reuse", subs: [
        { num: "4.1", title: "Overview" },
        { num: "4.2", title: "Bean Reuse Map" },
        { num: "4.3", title: "Beans NOT to Re-Declare" },
        { num: "4.4", title: "Dependency Declaration" },
        { num: "4.5", title: "Summary Table" },
    ]},
];

console.log("Reading source documents...");
const allElements = [
    ...makeCoverPage(),
    ...makeTocPage(TOC_STRUCTURE),
];

DOC_FILES.forEach(file => {
    const filePath = path.join(DOCS, file);
    console.log(`  Parsing ${file} ...`);
    const md = fs.readFileSync(filePath, "utf8");
    allElements.push(...parseMarkdown(md));
});

console.log("Building document...");
const doc = new Document({
    creator:     "PKI-RA Development Team",
    title:       "RA AD CSR Service — Detail Component Design",
    description: "Spring Boot 4 / Java 26 component design for AD-authenticated CSR submission",
    styles: {
        default: {
            document: {
                run: { font: FONT_BODY, size: SZ.body, color: C.bodyFg },
            },
        },
    },
    sections: [{
        properties: {
            page: {
                margin: {
                    top:    convertInchesToTwip(1.0),
                    bottom: convertInchesToTwip(1.0),
                    left:   convertInchesToTwip(1.0),
                    right:  convertInchesToTwip(1.0),
                },
            },
        },
        headers: { default: makeHeader() },
        footers: { default: makeFooter() },
        children: allElements,
    }],
});

console.log("Writing detail-component-design.docx ...");
Packer.toBuffer(doc).then(buf => {
    fs.writeFileSync(OUT, buf);
    const kb = (buf.length / 1024).toFixed(1);
    console.log(`\n✔  Done: ${OUT}`);
    console.log(`   File size: ${kb} KB`);
    console.log(`   Sections:  ${DOC_FILES.length}`);
    console.log(`   Elements:  ${allElements.length}`);
}).catch(err => {
    console.error("ERROR:", err.message);
    process.exit(1);
});
