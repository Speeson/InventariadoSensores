import csv
import io
from difflib import SequenceMatcher
from typing import Any

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile, status
from pydantic import BaseModel, Field
from sqlalchemy.exc import IntegrityError
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.cache.redis_cache import cache_invalidate_prefix
from app.db.deps import get_db
from app.models import ImportBatch, ImportError, ImportReview
from app.models.category import Category
from app.models.location import Location
from app.models.product import Product
from app.models.enums import Source, UserRole, AlertType, AlertStatus
from app.repositories import category_repo, product_repo, alert_repo
from app.services import inventory_service
from app.models.user import User


router = APIRouter(prefix="/imports", tags=["imports"])


class ImportErrorResponse(BaseModel):
    row_number: int
    error_code: str
    message: str


class ImportReviewResponse(BaseModel):
    row_number: int
    reason: str
    suggestions: dict | None = None


class ImportSummaryResponse(BaseModel):
    batch_id: int
    dry_run: bool
    total_rows: int
    ok_rows: int
    error_rows: int
    review_rows: int
    errors: list[ImportErrorResponse] = Field(default_factory=list)
    reviews: list[ImportReviewResponse] = Field(default_factory=list)


class ImportReviewItem(BaseModel):
    id: int
    batch_id: int
    row_number: int
    reason: str
    payload: dict
    suggestions: dict | None = None


class ImportReviewListResponse(BaseModel):
    items: list[ImportReviewItem]
    total: int
    limit: int
    offset: int


def _decode_csv(upload: UploadFile) -> csv.DictReader:
    raw = upload.file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="CSV vacio")
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        raise HTTPException(status_code=400, detail="CSV debe estar en UTF-8")
    return csv.DictReader(io.StringIO(text))


def _parse_int(value: str, *, field_name: str) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        raise ValueError(f"{field_name} debe ser un entero")


def _normalize_row(row: dict[str, Any]) -> dict[str, Any]:
    return {k.strip(): (v.strip() if isinstance(v, str) else v) for k, v in row.items()}


def _find_similar_products(db: Session, name: str, *, threshold: float, limit: int = 3) -> list[dict]:
    candidates = db.execute(select(Product.id, Product.name, Product.sku, Product.barcode)).all()
    if not candidates:
        return []

    scored = []
    target = name.strip().lower()
    for pid, pname, sku, barcode in candidates:
        ratio = SequenceMatcher(a=target, b=pname.strip().lower()).ratio()
        if ratio >= threshold:
            scored.append((ratio, pid, pname, sku, barcode))

    scored.sort(key=lambda x: x[0], reverse=True)
    return [
        {"product_id": pid, "name": pname, "sku": sku, "barcode": barcode, "similarity": round(ratio, 3)}
        for ratio, pid, pname, sku, barcode in scored[:limit]
    ]


def _get_category_or_error(db: Session, category_id: int) -> Category | None:
    return category_repo.get(db, category_id)


def _get_location_or_error(db: Session, location_id: int) -> Location | None:
    return db.get(Location, location_id)


def _resolve_product(
    db: Session,
    *,
    sku: str,
    barcode: str,
    name: str | None,
    category_id: int,
    fuzzy_threshold: float,
) -> tuple[Product | None, str | None, dict | None, bool]:
    by_sku = product_repo.get_by_sku(db, sku)
    by_barcode = product_repo.get_by_barcode(db, barcode)

    if by_sku and by_barcode and by_sku.id != by_barcode.id:
        return None, "sku_barcode_conflict", None, False

    product = by_sku or by_barcode
    if product:
        if product.sku != sku or (product.barcode or "") != barcode:
            return None, "sku_barcode_mismatch", None, False
        if product.category_id != category_id:
            return None, "category_mismatch", None, False
        return product, None, None, False

    if not name:
        return None, "missing_product_name", None, False

    suggestions = _find_similar_products(db, name, threshold=fuzzy_threshold)
    if suggestions:
        return None, "possible_duplicate", {"matches": suggestions}, False

    return None, None, None, True


