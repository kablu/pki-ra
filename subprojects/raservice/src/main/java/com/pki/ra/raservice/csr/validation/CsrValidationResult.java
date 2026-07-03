package com.pki.ra.raservice.csr.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result returned by {@link CsrValidatorService}.
 *
 * <p>A result is either PASSED (no hard errors) or FAILED (at least one error).
 * Warnings are always collected regardless of pass/fail.
 *
 * <p>Fields extracted from the CSR during validation are stored here so the
 * caller does not need to re-parse the CSR a second time.
 */
public final class CsrValidationResult {

    // ── Outcome ───────────────────────────────────────────────────────────────

    private final boolean passed;
    private final List<String> errors;
    private final List<String> warnings;

    // ── Extracted CSR metadata ────────────────────────────────────────────────

    private final String subjectDn;
    private final String keyAlgorithm;   // "RSA" | "EC" | "EdDSA"
    private final int    keySize;         // bits (RSA/EC key length, 0 for Ed25519)
    private final String signatureAlgorithm;
    private final String subjectAltNames; // comma-separated "DNS:api.acme.com, ..."
    private final String commonName;      // raw CN value from Subject DN

    private CsrValidationResult(Builder b) {
        this.passed             = b.errors.isEmpty();
        this.errors             = Collections.unmodifiableList(new ArrayList<>(b.errors));
        this.warnings           = Collections.unmodifiableList(new ArrayList<>(b.warnings));
        this.subjectDn          = b.subjectDn;
        this.keyAlgorithm       = b.keyAlgorithm;
        this.keySize            = b.keySize;
        this.signatureAlgorithm = b.signatureAlgorithm;
        this.subjectAltNames    = b.subjectAltNames;
        this.commonName         = b.commonName;
    }

    public static Builder builder() { return new Builder(); }

    public boolean isPassed()               { return passed; }
    public List<String> getErrors()         { return errors; }
    public List<String> getWarnings()       { return warnings; }
    public String getSubjectDn()            { return subjectDn; }
    public String getKeyAlgorithm()         { return keyAlgorithm; }
    public int getKeySize()                 { return keySize; }
    public String getSignatureAlgorithm()   { return signatureAlgorithm; }
    public String getSubjectAltNames()      { return subjectAltNames; }
    public String getCommonName()           { return commonName; }

    public static final class Builder {
        private final List<String> errors   = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private String subjectDn;
        private String keyAlgorithm;
        private int    keySize;
        private String signatureAlgorithm;
        private String subjectAltNames;
        private String commonName;

        public Builder addError(String code, String detail) {
            errors.add("[" + code + "] " + detail);
            return this;
        }

        public Builder addWarning(String code, String detail) {
            warnings.add("[" + code + "] " + detail);
            return this;
        }

        public Builder subjectDn(String v)          { subjectDn = v;          return this; }
        public Builder keyAlgorithm(String v)       { keyAlgorithm = v;       return this; }
        public Builder keySize(int v)               { keySize = v;            return this; }
        public Builder signatureAlgorithm(String v) { signatureAlgorithm = v; return this; }
        public Builder subjectAltNames(String v)    { subjectAltNames = v;    return this; }
        public Builder commonName(String v)         { commonName = v;         return this; }

        public CsrValidationResult build()          { return new CsrValidationResult(this); }
    }
}
