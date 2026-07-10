package com.pki.ra.common.model.enums;

/**
 * Certificate profile/type — determines which template
 * and validation rules apply to the CSR.
 */
public enum CsrProfile {

    TLS_SERVER("TLS Server certificate"),
    TLS_CLIENT("TLS Client authentication certificate"),
    CODE_SIGNING("Code Signing certificate"),
    SMIME("S/MIME Email certificate"),
    DOCUMENT_SIGNING("Document Signing certificate");

    private final String description;

    CsrProfile(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
