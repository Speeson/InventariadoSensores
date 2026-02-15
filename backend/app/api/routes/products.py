from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import FileResponse
import logging
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.cache.redis_cache import cache_get, cache_set, cache_invalidate_prefix, make_key
from app.db.deps import get_db
from app.models.enums import UserRole, Entity, ActionType
from app.repositories import product_repo, audit_log_repo
from app.schemas.product import ProductCreate, ProductUpdate, ProductResponse
from app.models.category import Category
from app.models.user import User
from app.services import label_service


class ProductListResponse(BaseModel):
    items: list[ProductResponse]
    total: int
    limit: int
    offset: int


router = APIRouter(prefix="/products", tags=["products"])
logger = logging.getLogger(__name__)


@router.get("/", response_model=ProductListResponse, dependencies=[Depends(get_current_user)])
def list_products(
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
    sku: str | None = Query(None),
    name: str | None = Query(None),
    barcode: str | None = Query(None),
    category_id: int | None = Query(None),
    active: bool | None = Query(None),
    order_by: str | None = Query("id"),
    order_dir: str | None = Query("asc"),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    allowed_order = {"id", "created_at"}
    if order_by not in allowed_order:
        raise HTTPException(status_code=400, detail=f"order_by debe ser uno de {sorted(allowed_order)}")
    if order_dir not in {"asc", "desc"}:
        raise HTTPException(status_code=400, detail="order_dir debe ser 'asc' o 'desc'")
    cache_key = make_key(
        "products:list",
        user.id if user else None,
        {
            "sku": sku,
            "name": name,
            "barcode": barcode,
            "category_id": category_id,
            "active": active,
            "order_by": order_by,
            "order_dir": order_dir,
            "limit": limit,
            "offset": offset,
        },
    )
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    items, total = product_repo.list_products(
        db,
        sku=sku,
        name=name,
        barcode=barcode,
        category_id=category_id,
        active=active,
        order_by=order_by,
        order_dir=order_dir,
        limit=limit,
        offset=offset,
    )
    payload = ProductListResponse(items=items, total=total, limit=limit, offset=offset)
    cache_set(cache_key, payload, ttl_seconds=300)
    return payload


@router.get("/{product_id}", response_model=ProductResponse, dependencies=[Depends(get_current_user)])
def get_product(product_id: int, db: Session = Depends(get_db), user=Depends(get_current_user)):
    cache_key = make_key("products:detail", user.id if user else None, {"id": product_id})
    cached = cache_get(cache_key)
    if cached is not None:
        return cached
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")
    cache_set(cache_key, product, ttl_seconds=300)
    return product


@router.post(
    "/",
    response_model=ProductResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_product(
    payload: ProductCreate,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    category = db.get(Category, payload.category_id)
    if not category:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Categoria no existe")

    if product_repo.get_by_sku(db, payload.sku):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="SKU ya existe")
    if payload.barcode and product_repo.get_by_barcode(db, payload.barcode):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Barcode ya existe")
    product = product_repo.create_product(
        db,
        sku=payload.sku,
        name=payload.name,
        barcode=payload.barcode,
        category_id=payload.category_id,
        active=payload.active if payload.active is not None else True,
    )
    if product.barcode:
        try:
            label_service.generate_and_store_label(
                product_id=product.id,
                barcode_value=product.barcode,
                sku=product.sku,
            )
        except Exception:
            logger.exception("No se pudo generar la etiqueta para producto %s", product.id)
    audit_log_repo.create_log(
        db,
        entity=Entity.PRODUCT,
        action=ActionType.CREATE,
        user_id=user.id,
        details=f"product_id={product.id} sku={product.sku}",
    )
    return product


@router.patch(
    "/{product_id}",
    response_model=ProductResponse,
)
def update_product(
    product_id: int,
    payload: ProductUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")

    if payload.category_id is not None:
        category = db.get(Category, payload.category_id)
        if not category:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Categoria no existe")

    if payload.barcode:
        existing_barcode = product_repo.get_by_barcode(db, payload.barcode)
        if existing_barcode and existing_barcode.id != product_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Barcode ya existe")
    should_regen_label = payload.barcode is not None
    if payload.name is not None or payload.barcode is not None or payload.category_id is not None or payload.active is not None:
        product = product_repo.update_product(
            db,
            product,
            name=payload.name,
            barcode=payload.barcode,
            category_id=payload.category_id,
            active=payload.active,
        )
        if should_regen_label and product.barcode:
            try:
                label_service.generate_and_store_label(
                    product_id=product.id,
                    barcode_value=product.barcode,
                    sku=product.sku,
                )
            except Exception:
                logger.exception("No se pudo regenerar la etiqueta para producto %s", product.id)
    cache_invalidate_prefix("products:list")
    cache_invalidate_prefix("products:detail")
    audit_log_repo.create_log(
        db,
        entity=Entity.PRODUCT,
        action=ActionType.UPDATE,
        user_id=user.id,
        details=f"product_id={product.id} sku={product.sku}",
    )
    return product


@router.delete(
    "/{product_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def delete_product(
    product_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")
    label_path = label_service.label_path_for(product.id)
    product_repo.delete_product(db, product)
    try:
        label_path.unlink()
    except OSError:
        pass
    cache_invalidate_prefix("products:list")
    cache_invalidate_prefix("products:detail")
    audit_log_repo.create_log(
        db,
        entity=Entity.PRODUCT,
        action=ActionType.DELETE,
        user_id=user.id,
        details=f"product_id={product.id} sku={product.sku}",
    )
    return None


@router.get(
    "/{product_id}/label.svg",
    dependencies=[Depends(get_current_user)],
)
def get_product_label_svg(product_id: int, db: Session = Depends(get_db)):
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")
    if not product.barcode:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Producto sin barcode")

    label_path = label_service.label_path_for(product.id)
    if not label_path.exists():
        try:
            label_service.generate_and_store_label(
                product_id=product.id,
                barcode_value=product.barcode,
                sku=product.sku,
            )
        except Exception:
            logger.exception("No se pudo generar la etiqueta para producto %s", product.id)
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Error generando etiqueta")

    return FileResponse(label_path, media_type="image/svg+xml")


@router.post(
    "/{product_id}/label/regenerate",
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def regenerate_product_label(product_id: int, db: Session = Depends(get_db)):
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")
    if not product.barcode:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Producto sin barcode")
    try:
        label_service.generate_and_store_label(
            product_id=product.id,
            barcode_value=product.barcode,
            sku=product.sku,
        )
    except Exception:
        logger.exception("No se pudo regenerar la etiqueta para producto %s", product.id)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Error regenerando etiqueta")
    return {"ok": True, "product_id": product.id}
