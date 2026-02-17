from uuid import uuid4


def test_register_and_login(client):
    email = f"user_{uuid4().hex}@example.com"
    username = f"user_{uuid4().hex[:8]}"
    password = "Password123!"

    register_response = client.post(
        "/auth/register",
        json={"email": email, "username": username, "password": password, "role": "ADMIN"},
    )
    assert register_response.status_code == 200
    assert "access_token" in register_response.json()

    login_response = client.post(
        "/auth/login",
        data={"username": email, "password": password},
    )
    assert login_response.status_code == 200
    assert "access_token" in login_response.json()
