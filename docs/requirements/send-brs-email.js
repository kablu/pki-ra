'use strict';
const nodemailer = require('./node_modules/nodemailer');
const path       = require('path');
const fs         = require('fs');

const GMAIL_USER = process.env.GMAIL_USER;
const GMAIL_PASS = process.env.GMAIL_PASS;
const TO         = 'kablumndl546@gmail.com';
const DOCX_PATH  = path.join(__dirname, 'ad-auth-brs.docx');

if (!GMAIL_USER || !GMAIL_PASS) {
  console.error('Set GMAIL_USER and GMAIL_PASS environment variables.');
  process.exit(1);
}

const transporter = nodemailer.createTransport({
  service: 'gmail',
  auth: { user: GMAIL_USER, pass: GMAIL_PASS },
});

const mailOptions = {
  from: `"PKI-RA Project" <${GMAIL_USER}>`,
  to: TO,
  subject: '[PKI-RA] Baseline Requirements Specification — AD Authentication (PKI-RA-BRS-001 v1.0)',
  text: [
    'Hi,',
    '',
    'Please find attached the Baseline Requirements Specification (BRS) for the',
    'Active Directory Authentication component of the PKI-RA system.',
    '',
    'Document: PKI-RA-BRS-001',
    'Title:    Baseline Requirements Specification — AD Authentication',
    'Version:  1.0',
    'Status:   Baseline',
    'Date:     2026-04-27',
    '',
    'The document covers:',
    '  • Sections 1–6:  Introduction, Scope, Stakeholders, Definitions, Assumptions, Constraints',
    '  • Section 7–8:   Business Requirements (KPIs) + Stakeholder Requirements',
    '  • Section 9:     35 Functional Requirements (Auth, Role Mapping, Sessions, JIT, Audit, Admin)',
    '  • Sections 10–13: NFRs, Security Requirements, Integration, Compliance',
    '  • Sections 14–17: UI Requirements, Data Rules, Operational, Transition Plan',
    '  • Sections 18–21: Acceptance Criteria, Out of Scope, Risk Register, Glossary',
    '',
    'Regards,',
    'PKI-RA Project Team',
  ].join('\n'),
  attachments: [
    {
      filename: 'PKI-RA-BRS-001-AD-Authentication-v1.0.docx',
      content:  fs.readFileSync(DOCX_PATH),
      contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    },
  ],
};

transporter.sendMail(mailOptions, (err, info) => {
  if (err) { console.error('Send failed:', err.message); process.exit(1); }
  console.log('Email sent:', info.messageId);
  console.log('Response: ', info.response);
});