def _record_error(db: Session, batch_id: int, row_number: int, code: str, message: str, payload: dict) -> ImportError:
    entry = ImportError(
        batch_id=batch_id,
        row_number=row_number,
        error_code=code,
        message=message,
        payload=payload,
    )
    db.add(entry)
    return entry


def _record_review(
    db: Session,
    batch_id: int,
    row_number: int,
    reason: str,
    payload: dict,
    suggestions: dict | None = None,
) -> ImportReview:
    entry = ImportReview(
        batch_id=batch_id,
        row_number=row_number,
        reason=reason,
        payload=payload,
        suggestions=suggestions,
    )
    db.add(entry)
    return entry


def _apply_event_row(
    db: Session,
    *,
    row: dict[str, Any],
    user_id: int,
):
    movement_type = row.get("type", "").upper()
    if movement_type not in {"IN", "OUT", "ADJUST"}:
        raise ValueError("type invalido (IN/OUT/ADJUST)")

    sku = row.get("sku", "")
    barcode = row.get("barcode", "")
    name = row.get("name", "") or None
    if not sku or not barcode:
        raise ValueError("sku y barcode son obligatorios")

    category_id = _parse_int(row.get("category_id"), field_name="category_id")
    location_id = _parse_int(row.get("location_id"), field_name="location_id")
    quantity = _parse_int(row.get("quantity"), field_name="quantity")

    if movement_type in {"IN", "OUT"} and quantity <= 0:
        raise ValueError("quantity debe ser > 0 para IN/OUT")
    if movement_type == "ADJUST" and quantity == 0:
        raise ValueError("quantity no puede ser 0 para ADJUST")

    if not _get_category_or_error(db, category_id):
        raise ValueError("category_id no existe")
    if not _get_location_or_error(db, location_id):
        raise ValueError("location_id no existe")

    by_sku = product_repo.get_by_sku(db, sku)
    by_barcode = product_repo.get_by_barcode(db, barcode)
    if by_sku and by_barcode and by_sku.id != by_barcode.id:
        raise ValueError("sku_barcode_conflict")

    product = by_sku or by_barcode
    if product:
        if product.sku != sku or (product.barcode or "") != barcode:
            raise ValueError("sku_barcode_mismatch")
        if product.category_id != category_id:
            raise ValueError("category_mismatch")
    else:
        if not name:
            raise ValueError("missing_product_name")
        try:
            product = product_repo.create_product(
                db,
                sku=sku,
                name=name,
                barcode=barcode,
                category_id=category_id,
            )
        except IntegrityError:
            db.rollback()
            raise ValueError("product_unique_conflict")

    if movement_type == "IN":
        inventory_service.increase_stock_by_location_id(
            db,
            product_id=product.id,
            quantity=quantity,
            user_id=user_id,
            location_id=location_id,
            source=Source.MANUAL,
        )
    elif movement_type == "OUT":
        inventory_service.decrease_stock_by_location_id(
            db,
            product_id=product.id,
            quantity=quantity,
            user_id=user_id,
            location_id=location_id,
            source=Source.MANUAL,
        )
    else:
        inventory_service.adjust_stock_by_location_id(
            db,
            product_id=product.id,
            quantity=quantity,
            user_id=user_id,
            location_id=location_id,
            source=Source.MANUAL,
        )


