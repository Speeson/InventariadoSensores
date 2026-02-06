from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import FileResponse
import logging
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.db.deps import get_db
from app.models.enums import UserRole
from app.repositories import product_repo
from app.schemas.product import ProductCreate, ProductUpdate, ProductResponse
from app.models.category import Category
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
    sku: str | None = Query(None),
    name: str | None = Query(None),
    barcode: str | None = Query(None),
    category_id: int | None = Query(None),
    active: bool | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    items, total = product_repo.list_products(
        db,
        sku=sku,
        name=name,
        barcode=barcode,
        category_id=category_id,
        active=active,
        limit=limit,
        offset=offset,
    )
    return ProductListResponse(items=items, total=total, limit=limit, offset=offset)


@router.get("/{product_id}", response_model=ProductResponse, dependencies=[Depends(get_current_user)])
def get_product(product_id: int, db: Session = Depends(get_db)):
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")
    return product


@router.post(
    "/",
    response_model=ProductResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def create_product(payload: ProductCreate, db: Session = Depends(get_db)):
    category = db.get(Category, payload.category_id)
    if not category:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Categoría no existe")

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
    return product


@router.patch(
    "/{product_id}",
    response_model=ProductResponse,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def update_product(product_id: int, payload: ProductUpdate, db: Session = Depends(get_db)):
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")

    if payload.category_id is not None:
        category = db.get(Category, payload.category_id)
        if not category:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Categoría no existe")

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
    return product


@router.delete(
    "/{product_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def delete_product(product_id: int, db: Session = Depends(get_db)):
    product = product_repo.get(db, product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")
    label_path = label_service.label_path_for(product.id)
    product_repo.delete_product(db, product)
    try:
        label_path.unlink()
    except OSError:
        pass
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
