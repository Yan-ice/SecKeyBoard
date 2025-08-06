from smartcard.System import readers
from smartcard.util import toHexString
from cryptography.hazmat.primitives.serialization import load_der_public_key
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
import os
import time

# APDU 命令定义
SELECT_AID = [0x00, 0xA4, 0x04, 0x00, 0x05, 0xF0, 0x20, 0x02, 0x05, 0x20]
GET_RSA_PUBKEY_PART1 = [0x00, 0xB0, 0x00, 0x00, 0xFF]
GET_RSA_PUBKEY_PART2 = [0x00, 0xB1, 0x00, 0x00, 0xFF]
SEND_AES_KEY_PART1 = 0xD0
SEND_AES_KEY_PART2 = 0xD1
SEND_ENCRYPTED_DATA = 0xD2
RECV_ENCRYPTED_DATA = 0xD3

def wait_for_card(reader):
    connection = reader.createConnection()
    while True:
        try:
            connection.connect()
            print("Phone connected (HCE card detected)")
            return connection
        except Exception:
            time.sleep(0.1)

def send_apdu(connection, apdu):
    # print(f"send: {toHexString(apdu)}")
    response, sw1, sw2 = connection.transmit(apdu)
    # print(f"receive: SW1={sw1:02X} SW2={sw2:02X} {toHexString(response)}")
    if [sw1, sw2] != [0x90, 0x00]:
        raise Exception(f"APDU failed: SW1={sw1:02X} SW2={sw2:02X}")
    return bytes(response) if response else b""


def aes_receive(connection, aes_key):
    encrypted_chunks = []
    while True:
        # 发送 D3 指令请求下一段加密数据
        apdu = [0x00, RECV_ENCRYPTED_DATA, 0x00, 0x00, 0x00]
        response = send_apdu(connection, apdu)
        if len(response) == 0:
            # print("Received empty packet, end of encrypted data.")
            break
        encrypted_chunks.append(response)

    encrypted_data_full = b"".join(encrypted_chunks)
    print(f"Encrypted data received: {len(encrypted_data_full)} bytes")

    if len(encrypted_data_full) < 12:
        print("Error: Encrypted data too short to contain IV.")
        return None

    iv = encrypted_data_full[:12]
    ciphertext_with_tag = encrypted_data_full[12:]

    try:
        aesgcm = AESGCM(aes_key)
        decrypted = aesgcm.decrypt(iv, ciphertext_with_tag, None)
        print(f"Decrypted data: {decrypted.decode('utf-8')}")
        return decrypted
    except Exception as e:
        print(f"Decryption failed: {e}")
        return None


def aes_send(connection, aes_key, iv, data):
    aesgcm = AESGCM(aes_key)
    encrypted = aesgcm.encrypt(iv, data.encode(), None)  # ciphertext + tag
    ciphertext, tag = encrypted[:-16], encrypted[-16:]
    payload = iv + ciphertext + tag
    
    print(f"Sending encrypted password ({len(payload)} bytes)")
    apdu3 = [0x00, SEND_ENCRYPTED_DATA, 0x00, 0x00, len(payload)] + list(payload)
    send_apdu(connection, apdu3)
    
def aes_gen(connection, rsa_pubkey_der):
    # 1. 生成 AES 密钥
    aes_key = AESGCM.generate_key(bit_length=128)

    # 2. 用 RSA 公钥加密 AES 密钥
    rsa_pubkey = load_der_public_key(rsa_pubkey_der)
    aes_key_encrypted = rsa_pubkey.encrypt(
        aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )

    # 3. AES密钥分两段发送
    half = len(aes_key_encrypted) // 2
    aes_key_part1 = aes_key_encrypted[:half]
    aes_key_part2 = aes_key_encrypted[half:]

    print(f"Sending AES key part 1 ({len(aes_key_part1)} bytes)")
    apdu1 = [0x00, SEND_AES_KEY_PART1, 0x00, 0x00, len(aes_key_part1)] + list(aes_key_part1)
    send_apdu(connection, apdu1)

    print(f"Sending AES key part 2 ({len(aes_key_part2)} bytes)")
    apdu2 = [0x00, SEND_AES_KEY_PART2, 0x00, 0x00, len(aes_key_part2)] + list(aes_key_part2)
    send_apdu(connection, apdu2)
    return aes_key
    
def main():
    rlist = readers()
    if not rlist:
        print("❌ No smart card readers found.")
        return

    reader = rlist[0]
    print(f"✅ Using reader: {reader}")

    try:
            connection = wait_for_card(reader)

            # Step 1: 选择 AID
            send_apdu(connection, SELECT_AID)

            # Step 2: 读取公钥两段
            pubkey_part1 = send_apdu(connection, GET_RSA_PUBKEY_PART1)
            print(f"Received public key part 1 ({len(pubkey_part1)} bytes)")
            pubkey_part2 = send_apdu(connection, GET_RSA_PUBKEY_PART2)
            print(f"Received public key part 2 ({len(pubkey_part2)} bytes)")

            public_key = pubkey_part1 + pubkey_part2
            print(f"Combined public key length: {len(public_key)} bytes")

            # Step 3~4: 发送加密 AES 密钥和加密密码
            aes_key = aes_gen(connection, public_key)
            
            iv = bytes.fromhex("a1b2c3d4e5f60718293a4b5c")

            # aes_send(connection, aes_key, iv, "SuperSecret123")
            
            pwd = aes_receive(connection, aes_key)
            print(f"=====================================")
            print(f"Received the password success: {pwd}")
            print(f"=====================================")
    except Exception as e:
        print(f"⚠️ Error: {e}")
        time.sleep(1)

if __name__ == "__main__":
    main()

