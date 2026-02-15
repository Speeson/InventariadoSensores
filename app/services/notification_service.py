import os
import smtplib
from email.message import EmailMessage
from app.models.enums import AlertType


def _get_env(name: str, default: str = "") -> str:
    value = os.getenv(name)
    return value if value is not None else default


def _smtp_configured() -> bool:
    return bool(_get_env("SMTP_HOST") and _get_env("SMTP_TO") and _get_env("SMTP_FROM"))


def send_low_stock_email(
    *,
    product_id: int,
    product_name: str | None = None,
    location: str,
    quantity: int,
    min_quantity: int,
) -> None:
    if not _smtp_configured():
        return

    host = _get_env("SMTP_HOST")
    port = int(_get_env("SMTP_PORT", "587"))
    user = _get_env("SMTP_USER")
    password = _get_env("SMTP_PASSWORD")
    from_addr = _get_env("SMTP_FROM")
    to_addr = _get_env("SMTP_TO")
    use_tls = _get_env("SMTP_USE_TLS", "true").lower() in ("1", "true", "yes")

    product_label = f"{product_name} ({product_id})" if product_name else f"Producto {product_id}"
    subject = f"Alerta de stock bajo: {product_label}"
    body = (
        "Se ha detectado stock bajo.\n\n"
        f"Producto: {product_label}\n"
        f"Ubicacion: {location}\n"
        f"Cantidad actual: {quantity}\n"
        f"Minimo configurado: {min_quantity}\n"
    )

    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = from_addr
    msg["To"] = to_addr
    msg.set_content(body)

    try:
        with smtplib.SMTP(host, port, timeout=10) as smtp:
            if use_tls:
                smtp.starttls()
            if user and password:
                smtp.login(user, password)
            smtp.send_message(msg)
    except Exception as exc:
        # Evita romper el job por problemas de email
        print(f"[notifications] Email error: {exc}")


def send_stock_alert_email(
    *,
    alert_type: AlertType,
    product_id: int,
    product_name: str | None = None,
    location: str,
    quantity: int,
    min_quantity: int,
) -> bool:
    if not _smtp_configured():
        return False

    host = _get_env("SMTP_HOST")
    port = int(_get_env("SMTP_PORT", "587"))
    user = _get_env("SMTP_USER")
    password = _get_env("SMTP_PASSWORD")
    from_addr = _get_env("SMTP_FROM")
    to_addr = _get_env("SMTP_TO")
    use_tls = _get_env("SMTP_USE_TLS", "true").lower() in ("1", "true", "yes")

    product_label = f"{product_name} ({product_id})" if product_name else f"Producto {product_id}"
    if alert_type == AlertType.OUT_OF_STOCK:
        subject = f"Alerta de stock agotado: {product_label}"
        intro = "Se ha detectado stock agotado."
    else:
        subject = f"Alerta de stock bajo: {product_label}"
        intro = "Se ha detectado stock bajo."

    body = (
        f"{intro}\n\n"
        f"Producto: {product_label}\n"
        f"Ubicacion: {location}\n"
        f"Cantidad actual: {quantity}\n"
        f"Minimo configurado: {min_quantity}\n"
    )

    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = from_addr
    msg["To"] = to_addr
    msg.set_content(body)

    try:
        with smtplib.SMTP(host, port, timeout=10) as smtp:
            if use_tls:
                smtp.starttls()
            if user and password:
                smtp.login(user, password)
            smtp.send_message(msg)
        return True
    except Exception as exc:
        print(f"[notifications] Email error: {exc}")
        return False
