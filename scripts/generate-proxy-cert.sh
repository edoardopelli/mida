#!/usr/bin/env bash
# Genera un certificato self-signed per l'hostname di MIDA.
# Per la produzione sostituisci fullchain.pem e privkey.pem con un
# certificato reale (per esempio Let's Encrypt) e riavvia mida-proxy.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERT_DIR="${ROOT_DIR}/proxy/certs"
CERT_FILE="${CERT_DIR}/fullchain.pem"
KEY_FILE="${CERT_DIR}/privkey.pem"

mkdir -p "${CERT_DIR}"

if [[ -f "${CERT_FILE}" && -f "${KEY_FILE}" ]]; then
  echo "Certificati già presenti in ${CERT_DIR}"
  exit 0
fi

openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout "${KEY_FILE}" \
  -out "${CERT_FILE}" \
  -subj "/CN=maximilianclassroom.com" \
  -addext "subjectAltName=DNS:maximilianclassroom.com,DNS:www.maximilianclassroom.com,DNS:localhost,IP:127.0.0.1,IP:::1" \
  -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage=serverAuth"

chmod 600 "${KEY_FILE}"
echo "Certificato self-signed creato in ${CERT_DIR}"
echo "Sostituisci fullchain.pem e privkey.pem con un certificato reale prima di esporre MIDA su Internet."
