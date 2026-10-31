"""부하 테스트용 JWT(HS256)를 만든다. 서비스와 같은 JWT_SECRET(Base64)을 사용한다.

사용법: python3 make-token.py <memberId> [만료까지 초]
JWT_SECRET 환경 변수가 없으면 docker-compose.yml의 로컬 기본값을 쓴다.
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time

LOCAL_DEFAULT_SECRET = (
    "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW4tZ2VuZXJhdGlvbi1zaG91bGQtYmUtbG9uZy1lbm91Z2g="
)


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def make_token(member_id: str, ttl_seconds: int) -> str:
    key = base64.b64decode(os.environ.get("JWT_SECRET", LOCAL_DEFAULT_SECRET))
    now = int(time.time())
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    payload = b64url(json.dumps({"sub": member_id, "iat": now, "exp": now + ttl_seconds}).encode())
    signing_input = f"{header}.{payload}".encode()
    signature = b64url(hmac.new(key, signing_input, hashlib.sha256).digest())
    return f"{header}.{payload}.{signature}"


if __name__ == "__main__":
    if len(sys.argv) < 2 or not sys.argv[1].isdigit():
        sys.exit("usage: make-token.py <memberId> [ttlSeconds]")
    ttl = int(sys.argv[2]) if len(sys.argv) > 2 else 3600
    print(make_token(sys.argv[1], ttl))
