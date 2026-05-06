'use strict';
/**
 * Converts ad-auth-brs.md → ad-auth-brs.docx using the docx npm package.
 * Parses the markdown structurally (headings, tables, lists, paragraphs).
 */
const fs   = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, Table, TableRow, TableCell,
  TextRun, HeadingLevel, AlignmentType, WidthType, BorderStyle,
  ShadingType, convertInchesToTwip, TableLayoutType,
} = require('./node_modules/docx');

const mdPath   = path.join(__dirname, 'ad-auth-brs.md');
const outPath  = path.join(__dirname, 'ad-auth-brs.docx');
const mdText   = fs.readFileSync(mdPath, 'utf8');
const lines    = mdText.split('\n');

// ── Colour palette ────────────────────────────────────────────────────────────
const HEADER_BG   = '1F3864';  // dark navy
const HEADER_FG   = 'FFFFFF';
const ALT_ROW_BG  = 'DCE6F1';  // light blue
const BORDER_CLR  = 'B8CCE4';

// ── Helpers ────────────────────────────────────────────────────────────────────
function borderDef(color = BORDER_CLR) {
  const b = { style: BorderStyle.SINGLE, size: 6, color };
  return { top: b, bottom: b, left: b, right: b };
}

function inlineRuns(text) {
  // Handle **bold**, *italic*, `code` inline
  const runs = [];
  const re = /(\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|([^*`]+))/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    if (m[2])      runs.push(new TextRun({ text: m[2], bold: true }));
    else if (m[3]) runs.push(new TextRun({ text: m[3], italics: true }));
    else if (m[4]) runs.push(new TextRun({ text: m[4], font: 'Courier New', size: 18 }));
    else if (m[5]) runs.push(new TextRun({ text: m[5] }));
  }
  return runs.length ? runs : [new TextRun({ text })];
}

function makeParagraph(text, opts = {}) {
  const stripped = text.replace(/^[-*]\s+/, '');
  return new Paragraph({
    children: inlineRuns(stripped),
    spacing: { after: 80 },
    ...opts,
  });
}

function headingLevel(depth) {
  const map = { 1: HeadingLevel.HEADING_1, 2: HeadingLevel.HEADING_2,
                3: HeadingLevel.HEADING_3, 4: HeadingLevel.HEADING_4 };
  return map[depth] || HeadingLevel.HEADING_4;
}

function cellText(text, bold = false, shading = null, fg = '000000') {
  const opts = { bold, color: fg, size: 18 };
  const cell = new TableCell({
    children: [new Paragraph({
      children: [new TextRun({ text: text.trim(), ...opts })],
      spacing: { before: 40, after: 40 },
    })],
    borders: borderDef(),
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    ...(shading ? { shading: { fill: shading, type: ShadingType.CLEAR, color: 'auto' } } : {}),
  });
  return cell;
}

function buildTable(headerCells, dataRows) {
  const colCount = headerCells.length;
  const colWidthPct = Math.floor(100 / colCount);

  const headerRow = new TableRow({
    children: headerCells.map(h => cellText(h, true, HEADER_BG, HEADER_FG)),
    tableHeader: true,
  });

  const bodyRows = dataRows.map((row, i) => {
    const bg = i % 2 === 1 ? ALT_ROW_BG : null;
    const paddedRow = [...row];
    while (paddedRow.length < colCount) paddedRow.push('');
    return new TableRow({
      children: paddedRow.map(c => cellText(c, false, bg)),
    });
  });

  return new Table({
    rows: [headerRow, ...bodyRows],
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    columnWidths: Array(colCount).fill(colWidthPct),
  });
}

// ── Parse markdown into docx elements ────────────────────────────────────────
const children = [];

// Cover page
children.push(
  new Paragraph({
    children: [new TextRun({ text: 'PKI-RA-BRS-001', bold: true, size: 28, color: HEADER_BG })],
    heading: HeadingLevel.TITLE,
    alignment: AlignmentType.CENTER,
    spacing: { after: 200 },
  }),
  new Paragraph({
    children: [new TextRun({ text: 'Baseline Requirements Specification', bold: true, size: 36 })],
    alignment: AlignmentType.CENTER,
    spacing: { after: 120 },
  }),
  new Paragraph({
    children: [new TextRun({ text: 'Active Directory Authentication — PKI-RA System', size: 26, italics: true })],
    alignment: AlignmentType.CENTER,
    spacing: { after: 600 },
  }),
);

let i = 0;

function parseTableBlock() {
  // collect rows until non-table line
  const rows = [];
  while (i < lines.length && lines[i].trim().startsWith('|')) {
    rows.push(lines[i]);
    i++;
  }
  if (rows.length < 2) return null;

  const parseRow = r => r.split('|').slice(1, -1).map(c => c.replace(/\*\*/g, '').replace(/`/g, '').trim());

  const header = parseRow(rows[0]);
  // rows[1] is separator — skip it
  const data   = rows.slice(2).map(parseRow);
  return buildTable(header, data);
}

while (i < lines.length) {
  const line = lines[i];

  // Blank line
  if (line.trim() === '') { i++; continue; }

  // Horizontal rule
  if (/^---+$/.test(line.trim())) { i++; continue; }

  // Heading
  const hMatch = line.match(/^(#{1,4})\s+(.*)/);
  if (hMatch) {
    const depth = hMatch[1].length;
    const text  = hMatch[2].replace(/\*\*/g, '');
    // Skip the very first h1 (document title — already in cover)
    if (!(depth === 1 && text.startsWith('Baseline Requirements'))) {
      children.push(new Paragraph({
        text,
        heading: headingLevel(depth),
        spacing: { before: depth <= 2 ? 300 : 160, after: 100 },
      }));
    }
    i++;
    continue;
  }

  // Table
  if (line.trim().startsWith('|')) {
    const tbl = parseTableBlock();
    if (tbl) { children.push(tbl); children.push(new Paragraph({ text: '', spacing: { after: 120 } })); }
    continue;
  }

  // List item
  if (/^[-*]\s+/.test(line)) {
    children.push(makeParagraph(line, {
      bullet: { level: 0 },
      spacing: { after: 60 },
    }));
    i++;
    continue;
  }

  // Fenced code block — skip
  if (line.trim().startsWith('```')) {
    i++;
    while (i < lines.length && !lines[i].trim().startsWith('```')) i++;
    i++;
    continue;
  }

  // Normal paragraph
  if (line.trim()) {
    children.push(makeParagraph(line));
  }
  i++;
}

// ── Build and save document ───────────────────────────────────────────────────
const doc = new Document({
  creator: 'PKI-RA Project Team',
  title: 'Baseline Requirements Specification — AD Authentication',
  description: 'PKI-RA-BRS-001 v1.0',
  styles: {
    default: {
      document: { run: { font: 'Calibri', size: 22 } },
    },
    paragraphStyles: [
      {
        id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal',
        run: { bold: true, size: 32, color: HEADER_BG },
        paragraph: { spacing: { before: 360, after: 120 } },
      },
      {
        id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal',
        run: { bold: true, size: 26, color: '2E74B5' },
        paragraph: { spacing: { before: 240, after: 80 } },
      },
      {
        id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal',
        run: { bold: true, size: 22, color: '2E74B5' },
      },
    ],
  },
  sections: [{ properties: { page: { margin: {
    top:    convertInchesToTwip(1),
    bottom: convertInchesToTwip(1),
    left:   convertInchesToTwip(1.2),
    right:  convertInchesToTwip(1.2),
  }}}, children }],
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outPath, buf);
  console.log(`docx written: ${outPath} (${(buf.length / 1024).toFixed(1)} KB)`);
}).catch(e => { console.error(e); process.exit(1); });
