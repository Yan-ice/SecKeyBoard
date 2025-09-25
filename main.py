import time

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import load_der_public_key
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from smartcard.Exceptions import CardConnectionException
from NFCutil import RECV_CLIENT_KEY, SEND_CERT, SEND_DH, SEND_DH_SIGNATURE, recv_item, send_apdu
from NFCutil import wait_for_card, send_select_aid, send_item
from session import continue_session, get_session_info, init_platform_cert, kill_session, new_session
import base64



def first_step(session_id):
    info = get_session_info(session_id)

    connection = info["connection"]
    # 读取测试证书
    cert_bytes = info["server_cert_der"]
    server_pub_bytes = info["server_pub_edch"]
    signature = info["server_pub_edch_signature"]

    print("sending cert")
    send_item(connection, cert_bytes, SEND_CERT)
    
    # 将EDCH公钥发送给安卓APP。
    print("sending ECDH pub key")
    send_item(connection, server_pub_bytes, SEND_DH)
    
    print("sending ECDH pub key signature")
    send_item(connection, signature, SEND_DH_SIGNATURE)

def second_step(session_id):
    info = get_session_info(session_id)

    connection = info["connection"]
    server_ecdh_private = info["server_priv_edch_key"]

    client_edch_public_bytes = recv_item(connection, RECV_CLIENT_KEY)
    print("receive cli_dh_key byte array success: ", client_edch_public_bytes.hex())

    client_ecdh_public = load_der_public_key(client_edch_public_bytes)
    # TODO: use cli key to calculate AES key.

    print("the AES key: ?")
    # expected to have same AES key with the client
    
    # print(base64.b64encode(data).decode())

    pass

    shared_secret = server_ecdh_private.exchange(ec.ECDH(), client_ecdh_public)

    # 5. 从 shared secret 派生出 AES key (推荐用 HKDF)
    derived_aes_key = HKDF(
        algorithm=hashes.SHA256(),
        length=32,   # 32字节 = AES-256，也可改成16字节=AES-128
        salt=None,
        info=b'handshake data',
    ).derive(shared_secret)

    print(f"shared aes: {derived_aes_key.hex()}")

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
                kill_session(session_id)
        if phase == 2:
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
                kill_session(session_id)
        time.sleep(3)

        # print("📡 Testing...")
        # for ins in range(0,0xFF):
        #     for p1 in range(0, 0xFF):
        #         try:
        #             connection = wait_for_card()
        #             send_apdu(connection, 0xA4, b'1', p1)
        #             print(f"ins={ins},p1={p1} sucess with response")
        #         except CardConnectionException:
        #             pass

if __name__ == "__main__":
    run_reader()