def _apply_transfer_row(
    db: Session,
    *,
    row: dict[str, Any],
    user_id: int,
):
    sku = row.get("sku", "")
    barcode = row.get("barcode", "")
    name = row.get("name", "") or None
    if not sku or not barcode:
        raise ValueError("sku y barcode son obligatorios")

    category_id = _parse_int(row.get("category_id"), field_name="category_id")
    from_location_id = _parse_int(row.get("from_location_id"), field_name="from_location_id")
    to_location_id = _parse_int(row.get("to_location_id"), field_name="to_location_id")
    quantity = _parse_int(row.get("quantity"), field_name="quantity")

    if quantity <= 0:
        raise ValueError("quantity debe ser > 0")
    if from_location_id == to_location_id:
        raise ValueError("from_location_id y to_location_id no pueden ser iguales")

    if not _get_category_or_error(db, category_id):
        raise ValueError("category_id no existe")
    if not _get_location_or_error(db, from_location_id):
        raise ValueError("from_location_id no existe")
    if not _get_location_or_error(db, to_location_id):
        raise ValueError("to_location_id no existe")

    by_sku = product_repo.get_by_sku(db, sku)
    by_barcode = product_repo.get_by_barcode(db, barcode)
    if by_sku and by_barcode and by_sku.id != by_barcode.id:
        raise ValueError("sku_barcode_conflict")

    product = by_sku or by_barcode
    if product:
        if product.sku != sku or (product.barcode or "") != barcode:
            raise ValueError("sku_barcode_mismatch")
        if product.category_id != category_id:
            raise ValueError("category_mismatch")
    else:
        if not name:
            raise ValueError("missing_product_name")
        try:
            product = product_repo.create_product(
                db,
                sku=sku,
                name=name,
                barcode=barcode,
                category_id=category_id,
            )
        except IntegrityError:
            db.rollback()
            raise ValueError("product_unique_conflict")

    inventory_service.transfer_stock_by_location_id(
        db,
        product_id=product.id,
        quantity=quantity,
        user_id=user_id,
        from_location_id=from_location_id,
        to_location_id=to_location_id,
        source=Source.MANUAL,
    )


