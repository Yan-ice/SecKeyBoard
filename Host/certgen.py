##############################
#
# This script is used to generate cert.pem, cert.der, priv_key.pem
#
##############################

from cryptography import x509
from cryptography.x509.oid import ObjectIdentifier, NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
import datetime


def generate_cert_with_ext():

    key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )

    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CN"),
        x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, "Shanghai"),
        x509.NameAttribute(NameOID.LOCALITY_NAME, "Shanghai"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "TestCompany"),
        x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "Dev"),
        x509.NameAttribute(NameOID.COMMON_NAME, "example.com"),
    ])

    # 自定义 OID
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(datetime.datetime.utcnow())
        .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=365))
        .add_extension( # input format: 00=num, 01=ABCDEFG, 02=letter
            x509.UnrecognizedExtension(ObjectIdentifier("1.2.3.4.1"), (0x66667).to_bytes(4, "big")),
            critical=False,
        )
        .add_extension( # nonce for DH
            x509.UnrecognizedExtension(ObjectIdentifier("1.2.3.4.2"), (0x12345).to_bytes(4, "big")),
            critical=False,
        )
        .sign(private_key=key, algorithm=hashes.SHA256())
    )
    with open("private_key.pem", "wb") as f:
        f.write(
            key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.TraditionalOpenSSL,
                encryption_algorithm=serialization.NoEncryption(),
            )
        )

    with open("test_cert.pem", "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))

    with open("test_cert.der", "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.DER))

    print("Generated: private_key.pem, test_cert.pem, test_cert.der")


if __name__ == "__main__":
    generate_cert_with_ext()

