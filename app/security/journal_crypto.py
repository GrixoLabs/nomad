"""AES-GCM masking for journal bodies at rest.

Only this module (and the developer decrypt script) should reverse ciphertext.
"""

from __future__ import annotations

import base64
import hashlib
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from app.config.settings import get_settings


def _key_bytes() -> bytes:
    settings = get_settings()
    raw = (settings.journal_mask_key or settings.jwt_secret or "").encode("utf-8")
    # Derive a stable 32-byte key from whatever secret is configured.
    return hashlib.sha256(raw).digest()


def encrypt_journal(plaintext: str) -> tuple[bytes, bytes]:
    nonce = os.urandom(12)
    ct = AESGCM(_key_bytes()).encrypt(nonce, plaintext.encode("utf-8"), None)
    return ct, nonce


def decrypt_journal(ciphertext: bytes, nonce: bytes) -> str:
    pt = AESGCM(_key_bytes()).decrypt(nonce, ciphertext, None)
    return pt.decode("utf-8")


def decrypt_journal_b64(ciphertext_b64: str, nonce_b64: str) -> str:
    return decrypt_journal(
        base64.b64decode(ciphertext_b64),
        base64.b64decode(nonce_b64),
    )
