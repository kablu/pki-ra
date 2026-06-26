#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <openssl/ssl.h>
#include <openssl/x509.h>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/err.h>

// ============================================================================
// Sample PKI/Crypto C code — INTENTIONALLY contains security issues
// for testing the Gemini review skill
// ============================================================================

// ISSUE 1: Weak random — using rand() instead of CSPRNG
void generate_session_id(char *buf, int len) {
    srand(time(NULL));  // predictable seed
    for (int i = 0; i < len; i++) {
        buf[i] = 'A' + (rand() % 26);
    }
    buf[len] = '\0';  // ISSUE 2: off-by-one buffer overflow
}

// ISSUE 3: No certificate hostname verification
int connect_tls(const char *hostname, int port) {
    SSL_CTX *ctx = SSL_CTX_new(SSLv23_method());  // ISSUE 4: deprecated API
    SSL_CTX_set_verify(ctx, SSL_VERIFY_NONE, NULL); // ISSUE 5: no cert verify!

    SSL *ssl = SSL_new(ctx);
    BIO *bio = BIO_new_ssl_connect(ctx);
    BIO_set_conn_hostname(bio, hostname);

    if (BIO_do_connect(bio) <= 0) {
        // ISSUE 6: missing BIO_free, SSL_free, CTX_free — resource leak
        return -1;
    }

    // ISSUE 7: no hostname verification after handshake
    // ISSUE 8: no certificate chain validation
    return 0;
    // ISSUE 9: no cleanup of ssl, bio, ctx
}

// ISSUE 10: Timing attack — non-constant-time comparison
int verify_hmac(const unsigned char *expected, const unsigned char *actual, int len) {
    return memcmp(expected, actual, len) == 0;  // timing side-channel!
}

// ISSUE 11: Key material not zeroed after use
int decrypt_private_key(const char *keyfile, char *password) {
    FILE *fp = fopen(keyfile, "r");
    EVP_PKEY *pkey = PEM_read_PrivateKey(fp, NULL, NULL, password);
    fclose(fp);

    if (pkey == NULL) {
        return -1;  // ISSUE 12: password still in memory
    }

    // ... use the key ...

    EVP_PKEY_free(pkey);
    // ISSUE 13: password buffer not zeroed — key material leak
    return 0;
}

// ISSUE 14: Buffer overflow in ASN.1 parsing
int parse_certificate_extension(const unsigned char *data, int data_len) {
    unsigned char oid[64];
    int oid_len = data[1];  // ISSUE 15: no bounds check on length field

    // ISSUE 16: oid_len could be > 64, causing stack buffer overflow
    memcpy(oid, data + 2, oid_len);

    // ISSUE 17: integer overflow if oid_len is close to INT_MAX
    char *value = malloc(oid_len + 1);
    if (!value) return -1;

    memcpy(value, data + 2 + oid_len, data_len - 2 - oid_len);
    // ISSUE 18: no check that data_len > 2 + oid_len

    free(value);
    return 0;
}

// ISSUE 19: ECB mode — patterns visible in ciphertext
int encrypt_data(const unsigned char *key, const unsigned char *plaintext,
                 int plaintext_len, unsigned char *ciphertext) {
    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    unsigned char iv[16] = {0};  // ISSUE 20: zero IV — terrible!

    EVP_EncryptInit_ex(ctx, EVP_aes_256_ecb(), NULL, key, iv);  // ECB mode!
    // ISSUE 21: no error check on EVP_EncryptInit_ex

    int len;
    EVP_EncryptUpdate(ctx, ciphertext, &len, plaintext, plaintext_len);
    EVP_EncryptFinal_ex(ctx, ciphertext + len, &len);

    EVP_CIPHER_CTX_free(ctx);
    return len;
}

// ISSUE 22: PKCS#11 session not closed properly
int sign_with_hsm(unsigned char *data, int data_len, unsigned char *sig) {
    CK_SESSION_HANDLE session;
    CK_RV rv = C_OpenSession(0, CKF_SERIAL_SESSION, NULL, NULL, &session);

    if (rv != CKR_OK) return -1;

    // ... sign data ...

    // ISSUE 23: missing C_CloseSession — session leak
    // ISSUE 24: missing C_Finalize
    return 0;
}

// ISSUE 25: Command injection
int check_certificate_revocation(const char *serial) {
    char cmd[256];
    // ISSUE 26: serial from user input — command injection!
    sprintf(cmd, "openssl crl -in crl.pem -text | grep %s", serial);
    system(cmd);
    return 0;
}
