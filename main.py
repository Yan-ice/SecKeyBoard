import time
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec, padding
from cryptography.hazmat.primitives.serialization import load_der_public_key
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from smartcard.Exceptions import CardConnectionException, NoCardException
from smartcard.util import toHexString
from CryptoUtil import parse_cert_and_print_attestation
from NFCutil import RECV_AES_PWD, RECV_CLIENT_CERT, RECV_CLIENT_DH, RECV_CLIENT_DH_SIGNATURE, SEND_CERT, SEND_DH, SEND_DH_SIGNATURE, STATE_END, recv_item, send_apdu, send_state
from NFCutil import wait_for_card, send_select_aid, send_item
from session import continue_session, get_session_info, init_platform_cert, kill_session, new_session
import base64

from timer import Timer



def first_step(session_id):

    info = get_session_info(session_id)
    connection = info["connection"]

    # 读取测试证书
    cert_bytes = info["server_cert_der"]
    server_pub_bytes = info["server_pub_edch"]
    signature = info["server_pub_edch_signature"]

    with Timer("一阶段NFC"):
        print("sending cert")
        send_item(connection, cert_bytes, SEND_CERT)
        
        # 将EDCH公钥发送给安卓APP。
        print(f"sending ECDH pub key (also challenge)")
        
        send_item(connection, server_pub_bytes, SEND_DH)
        
        print("sending ECDH pub key signature")
        send_item(connection, signature, SEND_DH_SIGNATURE)

def second_step(session_id):

    info = get_session_info(session_id)
    connection = info["connection"]

    server_ecdh_private = info["server_priv_edch_key"]
    server_pub_bytes = info["server_pub_edch"]
    
    with Timer("二阶段NFC"):
        client_edch_cert = recv_item(connection, RECV_CLIENT_CERT)
        print(f"成功接收来自客户端的证书")

        client_edch_public_bytes = recv_item(connection, RECV_CLIENT_DH)
        client_edch_signature = recv_item(connection, RECV_CLIENT_DH_SIGNATURE)
        print(f"成功接收来自客户端的ECDH公钥和签名")

        aes_enc_data = recv_item(connection, RECV_AES_PWD)
        print(f"成功接收来自客户端的AES密文")

        send_state(connection, STATE_END)

    cert, chal = parse_cert_and_print_attestation(client_edch_cert)
    print(f"客户端证书解析成功")

    if chal == server_pub_bytes:
        print(f"证书挑战值比对成功")
    else:
        print(f"证书挑战值比对失败")

    # TODO: verify client_edch_signature
    client_auth_pub_key = cert.public_key()

    client_auth_pub_key.verify(
            bytes(client_edch_signature),
            bytes(client_edch_public_bytes),
            padding.PKCS1v15(),
            hashes.SHA256()
    )
    print(f"客户端签名验证成功")

    # 思路：client_edch_cert是客户端keystore证书，包含身份公钥，且该公钥由platform证书颁发
    # edch_public_bytes是客户端ECDH公钥，被keystore证书签名得到client_edch_signature

    # 所以我们要从client_edch_cert中提取身份公钥，并验证client_edch_signature确认client_edch_public_bytes的完整性

    client_ecdh_public = load_der_public_key(client_edch_public_bytes)
    # TODO: use cli key to calculate AES key.

    shared_secret = server_ecdh_private.exchange(ec.ECDH(), client_ecdh_public)

    # 5. 从 shared secret 派生出 AES key (推荐用 HKDF)
    derived_aes_key = HKDF(
            algorithm=hashes.SHA256(),
            length=32,   # 32字节 = AES-256，也可改成16字节=AES-128
            salt=None,
            info=b'handshake data',
    ).derive(shared_secret)
        
    iv = "abcdef1234567890".encode("utf-8")

    cipher = AES.new(derived_aes_key, AES.MODE_CBC, iv)
    # 解密并去除填充
    decrypted = unpad(cipher.decrypt(aes_enc_data), AES.block_size)
    print(f"成功解密来自客户端的密文: {decrypted}")

def run_reader():

    init_platform_cert()

    phase = 1

    print("📡 Waiting for Android HCE device (Tap your phone)...")

    session_id = 10

    while True:
        if phase == 1:
            while phase == 1:
                time.sleep(1)
                print("📡 Waiting for Phase 1 (Tap your phone)...")
                try:
                    connection = wait_for_card()
                    print(f"new session id: {session_id}")
                    new_session(session_id, connection)
                    first_step(session_id)
                    phase = 2
                except CardConnectionException:
                    print(f"HCE无响应。重新连接中......")
                except NoCardException:
                    print(f"无法检测到卡。重新连接中......")
                kill_session(session_id)
        elif phase == 2:
            while phase == 2:
                time.sleep(1)
                print("📡 Waiting for Phase 2 (Tap your phone)...")
                try:
                    connection = wait_for_card()
                    session_id = 10
                    continue_session(session_id, connection)
                    second_step(session_id)
                    connection.disconnect()
                    phase = 1
                except CardConnectionException:
                    print(f"HCE无响应。重新连接中......")
                except NoCardException:
                    print(f"无法检测到卡。重新连接中......")
                kill_session(session_id)
        time.sleep(5)

if __name__ == "__main__":
    run_reader()

