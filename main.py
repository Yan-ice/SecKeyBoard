import time
from NFCutil import INS_END_WITH_CERT, INS_END_WITH_DH, INS_END_WITH_DH_SIGNATURE
from NFCutil import wait_for_card, send_select_aid, send_item
from session import get_session_info, init_platform_cert, new_session


def first_step(session_id):
    info = get_session_info(session_id)

    connection = info["connection"]
    # 读取测试证书
    cert_bytes = info["server_cert_der"]
    server_pub_bytes = info["server_pub_edch"]
    signature = info["server_pub_edch_signature"]

    send_item(connection, cert_bytes, INS_END_WITH_CERT)
    # 将EDCH公钥发送给安卓APP。
    send_item(connection, server_pub_bytes, INS_END_WITH_DH)
    
    send_item(connection, signature, INS_END_WITH_DH_SIGNATURE)

def run_reader():

    init_platform_cert()
    
    print("📡 Waiting for Android HCE device (Tap your phone)...")
    while True:
        connection = wait_for_card()
        session_id = send_select_aid(connection, 0x21)
        print(f"new session id: {session_id}")
        new_session(session_id, connection)
        first_step(session_id)
        
        time.sleep(5)
        
        # connection = wait_for_card()
        # continue_session(session_id, connection)
        # send_select_aid(connection, 0x20)
        # second_step(session_id)

if __name__ == "__main__":
    run_reader()

