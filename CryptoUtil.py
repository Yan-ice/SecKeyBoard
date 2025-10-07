
from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.x509.oid import ObjectIdentifier
from smartcard.util import toHexString

def load_cert_der(cert_bytes):
    cert = x509.load_der_x509_certificate(bytes(cert_bytes), default_backend())

def print_cert(cert):
    print("== 证书基本信息 ==")
    print("版本号:", cert.version)
    print("序列号:", cert.serial_number)
    print("签名算法:", cert.signature_algorithm_oid._name)
    print("签发者(Issuer):", cert.issuer.rfc4514_string())
    print("主题(Subject):", cert.subject.rfc4514_string())
    print("有效期: {} 至 {}".format(cert.not_valid_before, cert.not_valid_after))
    print("公钥类型:", cert.public_key().__class__.__name__)

    attestation_oid = ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17")
    try:
        attestation_ext = cert.extensions.get_extension_for_oid(attestation_oid)
        data = attestation_ext.value.value  # bytes类型
        print("Attestation Extension Data (raw bytes):", data)
    except x509.ExtensionNotFound:
        print("无 Android Key Attestation 扩展")

        # 文件: parse_android_attestation.py
from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.x509.oid import ObjectIdentifier
from pyasn1.type import univ, namedtype, tag
from pyasn1.codec.der import decoder as der_decoder
from pyasn1.codec.der import encoder as der_encoder
import binascii

# OID for Android Key Attestation
ATTESTATION_OID = ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17")

# --- ASN.1 spec (outer AttestationRecord) ---
# We keep AuthorizationList as generic SEQUENCE (we'll inspect raw tags inside)
class AuthorizationList(univ.Sequence):
    componentType = namedtype.NamedTypes(
        # We do not enumerate all possible labelled fields here.
        # Represent as open sequence — pyasn1 will still decode inner tagged elements
    )

class AttestationRecord(univ.Sequence):
    componentType = namedtype.NamedTypes(
        namedtype.NamedType('attestationVersion', univ.Integer()),
        namedtype.NamedType('attestationSecurityLevel', univ.Integer()),
        namedtype.NamedType('keymasterVersion', univ.Integer()),
        namedtype.NamedType('keymasterSecurityLevel', univ.Integer()),
        namedtype.NamedType('attestationChallenge', univ.OctetString()),
        namedtype.NamedType('uniqueId', univ.OctetString()),
        namedtype.NamedType('softwareEnforced', AuthorizationList()),
        namedtype.NamedType('teeEnforced', AuthorizationList()),
    )

# Helper: pretty print bytes / hex
def hx(b: bytes) -> str:
    return binascii.hexlify(b).decode()

# Parse a certificate (cryptography.x509.Certificate) and return attestation extension raw bytes
def get_attestation_ext_bytes(cert: x509.Certificate) -> bytes:
    try:
        ext = cert.extensions.get_extension_for_oid(ATTESTATION_OID)
    except x509.ExtensionNotFound:
        raise ValueError("Certificate has no Android attestation extension (OID 1.3.6.1.4.1.11129.2.1.17)")
    # ext.value is an UnrecognizedExtension; its .value is raw bytes
    # For cryptography >= some versions, ext.value.value holds the bytes
    val = getattr(ext.value, "value", None)
    if val is None:
        # fallback: try to take the extension's DER encoding (less common)
        raise ValueError("Couldn't extract raw extension bytes")
    return val

# Decode attestation record - returns pyasn1 object
def parse_attestation_record(ext_bytes: bytes):
    # 宽松解析，不使用asn1Spec
    asn1_obj, rest = der_decoder.decode(ext_bytes)
    if rest:
        print("Warning: leftover bytes after decoding:", len(rest))
    return asn1_obj


# Inspect AuthorizationList: print each child element's tag and raw bytes (so you can map tag numbers)
def inspect_authorization_list(auth_list_asn1):
    # auth_list_asn1 is decoded as a Sequence object; however, many authorization list fields
    # are encoded as context-specific tags rather than as named sequence components.
    # pyasn1 will put those tagged elements inside the sequence as elements with tagSet metadata.
    out = []
    for idx, comp in enumerate(auth_list_asn1):
        # Get tagSet: shows (tagClass, tagFormat, tagId) etc.
        tagset = comp.getTagSet()
        # Raw bytes of this component
        raw = der_encoder.encode(comp)
        out_item = {
            "index": idx,
            "tag_set": str(tagset),
            "raw_len": len(raw),
            "raw_hex": hx(raw),
            "pretty": repr(comp)
        }
        out.append(out_item)
    return out

# High-level function: takes cert bytes (DER or PEM) and prints parsed attestation
def parse_cert_and_print_attestation(cert_bytes: bytes, is_pem: bool = False):
    if is_pem:
        cert = x509.load_pem_x509_certificate(cert_bytes, default_backend())
    else:
        cert = x509.load_der_x509_certificate(cert_bytes, default_backend())

    print("Subject:", cert.subject)
    print("Issuer:", cert.issuer)
    print("Serial:", cert.serial_number)
    print("Public key type:", type(cert.public_key()))

    ext_bytes = get_attestation_ext_bytes(cert)
    print("Attestation extension raw bytes length:", len(ext_bytes))

    ar = parse_attestation_record(ext_bytes)

    print("attestationVersion:", int(ar[0]))
    print("attestationSecurityLevel:", int(ar[1]))
    print("keymasterVersion:", int(ar[2]))
    print("keymasterSecurityLevel:", int(ar[3]))
    print("attestationChallenge (hex):", toHexString(list(ar[4])))
    print("uniqueId (hex):", ar[5].asOctets().hex())

    # print("attestationVersion:", int(ar.getComponentByName('attestationVersion')))
    # asl = int(ar.getComponentByName('attestationSecurityLevel'))
    # print("attestationSecurityLevel (numeric):", asl, security_level_name(asl))
    # print("keymasterVersion:", int(ar.getComponentByName('keymasterVersion')))
    # ksl = int(ar.getComponentByName('keymasterSecurityLevel'))
    # print("keymasterSecurityLevel (numeric):", ksl, security_level_name(ksl))

    # chal = bytes(ar.getComponentByName('attestationChallenge'))
    # uid = bytes(ar.getComponentByName('uniqueId'))
    # print("attestationChallenge (hex):", hx(chal))
    # print("uniqueId (hex):", hx(uid))

    # software = ar.getComponentByName('softwareEnforced')
    # tee = ar.getComponentByName('teeEnforced')

    # print("\n-- softwareEnforced: %d child elements --" % len(software))
    # for item in inspect_authorization_list(software):
    #     print(item)

    # print("\n-- teeEnforced: %d child elements --" % len(tee))
    # for item in inspect_authorization_list(tee):
    #     print(item)
    return cert

# Helper: map security level numeric to name (common mapping in Android)
def security_level_name(n: int) -> str:
    if n == 0:
        return "Software"
    elif n == 1:
        return "TrustedEnvironment (TEE)"
    elif n == 2:
        return "StrongBox"
    else:
        return "Unknown"