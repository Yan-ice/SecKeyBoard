import uuid
from smartcard.System import readers
from smartcard.util import toHexString

from cryptography import x509
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import ec, padding
from cryptography.hazmat.primitives.serialization import load_pem_private_key

    # payload = {
    #     "session_id": session_id,
    #     "server_pub_x509": server_pub_bytes,
    #     "server_pub_signature": signature,
    #     "server_cert_der": cert_bytes,
    #     "curve": "secp256r1"
    # }

SESSION_INFO = {}

cert_bytes = None
private_key = None

def init_platform_cert():
    # 读取测试证书
    with open("test_cert.der", "rb") as f:
        global cert_bytes
        cert_bytes = f.read()

    with open("private_key.pem", "rb") as f:
        global private_key
        private_key = load_pem_private_key(f.read(), password=None)

    print("certificate load success.")

    return cert_bytes


def new_session(session_id, connection):

    # 生成临时 ECDH 密钥对
    server_ecdh_private = ec.generate_private_key(ec.SECP256R1())
    server_ecdh_public = server_ecdh_private.public_key()

    # 导出ECDH公钥为字节 (X.509)
    server_pub_bytes = server_ecdh_public.public_bytes(
	serialization.Encoding.DER,
	serialization.PublicFormat.SubjectPublicKeyInfo
    )

    # 用证书私钥对临时公钥签名
    signature = private_key.sign(
        server_pub_bytes,
        padding.PKCS1v15(),     # 或者 padding.PSS(...) 更安全
        hashes.SHA256()
    )

    payload = {
        "session_id": session_id,
        "connection": connection,
        "server_priv_edch_key": server_ecdh_private,
        "server_pub_edch": server_pub_bytes,
        "server_pub_edch_signature": signature,
        "server_cert_der": cert_bytes,
        "curve": "secp256r1"
    }

    SESSION_INFO[session_id] = payload

    return session_id

def continue_session(session_id, connection):
    if session_id not in SESSION_INFO:
        new_session(session_id, connection)
    else:  
        SESSION_INFO[session_id]["connection"] = connection

def kill_session(session_id):
    if session_id in SESSION_INFO:
        SESSION_INFO[session_id]["connection"].disconnect()

def get_session_info(session_id):
    return SESSION_INFO[session_id]

def get_session_connection(session_id):
    return SESSION_INFO[session_id]["connection"]