@router.post(
    "/events/csv",
    response_model=ImportSummaryResponse,
    status_code=status.HTTP_201_CREATED,
)
def import_events_csv(
    file: UploadFile = File(...),
    dry_run: bool = Query(False),
    fuzzy_threshold: float = Query(0.9, ge=0.0, le=1.0),
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    reader = _decode_csv(file)
    required = {"type", "sku", "barcode", "name", "category_id", "location_id", "quantity"}
    if not reader.fieldnames:
        raise HTTPException(status_code=400, detail="CSV sin cabecera")
    missing = required - {name.strip() for name in reader.fieldnames}
    if missing:
        raise HTTPException(status_code=400, detail=f"Faltan columnas: {', '.join(sorted(missing))}")

    batch = ImportBatch(kind="EVENTS", user_id=user.id, dry_run=dry_run)
    db.add(batch)
    db.flush()

    errors: list[ImportErrorResponse] = []
    reviews: list[ImportReviewResponse] = []

    total_rows = 0
    ok_rows = 0
    error_rows = 0
    review_rows = 0

    for idx, raw_row in enumerate(reader, start=2):
        total_rows += 1
        row = _normalize_row(raw_row)
        payload = dict(row)

        try:
            movement_type = row.get("type", "").upper()
            if movement_type not in {"IN", "OUT", "ADJUST"}:
                raise ValueError("type invalido (IN/OUT/ADJUST)")

            sku = row.get("sku", "")
            barcode = row.get("barcode", "")
            name = row.get("name", "") or None
            if not sku or not barcode:
                raise ValueError("sku y barcode son obligatorios")

            category_id = _parse_int(row.get("category_id"), field_name="category_id")
            location_id = _parse_int(row.get("location_id"), field_name="location_id")
            quantity = _parse_int(row.get("quantity"), field_name="quantity")

            if movement_type in {"IN", "OUT"} and quantity <= 0:
                raise ValueError("quantity debe ser > 0 para IN/OUT")
            if movement_type == "ADJUST" and quantity == 0:
                raise ValueError("quantity no puede ser 0 para ADJUST")

            if not _get_category_or_error(db, category_id):
                raise ValueError("category_id no existe")
            if not _get_location_or_error(db, location_id):
                raise ValueError("location_id no existe")

            product, product_issue, suggestions, would_create = _resolve_product(
                db,
                sku=sku,
                barcode=barcode,
                name=name,
                category_id=category_id,
                fuzzy_threshold=fuzzy_threshold,
            )
            if product_issue:
                review = _record_review(
                    db,
                    batch.id,
                    idx,
                    product_issue,
                    payload,
                    suggestions,
                )
                review_rows += 1
                reviews.append(
                    ImportReviewResponse(
                        row_number=review.row_number,
                        reason=review.reason,
                        suggestions=review.suggestions,
                    )
                )
                continue
            if product is None and would_create:
                if dry_run:
                    ok_rows += 1
                    continue
                try:
                    product = product_repo.create_product(
                        db,
                        sku=sku,
                        name=name or sku,
                        barcode=barcode,
                        category_id=category_id,
                    )
                except IntegrityError:
                    db.rollback()
                    error = _record_error(db, batch.id, idx, "product_unique_conflict", "Producto duplicado", payload)
                    error_rows += 1
                    errors.append(
                        ImportErrorResponse(
                            row_number=error.row_number,
                            error_code=error.error_code,
                            message=error.message,
                        )
                    )
                    continue

            if not dry_run and product is not None:
                if movement_type == "IN":
                    inventory_service.increase_stock_by_location_id(
                        db,
                        product_id=product.id,
                        quantity=quantity,
                        user_id=user.id,
                        location_id=location_id,
                        source=Source.MANUAL,
                    )
                elif movement_type == "OUT":
                    inventory_service.decrease_stock_by_location_id(
                        db,
                        product_id=product.id,
                        quantity=quantity,
                        user_id=user.id,
                        location_id=location_id,
                        source=Source.MANUAL,
                    )
                else:
                    inventory_service.adjust_stock_by_location_id(
                        db,
                        product_id=product.id,
                        quantity=quantity,
                        user_id=user.id,
                        location_id=location_id,
                        source=Source.MANUAL,
                    )

            ok_rows += 1
        except inventory_service.InventoryError as exc:
            error = _record_error(db, batch.id, idx, "inventory_error", str(exc), payload)
            error_rows += 1
            errors.append(
                ImportErrorResponse(
                    row_number=error.row_number,
                    error_code=error.error_code,
                    message=error.message,
                )
            )
        except ValueError as exc:
            error = _record_error(db, batch.id, idx, "validation_error", str(exc), payload)
            error_rows += 1
            errors.append(
                ImportErrorResponse(
                    row_number=error.row_number,
                    error_code=error.error_code,
                    message=error.message,
                )
            )

    batch.total_rows = total_rows
    batch.ok_rows = ok_rows
    batch.error_rows = error_rows
    batch.review_rows = review_rows
    db.add(batch)
    db.commit()

    if ok_rows and not dry_run:
        cache_invalidate_prefix("movements:list")
        cache_invalidate_prefix("stocks:list")
        cache_invalidate_prefix("stocks:detail")
        cache_invalidate_prefix("products:list")
        cache_invalidate_prefix("reports:top-consumed")
        cache_invalidate_prefix("reports:turnover")

    if not dry_run and (error_rows > 0 or review_rows > 0):
        alert_repo.create_alert(
            db,
            stock_id=None,
            quantity=error_rows + review_rows,
            min_quantity=0,
            alert_type=AlertType.IMPORT_ISSUES,
            status=AlertStatus.PENDING,
        )

    return ImportSummaryResponse(
        batch_id=batch.id,
        dry_run=dry_run,
        total_rows=total_rows,
        ok_rows=ok_rows,
        error_rows=error_rows,
        review_rows=review_rows,
        errors=errors,
        reviews=reviews,
    )


@router.post(
    "/transfers/csv",
    response_model=ImportSummaryResponse,
    status_code=status.HTTP_201_CREATED,
)
def import_transfers_csv(
    file: UploadFile = File(...),
    dry_run: bool = Query(False),
    fuzzy_threshold: float = Query(0.9, ge=0.0, le=1.0),
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    reader = _decode_csv(file)
    required = {"sku", "barcode", "name", "category_id", "from_location_id", "to_location_id", "quantity"}
    if not reader.fieldnames:
        raise HTTPException(status_code=400, detail="CSV sin cabecera")
    missing = required - {name.strip() for name in reader.fieldnames}
    if missing:
        raise HTTPException(status_code=400, detail=f"Faltan columnas: {', '.join(sorted(missing))}")

    batch = ImportBatch(kind="TRANSFERS", user_id=user.id, dry_run=dry_run)
    db.add(batch)
    db.flush()

    errors: list[ImportErrorResponse] = []
    reviews: list[ImportReviewResponse] = []

    total_rows = 0
    ok_rows = 0
    error_rows = 0
    review_rows = 0

    for idx, raw_row in enumerate(reader, start=2):
        total_rows += 1
        row = _normalize_row(raw_row)
        payload = dict(row)

        try:
            sku = row.get("sku", "")
            barcode = row.get("barcode", "")
            name = row.get("name", "") or None
            if not sku or not barcode:
                raise ValueError("sku y barcode son obligatorios")

            category_id = _parse_int(row.get("category_id"), field_name="category_id")
            from_location_id = _parse_int(row.get("from_location_id"), field_name="from_location_id")
            to_location_id = _parse_int(row.get("to_location_id"), field_name="to_location_id")
            quantity = _parse_int(row.get("quantity"), field_name="quantity")

            if quantity <= 0:
                raise ValueError("quantity debe ser > 0")
            if from_location_id == to_location_id:
                raise ValueError("from_location_id y to_location_id no pueden ser iguales")

            if not _get_category_or_error(db, category_id):
                raise ValueError("category_id no existe")
            if not _get_location_or_error(db, from_location_id):
                raise ValueError("from_location_id no existe")
            if not _get_location_or_error(db, to_location_id):
                raise ValueError("to_location_id no existe")

            product, product_issue, suggestions, would_create = _resolve_product(
                db,
                sku=sku,
                barcode=barcode,
                name=name,
                category_id=category_id,
                fuzzy_threshold=fuzzy_threshold,
            )
            if product_issue:
                review = _record_review(
                    db,
                    batch.id,
                    idx,
                    product_issue,
                    payload,
                    suggestions,
                )
                review_rows += 1
                reviews.append(
                    ImportReviewResponse(
                        row_number=review.row_number,
                        reason=review.reason,
                        suggestions=review.suggestions,
                    )
                )
                continue
            if product is None and would_create:
                if dry_run:
                    ok_rows += 1
                    continue
                try:
                    product = product_repo.create_product(
                        db,
                        sku=sku,
                        name=name or sku,
                        barcode=barcode,
                        category_id=category_id,
                    )
                except IntegrityError:
                    db.rollback()
                    error = _record_error(db, batch.id, idx, "product_unique_conflict", "Producto duplicado", payload)
                    error_rows += 1
                    errors.append(
                        ImportErrorResponse(
                            row_number=error.row_number,
                            error_code=error.error_code,
                            message=error.message,
                        )
                    )
                    continue

            if not dry_run and product is not None:
                inventory_service.transfer_stock_by_location_id(
                    db,
                    product_id=product.id,
                    quantity=quantity,
                    user_id=user.id,
                    from_location_id=from_location_id,
                    to_location_id=to_location_id,
                    source=Source.MANUAL,
                )

            ok_rows += 1
        except inventory_service.InventoryError as exc:
            error = _record_error(db, batch.id, idx, "inventory_error", str(exc), payload)
            error_rows += 1
            errors.append(
                ImportErrorResponse(
                    row_number=error.row_number,
                    error_code=error.error_code,
                    message=error.message,
                )
            )
        except ValueError as exc:
            error = _record_error(db, batch.id, idx, "validation_error", str(exc), payload)
            error_rows += 1
            errors.append(
                ImportErrorResponse(
                    row_number=error.row_number,
                    error_code=error.error_code,
                    message=error.message,
                )
            )

    batch.total_rows = total_rows
    batch.ok_rows = ok_rows
    batch.error_rows = error_rows
    batch.review_rows = review_rows
    db.add(batch)
    db.commit()

    if ok_rows and not dry_run:
        cache_invalidate_prefix("movements:list")
        cache_invalidate_prefix("stocks:list")
        cache_invalidate_prefix("stocks:detail")
        cache_invalidate_prefix("products:list")
        cache_invalidate_prefix("reports:top-consumed")
        cache_invalidate_prefix("reports:turnover")

    if not dry_run and (error_rows > 0 or review_rows > 0):
        alert_repo.create_alert(
            db,
            stock_id=None,
            quantity=error_rows + review_rows,
            min_quantity=0,
            alert_type=AlertType.IMPORT_ISSUES,
            status=AlertStatus.PENDING,
        )

    return ImportSummaryResponse(
        batch_id=batch.id,
        dry_run=dry_run,
        total_rows=total_rows,
        ok_rows=ok_rows,
        error_rows=error_rows,
        review_rows=review_rows,
        errors=errors,
        reviews=reviews,
    )


@router.get(
    "/reviews",
    response_model=ImportReviewListResponse,
)
def list_import_reviews(
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
    batch_id: int | None = Query(None),
    kind: str | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    stmt = select(ImportReview, ImportBatch).join(ImportBatch, ImportReview.batch_id == ImportBatch.id)
    if batch_id is not None:
        stmt = stmt.where(ImportReview.batch_id == batch_id)
    if kind:
        stmt = stmt.where(ImportBatch.kind == kind)

    total = db.execute(
        select(func.count()).select_from(stmt.subquery())
    ).scalar() or 0

    rows = db.execute(
        stmt.order_by(ImportReview.id.asc()).offset(offset).limit(limit)
    ).all()

    items = [
        ImportReviewItem(
            id=review.id,
            batch_id=review.batch_id,
            row_number=review.row_number,
            reason=review.reason,
            payload=review.payload,
            suggestions=review.suggestions,
        )
        for review, _batch in rows
    ]
    return ImportReviewListResponse(items=items, total=total, limit=limit, offset=offset)


@router.post(
    "/reviews/{review_id}/approve",
    status_code=status.HTTP_200_OK,
)
def approve_import_review(
    review_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    review = db.get(ImportReview, review_id)
    if not review:
        raise HTTPException(status_code=404, detail="Review no encontrada")
    batch = db.get(ImportBatch, review.batch_id)
    if not batch:
        raise HTTPException(status_code=404, detail="Batch no encontrado")

    try:
        row = _normalize_row(review.payload)
        if batch.kind == "EVENTS":
            _apply_event_row(db, row=row, user_id=user.id)
        elif batch.kind == "TRANSFERS":
            _apply_transfer_row(db, row=row, user_id=user.id)
        else:
            raise HTTPException(status_code=400, detail="Tipo de batch no soportado")
    except inventory_service.InventoryError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    batch.review_rows = max(0, batch.review_rows - 1)
    batch.ok_rows += 1
    db.delete(review)
    db.add(batch)
    db.commit()

    cache_invalidate_prefix("movements:list")
    cache_invalidate_prefix("stocks:list")
    cache_invalidate_prefix("stocks:detail")
    cache_invalidate_prefix("products:list")
    cache_invalidate_prefix("reports:top-consumed")
    cache_invalidate_prefix("reports:turnover")

    return {"ok": True}


@router.post(
    "/reviews/{review_id}/reject",
    status_code=status.HTTP_200_OK,
)
def reject_import_review(
    review_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    review = db.get(ImportReview, review_id)
    if not review:
        raise HTTPException(status_code=404, detail="Review no encontrada")
    batch = db.get(ImportBatch, review.batch_id)
    if not batch:
        raise HTTPException(status_code=404, detail="Batch no encontrado")

    _record_error(
        db,
        batch.id,
        review.row_number,
        "review_rejected",
        f"Revisado y rechazado: {review.reason}",
        review.payload,
    )

    batch.review_rows = max(0, batch.review_rows - 1)
    batch.error_rows += 1
    db.delete(review)
    db.add(batch)
    db.commit()

    return {"ok": True}
